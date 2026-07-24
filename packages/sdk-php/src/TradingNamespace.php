<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Grouped accessor for all trading-plane modules.
 *
 * Access via `$melaya->trading->*`.
 *
 * @example
 * ```php
 * $m = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * // Market data
 * $ticker = $m->trading->market->ticker('binance', 'BTC/USDT', 'spot');
 *
 * // Strategy lifecycle
 * $m->trading->strategies->pause($id);
 *
 * // Live WebSocket stream
 * $ws = $m->trading->stream->ticker('binance', 'BTC/USDT', 'spot');
 *
 * // Backtest
 * $job = $m->trading->backtest->start([...]);
 *
 * // Parameter sweep optimizer
 * $opt = $m->trading->optimize->start([...]);
 * ```
 */
final class TradingNamespace
{
    /** REST market-data + reference endpoints (public and authenticated). */
    public readonly MarketAPI $market;

    /** Authenticated account reads: connected CEX keys, tier limits, usage. */
    public readonly AccountAPI $account;

    /** Paper trading (sim broker): virtual balance, positions, and orders. */
    public readonly SimAPI $sim;

    /** Launch, control, and inspect trading strategies (paper + live). */
    public readonly StrategiesAPI $strategies;

    /** Live credentialed trading on a connected exchange (real funds). */
    public readonly TradeAPI $trade;

    /** Historical backtests + parameter sweeps on the Rust engine. */
    public readonly BacktestAPI $backtest;

    /** WebSocket streaming endpoints (public market data + private feeds). */
    public readonly StreamAPI $stream;

    /** Parameter sweep / genetic optimizer for strategy configs. */
    public readonly OptimizeAPI $optimize;

    /** @internal Constructed by {@see Melaya}. */
    public function __construct(
        MarketAPI $market,
        AccountAPI $account,
        SimAPI $sim,
        StrategiesAPI $strategies,
        TradeAPI $trade,
        BacktestAPI $backtest,
        StreamAPI $stream,
        OptimizeAPI $optimize,
    ) {
        $this->market     = $market;
        $this->account    = $account;
        $this->sim        = $sim;
        $this->strategies = $strategies;
        $this->trade      = $trade;
        $this->backtest   = $backtest;
        $this->stream     = $stream;
        $this->optimize   = $optimize;
    }
}
