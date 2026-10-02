namespace Melaya;

/// <summary>
/// Pipelines API — overview, runs, traces, cron schedules, and pipeline lifecycle.
/// Maps to <c>/api/v1/private/overview/pipeline*</c>, <c>/api/v1/private/runs/:runId/traces</c>,
/// <c>/api/v1/private/pipeline-schedule</c>, and <c>/api/v1/private/pipelines/*</c>.
/// </summary>
public sealed class PipelinesApi
{
    private readonly MelayaHttpClient _http;

    internal PipelinesApi(MelayaHttpClient http) => _http = http;

    // ── Overview ──────────────────────────────────────────────────────────────

    /// <summary>Dashboard overview: usage stats, active strategies, recent runs.</summary>
    public async Task<OverviewSummary> OverviewAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<OverviewSummary>("/api/v1/private/overview", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get the usage summary (pipeline count, RAG usage, plan limits) for the sidebar/dashboard.</summary>
    public async Task<System.Text.Json.JsonElement> UsageSummaryAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<System.Text.Json.JsonElement>("/api/v1/private/overview/usage", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get pricing data for available AI models.</summary>
    public async Task<System.Text.Json.JsonElement> ModelPricesAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<System.Text.Json.JsonElement>("/api/v1/private/overview/model-prices", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get chart data for overview dashboard (cost/usage over time).</summary>
    public async Task<System.Text.Json.JsonElement> ChartDataAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<System.Text.Json.JsonElement>("/api/v1/private/overview/chart", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get cost breakdown by model/provider.</summary>
    public async Task<System.Text.Json.JsonElement> CostBreakdownAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<System.Text.Json.JsonElement>("/api/v1/private/overview/cost-breakdown", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Count of pipeline runs grouped by status.</summary>
    public async Task<PipelineCountByStatus> CountAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<PipelineCountByStatus>("/api/v1/private/overview/pipeline-count", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Paginated list of pipeline runs.</summary>
    public async Task<List<PipelineRun>> ListAsync(
        string? project = null,
        string? pipelineName = null,
        string? status = null,
        int? limit = null,
        int? offset = null,
        CancellationToken ct = default)
    {
        var q = Q(
            ("project", project),
            ("pipelineName", pipelineName),
            ("status", status),
            ("limit", limit?.ToString()),
            ("offset", offset?.ToString()));
        return await _http.GetAsync<List<PipelineRun>>("/api/v1/private/overview/pipelines", q, ct).ConfigureAwait(false);
    }

    /// <summary>Most recent pipeline runs for a dashboard widget.</summary>
    public async Task<List<PipelineRun>> RecentAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<List<PipelineRun>>("/api/v1/private/overview/pipelines/recent", ct: ct).ConfigureAwait(false);
    }

    // ── Traces ────────────────────────────────────────────────────────────────

    /// <summary>
    /// List traces for a run (paginated).
    /// Returns a <see cref="TraceListPage"/> containing <c>List</c>, <c>Total</c>, <c>Page</c>,
    /// and <c>PageSize</c>, matching the <c>{ data: { list, total, page, pageSize } }</c> server envelope.
    /// </summary>
    public async Task<TraceListPage> TracesAsync(string runId, CancellationToken ct = default)
    {
        var envelope = await _http.GetAsync<TraceListEnvelope>(
            $"/api/v1/private/runs/{Uri.EscapeDataString(runId)}/traces", ct: ct).ConfigureAwait(false);
        return envelope.Data ?? new TraceListPage();
    }

    /// <summary>Get a single trace by ID.</summary>
    public async Task<Trace> TraceAsync(string runId, string traceId, CancellationToken ct = default)
    {
        return await _http.GetAsync<Trace>(
            $"/api/v1/private/runs/{Uri.EscapeDataString(runId)}/traces/{Uri.EscapeDataString(traceId)}",
            ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get statistics for a specific trace.</summary>
    public async Task<TraceStats> TraceStatsAsync(string runId, string traceId, CancellationToken ct = default)
    {
        return await _http.GetAsync<TraceStats>(
            $"/api/v1/private/runs/{Uri.EscapeDataString(runId)}/traces/{Uri.EscapeDataString(traceId)}/stats",
            ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Delete all traces for a run by <paramref name="runId"/>.
    /// Issues DELETE <c>/api/v1/private/runs/:runId/traces</c> with no body.
    /// Returns <see cref="DeleteTracesResult"/> with <c>DeletedSpans</c> and optionally <c>RequestedTraces</c>.
    /// </summary>
    public async Task<DeleteTracesResult> DeleteTracesAsync(string runId, CancellationToken ct = default)
    {
        return await _http.DeleteAsync<DeleteTracesResult>(
            $"/api/v1/private/runs/{Uri.EscapeDataString(runId)}/traces", ct: ct).ConfigureAwait(false);
    }

    // ── Schedule ──────────────────────────────────────────────────────────────

    /// <summary>List all pipeline schedules accessible to the caller.</summary>
    public async Task<List<PipelineSchedule>> ListSchedulesAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<List<PipelineSchedule>>("/api/v1/private/pipeline-schedule", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get the schedule status for a specific pipeline.</summary>
    public async Task<PipelineSchedule> GetScheduleAsync(string project, string pipelineName, CancellationToken ct = default)
    {
        return await _http.GetAsync<PipelineSchedule>(
            $"/api/v1/private/pipeline-schedule/{Uri.EscapeDataString(project)}/{Uri.EscapeDataString(pipelineName)}",
            ct: ct).ConfigureAwait(false);
    }

    /// <summary>Create or update a pipeline schedule (cron expression + optional config).</summary>
    public async Task<PipelineSchedule> UpsertScheduleAsync(
        string project,
        string pipelineName,
        PipelineScheduleUpsertRequest body,
        CancellationToken ct = default)
    {
        return await _http.PutAsync<PipelineSchedule>(
            $"/api/v1/private/pipeline-schedule/{Uri.EscapeDataString(project)}/{Uri.EscapeDataString(pipelineName)}",
            body, ct).ConfigureAwait(false);
    }

    /// <summary>Pause a pipeline schedule.</summary>
    public async Task<BoolResult> PauseScheduleAsync(string project, string pipelineName, CancellationToken ct = default)
    {
        return await _http.PostAsync<BoolResult>(
            $"/api/v1/private/pipeline-schedule/{Uri.EscapeDataString(project)}/{Uri.EscapeDataString(pipelineName)}/pause",
            null, ct).ConfigureAwait(false);
    }

    /// <summary>Resume a paused pipeline schedule.</summary>
    public async Task<BoolResult> ResumeScheduleAsync(string project, string pipelineName, CancellationToken ct = default)
    {
        return await _http.PostAsync<BoolResult>(
            $"/api/v1/private/pipeline-schedule/{Uri.EscapeDataString(project)}/{Uri.EscapeDataString(pipelineName)}/resume",
            null, ct).ConfigureAwait(false);
    }

    // ── Pipeline lifecycle ────────────────────────────────────────────────────

    /// <summary>List all pipelines accessible to the caller.</summary>
    public async Task<PipelineListResult> ListPipelinesAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<PipelineListResult>("/api/v1/private/pipelines", ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Create a new pipeline definition.
    /// <para>
    /// The run is generated ONLY from a <c>steps</c> array in the config — a top-level
    /// <c>agents</c> array by itself produces an EMPTY pipeline. Each step embeds its own
    /// full agent definition, e.g.
    /// <c>steps: [{ kind: "agent", agent: { name, role, instruction, model: { provider, name }, agent_tools, human_approval_tools } }]</c>.
    /// There is no <c>prompt</c> field: the task goes in <c>instruction</c>, and
    /// <c>system_prompt_override</c> replaces the persona prompt entirely.
    /// </para>
    /// <para>
    /// Other config fields worth knowing: <c>hitl_mode</c> (<c>"safe"</c> default |
    /// <c>"autonomous"</c> | <c>"payments_only"</c> — only <c>"safe"</c> honours
    /// <c>human_approval_tools</c>), <c>connector_source</c> (<c>"personal"</c> |
    /// <c>"project"</c>), <c>force_local_runner</c>, and <c>inputs[]</c> (declares the
    /// <c>run_inputs</c> schema accepted by <see cref="RunAsync"/>).
    /// </para>
    /// </summary>
    /// <param name="body">Name, project, optional description, and pipeline config.</param>
    public async Task<System.Text.Json.JsonElement> CreateAsync(PipelineCreateRequest body, CancellationToken ct = default)
    {
        return await _http.PostAsync<System.Text.Json.JsonElement>("/api/v1/private/pipelines", body, ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Get a pipeline definition by name. Returns the full envelope
    /// <c>{ name, client, config, code, docs }</c> — the editable pipeline config lives under
    /// <c>config</c>. To edit and save: read <c>config</c> out of the envelope, mutate it
    /// (e.g. via <see cref="System.Text.Json.Nodes.JsonNode"/>), and pass it back to
    /// <see cref="UpdateAsync"/> as <c>PipelineUpdateRequest.Config</c>.
    /// </summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="project">Optional project scope.</param>
    public async Task<System.Text.Json.JsonElement> GetAsync(string name, string? project = null, CancellationToken ct = default)
    {
        var q = Q(("project", project));
        return await _http.GetAsync<System.Text.Json.JsonElement>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}", q, ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Update a pipeline definition. Pass the full config (typically the <c>config</c>
    /// property read back from <see cref="GetAsync"/>'s envelope) — partial merges are not
    /// supported server-side.
    /// </summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="body">Updated config and project.</param>
    public async Task<System.Text.Json.JsonElement> UpdateAsync(string name, PipelineUpdateRequest body, CancellationToken ct = default)
    {
        return await _http.PutAsync<System.Text.Json.JsonElement>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}", body, ct).ConfigureAwait(false);
    }

    /// <summary>Delete a pipeline definition.</summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="project">Optional project scope.</param>
    public async Task<BoolResult> DeleteAsync(string name, string? project = null, CancellationToken ct = default)
    {
        var q = Q(("project", project));
        return await _http.DeleteAsync<BoolResult>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}", q, ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Enqueue a pipeline run. <paramref name="body"/>.RunInputs lets you deliver a free-text
    /// brief and/or keyed values (including uploaded-file references) to the pipeline's first
    /// agent; the response echoes them back under <c>RunInputs</c> when provided.
    /// </summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="body">Optional project, execution target, studio URL, env overrides, and run inputs.</param>
    public async Task<PipelineRunResult> RunAsync(string name, PipelineRunRequest? body = null, CancellationToken ct = default)
    {
        return await _http.PostAsync<PipelineRunResult>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/run", body, ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Upload a file to reference from <see cref="PipelineRunInputs.Values"/> as <c>{ file_id }</c>.
    /// The returned <see cref="RunFileUploadResult.FileId"/> is single-use and valid for 24 hours.
    /// </summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="key">The run-inputs value key this file is destined for.</param>
    /// <param name="file">Raw file bytes.</param>
    /// <param name="filename">File name sent in the multipart part.</param>
    /// <param name="contentType">Optional MIME type for the uploaded file.</param>
    /// <param name="project">Optional project scope.</param>
    public async Task<RunFileUploadResult> UploadRunFileAsync(
        string name, string key, byte[] file, string filename,
        string? contentType = null, string? project = null, CancellationToken ct = default)
    {
        var q = Q(("key", key), ("project", project));
        return await _http.PostMultipartAsync<RunFileUploadResult>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/run-files",
            q, "file", file, filename, contentType, ct).ConfigureAwait(false);
    }

    /// <summary>List all run IDs for a pipeline.</summary>
    /// <param name="name">Pipeline name.</param>
    public async Task<PipelineRunIdsResult> RunIdsAsync(string name, CancellationToken ct = default)
    {
        return await _http.GetAsync<PipelineRunIdsResult>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/runs", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get the status of a specific run.</summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="runId">Run ID.</param>
    public async Task<PipelineRunStatus> RunStatusAsync(string name, string runId, CancellationToken ct = default)
    {
        return await _http.GetAsync<PipelineRunStatus>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/runs/{Uri.EscapeDataString(runId)}",
            ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Replace ONLY the pipeline's declared run inputs (<c>config.inputs</c>), without touching
    /// the rest of the config. Max 30; an empty list removes them all. Editor or owner only;
    /// a bad declaration fails with HTTP 422 <c>run_inputs_invalid: &lt;reason&gt;</c>.
    /// Keep keys stable: <c>{{inputs.&lt;key&gt;}}</c> placeholders in agent instructions use them.
    /// Maps to <c>PUT /api/v1/private/pipelines/{name}/inputs</c>.
    /// </summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="project">Project the pipeline belongs to.</param>
    /// <param name="inputs">The full list of declarations.</param>
    public async Task<PipelineInputsUpdateResult> SetInputsAsync(string name, string project, IReadOnlyList<PipelineInputDeclaration> inputs, CancellationToken ct = default)
    {
        var body = new Dictionary<string, object?>
        {
            ["inputs"]  = inputs ?? Array.Empty<PipelineInputDeclaration>(),
            ["project"] = project,
        };
        return await _http.PutAsync<PipelineInputsUpdateResult>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/inputs", body, ct).ConfigureAwait(false);
    }

    /// <summary>Read back the inputs (brief, values, file metadata) recorded for a run.</summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="runId">Run ID (16 hex chars).</param>
    public async Task<PipelineRunInputsResult> RunInputsAsync(string name, string runId, CancellationToken ct = default)
    {
        return await _http.GetAsync<PipelineRunInputsResult>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/runs/{Uri.EscapeDataString(runId)}/inputs",
            ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Download one run-input file's raw bytes by its index within the run's file list.
    /// The response is a binary download — it is never JSON-parsed.
    /// </summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="runId">Run ID (16 hex chars).</param>
    /// <param name="index">File index (0-99).</param>
    public async Task<byte[]> RunInputFileAsync(string name, string runId, int index, CancellationToken ct = default)
    {
        return await _http.GetBytesAsync(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/runs/{Uri.EscapeDataString(runId)}/inputs/files/{index}",
            ct: ct).ConfigureAwait(false);
    }

    /// <summary>Check whether a run is still active (queued or running).</summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="runId">Run ID.</param>
    public async Task<bool> RunActiveAsync(string name, string runId, CancellationToken ct = default)
    {
        var r = await _http.GetAsync<PipelineRunActiveResult>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/runs/{Uri.EscapeDataString(runId)}/active",
            ct: ct).ConfigureAwait(false);
        return r.Active ?? false;
    }

    /// <summary>Cancel an in-progress run.</summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="runId">Run ID to cancel.</param>
    public async Task<BoolResult> CancelRunAsync(string name, string runId, CancellationToken ct = default)
    {
        return await _http.DeleteAsync<BoolResult>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/runs/{Uri.EscapeDataString(runId)}",
            ct: ct).ConfigureAwait(false);
    }

    /// <summary>List all output artifacts for a pipeline.</summary>
    /// <param name="name">Pipeline name.</param>
    public async Task<System.Text.Json.JsonElement> OutputsAsync(string name, CancellationToken ct = default)
    {
        return await _http.GetAsync<System.Text.Json.JsonElement>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/outputs", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get a single output artifact, optionally as a download redirect.</summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="path">Output path — each segment is URL-encoded and joined with <c>/</c>.</param>
    /// <param name="download">Pass <c>true</c> to request a download redirect (<c>?download=1</c>).</param>
    public async Task<System.Text.Json.JsonElement> OutputAsync(string name, string path, bool download = false, CancellationToken ct = default)
    {
        // URL-encode each path segment individually to preserve the '/' separator
        var encodedSegments = path
            .Split('/')
            .Select(Uri.EscapeDataString);
        var encodedPath = string.Join("/", encodedSegments);

        var q = download ? Q(("download", "1")) : null;
        return await _http.GetAsync<System.Text.Json.JsonElement>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/outputs/{encodedPath}", q, ct).ConfigureAwait(false);
    }

    /// <summary>Preview the generated agent code for a pipeline config without saving.</summary>
    /// <param name="body">Pipeline config object.</param>
    public async Task<System.Text.Json.JsonElement> PreviewCodeAsync(object body, CancellationToken ct = default)
    {
        return await _http.PostAsync<System.Text.Json.JsonElement>(
            "/api/v1/private/pipelines/preview-code", body, ct).ConfigureAwait(false);
    }

    /// <summary>List all tools available in the pipeline tool registry.</summary>
    public async Task<System.Text.Json.JsonElement> ToolsAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<System.Text.Json.JsonElement>(
            "/api/v1/private/pipelines/tools", ct: ct).ConfigureAwait(false);
    }

    /// <summary>List all registered subagent definitions.</summary>
    public async Task<System.Text.Json.JsonElement> SubagentsAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<System.Text.Json.JsonElement>(
            "/api/v1/private/pipelines/subagents", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Instantiate a pipeline template, creating a new pipeline definition.</summary>
    /// <param name="templateId">Template ID to instantiate.</param>
    /// <param name="body">Name, project, and optional config overrides.</param>
    public async Task<InstantiateTemplateResult> InstantiateTemplateAsync(
        string templateId,
        InstantiateTemplateRequest body,
        CancellationToken ct = default)
    {
        return await _http.PostAsync<InstantiateTemplateResult>(
            $"/api/v1/private/templates/{Uri.EscapeDataString(templateId)}/instantiate", body, ct).ConfigureAwait(false);
    }

    /// <summary>Build a pipeline config from a plain-text brief using the Melaya AI builder.</summary>
    /// <param name="body">Brief describing the desired pipeline behaviour.</param>
    public async Task<System.Text.Json.JsonElement> BuildWithAIAsync(object body, CancellationToken ct = default)
    {
        return await _http.PostAsync<System.Text.Json.JsonElement>(
            "/api/v1/private/ai/build-pipeline/sync", body, ct).ConfigureAwait(false);
    }

    // ── Static-context documents ─────────────────────────────────────────────────

    /// <summary>List static-context documents attached to a pipeline.</summary>
    /// <param name="name">Pipeline name.</param>
    public async Task<System.Text.Json.JsonElement> ListDocsAsync(string name, CancellationToken ct = default)
    {
        return await _http.GetAsync<System.Text.Json.JsonElement>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/docs", ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Upload a static-context document for a pipeline.
    /// Allowed extensions: <c>.txt .md .pdf .csv .json .docx .doc .pptx .xlsx</c>.
    /// </summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="file">Raw file bytes.</param>
    /// <param name="filename">File name sent in the multipart part.</param>
    /// <param name="contentType">Optional MIME type for the uploaded file.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<System.Text.Json.JsonElement> UploadDocAsync(
        string name, byte[] file, string filename, string? contentType = null, CancellationToken ct = default)
    {
        return await _http.PostMultipartAsync<System.Text.Json.JsonElement>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/docs",
            null, "file", file, filename, contentType, ct).ConfigureAwait(false);
    }

    /// <summary>Delete a static-context document by filename.</summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="filename">Document filename, as returned by <see cref="ListDocsAsync"/>.</param>
    public async Task<BoolResult> DeleteDocAsync(string name, string filename, CancellationToken ct = default)
    {
        return await _http.DeleteAsync<BoolResult>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/docs/{Uri.EscapeDataString(filename)}",
            ct: ct).ConfigureAwait(false);
    }

    // ── RAG (retrieval) documents ─────────────────────────────────────────────────

    /// <summary>Upload a RAG retrieval document for a pipeline.</summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="file">Raw file bytes.</param>
    /// <param name="filename">File name sent in the multipart part.</param>
    /// <param name="contentType">Optional MIME type for the uploaded file.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<System.Text.Json.JsonElement> UploadRetrievalDocAsync(
        string name, byte[] file, string filename, string? contentType = null, CancellationToken ct = default)
    {
        return await _http.PostMultipartAsync<System.Text.Json.JsonElement>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/docs/retrieval",
            null, "file", file, filename, contentType, ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Embed changed RAG retrieval documents with the pipeline's configured embedder.
    /// Can take minutes for a large document set — defaults to a 300 s timeout for this call
    /// alone (override via <paramref name="timeoutMs"/>; does not affect other calls).
    /// </summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="body">Optional request body; sent as <c>{}</c> when omitted.</param>
    /// <param name="timeoutMs">Per-call timeout in milliseconds (default 300 000 ms / 5 min).</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<System.Text.Json.JsonElement> IngestRetrievalAsync(
        string name, object? body = null, int timeoutMs = 300_000, CancellationToken ct = default)
    {
        return await _http.PostAsync<System.Text.Json.JsonElement>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/docs/retrieval/ingest",
            body ?? new { }, ct, timeoutMs).ConfigureAwait(false);
    }

    /// <summary>Delete a RAG retrieval document by filename.</summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="filename">Document filename.</param>
    public async Task<BoolResult> DeleteRetrievalDocAsync(string name, string filename, CancellationToken ct = default)
    {
        return await _http.DeleteAsync<BoolResult>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/docs/retrieval/{Uri.EscapeDataString(filename)}",
            ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Per-document extraction stats of the static-context documents (characters kept per file),
    /// with caps for the model the agents use. Pass the model to see what fits its context window.
    /// </summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="modelName">Optional model name (sent as <c>model_name</c>).</param>
    /// <param name="modelProvider">Optional model provider (sent as <c>model_provider</c>).</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<System.Text.Json.JsonElement> DocsPreviewAsync(
        string name, string? modelName = null, string? modelProvider = null, CancellationToken ct = default)
    {
        var q = Q(("model_name", modelName), ("model_provider", modelProvider));
        return await _http.GetAsync<System.Text.Json.JsonElement>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/docs/preview", q, ct).ConfigureAwait(false);
    }

    /// <summary>Stats of the pipeline's retrieval store (documents, chunks, embedder).</summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<System.Text.Json.JsonElement> RetrievalPreviewAsync(string name, CancellationToken ct = default)
    {
        return await _http.GetAsync<System.Text.Json.JsonElement>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/docs/retrieval/preview", ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Run a sample query against the pipeline's retrieval store and see the passages agents
    /// would get. Needs an embedder configured; a store built with another embedder answers
    /// HTTP 409 (re-ingest with <see cref="IngestRetrievalAsync"/>).
    /// </summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="query">Sample query text.</param>
    /// <param name="limit">Number of passages, 1-20 (server default 5). Omitted when null.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<System.Text.Json.JsonElement> TestRetrieveAsync(
        string name, string query, int? limit = null, CancellationToken ct = default)
    {
        var body = new { query, limit };
        return await _http.PostAsync<System.Text.Json.JsonElement>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/docs/retrieval/test_retrieve", body, ct).ConfigureAwait(false);
    }

    // ── Tool-call audit ───────────────────────────────────────────────────────────
    // Maps to /api/v1/private/projects/:project/tool-calls* and
    // /api/v1/private/runs/:runId/tool-calls/:spanId — kept alongside traces since both
    // read the same per-run execution history.

    /// <summary>
    /// Paginated tool-call audit log for a project. Filter with any combination of the
    /// optional parameters; page backwards in time with <paramref name="beforeCreatedAt"/> +
    /// <paramref name="beforeId"/> (from a previous page's <c>NextCursor</c>).
    /// </summary>
    /// <param name="project">Project name.</param>
    /// <param name="beforeCreatedAt">Cursor: only calls created before this ISO timestamp.</param>
    /// <param name="beforeId">Cursor: tie-breaker id paired with <paramref name="beforeCreatedAt"/>.</param>
    /// <param name="limit">Page size, 1-100 (default 30).</param>
    /// <param name="tool">Filter by tool name.</param>
    /// <param name="agent">Filter by agent id.</param>
    /// <param name="runId">Filter by run id.</param>
    /// <param name="status">Filter by outcome: <c>"ok"</c> or <c>"error"</c>.</param>
    /// <param name="search">Free-text search across tool input/output.</param>
    /// <param name="connectorSource">Filter by connector source: <c>"project"</c> or <c>"personal"</c>.</param>
    /// <param name="approval">Filter by approval: <c>"auto"</c>, <c>"approved"</c>, or <c>"by:&lt;username&gt;"</c>.</param>
    /// <param name="provider">Filter by model provider.</param>
    /// <param name="sort">Sort order: <c>"recent"</c> (default), <c>"oldest"</c>, <c>"slowest"</c>, or <c>"fastest"</c>.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<ToolCallListResult> ProjectToolCallsAsync(
        string project,
        string? beforeCreatedAt = null,
        string? beforeId = null,
        int? limit = null,
        string? tool = null,
        string? agent = null,
        string? runId = null,
        string? status = null,
        string? search = null,
        string? connectorSource = null,
        string? approval = null,
        string? provider = null,
        string? sort = null,
        CancellationToken ct = default)
    {
        var q = Q(
            ("beforeCreatedAt", beforeCreatedAt),
            ("beforeId", beforeId),
            ("limit", limit?.ToString()),
            ("tool", tool),
            ("agent", agent),
            ("runId", runId),
            ("status", status),
            ("search", search),
            ("connectorSource", connectorSource),
            ("approval", approval),
            ("provider", provider),
            ("sort", sort));
        return await _http.GetAsync<ToolCallListResult>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/tool-calls", q, ct).ConfigureAwait(false);
    }

    /// <summary>Facet counts (tool names, agent ids) for filtering a project's tool-call audit log.</summary>
    /// <param name="project">Project name.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<ToolCallFacets> ProjectToolCallFacetsAsync(string project, CancellationToken ct = default)
    {
        return await _http.GetAsync<ToolCallFacets>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/tool-calls/facets", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get the full, untruncated input/output for one tool-call span.</summary>
    /// <param name="runId">Run ID.</param>
    /// <param name="spanId">Span ID, as returned in <see cref="ToolCallListResult.Items"/>.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<System.Text.Json.JsonElement> ToolCallDetailAsync(string runId, string spanId, CancellationToken ct = default)
    {
        return await _http.GetAsync<System.Text.Json.JsonElement>(
            $"/api/v1/private/runs/{Uri.EscapeDataString(runId)}/tool-calls/{Uri.EscapeDataString(spanId)}",
            ct: ct).ConfigureAwait(false);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static Dictionary<string, string?> Q(params (string Key, string? Value)[] pairs)
    {
        var d = new Dictionary<string, string?>(pairs.Length);
        foreach (var (k, v) in pairs)
            if (v is not null) d[k] = v;
        return d;
    }
}
