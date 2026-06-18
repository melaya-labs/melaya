/**
 * REST market-data API — normalized across all 70+ venues.
 *
 * Every method maps to a documented endpoint under
 * `https://api.melaya.org/api/v1/market/*`. The API wraps payloads in an
 * `{ ok, <data> }` envelope; these methods unwrap to the inner data and the
 * HttpClient throws on `ok: false`. The API key is injected on every request.
 */
import type { HttpClient } from "./client.js";
import type {
  Ticker,
  OrderBook,
  Candle,
  Trade,
  Liquidation,
  ExchangeStatus,
  ExchangeInfo,
  SymbolQuery,
  OrderBookQuery,
  OhlcvQuery,
  ExchangeQuery,
} from "./types.js";

export class MarketAPI {
  constructor(private readonly http: HttpClient) {}

  /** List the exchanges Melaya supports right now (the source of truth — don't hardcode). */
  async listExchanges(): Promise<ExchangeInfo[]> {
    return (await this.http.get<{ exchanges: ExchangeInfo[] }>("/api/v1/market/list-exchanges")).exchanges;
  }

  /** Best bid/ask, last price, and 24h aggregates for one symbol. */
  async ticker(q: SymbolQuery): Promise<Ticker> {
    return (await this.http.get<{ ticker: Ticker }>("/api/v1/market/ticker", { ...q })).ticker;
  }

  /** Order book to a given depth. */
  async orderbook(q: OrderBookQuery): Promise<OrderBook> {
    return (await this.http.get<{ orderbook: OrderBook }>("/api/v1/market/orderbook", { ...q })).orderbook;
  }

  /** OHLCV candles for a timeframe. Each candle is [ts, open, high, low, close, volume]. */
  async ohlcv(q: OhlcvQuery): Promise<Candle[]> {
    return (await this.http.get<{ candles: Candle[] }>("/api/v1/market/ohlcv", { ...q })).candles;
  }

  /** Recent public trades. */
  async trades(q: SymbolQuery): Promise<Trade[]> {
    return (await this.http.get<{ trades: Trade[] }>("/api/v1/market/trades", { ...q })).trades;
  }

  /** Tradable markets on a venue. */
  async markets(q: ExchangeQuery): Promise<unknown[]> {
    return (await this.http.get<{ markets: unknown[] }>("/api/v1/market/markets", { ...q })).markets;
  }

  /** Listed currencies on a venue. (Not supported on every venue.) */
  async currencies(q: ExchangeQuery): Promise<unknown[]> {
    return (await this.http.get<{ currencies: unknown[] }>("/api/v1/market/currencies", { ...q })).currencies;
  }

  /** Operational status: ok / maintenance / degraded. */
  async status(q: ExchangeQuery): Promise<ExchangeStatus> {
    return (await this.http.get<{ status: ExchangeStatus }>("/api/v1/market/status", { ...q })).status;
  }

  /** Exchange server time. */
  async time(q: ExchangeQuery): Promise<unknown> {
    return (await this.http.get<{ time: unknown }>("/api/v1/market/time", { ...q })).time;
  }

  // ── Batch / derivatives (POST) ──────────────────────────────────────────

  /** Tickers for many symbols on one venue in a single call. Keyed by symbol. */
  async tickers(body: { exchange: string; symbols: string[]; market?: string }): Promise<Record<string, Ticker>> {
    return (await this.http.post<{ tickers: Record<string, Ticker> }>("/api/v1/market/tickers", body)).tickers;
  }

  /** Latest funding rates for perpetuals. Keyed by symbol. */
  async fundingRates(body: { exchange: string; symbols: string[]; market?: string }): Promise<Record<string, unknown>> {
    return (await this.http.post<{ rates: Record<string, unknown> }>("/api/v1/market/funding-rates", body)).rates;
  }

  /** Funding-rate history. */
  async fundingRateHistory(body: { exchange: string; symbol: string; hours?: number; market?: string }): Promise<unknown[]> {
    return (await this.http.post<{ history: unknown[] }>("/api/v1/market/funding-rate-history", body)).history;
  }

  /** Open interest for one or more perpetuals. Keyed by symbol. */
  async openInterest(body: { exchange: string; symbols: string[]; market?: string }): Promise<Record<string, unknown>> {
    return (await this.http.post<{ openInterest: Record<string, unknown> }>("/api/v1/market/open-interest", body)).openInterest;
  }

  /** Open-interest history. */
  async openInterestHistory(body: { exchange: string; symbol: string; hours?: number; market?: string }): Promise<unknown[]> {
    return (await this.http.post<{ history: unknown[] }>("/api/v1/market/open-interest-history", body)).history;
  }

  /** Instrument list + trading constraints (tick size, min notional, qty step). */
  async instruments(body: { exchange: string; market?: string }): Promise<unknown> {
    return await this.http.post<unknown>("/api/v1/market/instruments", body);
  }

  /** Cross-exchange liquidation events (historical query). */
  async liquidationEvents(body: { exchange?: string; symbol?: string; sinceMs?: number; limit?: number }): Promise<Liquidation[]> {
    return (await this.http.post<{ events: Liquidation[] }>("/api/v1/market/liquidation-events", body)).events;
  }

  /** Multi-symbol OHLCV in one call. Returns candle arrays keyed by symbol. */
  async ohlcvMulti(body: { exchange: string; symbols: string[]; timeframe: string; limit?: number; market?: string }): Promise<Record<string, Candle[]>> {
    return (await this.http.post<{ perSymbol: Record<string, Candle[]> }>("/api/v1/market/ohlcv-multi", body)).perSymbol;
  }

  /** Trading constraints for one symbol (tick size, min notional, qty step, leverage). */
  async marketConstraints(body: { exchange: string; symbol: string; market?: string }): Promise<unknown> {
    return (await this.http.post<{ constraints: unknown }>("/api/v1/market/market-constraints", body)).constraints;
  }

  /** Funding-rate history for one symbol across several venues. Keyed by exchange. */
  async fundingRateHistoryMulti(body: { exchanges: string[]; symbol: string; hours?: number }): Promise<Record<string, unknown>> {
    return (await this.http.post<{ perExchange: Record<string, unknown> }>("/api/v1/market/funding-rate-history-multi", body)).perExchange;
  }

  /** Open-interest history for one symbol across several venues. Keyed by exchange. */
  async openInterestHistoryMulti(body: { exchanges: string[]; symbol: string; hours?: number }): Promise<Record<string, unknown>> {
    return (await this.http.post<{ perExchange: Record<string, unknown> }>("/api/v1/market/open-interest-history-multi", body)).perExchange;
  }

  /** Prediction-market listings for a venue (polymarket, kalshi, drift_pm, sxbet, azuro, overtime). */
  async predictionMarkets(body: { venue: string } = { venue: "polymarket" }): Promise<unknown[]> {
    return (await this.http.post<{ markets: unknown[] }>("/api/v1/market/pm-markets", body)).markets;
  }

  /** Live platform catalog counts (agentic tools, subagents, by category). Public. */
  async catalogCounts(): Promise<{ tools: number; subagents: number; byCategory?: unknown }> {
    return await this.http.get<{ tools: number; subagents: number; byCategory?: unknown }>("/api/v1/public/catalog-counts");
  }
}
