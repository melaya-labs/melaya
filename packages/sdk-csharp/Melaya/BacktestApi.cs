using System.Text.Json;

namespace Melaya;

/// <summary>
/// Historical backtests + parameter sweeps on the Rust engine.
/// Maps to <c>https://api.melaya.org/api/v1/private/backtest/*</c>.
/// </summary>
public sealed class BacktestApi
{
    private readonly MelayaHttpClient _http;

    internal BacktestApi(MelayaHttpClient http) => _http = http;

    /// <summary>
    /// Start a backtest. <c>mode</c> defaults to a single run; pass
    /// <c>grid_sweep</c> / <c>random_sweep</c> with <c>paramRanges</c> to fan out.
    /// </summary>
    public async Task<BacktestStartResult> StartAsync(object body, CancellationToken ct = default)
    {
        return await _http.PostAsync<BacktestStartResult>("/api/v1/private/backtest/start", body, ct).ConfigureAwait(false);
    }

    /// <summary>Job status + progress (<c>status</c>, <c>progress_pct</c>, ...).</summary>
    public async Task<JsonElement> JobAsync(string jobId, CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>($"/api/v1/private/backtest/jobs/{jobId}", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Metrics, equity curve, and OHLCV for a completed job.</summary>
    public async Task<JsonElement> ResultsAsync(string jobId, CancellationToken ct = default)
    {
        var r = await _http.GetAsync<ResultEnvelope>($"/api/v1/private/backtest/results/{jobId}", ct: ct).ConfigureAwait(false);
        return r.Result ?? default;
    }

    /// <summary>The trade list for a completed job (default 500, max 5 000 per call).</summary>
    public async Task<List<JsonElement>> TradesAsync(string jobId, int? limit = null, int? offset = null, CancellationToken ct = default)
    {
        var q = new Dictionary<string, string?>();
        if (limit  is not null) q["limit"]  = limit.ToString();
        if (offset is not null) q["offset"] = offset.ToString();
        var r = await _http.GetAsync<TradesEnvelope>($"/api/v1/private/backtest/trades/{jobId}", q, ct).ConfigureAwait(false);
        return r.Trades ?? [];
    }

    /// <summary>Ranked children of a sweep parent.</summary>
    public async Task<JsonElement> SweepAsync(string parentId, string? objective = null, int? limit = null, CancellationToken ct = default)
    {
        var q = new Dictionary<string, string?>();
        if (objective is not null) q["objective"] = objective;
        if (limit     is not null) q["limit"]     = limit.ToString();
        return await _http.GetAsync<JsonElement>($"/api/v1/private/backtest/sweep/{parentId}", q, ct).ConfigureAwait(false);
    }

    /// <summary>Your backtest jobs, newest first.</summary>
    public async Task<List<JsonElement>> ListAsync(int? limit = null, int? offset = null, CancellationToken ct = default)
    {
        var q = new Dictionary<string, string?>();
        if (limit  is not null) q["limit"]  = limit.ToString();
        if (offset is not null) q["offset"] = offset.ToString();
        var r = await _http.GetAsync<BacktestListEnvelope>("/api/v1/private/backtest", q, ct).ConfigureAwait(false);
        return r.Data?.Jobs ?? [];
    }

    /// <summary>Your favorited backtest jobs.</summary>
    public async Task<List<JsonElement>> FavoritesAsync(int? limit = null, int? offset = null, CancellationToken ct = default)
    {
        var q = new Dictionary<string, string?>();
        if (limit  is not null) q["limit"]  = limit.ToString();
        if (offset is not null) q["offset"] = offset.ToString();
        var r = await _http.GetAsync<BacktestListEnvelope>("/api/v1/private/backtest/favorites", q, ct).ConfigureAwait(false);
        return r.Data?.Jobs ?? [];
    }

    /// <summary>Earliest funding-rate timestamp available for an exchange+symbol (ms, or null).</summary>
    public async Task<long?> FundingRangeAsync(string exchange, string symbol, CancellationToken ct = default)
    {
        var q = new Dictionary<string, string?> { ["exchange"] = exchange, ["symbol"] = symbol };
        var r = await _http.GetAsync<EarliestMsEnvelope>("/api/v1/private/backtest/funding-range", q, ct).ConfigureAwait(false);
        return r.EarliestMs;
    }

    /// <summary>Cancel an in-flight job.</summary>
    public async Task<JsonElement> CancelAsync(string jobId, CancellationToken ct = default)
    {
        return await _http.PostAsync<JsonElement>($"/api/v1/private/backtest/{jobId}/cancel", null, ct).ConfigureAwait(false);
    }

    /// <summary>Soft-delete a single job.</summary>
    public async Task<OkResult> DeleteAsync(string jobId, CancellationToken ct = default)
    {
        return await _http.DeleteAsync<OkResult>($"/api/v1/private/backtest/{jobId}", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Soft-delete every non-favorited job. Returns the count deleted.</summary>
    public async Task<DeleteAllResult> DeleteAllAsync(CancellationToken ct = default)
    {
        return await _http.DeleteAsync<DeleteAllResult>("/api/v1/private/backtest", ct: ct).ConfigureAwait(false);
    }
}
