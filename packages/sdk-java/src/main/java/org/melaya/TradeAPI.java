package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Live trading API — credentialed order placement, account state, and
 * position management on a CONNECTED exchange.
 *
 * <p>Every method POSTs to {@code https://api.melaya.org/api/v1/private/<op>}; the server
 * resolves your connected exchange credential (referenced by {@code apiKeyId} — see
 * {@link AccountAPI#keys}) and forwards the call to the venue through Melaya's
 * in-house Rust engine. Responses share an envelope:
 * {@code {ok, exchange, operation, orderId, clientOrderId, payload, data, ...}}.
 *
 * <p><strong>WARNING:</strong> the write methods (createOrder, cancelOrder, amendOrder,
 * cancelAllOrders, cancelPlanOrders, closePosition, setLeverage, setMarginMode,
 * setPositionMode) hit the REAL venue with REAL funds. For risk-free testing use
 * {@link SimAPI} (paper) or a paper strategy instead.
 */
public class TradeAPI {

    private final HttpClient http;

    TradeAPI(HttpClient http) {
        this.http = http;
    }

    private JsonNode op(String op, Map<String, Object> body) {
        return http.post("/api/v1/private/" + op, body);
    }

    /** Build a nullable-omitting body map from alternating key/value pairs. */
    private static Map<String, Object> body(Object... kv) {
        if (kv.length % 2 != 0) throw new IllegalArgumentException("body() requires even number of args");
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i + 1] != null) m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    // ── Account state (reads) ─────────────────────────────────────────────────

    /** Live account balance on a connected venue. */
    public JsonNode balance(String exchange, String apiKeyId, String keyId, String marketType) {
        return op("balance", body(
                "exchange", exchange,
                "apiKeyId", apiKeyId,
                "keyId", keyId,
                "marketType", marketType));
    }

    /** Live open positions. */
    public JsonNode positions(String exchange, String apiKeyId, String marketType, String symbol) {
        return op("positions", body(
                "exchange", exchange,
                "apiKeyId", apiKeyId,
                "marketType", marketType,
                "symbol", symbol));
    }

    /** Historical positions (venue-dependent). */
    public JsonNode positionsHistory(String exchange, String apiKeyId, String marketType, String symbol) {
        return op("positions-history", body(
                "exchange", exchange,
                "apiKeyId", apiKeyId,
                "marketType", marketType,
                "symbol", symbol));
    }

    /** Resting (open) orders. */
    public JsonNode openOrders(String exchange, String apiKeyId, String marketType, String symbol) {
        return op("open-orders", body(
                "exchange", exchange,
                "apiKeyId", apiKeyId,
                "marketType", marketType,
                "symbol", symbol));
    }

    /** All orders (open + recent). */
    public JsonNode orders(String exchange, String apiKeyId, String marketType, String symbol) {
        return op("orders", body(
                "exchange", exchange,
                "apiKeyId", apiKeyId,
                "marketType", marketType,
                "symbol", symbol));
    }

    /** Closed/filled orders. */
    public JsonNode closedOrders(String exchange, String apiKeyId, String marketType, String symbol) {
        return op("closed-orders", body(
                "exchange", exchange,
                "apiKeyId", apiKeyId,
                "marketType", marketType,
                "symbol", symbol));
    }

    /** Your trade (fill) history. */
    public JsonNode myTrades(String exchange, String apiKeyId, String marketType, String symbol) {
        return op("my-trades", body(
                "exchange", exchange,
                "apiKeyId", apiKeyId,
                "marketType", marketType,
                "symbol", symbol));
    }

    /** Extended trade history (venue-dependent). */
    public JsonNode myTradesHistory(String exchange, String apiKeyId, String marketType, String symbol) {
        return op("my-trades-history", body(
                "exchange", exchange,
                "apiKeyId", apiKeyId,
                "marketType", marketType,
                "symbol", symbol));
    }

    /** Resting conditional/plan (trigger) orders. */
    public JsonNode planOrders(String exchange, String apiKeyId, String marketType, String symbol) {
        return op("plan-orders", body(
                "exchange", exchange,
                "apiKeyId", apiKeyId,
                "marketType", marketType,
                "symbol", symbol));
    }

    /** Current leverage for a symbol. */
    public JsonNode leverage(String exchange, String apiKeyId, String symbol, String marketType) {
        return op("leverage", body(
                "exchange", exchange,
                "apiKeyId", apiKeyId,
                "symbol", symbol,
                "marketType", marketType));
    }

    /** Leverage tiers / brackets for a symbol. */
    public JsonNode leverageTiers(String exchange, String apiKeyId, String symbol, String marketType) {
        return op("leverage-tiers", body(
                "exchange", exchange,
                "apiKeyId", apiKeyId,
                "symbol", symbol,
                "marketType", marketType));
    }

    // ── Order placement & management (LIVE writes — real funds) ───────────────

    /**
     * Place a live order on the venue. <strong>WARNING: real money.</strong>
     * {@code stopPrice}, {@code takeProfitPrice}, and {@code reduceOnly} are
     * folded into the {@code params} sub-object as required by the contract.
     */
    public JsonNode createOrder(String exchange, String apiKeyId, String symbol,
                                String side, double amount, String type, Double price,
                                String marketType, Double leverage, String clientOrderId,
                                Double stopPrice, Double takeProfitPrice, Boolean reduceOnly,
                                Map<String, Object> params) {
        Map<String, Object> p = new LinkedHashMap<>();
        if (params != null) p.putAll(params);
        if (stopPrice != null)       p.put("stopPrice",       stopPrice);
        if (takeProfitPrice != null) p.put("takeProfitPrice", takeProfitPrice);
        if (reduceOnly != null)      p.put("reduceOnly",      reduceOnly);

        Map<String, Object> b = body(
                "exchange",       exchange,
                "apiKeyId",       apiKeyId,
                "symbol",         symbol,
                "side",           side,
                "amount",         amount,
                "type",           type,
                "price",          price,
                "marketType",     marketType,
                "leverage",       leverage,
                "clientOrderId",  clientOrderId);
        if (!p.isEmpty()) b.put("params", p);
        return op("create-order", b);
    }

    /** Cancel a live order by id. <strong>WARNING.</strong> */
    public JsonNode cancelOrder(String exchange, String apiKeyId, String orderId,
                                String clientOrderId, String symbol, String marketType) {
        return op("cancel-order", body(
                "exchange",       exchange,
                "apiKeyId",       apiKeyId,
                "orderId",        orderId,
                "clientOrderId",  clientOrderId,
                "symbol",         symbol,
                "marketType",     marketType));
    }

    /** Amend (modify) a live order. <strong>WARNING.</strong> */
    public JsonNode amendOrder(String exchange, String apiKeyId, String orderId,
                               String symbol, Double amount, Double price) {
        return op("amend-order", body(
                "exchange",  exchange,
                "apiKeyId",  apiKeyId,
                "orderId",   orderId,
                "symbol",    symbol,
                "amount",    amount,
                "price",     price));
    }

    /** Cancel every open order (optionally scoped to a symbol). <strong>WARNING.</strong> */
    public JsonNode cancelAllOrders(String exchange, String apiKeyId, String symbol, String marketType) {
        return op("cancel-all-orders", body(
                "exchange",    exchange,
                "apiKeyId",    apiKeyId,
                "symbol",      symbol,
                "marketType",  marketType));
    }

    /** Cancel resting plan/trigger orders. <strong>WARNING.</strong> */
    public JsonNode cancelPlanOrders(String exchange, String apiKeyId, String symbol, String marketType) {
        return op("cancel-plan-orders", body(
                "exchange",    exchange,
                "apiKeyId",    apiKeyId,
                "symbol",      symbol,
                "marketType",  marketType));
    }

    /** Close an open position (market reduce-only). <strong>WARNING.</strong> */
    public JsonNode closePosition(String exchange, String apiKeyId, String symbol, String marketType) {
        return op("close-position", body(
                "exchange",    exchange,
                "apiKeyId",    apiKeyId,
                "symbol",      symbol,
                "marketType",  marketType));
    }

    /** Set leverage for a symbol. <strong>WARNING.</strong> */
    public JsonNode setLeverage(String exchange, String apiKeyId, String symbol,
                                double leverage, String marketType) {
        return op("set-leverage", body(
                "exchange",    exchange,
                "apiKeyId",    apiKeyId,
                "symbol",      symbol,
                "leverage",    leverage,
                "marketType",  marketType));
    }

    /** Set margin mode (cross/isolated). <strong>WARNING.</strong> */
    public JsonNode setMarginMode(String exchange, String apiKeyId, String marginMode,
                                  String symbol, String marketType) {
        return op("set-margin-mode", body(
                "exchange",    exchange,
                "apiKeyId",    apiKeyId,
                "marginMode",  marginMode,
                "symbol",      symbol,
                "marketType",  marketType));
    }

    /** Set position mode (one-way / hedge). <strong>WARNING.</strong> */
    public JsonNode setPositionMode(String exchange, String apiKeyId, Boolean hedged,
                                    String mode, String marketType) {
        return op("set-position-mode", body(
                "exchange",    exchange,
                "apiKeyId",    apiKeyId,
                "hedged",      hedged,
                "mode",        mode,
                "marketType",  marketType));
    }
}
