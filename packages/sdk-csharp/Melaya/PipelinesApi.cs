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

    /// <summary>Create a new pipeline definition.</summary>
    /// <param name="body">Name, project, optional description, and pipeline config.</param>
    public async Task<System.Text.Json.JsonElement> CreateAsync(PipelineCreateRequest body, CancellationToken ct = default)
    {
        return await _http.PostAsync<System.Text.Json.JsonElement>("/api/v1/private/pipelines", body, ct).ConfigureAwait(false);
    }

    /// <summary>Get a pipeline definition by name.</summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="project">Optional project scope.</param>
    public async Task<System.Text.Json.JsonElement> GetAsync(string name, string? project = null, CancellationToken ct = default)
    {
        var q = Q(("project", project));
        return await _http.GetAsync<System.Text.Json.JsonElement>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}", q, ct).ConfigureAwait(false);
    }

    /// <summary>Update a pipeline definition.</summary>
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

    /// <summary>Enqueue a pipeline run.</summary>
    /// <param name="name">Pipeline name.</param>
    /// <param name="body">Optional project, execution target, studio URL, and env overrides.</param>
    public async Task<PipelineRunResult> RunAsync(string name, PipelineRunRequest? body = null, CancellationToken ct = default)
    {
        return await _http.PostAsync<PipelineRunResult>(
            $"/api/v1/private/pipelines/{Uri.EscapeDataString(name)}/run", body, ct).ConfigureAwait(false);
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

    // ── Helper ────────────────────────────────────────────────────────────────

    private static Dictionary<string, string?> Q(params (string Key, string? Value)[] pairs)
    {
        var d = new Dictionary<string, string?>(pairs.Length);
        foreach (var (k, v) in pairs)
            if (v is not null) d[k] = v;
        return d;
    }
}
