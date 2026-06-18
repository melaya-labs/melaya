package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

import static org.melaya.MarketAPI.params;

/**
 * Paper-trading (sim broker) API.
 *
 * Every call is scoped to a {@code strategyId}. Create a strategy with
 * {@code dryRun: true} via {@link StrategiesAPI#create} and pass its id here.
 */
public class SimAPI {

    private final HttpClient http;

    SimAPI(HttpClient http) {
        this.http = http;
    }

    /** Paper accounts (one virtual wallet per paper strategy). */
    public JsonNode listAccounts() {
        JsonNode r = http.get("/api/v1/private/sim/list-accounts", null);
        if (r != null && r.isArray()) return r;
        if (r != null && r.isObject() && r.has("accounts")) return r.get("accounts");
        return r;
    }

    /** Virtual balance for a paper strategy (equity, realized/unrealized PnL, free/used). */
    public JsonNode balance(String strategyId, String asset) {
        Map<String, Object> q = params("strategy_id", strategyId, "asset", asset);
        return http.get("/api/v1/private/sim/balance", q);
    }

    /** Open paper positions for a strategy. */
    public JsonNode positions(String strategyId) {
        JsonNode r = http.get("/api/v1/private/sim/positions", params("strategy_id", strategyId));
        if (r != null && r.isArray()) return r;
        if (r != null && r.isObject() && r.has("positions")) return r.get("positions");
        return r;
    }

    /** Resting paper orders for a strategy. */
    public JsonNode openOrders(String strategyId) {
        JsonNode r = http.get("/api/v1/private/sim/open-orders", params("strategy_id", strategyId));
        if (r != null && r.isArray()) return r;
        if (r != null && r.isObject() && r.has("orders")) return r.get("orders");
        return r;
    }

    /** Filled paper trades for a strategy. */
    public JsonNode myTrades(String strategyId) {
        JsonNode r = http.get("/api/v1/private/sim/my-trades", params("strategy_id", strategyId));
        if (r != null && r.isArray()) return r;
        if (r != null && r.isObject() && r.has("trades")) return r.get("trades");
        return r;
    }

    /**
     * Place a paper order.
     *
     * @param strategyId  the paper strategy id
     * @param exchange    venue id (e.g. {@code "binance"})
     * @param symbol      unified symbol (e.g. {@code "BTC/USDT"})
     * @param side        {@code "buy"} or {@code "sell"}
     * @param amount      quantity
     * @param type        {@code "market"} (default) or {@code "limit"}
     * @param price       required for limit orders
     * @param market      market kind (optional, e.g. {@code "spot"})
     */
    public JsonNode createOrder(String strategyId, String exchange, String symbol,
                                String side, double amount, String type, Double price,
                                String market) {
        String orderType = type != null ? type : "market";
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("strategy_id", strategyId);
        body.put("exchange", exchange);
        body.put("symbol", symbol);
        body.put("side", side);
        body.put("amount", amount);
        body.put("order_type", orderType);
        body.put("orderType", orderType);
        if (price != null) { body.put("price", price); }
        if (market != null) { body.put("market", market); body.put("market_type", market); }
        return http.post("/api/v1/private/sim/create-order", body);
    }

    /** Cancel a resting paper order. */
    public JsonNode cancelOrder(String strategyId, String orderId, String symbol, String exchange) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("strategy_id", strategyId);
        body.put("order_id", orderId);
        body.put("orderId", orderId);
        if (symbol != null) body.put("symbol", symbol);
        if (exchange != null) body.put("exchange", exchange);
        return http.post("/api/v1/private/sim/cancel-order", body);
    }
}
