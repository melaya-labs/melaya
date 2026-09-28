# melaya-sdk-kotlin

> **Current production scope:** Agent Builder and Mobile Device Control are available now. Melaya Trading namespaces are preview-only and not generally available; do not use them with real funds.

Official SDK for the **[Melaya](https://melaya.org)** Agent Builder and flagship Mobile Device Control APIs. Trading namespaces are included only as a preview of a later product.

**Melaya products:** [Melaya Agents](https://melaya.org/en/product/agentic-framework) · [Melaya Assistant](https://melaya.org/en/product/assistant) · [Device Control](https://melaya.org/en/product/agentic-device-control) · [Browser Control](https://melaya.org/en/product/agentic-browser-control) · [MCP Server](https://melaya.org/en/product/mcp) · [Melaya Marketing](https://melaya.org/en/product/marketing)

## Install

The SDK is published to Maven Central as a standard Kotlin/JVM library (JVM 21+):

```kotlin
// build.gradle.kts
repositories {
    mavenCentral()
}
dependencies {
    implementation("org.melaya:melaya-sdk-kotlin:0.3.0")
}
```

Or with Maven:

```xml
<dependency>
  <groupId>org.melaya</groupId>
  <artifactId>melaya-sdk-kotlin</artifactId>
  <version>0.3.0</version>
</dependency>
```

To build from source instead, publish to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

## Agent Builder & Device Control

Agent Builder and Mobile Device Control are the generally available surface. Current catalogs expose **8,000+ scoped tools**, **100+ specialized subagents**, and **48 AI providers** — runtime catalog endpoints remain the source of truth.

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

A pipeline's run is generated ONLY from `steps[]` — a top-level `agents[]` array alone
produces an EMPTY pipeline. Each step embeds its own full agent definition; there is no
`prompt` field (the task goes in `instruction`, and `system_prompt_override` replaces the
whole system prompt).

```kotlin
melaya.agents.pipelines.create(
    name    = "mobile-review",
    project = "Operations",
    config  = mapOf(
        "steps" to listOf(mapOf(
            "kind" to "agent",
            "agent" to mapOf(
                "name"        to "mobile-operator",
                "role"        to "Careful mobile operator",
                "instruction" to "Read before acting. Never send, publish, or delete.",
                "model"       to mapOf("provider" to "anthropic", "name" to "claude-sonnet-4-6"),
                "agent_tools" to listOf(
                    "phone_get_screen_tree",
                    "phone_current_app",
                    "phone_open_app",
                    "phone_click_text",
                    "phone_back",
                    "phone_wait",
                ),
                "human_approval_tools" to emptyList<String>(),
            ),
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

### Edit a pipeline (`get` → mutate `config` → `update`)

`get()` returns the full envelope `{ name, client, config, code, docs }`. Edit the inner
`config` object and pass THAT to `update()` — not the envelope itself.

```kotlin
val envelope = melaya.agents.pipelines.get("mobile-review", project = "Operations")
val config   = envelope.getJSONObject("config")
config.getJSONArray("steps").getJSONObject(0).getJSONObject("agent")
    .put("model", mapOf("provider" to "anthropic", "name" to "claude-opus-4-8"))
melaya.agents.pipelines.update("mobile-review", config.toMap(), project = "Operations")
```

### Run inputs, uploads, and documents

```kotlin
// Upload a file for a later run, then reference it via run_inputs.
val upload = melaya.agents.pipelines.uploadRunFile(
    name = "mobile-review", key = "screenshot", file = pngBytes, filename = "screen.png",
)
val run = melaya.agents.pipelines.run(
    name = "mobile-review", project = "Operations",
    runInputs = mapOf(
        "brief"  to "Review the attached screenshot.",
        "values" to mapOf("screenshot" to mapOf("file_id" to upload.getString("file_id"))),
    ),
)

// Inspect what a run was started with, and download an attached input file's raw bytes.
val inputs   = melaya.agents.pipelines.runInputs("mobile-review", run.getString("run_id"))
val fileBytes: ByteArray = melaya.agents.pipelines.runInputFile("mobile-review", run.getString("run_id"), 0)

// Static-context documents (RAG-lite context injected verbatim).
melaya.agents.pipelines.uploadDoc("mobile-review", docBytes, filename = "policy.pdf")
melaya.agents.pipelines.listDocs("mobile-review")
melaya.agents.pipelines.deleteDoc("mobile-review", "policy.pdf")

// Retrieval (RAG) documents — upload, then ingest (can take minutes; uses a 300s timeout).
melaya.agents.pipelines.uploadRetrievalDoc("mobile-review", docBytes, filename = "manual.pdf")
melaya.agents.pipelines.ingestRetrieval("mobile-review")
melaya.agents.pipelines.deleteRetrievalDoc("mobile-review", "manual.pdf")
```

### Call a connector tool directly

`melaya.connectorTools` (also `melaya.agents.connectorTools`) calls any unlocked connector
tool — Gmail, Slack, Stripe, and the rest — the same surface the Melaya Assistant and MCP
server use. Reads run immediately. A write defaults to an approval card in the Melaya app
(`approval = "required"`); pass `approval = "none"` to run it immediately instead (still
audit-logged). Tools that move money or trade are refused under both modes. No method here
ever accepts or returns a credential value — for storing credentials, use `connectors` or
`credentials` instead.

```kotlin
val hits = melaya.connectorTools.search("unread email").getJSONArray("tools")
val tool = hits.getJSONObject(0).getString("name")
val read = melaya.connectorTools.call(tool)
println(read.getString("result"))

// A write is staged as an approval card by default; block until the user decides.
val outcome = melaya.connectorTools.callAndWait(
    "gmail_send",
    args = mapOf("to" to "a@b.com", "subject" to "Hi", "body" to "…"),
)
println(outcome.optString("status"))   // "done" or "rejected"

// Or run it immediately (still audit-logged), skipping the approval card:
melaya.connectorTools.call("gmail_send", args = mapOf("to" to "a@b.com"), approval = "none")
```

### Diagnose event triggers

`melaya.triggers` (also `melaya.agents.triggers`) reads and diagnoses the triggers that start
your pipelines. Use it to find out why an event did or did not start a run. It is read and
diagnose only: create, edit and delete a trigger in the Agent Builder or through MCP. No method
returns a signing secret.

```kotlin
val id = melaya.triggers.list(project = "my-project")[0].getString("id")

// Verdict counts for the last 24 hours, and the live log (includes events with no receipt).
val stats  = melaya.triggers.stats(id, hours = 24)
val events = melaya.triggers.events(triggerId = id, verdicts = listOf("filtered", "failed"))

// Dry run: prefilter, decide and routing run, the action never executes.
val test = melaya.triggers.test(id, payload = mapOf("subject" to "Refund request"))

// Poll triggers: state, and what the next poll would find (publishes nothing).
val state = melaya.triggers.pollStatus(id)
val dry   = melaya.triggers.pollTest(id)   // { ok: false, error } is returned, not thrown
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
| Connectors | `connectors.set`, `delete`, `connectedServices`, `envHandle`, `googleOAuthStart`, `applyPersonal`, `sharedBy`, `googleStatus`, `googleSetDefault`, `googleDisconnect`, `dbTestStart`, `dbTestStatus` |
| Connector Tools | `connectorTools.services`, `search`, `describe`, `test`, `connect`, `call`, `callStatus`, `callAndWait` |
| Triggers | `triggers.list`, `get`, `deliveries`, `stats`, `pendingApprovals`, `test`, `events`, `pollStatus`, `pollTest`, `pollNow`, `pollSync`, `presets`, `limits`, `sources` (read and diagnose only; create, edit and delete stay in the Agent Builder and MCP) |
| Credentials | `credentials.list`, `set`, `get`, `delete`, `test`, `connectedServices`, `listModels`, `googleStatus`, `googleSetDefault`, `googleDisconnect`, `dbTestStart`, `dbTestStatus`, `telegramQrStart`, `telegramQrPoll`, `whatsappSignupConfig`, `whatsappSignupExchange`, `tiktokCreatorInfo`, `substackEmailLinkSend`, `substackEmailLinkRedeem`, plus OAuth / RAG / operator-profile helpers |
| Pipelines | `pipelines.create`, `listPipelines`, `get`, `update`, `delete`, `run`, `runIds`, `runStatus`, `cancelRun`, `runActive`, `uploadRunFile`, `runInputs`, `runInputFile`, `listDocs`, `uploadDoc`, `deleteDoc`, `uploadRetrievalDoc`, `ingestRetrieval`, `deleteRetrievalDoc`, `outputs`, `output`, `previewCode`, `tools`, `subagents`, `instantiateTemplate`, `buildWithAI`, `overview`, `list`, `recent`, `count`, `chartData`, `costBreakdown`, `modelPrices`, `traces`, `trace`, `traceStats`, `deleteTraces`, `listSchedules`, `getSchedule`, `upsertSchedule`, `pauseSchedule`, `resumeSchedule`, `serverVersion` |
| Templates | `templates.list`, `listGlobal`, `listValidated`, `save`, `update`, `duplicate`, `delete`, `share`, `listAssignments`, `assign(templateId, userId = … / projectId = …)`, `unassign(templateId, userId = … / projectId = …)`, `shareTargets` |
| Phone (Device Control) | `phone.pair`, `listDevices`, `revokeDevice`, `screenTree`, `listApps`, `setAllowedApps`, `registerActiveRun`, `grantApp`, `requestCast` |
| HITL | `hitl.pending`, `history`, `approve`, `reject`, `bulkDecide`, `runToolCalls`, `runToolStats`, `runToolStatsByAgent`, `runMessages`, `projectToolCalls`, `projectToolCallFacets`, `toolCallDetail` |
| Evals | `evals.summary`, `listRuns`, `runDetail`, `compare`, `benchmarks`, `runMemory`, `crewMemory`, `memoryGraph` |
| Memory | `memory.editEntry`, `deleteEntry` |
| Events | `events.onRunUpdate`, `onInitPhase`, `onProjectEvent`, `onHitlApproval`, `onPipelineCreated`, `onPipelineUpdated`, `onPipelineDeleted`, `leaveRun`, `leaveProject`, `close` |
| Billing | `billing.subscription`, `plans`, `createCheckout`, `createPortal`, `ambassadorPerk`, `redeemCode`, `reservedPromo` |
| Accounts | `accounts.exportMyData`, `updateProfile`, `removeKey`, `credits`, `aiCredits`, `portfolioIdeasCredits`, `riskMonitoringCredits`, `resendEmailVerification`, `verifyEmail` |
| Runner | `runner.createToken`, `listTokens`, `revokeToken` |
| Team | `team.listMembers`, `invite`, `createInviteLink`, `acceptInvite`, `updateMemberRole`, `removeMember`, `getPipelineVisibility`, `setPipelineVisibility`, `transferOwnership` |
| Assistant | `assistant.getProfile`, `setProfile` |
| Bugs | `bugs.create`, `get`, `listMine`, `addComment`, `listNotifications`, `markNotificationsRead` |

All modules are also grouped by plane: `melaya.agents.*` (pipelines, hitl, assistant, phone, evals, memory, connectorTools, triggers), `melaya.platform.*` (projects, credentials, connectors, billing, team, templates, runner, auth, mfa, accounts, bugs, events), and `melaya.trading.*`.

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

Full docs: **[melaya.org/documentation](https://melaya.org/documentation)**.

## License

[Apache-2.0](../../LICENSE)
