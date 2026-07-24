package org.melaya

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

const val DEFAULT_BASE_URL = "https://api.melaya.org"
const val DEFAULT_WS_URL   = "wss://wss.melaya.org"

/**
 * The Melaya SDK entry point — v0.2.0.
 *
 * Covers the full REST surface across trading, agents, and platform planes,
 * plus real-time events via Socket.IO at `/api/v1/events`.
 *
 * Authentication: pass your `mk_` platform API key. REST requests send it
 * only as an `Authorization: Bearer` header. Public market WebSockets use the
 * server-required query parameter; private WebSockets use short-lived tickets.
 * The API key is never logged.
 *
 * ### Namespaced API (primary)
 *
 * The three top-level namespaces group every module by plane so IDE autocomplete
 * surfaces the structure immediately:
 *
 * ```kotlin
 * val melaya = Melaya(apiKey = System.getenv("MK"))
 *
 * // ── Trading plane ──────────────────────────────────────────────────────
 * val ticker = melaya.trading.market.ticker("binance", "BTC/USDT", "spot")
 * val book   = melaya.trading.stream.orderbook("bybit", "BTC/USDT", "spot")
 * melaya.trading.strategies.list()
 * melaya.trading.backtest.start(…)
 *
 * // ── Agents plane ───────────────────────────────────────────────────────
 * val runs    = melaya.agents.pipelines.recent()
 * val pending = melaya.agents.hitl.pending()
 * melaya.agents.hitl.approve(pending[0].getString("requestId"))
 *
 * // ── Platform plane ─────────────────────────────────────────────────────
 * val projects = melaya.platform.projects.list()
 * melaya.platform.credentials.set("openai", value = "sk-…")
 * melaya.platform.billing.subscription()
 *
 * // ── Real-time events ───────────────────────────────────────────────────
 * val unsub = melaya.platform.events.onRunUpdate("run-123") { e ->
 *     println("${e.optString("event_type")}: ${e.optString("status")}")
 * }
 * unsub()
 * melaya.platform.events.close()
 * ```
 *
 * ### Flat accessors (legacy / convenience)
 *
 * All modules remain directly accessible on the root client for backwards
 * compatibility: `melaya.market`, `melaya.hitl`, `melaya.projects`, etc.
 *
 * TLS certificate and hostname verification are always enabled. Install a
 * private development CA in the JVM trust store when using an intercept proxy.
 */
class Melaya @JvmOverloads constructor(
    apiKey: String,
    baseUrl: String = DEFAULT_BASE_URL,
    wsUrl: String   = DEFAULT_WS_URL,
    /**
     * Per-request HTTP call timeout in milliseconds.
     * Applied to every REST call via a per-call OkHttp client override.
     * Default: 30 000 ms. Set to 0 to disable.
     */
    requestTimeoutMs: Long = 30_000L,
) {
    init {
        require(apiKey.isNotBlank()) {
            "Melaya: apiKey is required (create one at melaya.org → Settings → API Keys)."
        }
        require(apiKey.startsWith("mk_")) {
            "Melaya: API keys must be prefixed 'mk_'."
        }
    }

    private val okHttp: OkHttpClient = buildOkHttpClient()
    private val http = HttpClient(apiKey, baseUrl, okHttp, requestTimeoutMs)

    // ── Trading plane ──────────────────────────────────────────────────────────

    /** REST market-data + reference endpoints (public and authenticated plane). */
    val market = MarketAPI(http)

    /** Authenticated account reads: connected exchange keys, tier limits, usage. */
    val account = AccountAPI(http)

    /** Paper trading (sim broker): virtual balance, positions, and orders. */
    val sim = SimAPI(http)

    /** Live trading (real funds): order placement, positions, balance on a connected venue. */
    val trade = TradeAPI(http)

    /** Launch, control, and inspect trading strategies (paper + live). */
    val strategies = StrategiesAPI(http)

    /** Historical backtests + parameter sweeps (including optimization runs) on the Rust engine. */
    val backtest = BacktestAPI(http)

    /** WebSocket streaming endpoints (public market data + private feeds). */
    val stream = StreamAPI(apiKey, wsUrl, http, okHttp)

    // ── Platform / agents plane ────────────────────────────────────────────────

    /**
     * Auth endpoints: login, register, MFA, password reset, session management.
     * Also includes MFA setup/confirm via [mfa].
     */
    val auth = AuthAPI(http)

    /** MFA enrollment: status, TOTP setup, confirm. */
    val mfa = MfaAPI(http)

    /**
     * Account profile, GDPR data export, CEX key removal, and credit balances.
     * For exchange key reads use [account]; for billing use [billing].
     */
    val accounts = AccountsAPI(http)

    /** Billing: subscription status, Stripe checkout / portal, pricing plans. */
    val billing = BillingAPI(http)

    /** Runner token management: mint, list, revoke `mel_run_` tokens. */
    val runner = RunnerAPI(http)

    /** Create and list agent projects. */
    val projects = ProjectsAPI(http)

    /** Pipeline run overview, traces, schedules, model prices, and server version. */
    val pipelines = PipelinesAPI(http)

    /** Human-in-the-loop approval queue: list pending, approve, reject, inspect run tool calls. */
    val hitl = HitlAPI(http)

    /** User-scoped credential storage (services, OAuth, RAG, env handles, AI models). */
    val credentials = CredentialsAPI(http)

    /** Project-scoped connector credentials (per-project service keys). */
    val connectors = ConnectorsAPI(http)

    /** Phone device control: pair, list, screen-tree, apps, active run registration. */
    val phone = PhoneAPI(http)

    /** Project team management: members, roles, invite links, pipeline visibility. */
    val team = TeamAPI(http)

    /** Pipeline templates: create, share, assign, and manage visibility. */
    val templates = TemplatesAPI(http)

    /** Assistant onboarding profile (get + set). */
    val assistant = AssistantAPI(http)

    /** Agent evaluation runs and benchmark scores. */
    val evals = EvalsAPI(http)

    /** In-app bug reports and notifications. */
    val bugs = BugsAPI(http)

    /**
     * Platform real-time events over Socket.IO at `/api/v1/events`.
     *
     * Subscribe to run updates, init-phase progress, HITL notifications,
     * and pipeline CRUD events. The connection opens lazily on the first
     * subscription or room join — constructing [Melaya] performs no events
     * I/O, so REST-only usage never opens a socket.
     *
     * Always call [MelayaEvents.close] when done, or use [AutoCloseable] with
     * try-with-resources.
     */
    val events = MelayaEvents(baseUrl, apiKey, okHttp)

    // ── Domain namespaces (primary API) ────────────────────────────────────────

    /**
     * Trading plane: market data, account, paper trading, live trading,
     * strategies, backtesting, and WebSocket streaming.
     *
     * ```kotlin
     * melaya.trading.market.ticker("binance", "BTC/USDT", "spot")
     * melaya.trading.strategies.list()
     * melaya.trading.backtest.start(…)
     * ```
     */
    val trading = TradingNamespace(
        market     = market,
        account    = account,
        sim        = sim,
        strategies = strategies,
        trade      = trade,
        backtest   = backtest,
        stream     = stream,
    )

    /**
     * Agents plane: pipeline runs, HITL approvals, assistant, phone control,
     * and evaluations.
     *
     * ```kotlin
     * melaya.agents.pipelines.recent()
     * melaya.agents.hitl.pending()
     * melaya.agents.evals.benchmarks()
     * ```
     */
    val agents = AgentsNamespace(
        pipelines = pipelines,
        hitl      = hitl,
        assistant = assistant,
        phone     = phone,
        evals     = evals,
    )

    /**
     * Platform plane: projects, credentials, connectors, billing, team,
     * templates, runner tokens, auth, MFA, accounts, bugs, and real-time events.
     *
     * ```kotlin
     * melaya.platform.projects.list()
     * melaya.platform.credentials.set("openai", value = "sk-…")
     * melaya.platform.events.onRunUpdate("run-123") { … }
     * ```
     */
    val platform = PlatformNamespace(
        projects    = projects,
        credentials = credentials,
        connectors  = connectors,
        billing     = billing,
        team        = team,
        templates   = templates,
        runner      = runner,
        auth        = auth,
        mfa         = mfa,
        accounts    = accounts,
        bugs        = bugs,
        events      = events,
    )

    // ── OkHttp client builder ──────────────────────────────────────────────────

    private fun buildOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor())
            .build()
    }
}
