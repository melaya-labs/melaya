# melaya-sdk-kotlin

Official Kotlin SDK for the **[Melaya](https://melaya.org)** trading platform — normalized market data, paper + live trading, backtesting, and an AI agentic trading crew across **70+ venues**, powered by an in-house Rust engine.

## Install

The SDK is a standard Kotlin/JVM library (JVM 21+).  Add it as a local dependency or publish it to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

Then in your project:

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.melaya:melaya-sdk-kotlin:0.1.0")
}
```

## Quick start

```kotlin
import org.melaya.Melaya

val melaya = Melaya(apiKey = System.getenv("MK"))   // keys are prefixed `mk_`

// Normalized ticker from any of 70+ venues
val ticker = melaya.market.ticker(exchange = "binance", symbol = "BTC/USDT", market = "spot")
println(ticker.optDouble("last"))

// Order book + candles
val book    = melaya.market.orderbook(exchange = "bybit",  symbol = "BTC/USDT", market = "spot", limit = 20)
val candles = melaya.market.ohlcv(    exchange = "okx",   symbol = "ETH/USDT", timeframe = "1h", limit = 200)
```

## Streaming

```kotlin
import org.melaya.Melaya

val melaya = Melaya(apiKey = System.getenv("MK"))

melaya.stream.ticker(exchange = "binance", symbol = "BTC/USDT", market = "spot").use { s ->
    s.awaitOpen()
    val frame = s.poll(10_000) ?: error("no frame")
    println(frame)
}
```

## Trading

```kotlin
import org.melaya.Melaya

val melaya = Melaya(apiKey = System.getenv("MK"))

// Account
val keys  = melaya.account.keys()    // [{"apiKeyId": "BINANCEUSDM_0", ...}]
val usage = melaya.account.usage()

// Strategies — SDK-launchable strategies are `custom` Rhai definitions.
// dryRun = true  → paper (no exchange key required)
// dryRun = false → live  (requires apiKeyId from account.keys())
val res = melaya.strategies.create(
    name         = "My first bot",
    strategyType = "custom",
    exchange     = "binance",
    symbol       = "BTC/USDT",
    market       = "spot",
    dryRun       = true,
    params       = mapOf(
        "language"   to "rhai",
        "definition" to """fn evaluate() { emit_long(param("qty")); }""",
        "qty"        to 0.001,
    ),
)
val sid = res.getString("strategyId")
melaya.strategies.pause(sid)
melaya.strategies.resume(sid)

// Paper trading (sim broker) — synthetic fills, no venue state
val balance = melaya.sim.balance(strategyId = sid)
val fill    = melaya.sim.createOrder(
    strategyId = sid, exchange = "binance", symbol = "BTC/USDT",
    side = "buy", amount = 0.001, type = "market", market = "spot",
)

// Backtest on the Rust engine
val nowMs   = System.currentTimeMillis()
val sinceMs = nowMs - 7L * 24 * 60 * 60 * 1000  // 7 days
val start   = melaya.backtest.start(
    strategyType = "custom",
    exchange     = "binance",
    symbol       = "BTC/USDT",
    timeframe    = "1h",
    sinceMs      = sinceMs,
    untilMs      = nowMs,
    language     = "rhai",
    definition   = """fn evaluate() { emit_long(param("qty")); }""",
    params       = mapOf("qty" to 0.001),
)
val jobId = start.getString("job_id")
var status = ""
while (status != "done" && status != "error") {
    Thread.sleep(2_000)
    status = melaya.backtest.job(jobId).getString("status")
}
val result = melaya.backtest.results(jobId)  // metrics, equity_curve, ohlcv

// Always clean up paper strategies
melaya.strategies.stop(sid)
melaya.strategies.delete(sid)

// Private strategy event stream (ticket minted automatically)
melaya.stream.strategies().use { s ->
    s.awaitOpen()
    println(s.poll(10_000))
}
```

## Authentication

Create an API key in the dashboard (**melaya.org → Settings → API Keys**).  Keys are prefixed `mk_`; the SDK sends it on every REST call and WebSocket connection as both a query param and an `Authorization: Bearer` header.

Public market-data and account/strategy reads work with the key alone.  **Live** order placement and live strategy launches additionally require a connected exchange key — connect one in **Settings → Connectors**, then reference it by `apiKeyId`.  Paper trading and backtesting never touch a venue and need no exchange credentials.

### TLS note (dev boxes)

If your machine intercepts TLS (corporate proxy / Charles / mitmproxy), set:

```bash
MELAYA_INSECURE_TLS=1
```

The SDK is secure by default; this flag disables certificate verification for the OkHttp client used for both REST and WebSocket calls.

## API surface

| Area | Methods |
|---|---|
| Reference | `market.listExchanges()`, `catalogCounts()` |
| Market data | `market.ticker`, `orderbook`, `ohlcv`, `ohlcvMulti`, `trades`, `markets`, `currencies`, `marketConstraints`, `status`, `time` |
| Batch / derivatives | `market.tickers`, `fundingRates`, `fundingRateHistory`, `fundingRateHistoryMulti`, `openInterest`, `openInterestHistory`, `openInterestHistoryMulti`, `instruments`, `liquidationEvents` |
| Prediction markets | `market.predictionMarkets` (polymarket, kalshi, drift_pm, sxbet, azuro, overtime) |
| Account | `account.keys`, `usage`, `apiKeyStatus` |
| Strategies | `strategies.create`, `list`, `get`, `pause`, `resume`, `stop`, `delete`, `updateParams`, `status`, `performance`, `executions`, `trades`, `logs` |
| AI optimizer | `strategies.aiOptStart`, `aiOptStatus`, `aiOptApprove`, `aiOptStop`, `aiOptRuns` |
| Paper trading | `sim.balance`, `positions`, `openOrders`, `myTrades`, `createOrder`, `cancelOrder`, `listAccounts` |
| Backtesting | `backtest.start`, `job`, `results`, `trades`, `sweep`, `list`, `favorites`, `fundingRange`, `cancel`, `delete`, `deleteAll` |
| Public streaming | `stream.ticker`, `orderbook`, `ohlcv`, `trades`, `liquidations` |
| Private streaming | `stream.strategies`, `stream.private` |

Full docs: **[melaya.org/docs](https://melaya.org/docs)**.

## License

[Apache-2.0](../../LICENSE)
