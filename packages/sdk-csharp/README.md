# Melaya .NET SDK

Official .NET SDK for the [Melaya](https://melaya.org) trading platform.
Normalized market data, paper/live strategies, backtesting, and WebSocket streaming
across 70+ venues — with zero external NuGet dependencies.

## Installation

```xml
<!-- In your .csproj, once the package is published to NuGet -->
<PackageReference Include="Melaya.SDK" Version="0.1.0" />
```

Or reference the project directly during development:

```xml
<ProjectReference Include="../sdk-csharp/Melaya/Melaya.csproj" />
```

## Authentication

Every Melaya API key is prefixed `mk_`. Create one at **melaya.org → Settings → API Keys**.
The SDK sends the key as both `?apiKey=mk_…` query parameter and `Authorization: Bearer mk_…`
header on every request.

**Never commit your key.** Read it from an environment variable:

```csharp
var mk = Environment.GetEnvironmentVariable("MK")
    ?? throw new InvalidOperationException("MK env var not set");
```

## Quick start

```csharp
using Melaya;

var mk = Environment.GetEnvironmentVariable("MK")!;
await using var m = new MelayaClient(new MelayaOptions { ApiKey = mk });

// Market data
var ticker = await m.Market.TickerAsync("binance", "BTC/USDT", "spot");
Console.WriteLine($"BTC last: {ticker.GetProperty("last")}");

// Launch a custom paper strategy
var created = await m.Strategies.CreateAsync(new
{
    name         = "my-rhai-bot",
    strategyType = "custom",
    exchange     = "binance",
    symbol       = "BTC/USDT",
    market       = "spot",
    dryRun       = true,
    @params      = new
    {
        language   = "rhai",
        definition = "fn evaluate() { emit_long(param(\"qty\")); }",
        qty        = 0.001,
    },
});
string sid = created.StrategyId!;
Console.WriteLine($"Strategy: {sid}");

// Sim: virtual balance
var balance = await m.Sim.BalanceAsync(sid);
Console.WriteLine($"Balance: {balance}");

// Backtest (completes in seconds)
var job = await m.Backtest.StartAsync(new
{
    strategyType = "custom",
    language     = "rhai",
    definition   = "fn evaluate() { emit_long(param(\"qty\")); }",
    exchange     = "binance",
    symbol       = "BTC/USDT",
    timeframe    = "1h",
    since_ms     = DateTimeOffset.UtcNow.AddDays(-30).ToUnixTimeMilliseconds(),
    until_ms     = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
    @params      = new { qty = 0.001 },
});
Console.WriteLine($"Backtest job: {job.JobId}");

// Clean up
await m.Strategies.StopAsync(sid);
await m.Strategies.DeleteAsync(sid);
```

## Streaming

```csharp
// Public ticker stream
await foreach (var frame in m.Stream.TickerAsync("binance", "BTC/USDT", "spot"))
{
    Console.WriteLine(frame);
    break; // take one frame then stop
}

// Private strategy events (mints a short-lived WS ticket)
await foreach (var frame in m.Stream.StrategiesAsync())
{
    Console.WriteLine(frame);
    break;
}
```

## TLS note

On corporate/dev machines with TLS interception set env var `MELAYA_INSECURE_TLS=1`.
The SDK stays secure by default; only the e2e test harness enables this flag.

## Method table

### market

| Method | Description |
|--------|-------------|
| `ListExchangesAsync()` | All supported venues |
| `TickerAsync(exchange, symbol, market?)` | Best bid/ask + 24h stats |
| `OrderbookAsync(...)` | Order book to a given depth |
| `OhlcvAsync(...)` | OHLCV candles |
| `TradesAsync(...)` | Recent public trades |
| `MarketsAsync(exchange)` | Tradable markets on a venue |
| `CurrenciesAsync(exchange)` | Listed currencies |
| `StatusAsync(exchange)` | Operational status |
| `TimeAsync(exchange)` | Exchange server time |
| `TickersAsync(exchange, symbols, market?)` | Batch tickers |
| `FundingRatesAsync(exchange, symbols, market?)` | Latest funding rates |
| `FundingRateHistoryAsync(exchange, symbol, hours?, market?)` | Funding-rate history |
| `OpenInterestAsync(exchange, symbols, market?)` | Open interest |
| `OpenInterestHistoryAsync(exchange, symbol, hours?, market?)` | Open-interest history |
| `InstrumentsAsync(exchange, market?)` | Instrument list + constraints |
| `LiquidationEventsAsync(...)` | Historical liquidation events |
| `OhlcvMultiAsync(...)` | Multi-symbol OHLCV |
| `MarketConstraintsAsync(exchange, symbol, market?)` | Trading constraints |
| `FundingRateHistoryMultiAsync(exchanges, symbol, hours?)` | Multi-venue funding history |
| `OpenInterestHistoryMultiAsync(exchanges, symbol, hours?)` | Multi-venue OI history |
| `PredictionMarketsAsync(venue?)` | Prediction market listings |
| `CatalogCountsAsync()` | Platform catalog counts |

### account

| Method | Description |
|--------|-------------|
| `KeysAsync()` | Connected exchange API keys (masked) |
| `UsageAsync()` | Tier, plan limits, live usage counters |
| `ApiKeyStatusAsync()` | Platform key status |

### sim (paper trading)

| Method | Description |
|--------|-------------|
| `ListAccountsAsync()` | Virtual wallets |
| `BalanceAsync(strategyId, asset?)` | Virtual balance |
| `PositionsAsync(strategyId)` | Open positions |
| `OpenOrdersAsync(strategyId)` | Resting orders |
| `MyTradesAsync(strategyId)` | Filled trades |
| `CreateOrderAsync(...)` | Place a paper order |
| `CancelOrderAsync(...)` | Cancel a resting order |

### strategies

| Method | Description |
|--------|-------------|
| `ListAsync()` | All your strategies |
| `GetAsync(strategyId)` | Single strategy |
| `CreateAsync(body)` | Launch a strategy |
| `PauseAsync(strategyId)` | Pause |
| `ResumeAsync(strategyId)` | Resume |
| `StopAsync(strategyId)` | Stop + tear down |
| `DeleteAsync(strategyId)` | Soft-delete |
| `UpdateParamsAsync(strategyId, params)` | Update params |
| `StatusAsync(strategyId)` | Runtime status |
| `PerformanceAsync(strategyId)` | Performance series |
| `ExecutionsAsync(strategyId)` | Execution rows |
| `TradesAsync(strategyId)` | Fill rows |
| `LogsAsync(strategyId)` | Log rows |
| `AiOptStartAsync(...)` | Start AI optimizer |
| `AiOptStatusAsync(strategyId)` | Optimizer status |
| `AiOptApproveAsync(strategyId)` | Apply optimizer params |
| `AiOptStopAsync(strategyId)` | Stop optimizer |
| `AiOptRunsAsync(strategyId)` | Past optimizer runs |

### backtest

| Method | Description |
|--------|-------------|
| `StartAsync(body)` | Start a backtest |
| `JobAsync(jobId)` | Job status + progress |
| `ResultsAsync(jobId)` | Metrics + equity curve |
| `TradesAsync(jobId, limit?, offset?)` | Trade list |
| `SweepAsync(parentId, objective?, limit?)` | Sweep rankings |
| `ListAsync(limit?, offset?)` | Your jobs |
| `FavoritesAsync(limit?, offset?)` | Favorited jobs |
| `FundingRangeAsync(exchange, symbol)` | Earliest funding-rate ms |
| `CancelAsync(jobId)` | Cancel in-flight job |
| `DeleteAsync(jobId)` | Delete a job |
| `DeleteAllAsync()` | Delete all non-favorited |

### stream

| Method | Description |
|--------|-------------|
| `TickerAsync(exchange, symbol, market?)` | Live ticker frames |
| `OrderbookAsync(exchange, symbol, ...)` | Live order-book frames |
| `OhlcvAsync(exchange, symbol, timeframe, ...)` | Live OHLCV frames |
| `TradesAsync(exchange, symbol, ...)` | Live public-trade frames |
| `LiquidationsAsync(exchange?)` | Liquidation firehose |
| `StrategiesAsync()` | Private strategy events |
| `PrivateAsync(exchange, ...)` | Private account feed |
