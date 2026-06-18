package org.melaya

import org.json.JSONArray
import org.json.JSONObject

/**
 * REST market-data API — normalized across all 70+ venues.
 *
 * Paths: `https://api.melaya.org/api/v1/market/…`
 */
class MarketAPI internal constructor(private val http: HttpClient) {

    /** List the exchanges Melaya supports right now. */
    fun listExchanges(): List<JSONObject> {
        return http.get("/api/v1/market/list-exchanges")
            .asObject().getArray("exchanges").toJsonObjects()
    }

    /** Best bid/ask, last price, and 24h aggregates for one symbol. */
    fun ticker(exchange: String, symbol: String, market: String? = null): JSONObject {
        return http.get("/api/v1/market/ticker", mapOf(
            "exchange" to exchange, "symbol" to symbol, "market" to market
        )).asObject().getObject("ticker")
    }

    /** Order book to a given depth. */
    fun orderbook(exchange: String, symbol: String, market: String? = null, limit: Int? = null): JSONObject {
        return http.get("/api/v1/market/orderbook", mapOf(
            "exchange" to exchange, "symbol" to symbol, "market" to market, "limit" to limit
        )).asObject().getObject("orderbook")
    }

    /** OHLCV candles for a timeframe. Each candle is [ts, open, high, low, close, volume]. */
    fun ohlcv(exchange: String, symbol: String, timeframe: String, market: String? = null, limit: Int? = null): JSONArray {
        return http.get("/api/v1/market/ohlcv", mapOf(
            "exchange" to exchange, "symbol" to symbol, "timeframe" to timeframe,
            "market" to market, "limit" to limit
        )).asObject().getArray("candles")
    }

    /** Recent public trades. */
    fun trades(exchange: String, symbol: String, market: String? = null): JSONArray {
        return http.get("/api/v1/market/trades", mapOf(
            "exchange" to exchange, "symbol" to symbol, "market" to market
        )).asObject().getArray("trades")
    }

    /** Tradable markets on a venue. */
    fun markets(exchange: String): JSONArray {
        return http.get("/api/v1/market/markets", mapOf("exchange" to exchange))
            .asObject().getArray("markets")
    }

    /** Listed currencies on a venue. */
    fun currencies(exchange: String): JSONArray {
        return http.get("/api/v1/market/currencies", mapOf("exchange" to exchange))
            .asObject().getArray("currencies")
    }

    /** Operational status: ok / maintenance / degraded. */
    fun status(exchange: String): JSONObject {
        return http.get("/api/v1/market/status", mapOf("exchange" to exchange))
            .asObject().getObject("status")
    }

    /** Exchange server time. */
    fun time(exchange: String): Any? {
        val obj = http.get("/api/v1/market/time", mapOf("exchange" to exchange)).asObject()
        return obj.opt("time")
    }

    // ── Batch / derivatives (POST) ───────────────────────────────────────────

    /** Tickers for many symbols on one venue in a single call. Keyed by symbol. */
    fun tickers(exchange: String, symbols: List<String>, market: String? = null): JSONObject {
        val body = buildMap {
            put("exchange", exchange); put("symbols", symbols)
            if (market != null) put("market", market)
        }
        return http.post("/api/v1/market/tickers", body).asObject().getObject("tickers")
    }

    /** Latest funding rates for perpetuals. Keyed by symbol. */
    fun fundingRates(exchange: String, symbols: List<String>, market: String? = null): JSONObject {
        val body = buildMap {
            put("exchange", exchange); put("symbols", symbols)
            if (market != null) put("market", market)
        }
        return http.post("/api/v1/market/funding-rates", body).asObject().getObject("rates")
    }

    /** Funding-rate history. */
    fun fundingRateHistory(exchange: String, symbol: String, hours: Int? = null, market: String? = null): JSONArray {
        val body = buildMap {
            put("exchange", exchange); put("symbol", symbol)
            if (hours != null) put("hours", hours)
            if (market != null) put("market", market)
        }
        return http.post("/api/v1/market/funding-rate-history", body).asObject().getArray("history")
    }

    /** Open interest for one or more perpetuals. Keyed by symbol. */
    fun openInterest(exchange: String, symbols: List<String>, market: String? = null): JSONObject {
        val body = buildMap {
            put("exchange", exchange); put("symbols", symbols)
            if (market != null) put("market", market)
        }
        return http.post("/api/v1/market/open-interest", body).asObject().getObject("openInterest")
    }

    /** Open-interest history. */
    fun openInterestHistory(exchange: String, symbol: String, hours: Int? = null, market: String? = null): JSONArray {
        val body = buildMap {
            put("exchange", exchange); put("symbol", symbol)
            if (hours != null) put("hours", hours)
            if (market != null) put("market", market)
        }
        return http.post("/api/v1/market/open-interest-history", body).asObject().getArray("history")
    }

    /** Instrument list + trading constraints (tick size, min notional, qty step). */
    fun instruments(exchange: String, market: String? = null): Any? {
        val body = buildMap {
            put("exchange", exchange)
            if (market != null) put("market", market)
        }
        return http.post("/api/v1/market/instruments", body)
    }

    /** Cross-exchange liquidation events (historical query). */
    fun liquidationEvents(
        exchange: String? = null, symbol: String? = null,
        sinceMs: Long? = null, limit: Int? = null,
    ): JSONArray {
        val body = buildMap {
            if (exchange != null) put("exchange", exchange)
            if (symbol != null) put("symbol", symbol)
            if (sinceMs != null) put("sinceMs", sinceMs)
            if (limit != null) put("limit", limit)
        }
        return http.post("/api/v1/market/liquidation-events", body).asObject().getArray("events")
    }

    /** Multi-symbol OHLCV in one call. Returns candle arrays keyed by symbol. */
    fun ohlcvMulti(exchange: String, symbols: List<String>, timeframe: String, limit: Int? = null, market: String? = null): JSONObject {
        val body = buildMap {
            put("exchange", exchange); put("symbols", symbols); put("timeframe", timeframe)
            if (limit != null) put("limit", limit)
            if (market != null) put("market", market)
        }
        return http.post("/api/v1/market/ohlcv-multi", body).asObject().getObject("perSymbol")
    }

    /** Trading constraints for one symbol (tick size, min notional, qty step, leverage). */
    fun marketConstraints(exchange: String, symbol: String, market: String? = null): Any? {
        val body = buildMap {
            put("exchange", exchange); put("symbol", symbol)
            if (market != null) put("market", market)
        }
        return http.post("/api/v1/market/market-constraints", body).asObject().opt("constraints")
    }

    /** Funding-rate history for one symbol across several venues. Keyed by exchange. */
    fun fundingRateHistoryMulti(exchanges: List<String>, symbol: String, hours: Int? = null): JSONObject {
        val body = buildMap {
            put("exchanges", exchanges); put("symbol", symbol)
            if (hours != null) put("hours", hours)
        }
        return http.post("/api/v1/market/funding-rate-history-multi", body).asObject().getObject("perExchange")
    }

    /** Open-interest history for one symbol across several venues. Keyed by exchange. */
    fun openInterestHistoryMulti(exchanges: List<String>, symbol: String, hours: Int? = null): JSONObject {
        val body = buildMap {
            put("exchanges", exchanges); put("symbol", symbol)
            if (hours != null) put("hours", hours)
        }
        return http.post("/api/v1/market/open-interest-history-multi", body).asObject().getObject("perExchange")
    }

    /** Prediction-market listings for a venue (polymarket, kalshi, drift_pm, sxbet, azuro, overtime). */
    fun predictionMarkets(venue: String = "polymarket"): JSONArray {
        return http.post("/api/v1/market/pm-markets", mapOf("venue" to venue))
            .asObject().getArray("markets")
    }

    /** Live platform catalog counts (agentic tools, subagents, by category). */
    fun catalogCounts(): JSONObject {
        return http.get("/api/v1/public/catalog-counts").asObject()
    }
}
