using System.Text.Json;

namespace Melaya;

/// <summary>
/// REST market-data API — normalized across all 70+ venues.
/// Maps to <c>https://api.melaya.org/api/v1/market/*</c>.
/// </summary>
public sealed class MarketApi
{
    private readonly MelayaHttpClient _http;

    internal MarketApi(MelayaHttpClient http) => _http = http;

    /// <summary>List the exchanges Melaya supports right now.</summary>
    public async Task<List<ExchangeInfo>> ListExchangesAsync(CancellationToken ct = default)
    {
        var r = await _http.GetAsync<ExchangesEnvelope>("/api/v1/market/list-exchanges", ct: ct).ConfigureAwait(false);
        return r.Exchanges ?? [];
    }

    /// <summary>Best bid/ask, last price, and 24h aggregates for one symbol.</summary>
    public async Task<JsonElement> TickerAsync(string exchange, string symbol, string? market = null, CancellationToken ct = default)
    {
        var q = Q(("exchange", exchange), ("symbol", symbol), ("market", market));
        var r = await _http.GetAsync<TickerEnvelope>("/api/v1/market/ticker", q, ct).ConfigureAwait(false);
        return r.Ticker ?? default;
    }

    /// <summary>Order book to a given depth.</summary>
    public async Task<JsonElement> OrderbookAsync(string exchange, string symbol, string? market = null, int? limit = null, CancellationToken ct = default)
    {
        var q = Q(("exchange", exchange), ("symbol", symbol), ("market", market), ("limit", limit?.ToString()));
        var r = await _http.GetAsync<OrderBookEnvelope>("/api/v1/market/orderbook", q, ct).ConfigureAwait(false);
        return r.Orderbook ?? default;
    }

    /// <summary>OHLCV candles. Each candle is <c>[ts, open, high, low, close, volume]</c>.</summary>
    public async Task<List<JsonElement>> OhlcvAsync(string exchange, string symbol, string timeframe, string? market = null, int? limit = null, CancellationToken ct = default)
    {
        var q = Q(("exchange", exchange), ("symbol", symbol), ("timeframe", timeframe), ("market", market), ("limit", limit?.ToString()));
        var r = await _http.GetAsync<CandlesEnvelope>("/api/v1/market/ohlcv", q, ct).ConfigureAwait(false);
        return r.Candles ?? [];
    }

    /// <summary>Recent public trades.</summary>
    public async Task<List<JsonElement>> TradesAsync(string exchange, string symbol, string? market = null, CancellationToken ct = default)
    {
        var q = Q(("exchange", exchange), ("symbol", symbol), ("market", market));
        var r = await _http.GetAsync<TradesEnvelope>("/api/v1/market/trades", q, ct).ConfigureAwait(false);
        return r.Trades ?? [];
    }

    /// <summary>Tradable markets on a venue.</summary>
    public async Task<List<JsonElement>> MarketsAsync(string exchange, CancellationToken ct = default)
    {
        var q = Q(("exchange", exchange));
        var r = await _http.GetAsync<MarketsEnvelope>("/api/v1/market/markets", q, ct).ConfigureAwait(false);
        return r.Markets ?? [];
    }

    /// <summary>Listed currencies on a venue.</summary>
    public async Task<List<JsonElement>> CurrenciesAsync(string exchange, CancellationToken ct = default)
    {
        var q = Q(("exchange", exchange));
        var r = await _http.GetAsync<CurrenciesEnvelope>("/api/v1/market/currencies", q, ct).ConfigureAwait(false);
        return r.Currencies ?? [];
    }

    /// <summary>Operational status: ok / maintenance / degraded.</summary>
    public async Task<JsonElement> StatusAsync(string exchange, CancellationToken ct = default)
    {
        var q = Q(("exchange", exchange));
        var r = await _http.GetAsync<StatusEnvelope>("/api/v1/market/status", q, ct).ConfigureAwait(false);
        return r.Status ?? default;
    }

    /// <summary>Exchange server time.</summary>
    public async Task<JsonElement> TimeAsync(string exchange, CancellationToken ct = default)
    {
        var q = Q(("exchange", exchange));
        var r = await _http.GetAsync<TimeEnvelope>("/api/v1/market/time", q, ct).ConfigureAwait(false);
        return r.Time ?? default;
    }

    // ── Batch / derivatives (POST) ────────────────────────────────────────────

    /// <summary>Tickers for many symbols on one venue. Keyed by symbol.</summary>
    public async Task<JsonElement> TickersAsync(string exchange, IEnumerable<string> symbols, string? market = null, CancellationToken ct = default)
    {
        var body = new { exchange, symbols, market };
        var r = await _http.PostAsync<TickersEnvelope>("/api/v1/market/tickers", body, ct).ConfigureAwait(false);
        return r.Tickers ?? default;
    }

    /// <summary>Latest funding rates for perpetuals. Keyed by symbol.</summary>
    public async Task<JsonElement> FundingRatesAsync(string exchange, IEnumerable<string> symbols, string? market = null, CancellationToken ct = default)
    {
        var body = new { exchange, symbols, market };
        var r = await _http.PostAsync<RatesEnvelope>("/api/v1/market/funding-rates", body, ct).ConfigureAwait(false);
        return r.Rates ?? default;
    }

    /// <summary>Funding-rate history.</summary>
    public async Task<List<JsonElement>> FundingRateHistoryAsync(string exchange, string symbol, int? hours = null, string? market = null, CancellationToken ct = default)
    {
        var body = new { exchange, symbol, hours, market };
        var r = await _http.PostAsync<HistoryEnvelope>("/api/v1/market/funding-rate-history", body, ct).ConfigureAwait(false);
        return r.History ?? [];
    }

    /// <summary>Open interest for one or more perpetuals. Keyed by symbol.</summary>
    public async Task<JsonElement> OpenInterestAsync(string exchange, IEnumerable<string> symbols, string? market = null, CancellationToken ct = default)
    {
        var body = new { exchange, symbols, market };
        var r = await _http.PostAsync<OpenInterestEnvelope>("/api/v1/market/open-interest", body, ct).ConfigureAwait(false);
        return r.OpenInterest ?? default;
    }

    /// <summary>Open-interest history.</summary>
    public async Task<List<JsonElement>> OpenInterestHistoryAsync(string exchange, string symbol, int? hours = null, string? market = null, CancellationToken ct = default)
    {
        var body = new { exchange, symbol, hours, market };
        var r = await _http.PostAsync<HistoryEnvelope>("/api/v1/market/open-interest-history", body, ct).ConfigureAwait(false);
        return r.History ?? [];
    }

    /// <summary>Instrument list + trading constraints.</summary>
    public async Task<JsonElement> InstrumentsAsync(string exchange, string? market = null, CancellationToken ct = default)
    {
        var body = new { exchange, market };
        return await _http.PostAsync<JsonElement>("/api/v1/market/instruments", body, ct).ConfigureAwait(false);
    }

    /// <summary>Cross-exchange liquidation events (historical query).</summary>
    public async Task<List<JsonElement>> LiquidationEventsAsync(string? exchange = null, string? symbol = null, long? sinceMs = null, int? limit = null, CancellationToken ct = default)
    {
        var body = new { exchange, symbol, sinceMs, limit };
        var r = await _http.PostAsync<EventsEnvelope>("/api/v1/market/liquidation-events", body, ct).ConfigureAwait(false);
        return r.Events ?? [];
    }

    /// <summary>Multi-symbol OHLCV. Returns candle arrays keyed by symbol.</summary>
    public async Task<JsonElement> OhlcvMultiAsync(string exchange, IEnumerable<string> symbols, string timeframe, int? limit = null, string? market = null, CancellationToken ct = default)
    {
        var body = new { exchange, symbols, timeframe, limit, market };
        var r = await _http.PostAsync<PerSymbolEnvelope>("/api/v1/market/ohlcv-multi", body, ct).ConfigureAwait(false);
        return r.PerSymbol ?? default;
    }

    /// <summary>Trading constraints for one symbol.</summary>
    public async Task<JsonElement> MarketConstraintsAsync(string exchange, string symbol, string? market = null, CancellationToken ct = default)
    {
        var body = new { exchange, symbol, market };
        var r = await _http.PostAsync<ConstraintsEnvelope>("/api/v1/market/market-constraints", body, ct).ConfigureAwait(false);
        return r.Constraints ?? default;
    }

    /// <summary>Funding-rate history for one symbol across several venues. Keyed by exchange.</summary>
    public async Task<JsonElement> FundingRateHistoryMultiAsync(IEnumerable<string> exchanges, string symbol, int? hours = null, CancellationToken ct = default)
    {
        var body = new { exchanges, symbol, hours };
        var r = await _http.PostAsync<PerExchangeEnvelope>("/api/v1/market/funding-rate-history-multi", body, ct).ConfigureAwait(false);
        return r.PerExchange ?? default;
    }

    /// <summary>Open-interest history for one symbol across several venues. Keyed by exchange.</summary>
    public async Task<JsonElement> OpenInterestHistoryMultiAsync(IEnumerable<string> exchanges, string symbol, int? hours = null, CancellationToken ct = default)
    {
        var body = new { exchanges, symbol, hours };
        var r = await _http.PostAsync<PerExchangeEnvelope>("/api/v1/market/open-interest-history-multi", body, ct).ConfigureAwait(false);
        return r.PerExchange ?? default;
    }

    /// <summary>Prediction-market listings for a venue.</summary>
    public async Task<List<JsonElement>> PredictionMarketsAsync(string venue = "polymarket", CancellationToken ct = default)
    {
        var body = new { venue };
        var r = await _http.PostAsync<PmMarketsEnvelope>("/api/v1/market/pm-markets", body, ct).ConfigureAwait(false);
        return r.Markets ?? [];
    }

    /// <summary>Live platform catalog counts (tools, subagents, by category). Public.</summary>
    public async Task<JsonElement> CatalogCountsAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/public/catalog-counts", ct: ct).ConfigureAwait(false);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static Dictionary<string, string?> Q(params (string Key, string? Value)[] pairs)
    {
        var d = new Dictionary<string, string?>(pairs.Length);
        foreach (var (k, v) in pairs)
            if (v is not null)
                d[k] = v;
        return d;
    }
}
