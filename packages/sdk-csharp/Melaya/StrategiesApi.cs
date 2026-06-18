using System.Text.Json;

namespace Melaya;

/// <summary>
/// Launch, control, and inspect trading strategies (paper + live).
/// Maps to <c>https://api.melaya.org/api/v1/strategies/*</c>.
/// </summary>
public sealed class StrategiesApi
{
    private readonly MelayaHttpClient _http;

    internal StrategiesApi(MelayaHttpClient http) => _http = http;

    /// <summary>Every strategy you own (running, paused, paper, and live).</summary>
    public async Task<List<JsonElement>> ListAsync(CancellationToken ct = default)
    {
        var r = await _http.GetAsync<StrategiesEnvelope>("/api/v1/strategies/list", ct: ct).ConfigureAwait(false);
        return r.Strategies ?? [];
    }

    /// <summary>A single strategy by id.</summary>
    public async Task<JsonElement> GetAsync(string strategyId, CancellationToken ct = default)
    {
        var r = await _http.GetAsync<StrategyEnvelope>($"/api/v1/strategies/{strategyId}", ct: ct).ConfigureAwait(false);
        return r.Strategy ?? default;
    }

    /// <summary>
    /// Launch a strategy. Pass <c>dryRun: true</c> for paper;
    /// live requires a connected <c>apiKeyId</c>.
    /// </summary>
    public async Task<StrategyCreateResult> CreateAsync(object body, CancellationToken ct = default)
    {
        return await _http.PostAsync<StrategyCreateResult>("/api/v1/strategies", body, ct).ConfigureAwait(false);
    }

    /// <summary>Pause a running strategy.</summary>
    public async Task<OkResult> PauseAsync(string strategyId, CancellationToken ct = default)
    {
        return await _http.PostAsync<OkResult>($"/api/v1/strategies/{strategyId}/pause", null, ct).ConfigureAwait(false);
    }

    /// <summary>Resume a paused strategy.</summary>
    public async Task<OkResult> ResumeAsync(string strategyId, CancellationToken ct = default)
    {
        return await _http.PostAsync<OkResult>($"/api/v1/strategies/{strategyId}/resume", null, ct).ConfigureAwait(false);
    }

    /// <summary>Stop a strategy and tear down its runner.</summary>
    public async Task<OkResult> StopAsync(string strategyId, CancellationToken ct = default)
    {
        return await _http.PostAsync<OkResult>($"/api/v1/strategies/{strategyId}/stop", null, ct).ConfigureAwait(false);
    }

    /// <summary>Soft-delete a strategy.</summary>
    public async Task<OkResult> DeleteAsync(string strategyId, CancellationToken ct = default)
    {
        return await _http.DeleteAsync<OkResult>($"/api/v1/strategies/{strategyId}", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Update a running strategy's params.</summary>
    public async Task<OkResult> UpdateParamsAsync(string strategyId, object @params, CancellationToken ct = default)
    {
        return await _http.PostAsync<OkResult>($"/api/v1/strategies/{strategyId}/update-params", @params, ct).ConfigureAwait(false);
    }

    /// <summary>Live runtime status (container health, tick count).</summary>
    public async Task<JsonElement> StatusAsync(string strategyId, CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>($"/api/v1/strategies/{strategyId}/status", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Performance series (equity, PnL over time).</summary>
    public async Task<List<JsonElement>> PerformanceAsync(string strategyId, CancellationToken ct = default)
    {
        var r = await _http.GetAsync<RowsEnvelope>($"/api/v1/strategies/{strategyId}/performance", ct: ct).ConfigureAwait(false);
        return r.Rows ?? [];
    }

    /// <summary>Execution (order) rows.</summary>
    public async Task<List<JsonElement>> ExecutionsAsync(string strategyId, CancellationToken ct = default)
    {
        var r = await _http.GetAsync<RowsEnvelope>($"/api/v1/strategies/{strategyId}/executions", ct: ct).ConfigureAwait(false);
        return r.Rows ?? [];
    }

    /// <summary>Trade (fill) rows.</summary>
    public async Task<List<JsonElement>> TradesAsync(string strategyId, CancellationToken ct = default)
    {
        var r = await _http.GetAsync<RowsEnvelope>($"/api/v1/strategies/{strategyId}/trades", ct: ct).ConfigureAwait(false);
        return r.Rows ?? [];
    }

    /// <summary>Log rows (cycle markers, persona messages, errors).</summary>
    public async Task<List<JsonElement>> LogsAsync(string strategyId, CancellationToken ct = default)
    {
        var r = await _http.GetAsync<RowsEnvelope>($"/api/v1/strategies/{strategyId}/logs", ct: ct).ConfigureAwait(false);
        return r.Rows ?? [];
    }

    // ── AI parameter optimizer ────────────────────────────────────────────────

    /// <summary>Kick off an AI-driven parameter optimization.</summary>
    public async Task<AiOptStartResult> AiOptStartAsync(
        string strategyId,
        Dictionary<string, double[]> paramBounds,
        string? objective = null,
        int? maxIterations = null,
        bool? requireApproval = null,
        CancellationToken ct = default)
    {
        var body = new { paramBounds, objective, maxIterations, requireApproval };
        return await _http.PostAsync<AiOptStartResult>($"/api/v1/strategies/{strategyId}/ai-opt/start", body, ct).ConfigureAwait(false);
    }

    /// <summary>Current optimization status for a strategy.</summary>
    public async Task<JsonElement> AiOptStatusAsync(string strategyId, CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>($"/api/v1/strategies/{strategyId}/ai-opt/status", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Approve and apply the optimizer's proposed params.</summary>
    public async Task<JsonElement> AiOptApproveAsync(string strategyId, object? body = null, CancellationToken ct = default)
    {
        return await _http.PostAsync<JsonElement>($"/api/v1/strategies/{strategyId}/ai-opt/approve", body ?? new { }, ct).ConfigureAwait(false);
    }

    /// <summary>Stop an in-progress optimization.</summary>
    public async Task<OkResult> AiOptStopAsync(string strategyId, CancellationToken ct = default)
    {
        return await _http.PostAsync<OkResult>($"/api/v1/strategies/{strategyId}/ai-opt/stop", null, ct).ConfigureAwait(false);
    }

    /// <summary>Past optimization runs for a strategy.</summary>
    public async Task<JsonElement> AiOptRunsAsync(string strategyId, CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>($"/api/v1/strategies/{strategyId}/ai-opt/runs", ct: ct).ConfigureAwait(false);
    }
}
