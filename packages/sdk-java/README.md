# Melaya Java SDK

Official Java SDK for the [Melaya](https://melaya.org) unified trading API — market data, paper trading, strategies, backtesting, and real-time WebSocket streaming across 70+ venues.

## Installation

### Gradle

```groovy
dependencies {
    implementation 'org.melaya:melaya-sdk:1.0.0'
}
```

### Maven

```xml
<dependency>
    <groupId>org.melaya</groupId>
    <artifactId>melaya-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Authentication

Create an API key at [melaya.org → Settings → API Keys](https://melaya.org). Keys are prefixed `mk_`.

The SDK sends the key as both a query parameter (`?apiKey=mk_...`) and an `Authorization: Bearer mk_...` header on every request.

**Never hardcode your API key.** Read it from an environment variable:

```java
String apiKey = System.getenv("MELAYA_API_KEY");
Melaya melaya = new Melaya(apiKey);
```

## Quick Start

```java
import org.melaya.Melaya;
import com.fasterxml.jackson.databind.JsonNode;

Melaya melaya = new Melaya(System.getenv("MELAYA_API_KEY"));

// Market data
JsonNode ticker = melaya.market().ticker("binance", "BTC/USDT", "spot");
System.out.println("BTC last: " + ticker.get("last"));

JsonNode exchanges = melaya.market().listExchanges();
```

### Paper strategy + backtest

```java
import org.melaya.Melaya;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

Melaya melaya = new Melaya(System.getenv("MELAYA_API_KEY"));

// Launch a custom Rhai paper strategy
Map<String, Object> body = new java.util.LinkedHashMap<>();
body.put("name", "my-custom-strategy");
body.put("strategyType", "custom");
body.put("exchange", "binance");
body.put("symbol", "BTC/USDT");
body.put("market", "spot");
body.put("dryRun", true);     // paper only — never live
body.put("params", Map.of(
    "language",   "rhai",
    "definition", "fn evaluate() { emit_long(param(\"qty\")); }",
    "qty",        0.001
));
JsonNode created = melaya.strategies().create(body);
String strategyId = created.get("strategyId").asText();

// Run a backtest
long now = System.currentTimeMillis();
JsonNode bt = melaya.backtest().start(Map.of(
    "strategyType", "custom",
    "exchange",     "binance",
    "symbol",       "BTC/USDT",
    "timeframe",    "1h",
    "since_ms",     now - 30L * 24 * 60 * 60 * 1000,
    "until_ms",     now,
    "language",     "rhai",
    "definition",   "fn evaluate() { emit_long(param(\"qty\")); }",
    "params",       Map.of("qty", 0.001)
));
String jobId = bt.get("job_id").asText();

// Always clean up paper strategies
melaya.strategies().stop(strategyId);
melaya.strategies().delete(strategyId);
```

### WebSocket streaming

```java
import org.melaya.MelayaStream;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.TimeUnit;

try (MelayaStream stream = melaya.stream().ticker("binance", "BTC/USDT", "spot")) {
    JsonNode frame = stream.nextFrame(10, TimeUnit.SECONDS);
    System.out.println(frame);
}

// Private strategy events
try (MelayaStream s = melaya.stream().strategies()) {
    JsonNode ev = s.nextFrame(10, TimeUnit.SECONDS);
    System.out.println(ev);
}
```

## TLS

The SDK verifies TLS certificates by default. To disable verification in dev/proxy environments only, set:

```
MELAYA_INSECURE_TLS=1
```

## Method Reference

### `market`

| Method | Description |
|---|---|
| `listExchanges()` | List all supported exchanges |
| `ticker(exchange, symbol, market)` | Best bid/ask, last price, 24 h stats |
| `orderbook(exchange, symbol, market, limit)` | Order book to given depth |
| `ohlcv(exchange, symbol, timeframe, market, limit)` | OHLCV candles |
| `trades(exchange, symbol, market)` | Recent public trades |
| `markets(exchange)` | Tradable markets on a venue |
| `currencies(exchange)` | Listed currencies |
| `status(exchange)` | Operational status |
| `time(exchange)` | Exchange server time |
| `tickers(body)` | Tickers for many symbols (POST) |
| `fundingRates(body)` | Latest funding rates (POST) |
| `fundingRateHistory(body)` | Funding-rate history (POST) |
| `openInterest(body)` | Open interest (POST) |
| `openInterestHistory(body)` | Open-interest history (POST) |
| `instruments(body)` | Instruments + constraints (POST) |
| `liquidationEvents(body)` | Historical liquidation events (POST) |
| `ohlcvMulti(body)` | Multi-symbol OHLCV (POST) |
| `marketConstraints(body)` | Trading constraints (POST) |
| `fundingRateHistoryMulti(body)` | Funding history, multi-venue (POST) |
| `openInterestHistoryMulti(body)` | OI history, multi-venue (POST) |
| `predictionMarkets(body)` | Prediction-market listings (POST) |
| `catalogCounts()` | Platform catalog counts |

### `account`

| Method | Description |
|---|---|
| `keys()` | Connected exchange API keys (masked) |
| `usage()` | Tier limits and usage counters |
| `apiKeyStatus()` | Platform API key status |

### `sim`

| Method | Description |
|---|---|
| `listAccounts()` | Virtual wallets per paper strategy |
| `balance(strategyId, asset)` | Virtual balance |
| `positions(strategyId)` | Open positions |
| `openOrders(strategyId)` | Resting orders |
| `myTrades(strategyId)` | Filled trades |
| `createOrder(...)` | Place a paper order |
| `cancelOrder(strategyId, orderId, symbol, exchange)` | Cancel a resting order |

### `strategies`

| Method | Description |
|---|---|
| `list()` | All strategies you own |
| `get(strategyId)` | Single strategy |
| `create(body)` | Launch a strategy (`dryRun: true` for paper) |
| `pause(strategyId)` | Pause a running strategy |
| `resume(strategyId)` | Resume a paused strategy |
| `stop(strategyId)` | Stop and tear down |
| `delete(strategyId)` | Soft-delete |
| `updateParams(strategyId, params)` | Update runtime params |
| `status(strategyId)` | Runtime status |
| `performance(strategyId)` | Performance series |
| `executions(strategyId)` | Order rows |
| `trades(strategyId)` | Fill rows |
| `logs(strategyId)` | Log rows |
| `aiOptStart(strategyId, body)` | Start AI optimizer |
| `aiOptStatus(strategyId)` | Optimizer status |
| `aiOptApprove(strategyId, body)` | Apply optimizer result |
| `aiOptStop(strategyId)` | Stop optimization |
| `aiOptRuns(strategyId)` | Past optimization runs |

### `backtest`

| Method | Description |
|---|---|
| `start(body)` | Start a backtest job |
| `job(jobId)` | Job status + progress |
| `results(jobId)` | Metrics, equity curve |
| `trades(jobId, limit, offset)` | Trade list |
| `sweep(parentId, objective, limit)` | Sweep children ranked |
| `list(limit, offset)` | Your jobs, newest first |
| `favorites(limit, offset)` | Favorited jobs |
| `fundingRange(exchange, symbol)` | Earliest funding-rate data |
| `cancel(jobId)` | Cancel in-flight job |
| `delete(jobId)` | Soft-delete a job |
| `deleteAll()` | Delete all non-favorited jobs |

### `stream`

| Method | Description |
|---|---|
| `ticker(exchange, symbol, market)` | Live ticker frames |
| `orderbook(exchange, symbol, market, limit)` | Live order-book frames |
| `ohlcv(exchange, symbol, timeframe, market)` | Live OHLCV candle frames |
| `trades(exchange, symbol, market)` | Live public-trade frames |
| `liquidations(exchange)` | Liquidation firehose |
| `strategies()` | Private strategy events (ticket-minted) |
| `privateStream(exchange, market, apiKeyId, keyId, symbol)` | Private account feed |

## Running the E2E Smoke Test

```
cd packages/sdk-java
MK=mk_yourkey MELAYA_INSECURE_TLS=1 ./gradlew run
```

The smoke test exercises every category and prints `PASS`/`FAIL` per check with a final tally.
