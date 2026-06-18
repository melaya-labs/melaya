<?php

declare(strict_types=1);

namespace Melaya;

/**
 * REST market-data API — normalized across all 70+ venues.
 *
 * Maps to https://api.melaya.org/api/v1/market/*.
 * GET endpoints accept query params; POST batch endpoints accept a JSON body.
 */
class MarketAPI
{
    public function __construct(private readonly HttpClient $http) {}

    /** List the exchanges Melaya supports right now (the source of truth). */
    public function listExchanges(): array
    {
        return $this->http->get('/api/v1/market/list-exchanges')['exchanges'];
    }

    /** Best bid/ask, last price, and 24h aggregates for one symbol. */
    public function ticker(string $exchange, string $symbol, ?string $market = null): array
    {
        return $this->http->get('/api/v1/market/ticker', array_filter(
            ['exchange' => $exchange, 'symbol' => $symbol, 'market' => $market],
            fn($v) => $v !== null,
        ))['ticker'];
    }

    /** Order book to a given depth. */
    public function orderbook(string $exchange, string $symbol, ?int $limit = null, ?string $market = null): array
    {
        return $this->http->get('/api/v1/market/orderbook', array_filter(
            ['exchange' => $exchange, 'symbol' => $symbol, 'limit' => $limit, 'market' => $market],
            fn($v) => $v !== null,
        ))['orderbook'];
    }

    /** OHLCV candles. Each candle is [ts, open, high, low, close, volume]. */
    public function ohlcv(string $exchange, string $symbol, string $timeframe, ?int $limit = null, ?string $market = null): array
    {
        return $this->http->get('/api/v1/market/ohlcv', array_filter(
            ['exchange' => $exchange, 'symbol' => $symbol, 'timeframe' => $timeframe, 'limit' => $limit, 'market' => $market],
            fn($v) => $v !== null,
        ))['candles'];
    }

    /** Recent public trades. */
    public function trades(string $exchange, string $symbol, ?string $market = null): array
    {
        return $this->http->get('/api/v1/market/trades', array_filter(
            ['exchange' => $exchange, 'symbol' => $symbol, 'market' => $market],
            fn($v) => $v !== null,
        ))['trades'];
    }

    /** Tradable markets on a venue. */
    public function markets(string $exchange): array
    {
        return $this->http->get('/api/v1/market/markets', ['exchange' => $exchange])['markets'];
    }

    /** Listed currencies on a venue. (Not supported on every venue.) */
    public function currencies(string $exchange): array
    {
        return $this->http->get('/api/v1/market/currencies', ['exchange' => $exchange])['currencies'];
    }

    /** Operational status: ok / maintenance / degraded. */
    public function status(string $exchange): mixed
    {
        return $this->http->get('/api/v1/market/status', ['exchange' => $exchange])['status'];
    }

    /** Exchange server time. */
    public function time(string $exchange): mixed
    {
        return $this->http->get('/api/v1/market/time', ['exchange' => $exchange])['time'];
    }

    // ── Batch / derivatives (POST) ──────────────────────────────────────────

    /** Tickers for many symbols on one venue in a single call. Keyed by symbol. */
    public function tickers(string $exchange, array $symbols, ?string $market = null): array
    {
        $body = array_filter(['exchange' => $exchange, 'symbols' => $symbols, 'market' => $market], fn($v) => $v !== null);
        return $this->http->post('/api/v1/market/tickers', $body)['tickers'];
    }

    /** Latest funding rates for perpetuals. Keyed by symbol. */
    public function fundingRates(string $exchange, array $symbols, ?string $market = null): array
    {
        $body = array_filter(['exchange' => $exchange, 'symbols' => $symbols, 'market' => $market], fn($v) => $v !== null);
        return $this->http->post('/api/v1/market/funding-rates', $body)['rates'];
    }

    /** Funding-rate history. */
    public function fundingRateHistory(string $exchange, string $symbol, ?int $hours = null, ?string $market = null): array
    {
        $body = array_filter(['exchange' => $exchange, 'symbol' => $symbol, 'hours' => $hours, 'market' => $market], fn($v) => $v !== null);
        return $this->http->post('/api/v1/market/funding-rate-history', $body)['history'];
    }

    /** Open interest for one or more perpetuals. Keyed by symbol. */
    public function openInterest(string $exchange, array $symbols, ?string $market = null): array
    {
        $body = array_filter(['exchange' => $exchange, 'symbols' => $symbols, 'market' => $market], fn($v) => $v !== null);
        return $this->http->post('/api/v1/market/open-interest', $body)['openInterest'];
    }

    /** Open-interest history. */
    public function openInterestHistory(string $exchange, string $symbol, ?int $hours = null, ?string $market = null): array
    {
        $body = array_filter(['exchange' => $exchange, 'symbol' => $symbol, 'hours' => $hours, 'market' => $market], fn($v) => $v !== null);
        return $this->http->post('/api/v1/market/open-interest-history', $body)['history'];
    }

    /** Instrument list + trading constraints (tick size, min notional, qty step). */
    public function instruments(string $exchange, ?string $market = null): mixed
    {
        $body = array_filter(['exchange' => $exchange, 'market' => $market], fn($v) => $v !== null);
        return $this->http->post('/api/v1/market/instruments', $body);
    }

    /** Cross-exchange liquidation events (historical query). */
    public function liquidationEvents(?string $exchange = null, ?string $symbol = null, ?int $sinceMs = null, ?int $limit = null): array
    {
        $body = array_filter(
            ['exchange' => $exchange, 'symbol' => $symbol, 'sinceMs' => $sinceMs, 'limit' => $limit],
            fn($v) => $v !== null,
        );
        return $this->http->post('/api/v1/market/liquidation-events', $body)['events'];
    }

    /** Multi-symbol OHLCV in one call. Returns candle arrays keyed by symbol. */
    public function ohlcvMulti(string $exchange, array $symbols, string $timeframe, ?int $limit = null, ?string $market = null): array
    {
        $body = array_filter(
            ['exchange' => $exchange, 'symbols' => $symbols, 'timeframe' => $timeframe, 'limit' => $limit, 'market' => $market],
            fn($v) => $v !== null,
        );
        return $this->http->post('/api/v1/market/ohlcv-multi', $body)['perSymbol'];
    }

    /** Trading constraints for one symbol (tick size, min notional, qty step, leverage). */
    public function marketConstraints(string $exchange, string $symbol, ?string $market = null): mixed
    {
        $body = array_filter(['exchange' => $exchange, 'symbol' => $symbol, 'market' => $market], fn($v) => $v !== null);
        return $this->http->post('/api/v1/market/market-constraints', $body)['constraints'];
    }

    /** Funding-rate history for one symbol across several venues. Keyed by exchange. */
    public function fundingRateHistoryMulti(array $exchanges, string $symbol, ?int $hours = null): array
    {
        $body = array_filter(['exchanges' => $exchanges, 'symbol' => $symbol, 'hours' => $hours], fn($v) => $v !== null);
        return $this->http->post('/api/v1/market/funding-rate-history-multi', $body)['perExchange'];
    }

    /** Open-interest history for one symbol across several venues. Keyed by exchange. */
    public function openInterestHistoryMulti(array $exchanges, string $symbol, ?int $hours = null): array
    {
        $body = array_filter(['exchanges' => $exchanges, 'symbol' => $symbol, 'hours' => $hours], fn($v) => $v !== null);
        return $this->http->post('/api/v1/market/open-interest-history-multi', $body)['perExchange'];
    }

    /** Prediction-market listings for a venue (polymarket, kalshi, drift_pm, sxbet, azuro, overtime). */
    public function predictionMarkets(string $venue = 'polymarket'): array
    {
        return $this->http->post('/api/v1/market/pm-markets', ['venue' => $venue])['markets'];
    }

    /** Live platform catalog counts (agentic tools, subagents, by category). Public. */
    public function catalogCounts(): array
    {
        return $this->http->get('/api/v1/public/catalog-counts');
    }
}
