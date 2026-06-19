# Changelog

All notable changes to the Melaya SDKs are documented here. This project follows [Semantic Versioning](https://semver.org).

## [0.1.1] — Preview

First clean multi-registry cut: all nine SDKs publish together from one version tag.

### Added
- **Go**, **Rust**, **Java**, **Kotlin**, **C#/.NET**, **Ruby**, and **PHP** SDKs join TypeScript and Python on their registries — one identical surface each.
- Live trading plane (`trade.*`) across every SDK: reads (balance, positions, open/closed orders, my-trades, leverage, leverage-tiers) and writes (create/cancel/amend/cancel-all/cancel-plan orders, close-position, set-leverage/margin-mode/position-mode).
- Runnable quickstart examples in all nine languages under [`examples/`](./examples).
- Expanded REST surface: `ohlcvMulti`, `fundingRateHistoryMulti`, `openInterestHistoryMulti`, `marketConstraints`, `predictionMarkets`, `catalogCounts`.

### Fixed
- Removed hard-coded local build paths from `scripts/build_logos.py` (now repo-relative + env-driven).
- Corrected the `sdk-rust` repository URL to `melaya-labs/melaya`.

## [0.1.0] — Preview

Initial public release of the official Melaya SDKs and developer docs.

### Added
- **Official SDKs in 9 languages**, one identical surface each: **`@melaya/sdk`** (TypeScript/JavaScript), **`melaya`** (Python), **`melaya`** (Rust crate), **sdk-go** (Go module), **`melaya`** (RubyGem), and Java, Kotlin, C#/.NET, and PHP clients — thin, dependency-light clients for the Melaya trading platform across 70+ venues.
- REST market data: `listExchanges`, `ticker`, `orderbook`, `ohlcv`, `trades`, `markets`, `currencies`, `status`, `time`.
- Batch / derivatives: `tickers`, `ohlcvMulti`, `fundingRates`, `fundingRateHistory`, `fundingRateHistoryMulti`, `openInterest`, `openInterestHistory`, `openInterestHistoryMulti`, `instruments`, `marketConstraints`, `liquidationEvents`, `predictionMarkets`, and `catalogCounts`.
- Account: `account.keys`, `usage`, `apiKeyStatus`.
- Strategies: launch (`create`), `list`, `get`, `pause`, `resume`, `stop`, `delete`, `updateParams`, `status`, `performance`, `executions`, `trades`, `logs`, plus the AI optimizer (`aiOptStart`/`aiOptStatus`/`aiOptApprove`/`aiOptStop`/`aiOptRuns`).
- Paper trading (sim broker): `balance`, `positions`, `openOrders`, `myTrades`, `createOrder`, `cancelOrder`, `listAccounts`.
- **Live trading plane** (`trade.*`, a connected exchange key required) — reads: `balance`, `positions`, `openOrders`, `orders`, `closedOrders`, `myTrades`, `leverage`, `leverageTiers`; writes: `createOrder`, `cancelOrder`, `amendOrder`, `cancelAllOrders`, `cancelPlanOrders`, `closePosition`, `setLeverage`, `setMarginMode`, `setPositionMode`.
- Backtesting on the Rust engine: `start`, `job`, `results`, `trades`, `sweep`, `list`, `favorites`, `fundingRange`, `cancel`, `delete`, `deleteAll`.
- WebSocket streams: public `ticker`, `orderbook`, `ohlcv`, `trades`, `liquidations`; private `stream.strategies` and `stream.private` (tickets minted automatically).
- Developer docs (concepts, exchanges, FAQ, comparison, agentic-trading guide), the published 70+-venue dataset (`data/exchanges.json` / `.csv`), and runnable quickstart examples in all 9 languages under [`examples/`](./examples).

### Notes
- Responses are unwrapped from the API's `{ ok, ... }` envelope; a request-level failure (`ok: false`) raises a `MelayaError`.
- One `mk_` key authenticates the whole client. Market data, account/strategy reads, paper trading, and backtesting need only the key; **live** order placement and live strategy launches additionally require a connected exchange key (referenced by `apiKeyId`).
- Verified end-to-end against the live API in every language: market data, all five public streams, account reads, strategy reads, a paper-order round-trip, backtest start/poll/list, and the private strategy + account streams all pass. Live-trading **write** ops are wired and validated but never auto-invoked (they move real funds).
