/**
 * Live trading API — credentialed order placement, account state, and
 * position management on a CONNECTED exchange.
 *
 * Every method POSTs to `https://api.melaya.org/api/v1/private/<op>`; the server
 * resolves your connected exchange credential (referenced by `apiKeyId` — see
 * {@link AccountAPI.keys}) and forwards the call to the venue through Melaya's
 * in-house Rust engine. Responses share an envelope:
 * `{ ok, exchange, operation, orderId, clientOrderId, payload, data, ... }`.
 *
 * ⚠️  These hit the REAL venue with REAL funds. The write methods
 * (createOrder, cancelOrder, amendOrder, cancelAllOrders, cancelPlanOrders,
 * closePosition, setLeverage, setMarginMode, setPositionMode) move money or
 * change account state. For risk-free testing use {@link SimAPI} (paper) or a
 * paper strategy instead.
 */
import type { HttpClient } from "./client.js";

/** Identifies which connected key + venue to act on. `apiKeyId` from `account.keys()`. */
export interface VenueRef {
  exchange: string;
  apiKeyId?: string;
  keyId?: string;
  marketType?: string;
  symbol?: string;
  params?: Record<string, unknown>;
}

/** A live order. `type` defaults to the venue's market order; `price` required for limit. */
export interface LiveOrder extends VenueRef {
  symbol: string;
  side: "buy" | "sell";
  amount: number;
  type?: "market" | "limit" | string;
  price?: number;
  /** Optional bracket — absolute prices, validated server-side for coherence. */
  stopPrice?: number;
  takeProfitPrice?: number;
  reduceOnly?: boolean;
  leverage?: number;
  clientOrderId?: string;
}

/** The shared response envelope of every live op. */
export interface LiveResult {
  ok: boolean;
  exchange: string;
  operation: string;
  orderId?: string;
  clientOrderId?: string;
  payload?: unknown;
  data?: unknown;
  [k: string]: unknown;
}

export class TradeAPI {
  constructor(private readonly http: HttpClient) {}

  private op(op: string, body: Record<string, unknown>): Promise<LiveResult> {
    return this.http.post<LiveResult>(`/api/v1/private/${op}`, body);
  }

  // ── Account state (reads) ─────────────────────────────────────────────────

  /** Live account balance on a connected venue. */
  balance(q: VenueRef): Promise<LiveResult> { return this.op("balance", { ...q }); }
  /** Live open positions. */
  positions(q: VenueRef): Promise<LiveResult> { return this.op("positions", { ...q }); }
  /** Historical positions (venue-dependent). */
  positionsHistory(q: VenueRef): Promise<LiveResult> { return this.op("positions-history", { ...q }); }
  /** Resting (open) orders. */
  openOrders(q: VenueRef): Promise<LiveResult> { return this.op("open-orders", { ...q }); }
  /** All orders (open + recent). */
  orders(q: VenueRef): Promise<LiveResult> { return this.op("orders", { ...q }); }
  /** Closed/filled orders. */
  closedOrders(q: VenueRef): Promise<LiveResult> { return this.op("closed-orders", { ...q }); }
  /** Your trade (fill) history. */
  myTrades(q: VenueRef): Promise<LiveResult> { return this.op("my-trades", { ...q }); }
  /** Extended trade history (venue-dependent). */
  myTradesHistory(q: VenueRef): Promise<LiveResult> { return this.op("my-trades-history", { ...q }); }
  /** Resting conditional/plan (trigger) orders. */
  planOrders(q: VenueRef): Promise<LiveResult> { return this.op("plan-orders", { ...q }); }
  /** Current leverage for a symbol. */
  leverage(q: VenueRef): Promise<LiveResult> { return this.op("leverage", { ...q }); }
  /** Leverage tiers / brackets for a symbol. */
  leverageTiers(q: VenueRef): Promise<LiveResult> { return this.op("leverage-tiers", { ...q }); }

  // ── Order placement & management (LIVE writes — real funds) ────────────────

  /** Place a live order on the venue. ⚠️ real money. */
  createOrder(o: LiveOrder): Promise<LiveResult> {
    const { stopPrice, takeProfitPrice, reduceOnly, clientOrderId, params, ...rest } = o;
    return this.op("create-order", {
      ...rest,
      clientOrderId,
      params: {
        ...(params ?? {}),
        ...(stopPrice != null ? { stopPrice } : {}),
        ...(takeProfitPrice != null ? { takeProfitPrice } : {}),
        ...(reduceOnly != null ? { reduceOnly } : {}),
      },
    });
  }
  /** Cancel a live order by id. ⚠️ */
  cancelOrder(o: VenueRef & { orderId?: string; clientOrderId?: string; symbol?: string }): Promise<LiveResult> {
    return this.op("cancel-order", { ...o });
  }
  /** Amend (modify) a live order. ⚠️ */
  amendOrder(o: VenueRef & { orderId?: string; symbol?: string; amount?: number; price?: number }): Promise<LiveResult> {
    return this.op("amend-order", { ...o });
  }
  /** Cancel every open order (optionally scoped to a symbol). ⚠️ */
  cancelAllOrders(q: VenueRef): Promise<LiveResult> { return this.op("cancel-all-orders", { ...q }); }
  /** Cancel resting plan/trigger orders. ⚠️ */
  cancelPlanOrders(q: VenueRef): Promise<LiveResult> { return this.op("cancel-plan-orders", { ...q }); }
  /** Close an open position (market reduce-only). ⚠️ */
  closePosition(o: VenueRef & { symbol: string }): Promise<LiveResult> { return this.op("close-position", { ...o }); }
  /** Set leverage for a symbol. ⚠️ */
  setLeverage(o: VenueRef & { symbol: string; leverage: number }): Promise<LiveResult> { return this.op("set-leverage", { ...o }); }
  /** Set margin mode (cross/isolated). ⚠️ */
  setMarginMode(o: VenueRef & { symbol?: string; marginMode: string }): Promise<LiveResult> { return this.op("set-margin-mode", { ...o }); }
  /** Set position mode (one-way / hedge). ⚠️ */
  setPositionMode(o: VenueRef & { hedged?: boolean; mode?: string }): Promise<LiveResult> { return this.op("set-position-mode", { ...o }); }
}
