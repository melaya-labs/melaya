using System.Text.Json;

namespace Melaya;

/// <summary>
/// Strategies Platform API — team strategy listing and bulk summaries.
/// Maps to <c>/api/v1/private/strategies/*</c>.
/// Individual strategy CRUD lives on <see cref="StrategiesApi"/>.
/// </summary>
public sealed class StrategiesPlatformApi
{
    private readonly MelayaHttpClient _http;

    internal StrategiesPlatformApi(MelayaHttpClient http) => _http = http;

    /// <summary>List strategies visible to the caller's team project.</summary>
    public async Task<List<StrategySummary>> ListTeamAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<List<StrategySummary>>("/api/v1/private/strategies/team", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Bulk-fetch lightweight summaries for a list of strategy IDs.</summary>
    public async Task<List<StrategySummary>> SummariesBulkAsync(IEnumerable<string> strategyIds, CancellationToken ct = default)
    {
        var body = new { strategyIds };
        return await _http.PostAsync<List<StrategySummary>>("/api/v1/private/strategies/summaries/bulk", body, ct).ConfigureAwait(false);
    }
}

/// <summary>
/// Backtests Platform API — parameter sweep optimization.
/// Maps to <c>/api/v1/private/backtest/optimize/*</c>.
/// The core backtest CRUD lives on <see cref="BacktestApi"/>.
/// </summary>
public sealed class BacktestsPlatformApi
{
    private readonly MelayaHttpClient _http;

    internal BacktestsPlatformApi(MelayaHttpClient http) => _http = http;

    /// <summary>Start a parameter sweep optimization (genetic/grid) over a strategy config.</summary>
    public async Task<OptimizeRun> OptimizeAsync(OptimizeStartRequest body, CancellationToken ct = default)
    {
        return await _http.PostAsync<OptimizeRun>("/api/v1/private/backtest/optimize", body, ct).ConfigureAwait(false);
    }

    /// <summary>List optimization sweep runs.</summary>
    public async Task<List<OptimizeRun>> ListOptimizationsAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<List<OptimizeRun>>("/api/v1/private/backtest/optimize", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get the status/progress of an optimization sweep run.</summary>
    public async Task<OptimizeRun> GetOptimizationStatusAsync(string optRunId, CancellationToken ct = default)
    {
        return await _http.GetAsync<OptimizeRun>(
            $"/api/v1/private/backtest/optimize/{Uri.EscapeDataString(optRunId)}/status", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Cancel an in-progress optimization sweep.</summary>
    public async Task<BoolResult> CancelOptimizationAsync(string optRunId, CancellationToken ct = default)
    {
        return await _http.PostAsync<BoolResult>(
            $"/api/v1/private/backtest/optimize/{Uri.EscapeDataString(optRunId)}/cancel", null, ct).ConfigureAwait(false);
    }

    /// <summary>Apply best params from a completed optimization sweep to a strategy.</summary>
    public async Task<BoolResult> ApplyOptimizationAsync(string optRunId, CancellationToken ct = default)
    {
        return await _http.PostAsync<BoolResult>(
            $"/api/v1/private/backtest/optimize/{Uri.EscapeDataString(optRunId)}/apply", null, ct).ConfigureAwait(false);
    }
}
