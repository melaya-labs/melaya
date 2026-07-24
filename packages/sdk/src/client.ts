/**
 * Core HTTP client + the Melaya entry point.
 *
 * Zero runtime dependencies: uses the global `fetch` (Node 18+, browsers) and
 * the global `WebSocket` (Node 22+, browsers). Both can be injected via
 * options for older runtimes.
 */
import { MarketAPI } from "./market.js";
import { AccountAPI } from "./account.js";
import { SimAPI } from "./sim.js";
import { StrategiesAPI } from "./strategies.js";
import { BacktestAPI } from "./backtest.js";
import { TradeAPI } from "./trade.js";
import { StreamAPI, type WebSocketCtor } from "./stream.js";
import { ProjectsAPI } from "./projects.js";
import { PipelinesAPI } from "./pipelines.js";
import { EvalsAPI } from "./evals.js";
import { MemoryAPI } from "./memory.js";
import { HitlAPI } from "./hitl.js";
import { CredentialsAPI } from "./credentials.js";
import { ConnectorsAPI } from "./connectors.js";
import { BillingAPI } from "./billing.js";
import { PhoneAPI } from "./phone.js";
import { TeamAPI } from "./team.js";
import { TemplatesAPI } from "./templates.js";
import { AssistantAPI } from "./assistant.js";
import { RunnerAPI } from "./runner.js";
import { MelayaEvents } from "./events.js";
import { AuthAPI } from "./auth.js";
import { MfaAPI } from "./mfa.js";
import { AccountsAPI } from "./accounts.js";
import { BugsAPI } from "./bugs.js";
import type { AIModel, OverviewSummary, PipelineCountByStatus, PipelineRun } from "./platform-types.js";

// ── Thin facades ──────────────────────────────────────────────────────────────

/**
 * Read-only AI model catalogue facade.
 *
 * `melaya.agents.models` exposes ONLY the model-catalogue read methods.
 * Secret CRUD (set / delete / test) stays exclusively on
 * `melaya.platform.credentials`.
 */
export class ModelsAPI {
  constructor(private readonly credentials: CredentialsAPI) {}

  /**
   * List available AI models across all configured providers.
   * Collapses 19+ provider fan-out into a parameterised query.
   *
   * @example
   * ```ts
   * const models = await melaya.agents.models.listModels();
   * ```
   */
  listModels(params?: { provider?: string; capability?: string }): Promise<AIModel[]> {
    return this.credentials.listModels(params);
  }
}

/**
 * Read-only platform overview facade.
 *
 * `melaya.platform.overview` exposes only the dashboard read methods from
 * `PipelinesAPI`. Mutation methods (create / run / remove / buildWithAI)
 * remain exclusively on `melaya.agents.pipelines`.
 */
export class OverviewAPI {
  constructor(private readonly pipelines: PipelinesAPI) {}

  /** Dashboard overview: usage stats, active strategies, recent runs. */
  overview(): Promise<OverviewSummary> {
    return this.pipelines.overview();
  }

  /** Count of pipeline runs grouped by status. */
  count(): Promise<PipelineCountByStatus> {
    return this.pipelines.count();
  }

  /**
   * Paginated list of pipeline runs.
   *
   * @example
   * ```ts
   * const runs = await melaya.platform.overview.list({ project: "my-project", limit: 20 });
   * ```
   */
  list(params?: Parameters<PipelinesAPI["list"]>[0]): Promise<PipelineRun[]> {
    return this.pipelines.list(params);
  }

  /** Most recent pipeline runs for a dashboard widget. */
  recent(): Promise<PipelineRun[]> {
    return this.pipelines.recent();
  }

  /**
   * Dashboard usage summary (pipeline count, RAG usage, plan limits).
   */
  usageSummary(): Promise<Record<string, unknown>> {
    return this.pipelines.usageSummary();
  }
}

export const DEFAULT_BASE_URL = "https://api.melaya.org";
export const DEFAULT_WS_URL = "wss://wss.melaya.org";

export interface MelayaOptions {
  /** Your Melaya API key (prefixed `mk_`). Create one at melaya.org → Settings → API Keys. */
  apiKey?: string;
  /** Session JWT returned by `platform.auth.login()`; mutually exclusive with `apiKey`. */
  sessionToken?: string;
  /** Override the REST base URL. Defaults to https://api.melaya.org */
  baseUrl?: string;
  /** Override the WebSocket base URL. Defaults to wss://wss.melaya.org */
  wsUrl?: string;
  /** Inject a `fetch` implementation (for Node < 18 or testing). */
  fetch?: typeof fetch;
  /** Inject a `WebSocket` constructor (for Node < 22, e.g. the `ws` package). */
  WebSocket?: WebSocketCtor;
  /**
   * Per-request timeout in milliseconds (default 30 000 ms).
   * Applied via AbortController to every HTTP call.
   */
  requestTimeoutMs?: number;
}

/** Thrown for non-2xx REST responses. */
export class MelayaError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly body?: unknown;
  constructor(message: string, status: number, code?: string, body?: unknown) {
    super(message);
    this.name = "MelayaError";
    this.status = status;
    this.code = code;
    this.body = body;
  }
}

type QueryValue = string | number | boolean | undefined | null;

const DEFAULT_TIMEOUT_MS = 30_000;
const MAX_GET_RETRIES = 2;

/** Internal HTTP client. Injects the API key on every call. */
export class HttpClient {
  private readonly timeoutMs: number;

  constructor(
    private readonly apiKey: string,
    private readonly baseUrl: string,
    private readonly fetchImpl: typeof fetch,
    timeoutMs?: number,
  ) {
    this.timeoutMs = timeoutMs ?? DEFAULT_TIMEOUT_MS;
  }

  private url(path: string, query?: Record<string, QueryValue>): string {
    const u = new URL(path.replace(/^\//, ""), this.baseUrl.endsWith("/") ? this.baseUrl : this.baseUrl + "/");
    // SECURITY: the API key is sent ONLY as `Authorization: Bearer <key>` (see
    // get/post/etc below). It must NEVER appear in the URL query string, where
    // it would leak into access logs, proxies, and the browser Referer header.
    if (query) {
      for (const [k, v] of Object.entries(query)) {
        if (v !== undefined && v !== null) u.searchParams.set(k, String(v));
      }
    }
    return u.toString();
  }

  /** Build an AbortSignal that fires after `this.timeoutMs`. */
  private timeoutSignal(): AbortSignal {
    return AbortSignal.timeout(this.timeoutMs);
  }

  /**
   * Execute a fetch call with a per-request timeout.
   * For idempotent GET requests, retries up to MAX_GET_RETRIES times on
   * network errors, 429 (with Retry-After), or 5xx responses.
   * POST / PUT / PATCH / DELETE are never retried.
   */
  private async fetchWithRetry(
    url: string,
    init: RequestInit,
    idempotent: boolean,
  ): Promise<Response> {
    let lastErr: unknown;
    const maxAttempts = idempotent ? 1 + MAX_GET_RETRIES : 1;

    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      // Fresh AbortSignal per attempt
      const signal = this.timeoutSignal();
      try {
        const res = await this.fetchImpl(url, { ...init, signal });

        // Success path
        if (res.ok || (res.status < 500 && res.status !== 429)) {
          return res;
        }

        // 429 — honour Retry-After if present
        if (res.status === 429 && attempt < maxAttempts - 1) {
          const retryAfter = res.headers.get("Retry-After");
          const delayMs = retryAfter
            ? (isNaN(Number(retryAfter)) ? 5000 : Number(retryAfter) * 1000)
            : this.backoffMs(attempt);
          await this.sleep(delayMs);
          continue;
        }

        // 5xx — backoff and retry if we have attempts left
        if (res.status >= 500 && attempt < maxAttempts - 1) {
          await this.sleep(this.backoffMs(attempt));
          continue;
        }

        return res; // surface the error response
      } catch (err) {
        lastErr = err;
        if (attempt < maxAttempts - 1) {
          await this.sleep(this.backoffMs(attempt));
        }
      }
    }

    throw lastErr;
  }

  /** Exponential backoff with ±20% jitter, capped at 10 s. */
  private backoffMs(attempt: number): number {
    const base = Math.min(500 * 2 ** attempt, 10_000);
    return base + base * 0.2 * (Math.random() * 2 - 1);
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  async get<T>(path: string, query?: Record<string, QueryValue>): Promise<T> {
    const res = await this.fetchWithRetry(
      this.url(path, query),
      { method: "GET", headers: { Authorization: `Bearer ${this.apiKey}` } },
      true,
    );
    return this.parse<T>(res);
  }

  async post<T>(path: string, body?: unknown): Promise<T> {
    const res = await this.fetchWithRetry(
      this.url(path),
      {
        method: "POST",
        headers: { Authorization: `Bearer ${this.apiKey}`, "Content-Type": "application/json" },
        body: body === undefined ? undefined : JSON.stringify(body),
      },
      false,
    );
    return this.parse<T>(res);
  }

  async put<T>(path: string, body?: unknown): Promise<T> {
    const res = await this.fetchWithRetry(
      this.url(path),
      {
        method: "PUT",
        headers: { Authorization: `Bearer ${this.apiKey}`, "Content-Type": "application/json" },
        body: body === undefined ? undefined : JSON.stringify(body),
      },
      false,
    );
    return this.parse<T>(res);
  }

  async patch<T>(path: string, body?: unknown): Promise<T> {
    const res = await this.fetchWithRetry(
      this.url(path),
      {
        method: "PATCH",
        headers: { Authorization: `Bearer ${this.apiKey}`, "Content-Type": "application/json" },
        body: body === undefined ? undefined : JSON.stringify(body),
      },
      false,
    );
    return this.parse<T>(res);
  }

  async delete<T>(path: string, query?: Record<string, QueryValue>): Promise<T> {
    const res = await this.fetchWithRetry(
      this.url(path, query),
      { method: "DELETE", headers: { Authorization: `Bearer ${this.apiKey}` } },
      false,
    );
    return this.parse<T>(res);
  }

  private async parse<T>(res: Response): Promise<T> {
    const text = await res.text();
    let data: unknown;
    try {
      data = text ? JSON.parse(text) : undefined;
    } catch {
      data = text;
    }
    if (!res.ok) {
      const code = (data as { error?: string } | undefined)?.error;
      throw new MelayaError(
        `Melaya API ${res.status}${code ? ` (${code})` : ""}`,
        res.status,
        code,
        data,
      );
    }
    // The API wraps every payload in an `{ ok, <data> }` envelope. A `false`
    // `ok` is a request-level failure (an unsupported per-venue operation, or
    // a cold venue that hasn't warmed yet) — surface it instead of returning
    // a silent null payload.
    if (data && typeof data === "object" && (data as { ok?: boolean }).ok === false) {
      const code = (data as { error?: string }).error;
      throw new MelayaError(
        `Melaya API request failed${code ? `: ${code}` : ""}`,
        res.status,
        code,
        data,
      );
    }
    return data as T;
  }
}

// ── Namespace type declarations ───────────────────────────────────────────────

/**
 * Grouped namespace for the trading plane.
 *
 * Primary API: `melaya.trading.<module>`
 */
export interface TradingNamespace {
  /** REST market-data + reference endpoints (public plane). */
  readonly market: MarketAPI;
  /** Authenticated account reads: connected keys, tier limits, usage. */
  readonly account: AccountAPI;
  /** Paper trading (sim broker): virtual balance, positions, and orders. */
  readonly sim: SimAPI;
  /** Launch, control, and inspect trading strategies (paper + live). */
  readonly strategies: StrategiesAPI;
  /** Live credentialed trading on a connected exchange (real funds). */
  readonly trade: TradeAPI;
  /** Historical backtests + parameter sweeps on the Rust engine. */
  readonly backtest: BacktestAPI;
  /** WebSocket streaming endpoints (public market data + private feeds). */
  readonly stream: StreamAPI;
}

/**
 * Grouped namespace for the agents plane.
 *
 * Primary API: `melaya.agents.<module>`
 */
export interface AgentsNamespace {
  /** Pipeline run overview, traces, and cron schedules. */
  readonly pipelines: PipelinesAPI;
  /** Run evaluations: quality/cost summaries, per-run verdicts, benchmarks. */
  readonly evals: EvalsAPI;
  /** Agent memory: constellation graph, per-run memory drill-down, crew memory. */
  readonly memory: MemoryAPI;
  /** Human-in-the-loop approval queue: list pending, approve, reject. */
  readonly hitl: HitlAPI;
  /** Assistant onboarding profile (get + set). */
  readonly assistant: AssistantAPI;
  /** Phone device control: pair, list, screen-tree, apps. */
  readonly phone: PhoneAPI;
  /**
   * Read-only AI model catalogue.
   * Call `melaya.agents.models.listModels()` to enumerate available models.
   * Secret CRUD stays exclusively on `melaya.platform.credentials`.
   */
  readonly models: ModelsAPI;
}

/**
 * Grouped namespace for the platform plane.
 *
 * Primary API: `melaya.platform.<module>`
 */
export interface PlatformNamespace {
  /** Authentication, registration, password, and session endpoints. */
  readonly auth: AuthAPI;
  /** TOTP MFA enrollment and status. */
  readonly mfa: MfaAPI;
  /** User profile, credits, GDPR export, and stored CEX-key lifecycle. */
  readonly accounts: AccountsAPI;
  /** Create and list agent projects. */
  readonly projects: ProjectsAPI;
  /** User-scoped credential storage, operator profile, and AI model catalogue. */
  readonly credentials: CredentialsAPI;
  /** Project-scoped connector credentials (per-project service keys). */
  readonly connectors: ConnectorsAPI;
  /** Billing: subscription status, Stripe checkout / portal, pricing plans. */
  readonly billing: BillingAPI;
  /** Project team management: members, roles, and invite links. */
  readonly team: TeamAPI;
  /** Pipeline templates: create, share, assign, and manage visibility. */
  readonly templates: TemplatesAPI;
  /**
   * Read-only pipeline / dashboard overview.
   * Exposes only dashboard reads: overview / count / list / recent / usageSummary.
   * Full pipeline CRUD stays on `melaya.agents.pipelines`.
   */
  readonly overview: OverviewAPI;
  /** Runner token management: mint, list, revoke `mel_run_` tokens. */
  readonly runner: RunnerAPI;
  /** User-facing bug reports, comments, and notifications. */
  readonly bugs: BugsAPI;
  /**
   * Platform real-time events over Socket.IO at `/api/v1/events`.
   * Subscribe to run updates, init-phase progress, HITL notifications,
   * and pipeline CRUD events.
   */
  readonly events: MelayaEvents;
}

/**
 * The Melaya client.
 *
 * Modules are accessible via three domain namespaces — the primary, documented
 * API — as well as via flat top-level accessors (maintained for backwards
 * compatibility):
 *
 * ```
 * melaya.trading.<module>   → trading plane
 * melaya.agents.<module>    → agentic / pipeline plane
 * melaya.platform.<module>  → platform management plane
 * ```
 *
 * @example
 * ```ts
 * import { Melaya } from "@melaya/sdk";
 * const melaya = new Melaya({ apiKey: process.env.MELAYA_API_KEY! });
 *
 * // ── Namespaced API (primary) ─────────────────────────────────────────────
 * // Trading
 * const t = await melaya.trading.market.ticker({ exchange: "binance", symbol: "BTC/USDT", market: "spot" });
 * const keys = await melaya.trading.account.keys();
 * for await (const book of melaya.trading.stream.orderbook({ exchange: "bybit", symbol: "BTC/USDT", limit: 20 })) { ... }
 *
 * // Agents
 * const runs = await melaya.agents.pipelines.list({ project: "my-project" });
 * const pending = await melaya.agents.hitl.pending();
 * const models = await melaya.agents.models.listModels();
 *
 * // Platform
 * const projects = await melaya.platform.projects.list();
 * await melaya.platform.credentials.set("openai", { value: "sk-..." });
 * melaya.platform.events.onRunUpdate("run-123", (e) => console.log(e.event_type));
 *
 * // ── Flat API (backwards-compatible aliases) ──────────────────────────────
 * const t2 = await melaya.market.ticker({ exchange: "binance", symbol: "BTC/USDT", market: "spot" });
 * const projects2 = await melaya.projects.list();
 * const pending2 = await melaya.hitl.pending();
 * ```
 */
export class Melaya {
  // ── Domain namespaces (primary API) ──────────────────────────────────────
  /**
   * Trading plane: market data, account, paper trading, strategies,
   * live trading, backtesting, and WebSocket streams.
   */
  readonly trading: TradingNamespace;
  /**
   * Agents plane: pipeline runs, HITL approvals, assistant profile,
   * phone device control, and AI model catalogue.
   */
  readonly agents: AgentsNamespace;
  /**
   * Platform plane: projects, credentials, connectors, billing, team,
   * templates, runner tokens, and real-time Socket.IO events.
   */
  readonly platform: PlatformNamespace;

  // ── Flat aliases (backwards-compatible) ──────────────────────────────────
  /** @deprecated Use `melaya.trading.market` instead. */
  readonly market: MarketAPI;
  /** @deprecated Use `melaya.trading.account` instead. */
  readonly account: AccountAPI;
  /** @deprecated Use `melaya.trading.sim` instead. */
  readonly sim: SimAPI;
  /** @deprecated Use `melaya.trading.strategies` instead. */
  readonly strategies: StrategiesAPI;
  /** @deprecated Use `melaya.trading.backtest` instead. */
  readonly backtest: BacktestAPI;
  /** @deprecated Use `melaya.trading.trade` instead. */
  readonly trade: TradeAPI;
  /** @deprecated Use `melaya.trading.stream` instead. */
  readonly stream: StreamAPI;
  /** @deprecated Use `melaya.platform.projects` instead. */
  readonly projects: ProjectsAPI;
  /** @deprecated Use `melaya.agents.pipelines` instead. */
  readonly pipelines: PipelinesAPI;
  /** @deprecated Use `melaya.agents.evals` instead. */
  readonly evals: EvalsAPI;
  /** @deprecated Use `melaya.agents.memory` instead. */
  readonly memory: MemoryAPI;
  /** @deprecated Use `melaya.agents.hitl` instead. */
  readonly hitl: HitlAPI;
  /** @deprecated Use `melaya.platform.credentials` instead. */
  readonly credentials: CredentialsAPI;
  /** @deprecated Use `melaya.platform.connectors` instead. */
  readonly connectors: ConnectorsAPI;
  /** @deprecated Use `melaya.platform.billing` instead. */
  readonly billing: BillingAPI;
  /** @deprecated Use `melaya.agents.phone` instead. */
  readonly phone: PhoneAPI;
  /** @deprecated Use `melaya.platform.team` instead. */
  readonly team: TeamAPI;
  /** @deprecated Use `melaya.platform.templates` instead. */
  readonly templates: TemplatesAPI;
  /** @deprecated Use `melaya.agents.assistant` instead. */
  readonly assistant: AssistantAPI;
  /** @deprecated Use `melaya.platform.runner` instead. */
  readonly runner: RunnerAPI;
  /** @deprecated Use `melaya.platform.auth` instead. */
  readonly auth: AuthAPI;
  /** @deprecated Use `melaya.platform.mfa` instead. */
  readonly mfa: MfaAPI;
  /** @deprecated Use `melaya.platform.accounts` instead. */
  readonly accounts: AccountsAPI;
  /** @deprecated Use `melaya.platform.bugs` instead. */
  readonly bugs: BugsAPI;
  /** @deprecated Use `melaya.platform.events` instead. */
  readonly events: MelayaEvents;

  constructor(opts: MelayaOptions) {
    if (!opts || (!opts.apiKey && !opts.sessionToken)) {
      throw new Error("Melaya: provide either `apiKey` or `sessionToken`.");
    }
    if (opts.apiKey && opts.sessionToken) {
      throw new Error("Melaya: provide `apiKey` or `sessionToken`, not both.");
    }
    if (opts.apiKey && !opts.apiKey.startsWith("mk_")) {
      throw new Error("Melaya: API keys must be prefixed `mk_`.");
    }
    const credential = opts.sessionToken ?? opts.apiKey!;
    const fetchImpl = opts.fetch ?? (globalThis.fetch as typeof fetch | undefined);
    if (!fetchImpl) {
      throw new Error("Melaya: no global `fetch` found. Pass `fetch` in options (Node < 18).");
    }
    const wsCtor = opts.WebSocket ?? (globalThis.WebSocket as WebSocketCtor | undefined);

    const http = new HttpClient(credential, opts.baseUrl ?? DEFAULT_BASE_URL, fetchImpl, opts.requestTimeoutMs);

    // ── Instantiate all modules ──────────────────────────────────────────────
    const market = new MarketAPI(http);
    const account = new AccountAPI(http);
    const sim = new SimAPI(http);
    const strategies = new StrategiesAPI(http);
    const backtest = new BacktestAPI(http);
    const trade = new TradeAPI(http);
    const stream = new StreamAPI(opts.apiKey ?? "", opts.wsUrl ?? DEFAULT_WS_URL, wsCtor, http);

    const projects = new ProjectsAPI(http);
    const pipelines = new PipelinesAPI(http);
    const evals = new EvalsAPI(http);
    const memory = new MemoryAPI(http);
    const hitl = new HitlAPI(http);
    const credentials = new CredentialsAPI(http);
    const connectors = new ConnectorsAPI(http);
    const billing = new BillingAPI(http);
    const phone = new PhoneAPI(http);
    const team = new TeamAPI(http);
    const templates = new TemplatesAPI(http);
    const assistant = new AssistantAPI(http);
    const runner = new RunnerAPI(http);
    const auth = new AuthAPI(http);
    const mfa = new MfaAPI(http);
    const accounts = new AccountsAPI(http);
    const bugs = new BugsAPI(http);
    const events = new MelayaEvents({
      baseUrl: opts.baseUrl ?? DEFAULT_BASE_URL,
      apiKey: credential,
      WebSocket: wsCtor,
      fetch: fetchImpl,
    });

    // ── Thin facades ─────────────────────────────────────────────────────────
    const models = new ModelsAPI(credentials);
    const overview = new OverviewAPI(pipelines);

    // ── Domain namespaces ────────────────────────────────────────────────────
    this.trading = { market, account, sim, strategies, trade, backtest, stream };
    this.agents = { pipelines, evals, memory, hitl, assistant, phone, models };
    this.platform = {
      auth,
      mfa,
      accounts,
      projects,
      credentials,
      connectors,
      billing,
      team,
      templates,
      overview,
      runner,
      bugs,
      events,
    };

    // ── Flat aliases ─────────────────────────────────────────────────────────
    this.market = market;
    this.account = account;
    this.sim = sim;
    this.strategies = strategies;
    this.backtest = backtest;
    this.trade = trade;
    this.stream = stream;
    this.projects = projects;
    this.pipelines = pipelines;
    this.evals = evals;
    this.memory = memory;
    this.hitl = hitl;
    this.credentials = credentials;
    this.connectors = connectors;
    this.billing = billing;
    this.phone = phone;
    this.team = team;
    this.templates = templates;
    this.assistant = assistant;
    this.runner = runner;
    this.auth = auth;
    this.mfa = mfa;
    this.accounts = accounts;
    this.bugs = bugs;
    this.events = events;
  }
}
