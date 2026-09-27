using System.Text.Json;

namespace Melaya;

/// <summary>
/// Evals API — query agent evaluation results, benchmarks, and memory graphs.
/// Maps to <c>/api/v1/private/evals/*</c>.
/// </summary>
public sealed class EvalsApi
{
    private readonly MelayaHttpClient _http;

    internal EvalsApi(MelayaHttpClient http) => _http = http;

    /// <summary>List eval run results for the caller's tenant.</summary>
    public async Task<List<EvalRun>> ListRunsAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<List<EvalRun>>("/api/v1/private/evals/runs", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get aggregate summary of eval results.</summary>
    public async Task<EvalSummary> SummaryAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<EvalSummary>("/api/v1/private/evals/summary", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get detailed results for a specific eval run.</summary>
    public async Task<EvalRun> RunDetailAsync(string runId, CancellationToken ct = default)
    {
        return await _http.GetAsync<EvalRun>(
            $"/api/v1/private/evals/runs/{Uri.EscapeDataString(runId)}", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Compare results across multiple eval runs.</summary>
    public async Task<JsonElement> CompareAsync(IEnumerable<string>? runIds = null, CancellationToken ct = default)
    {
        var q = new Dictionary<string, string?>();
        if (runIds is not null)
            q["runIds"] = string.Join(",", runIds);
        return await _http.GetAsync<JsonElement>("/api/v1/private/evals/compare", q, ct).ConfigureAwait(false);
    }

    /// <summary>Get memory graph visualization data for eval runs.</summary>
    public async Task<JsonElement> MemoryGraphAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/private/memory/graph", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get memory usage for a specific eval run.</summary>
    public async Task<JsonElement> RunMemoryAsync(string runId, CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>(
            $"/api/v1/private/memory/runs/{Uri.EscapeDataString(runId)}", ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Get agent crew memory for a pipeline.
    /// Pass <paramref name="pipeline"/> and <paramref name="project"/> query params.
    /// </summary>
    public async Task<JsonElement> CrewMemoryAsync(string? pipeline = null, string? project = null, CancellationToken ct = default)
    {
        var q = Q(("pipeline", pipeline), ("project", project));
        return await _http.GetAsync<JsonElement>("/api/v1/private/memory/crew", q, ct).ConfigureAwait(false);
    }

    /// <summary>Get benchmark scores across eval runs.</summary>
    public async Task<JsonElement> BenchmarksAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/private/evals/benchmarks", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Edit one persisted cross-run crew-memory entry (topic, content, and/or tags).</summary>
    public async Task<BoolResult> EditCrewMemoryEntryAsync(CrewMemoryEditRequest body, CancellationToken ct = default)
    {
        return await _http.PostAsync<BoolResult>("/api/v1/private/memory/crew/edit", body, ct).ConfigureAwait(false);
    }

    /// <summary>Delete one persisted cross-run crew-memory entry.</summary>
    /// <param name="pipeline">Pipeline / crew name that owns the entry.</param>
    /// <param name="entryId">Entry id to delete.</param>
    /// <param name="project">Optional project to disambiguate the pipeline name.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<BoolResult> DeleteCrewMemoryEntryAsync(string pipeline, string entryId, string? project = null, CancellationToken ct = default)
    {
        var body = new { pipeline, entryId, project };
        return await _http.PostAsync<BoolResult>("/api/v1/private/memory/crew/delete", body, ct).ConfigureAwait(false);
    }

    private static Dictionary<string, string?> Q(params (string Key, string? Value)[] pairs)
    {
        var d = new Dictionary<string, string?>(pairs.Length);
        foreach (var (k, v) in pairs)
            if (v is not null) d[k] = v;
        return d;
    }
}
