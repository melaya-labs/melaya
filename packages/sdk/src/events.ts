/**
 * Platform real-time events via Socket.IO at `/api/v1/events`.
 *
 * The `MelayaEvents` class exposes typed subscription helpers that mirror what
 * the Melaya frontend subscribes to in `SocketContext.tsx`. The underlying
 * Socket.IO connection is opened lazily on the first subscription/join call —
 * constructing the client performs no network I/O and never keeps a one-shot
 * process alive.
 *
 * Because Socket.IO uses its own protocol, this module does NOT reuse the
 * raw `WebSocket` used by `stream.ts`. Instead it builds a minimal, zero-dep
 * client that speaks the Engine.IO/Socket.IO wire format for HTTP long-polling
 * (the universal transport) and upgrades to WebSocket when available. For
 * Node environments we recommend also passing `WebSocket` in `MelayaOptions`.
 *
 * Room semantics (mirroring the server):
 *   - `run:<runId>` — subscribe to events for a specific pipeline run
 *   - `project:<project>` — subscribe to all events in a project
 *   - `hitl:user:<userId>` — HITL approval events (joined automatically for
 *     the authenticated user by the server on connect)
 *
 * @example
 * ```ts
 * const evts = melaya.events;
 * evts.onRunUpdate("run-123", (e) => console.log(e.event_type, e.status));
 * evts.onHitlApproval((e) => console.log("HITL:", e.type, e.requestId));
 *
 * // Clean up
 * evts.leaveRun("run-123");
 * evts.close();
 * ```
 */

import type {
  HitlApprovalEvent,
  PipelineCrudEvent,
  RunInitPhaseEvent,
  RunPushEvent,
} from "./platform-types.js";
import type { WebSocketCtor } from "./stream.js";

type Listener<T> = (payload: T) => void;
type AnyListener = Listener<unknown>;

/** Minimal Engine.IO / Socket.IO v4 packet types we care about. */
const EIO_PING = "2";
const EIO_PONG = "3";
const EIO_MESSAGE = "4"; // followed by Socket.IO packet

const SIO_CONNECT = 0;
const SIO_EVENT = 2;

/** Engine.IO v4 record separator between batched polling packets. */
const EIO_RECORD_SEPARATOR = "\x1e";

/**
 * A thin, zero-dependency Socket.IO v4 client (Engine.IO v4 underneath).
 *
 * Transport strategy: on the first subscription/join call, complete the
 * Engine.IO handshake over HTTP long-poll (the spec-mandated upgrade path),
 * then upgrade to WebSocket. In environments without WebSocket the client
 * stays on long-poll: incoming batches are split on the 0x1e record
 * separator, and outbound packets (pongs, room joins) are POSTed back to the
 * polling endpoint with the session id.
 *
 * The client only implements the subset needed for the Melaya events surface:
 *   - connect (emit `40` connect packet with auth)
 *   - join rooms (emit `42["joinRunRoom", ...]` / `42["joinProjectRoom", ...]`)
 *   - receive events (parse `42[...]` packets)
 *   - disconnect / close
 */
export class MelayaEvents {
  private readonly baseUrl: string;
  private readonly apiKey: string;
  private readonly WebSocketImpl: WebSocketCtor | undefined;
  private readonly fetchImpl: typeof fetch;

  private sid: string | null = null;
  private ws: import("./stream.js").WebSocketLike | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private closed = false;
  /** True once the first subscription/join has triggered a connect. */
  private started = false;
  /** Reconnect attempt count for exponential backoff (reset on successful connect). */
  private reconnectAttempt = 0;
  /** Outbound packets queued while the WebSocket upgrade is still connecting. */
  private pendingOut: string[] = [];

  /** event name → Set of listeners */
  private readonly eventListeners = new Map<string, Set<AnyListener>>();
  /** rooms currently joined (replayed after every reconnect) */
  private readonly joinedRooms = new Set<string>();
  /** room → listeners registered through that room (removed by leave*) */
  private readonly roomListeners = new Map<string, Set<{ event: string; listener: AnyListener }>>();

  constructor(opts: {
    baseUrl: string;
    apiKey: string;
    WebSocket?: WebSocketCtor;
    fetch: typeof fetch;
  }) {
    // Derive the Socket.IO URL: same origin, path=/api/v1/events
    const base = opts.baseUrl.replace(/\/$/, "");
    this.baseUrl = base;
    this.apiKey = opts.apiKey;
    this.WebSocketImpl = opts.WebSocket;
    this.fetchImpl = opts.fetch;
    // NOTE: no connect() here — the connection opens lazily on first use.
  }

  // ── Public subscription API ────────────────────────────────────────────────

  /**
   * Subscribe to run events for a specific pipeline run.
   * Automatically joins the `run:<runId>` Socket.IO room. Events that carry a
   * `runId` for a different run (e.g. when multiple run rooms are joined) are
   * filtered out before your callback fires.
   *
   * Returns an unsubscribe function.
   */
  onRunUpdate(runId: string, cb: Listener<RunPushEvent>): () => void {
    const filtered: Listener<RunPushEvent> = (e) => {
      if (e && typeof e === "object" && e.runId !== undefined && e.runId !== runId) return;
      cb(e);
    };
    return this.registerRoomListener(`run:${runId}`, "pushEvent", filtered as AnyListener);
  }

  /**
   * Subscribe to init-phase progress events for a run.
   * Automatically joins the `run:<runId>` room; events for other runs are
   * filtered out.
   *
   * Returns an unsubscribe function.
   */
  onInitPhase(runId: string, cb: Listener<RunInitPhaseEvent>): () => void {
    const filtered: Listener<RunInitPhaseEvent> = (e) => {
      if (e && typeof e === "object" && e.runId !== undefined && e.runId !== runId) return;
      cb(e);
    };
    return this.registerRoomListener(`run:${runId}`, "pushInitPhase", filtered as AnyListener);
  }

  /**
   * Subscribe to all events in a project (all runs, pipeline CRUD).
   * Automatically joins the `project:<project>` room. Events that carry a
   * `project` for a different project are filtered out.
   *
   * Returns an unsubscribe function.
   */
  onProjectEvent(
    project: string,
    cb: Listener<RunPushEvent | PipelineCrudEvent>,
  ): () => void {
    const filtered: Listener<RunPushEvent | PipelineCrudEvent> = (e) => {
      if (e && typeof e === "object" && e.project !== undefined && e.project !== project) return;
      cb(e);
    };
    return this.registerRoomListener(`project:${project}`, "pushEvent", filtered as AnyListener);
  }

  /**
   * Subscribe to HITL approval invalidation events.
   * The server automatically joins authenticated sockets to the
   * `hitl:user:<userId>` room on connect — no explicit join needed.
   *
   * Returns an unsubscribe function.
   */
  onHitlApproval(cb: Listener<HitlApprovalEvent>): () => void {
    return this.on("pushHitlApprovals", cb as AnyListener);
  }

  /** Subscribe to pipeline CRUD events within a project room. */
  onPipelineCreated(project: string, cb: Listener<PipelineCrudEvent>): () => void {
    return this.subscribeProjectCrud(project, "pipelineCreated", cb);
  }

  /** Subscribe to pipeline update events within a project room. */
  onPipelineUpdated(project: string, cb: Listener<PipelineCrudEvent>): () => void {
    return this.subscribeProjectCrud(project, "pipelineUpdated", cb);
  }

  /** Subscribe to pipeline deletion events within a project room. */
  onPipelineDeleted(project: string, cb: Listener<PipelineCrudEvent>): () => void {
    return this.subscribeProjectCrud(project, "pipelineDeleted", cb);
  }

  /**
   * Stop listening to a specific run (leave the run room and remove its listeners).
   * Listeners registered for other events/rooms are unaffected.
   */
  leaveRun(runId: string): void {
    this.leaveRoom(`run:${runId}`);
  }

  /** Leave a project room and remove the listeners registered through it. */
  leaveProject(project: string): void {
    this.leaveRoom(`project:${project}`);
  }

  /** Close the Socket.IO connection and release all listeners. */
  close(): void {
    this.closed = true;
    if (this.reconnectTimer) { clearTimeout(this.reconnectTimer); this.reconnectTimer = null; }
    this.ws?.close();
    this.ws = null;
    this.sid = null;
    this.pendingOut = [];
    this.eventListeners.clear();
    this.joinedRooms.clear();
    this.roomListeners.clear();
  }

  /**
   * Compute a bounded exponential backoff delay with jitter.
   * Caps at 30 000 ms; resets reconnectAttempt on successful connect.
   */
  private nextReconnectDelay(): number {
    const base = Math.min(1000 * 2 ** this.reconnectAttempt, 30_000);
    // ±20% jitter
    const jitter = base * 0.2 * (Math.random() * 2 - 1);
    this.reconnectAttempt = Math.min(this.reconnectAttempt + 1, 10);
    return Math.max(500, base + jitter);
  }

  // ── Internal helpers ───────────────────────────────────────────────────────

  /** Lazily open the connection on the first subscription/join. */
  private ensureConnected(): void {
    if (this.started || this.closed) return;
    this.started = true;
    void this.connect();
  }

  /** Register a raw Socket.IO event listener. Returns an unsubscribe fn. */
  private on(event: string, cb: AnyListener): () => void {
    this.ensureConnected();
    if (!this.eventListeners.has(event)) {
      this.eventListeners.set(event, new Set());
    }
    this.eventListeners.get(event)!.add(cb);
    return () => {
      this.eventListeners.get(event)?.delete(cb);
    };
  }

  /**
   * Join a room and register a listener that belongs to it, so a later
   * `leaveRun()` / `leaveProject()` can remove exactly this listener.
   */
  private registerRoomListener(room: string, event: string, listener: AnyListener): () => void {
    this.joinRoom(room);
    const off = this.on(event, listener);
    let set = this.roomListeners.get(room);
    if (!set) { set = new Set(); this.roomListeners.set(room, set); }
    const entry = { event, listener };
    set.add(entry);
    return () => {
      off();
      this.roomListeners.get(room)?.delete(entry);
    };
  }

  /** Shared body of the onPipelineCreated/Updated/Deleted subscriptions. */
  private subscribeProjectCrud(
    project: string,
    event: string,
    cb: Listener<PipelineCrudEvent>,
  ): () => void {
    const filtered: Listener<PipelineCrudEvent> = (e) => {
      if (e && typeof e === "object" && e.project !== undefined && e.project !== project) return;
      cb(e);
    };
    return this.registerRoomListener(`project:${project}`, event, filtered as AnyListener);
  }

  private emit(event: string, payload: unknown): void {
    const listeners = this.eventListeners.get(event);
    if (!listeners) return;
    for (const l of [...listeners]) {
      try { l(payload); } catch { /* listener errors must not crash the client */ }
    }
  }

  /** Handle a decoded Socket.IO event packet. */
  private handleSioEvent(args: unknown[]): void {
    if (!Array.isArray(args) || args.length < 2) return;
    const [event, payload] = args as [string, unknown];
    this.emit(event, payload);
  }

  /** Decode a raw Engine.IO packet string and dispatch. */
  private handlePacket(raw: string): void {
    if (!raw) return;
    const type = raw[0];
    if (type === EIO_PING) {
      // Engine.IO heartbeat — answer promptly with a pong on whatever
      // transport is active (WebSocket frame or polling POST).
      this.sendPacket(EIO_PONG);
      return;
    }
    if (type !== EIO_MESSAGE) return;
    // Socket.IO packet starts at index 1
    const sioRaw = raw.slice(1);
    const sioType = parseInt(sioRaw[0], 10);
    if (sioType !== SIO_EVENT) return;
    try {
      const args = JSON.parse(sioRaw.slice(1)) as unknown[];
      this.handleSioEvent(args);
    } catch {
      // ignore malformed packets
    }
  }

  // ── Connection lifecycle ───────────────────────────────────────────────────

  private pollUrl(params: Record<string, string> = {}): string {
    const u = new URL(`${this.baseUrl}/`);
    // Engine.IO endpoint is the Socket.IO path
    u.pathname = "/api/v1/events/";
    u.searchParams.set("EIO", "4");
    u.searchParams.set("transport", "polling");
    if (this.sid) u.searchParams.set("sid", this.sid);
    for (const [k, v] of Object.entries(params)) u.searchParams.set(k, v);
    return u.toString();
  }

  private wsUrl(): string {
    const u = new URL(`${this.baseUrl}/`);
    u.protocol = u.protocol === "https:" ? "wss:" : "ws:";
    u.pathname = "/api/v1/events/";
    u.searchParams.set("EIO", "4");
    u.searchParams.set("transport", "websocket");
    if (this.sid) u.searchParams.set("sid", this.sid);
    return u.toString();
  }

  private wsSend(data: string): void {
    if (this.ws && (this.ws.readyState === 1 /* OPEN */)) {
      try { (this.ws as unknown as { send: (d: string) => void }).send(data); } catch { /* ignore */ }
    }
  }

  /**
   * Send an Engine.IO packet over whatever transport is active:
   *   - WebSocket open → send as a frame
   *   - WebSocket still connecting → queue, flushed on open
   *   - long-poll → POST to the polling endpoint with the session id
   */
  private sendPacket(packet: string): void {
    if (this.ws) {
      if (this.ws.readyState === 1 /* OPEN */) {
        this.wsSend(packet);
      } else {
        this.pendingOut.push(packet);
      }
      return;
    }
    if (this.sid) {
      void this.postPacket(packet);
    }
    // Not connected yet: room joins are replayed after connect; anything else
    // (pongs) is meaningless without a session and is dropped.
  }

  /** POST one outbound packet to the long-poll endpoint. */
  private async postPacket(packet: string): Promise<void> {
    try {
      const res = await this.fetchImpl(this.pollUrl(), {
        method: "POST",
        headers: {
          Authorization: `Bearer ${this.apiKey}`,
          "Content-Type": "text/plain;charset=UTF-8",
        },
        body: packet,
      });
      if (res.status === 400 && !this.closed && !this.ws) {
        // "Session ID unknown" — the session is dead; full re-handshake.
        this.sid = null;
        this.scheduleReconnect();
      }
    } catch {
      /* transient network error; the poll loop detects dead sessions */
    }
  }

  /** Build the Socket.IO connect packet, including the Bearer token for auth. */
  private sioConnectPacket(): string {
    // "40" = EIO message (4) + SIO connect (0) + optional JSON data
    const authData = JSON.stringify({ token: this.apiKey });
    return `${EIO_MESSAGE}${SIO_CONNECT}${authData}`;
  }

  /** Emit a Socket.IO event packet over whatever transport is active. */
  private sioEmit(event: string, ...args: unknown[]): void {
    const packet = `${EIO_MESSAGE}${SIO_EVENT}${JSON.stringify([event, ...args])}`;
    this.sendPacket(packet);
  }

  /**
   * Map a logical room string to the correct server emit + arg.
   * - "run:<id>"      → joinRunRoom(<id>)
   * - "project:<p>"   → joinProjectRoom(<p>)
   * - else            → joinRunRoom(<room>) as a safe fallback
   */
  private emitJoin(room: string): void {
    if (room.startsWith("run:")) {
      this.sioEmit("joinRunRoom", room.slice(4));
    } else if (room.startsWith("project:")) {
      this.sioEmit("joinProjectRoom", room.slice(8));
    } else {
      // Unknown room prefix — best-effort fallback
      this.sioEmit("joinRunRoom", room);
    }
  }

  private joinRoom(room: string): void {
    this.ensureConnected();
    if (this.joinedRooms.has(room)) return;
    this.joinedRooms.add(room);
    if (this.sid) {
      this.emitJoin(room);
    }
    // if not connected yet, joinedRooms will be replayed in replayJoins()
  }

  private leaveRoom(room: string): void {
    // Remove the listeners registered through this room so callbacks stop
    // firing, and drop the room from the rejoin-on-reconnect list.
    const set = this.roomListeners.get(room);
    if (set) {
      for (const { event, listener } of set) {
        this.eventListeners.get(event)?.delete(listener);
      }
      this.roomListeners.delete(room);
    }
    this.joinedRooms.delete(room);
    if (this.sid) {
      // Server expects leaveRoom with the full room string ("run:<id>" / "project:<p>")
      this.sioEmit("leaveRoom", room);
    }
  }

  /** Replay all pending room joins after a successful connect. */
  private replayJoins(): void {
    for (const room of this.joinedRooms) {
      this.emitJoin(room);
    }
  }

  /**
   * Schedule a reconnect with bounded exponential backoff. The timer is
   * unref()ed (where supported) so a pending reconnect never keeps a Node
   * process alive on its own.
   */
  private scheduleReconnect(): void {
    if (this.closed || this.reconnectTimer) return;
    const t = setTimeout(() => {
      this.reconnectTimer = null;
      void this.connect();
    }, this.nextReconnectDelay());
    (t as unknown as { unref?: () => void }).unref?.();
    this.reconnectTimer = t;
  }

  private async connect(): Promise<void> {
    if (this.closed) return;
    try {
      // Step 1: Engine.IO handshake via HTTP polling
      const res = await this.fetchImpl(this.pollUrl(), {
        headers: { Authorization: `Bearer ${this.apiKey}` },
      });
      const text = await res.text();
      // EIO4 open packet format: "0{...json...}"
      const jsonStart = text.indexOf("{");
      if (!res.ok || jsonStart === -1) throw new Error("MelayaEvents: unexpected EIO handshake response");
      const openData = JSON.parse(
        text.slice(jsonStart).split(EIO_RECORD_SEPARATOR)[0],
      ) as { sid: string; pingInterval?: number; pingTimeout?: number };
      this.sid = openData.sid;

      // Step 2: Send Socket.IO connect packet with auth via polling POST
      const post = await this.fetchImpl(this.pollUrl(), {
        method: "POST",
        headers: {
          Authorization: `Bearer ${this.apiKey}`,
          "Content-Type": "text/plain;charset=UTF-8",
        },
        body: this.sioConnectPacket(),
      });
      if (!post.ok) throw new Error("MelayaEvents: Socket.IO connect packet rejected");

      this.reconnectAttempt = 0; // successful connect — reset backoff

      // Step 3: Upgrade to WebSocket if available; otherwise stay on long-poll
      if (this.WebSocketImpl && !this.closed) {
        this.upgradeToWs(); // joins are replayed once the socket opens
      } else if (!this.closed) {
        this.replayJoins();
        void this.pollLoop();
      }
    } catch {
      // Retry with bounded exponential back-off
      if (!this.closed) {
        this.scheduleReconnect();
      }
    }
  }

  private upgradeToWs(): void {
    if (!this.WebSocketImpl || this.closed) return;
    const ws = new this.WebSocketImpl(this.wsUrl());
    this.ws = ws;
    ws.addEventListener("message", (ev: unknown) => {
      const raw = (ev as { data?: unknown }).data;
      const text = typeof raw === "string" ? raw
        : raw instanceof ArrayBuffer ? new TextDecoder().decode(raw) : String(raw);
      this.handlePacket(text);
    });
    ws.addEventListener("close", () => {
      this.ws = null;
      this.pendingOut = [];
      if (!this.closed) {
        // reconnect with bounded exponential backoff
        this.sid = null;
        this.scheduleReconnect();
      }
    });
    ws.addEventListener("error", () => {
      /* close will follow */
    });
    ws.addEventListener("open", () => {
      // Engine.IO WebSocket upgrade probe
      this.wsSend("2probe");
      this.wsSend("5"); // upgrade packet
      // Flush anything queued while connecting, then (re)join our rooms.
      const queued = this.pendingOut;
      this.pendingOut = [];
      for (const p of queued) this.wsSend(p);
      this.replayJoins();
    });
  }

  /**
   * Long-poll receive loop (used when no WebSocket implementation is
   * available). Each GET is held by the server until packets are ready, so
   * the loop re-polls immediately. An HTTP 400 ("Session ID unknown") means
   * the session died server-side → full re-handshake.
   */
  private async pollLoop(): Promise<void> {
    while (!this.closed && !this.ws && this.sid) {
      try {
        const res = await this.fetchImpl(this.pollUrl(), {
          headers: { Authorization: `Bearer ${this.apiKey}` },
        });
        if (res.status === 400) {
          this.sid = null;
          this.scheduleReconnect();
          return;
        }
        const text = await res.text();
        this.parsePollResponse(text);
      } catch {
        if (!this.closed) {
          this.sid = null;
          this.scheduleReconnect();
        }
        return;
      }
    }
  }

  private parsePollResponse(text: string): void {
    // Engine.IO v4 batches packets separated by 0x1e (record separator).
    for (const packet of text.split(EIO_RECORD_SEPARATOR)) {
      if (packet) this.handlePacket(packet);
    }
  }
}
