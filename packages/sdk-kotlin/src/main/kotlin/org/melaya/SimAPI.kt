package org.melaya

import org.json.JSONArray
import org.json.JSONObject

/**
 * Paper-trading (sim broker) API.
 *
 * Every call is scoped to a `strategyId`.  Create a strategy with
 * `melaya.strategies.create(dryRun = true)` and pass its id here.
 *
 * Paths: `https://api.melaya.org/api/v1/private/sim/…`
 */
class SimAPI internal constructor(private val http: HttpClient) {

    /** Paper accounts (one virtual wallet per paper strategy). */
    fun listAccounts(): JSONArray {
        val r = http.get("/api/v1/private/sim/list-accounts")
        return when (r) {
            is JSONArray -> r
            is JSONObject -> r.optJSONArray("accounts") ?: JSONArray()
            else -> JSONArray()
        }
    }

    /** Virtual balance for a paper strategy (equity, realized/unrealized PnL, free/used). */
    fun balance(strategyId: String, asset: String? = null): JSONObject {
        return http.get("/api/v1/private/sim/balance", mapOf(
            "strategy_id" to strategyId, "asset" to asset
        )).asObject()
    }

    /** Open paper positions for a strategy. */
    fun positions(strategyId: String): JSONArray {
        val r = http.get("/api/v1/private/sim/positions", mapOf("strategy_id" to strategyId))
        return when (r) {
            is JSONArray -> r
            is JSONObject -> r.optJSONArray("positions") ?: JSONArray()
            else -> JSONArray()
        }
    }

    /** Resting paper orders for a strategy. */
    fun openOrders(strategyId: String): JSONArray {
        val r = http.get("/api/v1/private/sim/open-orders", mapOf("strategy_id" to strategyId))
        return when (r) {
            is JSONArray -> r
            is JSONObject -> r.optJSONArray("orders") ?: JSONArray()
            else -> JSONArray()
        }
    }

    /** Filled paper trades for a strategy. */
    fun myTrades(strategyId: String): JSONArray {
        val r = http.get("/api/v1/private/sim/my-trades", mapOf("strategy_id" to strategyId))
        return when (r) {
            is JSONArray -> r
            is JSONObject -> r.optJSONArray("trades") ?: JSONArray()
            else -> JSONArray()
        }
    }

    /**
     * Place a paper order.  Fills synthesise from the live ticker; nothing hits the venue.
     *
     * @param type Defaults to `"market"`. Pass `"limit"` with a `price` for resting orders.
     */
    fun createOrder(
        strategyId: String,
        exchange: String,
        symbol: String,
        side: String,
        amount: Double,
        type: String = "market",
        price: Double? = null,
        market: String? = null,
        leverage: Double? = null,
        reduceOnly: Boolean? = null,
        slPrice: Double? = null,
        tpPrice: Double? = null,
        clientOrderId: String? = null,
        params: Map<String, Any?> = emptyMap(),
    ): JSONObject {
        val body = buildMap {
            put("strategy_id", strategyId)
            put("exchange", exchange)
            put("symbol", symbol)
            put("side", side)
            put("amount", amount)
            put("order_type", type)
            put("orderType", type)
            if (price != null) put("price", price)
            if (market != null) { put("market", market); put("market_type", market) }
            if (leverage != null) put("leverage", leverage)
            if (reduceOnly != null) put("reduceOnly", reduceOnly)
            if (slPrice != null) put("slPrice", slPrice)
            if (tpPrice != null) put("tpPrice", tpPrice)
            if (clientOrderId != null) { put("client_order_id", clientOrderId); put("clientOrderId", clientOrderId) }
            if (params.isNotEmpty()) put("params", params)
        }
        return http.post("/api/v1/private/sim/create-order", body).asObject()
    }

    /** Cancel a resting paper order. */
    fun cancelOrder(
        strategyId: String,
        orderId: String,
        symbol: String? = null,
        exchange: String? = null,
    ): JSONObject {
        val body = buildMap {
            put("strategy_id", strategyId)
            put("order_id", orderId)
            put("orderId", orderId)
            if (symbol != null) put("symbol", symbol)
            if (exchange != null) put("exchange", exchange)
        }
        return http.post("/api/v1/private/sim/cancel-order", body).asObject()
    }
}
