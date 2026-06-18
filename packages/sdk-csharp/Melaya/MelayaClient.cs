using System.Net;

namespace Melaya;

/// <summary>
/// Options for the <see cref="MelayaClient"/>.
/// </summary>
public sealed class MelayaOptions
{
    /// <summary>
    /// Your Melaya API key (must be prefixed <c>mk_</c>).
    /// Create one at melaya.org → Settings → API Keys.
    /// </summary>
    public required string ApiKey { get; init; }

    /// <summary>Override the REST base URL. Defaults to <c>https://api.melaya.org</c>.</summary>
    public string BaseUrl { get; init; } = "https://api.melaya.org";

    /// <summary>Override the WebSocket base URL. Defaults to <c>wss://wss.melaya.org</c>.</summary>
    public string WsUrl   { get; init; } = "wss://wss.melaya.org";

    /// <summary>
    /// Disable TLS certificate verification (dev-box MITM proxies).
    /// Controlled by env var <c>MELAYA_INSECURE_TLS=1</c>.
    /// The SDK reads this automatically; you can also force it here.
    /// </summary>
    public bool InsecureTls { get; init; } = false;
}

/// <summary>
/// The Melaya .NET SDK entry point.
/// <para>Injects <c>?apiKey=</c> and <c>Authorization: Bearer</c> on every request.
/// Throws <see cref="MelayaException"/> on HTTP ≥ 400 or envelope <c>ok: false</c>.</para>
/// </summary>
/// <example>
/// <code>
/// var m = new MelayaClient(new MelayaOptions { ApiKey = Environment.GetEnvironmentVariable("MK")! });
/// var ticker = await m.Market.TickerAsync("binance", "BTC/USDT", "spot");
/// </code>
/// </example>
public sealed class MelayaClient : IDisposable
{
    private readonly MelayaHttpClient _http;

    /// <summary>REST market-data + reference endpoints.</summary>
    public MarketApi    Market     { get; }
    /// <summary>Authenticated account reads: connected keys, tier limits, usage.</summary>
    public AccountApi   Account    { get; }
    /// <summary>Paper trading (sim broker): virtual balance, positions, and orders.</summary>
    public SimApi       Sim        { get; }
    /// <summary>Launch, control, and inspect trading strategies.</summary>
    public StrategiesApi Strategies { get; }
    /// <summary>Historical backtests + parameter sweeps on the Rust engine.</summary>
    public BacktestApi  Backtest   { get; }
    /// <summary>WebSocket streaming endpoints (public market data + private feeds).</summary>
    public StreamApi    Stream     { get; }
    /// <summary>Live trading — credentialed order placement and account state on a connected exchange. ⚠️ real funds.</summary>
    public TradeApi     Trade      { get; }

    public MelayaClient(MelayaOptions opts)
    {
        if (string.IsNullOrWhiteSpace(opts?.ApiKey))
            throw new ArgumentException(
                "Melaya: `ApiKey` is required (create one at melaya.org → Settings → API Keys).");
        if (!opts.ApiKey.StartsWith("mk_", StringComparison.Ordinal))
            throw new ArgumentException("Melaya: API keys must be prefixed `mk_`.");

        // Respect MELAYA_INSECURE_TLS env var OR the option flag
        bool insecure = opts.InsecureTls ||
            string.Equals(Environment.GetEnvironmentVariable("MELAYA_INSECURE_TLS"), "1",
                StringComparison.Ordinal);

        HttpMessageHandler handler;
        if (insecure)
        {
            var h = new HttpClientHandler
            {
                ServerCertificateCustomValidationCallback = (_, _, _, _) => true,
            };
            handler = h;
        }
        else
        {
            handler = new HttpClientHandler();
        }

        _http       = new MelayaHttpClient(opts.ApiKey, opts.BaseUrl, handler);
        Market      = new MarketApi(_http);
        Account     = new AccountApi(_http);
        Sim         = new SimApi(_http);
        Strategies  = new StrategiesApi(_http);
        Backtest    = new BacktestApi(_http);
        Stream      = new StreamApi(opts.ApiKey, opts.WsUrl, _http, insecure);
        Trade       = new TradeApi(_http);
    }

    public void Dispose() => _http.Dispose();
}
