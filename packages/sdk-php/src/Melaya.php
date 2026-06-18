<?php

declare(strict_types=1);

namespace Melaya;

/**
 * The Melaya client.
 *
 * @example
 * ```php
 * require_once __DIR__ . '/vendor/autoload.php'; // or autoload.php
 * use Melaya\Melaya;
 *
 * $m = new Melaya(apiKey: getenv('MK'));
 * $ticker = $m->market->ticker('binance', 'BTC/USDT', 'spot');
 * echo $ticker['last'];
 * ```
 */
class Melaya
{
    public readonly MarketAPI     $market;
    public readonly AccountAPI    $account;
    public readonly SimAPI        $sim;
    public readonly StrategiesAPI $strategies;
    public readonly BacktestAPI   $backtest;
    public readonly StreamAPI     $stream;
    public readonly TradeAPI      $trade;

    public function __construct(
        string $apiKey,
        string $baseUrl = 'https://api.melaya.org',
        string $wsUrl   = 'wss://wss.melaya.org',
    ) {
        if ($apiKey === '') {
            throw new \InvalidArgumentException(
                'Melaya: apiKey is required (create one at melaya.org → Settings → API Keys).',
            );
        }
        if (!str_starts_with($apiKey, 'mk_')) {
            throw new \InvalidArgumentException('Melaya: API keys must be prefixed `mk_`.');
        }

        $http = new HttpClient($apiKey, $baseUrl);

        $this->market     = new MarketAPI($http);
        $this->account    = new AccountAPI($http);
        $this->sim        = new SimAPI($http);
        $this->strategies = new StrategiesAPI($http);
        $this->backtest   = new BacktestAPI($http);
        $this->stream     = new StreamAPI($apiKey, $wsUrl, $http);
        $this->trade      = new TradeAPI($http);
    }
}
