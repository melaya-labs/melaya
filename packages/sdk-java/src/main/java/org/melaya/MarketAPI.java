package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * REST market-data API — normalized across all 70+ venues.
 * Maps to {@code https://api.melaya.org/api/v1/market/*}.
 */
public class MarketAPI {

    private final HttpClient http;

    MarketAPI(HttpClient http) {
        this.http = http;
    }

    /** List the exchanges Melaya supports. */
    public JsonNode listExchanges() {
        return http.get("/api/v1/market/list-exchanges", null).get("exchanges");
    }

    /** Best bid/ask, last price, and 24 h aggregates for one symbol. */
    public JsonNode ticker(String exchange, String symbol, String market) {
        Map<String, Object> q = params("exchange", exchange, "symbol", symbol, "market", market);
        return http.get("/api/v1/market/ticker", q).get("ticker");
    }

    /** Order book to a given depth. */
    public JsonNode orderbook(String exchange, String symbol, String market, Integer limit) {
        Map<String, Object> q = params("exchange", exchange, "symbol", symbol, "market", market, "limit", limit);
        return http.get("/api/v1/market/orderbook", q).get("orderbook");
    }

    /** OHLCV candles for a timeframe. */
    public JsonNode ohlcv(String exchange, String symbol, String timeframe, String market, Integer limit) {
        Map<String, Object> q = params("exchange", exchange, "symbol", symbol, "timeframe", timeframe,
                "market", market, "limit", limit);
        return http.get("/api/v1/market/ohlcv", q).get("candles");
    }

    /** Recent public trades. */
    public JsonNode trades(String exchange, String symbol, String market) {
        Map<String, Object> q = params("exchange", exchange, "symbol", symbol, "market", market);
        return http.get("/api/v1/market/trades", q).get("trades");
    }

    /** Tradable markets on a venue. */
    public JsonNode markets(String exchange) {
        return http.get("/api/v1/market/markets", params("exchange", exchange)).get("markets");
    }

    /** Listed currencies on a venue. */
    public JsonNode currencies(String exchange) {
        return http.get("/api/v1/market/currencies", params("exchange", exchange)).get("currencies");
    }

    /** Operational status: ok / maintenance / degraded. */
    public JsonNode status(String exchange) {
        return http.get("/api/v1/market/status", params("exchange", exchange)).get("status");
    }

    /** Exchange server time. */
    public JsonNode time(String exchange) {
        return http.get("/api/v1/market/time", params("exchange", exchange)).get("time");
    }

    /** Tickers for many symbols on one venue in a single call (POST). */
    public JsonNode tickers(Map<String, Object> body) {
        return http.post("/api/v1/market/tickers", body).get("tickers");
    }

    /** Latest funding rates for perpetuals (POST). */
    public JsonNode fundingRates(Map<String, Object> body) {
        return http.post("/api/v1/market/funding-rates", body).get("rates");
    }

    /** Funding-rate history (POST). */
    public JsonNode fundingRateHistory(Map<String, Object> body) {
        return http.post("/api/v1/market/funding-rate-history", body).get("history");
    }

    /** Open interest for one or more perpetuals (POST). */
    public JsonNode openInterest(Map<String, Object> body) {
        return http.post("/api/v1/market/open-interest", body).get("openInterest");
    }

    /** Open-interest history (POST). */
    public JsonNode openInterestHistory(Map<String, Object> body) {
        return http.post("/api/v1/market/open-interest-history", body).get("history");
    }

    /** Instrument list + trading constraints (POST). */
    public JsonNode instruments(Map<String, Object> body) {
        return http.post("/api/v1/market/instruments", body);
    }

    /** Cross-exchange liquidation events (POST). */
    public JsonNode liquidationEvents(Map<String, Object> body) {
        return http.post("/api/v1/market/liquidation-events", body).get("events");
    }

    /** Multi-symbol OHLCV in one call (POST). */
    public JsonNode ohlcvMulti(Map<String, Object> body) {
        return http.post("/api/v1/market/ohlcv-multi", body).get("perSymbol");
    }

    /** Trading constraints for one symbol (POST). */
    public JsonNode marketConstraints(Map<String, Object> body) {
        return http.post("/api/v1/market/market-constraints", body).get("constraints");
    }

    /** Funding-rate history for one symbol across several venues (POST). */
    public JsonNode fundingRateHistoryMulti(Map<String, Object> body) {
        return http.post("/api/v1/market/funding-rate-history-multi", body).get("perExchange");
    }

    /** Open-interest history for one symbol across several venues (POST). */
    public JsonNode openInterestHistoryMulti(Map<String, Object> body) {
        return http.post("/api/v1/market/open-interest-history-multi", body).get("perExchange");
    }

    /** Prediction-market listings for a venue (POST). */
    public JsonNode predictionMarkets(Map<String, Object> body) {
        return http.post("/api/v1/market/pm-markets", body).get("markets");
    }

    /** Live platform catalog counts (public). */
    public JsonNode catalogCounts() {
        return http.get("/api/v1/public/catalog-counts", null);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Build a nullable-safe param map from alternating key/value pairs. */
    static Map<String, Object> params(Object... kv) {
        if (kv.length % 2 != 0) throw new IllegalArgumentException("params() requires even number of args");
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i + 1] != null) m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
