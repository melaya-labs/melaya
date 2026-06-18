/**
 * Shared types for the Melaya SDK.
 *
 * Response shapes mirror the Melaya engine's *normalized* output — the same
 * fields are present regardless of which of the 70+ venues served the data.
 * All response types allow extra fields (`[k: string]: unknown`) so the SDK
 * never breaks when the API returns additional information.
 */

/** Market kind. Venues use different names (swap/perp/linear/etc.); Melaya
 *  normalizes the common ones. Pass the value the venue is listed under. */
export type Market = "spot" | "swap" | "future" | "futures" | "perpetuals" | "option" | (string & {});

// ── REST inputs ─────────────────────────────────────────────────────────────

export interface SymbolQuery {
  /** Exchange id, e.g. "binance", "bybit", "okx". See market.listExchanges(). */
  exchange: string;
  /** Unified symbol, e.g. "BTC/USDT". */
  symbol: string;
  /** Market kind. Defaults to "spot" server-side when omitted. */
  market?: Market;
}

export interface OrderBookQuery extends SymbolQuery {
  /** Depth (levels per side). */
  limit?: number;
}

export interface OhlcvQuery extends SymbolQuery {
  /** Candle timeframe, e.g. "1m", "5m", "1h", "1d". */
  timeframe: string;
  /** Number of candles to return. */
  limit?: number;
}

export interface ExchangeQuery {
  exchange: string;
}

// ── Normalized REST responses ───────────────────────────────────────────────

export interface Ticker {
  exchange?: string;
  symbol?: string;
  bid?: number;
  ask?: number;
  last?: number;
  high?: number;
  low?: number;
  baseVolume?: number;
  quoteVolume?: number;
  /** Milliseconds since epoch. */
  timestamp?: number;
  [k: string]: unknown;
}

/** A single order-book level: [price, amount]. */
export type BookLevel = [price: number, amount: number];

export interface OrderBook {
  exchange?: string;
  symbol?: string;
  bids: BookLevel[];
  asks: BookLevel[];
  timestamp?: number;
  [k: string]: unknown;
}

/** A single OHLCV candle: [timestamp, open, high, low, close, volume]. */
export type Candle = [timestamp: number, open: number, high: number, low: number, close: number, volume: number];

export interface Trade {
  id?: string;
  timestamp?: number;
  side?: "buy" | "sell";
  price?: number;
  amount?: number;
  [k: string]: unknown;
}

export interface Liquidation {
  exchange: string;
  symbol?: string;
  side?: "buy" | "sell";
  price?: number;
  amount?: number;
  notional?: number;
  timestamp?: number;
  [k: string]: unknown;
}

export interface ExchangeStatus {
  exchange: string;
  status: "ok" | "maintenance" | "degraded" | (string & {});
  [k: string]: unknown;
}

export interface ExchangeInfo {
  id: string;
  /** Human-readable name (the API field is `display`). */
  display?: string;
  market?: string;
  subtype?: string;
  parent?: string | null;
  requiresPassphrase?: boolean;
  requiresApplicationId?: boolean;
  [k: string]: unknown;
}

// ── WebSocket frames ────────────────────────────────────────────────────────

export interface TickerFrame extends Ticker {}
export interface OrderBookFrame extends OrderBook {}
export interface TradeFrame extends Trade {
  exchange?: string;
  symbol?: string;
}
export interface OhlcvFrame {
  exchange?: string;
  symbol?: string;
  timeframe?: string;
  candle?: Candle;
  [k: string]: unknown;
}
export interface LiquidationFrame extends Liquidation {}

// ── Trading plane (authenticated) ───────────────────────────────────────────

/** A connected exchange API key. `apiKey` is masked; use `apiKeyId` as the reference. */
export interface ConnectedKey {
  id: string;
  /** Slot reference, e.g. `BINANCEUSDM_0` — pass this when launching strategies. */
  apiKeyId: string;
  /** Masked display value only (e.g. `R0Ik...h9m1`) — never a usable secret. */
  apiKey?: string;
  exchange: string;
  label?: string;
  market?: string;
  privileges?: string[];
  ipMode?: string;
  slot?: number | string;
  [k: string]: unknown;
}

export interface UsageMetric {
  key: string;
  label: string;
  current: number;
  limit: number | null;
  tracked?: boolean;
  unit?: string;
}
export interface UsageSummary {
  tier: string;
  multiEntityWorkspaces?: boolean;
  logRetentionDays?: number;
  metrics: UsageMetric[];
  [k: string]: unknown;
}

/** Virtual paper-wallet balance for a strategy. */
export interface SimBalance {
  asset: string;
  starting_equity: number;
  realized_pnl: number;
  unrealized_pnl: number;
  used: number;
  free: number;
  total: number;
  strategy_id: string;
  sim: boolean;
  [k: string]: unknown;
}
export interface SimPosition { [k: string]: unknown; }
export interface SimOpenOrder { [k: string]: unknown; }
export interface SimTrade { [k: string]: unknown; }
export interface SimOrderResult {
  ok: boolean;
  sim: boolean;
  order_id: string;
  client_order_id?: string;
  symbol: string;
  side: string;
  amount: number;
  fill_price?: number;
  notional_usd?: number;
  strategy_id?: string;
  [k: string]: unknown;
}

export interface Strategy {
  strategyId: string;
  name?: string;
  strategyType?: string;
  status?: string;
  exchange?: string;
  symbol?: string;
  market?: string;
  dryRun?: boolean;
  runtimeMode?: string;
  [k: string]: unknown;
}
/** Body for launching a strategy. `dryRun: true` = paper; live needs `apiKeyId`. */
export interface StrategyCreate {
  name: string;
  strategyType: string;
  exchange: string;
  market?: string;
  symbol?: string;
  /** Connected key reference (from `account.keys()`); required when `dryRun: false`. */
  apiKeyId?: string;
  params?: Record<string, unknown>;
  runtimeMode?: string;
  dryRun?: boolean;
  keyBindings?: Record<string, unknown>;
  [k: string]: unknown;
}
export interface StrategyCreateResult {
  ok: boolean;
  strategyId: string;
  status: string;
}

/** Body for starting a backtest. `mode` omitted = single run. */
export interface BacktestStart {
  strategyType: string;
  exchange: string;
  symbol: string;
  timeframe: string;
  since_ms?: number;
  until_ms?: number;
  initial_equity?: number;
  params?: Record<string, unknown>;
  /** `grid_sweep` / `random_sweep` to fan out a parameter search. */
  mode?: "grid_sweep" | "random_sweep" | string;
  paramRanges?: Record<string, unknown>;
  randomSamples?: number;
  /** For `strategyType: "custom"` — the strategy language (default `rhai`). */
  language?: string;
  /** For `strategyType: "custom"` — the inline strategy script. */
  definition?: string;
  [k: string]: unknown;
}
export interface BacktestJob {
  ok: boolean;
  job_id: string;
  status: string;
  progress_pct?: number;
  error_msg?: string | null;
  strategy_type?: string;
  exchange?: string;
  symbol?: string;
  timeframe?: string;
  [k: string]: unknown;
}
export interface BacktestResult {
  metrics?: Record<string, unknown>;
  equity_curve?: number[];
  equity_timestamps?: number[];
  ohlcv?: unknown;
  [k: string]: unknown;
}
