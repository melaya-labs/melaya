namespace Melaya;

// ─────────────────────────────────────────────────────────────────────────────
//  Domain namespace accessor objects
//
//  Usage (namespaced — preferred):
//    melaya.Trading.Market.TickerAsync(...)
//    melaya.Agents.Pipelines.ListAsync(...)
//    melaya.Platform.Projects.ListAsync(...)
//
//  All properties are direct references to the same underlying API objects
//  already accessible via the flat properties on MelayaClient (e.g. m.Market,
//  m.Pipelines).  Zero allocation overhead — just thin accessor wrappers.
// ─────────────────────────────────────────────────────────────────────────────

/// <summary>
/// The <c>trading</c> namespace — everything related to market data, account
/// positions, paper/live strategies, backtests, and WebSocket streams.
/// <para>Access via <see cref="MelayaClient.Trading"/>.</para>
/// </summary>
/// <example>
/// <code>
/// var ticker = await m.Trading.Market.TickerAsync("binance", "BTC/USDT", "spot");
/// var job    = await m.Trading.Backtest.StartAsync(body);
/// await foreach (var frame in m.Trading.Stream.TickerAsync("binance", "BTC/USDT", "spot")) { … }
/// </code>
/// </example>
public sealed class TradingNamespace
{
    /// <summary>REST market-data + reference endpoints (public and authenticated).</summary>
    public MarketApi     Market     { get; }

    /// <summary>Authenticated CEX account reads: connected keys, tier limits, usage.</summary>
    public AccountApi    Account    { get; }

    /// <summary>Paper trading (sim broker): virtual balance, positions, and orders.</summary>
    public SimApi        Sim        { get; }

    /// <summary>Launch, control, and inspect trading strategies (paper + live).</summary>
    public StrategiesApi Strategies { get; }

    /// <summary>Live credentialed order placement on a connected exchange. ⚠️ Real funds.</summary>
    public TradeApi      Trade      { get; }

    /// <summary>Historical backtests + parameter sweeps on the Rust engine.</summary>
    public BacktestApi   Backtest   { get; }

    /// <summary>WebSocket streaming endpoints (public market data + private feeds).</summary>
    public StreamApi     Stream     { get; }

    internal TradingNamespace(
        MarketApi market,
        AccountApi account,
        SimApi sim,
        StrategiesApi strategies,
        TradeApi trade,
        BacktestApi backtest,
        StreamApi stream)
    {
        Market     = market;
        Account    = account;
        Sim        = sim;
        Strategies = strategies;
        Trade      = trade;
        Backtest   = backtest;
        Stream     = stream;
    }
}

/// <summary>
/// The <c>agents</c> namespace — pipeline runs, HITL approvals, assistant,
/// phone control, evals, and AI model credentials.
/// <para>Access via <see cref="MelayaClient.Agents"/>.</para>
/// </summary>
/// <example>
/// <code>
/// var runs    = await m.Agents.Pipelines.ListAsync(project: "my-agents");
/// var pending = await m.Agents.Hitl.PendingAsync();
/// var profile = await m.Agents.Assistant.GetProfileAsync();
/// var evals   = await m.Agents.Evals.ListRunsAsync();
/// </code>
/// </example>
public sealed class AgentsNamespace
{
    /// <summary>Pipeline run overview, traces, cost data, and cron schedules.</summary>
    public PipelinesApi  Pipelines  { get; }

    /// <summary>Human-in-the-Loop approval queue: list pending, approve, reject, bulk decide.</summary>
    public HitlApi       Hitl       { get; }

    /// <summary>Assistant onboarding profile (get + set).</summary>
    public AssistantApi  Assistant  { get; }

    /// <summary>Phone device control: pair, list, screen-tree, apps, active run.</summary>
    public PhoneApi      Phone      { get; }

    /// <summary>Eval run results, summaries, benchmarks, and memory graphs.</summary>
    public EvalsApi      Evals      { get; }

    /// <summary>
    /// AI model credentials and model listing.
    /// Backed by <see cref="CredentialsApi"/> — exposes the model-related surface
    /// (<c>ListModelsAsync</c>) alongside full credential management.
    /// </summary>
    public CredentialsApi Models    { get; }

    internal AgentsNamespace(
        PipelinesApi pipelines,
        HitlApi hitl,
        AssistantApi assistant,
        PhoneApi phone,
        EvalsApi evals,
        CredentialsApi credentials)
    {
        Pipelines = pipelines;
        Hitl      = hitl;
        Assistant = assistant;
        Phone     = phone;
        Evals     = evals;
        Models    = credentials;   // same object — Models surfaces ListModelsAsync etc.
    }
}

/// <summary>
/// The <c>platform</c> namespace — projects, credentials, connectors, billing,
/// team, templates, overview, runner tokens, auth, MFA, accounts,
/// bugs, and real-time Socket.IO events.
/// <para>Access via <see cref="MelayaClient.Platform"/>.</para>
/// </summary>
/// <example>
/// <code>
/// var projects = await m.Platform.Projects.ListAsync();
/// await m.Platform.Credentials.SetAsync("openai", new CredentialSetRequest { Value = "sk-…" });
/// var sub      = await m.Platform.Billing.SubscriptionAsync();
/// m.Platform.Events.OnRunUpdate("run-123", e => Console.WriteLine(e.EventType));
/// </code>
/// </example>
public sealed class PlatformNamespace
{
    /// <summary>Create and list agent projects.</summary>
    public ProjectsApi           Projects     { get; }

    /// <summary>User-scoped credential storage (services, OAuth, env handles, AI models).</summary>
    public CredentialsApi        Credentials  { get; }

    /// <summary>Project-scoped connector credentials (per-project service keys).</summary>
    public ConnectorsApi         Connectors   { get; }

    /// <summary>Billing: subscription status, Stripe checkout / portal, pricing plans.</summary>
    public BillingApi            Billing      { get; }

    /// <summary>Project team management: members, roles, and invite links.</summary>
    public TeamApi               Team         { get; }

    /// <summary>Pipeline templates: create, share, assign, and manage visibility.</summary>
    public TemplatesApi          Templates    { get; }

    /// <summary>
    /// Pipeline run overview and cost dashboard.
    /// Backed by <see cref="PipelinesApi"/> — exposes <c>OverviewAsync</c>,
    /// <c>ChartDataAsync</c>, <c>CostBreakdownAsync</c>, etc.
    /// </summary>
    public PipelinesApi          Overview     { get; }

    /// <summary>Runner token management: mint, list, revoke <c>mel_run_</c> tokens.</summary>
    public RunnerApi             Runner       { get; }

    /// <summary>Auth endpoints: login, register, MFA, password reset, session utilities.</summary>
    public AuthApi               Auth         { get; }

    /// <summary>TOTP multi-factor authentication: setup, confirm, status.</summary>
    public MfaApi                Mfa          { get; }

    /// <summary>Account management: GDPR export, profile update, credit balances, CEX key removal.</summary>
    public AccountPlatformApi    Accounts     { get; }

    /// <summary>Bug reports: submit, list, comment, and read notifications.</summary>
    public BugsApi               Bugs         { get; }

    /// <summary>
    /// Real-time Socket.IO events: run updates, HITL notifications,
    /// init-phase progress, and pipeline CRUD events.
    /// </summary>
    public MelayaEvents          Events       { get; }

    internal PlatformNamespace(
        ProjectsApi projects,
        CredentialsApi credentials,
        ConnectorsApi connectors,
        BillingApi billing,
        TeamApi team,
        TemplatesApi templates,
        PipelinesApi overview,
        RunnerApi runner,
        AuthApi auth,
        MfaApi mfa,
        AccountPlatformApi accounts,
        BugsApi bugs,
        MelayaEvents events)
    {
        Projects    = projects;
        Credentials = credentials;
        Connectors  = connectors;
        Billing     = billing;
        Team        = team;
        Templates   = templates;
        Overview    = overview;
        Runner      = runner;
        Auth        = auth;
        Mfa         = mfa;
        Accounts    = accounts;
        Bugs        = bugs;
        Events      = events;
    }
}
