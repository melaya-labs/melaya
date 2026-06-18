# Changelog

All notable changes to the Melaya SDKs are documented here. This project follows [Semantic Versioning](https://semver.org).

## [0.1.0] — Preview

Initial public release of the official Melaya SDKs and developer docs.

### Added
- **`@melaya/sdk`** (TypeScript/JavaScript) and **`melaya`** (Python) — zero-dependency, isomorphic clients for the Melaya trading platform across 70+ venues.
- REST market data: `listExchanges`, `ticker`, `orderbook`, `ohlcv`, `trades`, `markets`, `currencies`, `status`, `time`.
- Batch / derivatives: `tickers`, `fundingRates`, `fundingRateHistory`, `openInterest`, `openInterestHistory`, `instruments`, `liquidationEvents`.
- Account: `account.keys`, `usage`, `apiKeyStatus`.
- Strategies: launch (`create`), `list`, `get`, `pause`, `resume`, `stop`, `delete`, `updateParams`, `status`, `performance`, `executions`, `trades`, `logs`, plus the AI optimizer (`aiOptStart`/`aiOptStatus`/`aiOptApprove`/`aiOptStop`/`aiOptRuns`).
- Paper trading (sim broker): `balance`, `positions`, `openOrders`, `myTrades`, `createOrder`, `cancelOrder`, `listAccounts`.
- Backtesting on the Rust engine: `start`, `job`, `results`, `trades`, `sweep`, `list`, `favorites`, `fundingRange`, `cancel`, `delete`, `deleteAll`.
- WebSocket streams: public `ticker`, `orderbook`, `ohlcv`, `trades`, `liquidations`; private `stream.strategies` and `stream.private` (tickets minted automatically).
- Developer docs (concepts, exchanges, FAQ, comparison, agentic-trading guide), the published 70+-venue dataset (`data/exchanges.json` / `.csv`), and runnable examples.

### Notes
- Responses are unwrapped from the API's `{ ok, ... }` envelope; a request-level failure (`ok: false`) raises a `MelayaError`.
- One `mk_` key authenticates the whole client. Market data, account/strategy reads, paper trading, and backtesting need only the key; **live** order placement and live strategy launches additionally require a connected exchange key (referenced by `apiKeyId`).
- Verified end-to-end against the live API: market data, all five public streams, account reads, strategy reads, a paper-order round-trip, backtest start/poll/list, and the private strategy stream all pass.
