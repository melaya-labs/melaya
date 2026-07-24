package org.melaya;

/**
 * The {@code trading} namespace — groups all trading-plane modules into one
 * crystal-clear entry point.
 *
 * <p>Access via {@link Melaya#trading()}:
 * <pre>{@code
 * Melaya melaya = new Melaya("mk_yourkey");
 *
 * // Market data
 * JsonNode ticker = melaya.trading().market().ticker("binance", "BTC/USDT", "spot");
 *
 * // Live trading
 * melaya.trading().trade().createOrder(...);
 *
 * // WebSocket streams
 * try (MelayaStream s = melaya.trading().stream().ticker("binance", "BTC/USDT", "spot")) { ... }
 * }</pre>
 *
 * <p>Modules in this namespace:
 * <ul>
 *   <li>{@link #market()} — public + authenticated market-data endpoints</li>
 *   <li>{@link #account()} — authenticated CEX account reads (keys, usage, tier)</li>
 *   <li>{@link #sim()} — paper-trading broker (virtual balance, positions, orders)</li>
 *   <li>{@link #strategies()} — launch, control, and inspect trading strategies</li>
 *   <li>{@link #backtest()} — historical backtests + parameter sweeps</li>
 *   <li>{@link #trade()} — live trading (real funds)</li>
 *   <li>{@link #stream()} — WebSocket streaming endpoints</li>
 * </ul>
 */
public final class TradingNamespace {

    private final MarketAPI     market;
    private final AccountAPI    account;
    private final SimAPI        sim;
    private final StrategiesAPI strategies;
    private final BacktestAPI   backtest;
    private final TradeAPI      trade;
    private final StreamAPI     stream;

    TradingNamespace(
            MarketAPI     market,
            AccountAPI    account,
            SimAPI        sim,
            StrategiesAPI strategies,
            BacktestAPI   backtest,
            TradeAPI      trade,
            StreamAPI     stream) {
        this.market     = market;
        this.account    = account;
        this.sim        = sim;
        this.strategies = strategies;
        this.backtest   = backtest;
        this.trade      = trade;
        this.stream     = stream;
    }

    /** REST market-data + reference endpoints (public + authenticated). */
    public MarketAPI market() { return market; }

    /** Authenticated CEX account reads: connected keys, tier limits, usage. */
    public AccountAPI account() { return account; }

    /** Paper trading (sim broker): virtual balance, positions, and orders. */
    public SimAPI sim() { return sim; }

    /** Launch, control, and inspect trading strategies (paper + live). */
    public StrategiesAPI strategies() { return strategies; }

    /** Historical backtests + parameter sweeps on the Rust engine. */
    public BacktestAPI backtest() { return backtest; }

    /** Live credentialed trading on a connected exchange (real funds). */
    public TradeAPI trade() { return trade; }

    /** WebSocket streaming endpoints (public market data + private feeds). */
    public StreamAPI stream() { return stream; }
}
