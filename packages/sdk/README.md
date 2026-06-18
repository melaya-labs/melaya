# @melaya/sdk

Official TypeScript/JavaScript SDK for the **[Melaya](https://melaya.org)** trading platform — normalized market data, paper + live trading, backtesting, and an AI agentic trading crew across **70+ venues**, powered by an in-house Rust engine.

- Zero runtime dependencies (uses the platform `fetch` + `WebSocket`).
- Isomorphic: works in Node 18+ and the browser.
- Fully typed, normalized responses across every venue.
- Public market data + the full authenticated trading surface from one client.

## Install

```bash
npm install @melaya/sdk
```

## Quick start

```ts
import { Melaya } from "@melaya/sdk";

const melaya = new Melaya({ apiKey: process.env.MELAYA_API_KEY! }); // keys are prefixed `mk_`

// REST — normalized ticker from any of 70+ venues
const ticker = await melaya.market.ticker({ exchange: "binance", symbol: "BTC/USDT", market: "spot" });
console.log(ticker.last, ticker.bid, ticker.ask);

// REST — order book
const book = await melaya.market.orderbook({ exchange: "bybit", symbol: "BTC/USDT", market: "spot", limit: 20 });

// REST — candles
const candles = await melaya.market.ohlcv({ exchange: "okx", symbol: "ETH/USDT", timeframe: "1h", limit: 200 });
```

## Streaming

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

## Trading

The same client covers the authenticated surface: your account, paper trading, live strategies, and backtests. Reads need only your `mk_` key; live order placement needs a connected exchange key (`melaya.account.keys()`).

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

## Authentication

Create an API key in the dashboard (**melaya.org → Settings → API Keys**). Keys are prefixed `mk_`; the SDK sends it automatically on every REST call and WebSocket connection. Public market-data and account/strategy reads work with the key alone. **Live** order placement and live strategy launches additionally require a connected exchange key — connect one in **Settings → Connectors**, then reference it by `apiKeyId`. Paper trading and backtesting never touch a venue and need no exchange credentials.

## Older runtimes

```ts
// Node < 18 (no global fetch) and/or Node < 22 (no global WebSocket):
import { Melaya } from "@melaya/sdk";
import fetch from "node-fetch";
import WebSocket from "ws";

const melaya = new Melaya({ apiKey: "mk_...", fetch: fetch as any, WebSocket: WebSocket as any });
```

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
| Live trading | `trade.balance`, `positions`, `openOrders`, `orders`, `closedOrders`, `myTrades`, `myTradesHistory`, `planOrders`, `positionsHistory`, `leverage`, `leverageTiers`, `createOrder`, `cancelOrder`, `amendOrder`, `cancelAllOrders`, `cancelPlanOrders`, `closePosition`, `setLeverage`, `setMarginMode`, `setPositionMode` |

Full docs: **[melaya.org/docs](https://melaya.org/docs)**.

## License

[Apache-2.0](../../LICENSE)
