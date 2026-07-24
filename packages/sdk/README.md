# @melaya/sdk

> **Current production scope:** Agent Builder and Mobile Device Control are available now. Melaya Trading namespaces are preview-only and not generally available; do not use them with real funds.

Official SDK for the **[Melaya](https://melaya.org)** Agent Builder and flagship Mobile Device Control APIs. Build agents from 1,500+ scoped tools, 100+ specialized subagents, and 20+ model providers; pair an Android phone and let an authorized agent operate approved apps through the visible interface. Trading namespaces are included only as a preview of a later product.

- Zero runtime dependencies (uses the platform `fetch` + `WebSocket`).
- Isomorphic: works in Node 18+ and the browser.
- Fully typed, from pipeline configs to real-time run events.
- Agent Builder, Device Control, and platform management from one client — plus preview trading namespaces.

## Install

```bash
npm install @melaya/sdk
```

## Quick start: pair a phone

```ts
import { Melaya } from "@melaya/sdk";

const melaya = new Melaya({ apiKey: process.env.MELAYA_API_KEY! }); // keys are prefixed `mk_`

const { code, expiresInSeconds } = await melaya.agents.phone.pair();
console.log({ code, expiresInSeconds });

const devices = await melaya.agents.phone.listDevices();
const apps = await melaya.agents.phone.listApps();

await melaya.agents.phone.setAllowedApps(["com.android.chrome"]);
```

A Melaya platform key is required. "No app API required" means Device Control operates the target app through its user interface; it does not mean the Melaya SDK is unauthenticated.

## Quick start: run an agent pipeline

Configure provider credentials through Melaya Connectors first. Never include a provider key in pipeline configuration or per-run overrides.

```ts
await melaya.agents.pipelines.create({
  name: "mobile-review",
  project: "Operations",
  model_provider: "anthropic",
  model_name: "claude-sonnet-4-6",
  agents: [{
    name: "mobile-operator",
    role: "Careful mobile operator",
    instruction: "Read before acting. Never send, publish, or delete.",
    agent_tools: [
      "phone_get_screen_tree",
      "phone_current_app",
      "phone_open_app",
      "phone_click_text",
      "phone_back",
      "phone_wait"
    ]
  }],
  steps: [{ kind: "agent", agent: { name: "mobile-operator" } }],
  maxCostUsd: 1.00
});

const { run_id } = await melaya.agents.pipelines.run("mobile-review", { project: "Operations" });

await melaya.agents.phone.registerActiveRun(run_id);

const status = await melaya.agents.pipelines.runStatus("mobile-review", run_id);

// Real-time run progress + HITL notifications over Socket.IO
melaya.platform.events.onRunUpdate(run_id, (e) => console.log(e.event_type, e.status));
melaya.platform.events.onHitlApproval((e) => console.log("HITL:", e.type, e.requestId));
```

## API surface (generally available)

Namespaced access is the primary API (`melaya.agents.*`, `melaya.platform.*`); flat aliases (`melaya.pipelines`, `melaya.hitl`, ...) remain for backwards compatibility.

| Area | Methods |
|---|---|
| Auth | `platform.auth.login`, `verifyMfa`, `register`, `verifySignup`, `resendVerification`, `forgotPassword`, `resetPassword`, `changePassword`, `me`, `check`, `refresh`, `myPermissions`, `createMobileHandoff` |
| MFA | `platform.mfa.status`, `setup`, `confirm` |
| Accounts | `platform.accounts.exportMyData`, `updateProfile`, `removeKey`, `credits`, `aiCredits`, `portfolioIdeasCredits`, `riskMonitoringCredits` |
| Projects | `platform.projects.list`, `create`, `rename`, `runnerProjects` |
| Connectors | `platform.connectors.connectedServices`, `set`, `delete`, `envHandle`, `googleOAuthStart` |
| Credentials | `platform.credentials.list`, `connectedServices`, `get`, `set`, `delete`, `test`, `getOperatorProfile`, `setOperatorProfile`, `listModels`, RAG + connect flows (`ragIngestStart`, `ragRetrieveStart`, `linkedinConnectStart`, `telegramAuthStart`, ...) |
| Pipelines | `agents.pipelines.listPipelines`, `create`, `get`, `update`, `remove`, `run`, `runIds`, `runStatus`, `cancelRun`, `outputs`, `output`, `previewCode`, `tools`, `subagents`, `catalogCounts`, `instantiateTemplate`, `buildWithAI`, `overview`, `count`, `list`, `recent`, `traces`, `trace`, `traceStats`, `deleteTraces`, `listSchedules`, `getSchedule`, `upsertSchedule`, `pauseSchedule`, `resumeSchedule` |
| Templates | `platform.templates.list`, `listGlobal`, `listValidated`, `save`, `update`, `duplicate`, `delete`, `share`, `shareTargets`, `listAssignments`, `assign(templateId, { userId \| projectId })`, `unassign(templateId, { userId \| projectId })` |
| Phone (Device Control) | `agents.phone.pair`, `listDevices`, `revokeDevice`, `screenTree`, `listApps`, `setAllowedApps`, `registerActiveRun` |
| HITL | `agents.hitl.pending`, `history`, `approve`, `reject`, `bulkDecide`, `runToolStats`, `runToolStatsByAgent`, `runMessages`, `runToolCalls` |
| Evals | `agents.evals.listRuns`, `summary`, `runDetail`, `compare`, `benchmarks` |
| Memory | `agents.memory.graph`, `runMemory`, `crew` |
| Models | `agents.models.listModels` |
| Assistant | `agents.assistant.getProfile`, `setProfile` |
| Events (real-time) | `platform.events.onRunUpdate`, `onInitPhase`, `onProjectEvent`, `onHitlApproval`, `onPipelineCreated`, `onPipelineUpdated`, `onPipelineDeleted`, `leaveRun`, `leaveProject`, `close` |
| Billing | `platform.billing.subscription`, `plans`, `createCheckout`, `createPortal` |
| Runner | `platform.runner.createToken`, `listTokens`, `revokeToken` |
| Team | `platform.team.listMembers`, `invite`, `createInviteLink`, `acceptInvite`, `updateMemberRole`, `removeMember`, `getPipelineVisibility`, `setPipelineVisibility` |
| Bugs | `platform.bugs.create`, `listMine`, `get`, `addComment`, `listNotifications`, `markNotificationsRead` |

## Authentication

Create an API key in the dashboard (**melaya.org → Settings → API Keys**). Keys are prefixed `mk_`. On the wire:

- **REST** — the key is sent only as an `Authorization: Bearer mk_...` header, never in the URL.
- **Public WebSocket streams** (preview market data) — the key rides as `?apiKey=` in the `wss://` URL (server protocol; redact it from proxy/APM/WebSocket query logs).
- **Private WebSocket streams** — the SDK first mints a short-lived ticket over REST, and only `?wsTicket=` appears in the URL; the API key itself is never in a private stream URL.

Alternatively, pass a `sessionToken` from `platform.auth.login()` instead of an API key.

---

## Trading (preview — not generally available)

> Everything below this line belongs to the Melaya Trading preview. It is not a production commitment and must not be used with real funds.

### Market data

```ts
// REST — normalized ticker from any of 70+ venues
const ticker = await melaya.market.ticker({ exchange: "binance", symbol: "BTC/USDT", market: "spot" });
console.log(ticker.last, ticker.bid, ticker.ask);

// REST — order book
const book = await melaya.market.orderbook({ exchange: "bybit", symbol: "BTC/USDT", market: "spot", limit: 20 });

// REST — candles
const candles = await melaya.market.ohlcv({ exchange: "okx", symbol: "ETH/USDT", timeframe: "1h", limit: 200 });
```

### Streaming

```ts
// Live ticker
const stream = melaya.stream.ticker({ exchange: "binance", symbol: "BTC/USDT", market: "spot" });
for await (const t of stream) {
  console.log(t.last);
}

// Or event-style
const liq = melaya.stream.liquidations({ exchange: "binance" });
liq.on("message", (e) => console.log(e.side, e.notional));
liq.on("close", () => console.log("stream closed"));
// liq.close();
```

### Trading

The same client covers the preview authenticated surface: your account, paper trading, strategies, and backtests. Reads need only your `mk_` key; live order placement needs a connected exchange key (`melaya.account.keys()`).

```ts
// Account: connected keys, tier limits, usage
const keys = await melaya.account.keys();          // [{ apiKeyId: "BINANCEUSDM_0", exchange, market, ... }]
const usage = await melaya.account.usage();

// Strategies — create() launches immediately. Paper (dryRun) needs no exchange key.
// SDK-launchable strategies are `custom` definitions: a Rhai script with an
// `evaluate()` that emits signals (emit_long / emit_short / emit_close).
const { strategyId } = await melaya.strategies.create({
  name: "My first bot",
  strategyType: "custom",
  exchange: "binanceusdm",
  symbol: "BTC/USDT:USDT",
  market: "FUTURES",
  dryRun: true,                                     // paper. dryRun:false + apiKeyId places real orders.
  params: {
    language: "rhai",
    definition: `fn evaluate() { emit_long(param("qty")); }`,
    qty: 0.001,
  },
});
await melaya.strategies.pause(strategyId);
await melaya.strategies.resume(strategyId);
const trades = await melaya.strategies.trades(strategyId);

// Paper trading (sim broker) — synthetic fills, no venue state
const bal = await melaya.sim.balance({ strategyId });
const fill = await melaya.sim.createOrder({
  strategyId, exchange: "binanceusdm", symbol: "BTC/USDT:USDT",
  side: "buy", type: "market", amount: 0.001, market: "FUTURES",
});

// Backtest on the Rust engine
const { job_id } = await melaya.backtest.start({
  strategyType: "custom", exchange: "binance", symbol: "BTC/USDT", timeframe: "1h",
  since_ms: Date.now() - 90 * 864e5, until_ms: Date.now(),
  language: "rhai", definition: `fn evaluate() { emit_long(param("qty")); }`, params: { qty: 0.001 },
});
let job; do { job = await melaya.backtest.job(job_id); } while (!["done","error"].includes(job.status));
const result = await melaya.backtest.results(job_id);   // metrics, equity_curve, ohlcv

// Live private feeds (ticket-minted automatically)
const events = await melaya.stream.strategies();
for await (const ev of events) console.log(ev.type, ev.strategyId);
```

### Trading API surface (preview)

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
| Live trading | `trade.balance`, `positions`, `openOrders`, `orders`, `closedOrders`, `myTrades`, `myTradesHistory`, `planOrders`, `positionsHistory`, `leverage`, `leverageTiers`, `createOrder`, `cancelOrder`, `amendOrder`, `cancelAllOrders`, `cancelPlanOrders`, `closePosition`, `setLeverage`, `setMarginMode`, `setPositionMode` |

---

## Older runtimes

```ts
// Node < 18 (no global fetch) and/or Node < 22 (no global WebSocket):
import { Melaya } from "@melaya/sdk";
import fetch from "node-fetch";
import WebSocket from "ws";

const melaya = new Melaya({ apiKey: "mk_...", fetch: fetch as any, WebSocket: WebSocket as any });
```

Full docs: **[melaya.org/docs](https://melaya.org/docs)**.

## License

[Apache-2.0](../../LICENSE)
