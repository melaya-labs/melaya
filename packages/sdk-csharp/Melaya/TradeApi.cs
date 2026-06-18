using System.Text.Json;
using System.Text.Json.Nodes;

namespace Melaya;

/// <summary>
/// Live trading API — credentialed order placement, account state, and
/// position management on a CONNECTED exchange.
///
/// Every method POSTs to <c>https://api.melaya.org/api/v1/private/&lt;op&gt;</c>; the server
/// resolves your connected exchange credential (referenced by <c>ApiKeyId</c> — see
/// <see cref="AccountApi.KeysAsync"/>) and forwards the call to the venue through
/// Melaya's in-house Rust engine. Responses share an envelope:
/// <c>{ ok, exchange, operation, orderId, clientOrderId, payload, data, ... }</c>.
///
/// ⚠️  These hit the REAL venue with REAL funds. The write methods
/// (CreateOrderAsync, CancelOrderAsync, AmendOrderAsync, CancelAllOrdersAsync,
/// CancelPlanOrdersAsync, ClosePositionAsync, SetLeverageAsync, SetMarginModeAsync,
/// SetPositionModeAsync) move money or change account state. For risk-free
/// testing use <see cref="SimApi"/> (paper) or a paper strategy instead.
/// </summary>
public sealed class TradeApi
{
    private readonly MelayaHttpClient _http;

    internal TradeApi(MelayaHttpClient http) => _http = http;

    private Task<JsonElement> Op(string op, object body, CancellationToken ct = default)
        => _http.PostAsync<JsonElement>($"/api/v1/private/{op}", body, ct);

    // ── Account state (reads) ─────────────────────────────────────────────────

    /// <summary>Live account balance on a connected venue.</summary>
    public Task<JsonElement> BalanceAsync(
        string exchange,
        string? apiKeyId = null,
        string? keyId = null,
        string? marketType = null,
        object? @params = null,
        CancellationToken ct = default)
        => Op("balance", Clean(new
        {
            exchange,
            apiKeyId,
            keyId,
            marketType,
            @params,
        }), ct);

    /// <summary>Live open positions.</summary>
    public Task<JsonElement> PositionsAsync(
        string exchange,
        string? apiKeyId = null,
        string? marketType = null,
        string? symbol = null,
        object? @params = null,
        CancellationToken ct = default)
        => Op("positions", Clean(new
        {
            exchange,
            apiKeyId,
            marketType,
            symbol,
            @params,
        }), ct);

    /// <summary>Historical positions (venue-dependent).</summary>
    public Task<JsonElement> PositionsHistoryAsync(
        string exchange,
        string? apiKeyId = null,
        string? marketType = null,
        string? symbol = null,
        CancellationToken ct = default)
        => Op("positions-history", Clean(new
        {
            exchange,
            apiKeyId,
            marketType,
            symbol,
        }), ct);

    /// <summary>Resting (open) orders.</summary>
    public Task<JsonElement> OpenOrdersAsync(
        string exchange,
        string? apiKeyId = null,
        string? marketType = null,
        string? symbol = null,
        CancellationToken ct = default)
        => Op("open-orders", Clean(new
        {
            exchange,
            apiKeyId,
            marketType,
            symbol,
        }), ct);

    /// <summary>All orders (open + recent).</summary>
    public Task<JsonElement> OrdersAsync(
        string exchange,
        string? apiKeyId = null,
        string? marketType = null,
        string? symbol = null,
        CancellationToken ct = default)
        => Op("orders", Clean(new
        {
            exchange,
            apiKeyId,
            marketType,
            symbol,
        }), ct);

    /// <summary>Closed/filled orders.</summary>
    public Task<JsonElement> ClosedOrdersAsync(
        string exchange,
        string? apiKeyId = null,
        string? marketType = null,
        string? symbol = null,
        CancellationToken ct = default)
        => Op("closed-orders", Clean(new
        {
            exchange,
            apiKeyId,
            marketType,
            symbol,
        }), ct);

    /// <summary>Your trade (fill) history.</summary>
    public Task<JsonElement> MyTradesAsync(
        string exchange,
        string? apiKeyId = null,
        string? marketType = null,
        string? symbol = null,
        CancellationToken ct = default)
        => Op("my-trades", Clean(new
        {
            exchange,
            apiKeyId,
            marketType,
            symbol,
        }), ct);

    /// <summary>Extended trade history (venue-dependent).</summary>
    public Task<JsonElement> MyTradesHistoryAsync(
        string exchange,
        string? apiKeyId = null,
        string? marketType = null,
        string? symbol = null,
        CancellationToken ct = default)
        => Op("my-trades-history", Clean(new
        {
            exchange,
            apiKeyId,
            marketType,
            symbol,
        }), ct);

    /// <summary>Resting conditional/plan (trigger) orders.</summary>
    public Task<JsonElement> PlanOrdersAsync(
        string exchange,
        string? apiKeyId = null,
        string? marketType = null,
        string? symbol = null,
        CancellationToken ct = default)
        => Op("plan-orders", Clean(new
        {
            exchange,
            apiKeyId,
            marketType,
            symbol,
        }), ct);

    /// <summary>Current leverage for a symbol.</summary>
    public Task<JsonElement> LeverageAsync(
        string exchange,
        string? apiKeyId = null,
        string? symbol = null,
        string? marketType = null,
        CancellationToken ct = default)
        => Op("leverage", Clean(new
        {
            exchange,
            apiKeyId,
            symbol,
            marketType,
        }), ct);

    /// <summary>Leverage tiers / brackets for a symbol.</summary>
    public Task<JsonElement> LeverageTiersAsync(
        string exchange,
        string? apiKeyId = null,
        string? symbol = null,
        string? marketType = null,
        CancellationToken ct = default)
        => Op("leverage-tiers", Clean(new
        {
            exchange,
            apiKeyId,
            symbol,
            marketType,
        }), ct);

    // ── Order placement & management (LIVE writes — real funds) ────────────────

    /// <summary>
    /// Place a live order on the venue.
    /// ⚠️  Real money. <c>stopPrice</c>, <c>takeProfitPrice</c>, and <c>reduceOnly</c>
    /// are folded into the <c>params</c> object before dispatch.
    /// </summary>
    public Task<JsonElement> CreateOrderAsync(
        string exchange,
        string symbol,
        string side,
        double amount,
        string? apiKeyId = null,
        string type = "market",
        double? price = null,
        string? marketType = null,
        double? stopPrice = null,
        double? takeProfitPrice = null,
        bool? reduceOnly = null,
        double? leverage = null,
        string? clientOrderId = null,
        object? @params = null,
        CancellationToken ct = default)
    {
        var p = new Dictionary<string, object?>();
        if (@params is System.Collections.Generic.IDictionary<string, object?> dict)
            foreach (var kv in dict) p[kv.Key] = kv.Value;
        if (stopPrice       != null) p["stopPrice"]       = stopPrice;
        if (takeProfitPrice != null) p["takeProfitPrice"] = takeProfitPrice;
        if (reduceOnly      != null) p["reduceOnly"]      = reduceOnly;
        return Op("create-order", Clean(new
        {
            exchange,
            apiKeyId,
            symbol,
            side,
            amount,
            type,
            price,
            marketType,
            leverage,
            clientOrderId,
            @params = p.Count > 0 ? (object)p : null,
        }), ct);
    }

    /// <summary>Cancel a live order by id. ⚠️</summary>
    public Task<JsonElement> CancelOrderAsync(
        string exchange,
        string? apiKeyId = null,
        string? orderId = null,
        string? clientOrderId = null,
        string? symbol = null,
        string? marketType = null,
        CancellationToken ct = default)
        => Op("cancel-order", Clean(new
        {
            exchange,
            apiKeyId,
            orderId,
            clientOrderId,
            symbol,
            marketType,
        }), ct);

    /// <summary>Amend (modify) a live order. ⚠️</summary>
    public Task<JsonElement> AmendOrderAsync(
        string exchange,
        string? apiKeyId = null,
        string? orderId = null,
        string? symbol = null,
        double? amount = null,
        double? price = null,
        CancellationToken ct = default)
        => Op("amend-order", Clean(new
        {
            exchange,
            apiKeyId,
            orderId,
            symbol,
            amount,
            price,
        }), ct);

    /// <summary>Cancel every open order (optionally scoped to a symbol). ⚠️</summary>
    public Task<JsonElement> CancelAllOrdersAsync(
        string exchange,
        string? apiKeyId = null,
        string? symbol = null,
        string? marketType = null,
        CancellationToken ct = default)
        => Op("cancel-all-orders", Clean(new
        {
            exchange,
            apiKeyId,
            symbol,
            marketType,
        }), ct);

    /// <summary>Cancel resting plan/trigger orders. ⚠️</summary>
    public Task<JsonElement> CancelPlanOrdersAsync(
        string exchange,
        string? apiKeyId = null,
        string? symbol = null,
        string? marketType = null,
        CancellationToken ct = default)
        => Op("cancel-plan-orders", Clean(new
        {
            exchange,
            apiKeyId,
            symbol,
            marketType,
        }), ct);

    /// <summary>Close an open position (market reduce-only). ⚠️</summary>
    public Task<JsonElement> ClosePositionAsync(
        string exchange,
        string symbol,
        string? apiKeyId = null,
        string? marketType = null,
        CancellationToken ct = default)
        => Op("close-position", Clean(new
        {
            exchange,
            apiKeyId,
            symbol,
            marketType,
        }), ct);

    /// <summary>Set leverage for a symbol. ⚠️</summary>
    public Task<JsonElement> SetLeverageAsync(
        string exchange,
        string symbol,
        double leverage,
        string? apiKeyId = null,
        string? marketType = null,
        CancellationToken ct = default)
        => Op("set-leverage", Clean(new
        {
            exchange,
            apiKeyId,
            symbol,
            leverage,
            marketType,
        }), ct);

    /// <summary>Set margin mode (cross/isolated). ⚠️</summary>
    public Task<JsonElement> SetMarginModeAsync(
        string exchange,
        string marginMode,
        string? apiKeyId = null,
        string? symbol = null,
        string? marketType = null,
        CancellationToken ct = default)
        => Op("set-margin-mode", Clean(new
        {
            exchange,
            apiKeyId,
            marginMode,
            symbol,
            marketType,
        }), ct);

    /// <summary>Set position mode (one-way / hedge). ⚠️</summary>
    public Task<JsonElement> SetPositionModeAsync(
        string exchange,
        string? apiKeyId = null,
        bool? hedged = null,
        string? mode = null,
        string? marketType = null,
        CancellationToken ct = default)
        => Op("set-position-mode", Clean(new
        {
            exchange,
            apiKeyId,
            hedged,
            mode,
            marketType,
        }), ct);

    // ── Helper ────────────────────────────────────────────────────────────────

    /// <summary>
    /// Strip null-valued properties from an anonymous object before
    /// serialisation so the wire body matches the contract (omit nulls).
    /// </summary>
    private static Dictionary<string, object?> Clean(object src)
    {
        var d = new Dictionary<string, object?>();
        foreach (var prop in src.GetType().GetProperties())
        {
            var v = prop.GetValue(src);
            if (v is not null) d[prop.Name] = v;
        }
        return d;
    }
}
