package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

import static org.melaya.MarketAPI.params;

/**
 * Evals API — list, compare, and inspect pipeline evaluation run results.
 *
 * <p>Maps to {@code /api/v1/private/evals/*}.
 */
public class EvalsAPI {

    private final HttpClient http;

    EvalsAPI(HttpClient http) {
        this.http = http;
    }

    /** List eval run results for the caller's tenant. */
    public JsonNode listRuns() {
        return http.get("/api/v1/private/evals/runs", null);
    }

    /** Get an aggregate summary of eval results. */
    public JsonNode summary() {
        return http.get("/api/v1/private/evals/summary", null);
    }

    /**
     * Get detailed results for a specific eval run.
     *
     * @param runId the eval run ID
     */
    public JsonNode runDetail(String runId) {
        return http.get("/api/v1/private/evals/runs/" + runId, null);
    }

    /**
     * Compare results across multiple eval runs.
     * Pass run IDs as query params; the API supports multi-value {@code runIds}.
     *
     * @param runIds comma-separated run IDs to compare
     */
    public JsonNode compare(String runIds) {
        return http.get("/api/v1/private/evals/compare", params("runIds", runIds));
    }

    /** Get memory graph visualization data for eval runs. */
    public JsonNode memoryGraph() {
        return http.get("/api/v1/private/evals/memory-graph", null);
    }

    /**
     * Get memory usage for a specific eval run.
     *
     * @param runId the eval run ID
     */
    public JsonNode runMemory(String runId) {
        return http.get("/api/v1/private/evals/runs/" + runId + "/memory", null);
    }

    /**
     * Get agent crew memory for a pipeline.
     * Pass {@code pipeline} and {@code project} query parameters.
     *
     * @param pipeline the pipeline name
     * @param project  the project name
     */
    public JsonNode crewMemory(String pipeline, String project) {
        return http.get("/api/v1/private/evals/crew-memory",
                params("pipeline", pipeline, "project", project));
    }

    /** Get benchmark scores across eval runs. */
    public JsonNode benchmarks() {
        return http.get("/api/v1/private/evals/benchmarks", null);
    }
}
