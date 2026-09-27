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
 *   <li>{@code /api/v1/private/projects/:project/tool-calls} — project tool-call audit ledger</li>
 *   <li>{@code /api/v1/private/pipeline-schedule} — cron scheduling</li>
 * </ul>
 *
 * <p><strong>IMPORTANT — {@code steps[]} vs {@code agents[]}:</strong> a pipeline's
 * runnable config is generated ONLY from {@code steps[]}. A top-level {@code agents[]}
 * list alone produces an EMPTY pipeline. Every step that runs an agent must embed
 * the FULL agent definition inline:
 * <pre>{@code
 * "steps": [{
 *   "kind": "agent",
 *   "agent": {
 *     "name": "researcher",
 *     "role": "...",
 *     "instruction": "...",              // the task — there is no "prompt" field
 *     "model": { "provider": "anthropic", "name": "claude-sonnet-4-6" },
 *     "agent_tools": ["..."],
 *     "human_approval_tools": ["..."]
 *   }
 * }]
 * }</pre>
 * Other notable config fields: {@code hitl_mode} ({@code "safe"} default | {@code "autonomous"} |
 * {@code "payments_only"} — only {@code "safe"} honours {@code human_approval_tools}),
 * {@code connector_source} ({@code "personal"} | {@code "project"}), {@code force_local_runner},
 * {@code inputs[]}.
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
     * Returns an ENVELOPE: {@code { name, client, config, code, docs }}. The
     * runnable pipeline configuration is {@code envelope.get("config")} — edit
     * THAT (e.g. {@code config.get("steps").get(0).get("agent")}) and pass it back
     * to {@link #update}, not the envelope itself.
     *
     * @param name    the pipeline name (URL-encoded automatically)
     * @param project optional project qualifier; pass {@code null} to omit
     */
    public JsonNode get(String name, String project) {
        Map<String, Object> q = project != null ? params("project", project) : null;
        return http.get("/api/v1/private/pipelines/" + encode(name), q);
    }

    /**
     * Update an existing pipeline — the path for editing per-agent prompts or
     * swapping a model. First {@link #get} the pipeline, mutate
     * {@code envelope.get("config")} in place (e.g. change the model on
     * {@code config.steps[0].agent}), then pass that mutated config back here.
     *
     * <p>Maps to {@code PUT /api/v1/private/pipelines/{name}}.
     *
     * @param name the pipeline name (URL-encoded automatically)
     * @param body request body containing {@code config} (the edited value from
     *             {@code get(name, project).get("config")}) and {@code project}
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
     * Returns {@code { run_id, queued, run_inputs? }} — {@code run_inputs} echoes
     * back the resolved inputs when {@code run_inputs} was sent in the request.
     *
     * <p>{@code executionTarget} is used ONLY for the tier check at request time —
     * where the run actually executes is decided by the pipeline's own stored
     * config (local model providers / {@code force_local_runner}), not by this field.
     *
     * <p>{@code env_overrides} keys prefixed {@code MEL_} or {@code MELAYA_} are
     * stripped server-side and never reach the run.
     *
     * @param name the pipeline name (URL-encoded automatically)
     * @param body optional run parameters: {@code project}, {@code executionTarget},
     *             {@code studio_url}, {@code env_overrides}, and
     *             {@code run_inputs}: {@code { brief?, values? }} where a file value
     *             inside {@code values} may be {@code { file_id }} (from
     *             {@link #uploadRunFile}), {@code { url }} (≤25 MB), or
     *             {@code { base64, name }} (≤7 MB); may be {@code null}
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

    /**
     * Upload a file for a later run. Multipart POST with a single field {@code file}
     * (built by hand — no third-party multipart dependency). Returns
     * {@code { file_id, ... }} — a single-use handle, valid 24 h. Pass it as
     * {@code run_inputs.values.<key> = { "file_id": file_id }} on {@link #run}.
     *
     * <p>Maps to {@code POST /api/v1/private/pipelines/{name}/run-files?key={key}[&project=]}.
     *
     * @param name the pipeline name (URL-encoded automatically)
     * @param key  the run-input key this file is uploaded for
     * @param file the file bytes
     * @param opts optional map: {@code filename} (defaults to {@code "file"}),
     *             {@code contentType} (defaults to {@code application/octet-stream}),
     *             {@code project}; may be {@code null}
     */
    public JsonNode uploadRunFile(String name, String key, byte[] file, Map<String, Object> opts) {
        Object project = opts != null ? opts.get("project") : null;
        Map<String, Object> query = params("key", key, "project", project);
        String filename = opts != null && opts.get("filename") != null
                ? String.valueOf(opts.get("filename")) : "file";
        String contentType = opts != null && opts.get("contentType") != null
                ? String.valueOf(opts.get("contentType")) : "application/octet-stream";
        return http.postMultipart(
                "/api/v1/private/pipelines/" + encode(name) + "/run-files",
                query, "file", file, filename, contentType);
    }

    /**
     * Get what a run was started with (brief, values, files).
     *
     * <p>Maps to {@code GET /api/v1/private/pipelines/{name}/runs/{runId}/inputs}.
     *
     * @param name  the pipeline name (URL-encoded automatically)
     * @param runId the run ID (16 hex chars)
     */
    public JsonNode runInputs(String name, String runId) {
        return http.get(
                "/api/v1/private/pipelines/" + encode(name) + "/runs/" + encode(runId) + "/inputs",
                null);
    }

    /**
     * Download one run-input file by index. Returns RAW BYTES — do not JSON-parse.
     *
     * <p>Maps to {@code GET /api/v1/private/pipelines/{name}/runs/{runId}/inputs/files/{index}}.
     *
     * @param name  the pipeline name (URL-encoded automatically)
     * @param runId the run ID (16 hex chars)
     * @param index the file index (0-99)
     */
    public byte[] runInputFile(String name, String runId, int index) {
        return http.getBytes(
                "/api/v1/private/pipelines/" + encode(name) + "/runs/" + encode(runId)
                        + "/inputs/files/" + index,
                null);
    }

    /**
     * Check whether a run is still active (liveness poll for cloud-spawn runs).
     *
     * <p>Maps to {@code GET /api/v1/private/pipelines/{name}/runs/{runId}/active}.
     * Returns {@code { active: boolean }}.
     *
     * @param name  the pipeline name (URL-encoded automatically)
     * @param runId the run ID
     */
    public JsonNode runActive(String name, String runId) {
        return http.get(
                "/api/v1/private/pipelines/" + encode(name) + "/runs/" + encode(runId) + "/active",
                null);
    }

    // ── Static-context documents ─────────────────────────────────────────────

    /**
     * List static-context documents attached to a pipeline.
     *
     * <p>Maps to {@code GET /api/v1/private/pipelines/{name}/docs}.
     */
    public JsonNode listDocs(String name) {
        return http.get("/api/v1/private/pipelines/" + encode(name) + "/docs", null);
    }

    /**
     * Upload a static-context document. Multipart POST, single field {@code file}.
     * Allowed extensions: {@code .txt .md .pdf .csv .json .docx .doc .pptx .xlsx}.
     *
     * <p>Maps to {@code POST /api/v1/private/pipelines/{name}/docs}.
     *
     * @param name the pipeline name (URL-encoded automatically)
     * @param file the file bytes
     * @param opts optional map: {@code filename}, {@code contentType}; may be {@code null}
     */
    public JsonNode uploadDoc(String name, byte[] file, Map<String, Object> opts) {
        String filename = opts != null && opts.get("filename") != null
                ? String.valueOf(opts.get("filename")) : "file";
        String contentType = opts != null && opts.get("contentType") != null
                ? String.valueOf(opts.get("contentType")) : "application/octet-stream";
        return http.postMultipart(
                "/api/v1/private/pipelines/" + encode(name) + "/docs",
                null, "file", file, filename, contentType);
    }

    /**
     * Delete a static-context document by filename.
     *
     * <p>Maps to {@code DELETE /api/v1/private/pipelines/{name}/docs/{filename}}.
     */
    public JsonNode deleteDoc(String name, String filename) {
        return http.delete(
                "/api/v1/private/pipelines/" + encode(name) + "/docs/" + encode(filename),
                null);
    }

    // ── RAG (retrieval) documents ────────────────────────────────────────────

    /**
     * Upload a RAG retrieval document. Multipart POST, single field {@code file}.
     *
     * <p>Maps to {@code POST /api/v1/private/pipelines/{name}/docs/retrieval}.
     *
     * @param name the pipeline name (URL-encoded automatically)
     * @param file the file bytes
     * @param opts optional map: {@code filename}, {@code contentType}; may be {@code null}
     */
    public JsonNode uploadRetrievalDoc(String name, byte[] file, Map<String, Object> opts) {
        String filename = opts != null && opts.get("filename") != null
                ? String.valueOf(opts.get("filename")) : "file";
        String contentType = opts != null && opts.get("contentType") != null
                ? String.valueOf(opts.get("contentType")) : "application/octet-stream";
        return http.postMultipart(
                "/api/v1/private/pipelines/" + encode(name) + "/docs/retrieval",
                null, "file", file, filename, contentType);
    }

    /**
     * Embed changed retrieval documents with the pipeline's configured embedder.
     * Can take minutes — uses a 300 s request timeout instead of the client default.
     *
     * <p>Maps to {@code POST /api/v1/private/pipelines/{name}/docs/retrieval/ingest}.
     *
     * @param name the pipeline name (URL-encoded automatically)
     * @param body optional ingest options; pass {@code null} to send {@code {}}
     */
    public JsonNode ingestRetrieval(String name, Map<String, Object> body) {
        return http.post(
                "/api/v1/private/pipelines/" + encode(name) + "/docs/retrieval/ingest",
                body != null ? body : Map.of(),
                300_000);
    }

    /**
     * Delete a RAG retrieval document by filename.
     *
     * <p>Maps to {@code DELETE /api/v1/private/pipelines/{name}/docs/retrieval/{filename}}.
     */
    public JsonNode deleteRetrievalDoc(String name, String filename) {
        return http.delete(
                "/api/v1/private/pipelines/" + encode(name) + "/docs/retrieval/" + encode(filename),
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

    // ── Tool calls / audit ────────────────────────────────────────────────────

    /**
     * Project tool-call audit ledger: every tool invocation across the project's
     * runs — tool, invoking agent, pipeline + run, who ran it, status, latency,
     * HITL approval provenance (auto vs approved + approver), 4KB-truncated
     * input/output. Keyset-paginated.
     *
     * <p>Maps to {@code GET /api/v1/private/projects/{project}/tool-calls}.
     * Returns {@code { items: ToolCall[], nextCursor: {beforeCreatedAt, beforeId} | null, capped: boolean }}.
     *
     * @param project the project name
     * @param query   optional query params: {@code beforeCreatedAt}, {@code beforeId},
     *                {@code limit} (1-100, default 30), {@code tool}, {@code agent}, {@code runId},
     *                {@code status} ({@code "ok"}|{@code "error"}), {@code search},
     *                {@code connectorSource} ({@code "project"}|{@code "personal"}),
     *                {@code approval} ({@code "auto"}|{@code "approved"}|{@code "by:<username>"}),
     *                {@code provider}, {@code sort} ({@code "recent"}|{@code "oldest"}|{@code "slowest"}|{@code "fastest"});
     *                may be {@code null}
     */
    public JsonNode projectToolCalls(String project, Map<String, Object> query) {
        return http.get("/api/v1/private/projects/" + encode(project) + "/tool-calls", query);
    }

    /**
     * Distinct tools (with call counts) and agents seen in the project's
     * tool-call ledger — powers the audit filters.
     *
     * <p>Maps to {@code GET /api/v1/private/projects/{project}/tool-calls/facets}.
     * Returns {@code { tools: [{name, count}], agents: string[] }}.
     */
    public JsonNode projectToolCallFacets(String project) {
        return http.get("/api/v1/private/projects/" + encode(project) + "/tool-calls/facets", null);
    }

    /**
     * Full, untruncated input/output for a single tool-call span in a run
     * (access-checked).
     *
     * <p>Maps to {@code GET /api/v1/private/runs/{runId}/tool-calls/{spanId}}.
     */
    public JsonNode toolCallDetail(String runId, String spanId) {
        return http.get(
                "/api/v1/private/runs/" + encode(runId) + "/tool-calls/" + encode(spanId),
                null);
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
