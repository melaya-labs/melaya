/**
 * WebSocket streaming API.
 *
 * Each method returns a {@link MelayaStream} that is both an async-iterable
 * (`for await (const frame of stream)`) and an event emitter
 * (`stream.on("message", cb)`). Uses the global `WebSocket` by default;
 * inject one (e.g. the `ws` package) for Node < 22.
 */
import type { HttpClient } from "./client.js";
import type {
  SymbolQuery,
  OrderBookQuery,
  OhlcvQuery,
  TickerFrame,
  OrderBookFrame,
  TradeFrame,
  OhlcvFrame,
  LiquidationFrame,
} from "./types.js";

/** Minimal WebSocket surface shared by the browser, Node 22+ global, and `ws`. */
export interface WebSocketLike {
  addEventListener(type: string, listener: (ev: unknown) => void): void;
  close(): void;
  readyState: number;
}
export type WebSocketCtor = new (url: string) => WebSocketLike;

type StreamEvent = "message" | "open" | "close" | "error";

/**
 * A live stream of normalized frames. Iterate it, or attach listeners.
 *
 * @example
 * ```ts
 * const s = melaya.stream.ticker({ exchange: "binance", symbol: "BTC/USDT", market: "spot" });
 * for await (const t of s) console.log(t.last);
 * ```
 */
export class MelayaStream<T> implements AsyncIterable<T> {
  private ws: WebSocketLike;
  private buffer: T[] = [];
  private waiting: ((r: IteratorResult<T>) => void) | null = null;
  private closed = false;
  private listeners: Record<StreamEvent, Array<(arg: unknown) => void>> = {
    message: [], open: [], close: [], error: [],
  };

  constructor(url: string, WebSocketImpl: WebSocketCtor) {
    this.ws = new WebSocketImpl(url);
    this.ws.addEventListener("open", (ev) => this.emit("open", ev));
    this.ws.addEventListener("error", (ev) => this.emit("error", ev));
    this.ws.addEventListener("close", () => this.finish());
    this.ws.addEventListener("message", (ev) => {
      const raw = (ev as { data?: unknown }).data;
      let frame: T;
      try {
        const text = typeof raw === "string" ? raw : raw instanceof ArrayBuffer ? new TextDecoder().decode(raw) : String(raw);
        frame = JSON.parse(text) as T;
      } catch {
        return; // ignore non-JSON keep-alive frames
      }
      this.emit("message", frame);
      if (this.waiting) {
        const w = this.waiting;
        this.waiting = null;
        w({ value: frame, done: false });
      } else {
        this.buffer.push(frame);
      }
    });
  }

  /** Subscribe to stream events. Returns an unsubscribe function. */
  on(event: "message", cb: (frame: T) => void): () => void;
  on(event: "open" | "close" | "error", cb: (ev: unknown) => void): () => void;
  on(event: StreamEvent, cb: (arg: never) => void): () => void {
    this.listeners[event].push(cb as (arg: unknown) => void);
    return () => {
      this.listeners[event] = this.listeners[event].filter((l) => l !== cb);
    };
  }

  /** Close the underlying socket and end iteration. */
  close(): void {
    try { this.ws.close(); } catch { /* already closing */ }
    this.finish();
  }

  private emit(event: StreamEvent, arg: unknown): void {
    for (const l of this.listeners[event]) l(arg);
  }

  private finish(): void {
    if (this.closed) return;
    this.closed = true;
    this.emit("close", undefined);
    if (this.waiting) {
      const w = this.waiting;
      this.waiting = null;
      w({ value: undefined as unknown as T, done: true });
    }
  }

  [Symbol.asyncIterator](): AsyncIterator<T> {
    return {
      next: (): Promise<IteratorResult<T>> => {
        if (this.buffer.length > 0) {
          return Promise.resolve({ value: this.buffer.shift() as T, done: false });
        }
        if (this.closed) {
          return Promise.resolve({ value: undefined as unknown as T, done: true });
        }
        return new Promise<IteratorResult<T>>((resolve) => {
          this.waiting = resolve;
        });
      },
      return: (): Promise<IteratorResult<T>> => {
        this.close();
        return Promise.resolve({ value: undefined as unknown as T, done: true });
      },
    };
  }
}

export class StreamAPI {
  constructor(
    private readonly apiKey: string,
    private readonly wsUrl: string,
    private readonly WebSocketImpl: WebSocketCtor | undefined,
    private readonly http: HttpClient,
  ) {}

  /** Live ticker frames (fires only when the normalized ticker advances). */
  ticker(q: SymbolQuery): MelayaStream<TickerFrame> {
    return this.open<TickerFrame>("/ws/ticker", { ...q });
  }

  /** Live order-book frames. */
  orderbook(q: OrderBookQuery): MelayaStream<OrderBookFrame> {
    return this.open<OrderBookFrame>("/ws/orderbook", { ...q });
  }

  /** Live OHLCV candle frames. */
  ohlcv(q: OhlcvQuery): MelayaStream<OhlcvFrame> {
    return this.open<OhlcvFrame>("/ws/ohlcv", { ...q });
  }

  /** Live public-trade frames. */
  trades(q: SymbolQuery): MelayaStream<TradeFrame> {
    return this.open<TradeFrame>("/ws/public-trades", { ...q });
  }

  /** Cross-exchange liquidation firehose. Omit `exchange` for all venues. */
  liquidations(q: { exchange?: string } = {}): MelayaStream<LiquidationFrame> {
    return this.open<LiquidationFrame>("/ws/liquidations", { ...q });
  }

  // ── Private feeds (authenticated; ticket-minted) ──────────────────────────

  /**
   * Live strategy events for your account: cycle markers, agent messages,
   * approval requests, executions, and status changes across every strategy
   * you own. Mints a short-lived ticket, then opens `/ws/strategies`.
   *
   * @example
   * ```ts
   * const s = await melaya.stream.strategies();
   * for await (const ev of s) console.log(ev.type, ev.strategyId);
   * ```
   */
  async strategies(): Promise<MelayaStream<Record<string, unknown>>> {
    return this.openPrivate<Record<string, unknown>>("/ws/strategies", "strategies", {});
  }

  /**
   * Live private account feed for one connected exchange key (balance,
   * positions, your orders/fills). Pass the `apiKeyId` from
   * `melaya.account.keys()`. Mints a key-scoped ticket, then opens `/ws/private`.
   */
  async private(q: { exchange: string; market?: string; apiKeyId?: string; keyId?: string; symbol?: string }): Promise<MelayaStream<Record<string, unknown>>> {
    return this.openPrivate<Record<string, unknown>>("/ws/private", "private", {
      exchange: q.exchange,
      market: q.market,
      apiKeyId: q.apiKeyId,
      keyId: q.keyId,
      symbol: q.symbol,
    });
  }

  /** Mint a `wsTicket` for the given private stream, then open the socket with it. */
  private async openPrivate<T>(path: string, stream: string, body: Record<string, unknown>): Promise<MelayaStream<T>> {
    if (!this.WebSocketImpl) {
      throw new Error("Melaya: no global `WebSocket` found. Pass `WebSocket` in options (Node < 22, e.g. the `ws` package).");
    }
    const clean: Record<string, unknown> = { stream };
    for (const [k, v] of Object.entries(body)) if (v !== undefined && v !== null) clean[k] = v;
    const { wsTicket } = await this.http.post<{ wsTicket: string }>("/api/v1/private/private-ticket", clean);
    const u = new URL(path.replace(/^\//, ""), this.wsUrl.endsWith("/") ? this.wsUrl : this.wsUrl + "/");
    u.searchParams.set("wsTicket", wsTicket);
    return new MelayaStream<T>(u.toString(), this.WebSocketImpl);
  }

  private open<T>(path: string, params: Record<string, string | number | undefined>): MelayaStream<T> {
    if (!this.WebSocketImpl) {
      throw new Error("Melaya: no global `WebSocket` found. Pass `WebSocket` in options (Node < 22, e.g. the `ws` package).");
    }
    const u = new URL(path.replace(/^\//, ""), this.wsUrl.endsWith("/") ? this.wsUrl : this.wsUrl + "/");
    u.searchParams.set("apiKey", this.apiKey);
    for (const [k, v] of Object.entries(params)) {
      if (v !== undefined && v !== null) u.searchParams.set(k, String(v));
    }
    return new MelayaStream<T>(u.toString(), this.WebSocketImpl);
  }
}
