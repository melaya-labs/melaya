package org.melaya

import org.json.JSONObject

/**
 * Evals API — list and inspect agent evaluation runs and benchmark scores.
 *
 * Paths:
 *   - `GET /api/v1/private/evals/runs`              — list eval runs
 *   - `GET /api/v1/private/evals/summary`           — aggregate summary
 *   - `GET /api/v1/private/evals/runs/:runId`       — detailed results for a run
 *   - `GET /api/v1/private/evals/compare`           — compare multiple runs
 *   - `GET /api/v1/private/evals/memory-graph`      — memory graph visualization data
 *   - `GET /api/v1/private/evals/runs/:runId/memory`— memory usage for a run
 *   - `GET /api/v1/private/evals/crew-memory`       — agent crew memory for a pipeline
 *   - `GET /api/v1/private/evals/benchmarks`        — benchmark scores across runs
 *
 * @example
 * ```kotlin
 * val runs    = melaya.evals.listRuns()
 * val summary = melaya.evals.summary()
 * val detail  = melaya.evals.runDetail(runs[0].getString("id"))
 * ```
 */
class EvalsAPI internal constructor(private val http: HttpClient) {

    /** List eval run results for the caller's tenant. */
    fun listRuns(): List<JSONObject> {
        val r = http.get("/api/v1/private/evals/runs")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("runs")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /** Get aggregate summary of eval results (pass rate, total runs, etc.). */
    fun summary(): JSONObject {
        return http.get("/api/v1/private/evals/summary").asObject()
    }

    /** Get detailed results for a specific eval run. */
    fun runDetail(runId: String): JSONObject {
        return http.get("/api/v1/private/evals/runs/${enc(runId)}").asObject()
    }

    /**
     * Compare results across multiple eval runs.
     * Pass [runIds] as a comma-separated string or list of IDs via query params.
     */
    fun compare(runIds: List<String>? = null, extra: Map<String, Any?> = emptyMap()): JSONObject {
        val query = buildMap<String, Any?> {
            if (runIds != null) put("runIds", runIds.joinToString(","))
            putAll(extra)
        }
        return http.get("/api/v1/private/evals/compare", query).asObject()
    }

    /** Get memory graph visualization data for eval runs. */
    fun memoryGraph(): JSONObject {
        return http.get("/api/v1/private/evals/memory-graph").asObject()
    }

    /** Get memory usage for a specific eval run. */
    fun runMemory(runId: String): JSONObject {
        return http.get("/api/v1/private/evals/runs/${enc(runId)}/memory").asObject()
    }

    /**
     * Get agent crew memory for a pipeline.
     * @param pipeline The pipeline name.
     * @param project  The project name.
     */
    fun crewMemory(pipeline: String, project: String): JSONObject {
        return http.get(
            "/api/v1/private/evals/crew-memory",
            mapOf("pipeline" to pipeline, "project" to project)
        ).asObject()
    }

    /** Get benchmark scores across eval runs. */
    fun benchmarks(): JSONObject {
        return http.get("/api/v1/private/evals/benchmarks").asObject()
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
