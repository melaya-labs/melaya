# Melaya Java SDK

> **Current production scope:** Agent Builder and Mobile Device Control are available now. Melaya Trading namespaces are preview-only and not generally available; do not use them with real funds.

Official SDK for the **[Melaya](https://melaya.org)** Agent Builder and flagship Mobile Device Control APIs. Trading namespaces are included only as a preview of a later product.

## Installation

### Gradle

```groovy
dependencies {
    implementation 'org.melaya:melaya-sdk:0.2.0'
}
```

### Maven

```xml
<dependency>
    <groupId>org.melaya</groupId>
    <artifactId>melaya-sdk</artifactId>
    <version>0.2.0</version>
</dependency>
```

## Authentication

Create an API key at [melaya.org → Settings → API Keys](https://melaya.org). Keys are prefixed `mk_`.

REST requests send the key only as an `Authorization: Bearer mk_...` header — never in the URL query string. Public WebSocket streams pass `?apiKey=` in the `wss://` URL (server protocol); private WebSocket streams use a short-lived `?wsTicket=` minted per connection.

**Never hardcode your API key.** Read it from an environment variable:

```java
String apiKey = System.getenv("MELAYA_API_KEY");
Melaya melaya = new Melaya(apiKey);
```

## Quick Start: Agent Builder & Device Control

Pair an Android phone, then let an authorized agent operate approved apps through the visible interface. The tool registry exposes 1,500+ scoped tools and 100+ specialized subagents across 20+ model providers (`melaya.agents().pipelines().tools()` / `.subagents()`).

```java
import org.melaya.Melaya;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

Melaya melaya = new Melaya(System.getenv("MELAYA_API_KEY"));

// Pair a phone — enter the code in the Melaya APK
JsonNode pair = melaya.agents().phone().pair();
System.out.println("pairing code: " + pair.get("code").asText());

JsonNode devices = melaya.agents().phone().listDevices();
JsonNode apps    = melaya.agents().phone().listApps();

// Give agents the smallest practical app allowlist
melaya.agents().phone().setAllowedApps(List.of("com.android.chrome"));
```

Create and run an agent pipeline. Configure provider credentials through Melaya Connectors first — never include a provider key in pipeline configuration or per-run overrides.

```java
melaya.agents().pipelines().create(Map.of(
    "name",           "mobile-review",
    "project",        "Operations",
    "model_provider", "anthropic",
    "model_name",     "claude-sonnet-4-6",
    "agents", List.of(Map.of(
        "name",        "mobile-operator",
        "role",        "Careful mobile operator",
        "instruction", "Read before acting. Never send, publish, or delete.",
        "agent_tools", List.of(
            "phone_get_screen_tree", "phone_current_app", "phone_open_app",
            "phone_click_text", "phone_back", "phone_wait"))),
    "steps", List.of(Map.of(
        "kind",  "agent",
        "agent", Map.of("name", "mobile-operator"))),
    "maxCostUsd", 1.00));

JsonNode run = melaya.agents().pipelines().run("mobile-review",
        Map.of("project", "Operations"));
String runId = run.get("run_id").asText();

melaya.agents().phone().registerActiveRun(runId);

JsonNode status = melaya.agents().pipelines().runStatus("mobile-review", runId);

// Live run events over Socket.IO — the connection opens lazily on first subscription
melaya.platform().events().onRunUpdate(runId, frame ->
        System.out.println(frame.get("event_type").asText()));
```

## Quick Start: Trading (preview — paper only)

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

## Method Reference

### GA namespaces — Agent Builder, Device Control, platform

Reachable through `melaya.agents()`, `melaya.platform()`, or the equivalent flat accessors (`melaya.pipelines()`, `melaya.phone()`, …).

| Namespace | Access | Methods |
|---|---|---|
| `auth` | `melaya.platform().auth()` | `login`, `verifyMfa`, `register`, `verifySignup`, `resendVerification`, `forgotPassword`, `resetPassword`, `changePassword`, `me`, `check`, `myPermissions`, `refresh`, `createMobileHandoff`, `version` |
| `projects` | `melaya.platform().projects()` | `list`, `create`, `rename`, `getRunnerProjects` |
| `connectors` | `melaya.platform().connectors()` | `connectedServices`, `set`, `delete`, `getEnvHandle`, `googleOAuthStart` |
| `credentials` | `melaya.platform().credentials()` | `list`, `connectedServices`, `get`, `set`, `delete`, `test`, `getOperatorProfile`, `setOperatorProfile`, `listModels`, `melayaAccounts`, plus RAG (`ragIngestStart/Status`, `ragRetrieveStart/Status`, `pickFolderStart/Status`) and OAuth connect helpers (Google, LinkedIn, Luma, NotebookLM, Telegram, CLI) |
| `pipelines` | `melaya.agents().pipelines()` | `listPipelines`, `create`, `get`, `update`, `delete`, `run`, `runIds`, `runStatus`, `cancelRun`, `outputs`, `output`, `previewCode`, `tools`, `subagents`, `instantiateTemplate`, `buildWithAI`, `traces`, `trace`, `traceStats`, `deleteTraces`, `listSchedules`, `getSchedule`, `upsertSchedule`, `pauseSchedule`, `resumeSchedule` |
| `templates` | `melaya.platform().templates()` | `list`, `save`, `update`, `duplicate`, `delete`, `share`, `listAssignments`, `assign(templateId, target)` — `target` holds exactly one of `userId` / `projectId` (JSON body), `unassign(templateId, target)` — `userId` / `projectId` sent as query params, `shareTargets`, `listGlobal`, `listValidated` |
| `phone` | `melaya.agents().phone()` | `pair`, `listDevices`, `revokeDevice`, `screenTree`, `listApps`, `setAllowedApps`, `registerActiveRun` |
| `hitl` | `melaya.agents().hitl()` | `pending`, `history`, `approve`, `reject`, `bulkDecide`, `runToolStats`, `runToolStatsByAgent`, `runToolCalls`, `runMessages` |
| `evals` | `melaya.agents().evals()` | `listRuns`, `summary`, `runDetail`, `compare`, `memoryGraph`, `runMemory`, `crewMemory`, `benchmarks` |
| `events` | `melaya.platform().events()` | `onRunUpdate`, `onInitPhase`, `onProjectEvent`, `onHitlApproval`, `onPipelineCreated`, `onPipelineUpdated`, `onPipelineDeleted`, `leaveRun`, `leaveProject`, `close` — Socket.IO; connects lazily on first subscription |
| `billing` | `melaya.platform().billing()` | `subscription`, `createCheckout`, `createPortal`, `plans` |
| `accounts` | `melaya.platform().accounts()` | `exportMyData`, `removeKey`, `updateProfile`, `credits`, `aiCredits`, `portfolioIdeasCredits`, `riskMonitoringCredits` |
| `runner` | `melaya.platform().runner()` | `createToken`, `listTokens`, `revokeToken` |
| `team` | `melaya.platform().team()` | `listMembers`, `invite`, `createInviteLink`, `acceptInvite`, `updateMemberRole`, `removeMember`, `getPipelineVisibility`, `setPipelineVisibility` |
| `mfa` | `melaya.platform().mfa()` | `status`, `setup`, `confirmSetup` |
| `assistant` | `melaya.agents().assistant()` | `getProfile`, `setProfile` |
| `bugs` | `melaya.platform().bugs()` | `create`, `listMine`, `get`, `addComment`, `listNotifications`, `markNotificationsRead` |
| `overview` | `melaya.platform().overview()` | `get`, `usageSummary`, `modelPrices`, `chartData`, `costBreakdown`, `pipelineCount`, `pipelineList`, `recentPipelines` |

### Trading namespaces (preview — not generally available)

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
MK=mk_yourkey gradle run
```

The smoke test exercises the trading-plane surface (market, account, sim, strategies, backtest, stream) and prints `PASS`/`FAIL` per check with a final tally. GA namespaces are not covered by this smoke test.
