package org.melaya

import org.json.JSONObject

/**
 * Strategies API — launch, control, and inspect trading strategies.
 *
 * Paths: `https://api.melaya.org/api/v1/strategies/…`
 */
class StrategiesAPI internal constructor(private val http: HttpClient) {

    /** Every strategy you own (running, paused, paper, and live). */
    fun list(): List<JSONObject> {
        return http.get("/api/v1/strategies/list")
            .asObject().getArray("strategies").toJsonObjects()
    }

    /** A single strategy by id. */
    fun get(strategyId: String): JSONObject {
        return http.get("/api/v1/strategies/$strategyId")
            .asObject().getObject("strategy")
    }

    /**
     * Launch a strategy.  Pass `dryRun = true` for paper; `dryRun = false`
     * places real orders and requires a connected `apiKeyId`.
     * Returns a JSONObject with `strategyId` and `status`.
     */
    fun create(
        name: String,
        strategyType: String,
        exchange: String,
        market: String? = null,
        symbol: String? = null,
        apiKeyId: String? = null,
        params: Map<String, Any?> = emptyMap(),
        runtimeMode: String? = null,
        dryRun: Boolean = true,
        keyBindings: Map<String, Any?> = emptyMap(),
        extra: Map<String, Any?> = emptyMap(),
    ): JSONObject {
        val body = buildMap {
            put("name", name)
            put("strategyType", strategyType)
            put("exchange", exchange)
            if (market != null) put("market", market)
            if (symbol != null) put("symbol", symbol)
            if (apiKeyId != null) put("apiKeyId", apiKeyId)
            if (params.isNotEmpty()) put("params", params)
            if (runtimeMode != null) put("runtimeMode", runtimeMode)
            put("dryRun", dryRun)
            if (keyBindings.isNotEmpty()) put("keyBindings", keyBindings)
            putAll(extra)
        }
        return http.post("/api/v1/strategies", body).asObject()
    }

    /** Pause a running strategy. */
    fun pause(strategyId: String): JSONObject {
        return http.post("/api/v1/strategies/$strategyId/pause").asObject()
    }

    /** Resume a paused strategy. */
    fun resume(strategyId: String): JSONObject {
        return http.post("/api/v1/strategies/$strategyId/resume").asObject()
    }

    /** Stop a strategy and tear down its runner. */
    fun stop(strategyId: String): JSONObject {
        return http.post("/api/v1/strategies/$strategyId/stop").asObject()
    }

    /** Soft-delete a strategy. */
    fun delete(strategyId: String): JSONObject {
        return http.delete("/api/v1/strategies/$strategyId").asObject()
    }

    /** Update a running strategy's params. */
    fun updateParams(strategyId: String, params: Map<String, Any?>): JSONObject {
        return http.post("/api/v1/strategies/$strategyId/update-params", params).asObject()
    }

    /** Live runtime status of a strategy's runner. */
    fun status(strategyId: String): JSONObject {
        return http.get("/api/v1/strategies/$strategyId/status").asObject()
    }

    /** Performance series for a strategy (equity, PnL over time). */
    fun performance(strategyId: String): List<JSONObject> {
        return http.get("/api/v1/strategies/$strategyId/performance")
            .asObject().getArray("rows").toJsonObjects()
    }

    /** Execution (order) rows for a strategy. */
    fun executions(strategyId: String): List<JSONObject> {
        return http.get("/api/v1/strategies/$strategyId/executions")
            .asObject().getArray("rows").toJsonObjects()
    }

    /** Trade (fill) rows for a strategy. */
    fun trades(strategyId: String): List<JSONObject> {
        return http.get("/api/v1/strategies/$strategyId/trades")
            .asObject().getArray("rows").toJsonObjects()
    }

    /** Log rows for a strategy (cycle markers, persona messages, errors). */
    fun logs(strategyId: String): List<JSONObject> {
        return http.get("/api/v1/strategies/$strategyId/logs")
            .asObject().getArray("rows").toJsonObjects()
    }

    // ── AI parameter optimizer ───────────────────────────────────────────────

    /**
     * Kick off an AI-driven parameter optimization.
     * `paramBounds` maps each param to a `[min, max]` pair.
     */
    fun aiOptStart(
        strategyId: String,
        paramBounds: Map<String, Pair<Double, Double>>,
        objective: String? = null,
        maxIterations: Int? = null,
        requireApproval: Boolean? = null,
    ): JSONObject {
        val body = buildMap {
            put("paramBounds", paramBounds.mapValues { (_, v) -> listOf(v.first, v.second) })
            if (objective != null) put("objective", objective)
            if (maxIterations != null) put("maxIterations", maxIterations)
            if (requireApproval != null) put("requireApproval", requireApproval)
        }
        return http.post("/api/v1/strategies/$strategyId/ai-opt/start", body).asObject()
    }

    /** Current optimization status for a strategy. */
    fun aiOptStatus(strategyId: String): JSONObject {
        return http.get("/api/v1/strategies/$strategyId/ai-opt/status").asObject()
    }

    /** Approve and apply the optimizer's proposed params. */
    fun aiOptApprove(strategyId: String, body: Map<String, Any?> = emptyMap()): JSONObject {
        return http.post("/api/v1/strategies/$strategyId/ai-opt/approve", body).asObject()
    }

    /** Stop an in-progress optimization. */
    fun aiOptStop(strategyId: String): JSONObject {
        return http.post("/api/v1/strategies/$strategyId/ai-opt/stop").asObject()
    }

    /** Past optimization runs for a strategy. */
    fun aiOptRuns(strategyId: String): Any? {
        return http.get("/api/v1/strategies/$strategyId/ai-opt/runs")
    }
}
