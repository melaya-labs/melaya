# melaya — Official Rust SDK v0.2.0

> **Product status:** Agent Builder and Mobile Device Control are the current public products. Melaya Trading namespaces are preview-only and planned for later public release; do not use them with real funds.

Idiomatic async Rust client for the [Melaya](https://melaya.org) platform:
market data, trading, strategies, backtests, WebSocket streaming, agent projects,
HITL approvals, credentials, billing, phone control, templates, evals, and
real-time Socket.IO events.

---

## Domain namespaces

Three top-level namespaces group every module so the API surface is immediately
discoverable via IDE autocomplete:

| Namespace | Modules |
|---|---|
| `melaya.trading` | `market`, `account`, `sim`, `strategies`, `trade`, `backtest`, `stream` |
| `melaya.agents`  | `pipelines`, `hitl`, `assistant`, `phone`, `evals` |
| `melaya.platform`| `projects`, `credentials`, `connectors`, `billing`, `team`, `templates`, `runner`, `auth`, `mfa`, `accounts`, `bugs`, `events` |

Flat accessors (`melaya.market`, `melaya.pipelines`, …) remain available for
backward compatibility.

```rust
use melaya::Melaya;

#[tokio::main]
async fn main() {
    let m = Melaya::new(&std::env::var("MK").unwrap()).unwrap();

    // Trading namespace
    let ticker = m.trading.market.ticker("binance", "BTC/USDT", Some("spot")).await.unwrap();
    println!("BTC/USDT last: {}", ticker["last"]);

    let runs = m.trading.backtest.list(Some(10), None).await.unwrap();
    println!("Recent backtests: {runs}");

    // Agents namespace
    let pipelines = m.agents.pipelines.list(None, None, None, None, None, None).await.unwrap();
    println!("Pipeline runs: {pipelines}");

    let pending = m.agents.hitl.pending().await.unwrap();
    println!("Pending HITL approvals: {pending}");

    // Platform namespace
    let projects = m.platform.projects.list().await.unwrap();
    println!("Projects: {projects}");

    let sub = m.platform.billing.subscription().await.unwrap();
    println!("Subscription: {sub}");

    // Real-time events via platform namespace
    let mut ev = m.platform.events.subscribe_run("run-abc");
    while let Some(frame) = ev.recv().await {
        println!("run event: {frame}");
    }
}
```

---

## Installation

Add to your `Cargo.toml`:

```toml
[dependencies]
melaya = "0.2"
tokio = { version = "1", features = ["full"] }
```

---

## Auth

Create an API key at **melaya.org → Settings → API Keys**. Keys are prefixed `mk_`.
Pass it to the constructor; never hard-code it.

```rust
let m = melaya::Melaya::new(&std::env::var("MK").unwrap())?;
```

The constructor rejects keys that do not start with `mk_`.
Every REST call sends the key only as `Authorization: Bearer` — never in a
query string. Public WebSocket market-data streams authenticate with
`?apiKey=` in the `wss://` URL (server protocol); private WebSocket streams
use a short-lived `?wsTicket=` minted over REST instead of the API key.
Credentials are never logged. TLS is enforced by default.

---

## Quick start: Agent Builder & Device Control

### Pair a phone

```rust
use melaya::Melaya;

#[tokio::main]
async fn main() {
    let m = Melaya::new(&std::env::var("MELAYA_API_KEY").unwrap()).unwrap();

    // Start pairing — returns a code to enter on the Melaya APK
    let pairing = m.agents.phone.pair().await.unwrap();
    println!("Pairing code: {} (expires in {}s)", pairing["code"], pairing["expiresInSeconds"]);

    let devices = m.agents.phone.list_devices().await.unwrap();
    println!("Devices: {devices}");

    let apps = m.agents.phone.list_apps().await.unwrap();
    println!("Apps: {apps}");

    // Give agents the smallest practical app allowlist
    m.agents.phone.set_allowed_apps(&["com.android.chrome"]).await.unwrap();
}
```

### Create and run an agent pipeline

Configure provider credentials through Melaya Connectors first. Never include
a provider key in pipeline configuration or per-run overrides.

```rust
use melaya::{Melaya, PipelineRunOptions};
use serde_json::json;

#[tokio::main]
async fn main() {
    let m = Melaya::new(&std::env::var("MELAYA_API_KEY").unwrap()).unwrap();

    m.agents.pipelines.create(
        "mobile-review",
        "Operations",
        None,
        Some(&json!({
            "model_provider": "anthropic",
            "model_name": "claude-sonnet-4-6",
            "agents": [{
                "name": "mobile-operator",
                "role": "Careful mobile operator",
                "instruction": "Read before acting. Never send, publish, or delete.",
                "agent_tools": [
                    "phone_get_screen_tree",
                    "phone_current_app",
                    "phone_open_app",
                    "phone_click_text",
                    "phone_back",
                    "phone_wait"
                ]
            }],
            "steps": [{ "kind": "agent", "agent": { "name": "mobile-operator" } }],
            "maxCostUsd": 1.00
        })),
    ).await.unwrap();

    let accepted = m.agents.pipelines.run(
        "mobile-review",
        Some(PipelineRunOptions {
            project: Some("Operations".into()),
            ..Default::default()
        }),
    ).await.unwrap();

    m.agents.phone.register_active_run(&accepted.run_id).await.unwrap();

    let status = m.agents.pipelines
        .run_status("mobile-review", &accepted.run_id)
        .await
        .unwrap();
    println!("run {} → {}", status.run_id, status.status);
}
```

---

## Quick start

### Market data (trading preview, namespaced)

```rust
use melaya::Melaya;

#[tokio::main]
async fn main() {
    let m = Melaya::new(&std::env::var("MK").unwrap()).unwrap();

    let ticker = m.trading.market.ticker("binance", "BTC/USDT", Some("spot")).await.unwrap();
    println!("BTC/USDT last: {}", ticker["last"]);

    let candles = m.trading.market.ohlcv("binance", "BTC/USDT", "1h", Some("spot"), Some(24)).await.unwrap();
    println!("Got {} candles", candles.as_array().unwrap().len());
}
```

### Agent projects + HITL (namespaced)

```rust
use melaya::Melaya;

#[tokio::main]
async fn main() {
    let m = Melaya::new(&std::env::var("MK").unwrap()).unwrap();

    // List pipeline runs
    let runs = m.agents.pipelines.list(None, None, None, None, None, None).await.unwrap();
    println!("Runs: {runs}");

    // List pending HITL approvals
    let pending = m.agents.hitl.pending().await.unwrap();
    println!("Pending approvals: {pending}");

    // Approve the first one
    if let Some(req) = pending.as_array().and_then(|a| a.first()) {
        if let Some(rid) = req["requestId"].as_str() {
            m.agents.hitl.approve(rid, Some("LGTM")).await.unwrap();
        }
    }
}
```

### Real-time events (Socket.IO)

```rust
use melaya::Melaya;

#[tokio::main]
async fn main() {
    let m = Melaya::new(&std::env::var("MK").unwrap()).unwrap();

    // Subscribe to a pipeline run
    let mut run_ev = m.platform.events.subscribe_run("run-abc123");
    while let Some(ev) = run_ev.recv().await {
        println!("run event: {ev}");
    }

    // Subscribe to HITL approval events
    let mut hitl_ev = m.platform.events.subscribe_hitl();
    while let Some(ev) = hitl_ev.recv().await {
        println!("HITL: {ev}");
    }
}
```

### Backtest (trading preview)

```rust
use melaya::Melaya;
use serde_json::json;

#[tokio::main]
async fn main() {
    let m = Melaya::new(&std::env::var("MK").unwrap()).unwrap();

    let bt = m.trading.backtest.start(&json!({
        "strategyType": "custom",
        "language": "rhai",
        "definition": "fn evaluate() { emit_long(param(\"qty\")); }",
        "exchange": "binance",
        "symbol": "BTC/USDT",
        "timeframe": "1h",
        "params": { "qty": 0.001 }
    })).await.unwrap();

    let job_id = bt["job_id"].as_str().unwrap();
    let results = m.trading.backtest.results(job_id).await.unwrap();
    println!("Metrics: {}", results["metrics"]);
}
```

### Templates

```rust
use melaya::Melaya;
use serde_json::json;

#[tokio::main]
async fn main() {
    let m = Melaya::new(&std::env::var("MK").unwrap()).unwrap();

    // Create a template
    let tmpl = m.platform.templates.save(&json!({
        "name": "Daily report",
        "payload": { "steps": [] }
    })).await.unwrap();

    let id = tmpl["id"].as_str().unwrap();
    m.platform.templates.share(id, "team").await.unwrap();
    println!("Template shared: {id}");
}
```

---

## Flat vs namespaced access

Every module is reachable two ways — as a flat accessor on the client, or
grouped under a domain namespace (recommended). Both styles are supported:

```rust
// Flat style
m.market.ticker("binance", "BTC/USDT", Some("spot")).await?;
m.pipelines.list(None, None, None, None, None, None).await?;
m.hitl.pending().await?;
m.events.subscribe_run("run-abc");

// Namespaced style — recommended
m.trading.market.ticker("binance", "BTC/USDT", Some("spot")).await?;
m.agents.pipelines.list(None, None, None, None, None, None).await?;
m.agents.hitl.pending().await?;
m.platform.events.subscribe_run("run-abc");
```

The namespace structs share the same underlying HTTP client and Socket.IO
connection as the flat fields — no extra connections are opened.

---

## TLS

TLS certificate and hostname verification are always enabled using rustls and
the public Web PKI root set.

---

## Method reference

### `client.trading.market` / `client.market`

| Method | Description |
|---|---|
| `list_exchanges()` | All supported venues |
| `ticker(exchange, symbol, market)` | Best bid/ask + 24h stats |
| `orderbook(exchange, symbol, market, limit)` | Order book |
| `ohlcv(exchange, symbol, timeframe, market, limit)` | OHLCV candles |
| `trades(exchange, symbol, market)` | Recent public trades |
| `markets(exchange)` | Tradable markets |
| `currencies(exchange)` | Listed currencies |
| `status(exchange)` | Operational status |
| `time(exchange)` | Server time |
| `tickers(exchange, symbols, market)` | Batch tickers (POST) |
| `funding_rates(exchange, symbols, market)` | Funding rates (POST) |
| `funding_rate_history(exchange, symbol, hours, market)` | Funding rate history |
| `open_interest(exchange, symbols, market)` | Open interest |
| `open_interest_history(exchange, symbol, hours, market)` | OI history |
| `instruments(exchange, market)` | Instrument list |
| `liquidation_events(exchange, symbol, since_ms, limit)` | Historical liquidations |
| `ohlcv_multi(exchange, symbols, timeframe, limit, market)` | Multi-symbol OHLCV |
| `market_constraints(exchange, symbol, market)` | Trading constraints |
| `funding_rate_history_multi(exchanges, symbol, hours)` | Cross-venue funding history |
| `open_interest_history_multi(exchanges, symbol, hours)` | Cross-venue OI history |
| `prediction_markets(venue)` | Prediction market listings |
| `catalog_counts()` | Platform catalog counts |
| `liquidations(exchange, symbol, since_ms, limit)` | CEX liquidation data (POST) |
| `mdd_pairs()` | Max-drawdown pairs (public) |
| `onchain_yields()` | On-chain yield data (Forge+) |
| `onchain_liquidity()` | On-chain liquidity data (Forge+) |
| `banner()` | Marketing/notification banner (public) |
| `price_history()` | Price history for chart display (public) |

### `client.trading.account` / `client.account`

| Method | Description |
|---|---|
| `keys()` | Connected exchange keys (masked) |
| `usage()` | Tier + usage counters |
| `api_key_status()` | API key status |

### `client.platform.accounts` / `client.accounts` (platform account management)

| Method | Description |
|---|---|
| `export_my_data()` | GDPR data export |
| `remove_key(key_id)` | Remove a CEX API key |
| `update_profile(body)` | Update display name/avatar/settings |
| `credits()` | Credit balance + history |
| `ai_credits()` | AI/LLM credit balance |
| `portfolio_ideas_credits()` | Portfolio-ideas credit balance |
| `risk_monitoring_credits()` | Risk-monitoring credit balance |

### `client.platform.auth` / `client.auth`

| Method | Description |
|---|---|
| `login(username, password)` | Login; returns JWT + optional MFA challenge |
| `verify_mfa(challenge_token, code)` | Resolve MFA challenge |
| `register(body)` | Create new user account |
| `verify_signup(token)` | Confirm email from signup link |
| `resend_verification(email)` | Re-send email verification |
| `me()` | Current user profile |
| `check()` | Session validity check |
| `change_password(current, new)` | Change password |
| `forgot_password(email)` | Initiate password reset |
| `reset_password(token, new_password)` | Complete password reset |
| `create_mobile_handoff()` | Mint mobile deep-link handoff token |
| `my_permissions()` | Caller's permission flags |
| `refresh()` | Rotate session JWT |

### `client.platform.mfa` / `client.mfa`

| Method | Description |
|---|---|
| `status()` | MFA enrollment status |
| `setup()` | Initiate TOTP setup |
| `confirm_setup(code)` | Confirm TOTP setup |

### `client.platform.billing` / `client.billing`

| Method | Description |
|---|---|
| `subscription()` | Current subscription status and tier |
| `create_checkout(price_id, tier)` | Stripe Checkout session URL |
| `create_portal()` | Stripe Customer Portal session URL |
| `plans()` | Public pricing plan details |

### `client.platform.runner` / `client.runner`

| Method | Description |
|---|---|
| `create_token(label)` | Mint a `mel_run_` runner token |
| `list_tokens()` | List all tokens (masked) |
| `revoke_token(token_id)` | Revoke a runner token |

### `client.platform.projects` / `client.projects`

| Method | Description |
|---|---|
| `list()` | All accessible projects |
| `create(name, description)` | Create a new project |
| `rename(old_name, new_name)` | Rename a project |
| `runner_projects()` | Runner-facing project list |

### `client.agents.pipelines` / `client.pipelines`

| Method | Description |
|---|---|
| `overview()` | Dashboard overview (usage, active strategies) |
| `model_prices()` | AI model pricing data |
| `chart_data()` | Cost/usage chart data |
| `cost_breakdown()` | Cost breakdown by model/provider |
| `count()` | Run count by status |
| `list(...)` | Paginated run list |
| `recent()` | Most recent runs |
| `traces(run_id)` | Traces for a run |
| `trace(run_id, trace_id)` | Single trace |
| `trace_stats(run_id, trace_id)` | Trace statistics |
| `delete_traces(run_id)` | Delete all traces for a run |
| `list_schedules()` | All pipeline schedules |
| `get_schedule(project, name)` | Single schedule |
| `upsert_schedule(project, name, cron, config)` | Create/update schedule |
| `pause_schedule(project, name)` | Pause a schedule |
| `resume_schedule(project, name)` | Resume a schedule |
| `list_pipelines()` | All pipelines accessible to the caller |
| `create(name, project, description, config)` | Create a pipeline |
| `get(name, project)` | Single pipeline |
| `update(name, config, project)` | Update a pipeline's configuration |
| `delete_pipeline(name, project)` | Delete a pipeline |
| `run(name, opts)` | Enqueue a run; returns the run ID |
| `run_ids(name)` | All run IDs for a pipeline |
| `run_status(name, run_id)` | Status of a specific run |
| `cancel_run(name, run_id)` | Cancel a running or queued run |
| `outputs(name)` | List output artifacts |
| `output(name, output_path, download)` | Get one output artifact |
| `preview_code(config)` | Preview generated code without persisting |
| `tools()` | Agent tool registry (1,500+ scoped tools) |
| `subagents()` | Subagent registry (100+ specialized subagents) |
| `instantiate_template(template_id, name, project, overrides)` | Instantiate a pipeline from a template |
| `build_with_ai(brief)` | Generate a pipeline config from a brief |
| `server_version()` | Current public server version |

### `client.agents.hitl` / `client.hitl`

| Method | Description |
|---|---|
| `pending()` | List pending approvals |
| `history(limit, offset)` | Historical decisions |
| `approve(request_id, comment)` | Approve a tool call |
| `reject(request_id, comment)` | Reject a tool call |
| `bulk_decide(ids, decision, comment)` | Bulk approve/reject |
| `run_tool_stats(run_id)` | Tool-call stats for a run |
| `run_messages(run_id, limit, cursor)` | Paginated run messages |
| `run_tool_stats_by_agent(run_id)` | Stats by agent |
| `run_tool_calls(run_id)` | All tool calls for a run |

### `client.platform.credentials` / `client.credentials`

| Method | Description |
|---|---|
| `list()` | All stored credentials |
| `connected_services()` | Connected third-party services |
| `get(service, key)` | Get a credential by service |
| `set(service, value, key, label)` | Store/update a credential |
| `delete(service)` | Delete a credential |
| `test(service)` | Test a credential |
| `get_operator_profile()` | Operator profile |
| `set_operator_profile(profile)` | Save operator profile |
| `list_models(provider, capability)` | Available AI models |
| `rag_ingest_start(body)` | Start RAG ingestion job |
| `rag_ingest_status(session_id)` | Poll RAG ingestion status |
| `rag_retrieve_start(body)` | Start RAG retrieval |
| `rag_retrieve_status(session_id)` | Poll RAG retrieval status |
| `pick_folder_start()` | Start native folder picker |
| `pick_folder_status(session_id)` | Poll folder picker status |
| `linkedin_connect_start()` | Start LinkedIn OAuth |
| `linkedin_connect_cancel()` | Cancel LinkedIn OAuth |
| `linkedin_connect_status()` | Poll LinkedIn OAuth status |
| `luma_connect_start()` | Start Luma OAuth |
| `luma_connect_status()` | Poll Luma OAuth status |
| `luma_connect_cancel()` | Cancel Luma OAuth |
| `get_luma_registration_schema(event_id)` | Luma event form schema |
| `google_oauth_start(body)` | Start Google OAuth |
| `cli_auth_start()` | Start CLI auth (device-code) |
| `notebooklm_login(body)` | Store NotebookLM credentials |
| `notebooklm_status()` | Check NotebookLM status |
| `telegram_auth_start(phone)` | Start Telegram auth |
| `telegram_auth_code(code)` | Submit Telegram SMS code |
| `telegram_auth_2fa(password)` | Submit Telegram 2FA |
| `melaya_accounts()` | List Melaya sub-accounts |

### `client.platform.connectors` / `client.connectors`

| Method | Description |
|---|---|
| `connected_services(project)` | Connected services for a project |
| `set(project, service, value, key, label)` | Store project-scoped credential |
| `delete(project, service)` | Delete project-scoped credential |
| `env_handle(project)` | Short-lived env-handle token |
| `google_oauth_start(project, body)` | Project-scoped Google OAuth |

### `client.agents.phone` / `client.phone`

| Method | Description |
|---|---|
| `pair()` | Start device pairing |
| `list_devices()` | List paired devices |
| `revoke_device(device_id)` | Revoke a device |
| `screen_tree()` | Accessibility tree from screen |
| `list_apps()` | Installed apps |
| `set_allowed_apps(package_names)` | Set agent-accessible app allowlist |
| `register_active_run(run_id)` | Register active pipeline run on phone |

### `client.platform.team` / `client.team`

| Method | Description |
|---|---|
| `list_members(project)` | List team members |
| `invite(project, username)` | Invite by username |
| `create_invite_link(project)` | Create invite link |
| `accept_invite(token)` | Accept invite |
| `update_member_role(project, user_id, role)` | Update member role |
| `remove_member(project, user_id)` | Remove team member |
| `get_pipeline_visibility(project, pipeline)` | Get pipeline visibility |
| `set_pipeline_visibility(project, pipeline, body)` | Set pipeline visibility |

### `client.platform.templates` / `client.templates`

| Method | Description |
|---|---|
| `list()` | All visible templates |
| `list_global()` | Community-visibility templates |
| `list_validated()` | Validated template IDs |
| `save(body)` | Create a template |
| `update(id, body)` | Update a template |
| `duplicate(id, new_name)` | Duplicate a template |
| `delete(id)` | Delete a template |
| `share(id, visibility)` | Set template visibility |
| `list_assignments(id)` | List template assignments |
| `assign(id, user_id, project_id)` | Assign to a user or project (exactly one; JSON body) |
| `unassign(id, user_id, project_id)` | Remove an assignment (exactly one; query params) |
| `share_targets()` | Projects available for sharing |

### `client.agents.assistant` / `client.assistant`

| Method | Description |
|---|---|
| `get_profile()` | Get assistant onboarding profile |
| `set_profile(profile)` | Save assistant profile |

### `client.agents.evals` / `client.evals`

| Method | Description |
|---|---|
| `list_runs()` | List eval runs |
| `summary()` | Aggregate eval summary |
| `run_detail(run_id)` | Detailed eval run results |
| `compare(run_ids)` | Compare multiple eval runs |
| `memory_graph()` | Memory graph visualization |
| `run_memory(run_id)` | Memory for a run |
| `crew_memory(pipeline, project)` | Agent crew memory |
| `benchmarks()` | Benchmark scores |

### `client.platform.bugs` / `client.bugs`

| Method | Description |
|---|---|
| `create(body)` | Submit a bug report |
| `list_mine()` | My bug reports |
| `get(bug_id)` | Single bug report |
| `add_comment(bug_id, comment)` | Add a comment |
| `list_notifications()` | Unread notifications |
| `mark_notifications_read()` | Mark notifications read |

### `client.trading.sim` / `client.sim`

| Method | Description |
|---|---|
| `list_accounts()` | All paper accounts |
| `balance(strategy_id, asset)` | Virtual balance |
| `positions(strategy_id)` | Open positions |
| `open_orders(strategy_id)` | Resting orders |
| `my_trades(strategy_id)` | Filled trades |
| `create_order(...)` | Place a paper order |
| `cancel_order(strategy_id, order_id, symbol, exchange)` | Cancel a paper order |

### `client.trading.strategies` / `client.strategies`

| Method | Description |
|---|---|
| `list()` | All your strategies |
| `get(id)` | Single strategy |
| `create(body)` | Launch a strategy |
| `pause(id)` | Pause |
| `resume(id)` | Resume |
| `stop(id)` | Stop + tear down |
| `delete(id)` | Soft-delete |
| `update_params(id, params)` | Update params |
| `status(id)` | Runtime status |
| `performance(id)` | Equity / PnL series |
| `executions(id)` | Order rows |
| `trades(id)` | Trade rows |
| `logs(id)` | Log rows |
| `ai_opt_start(id, body)` | Start AI optimizer |
| `ai_opt_status(id)` | Optimizer status |
| `ai_opt_approve(id, body)` | Apply optimizer result |
| `ai_opt_stop(id)` | Stop optimizer |
| `ai_opt_runs(id)` | Past optimizer runs |
| `list_team()` | Team-visible strategies |
| `summaries_bulk(ids)` | Bulk strategy summaries |

### `client.trading.backtest` / `client.backtest`

| Method | Description |
|---|---|
| `start(body)` | Start a backtest |
| `job(id)` | Poll job status |
| `results(id)` | Metrics + equity curve |
| `trades(id, limit, offset)` | Trade list |
| `sweep(parent_id, objective, limit)` | Sweep children |
| `list(limit, offset)` | Your jobs |
| `favorites(limit, offset)` | Favorited jobs |
| `funding_range(exchange, symbol)` | Earliest funding timestamp |
| `cancel(id)` | Cancel in-flight job |
| `delete(id)` | Delete job |
| `delete_all()` | Delete all non-favorited jobs |
| `optimize(body)` | Start parameter sweep (backtests router) |
| `optimize_list()` | List optimization sweep runs |
| `optimize_status(opt_run_id)` | Status/progress of an optimization sweep |
| `optimize_cancel(opt_run_id)` | Cancel optimization sweep |
| `optimize_apply(opt_run_id, body)` | Apply best params from sweep |

### `client.trading.stream` / `client.stream`

| Method | Description |
|---|---|
| `ticker(exchange, symbol, market)` | Live ticker frames |
| `orderbook(exchange, symbol, market, limit)` | Live order book |
| `ohlcv(exchange, symbol, timeframe, market)` | Live OHLCV |
| `trades(exchange, symbol, market)` | Live public trades |
| `liquidations(exchange)` | Liquidation firehose |
| `strategies()` | Private strategy events (mints ticket) |
| `private(exchange, market, api_key_id, key_id, symbol)` | Private account feed |

### `client.platform.events` / `client.events` — Socket.IO real-time

| Method | Description |
|---|---|
| `subscribe_run(run_id)` | Run push events |
| `subscribe_init_phase(run_id)` | Run init-phase events |
| `subscribe_project(project)` | Project-wide events |
| `subscribe_hitl()` | HITL approval events |
| `subscribe_pipeline_events(project)` | Pipeline CRUD events |
| `join_room(room)` | Join a custom Socket.IO room |
| `leave_run(run_id)` | Leave a run room |
| `leave_project(project)` | Leave a project room |

---

## Real-time events detail

`MelayaEvents` implements a minimal Engine.IO v4 + Socket.IO v4 client:

1. HTTP long-poll to `/api/v1/events/?EIO=4&transport=polling` — the API key
   travels in the `Authorization: Bearer` header — to obtain the Engine.IO
   session ID.
2. POST the Socket.IO connect packet `40{"token":"mk_..."}` via polling.
3. Upgrade to WebSocket (`/api/v1/events/?EIO=4&transport=websocket&sid=...`).
4. Pump frames to a tokio `UnboundedReceiver<serde_json::Value>`.

The connection is opened lazily on the first `subscribe_*` / `join_room` call —
constructing the client performs no I/O and works outside a Tokio runtime.
If the WebSocket drops, the background task reconnects with exponential
back-off (1 s → 2 s → ... → 30 s max, reset after each successful connection).
Room joins are replayed on reconnect, left rooms are not, and the task exits
once every client handle has been dropped.

## License

[Apache-2.0](LICENSE)
