# Melaya Java SDK

> **Current production scope:** Agent Builder and Mobile Device Control are available now. Melaya Trading namespaces are preview-only and not generally available; do not use them with real funds.

Official SDK for the **[Melaya](https://melaya.org)** Agent Builder and flagship Mobile Device Control APIs. Trading namespaces are included only as a preview of a later product.

**Melaya products:** [Melaya Agents](https://melaya.org/en/product/agentic-framework) · [Melaya Assistant](https://melaya.org/en/product/assistant) · [Device Control](https://melaya.org/en/product/agentic-device-control) · [Browser Control](https://melaya.org/en/product/agentic-browser-control) · [MCP Server](https://melaya.org/en/product/mcp) · [Melaya Marketing](https://melaya.org/en/product/marketing)

## Installation

### Gradle

```groovy
dependencies {
    implementation 'org.melaya:melaya-sdk:0.3.0'
}
```

### Maven

```xml
<dependency>
    <groupId>org.melaya</groupId>
    <artifactId>melaya-sdk</artifactId>
    <version>0.3.0</version>
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

The run is generated ONLY from `steps[]` — a top-level `agents[]` list alone produces an EMPTY pipeline. Embed the full agent definition inside each step (there is no `prompt` field; the task goes in `instruction`, and `system_prompt_override` overrides the system prompt):

```java
melaya.agents().pipelines().create(Map.of(
    "name",    "mobile-review",
    "project", "Operations",
    "steps", List.of(Map.of(
        "kind",  "agent",
        "agent", Map.of(
            "name",        "mobile-operator",
            "role",        "Careful mobile operator",
            "instruction", "Read before acting. Never send, publish, or delete.",
            "model",       Map.of("provider", "anthropic", "name", "claude-sonnet-4-6"),
            "agent_tools", List.of(
                "phone_get_screen_tree", "phone_current_app", "phone_open_app",
                "phone_click_text", "phone_back", "phone_wait"),
            "human_approval_tools", List.of()))),
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

To edit a pipeline, `get()` it, mutate `envelope.get("config")`, and pass that (not the envelope) to `update()`:

```java
JsonNode envelope = melaya.agents().pipelines().get("mobile-review", "Operations"); // { name, client, config, code, docs }
JsonNode config = envelope.get("config");
((ObjectNode) config.at("/steps/0/agent/model")).put("name", "claude-opus-4-8");
melaya.agents().pipelines().update("mobile-review", Map.of("config", config, "project", "Operations"));
```

Other notable config fields: `hitl_mode` (`"safe"` default | `"autonomous"` | `"payments_only"` — only `"safe"` honours `human_approval_tools`), `connector_source` (`"personal"` | `"project"`), `force_local_runner`, `inputs[]`. Note `executionTarget` on `run()` is used only for the tier check — where the run actually executes is decided by the pipeline's stored config, not this field.

## Quick Start: Connector Tool Calls

`melaya.agents().connectorTools()` (flat alias: `melaya.connectorTools()`) calls the same tool
surface the MCP server and the in-app Assistant expose — list connected services, discover tools
by keyword, describe one, test a stored credential, start connecting a service, and call a tool —
over plain REST. It is distinct from `melaya.platform().connectors()`, which only stores project
connector credentials; no method here ever accepts or returns a credential value.

Read tools run immediately. Write tools default to `approval: "required"`: the call returns `202`
with a `requestId`, staged as the same approval card the Assistant raises in the Melaya app, and
the write runs once the user approves it there. Pass `approval: "none"` to run a write immediately
instead (still audit-logged). Tools that move money or trade are refused under both approval modes.

```java
import org.melaya.Melaya;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

Melaya melaya = new Melaya(System.getenv("MELAYA_API_KEY"));

JsonNode services = melaya.agents().connectorTools().services();
JsonNode hits     = melaya.agents().connectorTools().search("unread email", null);

// Reads run immediately.
JsonNode read = melaya.agents().connectorTools().call("gmail_list_messages", Map.of(), null);

// A write defaults to a staged approval card; block until the user decides.
JsonNode outcome = melaya.agents().connectorTools()
        .callAndWait("gmail_send", Map.of("to", "a@b.c"), "required", null, null);
System.out.println(outcome.get("status").asText()); // "done" or "rejected" (or "expired"/"pending" on timeout)

// Or run a write immediately, without an approval card:
melaya.agents().connectorTools().call("gmail_send", Map.of("to", "a@b.c"), "none");
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
| `connectors` | `melaya.platform().connectors()` | `connectedServices`, `set`, `delete`, `getEnvHandle`, `googleOAuthStart`, plus project-scoped `applyPersonal`, `sharedBy`, `googleStatus`, `googleSetDefault`, `googleDisconnect`, `dbTestStart`, `dbTestStatus` |
| `credentials` | `melaya.platform().credentials()` | `list`, `connectedServices`, `get`, `set`, `delete`, `test`, `getOperatorProfile`, `setOperatorProfile`, `listModels`, `melayaAccounts`, plus RAG (`ragIngestStart/Status`, `ragRetrieveStart/Status`, `pickFolderStart/Status`), OAuth connect helpers (Google, LinkedIn, Luma, NotebookLM, Telegram, CLI), and personal-scope `googleStatus`, `googleSetDefault`, `googleDisconnect`, `dbTestStart`, `dbTestStatus`, `telegramQrStart`, `telegramQrPoll`, `whatsappSignupConfig`, `whatsappSignupExchange`, `tiktokCreatorInfo`, `substackEmailLinkSend`, `substackEmailLinkRedeem` |
| `pipelines` | `melaya.agents().pipelines()` | `listPipelines`, `create`, `get`, `update`, `delete`, `run`, `uploadRunFile`, `runIds`, `runInputs`, `runInputFile`, `runStatus`, `runActive`, `cancelRun`, `listDocs`, `uploadDoc`, `deleteDoc`, `uploadRetrievalDoc`, `ingestRetrieval`, `deleteRetrievalDoc`, `outputs`, `output`, `previewCode`, `tools`, `subagents`, `instantiateTemplate`, `buildWithAI`, `traces`, `trace`, `traceStats`, `deleteTraces`, `projectToolCalls`, `projectToolCallFacets`, `toolCallDetail`, `listSchedules`, `getSchedule`, `upsertSchedule`, `pauseSchedule`, `resumeSchedule` |
| `templates` | `melaya.platform().templates()` | `list`, `save`, `update`, `duplicate`, `delete`, `share`, `listAssignments`, `assign(templateId, target)` — `target` holds exactly one of `userId` / `projectId` (JSON body), `unassign(templateId, target)` — `userId` / `projectId` sent as query params, `shareTargets`, `listGlobal`, `listValidated` |
| `phone` | `melaya.agents().phone()` | `pair`, `listDevices`, `revokeDevice`, `screenTree`, `listApps`, `setAllowedApps`, `registerActiveRun`, `grantApp`, `requestCast` |
| `hitl` | `melaya.agents().hitl()` | `pending`, `history`, `approve`, `reject`, `bulkDecide`, `runToolStats`, `runToolStatsByAgent`, `runToolCalls`, `runMessages` |
| `evals` | `melaya.agents().evals()` | `listRuns`, `summary`, `runDetail`, `compare`, `memoryGraph`, `runMemory`, `crewMemory`, `editCrewMemoryEntry`, `deleteCrewMemoryEntry`, `benchmarks` |
| `events` | `melaya.platform().events()` | `onRunUpdate`, `onInitPhase`, `onProjectEvent`, `onHitlApproval`, `onPipelineCreated`, `onPipelineUpdated`, `onPipelineDeleted`, `leaveRun`, `leaveProject`, `close` — Socket.IO; connects lazily on first subscription |
| `billing` | `melaya.platform().billing()` | `subscription`, `createCheckout`, `createPortal`, `plans`, `ambassadorPerk`, `redeemCode`, `reservedPromo` |
| `accounts` | `melaya.platform().accounts()` | `exportMyData`, `removeKey`, `updateProfile`, `credits`, `aiCredits`, `portfolioIdeasCredits`, `riskMonitoringCredits`, `resendEmailVerification`, `verifyEmail` |
| `runner` | `melaya.platform().runner()` | `createToken`, `listTokens`, `revokeToken` |
| `team` | `melaya.platform().team()` | `listMembers`, `invite`, `createInviteLink`, `acceptInvite`, `updateMemberRole`, `removeMember`, `transferOwnership`, `getPipelineVisibility`, `setPipelineVisibility` |
| `mfa` | `melaya.platform().mfa()` | `status`, `setup`, `confirmSetup` |
| `assistant` | `melaya.agents().assistant()` | `getProfile`, `setProfile` |
| `bugs` | `melaya.platform().bugs()` | `create`, `listMine`, `get`, `addComment`, `listNotifications`, `markNotificationsRead` |
| `overview` | `melaya.platform().overview()` | `get`, `usageSummary`, `modelPrices`, `chartData`, `costBreakdown`, `pipelineCount`, `pipelineList`, `recentPipelines` |
| `connectorTools` | `melaya.agents().connectorTools()` (flat alias: `melaya.connectorTools()`) | `services`, `search(q, limit)`, `describe(tool)`, `test(service)`, `connect(service)`, `call(tool, args, approval)`, `callStatus(requestId)`, `callAndWait(tool, args, approval, pollIntervalMs, timeoutMs)` |

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
