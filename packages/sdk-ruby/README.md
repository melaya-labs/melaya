# melaya

> **Current production scope:** Agent Builder and Mobile Device Control are available now. Melaya Trading namespaces are preview-only and not generally available; do not use them with real funds.

Official SDK for the **[Melaya](https://melaya.org)** Agent Builder and flagship Mobile Device Control APIs. Trading namespaces are included only as a preview of a later product.

**Melaya products:** [Melaya Agents](https://melaya.org/en/product/agentic-framework) · [Melaya Assistant](https://melaya.org/en/product/assistant) · [Device Control](https://melaya.org/en/product/agentic-device-control) · [Browser Control](https://melaya.org/en/product/agentic-browser-control) · [MCP Server](https://melaya.org/en/product/mcp) · [Melaya Marketing](https://melaya.org/en/product/marketing)

- Zero runtime gem dependencies (stdlib `net/http`, `openssl`, `json` only).
- Full Agent Builder lifecycle: projects, pipelines, templates, Connectors, HITL, evals, events, billing, team, and runner management.
- Catalogs of **8,000+ scoped tools**, **100+ specialized subagents**, and **48 AI providers** (runtime catalog endpoints are the source of truth).
- Pure Ruby WebSocket client (RFC 6455) — no external gem required for streaming.

## Install

Add to your `Gemfile`:

```ruby
gem "melaya", path: "path/to/sdk-ruby"   # local checkout
```

Or once published to RubyGems:

```bash
gem install melaya
```

## Agent Builder & Device Control

### Quick start: pair a phone

```ruby
require "melaya"

melaya = Melaya::Client.new(api_key: ENV["MELAYA_API_KEY"])  # keys are prefixed mk_

pairing = melaya.agents.phone.pair
puts "Pairing code: #{pairing["code"]} (expires in #{pairing["expiresInSeconds"]}s)"
# Enter the code in the Melaya APK on the phone

devices = melaya.agents.phone.list_devices
apps    = melaya.agents.phone.list_apps

melaya.agents.phone.set_allowed_apps(["com.android.chrome"])
```

A Melaya platform key is required. "No app API required" means Device Control operates the target app through its user interface; it does not mean the Melaya SDK is unauthenticated.

### Quick start: run an agent pipeline

Configure provider credentials through Melaya Connectors first. Never include a provider key in pipeline configuration or per-run overrides.

A pipeline's run is generated **only** from `config["steps"]` — a top-level `agents` list alone produces an **empty** pipeline. Every agent a step runs must be embedded inline on that step's `"agent"` key. There is no `prompt` field: the two prompt fields are `instruction` (the task) and, optionally, `system_prompt_override`.

```ruby
melaya.agents.pipelines.create(
  name:    "mobile-review",
  project: "Operations",
  steps: [{
    "kind"  => "agent",
    "agent" => {
      "name"        => "mobile-operator",
      "role"        => "Careful mobile operator",
      "instruction" => "Read before acting. Never send, publish, or delete.",
      "model"       => { "provider" => "anthropic", "name" => "claude-sonnet-4-6" },
      "agent_tools" => [
        "phone_get_screen_tree",
        "phone_current_app",
        "phone_open_app",
        "phone_click_text",
        "phone_back",
        "phone_wait"
      ],
      "human_approval_tools" => []
    }
  }],
  maxCostUsd: 1.00
)

run    = melaya.agents.pipelines.run("mobile-review", project: "Operations")
run_id = run["run_id"]

melaya.agents.phone.register_active_run(run_id)

status = melaya.agents.pipelines.run_status("mobile-review", run_id)

# Real-time run events over Socket.IO (connects lazily on first use)
melaya.events.on_run_update(run_id) { |e| puts e["event_type"] }
```

Other config fields worth knowing: `"hitl_mode"` (`"safe"` default | `"autonomous"` | `"payments_only"` — only `"safe"` honours each agent's `human_approval_tools`), `"connector_source"` (`"personal"` | `"project"`), `"force_local_runner"`, and `"inputs"` (declared run-input fields, see `run_inputs:` below).

`get` returns an **envelope**, not a bare config — `{ "name", "client", "config", "code", "docs" }`. To edit and save, mutate `envelope["config"]` and pass that to `update`:

```ruby
envelope = melaya.pipelines.get("mobile-review", project: "Operations")
config   = envelope["config"]
config["steps"][0]["agent"]["model"] = { "provider" => "anthropic", "name" => "claude-opus-4-8" }
melaya.pipelines.update("mobile-review", config: config, project: "Operations")
```

### Quick start: run inputs and file attachments

```ruby
# Upload a file ahead of a run, then reference it by file_id
upload = melaya.pipelines.upload_run_file("mobile-review", "screenshot", File.open("shot.png", "rb"))

run = melaya.pipelines.run("mobile-review",
  project: "Operations",
  run_inputs: {
    "brief"  => "Review the attached screenshot for policy violations.",
    "values" => { "screenshot" => { "file_id" => upload["file_id"] } }
  }
)

# Download a run's own input file back (raw bytes — do not JSON-parse)
bytes = melaya.pipelines.run_input_file("mobile-review", run["run_id"], 0)
```

### Quick start: call a connector tool directly

Call any of your already-connected service tools (Gmail, Slack, Stripe, ...) —
the same surface the MCP server and the Melaya Assistant use. This is
different from `melaya.connectors`, which only stores project credentials.

Reads run immediately. Writes default to `approval: "required"`, which stages
the same approval card the Assistant raises in the Melaya app and returns
HTTP 202 (a success, not an error) with a `requestId` to poll; pass
`approval: "none"` to run a write immediately instead (still audit-logged).
Money-moving/trading tools are always refused, under both approval modes. No
method here ever accepts or returns a credential value.

```ruby
melaya.agents.connector_tools.services                      # { "services" => [...], "toolCounts" => {...} }
melaya.agents.connector_tools.search("unread email")        # discover tools by keyword
melaya.agents.connector_tools.describe("gmail_list_messages")

# Read — runs immediately
result = melaya.agents.connector_tools.call("gmail_list_messages", args: { max_results: 5 })
puts result["result"]

# Write — staged for approval by default; block until decided (or it times out)
outcome = melaya.agents.connector_tools.call_and_wait("gmail_send", args: { to: "a@b.com" })

# Also reachable via the flat alias:
melaya.connector_tools.services
```

### Quick start: diagnose an event trigger

Read, diagnose and dry-run the event triggers that start your pipelines.
Creating, editing and deleting a trigger stay in the Agent Builder and the MCP
server. No method returns a signing secret. `test` and `poll_test` are dry
runs: the action never executes.

```ruby
triggers = melaya.agents.triggers.list(project: "support")
id = triggers[0]["id"]

melaya.agents.triggers.stats(id, hours: 24)            # counts by verdict
melaya.agents.triggers.events(trigger_id: id, verdicts: %w[rejected failed], limit: 50)
melaya.agents.triggers.test(id, payload: { type: "refund.created" })  # dry run

# Poll triggers
melaya.agents.triggers.poll_status(id)
dry = melaya.agents.triggers.poll_test(id)             # what it found and would publish
puts dry["ok"] ? "#{dry["found"]} found, #{dry["wouldPublish"]} new" : dry["error"]
```

### Quick start: several accounts per connector

Field-based connectors can hold several accounts (two mailboxes, two shops).
Agents use the default account unless a tool call names another one. Only
labels and ids come back, never credential values.

```ruby
# Personal connector
melaya.credentials.accounts("shopify")   # [{ "id", "label", "isDefault", "createdAt" }, ...]
melaya.credentials.add_account("shopify",
  label: "Shop EU", fields: { "SHOPIFY_STORE" => "eu-shop", "SHOPIFY_TOKEN" => "..." },
  make_default: false)                    # the connection is tested first
melaya.credentials.set_default_account("shopify", "a1b2c3")

# Project connector (owner only for writes)
melaya.connectors.accounts("support", "shopify")
melaya.connectors.add_account("support", "shopify", label: "Shop US", fields: { "SHOPIFY_STORE" => "us-shop", "SHOPIFY_TOKEN" => "..." })
melaya.connectors.set_default_account("support", "shopify", "a1b2c3")
```

### Quick start: rotate the platform API key

`rotate_api_key` replaces the current key at once and returns the new one a
single time. If you call it with the key this client uses, every later call of
this client fails until you build a new client with the returned key.

```ruby
new_key = melaya.account.rotate_api_key["apiKey"]   # store it now: shown once
melaya = Melaya::Client.new(api_key: new_key)
melaya.account.api_key_usage
```

### Quick start: declare run inputs and test retrieval

```ruby
melaya.pipelines.set_inputs("mobile-review", project: "Operations", inputs: [
  { "key" => "topic", "label" => "Topic", "type" => "text", "required" => true }
])

# Sample query against the pipeline's retrieval store: the passages agents would get
hits = melaya.pipelines.test_retrieve("mobile-review", query: "refund policy", limit: 5)
```

## Trading quick start (preview)

```ruby
require "melaya"

melaya = Melaya::Client.new(api_key: ENV["MELAYA_API_KEY"])  # keys are prefixed mk_

# REST — normalized ticker from any of 70+ venues
t = melaya.market.ticker(exchange: "binance", symbol: "BTC/USDT", market: "spot")
puts t["last"], t["bid"], t["ask"]

# Order book
ob = melaya.market.orderbook(exchange: "bybit", symbol: "BTC/USDT", market: "spot", limit: 20)

# Candles
candles = melaya.market.ohlcv(exchange: "okx", symbol: "ETH/USDT", timeframe: "1h", limit: 200)
```

## Streaming (preview)

```ruby
# Live ticker (block form — closes when block returns)
melaya.stream.ticker(exchange: "binance", symbol: "BTC/USDT", market: "spot") do |frame|
  puts frame["last"]
  break  # close after first frame
end

# Liquidation firehose
melaya.stream.liquidations(exchange: "binance") do |ev|
  puts ev["side"], ev["notional"]
  break
end
```

## Trading (preview)

```ruby
# Account: connected exchange keys and usage
keys  = melaya.account.keys    # [{ "apiKeyId" => "BINANCEUSDM_0", "exchange" => "binanceusdm", ... }]
usage = melaya.account.usage

# Strategies — launch immediately. Paper (dry_run: true) needs no exchange key.
# SDK-launchable strategies are `custom` Rhai definitions.
result = melaya.strategies.create(
  name:          "my-bot",
  strategy_type: "custom",
  exchange:      "binanceusdm",
  symbol:        "BTC/USDT:USDT",
  market:        "FUTURES",
  dry_run:       true,
  params: {
    "language"   => "rhai",
    "definition" => 'fn evaluate() { emit_long(param("qty")); }',
    "qty"        => 0.001,
  }
)
sid = result["strategyId"]
melaya.strategies.pause(sid)
melaya.strategies.resume(sid)
trades = melaya.strategies.trades(sid)

# Paper trading (sim broker) — synthetic fills, no venue state
bal  = melaya.sim.balance(strategy_id: sid)
fill = melaya.sim.create_order(
  strategy_id: sid,
  exchange: "binanceusdm",
  symbol: "BTC/USDT:USDT",
  side: "buy",
  type: "market",
  amount: 0.001,
  market: "FUTURES"
)

# Backtest on the Rust engine
r = melaya.backtest.start(
  "strategyType" => "custom",
  "exchange"     => "binance",
  "symbol"       => "BTC/USDT",
  "timeframe"    => "1h",
  "since_ms"     => (Time.now.to_i - 90 * 86400) * 1000,
  "until_ms"     => Time.now.to_i * 1000,
  "language"     => "rhai",
  "definition"   => 'fn evaluate() { emit_long(param("qty")); }',
  "params"       => { "qty" => 0.001 }
)
job_id = r["job_id"]
loop do
  j = melaya.backtest.job(job_id)
  break if %w[done error].include?(j["status"])
  sleep 2
end
result = melaya.backtest.results(job_id)

# Private streaming (ticket minted automatically)
melaya.stream.strategies do |ev|
  puts ev["type"], ev["strategyId"]
  break
end

# Always clean up
melaya.strategies.stop(sid)
melaya.strategies.delete(sid)
```

## Authentication

Create an API key in the dashboard (**melaya.org → Settings → API Keys**). Keys are prefixed `mk_`. Every REST call sends the key **only** as an `Authorization: Bearer mk_...` header — never in the URL query string. Public WebSocket market-data streams authenticate with `?apiKey=` in the `wss://` URL (server protocol); private WebSocket streams instead use a short-lived `?wsTicket=` that the SDK mints automatically.

Public market-data and account/strategy reads work with the `mk_` key alone. **Live** order placement and live strategy launches additionally require a connected exchange key — connect one in **Settings → Connectors**, then reference it by `apiKeyId`. Paper trading and backtesting never touch a venue and need no exchange credentials.

## API surface

### Agent Builder & platform (GA)

| Area | Methods |
|---|---|
| Auth | `auth.login`, `verify_mfa`, `register`, `verify_signup`, `resend_verification`, `me`, `check`, `change_password`, `forgot_password`, `reset_password`, `mobile_handoff`, `permissions`, `refresh` |
| MFA | `auth.mfa_status`, `mfa_setup`, `mfa_confirm` (also via `melaya.platform.mfa`) |
| Projects | `projects.list`, `create`, `rename`, `runner_projects` |
| Connectors | `connectors.connected_services`, `set`, `delete`, `env_handle`, `google_oauth_start`, `apply_personal`, `shared_by`, `google_status`, `google_set_default`, `google_disconnect`, `db_test_start`, `db_test_status`, `accounts`, `add_account`, `set_default_account`, `rename_account`, `remove_account` |
| Connector Tools | `connector_tools.services`, `search`, `describe`, `test`, `connect`, `call`, `call_status`, `call_and_wait` |
| Triggers | `triggers.list`, `get`, `deliveries`, `stats`, `pending_approvals`, `test`, `events`, `poll_status`, `poll_test`, `poll_now`, `poll_sync`, `presets`, `limits`, `sources` |
| Credentials | `credentials.list`, `connected_services`, `get`, `set`, `delete`, `test`, `list_models`, `google_status`, `google_set_default`, `google_disconnect`, `accounts`, `add_account`, `set_default_account`, `identify_account`, `rename_account`, `remove_account`, `db_test_start`, `db_test_status`, `telegram_qr_start`, `telegram_qr_poll`, `whatsapp_signup_config`, `whatsapp_signup_exchange`, `tiktok_creator_info`, `substack_email_link_send`, `substack_email_link_redeem`, plus operator-profile, OAuth, and RAG helpers |
| Pipelines | `pipelines.create`, `get`, `update`, `delete_pipeline`, `list_pipelines`, `run`, `set_inputs`, `upload_run_file`, `run_inputs`, `run_input_file`, `run_active`, `run_ids`, `run_status`, `cancel_run`, `outputs`, `output`, `preview_code`, `tools`, `subagents`, `instantiate_template`, `build_with_ai` |
| Pipeline docs & RAG | `pipelines.list_docs`, `upload_doc`, `delete_doc`, `upload_retrieval_doc`, `ingest_retrieval`, `delete_retrieval_doc`, `docs_preview`, `retrieval_preview`, `test_retrieve` |
| Runs & traces | `pipelines.list`, `recent`, `count`, `traces`, `trace`, `trace_stats`, `delete_traces` |
| Tool-call audit | `pipelines.project_tool_calls`, `project_tool_call_facets`, `tool_call_detail` |
| Schedules | `pipelines.list_schedules`, `get_schedule`, `upsert_schedule`, `pause_schedule`, `resume_schedule` |
| Overview | `pipelines.overview`, `model_prices`, `chart_data`, `cost_breakdown`, `server_version` |
| Templates | `templates.list`, `list_global`, `list_validated`, `save`, `update`, `duplicate`, `delete`, `share`, `share_targets`, `list_assignments`, `assign(id, user_id:` \| `project_id:)`, `unassign(id, user_id:` \| `project_id:)` |
| Phone | `phone.pair`, `list_devices`, `revoke_device`, `screen_tree`, `list_apps`, `set_allowed_apps`, `register_active_run`, `grant_app`, `request_cast` |
| HITL | `hitl.pending`, `history`, `approve`, `reject`, `bulk_decide`, `run_tool_stats`, `run_tool_stats_by_agent`, `run_messages`, `run_tool_calls` |
| Evals | `evals.list_runs`, `summary`, `run_detail`, `compare`, `memory_graph`, `run_memory`, `crew_memory`, `edit_crew_memory_entry`, `delete_crew_memory_entry`, `benchmarks` |
| Events | `events.on_run_update`, `on_init_phase`, `on_project_event`, `on_hitl_approval`, `on_pipeline_created`, `on_pipeline_updated`, `on_pipeline_deleted`, `leave_run`, `leave_project`, `close` |
| Billing | `billing.subscription`, `create_checkout`, `create_portal`, `plans`, `credits`, `ai_credits`, `portfolio_ideas_credits`, `risk_monitoring_credits`, `ambassador_perk`, `redeem_code`, `reserved_promo` |
| Accounts | `accounts.export_data`, `remove_key`, `update_profile`, `resend_email_verification`, `verify_email` |
| Runner | `runner.create_token`, `list_tokens`, `revoke_token` |
| Team | `team.list_members`, `invite`, `create_invite_link`, `accept_invite`, `update_member_role`, `remove_member`, `transfer_ownership`, `get_pipeline_visibility`, `set_pipeline_visibility` |
| Assistant | `assistant.get_profile`, `set_profile` |
| Bugs | `bugs.create`, `list_mine`, `get`, `add_comment`, `list_notifications`, `mark_notifications_read` |

Every area is also reachable through the domain namespaces: `melaya.agents.*` (pipelines/runs, hitl, assistant, phone, evals, models, connector_tools, triggers) and `melaya.platform.*` (projects, credentials, connectors, billing, team, templates, overview, runner, auth, mfa, accounts, bugs, events).

### Trading (preview — not generally available)

| Area | Methods |
|---|---|
| Reference | `market.list_exchanges`, `catalog_counts` |
| Market data | `market.ticker`, `orderbook`, `ohlcv`, `ohlcv_multi`, `trades`, `markets`, `currencies`, `market_constraints`, `status`, `time` |
| Batch / derivatives | `market.tickers`, `funding_rates`, `funding_rate_history`, `funding_rate_history_multi`, `open_interest`, `open_interest_history`, `open_interest_history_multi`, `instruments`, `liquidation_events` |
| Prediction markets | `market.prediction_markets` (polymarket, kalshi, drift_pm, sxbet, azuro, overtime) |
| Account | `account.keys`, `usage`, `api_key_status`, `rotate_api_key`, `revoke_api_key`, `api_key_usage` |
| Strategies | `strategies.create`, `list`, `get`, `pause`, `resume`, `stop`, `delete`, `update_params`, `status`, `performance`, `executions`, `trades`, `logs` |
| AI optimizer | `strategies.ai_opt_start`, `ai_opt_status`, `ai_opt_approve`, `ai_opt_stop`, `ai_opt_runs` |
| Paper trading | `sim.balance`, `positions`, `open_orders`, `my_trades`, `create_order`, `cancel_order`, `list_accounts` |
| Backtesting | `backtest.start`, `job`, `results`, `trades`, `sweep`, `list`, `favorites`, `funding_range`, `cancel`, `delete`, `delete_all` |
| Public streaming | `stream.ticker`, `orderbook`, `ohlcv`, `trades`, `liquidations` |
| Private streaming | `stream.strategies`, `stream.private` |

Full docs: **[melaya.org/documentation](https://melaya.org/documentation)**.

## License

[Apache-2.0](../../LICENSE)
