package org.melaya

import org.json.JSONObject

/**
 * Pipelines API — overview, runs, traces, schedules, model pricing, and
 * full pipeline lifecycle (CRUD, execution, outputs, AI build, templates).
 *
 * Covers:
 *   - Overview dashboard endpoints (/api/v1/private/overview/)
 *   - Pipeline run traces (/api/v1/private/runs/:runId/traces/)
 *   - Pipeline cron schedules (/api/v1/private/pipeline-schedule/)
 *   - Pipeline lifecycle (/api/v1/private/pipelines/)
 *   - AI pipeline builder (/api/v1/private/ai/build-pipeline/)
 *   - Template instantiation (/api/v1/private/templates/)
 *   - Server version (/api/v1/version)
 *
 * Paths:
 *   - `GET    /api/v1/private/overview`                                   — dashboard overview
 *   - `GET    /api/v1/private/overview/model-prices`                      — AI model pricing
 *   - `GET    /api/v1/private/overview/chart`                             — cost/usage chart
 *   - `GET    /api/v1/private/overview/cost-breakdown`                    — cost breakdown
 *   - `GET    /api/v1/private/overview/pipeline-count`                    — counts by status
 *   - `GET    /api/v1/private/overview/pipelines`                         — paginated run list
 *   - `GET    /api/v1/private/overview/pipelines/recent`                  — recent runs widget
 *   - `GET    /api/v1/private/runs/:runId/traces`                         — paginated traces for a run
 *   - `GET    /api/v1/private/runs/:runId/traces/:traceId`                — single trace
 *   - `GET    /api/v1/private/runs/:runId/traces/:traceId/stats`          — trace statistics
 *   - `DELETE /api/v1/private/runs/:runId/traces`                         — delete run traces
 *   - `GET    /api/v1/private/pipeline-schedule`                          — list schedules
 *   - `GET    /api/v1/private/pipeline-schedule/:p/:n`                    — schedule status
 *   - `PUT    /api/v1/private/pipeline-schedule/:p/:n`                    — upsert schedule
 *   - `POST   /api/v1/private/pipeline-schedule/:p/:n/pause`              — pause schedule
 *   - `POST   /api/v1/private/pipeline-schedule/:p/:n/resume`             — resume schedule
 *   - `GET    /api/v1/private/pipelines`                                  — list pipeline configs
 *   - `POST   /api/v1/private/pipelines`                                  — create pipeline
 *   - `GET    /api/v1/private/pipelines/:name`                            — get pipeline config
 *   - `PUT    /api/v1/private/pipelines/:name`                            — update pipeline config
 *   - `DELETE /api/v1/private/pipelines/:name`                            — delete pipeline
 *   - `POST   /api/v1/private/pipelines/:name/run`                        — trigger a run
 *   - `GET    /api/v1/private/pipelines/:name/runs`                       — list run IDs
 *   - `GET    /api/v1/private/pipelines/:name/runs/:runId`                — run status
 *   - `DELETE /api/v1/private/pipelines/:name/runs/:runId`                — cancel a run
 *   - `GET    /api/v1/private/pipelines/:name/outputs`                    — list output artifacts
 *   - `GET    /api/v1/private/pipelines/:name/outputs/:path`              — get a single artifact
 *   - `POST   /api/v1/private/pipelines/preview-code`                     — preview generated code
 *   - `GET    /api/v1/private/pipelines/tools`                            — tool registry
 *   - `GET    /api/v1/private/pipelines/subagents`                        — subagent registry
 *   - `POST   /api/v1/private/templates/:templateId/instantiate`          — instantiate template
 *   - `POST   /api/v1/private/ai/build-pipeline/sync`                     — build pipeline with AI
 *   - `GET    /api/v1/version`                                             — server version
 *
 * @example
 * ```kotlin
 * val summary = melaya.pipelines.overview()
 * val runs = melaya.pipelines.list(project = "my-project", limit = 20)
 * melaya.pipelines.upsertSchedule("my-project", "nightly", cron = "0 2 * * *")
 *
 * // Lifecycle
 * val config = melaya.pipelines.create(name = "my-pipeline", project = "my-project")
 * val run    = melaya.pipelines.run(name = "my-pipeline", project = "my-project")
 * val status = melaya.pipelines.runStatus(name = "my-pipeline", runId = run.getString("run_id"))
 * ```
 *
 * ### Key config fields
 *
 * A pipeline's run is generated ONLY from `steps[]` — a top-level `agents[]` array alone
 * produces an EMPTY pipeline. Each step embeds its own agent definition:
 *
 * ```kotlin
 * "steps" to listOf(mapOf(
 *     "kind" to "agent",
 *     "agent" to mapOf(
 *         "name"        to "researcher",
 *         "role"        to "Research analyst",
 *         "instruction" to "Summarize the attached report.",         // the task — there is no `prompt` field
 *         "model"       to mapOf("provider" to "anthropic", "name" to "claude-sonnet-4-6"),
 *         "agent_tools" to listOf("web_search"),
 *         "human_approval_tools" to emptyList<String>(),
 *     ),
 * ))
 * ```
 *
 * `instruction` is the agent's task; `system_prompt_override` replaces its whole system prompt
 * (there is no plain `prompt` field). Other config fields worth knowing:
 *   - `hitl_mode` — `"safe"` (default) | `"autonomous"` | `"payments_only"`. Only `"safe"` honours
 *     each agent's `human_approval_tools`.
 *   - `connector_source` — `"personal"` | `"project"`: which credential pool a run draws from.
 *   - `force_local_runner` — force execution on the user's own runner regardless of `executionTarget`.
 *   - `inputs[]` — declares the run-time inputs (brief/values/files) a pipeline accepts; validated
 *     server-side against `run_inputs` passed to [run].
 */
class PipelinesAPI internal constructor(private val http: HttpClient) {

    // ── Overview ─────────────────────────────────────────────────────────────

    /** Dashboard overview: usage stats, active strategies, recent runs. */
    fun overview(): JSONObject {
        return http.get("/api/v1/private/overview").asObject()
    }

    /** Get pricing data for available AI models. */
    fun modelPrices(): JSONObject {
        return http.get("/api/v1/private/overview/model-prices").asObject()
    }

    /** Get chart data for the overview dashboard (cost/usage over time). */
    fun chartData(): JSONObject {
        return http.get("/api/v1/private/overview/chart").asObject()
    }

    /** Get cost breakdown by model/provider. */
    fun costBreakdown(): JSONObject {
        return http.get("/api/v1/private/overview/cost-breakdown").asObject()
    }

    /** Get count of pipeline runs grouped by status. */
    fun count(): JSONObject {
        return http.get("/api/v1/private/overview/pipeline-count").asObject()
    }

    /**
     * Paginated list of pipeline runs.
     *
     * @param project      Filter by project name.
     * @param pipelineName Filter by pipeline name.
     * @param status       Filter by status (pending/running/done/error/…).
     * @param limit        Page size.
     * @param offset       Pagination offset.
     * @param page         Page number (alternative to offset).
     */
    fun list(
        project: String? = null,
        pipelineName: String? = null,
        status: String? = null,
        limit: Int? = null,
        offset: Int? = null,
        page: Int? = null,
    ): List<JSONObject> {
        val query = buildMap<String, Any?> {
            if (project != null)      put("project",      project)
            if (pipelineName != null) put("pipelineName", pipelineName)
            if (status != null)       put("status",       status)
            if (limit != null)        put("limit",        limit)
            if (offset != null)       put("offset",       offset)
            if (page != null)         put("page",         page)
        }
        val r = http.get("/api/v1/private/overview/pipelines", query)
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("runs")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /** Most recent pipeline runs for a dashboard widget. */
    fun recent(): List<JSONObject> {
        val r = http.get("/api/v1/private/overview/pipelines/recent")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("runs")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    // ── Traces ───────────────────────────────────────────────────────────────

    /**
     * List traces for a run — returns the server's paginated envelope.
     *
     * The server returns `{ data: { list: [...], total: Int, page: Int, pageSize: Int } }`.
     * Use `result.getJSONObject("data")` to reach the inner envelope, then
     * `.getJSONArray("list")` for the trace rows.
     *
     * @param page     1-based page number (default 1).
     * @param pageSize Number of traces per page (server default 10, max 100).
     */
    fun traces(
        runId: String,
        page: Int? = null,
        pageSize: Int? = null,
    ): JSONObject {
        val query = buildMap<String, Any?> {
            if (page != null)     put("page",     page)
            if (pageSize != null) put("pageSize", pageSize)
        }
        return http.get("/api/v1/private/runs/${enc(runId)}/traces", query).asObject()
    }

    /** Get a single trace by [traceId] within a run. */
    fun trace(runId: String, traceId: String): JSONObject {
        return http.get("/api/v1/private/runs/${enc(runId)}/traces/${enc(traceId)}").asObject()
    }

    /** Get statistics for a specific trace. */
    fun traceStats(runId: String, traceId: String): JSONObject {
        return http.get("/api/v1/private/runs/${enc(runId)}/traces/${enc(traceId)}/stats").asObject()
    }

    /**
     * Delete all traces (and their spans) for a run by [runId].
     *
     * Issues `DELETE /api/v1/private/runs/:runId/traces` with no request body.
     *
     * @return Object with keys `deletedSpans` (Int) and optionally `requestedTraces` (Int).
     */
    fun deleteTraces(runId: String): JSONObject {
        return http.delete("/api/v1/private/runs/${enc(runId)}/traces").asObject()
    }

    // ── Schedules ────────────────────────────────────────────────────────────

    /** List all pipeline schedules accessible to the caller. */
    fun listSchedules(): List<JSONObject> {
        val r = http.get("/api/v1/private/pipeline-schedule")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("schedules")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /** Get the schedule status for a specific pipeline. */
    fun getSchedule(project: String, pipelineName: String): JSONObject {
        return http.get("/api/v1/private/pipeline-schedule/${enc(project)}/${enc(pipelineName)}").asObject()
    }

    /**
     * Create or update a pipeline schedule.
     *
     * @param cron   Standard cron expression (e.g. `"0 2 * * *"` for 02:00 UTC daily).
     * @param config Optional pipeline config to pass on each scheduled run.
     */
    fun upsertSchedule(
        project: String,
        pipelineName: String,
        cron: String,
        config: Map<String, Any?> = emptyMap(),
    ): JSONObject {
        val body = buildMap<String, Any?> {
            put("cron", cron)
            if (config.isNotEmpty()) put("config", config)
        }
        return http.put(
            "/api/v1/private/pipeline-schedule/${enc(project)}/${enc(pipelineName)}",
            body
        ).asObject()
    }

    /** Pause a pipeline schedule. */
    fun pauseSchedule(project: String, pipelineName: String): JSONObject {
        return http.post(
            "/api/v1/private/pipeline-schedule/${enc(project)}/${enc(pipelineName)}/pause"
        ).asObject()
    }

    /** Resume a paused pipeline schedule. */
    fun resumeSchedule(project: String, pipelineName: String): JSONObject {
        return http.post(
            "/api/v1/private/pipeline-schedule/${enc(project)}/${enc(pipelineName)}/resume"
        ).asObject()
    }

    // ── Server version (standalone) ──────────────────────────────────────────

    /** Return the current server version string. Does not require authentication. */
    fun serverVersion(): JSONObject {
        return http.get("/api/v1/version").asObject()
    }

    // ── Pipeline lifecycle ────────────────────────────────────────────────────

    /**
     * List all pipeline configurations accessible to the caller.
     *
     * @return Object with a `pipelines` array containing each pipeline's config.
     */
    fun listPipelines(): JSONObject {
        return http.get("/api/v1/private/pipelines").asObject()
    }

    /**
     * Create a new pipeline configuration.
     *
     * @param name        Unique pipeline name within the project.
     * @param project     Project the pipeline belongs to.
     * @param description Optional human-readable description.
     * @param config      Additional pipeline configuration fields merged into the body.
     * @return The created pipeline config object.
     */
    fun create(
        name: String,
        project: String,
        description: String? = null,
        config: Map<String, Any?> = emptyMap(),
    ): JSONObject {
        val body = buildMap<String, Any?> {
            put("name", name)
            put("project", project)
            if (description != null) put("description", description)
            putAll(config)
        }
        return http.post("/api/v1/private/pipelines", body).asObject()
    }

    /**
     * Get a single pipeline configuration by name.
     *
     * Returns the full ENVELOPE `{ name, client, config, code, docs }` — the pipeline's
     * agents/steps/model config lives under `envelope.getJSONObject("config")`. To edit and
     * save, mutate that inner `config` object and pass IT (not the envelope) to [update].
     *
     * @example
     * ```kotlin
     * val envelope = melaya.pipelines.get("my-pipeline", project = "my-project")
     * val config   = envelope.getJSONObject("config")
     * config.getJSONArray("steps").getJSONObject(0).getJSONObject("agent")
     *     .put("model", mapOf("provider" to "anthropic", "name" to "claude-opus-4-8"))
     * melaya.pipelines.update("my-pipeline", config.toMap(), project = "my-project")
     * ```
     *
     * @param name    The pipeline name (URL-encoded automatically).
     * @param project Optional project filter.
     * @return The envelope object `{ name, client, config, code, docs }`.
     */
    fun get(name: String, project: String? = null): JSONObject {
        val query = buildMap<String, Any?> {
            if (project != null) put("project", project)
        }
        return http.get("/api/v1/private/pipelines/${enc(name)}", query).asObject()
    }

    /**
     * Update an existing pipeline configuration.
     *
     * @param name    The pipeline name to update (URL-encoded automatically).
     * @param config  The pipeline's config object (agents/steps/model/wiring) — typically
     *                [get]'s `envelope.config`, edited in place. NOT the whole envelope.
     * @param project Project that owns the pipeline.
     * @return The updated pipeline config object.
     */
    fun update(
        name: String,
        config: Map<String, Any?>,
        project: String? = null,
    ): JSONObject {
        val body = buildMap<String, Any?> {
            put("config", config)
            if (project != null) put("project", project)
        }
        return http.put("/api/v1/private/pipelines/${enc(name)}", body).asObject()
    }

    /**
     * Delete a pipeline configuration.
     *
     * @param name    The pipeline name to delete (URL-encoded automatically).
     * @param project Project that owns the pipeline.
     * @return Empty object on success.
     */
    fun delete(name: String, project: String? = null): JSONObject {
        val query = buildMap<String, Any?> {
            if (project != null) put("project", project)
        }
        return http.delete("/api/v1/private/pipelines/${enc(name)}", query).asObject()
    }

    /**
     * Trigger an execution run for a pipeline.
     *
     * @param name            The pipeline name (URL-encoded automatically).
     * @param project         Optional project override.
     * @param executionTarget Used ONLY for the tier check (e.g. gating cloud-spawn to paid tiers).
     *                        It does NOT decide where the run actually executes — that is decided
     *                        by the pipeline's stored config (local model providers / `force_local_runner`).
     * @param studioUrl       Optional Studio URL to associate with the run.
     * @param envOverrides    Environment variable overrides injected at runtime. `MEL_*` /
     *                        `MELAYA_*` keys are always stripped server-side and can never be overridden.
     * @param runInputs       Optional run inputs: `{ brief?, values? }`. File values inside `values`
     *                        may be `{ "file_id" to id }` (from [uploadRunFile]), `{ "url" to url }`
     *                        (≤25 MB), or `{ "base64" to data, "name" to filename }` (≤7 MB).
     * @return Object with `run_id` (String), `queued` (Boolean), and — when [runInputs] was
     *   supplied and echoed back by the server — a `run_inputs` field.
     */
    fun run(
        name: String,
        project: String? = null,
        executionTarget: String? = null,
        studioUrl: String? = null,
        envOverrides: Map<String, Any?> = emptyMap(),
        runInputs: Map<String, Any?>? = null,
    ): JSONObject {
        val body = buildMap<String, Any?> {
            if (project != null)         put("project",         project)
            if (executionTarget != null) put("executionTarget", executionTarget)
            if (studioUrl != null)       put("studio_url",      studioUrl)
            if (envOverrides.isNotEmpty()) put("env_overrides", envOverrides)
            if (runInputs != null)       put("run_inputs",      runInputs)
        }
        return http.post("/api/v1/private/pipelines/${enc(name)}/run", body).asObject()
    }

    /**
     * List run IDs for a pipeline.
     *
     * @param name The pipeline name (URL-encoded automatically).
     * @return List of run ID strings extracted from the `run_ids` array.
     */
    fun runIds(name: String): List<String> {
        val r = http.get("/api/v1/private/pipelines/${enc(name)}/runs")
        return when (r) {
            is org.json.JSONArray -> r.toAnyList().map { it.toString() }
            is JSONObject -> r.optJSONArray("run_ids")?.toAnyList()?.map { it.toString() } ?: emptyList()
            else -> emptyList()
        }
    }

    /**
     * Get the status of a specific run.
     *
     * @param name  The pipeline name (URL-encoded automatically).
     * @param runId The run ID (URL-encoded automatically).
     * @return Object with `runId`, `status`, `createdAt`, `executionTarget`, and `cost`.
     */
    fun runStatus(name: String, runId: String): JSONObject {
        return http.get(
            "/api/v1/private/pipelines/${enc(name)}/runs/${enc(runId)}"
        ).asObject()
    }

    /**
     * Cancel an in-progress run.
     *
     * @param name  The pipeline name (URL-encoded automatically).
     * @param runId The run ID to cancel (URL-encoded automatically).
     * @return Empty object on success.
     */
    fun cancelRun(name: String, runId: String): JSONObject {
        return http.delete(
            "/api/v1/private/pipelines/${enc(name)}/runs/${enc(runId)}"
        ).asObject()
    }

    /** Poll whether a run is still active (liveness check for cloud-spawn runs). */
    fun runActive(name: String, runId: String): JSONObject {
        return http.get(
            "/api/v1/private/pipelines/${enc(name)}/runs/${enc(runId)}/active"
        ).asObject()
    }

    // ── Run inputs (brief / values / files) ─────────────────────────────────────

    /**
     * Upload a file for use in a later [run] via `run_inputs.values`.
     *
     * The returned `file_id` is single-use and valid for 24 hours: pass it back as
     * `run_inputs.values.<key> = mapOf("file_id" to file_id)`.
     *
     * @param name        The pipeline name (URL-encoded automatically).
     * @param key         The run-input key this file is being uploaded for.
     * @param file        Raw file bytes.
     * @param project     Optional project to disambiguate the pipeline name.
     * @param filename    Filename sent with the upload (defaults to `"upload"`).
     * @param contentType Optional MIME type for the file part.
     * @return Object with `file_id` (String) and upload metadata.
     */
    fun uploadRunFile(
        name: String,
        key: String,
        file: ByteArray,
        project: String? = null,
        filename: String = "upload",
        contentType: String? = null,
    ): JSONObject {
        val query = buildMap<String, Any?> {
            put("key", key)
            if (project != null) put("project", project)
        }
        return http.postMultipart(
            "/api/v1/private/pipelines/${enc(name)}/run-files",
            query, "file", file, filename, contentType,
        ).asObject()
    }

    /**
     * Get the inputs a run was started with (brief, values, files).
     *
     * @param name  The pipeline name (URL-encoded automatically).
     * @param runId The run ID — 16 hex characters (URL-encoded automatically).
     */
    fun runInputs(name: String, runId: String): JSONObject {
        return http.get(
            "/api/v1/private/pipelines/${enc(name)}/runs/${enc(runId)}/inputs"
        ).asObject()
    }

    /**
     * Download one input file attached to a run.
     *
     * Returns the RAW file bytes — do not JSON-parse the result.
     *
     * @param name  The pipeline name (URL-encoded automatically).
     * @param runId The run ID — 16 hex characters (URL-encoded automatically).
     * @param index The file's index within the run's inputs, `0..99`.
     */
    fun runInputFile(name: String, runId: String, index: Int): ByteArray {
        return http.getBytes(
            "/api/v1/private/pipelines/${enc(name)}/runs/${enc(runId)}/inputs/files/$index"
        )
    }

    // ── Static-context documents ─────────────────────────────────────────────

    /** List static-context documents attached to a pipeline. */
    fun listDocs(name: String): JSONObject {
        return http.get("/api/v1/private/pipelines/${enc(name)}/docs").asObject()
    }

    /**
     * Upload a static-context document for a pipeline.
     *
     * Allowed extensions: `.txt .md .pdf .csv .json .docx .doc .pptx .xlsx`.
     *
     * @param name        The pipeline name (URL-encoded automatically).
     * @param file        Raw file bytes.
     * @param filename    Filename sent with the upload (defaults to `"upload"`).
     * @param contentType Optional MIME type for the file part.
     */
    fun uploadDoc(
        name: String,
        file: ByteArray,
        filename: String = "upload",
        contentType: String? = null,
    ): JSONObject {
        return http.postMultipart(
            "/api/v1/private/pipelines/${enc(name)}/docs",
            emptyMap(), "file", file, filename, contentType,
        ).asObject()
    }

    /** Delete a static-context document by [filename]. */
    fun deleteDoc(name: String, filename: String): JSONObject {
        return http.delete("/api/v1/private/pipelines/${enc(name)}/docs/${enc(filename)}").asObject()
    }

    // ── RAG (retrieval) documents ─────────────────────────────────────────────

    /**
     * Upload a document into the pipeline's retrieval (RAG) store.
     *
     * @param name        The pipeline name (URL-encoded automatically).
     * @param file        Raw file bytes.
     * @param filename    Filename sent with the upload (defaults to `"upload"`).
     * @param contentType Optional MIME type for the file part.
     */
    fun uploadRetrievalDoc(
        name: String,
        file: ByteArray,
        filename: String = "upload",
        contentType: String? = null,
    ): JSONObject {
        return http.postMultipart(
            "/api/v1/private/pipelines/${enc(name)}/docs/retrieval",
            emptyMap(), "file", file, filename, contentType,
        ).asObject()
    }

    /**
     * Ingest (embed) changed files in the pipeline's retrieval store with its configured
     * embedder. Can take minutes, so this call uses a 300s request timeout.
     *
     * @param name The pipeline name (URL-encoded automatically).
     * @param body Optional extra ingest options; sent as `{}` by default.
     */
    fun ingestRetrieval(name: String, body: Map<String, Any?> = emptyMap()): JSONObject {
        return http.post(
            "/api/v1/private/pipelines/${enc(name)}/docs/retrieval/ingest",
            body,
            timeoutMs = 300_000L,
        ).asObject()
    }

    /** Delete a retrieval-store document by [filename] (and its chunks upstream). */
    fun deleteRetrievalDoc(name: String, filename: String): JSONObject {
        return http.delete(
            "/api/v1/private/pipelines/${enc(name)}/docs/retrieval/${enc(filename)}"
        ).asObject()
    }

    /**
     * List all output artifacts produced by a pipeline.
     *
     * @param name The pipeline name (URL-encoded automatically).
     * @return Artifact listing object.
     */
    fun outputs(name: String): JSONObject {
        return http.get("/api/v1/private/pipelines/${enc(name)}/outputs").asObject()
    }

    /**
     * Retrieve a single output artifact by path.
     *
     * Each segment of [path] is URL-encoded individually and joined with `/`.
     *
     * @param name     The pipeline name (URL-encoded automatically).
     * @param path     Artifact path (e.g. `"results/data.csv"`). Segments are encoded separately.
     * @param download Pass `true` to request a download URL instead of the artifact metadata.
     * @return Artifact object (or redirect info when [download] is `true`).
     */
    fun output(name: String, path: String, download: Boolean = false): JSONObject {
        val encodedPath = path.split("/").joinToString("/") { enc(it) }
        val query = buildMap<String, Any?> {
            if (download) put("download", "1")
        }
        return http.get(
            "/api/v1/private/pipelines/${enc(name)}/outputs/$encodedPath",
            query
        ).asObject()
    }

    /**
     * Preview the generated code for a pipeline configuration without saving it.
     *
     * @param config Pipeline configuration map to preview.
     * @return Preview object containing the generated code and metadata.
     */
    fun previewCode(config: Map<String, Any?>): JSONObject {
        return http.post("/api/v1/private/pipelines/preview-code", config).asObject()
    }

    /**
     * Retrieve the tool registry available to pipelines.
     *
     * @return Registry object listing all available tools and their schemas.
     */
    fun tools(): JSONObject {
        return http.get("/api/v1/private/pipelines/tools").asObject()
    }

    /**
     * Retrieve the subagent registry available to pipelines.
     *
     * @return Registry object listing all available subagents and their schemas.
     */
    fun subagents(): JSONObject {
        return http.get("/api/v1/private/pipelines/subagents").asObject()
    }

    // ── Template instantiation ────────────────────────────────────────────────

    /**
     * Instantiate a pipeline from a published template.
     *
     * @param templateId The template ID to instantiate (URL-encoded automatically).
     * @param name       Name for the new pipeline instance.
     * @param project    Project the new pipeline will belong to.
     * @param overrides  Optional map of template parameter overrides.
     * @return Object with a `pipeline` key containing the new pipeline config.
     */
    fun instantiateTemplate(
        templateId: String,
        name: String,
        project: String,
        overrides: Map<String, Any?> = emptyMap(),
    ): JSONObject {
        val body = buildMap<String, Any?> {
            put("name", name)
            put("project", project)
            if (overrides.isNotEmpty()) put("overrides", overrides)
        }
        return http.post(
            "/api/v1/private/templates/${enc(templateId)}/instantiate",
            body
        ).asObject()
    }

    // ── AI pipeline builder ───────────────────────────────────────────────────

    /**
     * Build a pipeline configuration from a natural-language brief using AI.
     *
     * Calls the synchronous endpoint and blocks until the AI returns the config.
     *
     * @param brief Map describing the pipeline to build (e.g. `mapOf("prompt" to "…")`).
     * @return The generated pipeline config object.
     */
    fun buildWithAI(brief: Map<String, Any?>): JSONObject {
        return http.post("/api/v1/private/ai/build-pipeline/sync", brief).asObject()
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
