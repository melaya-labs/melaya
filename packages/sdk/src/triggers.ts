/**
 * Event triggers API — read, diagnose and dry-run the triggers that start
 * your pipelines from outside events (webhooks, streams, polls, push, engine).
 *
 * Covers the `/api/v1/private/triggers/*` surface: list and inspect triggers,
 * read their delivery receipts, verdict stats, pending approvals and the live
 * event log, dry-run an event, and check or test a poll trigger. Every read is
 * scoped to the caller's own triggers server-side.
 *
 * Create, update, delete and secret rotation are deliberately not on REST:
 * push consent and autonomy grants are enforced in the Agent Builder and the
 * MCP server, so manage triggers there.
 *
 * The API key is always sent as `Authorization: Bearer <key>` by the shared
 * HttpClient, never in the query string.
 */
import { MelayaError, type HttpClient } from "./client.js";

const enc = encodeURIComponent;

/** Trigger kinds. `wss` is a stream source (WebSocket or Server-Sent Events). */
export type TriggerKind = "webhook" | "wss" | "engine" | "poll" | "push" | (string & {});

/** Where one event came from. `test` is a dry-run fire from `test()`. */
export type TriggerEventSource = "webhook" | "wss" | "engine" | "poll" | "push" | "test" | (string & {});

/** Outcome of one event through the trigger pipeline. */
export type TriggerVerdict =
  | "accepted"
  | "duplicate"
  | "rejected"
  | "rate_limited"
  | "filtered"
  | "decided"
  | "dispatched"
  | "skipped"
  | "failed"
  | (string & {});

/** One event trigger. Secrets are never included. */
export interface TriggerRecord {
  id: string;
  publicId: string;
  name: string;
  kind: TriggerKind;
  project: string;
  pipelineName: string;
  enabled: boolean;
  /** Why the trigger was paused automatically, or null. */
  pausedReason: string | null;
  signingScheme: "melaya" | "stripe" | "github" | "slack" | (string & {});
  sourceId: string | null;
  /** Prefilter, decide, routes, action, poll / push / engine settings. */
  config: Record<string, unknown>;
  maxEventsPerMin: number;
  maxRunsPerDay: number;
  maxConcurrentRuns: number;
  consecutiveFailures: number;
  lastEventAt: string | null;
  createdAt: string;
  updatedAt: string;
  /** Public URL to send events to (webhook triggers only), else null. */
  webhookUrl: string | null;
  /** False when you lost access to the trigger's project (runs are paused). */
  projectAccess: boolean;
}

/** One delivery receipt: what happened to one event. */
export interface TriggerDelivery {
  id: string;
  triggerId: string;
  eventId: string;
  source: TriggerEventSource;
  receivedAt: string;
  verdict: TriggerVerdict;
  /** Decision answers from System One, when the trigger has a decide step. */
  decision: unknown;
  action: string | null;
  runId: string | null;
  detail: string | null;
  latencyMs: number | null;
  /** Per-stage milliseconds: ingress, pickup, receipt, prefilter, decide, act, total. */
  timings: Record<string, number> | null;
  /** Read-only tool_call output, up to 2 KB, redacted. */
  resultExcerpt: string | null;
  /** Stream delivery count (above 1 means redelivered after a crash). */
  redelivered: number | null;
}

/** Verdict counts and latency percentiles over a window. */
export interface TriggerStats {
  hours: number;
  byVerdict: Record<string, { n: number; p50: number | null; p95: number | null }>;
  /** Events dropped by the prefilter (no receipt); null when the counter is unavailable. */
  filtered: number | null;
  /** True when the window held more receipts than the stats sample (latest 5,000). */
  sampled: boolean;
}

/** A tool call of this trigger waiting for a human decision. */
export interface TriggerPendingApproval {
  requestId: string;
  triggerId: string;
  deliveryId: string;
  eventId: string;
  source: TriggerEventSource;
  service: string;
  tool: string;
  argsPreview: string;
  /** Epoch ms. */
  createdAt: number;
  /** Epoch ms. */
  expiresAt: number;
}

/** Result of a dry-run fire. `reason` is set when the event was not accepted
 *  (`disabled`, `project_access_lost`, `rate_limited`, `unavailable`, ...). */
export interface TriggerTestResult {
  accepted: boolean;
  eventId: string;
  reason?: string;
}

/** One entry of the live event log, including outcomes with no receipt. */
export interface TriggerLiveEvent {
  triggerId: string;
  deliveryId: string | null;
  eventId: string;
  source: TriggerEventSource;
  verdict: TriggerVerdict;
  action?: string | null;
  runId?: string | null;
  detail?: string | null;
  latencyMs?: number | null;
  /** Read-only tool_call result, up to 2 KB, redacted. */
  resultExcerpt?: string | null;
  /** Epoch ms. Pass the newest one you have seen as `since` to poll. */
  at: number;
}

/** Response of `events()`. */
export interface TriggerEventsResult {
  events: TriggerLiveEvent[];
  /** Log entries scanned to build this page. */
  scanned: number;
  retention: { maxEvents: number; ttlSec: number };
}

/** Params for `events()`. */
export interface TriggerEventsParams {
  /** Only events of this trigger. */
  triggerId?: string;
  /** Only events after this time (epoch ms). */
  since?: number;
  /** Only these verdicts. */
  verdicts?: TriggerVerdict[];
  /** Max events (1-200). Defaults to 50 server-side. */
  limit?: number;
}

/** Params for `list()`. */
export interface TriggerListParams {
  project?: string;
  pipelineName?: string;
}

/** Runtime state of a poll trigger. */
export interface TriggerPollStatus {
  /** False when the poll runtime row does not exist yet (see `pollSync()`). */
  synced: boolean;
  status: string;
  lastError: string | null;
  lastPolledAt: string | null;
  nextPollAt: string | null;
  armed: boolean;
  /** True until the first poll records the current items as the baseline. */
  baselinePending: boolean;
  seenCount: number;
  itemsPublished: number;
  consecutiveErrors: number;
  requestedIntervalSec: number;
  effectiveIntervalSec: number;
  tierFloorSec: number;
}

/** Result of a dry poll. Nothing is published and no state changes. */
export type TriggerPollTestResult =
  | {
      dry: true;
      ok: true;
      /** Items the tool returned. */
      found: number;
      /** True on the first poll after arming: items are recorded, none published. */
      baseline: boolean;
      wouldPublish: number;
      /** Up to 5 items with a redacted preview. */
      items: { id: string; preview: string }[];
      /** First item as an event payload (use it with `test()`). */
      samplePayload: unknown;
    }
  | { dry: true; ok: false; error: string };

/** Result of `pollNow()`. */
export interface TriggerPollNowResult {
  dry: false;
  /** True when the poll was made due now. */
  queued: boolean;
}

/** Result of `pollSync()`. */
export interface TriggerPollSyncResult {
  result: string;
}

/** Trigger presets available to the caller. */
export interface TriggerPresetsResult {
  tier: string;
  tierFloorSec: number;
  presets: Array<Record<string, unknown> & {
    id: string;
    kind: TriggerKind;
    available: boolean;
    unavailableReason: "connector_missing" | "tier" | "setup_pending" | null;
  }>;
  beta: { allowed: boolean; minTier: string };
}

/** Plan caps and usage. */
export interface TriggerLimits {
  tierClass: string;
  beta: { allowed: boolean; minTier: string };
  triggers: { used: number; cap: number };
  sources: { used: number; cap: number };
  eventsPerMin: { perUser: number; perTriggerMax: number; perTriggerDefault: number };
  pollIntervalFloorSec: number;
  approvalTtlSec: { min: number; max: number; default: number };
}

/** One stream source (WebSocket or Server-Sent Events). */
export interface TriggerSource {
  id: string;
  name: string;
  url: string;
  authHeaderName: string | null;
  /** True when a credential is stored (the value is never returned). */
  hasAuth: boolean;
  subscribeFrame: unknown;
  eventIdPath: string | null;
  enabled: boolean;
  status: string;
  lastError: string | null;
  lastConnectedAt: string | null;
  droppedFrames: number;
  createdAt: string;
  connectorService: string | null;
  transport: "ws" | "sse";
}

export class TriggersAPI {
  constructor(private readonly http: HttpClient) {}

  /**
   * List your event triggers, newest first.
   *
   * @example
   * ```ts
   * const triggers = await melaya.agents.triggers.list({ project: "acme" });
   * ```
   */
  async list(params?: TriggerListParams): Promise<TriggerRecord[]> {
    return this.http.get<TriggerRecord[]>("/api/v1/private/triggers", {
      project: params?.project,
      pipelineName: params?.pipelineName,
    });
  }

  /** One trigger with its config. An unknown id throws a `MelayaError` with status 404. */
  async get(id: string): Promise<TriggerRecord> {
    return this.http.get<TriggerRecord>(`/api/v1/private/triggers/${enc(id)}`);
  }

  /**
   * Recent delivery receipts of a trigger, newest first: verdict, decision,
   * action, run id, timings and result excerpt.
   *
   * @param limit 1-200. Defaults to 50 server-side.
   */
  async deliveries(id: string, params?: { limit?: number }): Promise<TriggerDelivery[]> {
    return this.http.get<TriggerDelivery[]>(`/api/v1/private/triggers/${enc(id)}/deliveries`, {
      limit: params?.limit,
    });
  }

  /**
   * Delivery counts by verdict over a window.
   *
   * @param hours 1-168. Defaults to 24 server-side.
   */
  async stats(id: string, params?: { hours?: number }): Promise<TriggerStats> {
    return this.http.get<TriggerStats>(`/api/v1/private/triggers/${enc(id)}/stats`, {
      hours: params?.hours,
    });
  }

  /** Approvals still waiting on this trigger's runs and tool calls. Decide them in the Melaya app. */
  async pendingApprovals(id: string): Promise<TriggerPendingApproval[]> {
    return this.http.get<TriggerPendingApproval[]>(`/api/v1/private/triggers/${enc(id)}/approvals`);
  }

  /**
   * Dry-run one event. Prefilter, decide and routing run for real; the action
   * never executes (no run, no write). Rate limits still apply.
   *
   * @example
   * ```ts
   * const { accepted, eventId } = await melaya.agents.triggers.test(id, { amount: 120 });
   * const { events } = await melaya.agents.triggers.events({ triggerId: id });
   * ```
   */
  async test(id: string, payload?: unknown): Promise<TriggerTestResult> {
    return this.http.post<TriggerTestResult>(`/api/v1/private/triggers/${enc(id)}/test`, { payload });
  }

  /**
   * Recent live trigger events, newest first (last 500 events / 24 h). Includes
   * outcomes that write no receipt: filtered, shed and ingress rejections.
   * Poll with `since` set to the newest `at` you have seen.
   */
  async events(params?: TriggerEventsParams): Promise<TriggerEventsResult> {
    return this.http.get<TriggerEventsResult>("/api/v1/private/triggers/events", {
      triggerId: params?.triggerId,
      since: params?.since,
      verdicts: params?.verdicts?.length ? params.verdicts.join(",") : undefined,
      limit: params?.limit,
    });
  }

  /** Runtime state of a poll trigger: status, last error, next poll, intervals. */
  async pollStatus(triggerId: string): Promise<TriggerPollStatus> {
    return this.http.get<TriggerPollStatus>(`/api/v1/private/triggers/${enc(triggerId)}/poll`);
  }

  /**
   * Dry poll: calls the tool now and returns what it found and would publish.
   * Nothing is published and no state changes. A tool failure is returned as
   * `{ dry: true, ok: false, error }`, not thrown.
   */
  async pollTest(triggerId: string): Promise<TriggerPollTestResult> {
    try {
      return await this.http.post<TriggerPollTestResult>(
        `/api/v1/private/triggers/${enc(triggerId)}/poll/test`,
        { dry: true },
      );
    } catch (err) {
      // The shared client throws on any `{ ok: false }` body; a failed dry poll
      // is a normal 200 result here, so hand it back as-is.
      const body = err instanceof MelayaError ? (err.body as { dry?: unknown } | undefined) : undefined;
      if (err instanceof MelayaError && err.status < 300 && body && body.dry === true) {
        return body as TriggerPollTestResult;
      }
      throw err;
    }
  }

  /** Make a real poll due now. The trigger must be enabled. */
  async pollNow(triggerId: string): Promise<TriggerPollNowResult> {
    return this.http.post<TriggerPollNowResult>(`/api/v1/private/triggers/${enc(triggerId)}/poll/test`, {
      dry: false,
    });
  }

  /** Re-create a poll trigger's runtime row from its saved config (re-arms a poller that never started). */
  async pollSync(triggerId: string): Promise<TriggerPollSyncResult> {
    return this.http.post<TriggerPollSyncResult>(`/api/v1/private/triggers/${enc(triggerId)}/poll/sync`);
  }

  /** Trigger presets for your connected services and plan. */
  async presets(): Promise<TriggerPresetsResult> {
    return this.http.get<TriggerPresetsResult>("/api/v1/private/triggers/presets");
  }

  /** Plan caps and usage: triggers, sources, events per minute, poll floor, approval TTL. */
  async limits(): Promise<TriggerLimits> {
    return this.http.get<TriggerLimits>("/api/v1/private/triggers/limits");
  }

  /** Your WebSocket / Server-Sent Events stream sources. */
  async sources(): Promise<TriggerSource[]> {
    return this.http.get<TriggerSource[]>("/api/v1/private/triggers/sources");
  }
}
