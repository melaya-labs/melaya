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
    /// Per-request timeout in milliseconds (default: 30 000 ms).
    /// Applied to every REST call via <see cref="System.Threading.CancellationTokenSource"/>.
    /// Set to 0 to disable (use only the caller's CancellationToken).
    /// </summary>
    public int RequestTimeoutMs { get; init; } = 30_000;
}

/// <summary>
/// The Melaya .NET SDK entry point — v0.2.0.
/// <para>
/// Exposes three domain namespaces as the primary API surface:
/// <list type="bullet">
///   <item><see cref="Trading"/> — market data, CEX account, sim, strategies, backtest, stream, trade</item>
///   <item><see cref="Agents"/> — pipelines/runs, HITL, assistant, phone, evals, models</item>
///   <item><see cref="Platform"/> — projects, credentials, connectors, billing, team, templates,
///         overview, runner, auth, MFA, accounts, bugs, events</item>
/// </list>
/// </para>
/// <para>All flat properties (e.g. <c>m.Market</c>, <c>m.Pipelines</c>) remain available
/// as aliases pointing to the same underlying objects.</para>
/// <para>Sends the API key via <c>Authorization: Bearer</c> on every request (never in the
/// query string). Throws <see cref="MelayaException"/> on HTTP ≥ 400 or envelope <c>ok: false</c>.</para>
/// </summary>
/// <example>
/// <code>
/// var mk = Environment.GetEnvironmentVariable("MK")!;
/// await using var m = new MelayaClient(new MelayaOptions { ApiKey = mk });
///
/// // ── Namespaced API (primary) ──────────────────────────────────────────────
/// var ticker  = await m.Trading.Market.TickerAsync("binance", "BTC/USDT", "spot");
/// var runs    = await m.Agents.Pipelines.ListAsync(project: "my-agents");
/// var pending = await m.Agents.Hitl.PendingAsync();
/// var profile = await m.Agents.Assistant.GetProfileAsync();
/// var projs   = await m.Platform.Projects.ListAsync();
/// m.Platform.Events.OnRunUpdate("run-123", e => Console.WriteLine(e));
///
/// // ── Flat API (aliases, also supported) ────────────────────────────────────
/// var ticker2 = await m.Market.TickerAsync("binance", "BTC/USDT", "spot");
/// var pending2 = await m.Hitl.PendingAsync();
/// </code>
/// </example>
public sealed class MelayaClient : IDisposable, IAsyncDisposable
{
    private readonly MelayaHttpClient _http;

    // ── Domain namespace accessors (primary API) ──────────────────────────────

    /// <summary>
    /// Trading domain — market data, CEX account, paper/live trading, backtests, and WebSocket streams.
    /// <code>
    /// var ticker = await m.Trading.Market.TickerAsync("binance", "BTC/USDT", "spot");
    /// var job    = await m.Trading.Backtest.StartAsync(body);
    /// await foreach (var f in m.Trading.Stream.TickerAsync("binance", "BTC/USDT", "spot")) { … }
    /// </code>
    /// </summary>
    public TradingNamespace  Trading  { get; }

    /// <summary>
    /// Agents domain — pipeline runs, HITL approvals, assistant, phone control, evals, and AI models.
    /// <code>
    /// var runs    = await m.Agents.Pipelines.ListAsync(project: "my-agents");
    /// var pending = await m.Agents.Hitl.PendingAsync();
    /// var models  = await m.Agents.Models.ListModelsAsync(provider: "openai");
    /// </code>
    /// </summary>
    public AgentsNamespace   Agents   { get; }

    /// <summary>
    /// Platform domain — projects, credentials, connectors, billing, team, templates, runner,
    /// auth, MFA, accounts, bugs, and real-time events.
    /// <code>
    /// var projects = await m.Platform.Projects.ListAsync();
    /// var sub      = await m.Platform.Billing.SubscriptionAsync();
    /// m.Platform.Events.OnRunUpdate("run-123", e => Console.WriteLine(e.EventType));
    /// </code>
    /// </summary>
    public PlatformNamespace Platform { get; }

    // ── Trading plane ──────────────────────────────────────────────────────────

    /// <summary>REST market-data + reference endpoints (public and authenticated).</summary>
    public MarketApi       Market              { get; }
    /// <summary>Authenticated account reads: connected keys, tier limits, usage.</summary>
    public AccountApi      Account             { get; }
    /// <summary>Paper trading (sim broker): virtual balance, positions, and orders.</summary>
    public SimApi          Sim                 { get; }
    /// <summary>Launch, control, and inspect trading strategies (paper + live).</summary>
    public StrategiesApi   Strategies          { get; }
    /// <summary>Historical backtests + parameter sweeps on the Rust engine.</summary>
    public BacktestApi     Backtest            { get; }
    /// <summary>WebSocket streaming endpoints (public market data + private feeds).</summary>
    public StreamApi       Stream              { get; }
    /// <summary>Live trading — credentialed order placement on a connected exchange. ⚠️ Real funds.</summary>
    public TradeApi        Trade               { get; }

    // ── Auth + account management ──────────────────────────────────────────────

    /// <summary>Auth endpoints: login, register, MFA, password reset, session utilities.</summary>
    public AuthApi         Auth                { get; }
    /// <summary>TOTP multi-factor authentication: setup, confirm, status.</summary>
    public MfaApi          Mfa                 { get; }
    /// <summary>Account management: GDPR export, profile update, credit balances, CEX key removal.</summary>
    public AccountPlatformApi AccountManagement { get; }

    // ── Platform / agents plane ────────────────────────────────────────────────

    /// <summary>Create and list agent projects.</summary>
    public ProjectsApi     Projects            { get; }
    /// <summary>Pipeline run overview, traces, cost data, and cron schedules.</summary>
    public PipelinesApi    Pipelines           { get; }
    /// <summary>Human-in-the-Loop approval queue: list pending, approve, reject, bulk decide.</summary>
    public HitlApi         Hitl                { get; }
    /// <summary>User-scoped credential storage (services, OAuth, env handles, AI models).</summary>
    public CredentialsApi  Credentials         { get; }
    /// <summary>Project-scoped connector credentials (per-project service keys).</summary>
    public ConnectorsApi   Connectors          { get; }
    /// <summary>Billing: subscription status, Stripe checkout / portal, pricing plans.</summary>
    public BillingApi      Billing             { get; }
    /// <summary>Runner token management: mint, list, revoke <c>mel_run_</c> tokens.</summary>
    public RunnerApi       Runner              { get; }
    /// <summary>Phone device control: pair, list, screen-tree, apps, active run.</summary>
    public PhoneApi        Phone               { get; }
    /// <summary>Project team management: members, roles, and invite links.</summary>
    public TeamApi         Team                { get; }
    /// <summary>Pipeline templates: create, share, assign, and manage visibility.</summary>
    public TemplatesApi    Templates           { get; }
    /// <summary>Assistant onboarding profile (get + set).</summary>
    public AssistantApi    Assistant           { get; }
    /// <summary>Eval run results, summaries, benchmarks, and memory graphs.</summary>
    public EvalsApi        Evals               { get; }
    /// <summary>Bug reports: submit, list, comment, and read notifications.</summary>
    public BugsApi         Bugs                { get; }

    // ── Cross-cutting platform endpoints ──────────────────────────────────────

    /// <summary>Platform-specific market data: liquidations, on-chain data, banners, price history.</summary>
    public PlatformMarketApi PlatformMarket    { get; }
    /// <summary>Team strategy listing and bulk summaries.</summary>
    public StrategiesPlatformApi StrategiesTeam { get; }
    /// <summary>Parameter sweep optimization runs.</summary>
    public BacktestsPlatformApi Optimizations  { get; }

    /// <summary>Server utility endpoints: version string.</summary>
    public ServerApi           Server          { get; }

    /// <summary>
    /// Real-time Socket.IO events at <c>/api/v1/events</c>: run updates, HITL notifications,
    /// init-phase progress, and pipeline CRUD events.
    /// </summary>
    public MelayaEvents    Events              { get; }

    public MelayaClient(MelayaOptions opts)
    {
        if (string.IsNullOrWhiteSpace(opts?.ApiKey))
            throw new ArgumentException(
                "Melaya: `ApiKey` is required (create one at melaya.org → Settings → API Keys).");
        if (!opts.ApiKey.StartsWith("mk_", StringComparison.Ordinal))
            throw new ArgumentException("Melaya: API keys must be prefixed `mk_`.");

        HttpMessageHandler handler = new HttpClientHandler();
        _http              = new MelayaHttpClient(opts.ApiKey, opts.BaseUrl, handler, opts.RequestTimeoutMs);

        // Trading plane
        Market             = new MarketApi(_http);
        Account            = new AccountApi(_http);
        Sim                = new SimApi(_http);
        Strategies         = new StrategiesApi(_http);
        Backtest           = new BacktestApi(_http);
        Stream             = new StreamApi(opts.ApiKey, opts.WsUrl, _http);
        Trade              = new TradeApi(_http);

        // Auth + account management
        Auth               = new AuthApi(_http);
        Mfa                = new MfaApi(_http);
        AccountManagement  = new AccountPlatformApi(_http);

        // Platform / agents plane
        Projects           = new ProjectsApi(_http);
        Pipelines          = new PipelinesApi(_http);
        Hitl               = new HitlApi(_http);
        Credentials        = new CredentialsApi(_http);
        Connectors         = new ConnectorsApi(_http);
        Billing            = new BillingApi(_http);
        Runner             = new RunnerApi(_http);
        Phone              = new PhoneApi(_http);
        Team               = new TeamApi(_http);
        Templates          = new TemplatesApi(_http);
        Assistant          = new AssistantApi(_http);
        Evals              = new EvalsApi(_http);
        Bugs               = new BugsApi(_http);

        // Cross-cutting
        PlatformMarket     = new PlatformMarketApi(_http);
        StrategiesTeam     = new StrategiesPlatformApi(_http);
        Optimizations      = new BacktestsPlatformApi(_http);
        Server             = new ServerApi(_http);

        // Real-time events
        Events             = new MelayaEvents(opts.ApiKey, opts.BaseUrl);

        // ── Domain namespace accessors ─────────────────────────────────────────
        Trading  = new TradingNamespace(Market, Account, Sim, Strategies, Trade, Backtest, Stream);
        Agents   = new AgentsNamespace(Pipelines, Hitl, Assistant, Phone, Evals, Credentials);
        Platform = new PlatformNamespace(
            Projects, Credentials, Connectors, Billing, Team, Templates,
            Pipelines, Runner, Auth, Mfa, AccountManagement, Bugs, Events);
    }

    public void Dispose()
    {
        Events.Dispose();
        _http.Dispose();
    }

    public async ValueTask DisposeAsync()
    {
        Events.Dispose();
        _http.Dispose();
        await Task.CompletedTask.ConfigureAwait(false);
    }
}
