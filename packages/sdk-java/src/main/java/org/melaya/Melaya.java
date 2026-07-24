package org.melaya;

/**
 * The Melaya client — entry point for all SDK operations.
 *
 * <h2>Namespaced API (recommended)</h2>
 * <pre>{@code
 * Melaya melaya = new Melaya("mk_yourkey");
 *
 * // ── trading ──────────────────────────────────────────────────────────────
 * JsonNode ticker   = melaya.trading().market().ticker("binance", "BTC/USDT", "spot");
 * melaya.trading().trade().createOrder(...);                   // real funds
 * melaya.trading().backtest().start(body);
 * try (MelayaStream s = melaya.trading().stream().ticker("binance","BTC/USDT","spot")) { ... }
 *
 * // ── agents ───────────────────────────────────────────────────────────────
 * melaya.agents().pipelines().traces("run-123");
 * JsonNode pending = melaya.agents().hitl().pending();
 * melaya.agents().hitl().approve(pending.get(0).get("requestId").asText(), null);
 * melaya.agents().evals().summary();
 *
 * // ── platform ─────────────────────────────────────────────────────────────
 * JsonNode projects = melaya.platform().projects().list();
 * melaya.platform().credentials().set("openai", Map.of("value", "sk-..."));
 * melaya.platform().billing().subscription();
 * melaya.platform().events().onRunUpdate("run-123", frame ->
 *     System.out.println(frame.get("event_type").asText()));
 * }</pre>
 *
 * <h2>Flat API (legacy, also valid)</h2>
 * <pre>{@code
 * // Equivalent flat accessors are preserved for backward compatibility:
 * melaya.market().ticker("binance", "BTC/USDT", "spot");
 * melaya.projects().list();
 * melaya.hitl().pending();
 * melaya.events().onRunUpdate("run-123", frame -> System.out.println(frame));
 * }</pre>
 *
 * <p><strong>REST base URL:</strong> {@code https://api.melaya.org}<br>
 * <strong>WS base URL:</strong> {@code wss://wss.melaya.org}
 *
 * <p>Every REST call injects the API key as:
 * <ul>
 *   <li>{@code Authorization: Bearer mk_...} header</li>
 * </ul>
 *
 * <p><strong>TLS:</strong> certificate and hostname verification always use the
 * JVM trust store; verification is never disabled.
 *
 * <p><strong>Retries:</strong> only idempotent GET requests are retried — up to
 * 2 extra attempts on network errors, 429, and 5xx responses, with exponential
 * back-off (500 ms → 1 s, plus up to 200 ms of random jitter), respecting the
 * {@code Retry-After} header when present. Non-GET methods are never retried.
 *
 * <p><strong>Security:</strong> API keys and JWT tokens are never logged.
 * Secrets are only carried in {@code Authorization} headers, never echoed in
 * exception messages.
 */
public class Melaya {

    /** REST base URL. */
    public static final String DEFAULT_BASE_URL = "https://api.melaya.org";
    /** WebSocket base URL. */
    public static final String DEFAULT_WS_URL   = "wss://wss.melaya.org";

    // ── Namespace accessors (primary API) ─────────────────────────────────────
    private final TradingNamespace  trading;
    private final AgentsNamespace   agents;
    private final PlatformNamespace platform;

    // ── Trading plane (flat — kept for backward compatibility) ────────────────
    private final MarketAPI     market;
    private final AccountAPI    account;
    private final SimAPI        sim;
    private final TradeAPI      trade;
    private final StrategiesAPI strategies;
    private final BacktestAPI   backtest;
    private final StreamAPI     stream;

    // ── Platform / agents plane (flat — kept for backward compatibility) ──────
    private final AuthAPI        auth;
    private final MfaAPI         mfa;
    private final AccountsAPI    accounts;
    private final BillingAPI     billing;
    private final RunnerAPI      runner;
    private final ProjectsAPI    projects;
    private final PipelinesAPI   pipelines;
    private final HitlAPI        hitl;
    private final CredentialsAPI credentials;
    private final ConnectorsAPI  connectors;
    private final OverviewAPI    overview;
    private final PhoneAPI       phone;
    private final TeamAPI        team;
    private final TemplatesAPI   templates;
    private final AssistantAPI   assistant;
    private final EvalsAPI       evals;
    private final BugsAPI        bugs;
    private final MelayaEvents   events;

    /**
     * Create a Melaya client with the given API key.
     *
     * @param apiKey your Melaya API key (must start with {@code mk_})
     * @throws IllegalArgumentException if the key is blank or not prefixed {@code mk_}
     */
    public Melaya(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, DEFAULT_WS_URL);
    }

    /**
     * Create a Melaya client with custom base URLs (useful for testing or staging).
     *
     * @param apiKey  your Melaya API key (must start with {@code mk_})
     * @param baseUrl REST base URL override
     * @param wsUrl   WebSocket base URL override
     */
    public Melaya(String apiKey, String baseUrl, String wsUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Melaya: apiKey is required (create one at melaya.org → Settings → API Keys).");
        }
        if (!apiKey.startsWith("mk_")) {
            throw new IllegalArgumentException("Melaya: API keys must be prefixed 'mk_'.");
        }
        HttpClient http = new HttpClient(apiKey, baseUrl);
        this.market      = new MarketAPI(http);
        this.account     = new AccountAPI(http);
        this.sim         = new SimAPI(http);
        this.trade       = new TradeAPI(http);
        this.strategies  = new StrategiesAPI(http);
        this.backtest    = new BacktestAPI(http);
        this.stream      = new StreamAPI(apiKey, wsUrl, http);
        this.auth        = new AuthAPI(http);
        this.mfa         = new MfaAPI(http);
        this.accounts    = new AccountsAPI(http);
        this.billing     = new BillingAPI(http);
        this.runner      = new RunnerAPI(http);
        this.projects    = new ProjectsAPI(http);
        this.pipelines   = new PipelinesAPI(http);
        this.hitl        = new HitlAPI(http);
        this.credentials = new CredentialsAPI(http);
        this.connectors  = new ConnectorsAPI(http);
        this.overview    = new OverviewAPI(http);
        this.phone       = new PhoneAPI(http);
        this.team        = new TeamAPI(http);
        this.templates   = new TemplatesAPI(http);
        this.assistant   = new AssistantAPI(http);
        this.evals       = new EvalsAPI(http);
        this.bugs        = new BugsAPI(http);
        this.events      = new MelayaEvents(apiKey, baseUrl);
        // ── Namespace wrappers (share the already-constructed module instances) ──
        this.trading  = new TradingNamespace(
                this.market, this.account, this.sim,
                this.strategies, this.backtest, this.trade, this.stream);
        this.agents   = new AgentsNamespace(
                this.pipelines, this.hitl, this.assistant, this.phone, this.evals);
        this.platform = new PlatformNamespace(
                this.projects, this.credentials, this.connectors, this.billing,
                this.team, this.templates, this.overview, this.runner,
                this.auth, this.mfa, this.accounts, this.bugs, this.events);
    }

    /** Package-private constructor for injecting a pre-built HttpClient (e.g. E2E). */
    Melaya(HttpClient http, String wsUrl) {
        String apiKey = http.getApiKey();
        String baseUrl = http.getBaseUrl();
        this.market      = new MarketAPI(http);
        this.account     = new AccountAPI(http);
        this.sim         = new SimAPI(http);
        this.trade       = new TradeAPI(http);
        this.strategies  = new StrategiesAPI(http);
        this.backtest    = new BacktestAPI(http);
        this.stream      = new StreamAPI(apiKey, wsUrl, http);
        this.auth        = new AuthAPI(http);
        this.mfa         = new MfaAPI(http);
        this.accounts    = new AccountsAPI(http);
        this.billing     = new BillingAPI(http);
        this.runner      = new RunnerAPI(http);
        this.projects    = new ProjectsAPI(http);
        this.pipelines   = new PipelinesAPI(http);
        this.hitl        = new HitlAPI(http);
        this.credentials = new CredentialsAPI(http);
        this.connectors  = new ConnectorsAPI(http);
        this.overview    = new OverviewAPI(http);
        this.phone       = new PhoneAPI(http);
        this.team        = new TeamAPI(http);
        this.templates   = new TemplatesAPI(http);
        this.assistant   = new AssistantAPI(http);
        this.evals       = new EvalsAPI(http);
        this.bugs        = new BugsAPI(http);
        this.events      = new MelayaEvents(apiKey, baseUrl);
        // ── Namespace wrappers (share the already-constructed module instances) ──
        this.trading  = new TradingNamespace(
                this.market, this.account, this.sim,
                this.strategies, this.backtest, this.trade, this.stream);
        this.agents   = new AgentsNamespace(
                this.pipelines, this.hitl, this.assistant, this.phone, this.evals);
        this.platform = new PlatformNamespace(
                this.projects, this.credentials, this.connectors, this.billing,
                this.team, this.templates, this.overview, this.runner,
                this.auth, this.mfa, this.accounts, this.bugs, this.events);
    }

    // ── Namespace accessors (primary API) ────────────────────────────────────

    /**
     * Trading namespace — market data, CEX account, sim, strategies, backtest,
     * live trade, and WebSocket streaming.
     *
     * <pre>{@code
     * melaya.trading().market().ticker("binance", "BTC/USDT", "spot");
     * melaya.trading().trade().createOrder(...);
     * melaya.trading().stream().orderbook("binance", "BTC/USDT", "spot", 20);
     * }</pre>
     */
    public TradingNamespace trading() { return trading; }

    /**
     * Agents namespace — pipeline runs, HITL approvals, assistant profile,
     * phone device control, and evaluations.
     *
     * <pre>{@code
     * melaya.agents().pipelines().traces("run-123");
     * melaya.agents().hitl().approve("requestId", null);
     * melaya.agents().evals().summary();
     * }</pre>
     */
    public AgentsNamespace agents() { return agents; }

    /**
     * Platform namespace — projects, credentials, connectors, billing, team,
     * templates, overview, runner, auth, MFA, accounts, bugs, and real-time events.
     *
     * <pre>{@code
     * melaya.platform().projects().list();
     * melaya.platform().credentials().set("openai", Map.of("value", "sk-..."));
     * melaya.platform().events().onRunUpdate("run-123", frame -> System.out.println(frame));
     * }</pre>
     */
    public PlatformNamespace platform() { return platform; }

    // ── Flat accessors (preserved for backward compatibility) ─────────────────

    /** REST market-data + reference endpoints (public + authenticated). */
    public MarketAPI market() { return market; }

    /** Authenticated account reads: connected keys, tier limits, usage. */
    public AccountAPI account() { return account; }

    /** Paper trading (sim broker): virtual balance, positions, and orders. */
    public SimAPI sim() { return sim; }

    /** Live trading (real funds): order placement, positions, balance on a connected venue. */
    public TradeAPI trade() { return trade; }

    /** Launch, control, and inspect trading strategies (paper + live). */
    public StrategiesAPI strategies() { return strategies; }

    /** Historical backtests + parameter sweeps on the Rust engine. */
    public BacktestAPI backtest() { return backtest; }

    /** WebSocket streaming endpoints (public market data + private feeds). */
    public StreamAPI stream() { return stream; }

    // ── Platform / agents plane accessors ─────────────────────────────────────

    /**
     * Auth API — session management, login, registration, password reset, token refresh.
     */
    public AuthAPI auth() { return auth; }

    /** MFA API — enroll, confirm, and check TOTP. */
    public MfaAPI mfa() { return mfa; }

    /**
     * Accounts API — profile update, GDPR export, API-key removal, credit balances.
     * Distinct from {@link #account()} which covers trading account reads.
     */
    public AccountsAPI accounts() { return accounts; }

    /** Billing API — subscription status, Stripe checkout/portal, pricing plans. */
    public BillingAPI billing() { return billing; }

    /** Runner token API — mint, list, and revoke {@code mel_run_} tokens. */
    public RunnerAPI runner() { return runner; }

    /** Projects API — create and list agent projects. */
    public ProjectsAPI projects() { return projects; }

    /** Pipelines API — run overview, traces, and cron schedules. */
    public PipelinesAPI pipelines() { return pipelines; }

    /** HITL API — list, approve, and reject pending tool-call approvals. */
    public HitlAPI hitl() { return hitl; }

    /** Credentials API — user-scoped credential storage, OAuth, RAG, models. */
    public CredentialsAPI credentials() { return credentials; }

    /** Connectors API — project-scoped connector credentials. */
    public ConnectorsAPI connectors() { return connectors; }

    /** Overview API — dashboard metrics, cost breakdown, pipeline lists. */
    public OverviewAPI overview() { return overview; }

    /** Phone API — pair and control Android devices via the Melaya APK. */
    public PhoneAPI phone() { return phone; }

    /** Team API — project team membership, roles, and invitations. */
    public TeamAPI team() { return team; }

    /** Templates API — create, share, assign, and manage pipeline templates. */
    public TemplatesAPI templates() { return templates; }

    /** Assistant API — get and set the caller's onboarding/persona profile. */
    public AssistantAPI assistant() { return assistant; }

    /** Evals API — list, compare, and inspect pipeline evaluation results. */
    public EvalsAPI evals() { return evals; }

    /** Bugs API — submit and track bug reports. */
    public BugsAPI bugs() { return bugs; }

    /**
     * Platform real-time events over Socket.IO at {@code /api/v1/events}.
     * Subscribe to run updates, init-phase progress, HITL notifications,
     * and pipeline CRUD events.
     */
    public MelayaEvents events() { return events; }
}
