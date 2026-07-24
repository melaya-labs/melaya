package org.melaya

/**
 * Groups all **trading-plane** APIs under a single accessor so IDE autocomplete
 * surfaces the structure immediately.
 *
 * Exposed as [Melaya.trading].
 *
 * ```kotlin
 * val melaya = Melaya(apiKey = "mk_…")
 *
 * // Namespaced access (primary API)
 * melaya.trading.market.ticker("binance", "BTC/USDT", "spot")
 * melaya.trading.account.keys()
 * melaya.trading.sim.balance(strategyId = "sid_…")
 * melaya.trading.strategies.list()
 * melaya.trading.trade.createOrder(…)
 * melaya.trading.backtest.start(…)
 * melaya.trading.stream.ticker("binance", "BTC/USDT", "spot")
 * ```
 */
class TradingNamespace internal constructor(
    /** REST market-data + reference endpoints (public and authenticated plane). */
    val market: MarketAPI,
    /** Authenticated account reads: connected exchange keys, tier limits, usage. */
    val account: AccountAPI,
    /** Paper trading (sim broker): virtual balance, positions, and orders. */
    val sim: SimAPI,
    /** Launch, control, and inspect trading strategies (paper + live). */
    val strategies: StrategiesAPI,
    /** Live trading (real funds): order placement, positions, balance on a connected venue. */
    val trade: TradeAPI,
    /** Historical backtests + parameter sweeps (including optimization runs) on the Rust engine. */
    val backtest: BacktestAPI,
    /** WebSocket streaming endpoints (public market data + private feeds). */
    val stream: StreamAPI,
)

/**
 * Groups all **agents-plane** APIs under a single accessor.
 *
 * Exposed as [Melaya.agents].
 *
 * ```kotlin
 * melaya.agents.pipelines.recent()
 * melaya.agents.hitl.pending()
 * melaya.agents.assistant.getProfile()
 * melaya.agents.phone.listDevices()
 * melaya.agents.evals.benchmarks()
 * ```
 */
class AgentsNamespace internal constructor(
    /** Pipeline run overview, traces, schedules, model prices, and server version. */
    val pipelines: PipelinesAPI,
    /** Human-in-the-loop approval queue: list pending, approve, reject, inspect run tool calls. */
    val hitl: HitlAPI,
    /** Assistant onboarding profile (get + set). */
    val assistant: AssistantAPI,
    /** Phone device control: pair, list, screen-tree, apps, active run registration. */
    val phone: PhoneAPI,
    /** Agent evaluation runs and benchmark scores. */
    val evals: EvalsAPI,
)

/**
 * Groups all **platform-plane** APIs under a single accessor.
 *
 * Exposed as [Melaya.platform].
 *
 * ```kotlin
 * melaya.platform.projects.list()
 * melaya.platform.credentials.list()
 * melaya.platform.connectors.set("proj", "openai", "sk-…")
 * melaya.platform.billing.subscription()
 * melaya.platform.team.listMembers("proj")
 * melaya.platform.templates.list()
 * melaya.platform.runner.createToken("prod-server-1")
 * melaya.platform.auth.me()
 * melaya.platform.mfa.setup()
 * melaya.platform.accounts.credits()
 * melaya.platform.bugs.listMine()
 * melaya.platform.events.onRunUpdate("run-123") { … }
 * ```
 */
class PlatformNamespace internal constructor(
    /** Create and list agent projects. */
    val projects: ProjectsAPI,
    /** User-scoped credential storage (services, OAuth, RAG, env handles, AI models). */
    val credentials: CredentialsAPI,
    /** Project-scoped connector credentials (per-project service keys). */
    val connectors: ConnectorsAPI,
    /** Billing: subscription status, Stripe checkout / portal, pricing plans. */
    val billing: BillingAPI,
    /** Project team management: members, roles, invite links, pipeline visibility. */
    val team: TeamAPI,
    /** Pipeline templates: create, share, assign, and manage visibility. */
    val templates: TemplatesAPI,
    /** Runner token management: mint, list, revoke `mel_run_` tokens. */
    val runner: RunnerAPI,
    /** Auth endpoints: login, register, MFA, password reset, session management. */
    val auth: AuthAPI,
    /** MFA enrollment: status, TOTP setup, confirm. */
    val mfa: MfaAPI,
    /**
     * Account profile, GDPR data export, CEX key removal, and credit balances.
     * For exchange key reads use [TradingNamespace.account]; for billing use [billing].
     */
    val accounts: AccountsAPI,
    /** In-app bug reports and notifications. */
    val bugs: BugsAPI,
    /**
     * Platform real-time events over Socket.IO at `/api/v1/events`.
     * Subscribe to run updates, init-phase progress, HITL notifications, and pipeline CRUD events.
     */
    val events: MelayaEvents,
)
