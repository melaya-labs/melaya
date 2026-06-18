/**
 * @melaya/sdk — Official TypeScript/JavaScript SDK for the Melaya unified
 * market-data & streaming API across 70+ venues.
 *
 * @example
 * ```ts
 * import { Melaya } from "@melaya/sdk";
 *
 * const melaya = new Melaya({ apiKey: process.env.MELAYA_API_KEY! });
 *
 * // REST
 * const ticker = await melaya.market.ticker({ exchange: "binance", symbol: "BTC/USDT", market: "spot" });
 *
 * // WebSocket
 * for await (const book of melaya.stream.orderbook({ exchange: "bybit", symbol: "BTC/USDT", limit: 20 })) {
 *   console.log(book.bids[0], book.asks[0]);
 * }
 * ```
 *
 * @see https://melaya.org/docs
 */
export { Melaya, MelayaError, DEFAULT_BASE_URL, DEFAULT_WS_URL } from "./client.js";
export type { MelayaOptions } from "./client.js";
export { MarketAPI } from "./market.js";
export { AccountAPI } from "./account.js";
export { SimAPI } from "./sim.js";
export type { SimCreateOrder } from "./sim.js";
export { StrategiesAPI } from "./strategies.js";
export { BacktestAPI } from "./backtest.js";
export { TradeAPI } from "./trade.js";
export type { VenueRef, LiveOrder, LiveResult } from "./trade.js";
export { StreamAPI, MelayaStream } from "./stream.js";
export type { WebSocketLike, WebSocketCtor } from "./stream.js";
export * from "./types.js";
