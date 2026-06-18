package org.melaya

import org.json.JSONObject

/**
 * Live trading API — credentialed order placement, account state, and
 * position management on a CONNECTED exchange.
 *
 * Every method POSTs to `https://api.melaya.org/api/v1/private/<op>`; the server
 * resolves your connected exchange credential (referenced by `apiKeyId` — see
 * `account.keys()`) and forwards the call to the venue through Melaya's in-house
 * Rust engine. Responses share an envelope:
 * `{ok, exchange, operation, orderId, clientOrderId, payload, data, ...}`.
 *
 * **WARNING:** the write methods (createOrder, cancelOrder, amendOrder,
 * cancelAllOrders, cancelPlanOrders, closePosition, setLeverage, setMarginMode,
 * setPositionMode) hit the REAL venue with REAL funds. For risk-free testing use
 * [SimAPI] (paper) or a paper strategy instead.
 */
class TradeAPI internal constructor(private val http: HttpClient) {

    private fun op(op: String, body: Map<String, Any?>): JSONObject =
        http.post("/api/v1/private/$op", body).asObject()

    /** Build a nullable-omitting map. */
    private fun b(vararg kv: Pair<String, Any?>): Map<String, Any?> =
        kv.filter { it.second != null }.toMap()

    // ── Account state (reads) ─────────────────────────────────────────────────

    /** Live account balance on a connected venue. */
    fun balance(
        exchange: String,
        apiKeyId: String? = null,
        keyId: String? = null,
        marketType: String? = null,
    ): JSONObject = op("balance", b(
        "exchange"   to exchange,
        "apiKeyId"   to apiKeyId,
        "keyId"      to keyId,
        "marketType" to marketType,
    ))

    /** Live open positions. */
    fun positions(
        exchange: String,
        apiKeyId: String? = null,
        marketType: String? = null,
        symbol: String? = null,
    ): JSONObject = op("positions", b(
        "exchange"   to exchange,
        "apiKeyId"   to apiKeyId,
        "marketType" to marketType,
        "symbol"     to symbol,
    ))

    /** Historical positions (venue-dependent). */
    fun positionsHistory(
        exchange: String,
        apiKeyId: String? = null,
        marketType: String? = null,
        symbol: String? = null,
    ): JSONObject = op("positions-history", b(
        "exchange"   to exchange,
        "apiKeyId"   to apiKeyId,
        "marketType" to marketType,
        "symbol"     to symbol,
    ))

    /** Resting (open) orders. */
    fun openOrders(
        exchange: String,
        apiKeyId: String? = null,
        marketType: String? = null,
        symbol: String? = null,
    ): JSONObject = op("open-orders", b(
        "exchange"   to exchange,
        "apiKeyId"   to apiKeyId,
        "marketType" to marketType,
        "symbol"     to symbol,
    ))

    /** All orders (open + recent). */
    fun orders(
        exchange: String,
        apiKeyId: String? = null,
        marketType: String? = null,
        symbol: String? = null,
    ): JSONObject = op("orders", b(
        "exchange"   to exchange,
        "apiKeyId"   to apiKeyId,
        "marketType" to marketType,
        "symbol"     to symbol,
    ))

    /** Closed/filled orders. */
    fun closedOrders(
        exchange: String,
        apiKeyId: String? = null,
        marketType: String? = null,
        symbol: String? = null,
    ): JSONObject = op("closed-orders", b(
        "exchange"   to exchange,
        "apiKeyId"   to apiKeyId,
        "marketType" to marketType,
        "symbol"     to symbol,
    ))

    /** Your trade (fill) history. */
    fun myTrades(
        exchange: String,
        apiKeyId: String? = null,
        marketType: String? = null,
        symbol: String? = null,
    ): JSONObject = op("my-trades", b(
        "exchange"   to exchange,
        "apiKeyId"   to apiKeyId,
        "marketType" to marketType,
        "symbol"     to symbol,
    ))

    /** Extended trade history (venue-dependent). */
    fun myTradesHistory(
        exchange: String,
        apiKeyId: String? = null,
        marketType: String? = null,
        symbol: String? = null,
    ): JSONObject = op("my-trades-history", b(
        "exchange"   to exchange,
        "apiKeyId"   to apiKeyId,
        "marketType" to marketType,
        "symbol"     to symbol,
    ))

    /** Resting conditional/plan (trigger) orders. */
    fun planOrders(
        exchange: String,
        apiKeyId: String? = null,
        marketType: String? = null,
        symbol: String? = null,
    ): JSONObject = op("plan-orders", b(
        "exchange"   to exchange,
        "apiKeyId"   to apiKeyId,
        "marketType" to marketType,
        "symbol"     to symbol,
    ))

    /** Current leverage for a symbol. */
    fun leverage(
        exchange: String,
        apiKeyId: String? = null,
        symbol: String? = null,
        marketType: String? = null,
    ): JSONObject = op("leverage", b(
        "exchange"   to exchange,
        "apiKeyId"   to apiKeyId,
        "symbol"     to symbol,
        "marketType" to marketType,
    ))

    /** Leverage tiers / brackets for a symbol. */
    fun leverageTiers(
        exchange: String,
        apiKeyId: String? = null,
        symbol: String? = null,
        marketType: String? = null,
    ): JSONObject = op("leverage-tiers", b(
        "exchange"   to exchange,
        "apiKeyId"   to apiKeyId,
        "symbol"     to symbol,
        "marketType" to marketType,
    ))

    // ── Order placement & management (LIVE writes — real funds) ───────────────

    /**
     * Place a live order on the venue. **WARNING: real money.**
     * [stopPrice], [takeProfitPrice], and [reduceOnly] are folded into the
     * `params` sub-object as required by the contract.
     */
    fun createOrder(
        exchange: String,
        symbol: String,
        side: String,
        amount: Double,
        apiKeyId: String? = null,
        type: String = "market",
        price: Double? = null,
        marketType: String? = null,
        leverage: Double? = null,
        clientOrderId: String? = null,
        stopPrice: Double? = null,
        takeProfitPrice: Double? = null,
        reduceOnly: Boolean? = null,
        params: Map<String, Any?> = emptyMap(),
    ): JSONObject {
        val p = buildMap {
            putAll(params)
            if (stopPrice != null)       put("stopPrice",       stopPrice)
            if (takeProfitPrice != null) put("takeProfitPrice", takeProfitPrice)
            if (reduceOnly != null)      put("reduceOnly",      reduceOnly)
        }
        val body = buildMap {
            put("exchange", exchange)
            if (apiKeyId != null)      put("apiKeyId",       apiKeyId)
            put("symbol", symbol)
            put("side", side)
            put("amount", amount)
            put("type", type)
            if (price != null)         put("price",          price)
            if (marketType != null)    put("marketType",     marketType)
            if (leverage != null)      put("leverage",       leverage)
            if (clientOrderId != null) put("clientOrderId",  clientOrderId)
            if (p.isNotEmpty())        put("params",         p)
        }
        return http.post("/api/v1/private/create-order", body).asObject()
    }

    /** Cancel a live order by id. **WARNING.** */
    fun cancelOrder(
        exchange: String,
        apiKeyId: String? = null,
        orderId: String? = null,
        clientOrderId: String? = null,
        symbol: String? = null,
        marketType: String? = null,
    ): JSONObject = op("cancel-order", b(
        "exchange"      to exchange,
        "apiKeyId"      to apiKeyId,
        "orderId"       to orderId,
        "clientOrderId" to clientOrderId,
        "symbol"        to symbol,
        "marketType"    to marketType,
    ))

    /** Amend (modify) a live order. **WARNING.** */
    fun amendOrder(
        exchange: String,
        apiKeyId: String? = null,
        orderId: String? = null,
        symbol: String? = null,
        amount: Double? = null,
        price: Double? = null,
    ): JSONObject = op("amend-order", b(
        "exchange"  to exchange,
        "apiKeyId"  to apiKeyId,
        "orderId"   to orderId,
        "symbol"    to symbol,
        "amount"    to amount,
        "price"     to price,
    ))

    /** Cancel every open order (optionally scoped to a symbol). **WARNING.** */
    fun cancelAllOrders(
        exchange: String,
        apiKeyId: String? = null,
        symbol: String? = null,
        marketType: String? = null,
    ): JSONObject = op("cancel-all-orders", b(
        "exchange"   to exchange,
        "apiKeyId"   to apiKeyId,
        "symbol"     to symbol,
        "marketType" to marketType,
    ))

    /** Cancel resting plan/trigger orders. **WARNING.** */
    fun cancelPlanOrders(
        exchange: String,
        apiKeyId: String? = null,
        symbol: String? = null,
        marketType: String? = null,
    ): JSONObject = op("cancel-plan-orders", b(
        "exchange"   to exchange,
        "apiKeyId"   to apiKeyId,
        "symbol"     to symbol,
        "marketType" to marketType,
    ))

    /** Close an open position (market reduce-only). **WARNING.** */
    fun closePosition(
        exchange: String,
        symbol: String,
        apiKeyId: String? = null,
        marketType: String? = null,
    ): JSONObject = op("close-position", b(
        "exchange"   to exchange,
        "apiKeyId"   to apiKeyId,
        "symbol"     to symbol,
        "marketType" to marketType,
    ))

    /** Set leverage for a symbol. **WARNING.** */
    fun setLeverage(
        exchange: String,
        symbol: String,
        leverage: Double,
        apiKeyId: String? = null,
        marketType: String? = null,
    ): JSONObject = op("set-leverage", b(
        "exchange"   to exchange,
        "apiKeyId"   to apiKeyId,
        "symbol"     to symbol,
        "leverage"   to leverage,
        "marketType" to marketType,
    ))

    /** Set margin mode (cross/isolated). **WARNING.** */
    fun setMarginMode(
        exchange: String,
        marginMode: String,
        apiKeyId: String? = null,
        symbol: String? = null,
        marketType: String? = null,
    ): JSONObject = op("set-margin-mode", b(
        "exchange"   to exchange,
        "apiKeyId"   to apiKeyId,
        "marginMode" to marginMode,
        "symbol"     to symbol,
        "marketType" to marketType,
    ))

    /** Set position mode (one-way / hedge). **WARNING.** */
    fun setPositionMode(
        exchange: String,
        apiKeyId: String? = null,
        hedged: Boolean? = null,
        mode: String? = null,
        marketType: String? = null,
    ): JSONObject = op("set-position-mode", b(
        "exchange"   to exchange,
        "apiKeyId"   to apiKeyId,
        "hedged"     to hedged,
        "mode"       to mode,
        "marketType" to marketType,
    ))
}
