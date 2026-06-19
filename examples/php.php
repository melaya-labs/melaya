<?php
// Melaya PHP SDK -- quickstart / smoke test.
//
//   composer require melaya/sdk
//   MELAYA_API_KEY=mk_... php examples/php.php
require __DIR__ . '/../packages/sdk-php/autoload.php';

use Melaya\Melaya;

$apiKey = getenv('MELAYA_API_KEY') ?: exit("Set MELAYA_API_KEY=mk_...\n");
$m = new Melaya($apiKey);

// 1. How many venues are live?
echo 'exchanges: ' . count($m->market->listExchanges()) . "\n";

// 2. Normalized REST ticker
$t = $m->market->ticker('binance', 'BTC/USDT', 'spot');
echo "BTC/USDT last={$t['last']} bid={$t['bid']} ask={$t['ask']}\n";

// 3. Order book
$book = $m->market->orderbook('bybit', 'BTC/USDT', 5, 'spot');
echo 'top bid: ' . json_encode($book['bids'][0]) . '  top ask: ' . json_encode($book['asks'][0]) . "\n";

// 4. Live stream -- print 3 ticker frames then stop
$n = 0;
$stream = $m->stream->ticker('binance', 'BTC/USDT', 'spot');
foreach ($stream->frames() as $frame) {
    echo "stream: {$frame['last']}\n";
    if (++$n >= 3) break;
}
$stream->close();

// 5. Account -- connected keys + tier usage
echo 'connected keys: ' . count($m->account->keys()) . "\n";
echo 'tier: ' . ($m->account->usage()['tier'] ?? '') . "\n";

// 6. Paper trading -- launch a paper strategy (no exchange key needed) and
//    round-trip a synthetic order through the sim broker. Nothing hits a venue.
$created = $m->strategies->create([
    'name' => 'SDK example (paper)',
    'strategyType' => 'custom',              // custom Rhai definition
    'exchange' => 'binanceusdm', 'symbol' => 'BTC/USDT:USDT', 'market' => 'FUTURES',
    'dryRun' => true,                         // dryRun:false + apiKeyId => REAL orders
    'params' => ['language' => 'rhai', 'definition' => 'fn evaluate() { emit_long(param("qty")); }', 'qty' => 0.001],
]);
$sid = $created['strategyId'];
echo "launched paper strategy $sid\n";
$fill = $m->sim->createOrder($sid, 'binanceusdm', 'BTC/USDT:USDT', 'buy', 0.001, 'market', null, 'FUTURES');
echo "paper fill @ {$fill['fill_price']}\n";
echo 'paper balance: ' . json_encode($m->sim->balance($sid)) . "\n";
$m->strategies->stop($sid);

// 7. Backtest on the Rust engine
$bt = $m->backtest->start([
    'strategyType' => 'custom', 'exchange' => 'binance', 'symbol' => 'BTC/USDT', 'timeframe' => '1h',
    'language' => 'rhai', 'definition' => 'fn evaluate() { emit_long(param("qty")); }', 'params' => ['qty' => 0.001],
]);
echo "backtest job {$bt['job_id']} started\n";
echo "done\n";
