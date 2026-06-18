# Melaya PHP SDK

Official PHP SDK for the [Melaya](https://melaya.org) unified trading API —
market data across 70+ venues, paper and live strategies, backtesting, and
WebSocket streaming.

## Requirements

- PHP 8.1+
- `curl` and `openssl` extensions (bundled with most PHP distributions)

## Installation

```bash
composer require melaya/sdk
```

Without Composer (e.g. in a quick script):

```php
require_once '/path/to/sdk-php/autoload.php';
```

## Authentication

Create an API key at [melaya.org → Settings → API Keys](https://melaya.org).
Keys are prefixed `mk_`.

Never hard-code your key. Read it from an environment variable:

```php
$m = new Melaya\Melaya(apiKey: getenv('MK'));
```

## Quick Start

### Market data

```php
require_once 'vendor/autoload.php';
use Melaya\Melaya;

$m = new Melaya(apiKey: getenv('MK'));

// Ticker
$t = $m->market->ticker('binance', 'BTC/USDT', 'spot');
echo $t['last'];

// OHLCV candles (last 100)
$candles = $m->market->ohlcv('binance', 'BTC/USDT', '1h', 100, 'spot');

// List supported exchanges
$exchanges = $m->market->listExchanges();
```

### Paper strategy + backtest

```php
$RHAI = 'fn evaluate() { emit_long(param("qty")); }';

// Create a paper strategy
$r = $m->strategies->create([
    'name'         => 'my-paper-bot',
    'strategyType' => 'custom',
    'exchange'     => 'binance',
    'symbol'       => 'BTC/USDT',
    'market'       => 'spot',
    'dryRun'       => true,
    'params'       => [
        'language'   => 'rhai',
        'definition' => $RHAI,
        'qty'        => 0.001,
    ],
]);
$strategyId = $r['strategyId'];

// Clean up when done
$m->strategies->stop($strategyId);
$m->strategies->delete($strategyId);

// Run a backtest
$now     = (int)(microtime(true) * 1000);
$sinceMs = $now - 90 * 24 * 3600 * 1000;

$job = $m->backtest->start([
    'strategyType' => 'custom',
    'language'     => 'rhai',
    'definition'   => $RHAI,
    'exchange'     => 'binance',
    'symbol'       => 'BTC/USDT',
    'timeframe'    => '1h',
    'since_ms'     => $sinceMs,
    'until_ms'     => $now,
    'params'       => ['qty' => 0.001],
]);
$jobId = $job['job_id'];

// Poll to completion
do {
    sleep(3);
    $j = $m->backtest->job($jobId);
} while (!in_array($j['status'], ['completed', 'done', 'finished']));

$results = $m->backtest->results($jobId);
```

### WebSocket streaming

```php
// Public ticker stream
$ws = $m->stream->ticker('binance', 'BTC/USDT', 'spot');
for ($i = 0; $i < 5; $i++) {
    $frame = $ws->readFrame();
    if ($frame) print_r($frame);
}
$ws->close();

// Private strategies stream
$ws = $m->stream->strategies();
$frame = $ws->readFrame();
print_r($frame);
$ws->close();
```

## Method Reference

### `$m->market`

| Method | Description |
|--------|-------------|
| `listExchanges()` | List supported exchanges |
| `ticker($exchange, $symbol, $market)` | Best bid/ask, last, 24h aggregates |
| `orderbook($exchange, $symbol, $limit, $market)` | Order book |
| `ohlcv($exchange, $symbol, $timeframe, $limit, $market)` | OHLCV candles |
| `trades($exchange, $symbol, $market)` | Recent public trades |
| `markets($exchange)` | Tradable markets on a venue |
| `currencies($exchange)` | Listed currencies |
| `status($exchange)` | Operational status |
| `time($exchange)` | Exchange server time |
| `tickers($exchange, $symbols, $market)` | Batch tickers (POST) |
| `fundingRates($exchange, $symbols, $market)` | Latest funding rates |
| `fundingRateHistory($exchange, $symbol, $hours, $market)` | Funding rate history |
| `openInterest($exchange, $symbols, $market)` | Open interest |
| `openInterestHistory($exchange, $symbol, $hours, $market)` | OI history |
| `instruments($exchange, $market)` | Instrument list |
| `liquidationEvents($exchange, $symbol, $sinceMs, $limit)` | Liquidation events |
| `ohlcvMulti($exchange, $symbols, $timeframe, $limit, $market)` | Multi-symbol OHLCV |
| `marketConstraints($exchange, $symbol, $market)` | Trading constraints |
| `fundingRateHistoryMulti($exchanges, $symbol, $hours)` | Multi-venue funding history |
| `openInterestHistoryMulti($exchanges, $symbol, $hours)` | Multi-venue OI history |
| `predictionMarkets($venue)` | Prediction market listings |
| `catalogCounts()` | Platform catalog counts |

### `$m->account`

| Method | Description |
|--------|-------------|
| `keys()` | Connected exchange keys |
| `usage()` | Tier, limits, and usage counters |
| `apiKeyStatus()` | Platform API key status |

### `$m->sim`

| Method | Description |
|--------|-------------|
| `listAccounts()` | Paper accounts |
| `balance($strategyId, $asset)` | Virtual balance |
| `positions($strategyId)` | Open paper positions |
| `openOrders($strategyId)` | Resting paper orders |
| `myTrades($strategyId)` | Filled paper trades |
| `createOrder(...)` | Place a paper order |
| `cancelOrder($strategyId, $orderId, ...)` | Cancel a paper order |

### `$m->strategies`

| Method | Description |
|--------|-------------|
| `list()` | All strategies |
| `get($id)` | Single strategy |
| `create($body)` | Launch a strategy |
| `pause($id)` | Pause |
| `resume($id)` | Resume |
| `stop($id)` | Stop and teardown |
| `delete($id)` | Soft-delete |
| `updateParams($id, $params)` | Update params |
| `status($id)` | Runtime status |
| `performance($id)` | Performance series |
| `executions($id)` | Execution rows |
| `trades($id)` | Trade rows |
| `logs($id)` | Log rows |
| `aiOptStart($id, $paramBounds, ...)` | Start AI optimizer |
| `aiOptStatus($id)` | Optimizer status |
| `aiOptApprove($id, $body)` | Approve proposed params |
| `aiOptStop($id)` | Stop optimizer |
| `aiOptRuns($id)` | Past optimization runs |

### `$m->backtest`

| Method | Description |
|--------|-------------|
| `start($body)` | Start a backtest |
| `job($jobId)` | Job status |
| `results($jobId)` | Metrics + equity curve |
| `trades($jobId, $limit, $offset)` | Trade list |
| `sweep($parentId, ...)` | Sweep results |
| `list($limit, $offset)` | All jobs |
| `favorites($limit, $offset)` | Favorited jobs |
| `fundingRange($exchange, $symbol)` | Earliest funding timestamp |
| `cancel($jobId)` | Cancel job |
| `delete($jobId)` | Delete job |
| `deleteAll()` | Delete all non-favorited jobs |

### `$m->stream` (WebSocket)

| Method | Description |
|--------|-------------|
| `ticker($exchange, $symbol, $market)` | Live ticker frames |
| `orderbook($exchange, $symbol, $limit, $market)` | Live order book |
| `ohlcv($exchange, $symbol, $timeframe, $market)` | Live OHLCV frames |
| `trades($exchange, $symbol, $market)` | Live public trades |
| `liquidations($exchange)` | Liquidation firehose |
| `strategies()` | Private strategy events |
| `private($exchange, ...)` | Private account feed |

Returns a `WsClient`. Call `readFrame()` to receive one JSON frame, `close()` when done.

## TLS Note

On some corporate or dev networks (intercepting proxies) certificate verification
must be disabled. Set `MELAYA_INSECURE_TLS=1` in the environment:

```bash
MELAYA_INSECURE_TLS=1 php my_script.php
```

The SDK is **secure by default** — verification is only skipped when this flag is set.

## License

MIT
