using System.Text.Json;

namespace Melaya;

/// <summary>
/// Paper-trading (sim broker) API — virtual balance, positions, and orders
/// per strategy. Maps to <c>https://api.melaya.org/api/v1/private/sim/*</c>.
/// </summary>
public sealed class SimApi
{
    private readonly MelayaHttpClient _http;

    internal SimApi(MelayaHttpClient http) => _http = http;

    /// <summary>Paper accounts (one virtual wallet per paper strategy).</summary>
    public async Task<List<JsonElement>> ListAccountsAsync(CancellationToken ct = default)
    {
        var r = await _http.GetAsync<JsonElement>("/api/v1/private/sim/list-accounts", ct: ct).ConfigureAwait(false);
        if (r.ValueKind == JsonValueKind.Array)
            return r.EnumerateArray().ToList();
        if (r.TryGetProperty("accounts", out var arr) && arr.ValueKind == JsonValueKind.Array)
            return arr.EnumerateArray().ToList();
        return [];
    }

    /// <summary>Virtual balance for a paper strategy.</summary>
    public async Task<JsonElement> BalanceAsync(string strategyId, string? asset = null, CancellationToken ct = default)
    {
        var q = new Dictionary<string, string?> { ["strategy_id"] = strategyId };
        if (asset is not null) q["asset"] = asset;
        return await _http.GetAsync<JsonElement>("/api/v1/private/sim/balance", q, ct).ConfigureAwait(false);
    }

    /// <summary>Open paper positions for a strategy.</summary>
    public async Task<List<JsonElement>> PositionsAsync(string strategyId, CancellationToken ct = default)
    {
        var q = new Dictionary<string, string?> { ["strategy_id"] = strategyId };
        var r = await _http.GetAsync<JsonElement>("/api/v1/private/sim/positions", q, ct).ConfigureAwait(false);
        return Unwrap(r, "positions");
    }

    /// <summary>Resting paper orders for a strategy.</summary>
    public async Task<List<JsonElement>> OpenOrdersAsync(string strategyId, CancellationToken ct = default)
    {
        var q = new Dictionary<string, string?> { ["strategy_id"] = strategyId };
        var r = await _http.GetAsync<JsonElement>("/api/v1/private/sim/open-orders", q, ct).ConfigureAwait(false);
        return Unwrap(r, "orders");
    }

    /// <summary>Filled paper trades for a strategy.</summary>
    public async Task<List<JsonElement>> MyTradesAsync(string strategyId, CancellationToken ct = default)
    {
        var q = new Dictionary<string, string?> { ["strategy_id"] = strategyId };
        var r = await _http.GetAsync<JsonElement>("/api/v1/private/sim/my-trades", q, ct).ConfigureAwait(false);
        return Unwrap(r, "trades");
    }

    /// <summary>Place a paper order. Nothing hits the real venue.</summary>
    public async Task<SimOrderResult> CreateOrderAsync(
        string strategyId,
        string exchange,
        string symbol,
        string side,
        double amount,
        string orderType = "market",
        double? price = null,
        string? market = null,
        double? leverage = null,
        bool? reduceOnly = null,
        double? slPrice = null,
        double? tpPrice = null,
        string? clientOrderId = null,
        CancellationToken ct = default)
    {
        var body = new
        {
            strategy_id       = strategyId,
            exchange,
            symbol,
            side,
            amount,
            order_type        = orderType,
            orderType,
            price,
            market,
            market_type       = market,
            leverage,
            reduceOnly,
            slPrice,
            tpPrice,
            client_order_id   = clientOrderId,
            clientOrderId,
        };
        return await _http.PostAsync<SimOrderResult>("/api/v1/private/sim/create-order", body, ct).ConfigureAwait(false);
    }

    /// <summary>Cancel a resting paper order.</summary>
    public async Task<JsonElement> CancelOrderAsync(
        string strategyId,
        string orderId,
        string? symbol = null,
        string? exchange = null,
        CancellationToken ct = default)
    {
        var body = new
        {
            strategy_id = strategyId,
            order_id    = orderId,
            orderId,
            symbol,
            exchange,
        };
        return await _http.PostAsync<JsonElement>("/api/v1/private/sim/cancel-order", body, ct).ConfigureAwait(false);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static List<JsonElement> Unwrap(JsonElement e, string key)
    {
        if (e.ValueKind == JsonValueKind.Array) return e.EnumerateArray().ToList();
        if (e.TryGetProperty(key, out var arr) && arr.ValueKind == JsonValueKind.Array)
            return arr.EnumerateArray().ToList();
        return [];
    }
}
