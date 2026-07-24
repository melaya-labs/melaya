package org.melaya

import org.json.JSONObject

/**
 * Backtest API — run strategies against historical data on the Rust engine.
 *
 * Paths: `https://api.melaya.org/api/v1/private/backtest/…`
 */
class BacktestAPI internal constructor(private val http: HttpClient) {

    /**
     * Start a backtest.  `mode` defaults to a single run; pass `grid_sweep` /
     * `random_sweep` with `paramRanges` to fan out a parameter search.
     * Returns a JSONObject containing `job_id`.
     */
    fun start(
        strategyType: String,
        exchange: String,
        symbol: String,
        timeframe: String,
        sinceMs: Long? = null,
        untilMs: Long? = null,
        initialEquity: Double? = null,
        params: Map<String, Any?> = emptyMap(),
        mode: String? = null,
        paramRanges: Map<String, Any?> = emptyMap(),
        randomSamples: Int? = null,
        language: String? = null,
        definition: String? = null,
        extra: Map<String, Any?> = emptyMap(),
    ): JSONObject {
        val body = buildMap {
            put("strategyType", strategyType)
            put("exchange", exchange)
            put("symbol", symbol)
            put("timeframe", timeframe)
            if (sinceMs != null) put("since_ms", sinceMs)
            if (untilMs != null) put("until_ms", untilMs)
            if (initialEquity != null) put("initial_equity", initialEquity)
            if (params.isNotEmpty()) put("params", params)
            if (mode != null) put("mode", mode)
            if (paramRanges.isNotEmpty()) put("paramRanges", paramRanges)
            if (randomSamples != null) put("randomSamples", randomSamples)
            if (language != null) put("language", language)
            if (definition != null) put("definition", definition)
            putAll(extra)
        }
        return http.post("/api/v1/private/backtest/start", body).asObject()
    }

    /** Job status + progress (`status`, `progress_pct`, ...). */
    fun job(jobId: String): JSONObject {
        return http.get("/api/v1/private/backtest/jobs/${enc(jobId)}").asObject()
    }

    /** Metrics, equity curve, and OHLCV for a completed job. */
    fun results(jobId: String): JSONObject {
        return http.get("/api/v1/private/backtest/results/${enc(jobId)}")
            .asObject().getObject("result")
    }

    /** The trade list for a completed job (default 500, max 5000 per call). */
    fun trades(jobId: String, limit: Int? = null, offset: Int? = null): List<JSONObject> {
        return http.get("/api/v1/private/backtest/trades/${enc(jobId)}", mapOf(
            "limit" to limit, "offset" to offset
        )).asObject().getArray("trades").toJsonObjects()
    }

    /** Ranked children of a sweep parent (default objective: sharpe DESC). */
    fun sweep(parentId: String, objective: String? = null, limit: Int? = null): JSONObject {
        return http.get("/api/v1/private/backtest/sweep/${enc(parentId)}", mapOf(
            "objective" to objective, "limit" to limit
        )).asObject()
    }

    /** Your backtest jobs, newest first. */
    fun list(limit: Int? = null, offset: Int? = null): List<JSONObject> {
        val r = http.get("/api/v1/private/backtest", mapOf("limit" to limit, "offset" to offset))
            .asObject()
        return r.optJSONObject("data")?.optJSONArray("jobs")?.toJsonObjects() ?: emptyList()
    }

    /** Your favorited backtest jobs (Forge tier and above). */
    fun favorites(limit: Int? = null, offset: Int? = null): List<JSONObject> {
        val r = http.get("/api/v1/private/backtest/favorites", mapOf("limit" to limit, "offset" to offset))
            .asObject()
        return r.optJSONObject("data")?.optJSONArray("jobs")?.toJsonObjects() ?: emptyList()
    }

    /** Earliest funding-rate timestamp available for an exchange+symbol (ms, or null). */
    fun fundingRange(exchange: String, symbol: String): Long? {
        val r = http.get("/api/v1/private/backtest/funding-range", mapOf(
            "exchange" to exchange, "symbol" to symbol
        )).asObject()
        return if (r.isNull("earliest_ms")) null else r.optLong("earliest_ms")
    }

    /** Cancel an in-flight job. */
    fun cancel(jobId: String): JSONObject {
        return http.post("/api/v1/private/backtest/${enc(jobId)}/cancel").asObject()
    }

    /** Soft-delete a single job. */
    fun delete(jobId: String): JSONObject {
        return http.delete("/api/v1/private/backtest/${enc(jobId)}").asObject()
    }

    /** Soft-delete every non-favorited job. Returns the count deleted. */
    fun deleteAll(): JSONObject {
        return http.delete("/api/v1/private/backtest").asObject()
    }

    // ── Parameter sweep optimization (Forge+) ───────────────────────────────

    /**
     * Start a parameter sweep optimization (genetic or grid) over a strategy config.
     * Maps to `POST /api/v1/private/backtest/optimize`.
     * Returns an object containing `optRunId`.
     */
    fun optimizeStart(body: Map<String, Any?>): JSONObject {
        return http.post("/api/v1/private/backtest/optimize", body).asObject()
    }

    /**
     * List optimization sweep runs.
     * Maps to `GET /api/v1/private/backtest/optimize`.
     */
    fun optimizeList(): List<JSONObject> {
        val r = http.get("/api/v1/private/backtest/optimize")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("runs")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /**
     * Get the status/progress of an optimization sweep run.
     * Maps to `GET /api/v1/private/backtest/optimize/:optRunId/status`.
     */
    fun optimizeStatus(optRunId: String): JSONObject {
        return http.get("/api/v1/private/backtest/optimize/${enc(optRunId)}/status").asObject()
    }

    /**
     * Cancel an in-progress optimization sweep.
     * Maps to `POST /api/v1/private/backtest/optimize/:optRunId/cancel`.
     */
    fun optimizeCancel(optRunId: String): JSONObject {
        return http.post("/api/v1/private/backtest/optimize/${enc(optRunId)}/cancel").asObject()
    }

    /**
     * Apply best params from a completed optimization sweep to a strategy.
     * Maps to `POST /api/v1/private/backtest/optimize/:optRunId/apply`.
     */
    fun optimizeApply(optRunId: String, body: Map<String, Any?> = emptyMap()): JSONObject {
        return http.post("/api/v1/private/backtest/optimize/${enc(optRunId)}/apply", body).asObject()
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
