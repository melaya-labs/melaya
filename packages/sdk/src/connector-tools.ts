/**
 * Connector Tools API — the same connector tool surface the MCP server
 * exposes (discover, describe, test, connect, call), over plain REST.
 *
 * Covers `/api/v1/private/connector-tools/*`. Do not confuse this with
 * `platform.connectors` / `platform.credentials`, which store project- and
 * account-scoped connector CREDENTIALS; this module CALLS the tools those
 * credentials unlock. No method here ever accepts or returns a credential
 * value.
 *
 * Write policy, decided per call:
 * - Read tools always run immediately.
 * - `approval: "required"` (the default) stages the write as the same
 *   approval card the Melaya Assistant raises; the caller polls
 *   {@link ConnectorToolsAPI.callStatus} (or uses
 *   {@link ConnectorToolsAPI.callAndWait}) until the user decides in the
 *   Melaya app, and the write runs exactly once, on the first poll after
 *   approval.
 * - `approval: "none"` runs the write immediately and is still audit-logged.
 * - Tools that move money or trade are refused under BOTH approval modes —
 *   those only ever run from the Melaya app.
 *
 * @example
 * ```ts
 * const { tools } = await melaya.agents.connectorTools.search("unread email");
 * const outcome = await melaya.agents.connectorTools.callAndWait("gmail_send", { to: "a@b.c" });
 * if (outcome.status === "done" && outcome.ok) console.log(outcome.result);
 * ```
 */
import type { HttpClient } from "./client.js";
import { MelayaError } from "./client.js";

const enc = encodeURIComponent;
const BASE = "/api/v1/private/connector-tools";

/** Approval mode for a write call. `"required"` (default) stages the
 *  Assistant's approval card; `"none"` runs the write immediately. */
export type ConnectorApproval = "required" | "none";

/** One parameter of a connector tool, as reported by `describe()` / `search()`. */
export interface ConnectorToolParam {
  type?: string;
  required?: boolean;
  default?: unknown;
  description?: string;
}

/** A discoverable connector tool. */
export interface ConnectorToolInfo {
  name: string;
  service: string;
  description: string;
  readOnly: boolean;
  /** True for a write tool the money/trading classifier flags; always
   *  refused by `call()`, under both approval modes. */
  movesMoney: boolean;
  params: Record<string, ConnectorToolParam>;
}

/** `services()` result: connected services + built-in tool set + per-service counts. */
export interface ConnectorServicesResult {
  services: string[];
  builtIn: string;
  toolCounts: Record<string, { readTools: number; writeTools: number }>;
}

/** `search()` result: tools ranked against plain business keywords. */
export interface ConnectorSearchResult {
  query: string;
  services: string[];
  tools: ConnectorToolInfo[];
}

/** `test()` result: outcome of testing the STORED credential for a service. */
export interface ConnectorTestResult {
  service: string;
  success: boolean;
  message: string;
}

/** `connect()` result: how to connect a service. Never carries a secret. */
export interface ConnectorConnectResult {
  service: string;
  kind: "oauth" | "oauth_unavailable" | "interactive_login" | "api_key";
  /** Present when `kind === "oauth"`: open this URL to authorize. */
  authorizationUrl?: string;
  /** Present for `interactive_login` / `api_key`: the Connectors page. */
  connectUrl?: string;
  message: string;
}

/** `call()` result when the tool ran immediately (a read, or a write with
 *  `approval: "none"`). */
export interface ConnectorCallDone {
  status: "done";
  tool: string;
  readOnly: boolean;
  result: string;
}

/** `call()` result when a write was staged for approval (HTTP 202 — a
 *  success, not an error). Poll {@link ConnectorToolsAPI.callStatus}. */
export interface ConnectorCallPending {
  status: "pending_approval";
  tool: string;
  requestId: string;
  message: string;
}

export type ConnectorCallResult = ConnectorCallDone | ConnectorCallPending;

/** `callStatus()` result: still waiting on a decision, or already claimed
 *  and running (a concurrent poll got there first). */
export interface ConnectorCallStatusWaiting {
  requestId: string;
  tool?: string;
  status: "pending" | "running" | "expired";
}

/** `callStatus()` result: the staged write ran (or failed) after approval. */
export interface ConnectorCallStatusDone {
  requestId: string;
  tool?: string;
  status: "done";
  ok: boolean;
  result?: string;
  error?: string;
}

/** `callStatus()` result: the user rejected the approval card. */
export interface ConnectorCallStatusRejected {
  requestId: string;
  tool?: string;
  status: "rejected";
  reason?: string;
}

export type ConnectorCallStatus =
  | ConnectorCallStatusWaiting
  | ConnectorCallStatusDone
  | ConnectorCallStatusRejected;

/** Options for one `call()`. */
export interface ConnectorCallOptions {
  /** `"required"` (default) stages an approval card; `"none"` runs immediately. */
  approval?: ConnectorApproval;
}

/** Options for `callAndWait()`. */
export interface ConnectorCallAndWaitOptions extends ConnectorCallOptions {
  /** Delay between polls of `callStatus()`, in ms. Default 3 000. */
  pollIntervalMs?: number;
  /** Give up waiting for a decision after this many ms. Default 600 000 (10 min).
   *  The staged request itself is untouched — a later `callStatus()` call
   *  can still pick up the real outcome once the user decides. */
  timeoutMs?: number;
}

/**
 * The final outcome of {@link ConnectorToolsAPI.callAndWait}: either the tool
 * ran immediately, or the polled outcome of a staged approval (approved,
 * rejected, or the request expired / the wait timed out).
 */
export interface ConnectorCallOutcome {
  status: "done" | "rejected" | "expired";
  tool: string;
  /** Absent when the tool ran immediately — no approval was ever staged. */
  requestId?: string;
  ok?: boolean;
  result?: string;
  error?: string;
  reason?: string;
}

export class ConnectorToolsAPI {
  constructor(private readonly http: HttpClient) {}

  /** Connected services, the always-on `melaya_core` built-ins, and a
   *  read/write tool count per service. */
  async services(): Promise<ConnectorServicesResult> {
    return this.http.get<ConnectorServicesResult>(`${BASE}/services`);
  }

  /**
   * Discover tools by plain business keywords (e.g. `"unread email"`,
   * `"refund a charge"`) — not tool names. `limit` is clamped server-side to
   * 1–50 (default 15).
   */
  async search(q: string, opts?: { limit?: number }): Promise<ConnectorSearchResult> {
    return this.http.get<ConnectorSearchResult>(`${BASE}/search`, { q, limit: opts?.limit });
  }

  /** Full description + parameters for one tool. Throws a `MelayaError`
   *  (status 404) when the tool is unknown, or not unlocked by anything
   *  connected. */
  async describe(tool: string): Promise<ConnectorToolInfo> {
    return this.http.get<ConnectorToolInfo>(`${BASE}/tools/${enc(tool)}`);
  }

  /** Test the STORED credential for a service (no secret is sent). Throws a
   *  `MelayaError` with status 504 / code `"timeout"` if the service takes
   *  longer than 30 s to answer. */
  async test(service: string): Promise<ConnectorTestResult> {
    return this.http.post<ConnectorTestResult>(`${BASE}/test`, { service });
  }

  /**
   * Start connecting a service. OAuth services return an `authorizationUrl`
   * the user opens; everything else points at the Melaya Connectors page.
   * Never accepts or returns a secret.
   *
   * `kind: "oauth_unavailable"` is a normal outcome (the server answers it
   * with HTTP 503, since nothing can be connected right now) — this method
   * returns it like any other result instead of throwing.
   */
  async connect(service: string): Promise<ConnectorConnectResult> {
    try {
      return await this.http.post<ConnectorConnectResult>(`${BASE}/connect`, { service });
    } catch (e) {
      if (e instanceof MelayaError && e.status === 503 && (e.body as { kind?: string } | undefined)?.kind === "oauth_unavailable") {
        return e.body as ConnectorConnectResult;
      }
      throw e;
    }
  }

  /**
   * Call a connector tool. Reads run immediately. Writes default to
   * `approval: "required"` (a 202 staging the Assistant's approval card —
   * this is a success, not an error); pass `approval: "none"` to run a write
   * immediately (still audit-logged). Tools that move money or trade are
   * refused under both modes and throw a `MelayaError`
   * (`money_moving_requires_app_approval`).
   */
  async call(
    tool: string,
    args?: Record<string, unknown>,
    opts?: ConnectorCallOptions,
  ): Promise<ConnectorCallResult> {
    return this.http.post<ConnectorCallResult>(`${BASE}/call`, {
      tool,
      args: args ?? {},
      approval: opts?.approval ?? "required",
    });
  }

  /** Outcome of a staged write started by `call()`. Throws a `MelayaError`
   *  (status 404) once the request id is unknown or has expired. */
  async callStatus(requestId: string): Promise<ConnectorCallStatus> {
    return this.http.get<ConnectorCallStatus>(`${BASE}/calls/${enc(requestId)}`);
  }

  /**
   * Call a tool and wait for its final outcome: if it runs immediately, that
   * result is the outcome; if it is staged for approval, this polls
   * `callStatus()` (every `pollIntervalMs`, default 3 000) until the user
   * decides, the request expires, or `timeoutMs` (default 600 000 / 10 min)
   * elapses. A timeout stops waiting here only — the staged request is
   * untouched, so a later `callStatus()` call can still pick up the real
   * decision.
   *
   * @example
   * ```ts
   * const outcome = await melaya.agents.connectorTools.callAndWait("gmail_send", { to: "a@b.c" });
   * if (outcome.status === "done" && outcome.ok) console.log(outcome.result);
   * else if (outcome.status === "rejected") console.log("rejected:", outcome.reason);
   * ```
   */
  async callAndWait(
    tool: string,
    args?: Record<string, unknown>,
    opts?: ConnectorCallAndWaitOptions,
  ): Promise<ConnectorCallOutcome> {
    const first = await this.call(tool, args, { approval: opts?.approval });
    if (first.status === "done") {
      return { status: "done", tool, ok: true, result: first.result };
    }

    const { requestId } = first;
    const pollIntervalMs = opts?.pollIntervalMs ?? 3_000;
    const deadline = Date.now() + (opts?.timeoutMs ?? 600_000);

    for (;;) {
      const s = await this.callStatus(requestId);
      if (s.status === "done") {
        return { status: "done", tool, requestId, ok: s.ok, result: s.result, error: s.error };
      }
      if (s.status === "rejected") {
        return { status: "rejected", tool, requestId, reason: s.reason };
      }
      if (s.status === "expired") {
        return { status: "expired", tool, requestId };
      }
      const remaining = deadline - Date.now();
      if (remaining <= 0) {
        return { status: "expired", tool, requestId, error: "callAndWait timed out waiting for a decision" };
      }
      await sleep(Math.min(pollIntervalMs, remaining));
    }
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
