# melaya-sdk-kotlin

> **Current production scope:** Agent Builder and Mobile Device Control are available now. Melaya Trading namespaces are preview-only and not generally available; do not use them with real funds.

Official SDK for the **[Melaya](https://melaya.org)** Agent Builder and flagship Mobile Device Control APIs. Trading namespaces are included only as a preview of a later product.

## Install

The SDK is published to Maven Central as a standard Kotlin/JVM library (JVM 21+):

```kotlin
// build.gradle.kts
repositories {
    mavenCentral()
}
dependencies {
    implementation("org.melaya:melaya-sdk-kotlin:0.2.0")
}
```

Or with Maven:

```xml
<dependency>
  <groupId>org.melaya</groupId>
  <artifactId>melaya-sdk-kotlin</artifactId>
  <version>0.2.0</version>
</dependency>
```

To build from source instead, publish to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

## Agent Builder & Device Control

Agent Builder and Mobile Device Control are the generally available surface. Current catalogs expose **1,500+ scoped tools**, **100+ specialized subagents**, and **20+ model providers** — runtime catalog endpoints remain the source of truth.

### Pair a phone

```kotlin
import org.melaya.Melaya

val melaya = Melaya(apiKey = System.getenv("MELAYA_API_KEY"))   // keys are prefixed `mk_`

val pair = melaya.agents.phone.pair()
println("Enter code on the Melaya APK: ${pair.getString("code")} (expires in ${pair.optInt("expiresInSeconds")}s)")

val devices = melaya.agents.phone.listDevices()
val apps    = melaya.agents.phone.listApps()

melaya.agents.phone.setAllowedApps(listOf("com.android.chrome"))
```

### Create and run an agent pipeline

Configure provider credentials through Melaya Connectors first. Never include a provider key in pipeline configuration or per-run overrides.

```kotlin
melaya.agents.pipelines.create(
    name    = "mobile-review",
    project = "Operations",
    config  = mapOf(
        "model_provider" to "anthropic",
        "model_name"     to "claude-sonnet-4-6",
        "agents" to listOf(mapOf(
            "name"        to "mobile-operator",
            "role"        to "Careful mobile operator",
            "instruction" to "Read before acting. Never send, publish, or delete.",
            "agent_tools" to listOf(
                "phone_get_screen_tree",
                "phone_current_app",
                "phone_open_app",
                "phone_click_text",
                "phone_back",
                "phone_wait",
            ),
        )),
        "steps" to listOf(mapOf(
            "kind"  to "agent",
            "agent" to mapOf("name" to "mobile-operator"),
        )),
        "maxCostUsd" to 1.00,
    ),
)

val run   = melaya.agents.pipelines.run("mobile-review", project = "Operations")
val runId = run.getString("run_id")

melaya.agents.phone.registerActiveRun(runId)

val status = melaya.agents.pipelines.runStatus("mobile-review", runId)

// Real-time run events over Socket.IO — the connection opens lazily on the
// first subscription, so REST-only usage never opens a socket.
val unsub = melaya.platform.events.onRunUpdate(runId) { e ->
    println("${e.optString("event_type")}: ${e.optString("status")}")
}
unsub()
melaya.platform.events.close()
```

## Quick start — market data (trading preview)

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

## Streaming (trading preview)

```kotlin
import org.melaya.Melaya

val melaya = Melaya(apiKey = System.getenv("MK"))

melaya.stream.ticker(exchange = "binance", symbol = "BTC/USDT", market = "spot").use { s ->
    s.awaitOpen()
    val frame = s.poll(10_000) ?: error("no frame")
    println(frame)
}
```

## Trading (preview)

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

Create an API key in the dashboard (**melaya.org → Settings → API Keys**).  Keys are prefixed `mk_`.  The SDK sends the key on every REST call **only** as an `Authorization: Bearer mk_...` header — never in the URL query string.  Public market-data WebSocket streams pass the key as an `?apiKey=` query parameter in the `wss://` URL (server protocol).  Private WebSocket streams never carry the key at all: the SDK first mints a short-lived ticket over REST and connects with `?wsTicket=`.

Public market-data and account/strategy reads work with the key alone.  **Live** order placement and live strategy launches additionally require a connected exchange key — connect one in **Settings → Connectors**, then reference it by `apiKeyId`.  Paper trading and backtesting never touch a venue and need no exchange credentials.

## API surface

### Agent Builder, Device Control & platform (GA)

| Area | Methods |
|---|---|
| Auth | `auth.me`, `login`, `register`, `refresh`, `check`, `permissions`, `changePassword`, `forgotPassword`, `resetPassword`, `verifyMfa`, `verifySignup`, `resendVerification`, `createMobileHandoff` |
| MFA | `mfa.status`, `setup`, `confirmSetup` |
| Projects | `projects.list`, `create`, `rename`, `runnerProjects` |
| Connectors | `connectors.set`, `delete`, `connectedServices`, `envHandle`, `googleOAuthStart` |
| Credentials | `credentials.list`, `set`, `get`, `delete`, `test`, `connectedServices`, `listModels`, plus OAuth / RAG / operator-profile helpers |
| Pipelines | `pipelines.create`, `listPipelines`, `get`, `update`, `delete`, `run`, `runIds`, `runStatus`, `cancelRun`, `outputs`, `output`, `previewCode`, `tools`, `subagents`, `instantiateTemplate`, `buildWithAI`, `overview`, `list`, `recent`, `count`, `chartData`, `costBreakdown`, `modelPrices`, `traces`, `trace`, `traceStats`, `deleteTraces`, `listSchedules`, `getSchedule`, `upsertSchedule`, `pauseSchedule`, `resumeSchedule`, `serverVersion` |
| Templates | `templates.list`, `listGlobal`, `listValidated`, `save`, `update`, `duplicate`, `delete`, `share`, `listAssignments`, `assign(templateId, userId = … / projectId = …)`, `unassign(templateId, userId = … / projectId = …)`, `shareTargets` |
| Phone (Device Control) | `phone.pair`, `listDevices`, `revokeDevice`, `screenTree`, `listApps`, `setAllowedApps`, `registerActiveRun` |
| HITL | `hitl.pending`, `history`, `approve`, `reject`, `bulkDecide`, `runToolCalls`, `runToolStats`, `runToolStatsByAgent`, `runMessages` |
| Evals | `evals.summary`, `listRuns`, `runDetail`, `compare`, `benchmarks`, `runMemory`, `crewMemory`, `memoryGraph` |
| Events | `events.onRunUpdate`, `onInitPhase`, `onProjectEvent`, `onHitlApproval`, `onPipelineCreated`, `onPipelineUpdated`, `onPipelineDeleted`, `leaveRun`, `leaveProject`, `close` |
| Billing | `billing.subscription`, `plans`, `createCheckout`, `createPortal` |
| Accounts | `accounts.exportMyData`, `updateProfile`, `removeKey`, `credits`, `aiCredits`, `portfolioIdeasCredits`, `riskMonitoringCredits` |
| Runner | `runner.createToken`, `listTokens`, `revokeToken` |
| Team | `team.listMembers`, `invite`, `createInviteLink`, `acceptInvite`, `updateMemberRole`, `removeMember`, `getPipelineVisibility`, `setPipelineVisibility` |
| Assistant | `assistant.getProfile`, `setProfile` |
| Bugs | `bugs.create`, `get`, `listMine`, `addComment`, `listNotifications`, `markNotificationsRead` |

All modules are also grouped by plane: `melaya.agents.*` (pipelines, hitl, assistant, phone, evals), `melaya.platform.*` (projects, credentials, connectors, billing, team, templates, runner, auth, mfa, accounts, bugs, events), and `melaya.trading.*`.

### Trading (preview)

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
