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

export const DEFAULT_BASE_URL = "https://api.melaya.org";
export const DEFAULT_WS_URL = "wss://wss.melaya.org";

export interface MelayaOptions {
  /** Your Melaya API key (prefixed `mk_`). Create one at melaya.org → Settings → API Keys. */
  apiKey: string;
  /** Override the REST base URL. Defaults to https://api.melaya.org */
  baseUrl?: string;
  /** Override the WebSocket base URL. Defaults to wss://wss.melaya.org */
  wsUrl?: string;
  /** Inject a `fetch` implementation (for Node < 18 or testing). */
  fetch?: typeof fetch;
  /** Inject a `WebSocket` constructor (for Node < 22, e.g. the `ws` package). */
  WebSocket?: WebSocketCtor;
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

/** Internal HTTP client. Injects the API key on every call. */
export class HttpClient {
  constructor(
    private readonly apiKey: string,
    private readonly baseUrl: string,
    private readonly fetchImpl: typeof fetch,
  ) {}

  private url(path: string, query?: Record<string, QueryValue>): string {
    const u = new URL(path.replace(/^\//, ""), this.baseUrl.endsWith("/") ? this.baseUrl : this.baseUrl + "/");
    u.searchParams.set("apiKey", this.apiKey);
    if (query) {
      for (const [k, v] of Object.entries(query)) {
        if (v !== undefined && v !== null) u.searchParams.set(k, String(v));
      }
    }
    return u.toString();
  }

  async get<T>(path: string, query?: Record<string, QueryValue>): Promise<T> {
    const res = await this.fetchImpl(this.url(path, query), {
      method: "GET",
      headers: { Authorization: `Bearer ${this.apiKey}` },
    });
    return this.parse<T>(res);
  }

  async post<T>(path: string, body?: unknown): Promise<T> {
    const res = await this.fetchImpl(this.url(path), {
      method: "POST",
      headers: { Authorization: `Bearer ${this.apiKey}`, "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    return this.parse<T>(res);
  }

  async delete<T>(path: string, query?: Record<string, QueryValue>): Promise<T> {
    const res = await this.fetchImpl(this.url(path, query), {
      method: "DELETE",
      headers: { Authorization: `Bearer ${this.apiKey}` },
    });
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

/**
 * The Melaya client.
 *
 * @example
 * ```ts
 * import { Melaya } from "@melaya/sdk";
 * const melaya = new Melaya({ apiKey: process.env.MELAYA_API_KEY! });
 * const t = await melaya.market.ticker({ exchange: "binance", symbol: "BTC/USDT", market: "spot" });
 * ```
 */
export class Melaya {
  /** REST market-data + reference endpoints (public plane). */
  readonly market: MarketAPI;
  /** Authenticated account reads: connected keys, tier limits, usage. */
  readonly account: AccountAPI;
  /** Paper trading (sim broker): virtual balance, positions, and orders. */
  readonly sim: SimAPI;
  /** Launch, control, and inspect trading strategies (paper + live). */
  readonly strategies: StrategiesAPI;
  /** Historical backtests + parameter sweeps on the Rust engine. */
  readonly backtest: BacktestAPI;
  /** Live credentialed trading on a connected exchange (real funds). */
  readonly trade: TradeAPI;
  /** WebSocket streaming endpoints (public market data + private feeds). */
  readonly stream: StreamAPI;

  constructor(opts: MelayaOptions) {
    if (!opts?.apiKey) throw new Error("Melaya: `apiKey` is required (create one at melaya.org → Settings → API Keys).");
    if (!opts.apiKey.startsWith("mk_")) {
      throw new Error("Melaya: API keys must be prefixed `mk_`.");
    }
    const fetchImpl = opts.fetch ?? (globalThis.fetch as typeof fetch | undefined);
    if (!fetchImpl) {
      throw new Error("Melaya: no global `fetch` found. Pass `fetch` in options (Node < 18).");
    }
    const wsCtor = opts.WebSocket ?? (globalThis.WebSocket as WebSocketCtor | undefined);

    const http = new HttpClient(opts.apiKey, opts.baseUrl ?? DEFAULT_BASE_URL, fetchImpl);
    this.market = new MarketAPI(http);
    this.account = new AccountAPI(http);
    this.sim = new SimAPI(http);
    this.strategies = new StrategiesAPI(http);
    this.backtest = new BacktestAPI(http);
    this.trade = new TradeAPI(http);
    this.stream = new StreamAPI(opts.apiKey, opts.wsUrl ?? DEFAULT_WS_URL, wsCtor, http);
  }
}
