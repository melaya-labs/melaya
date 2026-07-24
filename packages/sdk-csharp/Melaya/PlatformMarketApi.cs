using System.Text.Json;

namespace Melaya;

/// <summary>
/// Platform Market API — authenticated market endpoints + public screener data.
/// Maps to <c>/api/v1/market/*</c> (public) and <c>/api/v1/private/market/*</c> (Forge+ tier).
/// The general market-data endpoints (ticker, orderbook, etc.) live on <see cref="MarketApi"/>.
/// These are the platform-specific additions: liquidation queries, on-chain data, and banners.
/// </summary>
public sealed class PlatformMarketApi
{
    private readonly MelayaHttpClient _http;

    internal PlatformMarketApi(MelayaHttpClient http) => _http = http;

    /// <summary>Get aggregated CEX liquidation data. Requires auth.</summary>
    public async Task<JsonElement> LiquidationsAsync(object? body = null, CancellationToken ct = default)
    {
        return await _http.PostAsync<JsonElement>("/api/v1/private/market/liquidations", body, ct).ConfigureAwait(false);
    }

    /// <summary>Get max-drawdown pairs list (public screener data).</summary>
    public async Task<JsonElement> MddPairsAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/market/mdd-pairs", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get on-chain yield data (Forge+ tier).</summary>
    public async Task<JsonElement> OnchainYieldsAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/private/market/onchain-yields", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get on-chain liquidity data (Forge+ tier).</summary>
    public async Task<JsonElement> OnchainLiquidityAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/private/market/onchain-liquidity", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get marketing/notification banner content (public).</summary>
    public async Task<JsonElement> BannerAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/market/banner", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get price history for chart display (public).</summary>
    public async Task<JsonElement> PriceHistoryAsync(string? symbol = null, string? timeframe = null, CancellationToken ct = default)
    {
        var q = Q(("symbol", symbol), ("timeframe", timeframe));
        return await _http.GetAsync<JsonElement>("/api/v1/market/price-history", q, ct).ConfigureAwait(false);
    }

    private static Dictionary<string, string?> Q(params (string Key, string? Value)[] pairs)
    {
        var d = new Dictionary<string, string?>(pairs.Length);
        foreach (var (k, v) in pairs)
            if (v is not null) d[k] = v;
        return d;
    }
}
