package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

import static org.melaya.MarketAPI.params;

/**
 * Pipelines API — full lifecycle management for Melaya pipelines.
 *
 * <p>Covers:
 * <ul>
 *   <li>{@code /api/v1/private/pipelines} — CRUD (list, create, get, update, delete)</li>
 *   <li>{@code /api/v1/private/pipelines/{name}/run} — trigger and cancel runs</li>
 *   <li>{@code /api/v1/private/pipelines/{name}/runs} — run IDs and status</li>
 *   <li>{@code /api/v1/private/pipelines/{name}/outputs} — artifact access</li>
 *   <li>{@code /api/v1/private/pipelines/preview-code} — code preview</li>
 *   <li>{@code /api/v1/private/pipelines/tools} and {@code /subagents} — registries</li>
 *   <li>{@code /api/v1/private/templates/{id}/instantiate} — template instantiation</li>
 *   <li>{@code /api/v1/private/ai/build-pipeline/sync} — AI-assisted build</li>
 *   <li>{@code /api/v1/private/overview/pipeline*} — listing/counting runs</li>
 *   <li>{@code /api/v1/private/runs/:runId/traces} — trace access</li>
 *   <li>{@code /api/v1/private/pipeline-schedule} — cron scheduling</li>
 * </ul>
 *
 * @example
 * <pre>{@code
 * // List all pipelines in a project
 * JsonNode all = melaya.pipelines().listPipelines(Map.of("project", "my-project"));
 *
 * // Create and immediately run a pipeline
 * JsonNode cfg = melaya.pipelines().create(Map.of(
 *     "name",    "nightly-report",
 *     "project", "my-project"));
 * JsonNode run = melaya.pipelines().run("nightly-report",
 *     Map.of("project", "my-project"));
 *
 * // Check status
 * JsonNode status = melaya.pipelines().runStatus("nightly-report",
 *     run.get("run_id").asText());
 *
 * // Legacy schedule helper
 * melaya.pipelines().upsertSchedule("my-project", "nightly",
 *     Map.of("cron", "0 2 * * *"));
 * }</pre>
 */
public class PipelinesAPI {

    private final HttpClient http;

    PipelinesAPI(HttpClient http) {
        this.http = http;
    }

    // ── Pipeline CRUD ─────────────────────────────────────────────────────────

    /**
     * List all pipelines visible to the caller.
     *
     * <p>Maps to {@code GET /api/v1/private/pipelines}.
     * Returns {@code { pipelines: [...] }}.
     *
     * @param query optional query params (e.g. {@code project}); may be {@code null}
     */
    public JsonNode listPipelines(Map<String, Object> query) {
        return http.get("/api/v1/private/pipelines", query);
    }

    /**
     * Create a new pipeline.
     *
     * <p>Maps to {@code POST /api/v1/private/pipelines}.
     *
     * @param body request body containing at minimum {@code name} and {@code project};
     *             optional fields: {@code description} and any pipeline config keys
     */
    public JsonNode create(Map<String, Object> body) {
        return http.post("/api/v1/private/pipelines", body);
    }

    /**
     * Get a pipeline's full configuration by name.
     *
     * <p>Maps to {@code GET /api/v1/private/pipelines/{name}}.
     *
     * @param name    the pipeline name (URL-encoded automatically)
     * @param project optional project qualifier; pass {@code null} to omit
     */
    public JsonNode get(String name, String project) {
        Map<String, Object> q = project != null ? params("project", project) : null;
        return http.get("/api/v1/private/pipelines/" + encode(name), q);
    }

    /**
     * Update an existing pipeline.
     *
     * <p>Maps to {@code PUT /api/v1/private/pipelines/{name}}.
     *
     * @param name the pipeline name (URL-encoded automatically)
     * @param body request body containing {@code config} and {@code project}
     */
    public JsonNode update(String name, Map<String, Object> body) {
        return http.put("/api/v1/private/pipelines/" + encode(name), body);
    }

    /**
     * Delete a pipeline.
     *
     * <p>Maps to {@code DELETE /api/v1/private/pipelines/{name}?project=}.
     *
     * @param name    the pipeline name (URL-encoded automatically)
     * @param project optional project qualifier; pass {@code null} to omit
     */
    public JsonNode delete(String name, String project) {
        Map<String, Object> q = project != null ? params("project", project) : null;
        return http.delete("/api/v1/private/pipelines/" + encode(name), q);
    }

    // ── Run lifecycle ─────────────────────────────────────────────────────────

    /**
     * Trigger a pipeline run.
     *
     * <p>Maps to {@code POST /api/v1/private/pipelines/{name}/run}.
     * Returns {@code { run_id, queued }}.
     *
     * @param name the pipeline name (URL-encoded automatically)
     * @param body optional run parameters: {@code project}, {@code executionTarget},
     *             {@code studio_url}, {@code env_overrides}; may be {@code null}
     */
    public JsonNode run(String name, Map<String, Object> body) {
        return http.post("/api/v1/private/pipelines/" + encode(name) + "/run", body);
    }

    /**
     * List run IDs for a pipeline.
     *
     * <p>Maps to {@code GET /api/v1/private/pipelines/{name}/runs}.
     * Returns {@code { run_ids: [...] }}.
     *
     * @param name the pipeline name (URL-encoded automatically)
     */
    public JsonNode runIds(String name) {
        return http.get("/api/v1/private/pipelines/" + encode(name) + "/runs", null);
    }

    /**
     * Get the status of a specific run.
     *
     * <p>Maps to {@code GET /api/v1/private/pipelines/{name}/runs/{runId}}.
     * Returns {@code { runId, status, createdAt, executionTarget, cost }}.
     *
     * @param name  the pipeline name (URL-encoded automatically)
     * @param runId the run ID (URL-encoded automatically)
     */
    public JsonNode runStatus(String name, String runId) {
        return http.get(
                "/api/v1/private/pipelines/" + encode(name) + "/runs/" + encode(runId),
                null);
    }

    /**
     * Cancel an in-progress run.
     *
     * <p>Maps to {@code DELETE /api/v1/private/pipelines/{name}/runs/{runId}}.
     *
     * @param name  the pipeline name (URL-encoded automatically)
     * @param runId the run ID (URL-encoded automatically)
     */
    public JsonNode cancelRun(String name, String runId) {
        return http.delete(
                "/api/v1/private/pipelines/" + encode(name) + "/runs/" + encode(runId),
                null);
    }

    // ── Outputs ───────────────────────────────────────────────────────────────

    /**
     * List output artifacts for a pipeline.
     *
     * <p>Maps to {@code GET /api/v1/private/pipelines/{name}/outputs}.
     *
     * @param name the pipeline name (URL-encoded automatically)
     */
    public JsonNode outputs(String name) {
        return http.get("/api/v1/private/pipelines/" + encode(name) + "/outputs", null);
    }

    /**
     * Get a single output artifact by path.
     *
     * <p>Maps to {@code GET /api/v1/private/pipelines/{name}/outputs/{path}}.
     * Each segment of {@code path} is URL-encoded individually and joined with {@code /}.
     * Pass {@code download=true} to receive a presigned download URL.
     *
     * @param name     the pipeline name (URL-encoded automatically)
     * @param path     the artifact path (e.g. {@code "results/report.pdf"});
     *                 each {@code /}-delimited segment is encoded separately
     * @param download if {@code true}, appends {@code ?download=1} to request a download URL
     */
    public JsonNode output(String name, String path, boolean download) {
        String encodedPath = encodePathSegments(path);
        Map<String, Object> q = download ? params("download", 1) : null;
        return http.get(
                "/api/v1/private/pipelines/" + encode(name) + "/outputs/" + encodedPath,
                q);
    }

    // ── Code preview & registries ─────────────────────────────────────────────

    /**
     * Preview generated code for a pipeline configuration.
     *
     * <p>Maps to {@code POST /api/v1/private/pipelines/preview-code}.
     *
     * @param config the pipeline configuration to preview
     */
    public JsonNode previewCode(Map<String, Object> config) {
        return http.post("/api/v1/private/pipelines/preview-code", config);
    }

    /**
     * Get the available tools registry.
     *
     * <p>Maps to {@code GET /api/v1/private/pipelines/tools}.
     */
    public JsonNode tools() {
        return http.get("/api/v1/private/pipelines/tools", null);
    }

    /**
     * Get the available subagents registry.
     *
     * <p>Maps to {@code GET /api/v1/private/pipelines/subagents}.
     */
    public JsonNode subagents() {
        return http.get("/api/v1/private/pipelines/subagents", null);
    }

    // ── Template instantiation ────────────────────────────────────────────────

    /**
     * Instantiate a validated template into a runnable pipeline.
     *
     * <p>Maps to {@code POST /api/v1/private/templates/{templateId}/instantiate}.
     * Returns {@code { pipeline }}.
     *
     * @param templateId the template ID (URL-encoded automatically)
     * @param body       request body containing {@code name}, {@code project},
     *                   and optional {@code overrides}
     */
    public JsonNode instantiateTemplate(String templateId, Map<String, Object> body) {
        return http.post(
                "/api/v1/private/templates/" + encode(templateId) + "/instantiate",
                body);
    }

    // ── AI build ─────────────────────────────────────────────────────────────

    /**
     * Build a pipeline configuration from a natural-language brief using AI.
     *
     * <p>Maps to {@code POST /api/v1/private/ai/build-pipeline/sync}.
     *
     * @param brief the build brief (passed directly as the request body)
     */
    public JsonNode buildWithAI(Map<String, Object> brief) {
        return http.post("/api/v1/private/ai/build-pipeline/sync", brief);
    }

    // ── Traces ────────────────────────────────────────────────────────────────

    /**
     * Get the paginated trace list for a run.
     *
     * <p>Maps to {@code GET /api/v1/private/runs/:runId/traces}.
     * Returns a paginated envelope:
     * <pre>{@code
     * {
     *   "data": {
     *     "list":     [ { traceId, traceName, startTime, endTime, status,
     *                     spanCount, totalTokens }, ... ],
     *     "total":    <int>,
     *     "page":     <int>,
     *     "pageSize": <int>
     *   }
     * }
     * }</pre>
     * Use {@code node.at("/data/list")} to access the trace rows and
     * {@code node.at("/data/total")} for the total count.
     *
     * @param runId the pipeline run ID
     */
    public JsonNode traces(String runId) {
        return http.get("/api/v1/private/runs/" + encode(runId) + "/traces", null);
    }

    /**
     * Get a single trace by ID.
     *
     * @param runId   the pipeline run ID
     * @param traceId the trace ID
     */
    public JsonNode trace(String runId, String traceId) {
        return http.get(
                "/api/v1/private/runs/" + encode(runId) + "/traces/" + encode(traceId),
                null);
    }

    /**
     * Get statistics for a specific trace.
     *
     * @param runId   the pipeline run ID
     * @param traceId the trace ID
     */
    public JsonNode traceStats(String runId, String traceId) {
        return http.get(
                "/api/v1/private/runs/" + encode(runId) + "/traces/" + encode(traceId) + "/stats",
                null);
    }

    /**
     * Delete all traces (and their spans) for a run.
     *
     * <p>Maps to {@code DELETE /api/v1/private/runs/:runId/traces} (no body).
     * Returns {@code { deletedSpans: <int>, requestedTraces?: <int> }}.
     *
     * @param runId the pipeline run ID
     */
    public JsonNode deleteTraces(String runId) {
        return http.delete("/api/v1/private/runs/" + encode(runId) + "/traces", null);
    }

    // ── Schedule ──────────────────────────────────────────────────────────────

    /** List all pipeline schedules accessible to the caller. */
    public JsonNode listSchedules() {
        return http.get("/api/v1/private/pipeline-schedule", null);
    }

    /**
     * Get the schedule status for a specific pipeline.
     *
     * @param project      the project name
     * @param pipelineName the pipeline name
     */
    public JsonNode getSchedule(String project, String pipelineName) {
        return http.get(
                "/api/v1/private/pipeline-schedule/" + encode(project) + "/" + encode(pipelineName),
                null);
    }

    /**
     * Create or update a pipeline schedule (cron expression + optional config).
     *
     * @param project      the project name
     * @param pipelineName the pipeline name
     * @param body         map containing {@code cron} (required) and optional {@code config}
     */
    public JsonNode upsertSchedule(String project, String pipelineName, Map<String, Object> body) {
        return http.put(
                "/api/v1/private/pipeline-schedule/" + encode(project) + "/" + encode(pipelineName),
                body);
    }

    /**
     * Pause a pipeline schedule.
     *
     * @param project      the project name
     * @param pipelineName the pipeline name
     */
    public JsonNode pauseSchedule(String project, String pipelineName) {
        return http.post(
                "/api/v1/private/pipeline-schedule/" + encode(project) + "/" + encode(pipelineName) + "/pause",
                null);
    }

    /**
     * Resume a paused pipeline schedule.
     *
     * @param project      the project name
     * @param pipelineName the pipeline name
     */
    public JsonNode resumeSchedule(String project, String pipelineName) {
        return http.post(
                "/api/v1/private/pipeline-schedule/" + encode(project) + "/" + encode(pipelineName) + "/resume",
                null);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    /**
     * URL-encodes each {@code /}-delimited segment of {@code path} independently
     * and rejoins them with {@code /}.  This preserves the path hierarchy while
     * ensuring special characters inside segment names are percent-encoded.
     */
    private static String encodePathSegments(String path) {
        if (path == null || path.isEmpty()) return "";
        String[] segments = path.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(encode(segments[i]));
        }
        return sb.toString();
    }
}
