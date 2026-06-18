/**
 * Paper-trading (sim broker) API.
 *
 * The sim broker synthesises fills from Melaya's live ticker tape and keeps a
 * virtual wallet per strategy — no venue-side state ever changes, no exchange
 * credentials are needed. Account state (balance / positions / orders / trades)
 * is derived on the fly from the strategy's filled executions.
 *
 * Every call is scoped to a `strategyId`: create a strategy with
 * `melaya.strategies.create({ ..., dryRun: true })` (or use an existing
 * paper strategy) and pass its id here.
 */
import type { HttpClient } from "./client.js";
import type { SimBalance, SimPosition, SimOpenOrder, SimTrade, SimOrderResult } from "./types.js";

/** A paper order. `type` defaults to `market`; `price` is required for limit orders. */
export interface SimCreateOrder {
  strategyId: string;
  exchange: string;
  symbol: string;
  side: "buy" | "sell";
  amount: number;
  type?: "market" | "limit";
  price?: number;
  market?: string;
  leverage?: number;
  reduceOnly?: boolean;
  slPrice?: number;
  tpPrice?: number;
  clientOrderId?: string;
  params?: Record<string, unknown>;
}

export class SimAPI {
  constructor(private readonly http: HttpClient) {}

  /** Paper accounts (one virtual wallet per paper strategy). */
  async listAccounts(): Promise<unknown[]> {
    const r = await this.http.get<unknown>("/api/v1/private/sim/list-accounts");
    return Array.isArray(r) ? r : ((r as { accounts?: unknown[] })?.accounts ?? []);
  }

  /** Virtual balance for a paper strategy (equity, realized/unrealized PnL, free/used). */
  async balance(q: { strategyId: string; asset?: string }): Promise<SimBalance> {
    return await this.http.get<SimBalance>("/api/v1/private/sim/balance", {
      strategy_id: q.strategyId,
      asset: q.asset,
    });
  }

  /** Open paper positions for a strategy. */
  async positions(q: { strategyId: string }): Promise<SimPosition[]> {
    const r = await this.http.get<unknown>("/api/v1/private/sim/positions", { strategy_id: q.strategyId });
    return Array.isArray(r) ? (r as SimPosition[]) : ((r as { positions?: SimPosition[] })?.positions ?? []);
  }

  /** Resting paper orders for a strategy. */
  async openOrders(q: { strategyId: string }): Promise<SimOpenOrder[]> {
    const r = await this.http.get<unknown>("/api/v1/private/sim/open-orders", { strategy_id: q.strategyId });
    return Array.isArray(r) ? (r as SimOpenOrder[]) : ((r as { orders?: SimOpenOrder[] })?.orders ?? []);
  }

  /** Filled paper trades for a strategy. */
  async myTrades(q: { strategyId: string }): Promise<SimTrade[]> {
    const r = await this.http.get<unknown>("/api/v1/private/sim/my-trades", { strategy_id: q.strategyId });
    return Array.isArray(r) ? (r as SimTrade[]) : ((r as { trades?: SimTrade[] })?.trades ?? []);
  }

  /** Place a paper order. Fills synthesise from the live ticker; nothing hits the venue. */
  async createOrder(o: SimCreateOrder): Promise<SimOrderResult> {
    const orderType = o.type ?? "market";
    // The broker accepts both snake_case and camelCase for several fields;
    // send the canonical snake_case form plus the few camelCase aliases it
    // also reads, so one call works regardless of server-side preference.
    return await this.http.post<SimOrderResult>("/api/v1/private/sim/create-order", {
      strategy_id: o.strategyId,
      exchange: o.exchange,
      symbol: o.symbol,
      side: o.side,
      amount: o.amount,
      order_type: orderType,
      orderType,
      price: o.price,
      market: o.market,
      market_type: o.market,
      leverage: o.leverage,
      reduceOnly: o.reduceOnly,
      slPrice: o.slPrice,
      tpPrice: o.tpPrice,
      client_order_id: o.clientOrderId,
      clientOrderId: o.clientOrderId,
      params: o.params,
    });
  }

  /** Cancel a resting paper order. */
  async cancelOrder(o: { strategyId: string; orderId: string; symbol?: string; exchange?: string }): Promise<Record<string, unknown>> {
    return await this.http.post<Record<string, unknown>>("/api/v1/private/sim/cancel-order", {
      strategy_id: o.strategyId,
      order_id: o.orderId,
      orderId: o.orderId,
      symbol: o.symbol,
      exchange: o.exchange,
    });
  }
}
