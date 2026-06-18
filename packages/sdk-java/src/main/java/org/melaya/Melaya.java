package org.melaya;

/**
 * The Melaya client — entry point for all SDK operations.
 *
 * <pre>{@code
 * Melaya melaya = new Melaya("mk_yourkey");
 * JsonNode ticker = melaya.market().ticker("binance", "BTC/USDT", "spot");
 * System.out.println(ticker.get("last"));
 * }</pre>
 *
 * <p>REST base URL: {@code https://api.melaya.org}<br>
 * WS base URL: {@code wss://wss.melaya.org}
 *
 * <p>Every REST call injects the API key as both:
 * <ul>
 *   <li>{@code ?apiKey=mk_...} query parameter</li>
 *   <li>{@code Authorization: Bearer mk_...} header</li>
 * </ul>
 *
 * <p>Set env var {@code MELAYA_INSECURE_TLS=1} to disable TLS certificate
 * verification (development/proxy environments only — not recommended for production).
 */
public class Melaya {

    public static final String DEFAULT_BASE_URL = "https://api.melaya.org";
    public static final String DEFAULT_WS_URL = "wss://wss.melaya.org";

    private final MarketAPI market;
    private final AccountAPI account;
    private final SimAPI sim;
    private final TradeAPI trade;
    private final StrategiesAPI strategies;
    private final BacktestAPI backtest;
    private final StreamAPI stream;

    /**
     * Create a Melaya client with the given API key.
     *
     * @param apiKey your Melaya API key (must start with {@code mk_})
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
        this.market = new MarketAPI(http);
        this.account = new AccountAPI(http);
        this.sim = new SimAPI(http);
        this.trade = new TradeAPI(http);
        this.strategies = new StrategiesAPI(http);
        this.backtest = new BacktestAPI(http);
        this.stream = new StreamAPI(apiKey, wsUrl, http);
    }

    /**
     * Package-private constructor for injecting a pre-built HttpClient (e.g. E2E with trust-all TLS).
     */
    Melaya(HttpClient http, String wsUrl) {
        this.market = new MarketAPI(http);
        this.account = new AccountAPI(http);
        this.sim = new SimAPI(http);
        this.trade = new TradeAPI(http);
        this.strategies = new StrategiesAPI(http);
        this.backtest = new BacktestAPI(http);
        this.stream = new StreamAPI(http.getApiKey(), wsUrl, http);
    }

    /** REST market-data + reference endpoints (public plane). */
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
}
