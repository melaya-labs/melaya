# Changelog

All notable changes to the Melaya SDKs are documented here. This project follows [Semantic Versioning](https://semver.org).

## [0.3.0] — Preview

Every SDK catches up with the platform as of September 2026: run inputs, pipeline documents, the tool-call audit log, project connectors, and the account, billing and phone calls added since 0.2.0. Same surface in all 9 languages.

### Added
- **Connector tool calls, the same surface as the MCP server**: a new `connectorTools` module lists your connected services, searches their tools by business keywords, describes one, tests a stored connector, starts connecting a service and calls a tool. Reads return at once. For writes you choose per call: `approval: "required"` (default) puts an approval card in the Melaya app and the write runs once after it is approved (`callStatus`, or `callAndWait` to poll for you); `approval: "none"` runs it immediately, audit-logged. Tools that move money or trade are refused in both modes. No call ever carries a credential value.
- **Run inputs**: `run()` takes `run_inputs` (`brief` + `values`); file values can be an uploaded `file_id`, a `url` (up to 25 MB) or inline `base64` (up to 7 MB). New `uploadRunFile`, `runInputs`, `runInputFile` (raw bytes) and `runActive`.
- **Pipeline documents**: upload, list and delete static-context documents; upload, ingest and delete RAG documents.
- **Tool-call audit log**: `projectToolCalls` (keyset-paginated, filter by tool, agent, run, status, connector source, approval, provider), `projectToolCallFacets`, `toolCallDetail`.
- **Project connectors**: `applyPersonal` (share your own connector into a project), `sharedBy`, Google account status / default / disconnect, and a database connection test run from your own runner.
- **Personal credentials**: Google account status / default / disconnect, database connection test, Telegram QR login, WhatsApp embedded signup, TikTok creator info, Substack email-link sign-in.
- **Everything else**: project ownership transfer, crew-memory edit and delete, ambassador perk / promo code redemption / reserved promo, email verification, phone app grant and screen-cast request.
- Every SDK gains a small built-in multipart upload and raw-bytes download (no new dependencies), plus offline unit tests for the new calls.

### Changed
- TypeScript: `pipelines.get()` is typed as the envelope the server returns, `{ name, client, config, code, docs }`, instead of the bare config. Read `envelope.config`, edit it, and pass that to `update()`.
- Pipeline examples and docs now match the server: the run is built from `steps[]` only (a config with only `agents[]` produces an empty pipeline), there is no `prompt` field (use `instruction` and `system_prompt_override`), and `hitl_mode`, `connector_source`, `force_local_runner` and `inputs[]` are documented. `executionTarget` is used for the tier check only; where a run executes comes from the stored config.

### Fixed
- Python, Go, Java, Kotlin, C#, PHP, Ruby and Rust: the memory reads (`memoryGraph`, `runMemory`, `crewMemory`) called `/evals/...` paths that do not exist and always failed with 404. They now use `/api/v1/private/memory/graph`, `/memory/runs/{runId}` and `/memory/crew`.

## [0.2.0] — Preview

The SDKs graduate from a trading client to the full Melaya platform. Melaya is the visual builder for high-trust AI agents; its flagship, Device Control, puts the AI you already pay for on your real phone apps. Trading ships later under Melaya Labs. Every SDK now covers the whole platform and agents surface, organized into clear namespaces.

### Added
- **Platform and Agents API across all 9 SDKs**: projects, pipelines and runs, HITL approvals, credentials and connectors, models, overview and usage, phone and device control, assistant, teams, templates, billing, runner. 150+ endpoints over the new REST surface, plus the trading plane that was already there.
- **Crystal-clear namespaces**: `melaya.trading.*`, `melaya.agents.*`, `melaya.platform.*`. The former flat accessors remain as deprecated aliases so nothing breaks.
- **Real-time events**: a Socket.IO client for `/api/v1/events` (agent run events and HITL approvals) in every language.

### Changed
- Positioning: Melaya is an agent builder and mobile Device Control platform first; trading is one plane among many and moves under Melaya Labs. The SDK docs now lead with agents and platform.
- Security: on REST requests the API key travels in the `Authorization` header, never in the URL query string. (Public WebSocket streams still authenticate with `?apiKey=` on the wss URL; private streams use a short-lived `?wsTicket=`.)
- Template assignments now use the server contract: `templates.assign` / `templates.unassign` take exactly one of `userId` or `projectId` (the former `targetType`/`targetId` shape was rejected by the server; `unassign` sends the target as query parameters on the `DELETE`).
- Events clients connect lazily on the first subscription or room join instead of in the client constructor, so REST-only usage opens no background connection and short-lived processes exit cleanly.

### Fixed
- Go SDK honors the `Retry-After` header on 429; the Python WebSocket stream reconnects with capped backoff and re-mints the private-stream `wsTicket` on reconnect.
- Run- and project-scoped event subscriptions filter by the payload's `runId`/`projectId`, so subscribing to several rooms no longer delivers every room's events to every callback; leaving a room also removes its listeners.
- Engine.IO long-poll transport (TypeScript, Python, Go, PHP, Ruby): v4 batched packets are split correctly, pings are answered on the polling transport, room joins are delivered, and a dead session triggers a full re-handshake instead of silent event loss.
- Python events work with `websockets` 13+ (`additional_headers` rename).
- Java and Kotlin publish their API-surface dependencies (Jackson; OkHttp and org.json) at compile scope, so consumers no longer need to add them manually.

## [0.1.4] — Preview

Publishing hardening across registries.

### Changed
- RubyGems publishing moved to OIDC trusted publishing (no long-lived `RUBYGEMS_API_KEY` secret).
- Maven Central releases automatically from the Central Portal (no manual promotion step).

## [0.1.3] — Preview

Two more registries come online.

### Added
- PHP publishing to Packagist via the root `composer.json`, with `.gitattributes` `export-ignore` rules so the package archive contains only the SDK.
- NuGet trusted publishing for the C#/.NET SDK (`Melaya.SDK`).

## [0.1.2] — Preview

Release-pipeline fixes so all SDKs publish from one tag.

### Fixed
- Maven Central publishing targets the Central Portal (`SonatypeHost.CENTRAL_PORTAL`) — fixes the legacy-OSSRH 402 on `createStagingRepository`.
- RubyGems job uses a token (`RUBYGEMS_API_KEY`) instead of the non-existent `configure-rubygems-credentials@v1` action.
- crates job publishes with `--allow-dirty` (regenerated `Cargo.lock`).

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
