<!--
SEO note: canonical reference for Melaya's trading API — account, paper (sim)
trading, live trading, backtesting, and launching strategies and AI agentic
trading crews (agent_crew) across 70+ venues.
Keywords: crypto trading API, paper trading API, backtesting API, launch trading
bot API, AI trading crew, agent_crew, human-in-the-loop trading, unified order API.
-->

# Trading & strategies

The authenticated plane: **account, paper (sim) trading, live trading, backtesting, and launching strategies**, including [AI agentic trading crews](./agentic-trading.md). For market data and public streams, see [Market data & streaming](./market-data.md); for the venue catalog and schema, see [Exchanges](./exchanges.md).

The same `mk_` key (sent as `Authorization: Bearer mk_...`) unlocks this plane. **Reads, paper trading, and backtesting need only the key; live order placement and live strategy/crew launches additionally require a connected exchange key** (referenced by `apiKeyId`; see `account.keys()`). Connect exchange keys in the dashboard → **Settings → Connectors**.

## Account

`GET /api/v1/private/keys` (connected keys, masked) · `/usage` · `/api-key`.

## Paper trading (sim broker)

Synthetic fills from the live tape (no venue, no credentials, no capital), per strategy:
`POST /api/v1/private/sim/create-order` · `cancel-order` · `GET .../sim/{balance,positions,open-orders,my-trades,list-accounts}`.

## Strategies

Launch = create; paper or live.

`POST /api/v1/strategies` (launch) · `GET /api/v1/strategies/list` · `/{id}` · `/{id}/{status,performance,executions,trades,logs}` · `POST /{id}/{pause,resume,stop,update-params}` · `DELETE /{id}`, plus the AI optimizer `POST /{id}/ai-opt/{start,approve,stop}` · `GET /{id}/ai-opt/{status,runs}`.

Two launchable `strategyType`s:

- **`custom`** — a Rhai `evaluate()` script (deterministic, per-bar). The lightweight path for rule-based bots.
- **`agent_crew`** — an autonomous multi-agent **trading crew** (Macro / TA / Quant / Sentiment / Risk / Portfolio / Execution personas). The full config rides in `params` (see [Launching a trading crew](#launching-a-trading-crew)). This is the flagship [AI agentic trading](./agentic-trading.md) capability, launchable straight from the API.

> **Approvals & HITL.** A live `agent_crew` gates **every order** through human-in-the-loop approval. Today those approvals are actioned in the Studio approval queue; a programmatic approvals API (receive request → approve / edit / reject) ships with the full agent API in **v2**. HITL is currently always-on for crews; opt-out for fully-autonomous live crews is on the roadmap.

## Backtesting

Native, on the Rust engine: the same engine that runs the strategy live.

`POST /api/v1/private/backtest/start` (single, grid, or random sweep) · `GET .../backtest/{jobs/:id, results/:id, trades/:id, sweep/:parent, favorites, funding-range}` · list / cancel / delete.

## Live trading

Real funds, so a connected exchange key is required. `POST /api/v1/private/<op>` for:

- **Writes** — `create-order`, `cancel-order`, `amend-order`, `cancel-all-orders`, `cancel-plan-orders`, `close-position`, `set-leverage`, `set-margin-mode`, `set-position-mode`.
- **Reads** — `balance`, `positions`, `open-orders`, `orders`, `closed-orders`, `my-trades`, `leverage`, `leverage-tiers`.

## Private WebSocket feeds

Mint a short-lived ticket (`POST /api/v1/private/private-ticket`), then open:

| Stream | Path |
|---|---|
| Account (balance / positions / orders / fills) | `wss://wss.melaya.org/ws/private?wsTicket=...` (or `?apiKey=mk_...&exchange=...&apiKeyId=...`) |
| Strategy events (status, fills, perf, lifecycle) | `wss://wss.melaya.org/ws/strategies?wsTicket=...` |

## Using the SDK

```ts
import { Melaya } from "@melaya/sdk";
const m = new Melaya({ apiKey: "mk_..." });

// Paper — launch a custom (Rhai) strategy, round-trip a sim order (no exchange key needed)
const { strategyId } = await m.strategies.create({
  name: "demo", strategyType: "custom", exchange: "binanceusdm", symbol: "BTC/USDT:USDT",
  market: "FUTURES", dryRun: true,
  params: { language: "rhai", definition: `fn evaluate() { emit_long(param("qty")); }`, qty: 0.001 },
});

// Live read (connected exchange key)
const bal = await m.trade.balance({ exchange: "binanceusdm", apiKeyId: "BINANCEUSDM_0" });

// Backtest on the Rust engine
const { job_id } = await m.backtest.start({
  strategyType: "custom", exchange: "binance", symbol: "BTC/USDT", timeframe: "1h",
  language: "rhai", definition: `fn evaluate() { emit_long(param("qty")); }`, params: { qty: 0.001 },
});

// Private stream
for await (const ev of await m.stream.strategies()) console.log(ev.type);
```

The same surface exists in all 9 SDKs (idiomatic per language). Live-trading **write** ops (`trade.createOrder`, `cancelOrder`, …) move **real funds**, so test with the paper `sim` broker or a `dryRun` strategy first.

## Launching a trading crew

A crew launches through the same `strategies.create` call, with `strategyType: "agent_crew"` and the crew config in `params`. Run it `dryRun: true` for paper, or `dryRun: false` with an `apiKeyId` for live. See the [AI agentic trading guide](./agentic-trading.md) for what each field means.

```ts
import { Melaya } from "@melaya/sdk";
const m = new Melaya({ apiKey: "mk_..." });

const SONNET = { provider: "anthropic", name: "claude-sonnet-4-6" };
const FROM   = "shared.runtime.trading_crew_personas";   // import_path for every persona factory

const { strategyId } = await m.strategies.create({
  name: "Daily Majors Long",
  strategyType: "agent_crew",
  exchange: "binanceusdm",
  market: "futures",
  dryRun: true,                          // paper. dryRun:false + apiKeyId = live.
  params: {
    // ── Universe & venue ──
    universe: ["BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "AVAXUSDT"],
    exchange: "binanceusdm",
    dryRunMode: "paper",
    runtimeMode: "docker",               // "local_runner" if any persona uses lmstudio/ollama

    // ── Cadence ──
    cadenceMode: "time",                 // "time" | "event" | "hybrid"
    cadenceSeconds: 86400,               // daily

    // ── Crew-wide toolkit (the allowlist). Per-persona scoping is `agentTools`
    //    on each step below; a persona with agentTools:[] inherits this full set. ──
    tools: [
      "melaya_ohlcv", "melaya_funding_rates_latest", "melaya_ws_ticker", "melaya_ws_orderbook",
      "melaya_get_account_balance", "melaya_get_positions",
      "melaya_place_order", "melaya_exec_sim_create_order",
    ],

    // ── Shared context docs, each granted to specific personas (RAG / inline) ──
    context: [
      { id: "house_rules", title: "House rules",
        body: "Long-only. Never enter without a stop. ≤5 concurrent positions.",
        grantedTo: ["risk_manager", "execution_trader"] },
    ],

    // ── The persona pipeline: Macro ∥ TA → Risk → Execution. ──
    //   Each agent names its factory + import_path + model, an `instruction`
    //   (omit to use the persona's default prompt), its OWN `agentTools`
    //   (scoped — [] inherits the crew toolkit), and `humanApprovalTools`
    //   (the write tools that gate through HITL for THIS persona).
    steps: [
      {
        kind: "parallel",                // analysts fan out; Risk gets both outputs
        joinStrategy: "concat",
        agents: [
          { factory: "make_macro_analyst", import_path: FROM, model: SONNET,
            instruction: "Frame the regime from rates, DXY, funding, liquidations. Emit a per-asset macro score.",
            agentTools: ["melaya_funding_rates_latest"],          // scoped to this persona
            humanApprovalTools: [] },                              // read-only → nothing to gate
          { factory: "make_ta_analyst", import_path: FROM, model: SONNET,
            instruction: "Confirm setups; emit entry / SL / TP as ABSOLUTE prices.",
            agentTools: ["melaya_ohlcv", "melaya_ws_orderbook"],
            humanApprovalTools: [] },
        ],
      },
      {
        kind: "agent",
        agent: { factory: "make_risk_manager", import_path: FROM, model: SONNET,
          instruction: "Size ≤0.5% equity/trade, 5% total. VETO with RISK_BLOCK. Emit go/no-go AND size.",
          agentTools: ["melaya_get_account_balance", "melaya_get_positions"],
          humanApprovalTools: [] },
      },
      {
        kind: "agent",
        agent: { factory: "make_execution_trader", import_path: FROM, model: SONNET,
          instruction: "Place the approved plan: a single entry with attached SL + TP. Never resize, never skip an SL.",
          agentTools: ["melaya_place_order", "melaya_exec_sim_create_order"],
          humanApprovalTools: ["melaya_place_order", "melaya_exec_sim_create_order"] }, // every order waits for approval
      },
    ],

    // ── Live market view + event triggers ──
    wsSubscriptions: [
      { endpoint: "ticker", exchange: "binanceusdm", symbol: "BTCUSDT", market: "FUTURES" },
      // …one per universe symbol
    ],
    eventTriggers: [],                   // event/hybrid: [{ name, endpoint, exchange, symbol, expression }]

    // ── Safety ──
    sidecars: { drawdown: true, blackout: true, funding: true, cascade: true },
    maxWritesPerCycle: 5,
    maxCostPerDayUsd: 5,
    cumulativeLossHaltPct: 10,
    consecutiveLossesHalt: 5,
    onShutdown: "leave",                 // venue-side SL/TP protects positions on stop; "flat" to auto-flatten
  },
});
// Watch lifecycle + fills on the strategy stream; approve orders in the Studio queue (v1).
for await (const ev of await m.stream.strategies()) console.log(ev.type);
```

**Field notes**

- **Tools are per-persona.** Top-level `params.tools` is the crew's allowlist; each persona's `agentTools` *scopes within* it (`[]` = inherit the whole crew toolkit). `humanApprovalTools` is that persona's HITL gate — the write tools whose calls pause for your approval. Only the Execution seat should carry order-placing tools.
- **`steps`** is the pipeline. A step is either `{ kind: "agent", agent: {…} }` or `{ kind: "parallel", agents: [{…}], joinStrategy: "concat" }` (parallel analysts; their outputs are concatenated for the next seat). Each agent entry: `factory`, `import_path` (always `shared.runtime.trading_crew_personas`), `model: { provider, name }`, optional `instruction`, `agentTools`, `humanApprovalTools`, and optional per-agent `context: [{ title, body }]`.
- **`context`** carries shared docs; `grantedTo` lists the persona keys that receive each one.
- **Persona factories:** `make_macro_analyst`, `make_ta_analyst`, `make_quant`, `make_sentiment_analyst`, `make_risk_manager`, `make_portfolio_manager`, `make_execution_trader`.
- **Validation (server-side):** `tools` must be on the trading allowlist, any local-model persona forces `runtimeMode: "local_runner"`, and `eventTriggers` expressions are sandboxed.

The broader **agent-builder API** (arbitrary non-trading agents, pipelines, RAG, connectors) arrives in **v2**; today the public API launches `custom` and `agent_crew` strategies.

## Where next

- **[AI agentic trading →](./agentic-trading.md)** — the conceptual guide to trading crews (personas, cadence, safety, lifecycle).
- **[Market data & streaming →](./market-data.md)** — REST reads and public streams.
- **[Exchanges →](./exchanges.md)** — venue catalog and normalized schema.
