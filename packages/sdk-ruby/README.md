# melaya

> **Current production scope:** Agent Builder and Mobile Device Control are available now. Melaya Trading namespaces are preview-only and not generally available; do not use them with real funds.

Official SDK for the **[Melaya](https://melaya.org)** Agent Builder and flagship Mobile Device Control APIs. Trading namespaces are included only as a preview of a later product.

- Zero runtime gem dependencies (stdlib `net/http`, `openssl`, `json` only).
- Full Agent Builder lifecycle: projects, pipelines, templates, Connectors, HITL, evals, events, billing, team, and runner management.
- Catalogs of **1,500+ scoped tools**, **100+ specialized subagents**, and **20+ model providers** (runtime catalog endpoints are the source of truth).
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

```ruby
melaya.agents.pipelines.create(
  name:    "mobile-review",
  project: "Operations",
  model_provider: "anthropic",
  model_name:     "claude-sonnet-4-6",
  agents: [{
    "name"        => "mobile-operator",
    "role"        => "Careful mobile operator",
    "instruction" => "Read before acting. Never send, publish, or delete.",
    "agent_tools" => [
      "phone_get_screen_tree",
      "phone_current_app",
      "phone_open_app",
      "phone_click_text",
      "phone_back",
      "phone_wait"
    ]
  }],
  steps: [{ "kind" => "agent", "agent" => { "name" => "mobile-operator" } }],
  maxCostUsd: 1.00
)

run    = melaya.agents.pipelines.run("mobile-review", project: "Operations")
run_id = run["run_id"]

melaya.agents.phone.register_active_run(run_id)

status = melaya.agents.pipelines.run_status("mobile-review", run_id)

# Real-time run events over Socket.IO (connects lazily on first use)
melaya.events.on_run_update(run_id) { |e| puts e["event_type"] }
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
| Connectors | `connectors.connected_services`, `set`, `delete`, `env_handle`, `google_oauth_start` |
| Credentials | `credentials.list`, `connected_services`, `get`, `set`, `delete`, `test`, `list_models`, plus operator-profile, OAuth, and RAG helpers |
| Pipelines | `pipelines.create`, `get`, `update`, `delete_pipeline`, `list_pipelines`, `run`, `run_ids`, `run_status`, `cancel_run`, `outputs`, `output`, `preview_code`, `tools`, `subagents`, `instantiate_template`, `build_with_ai` |
| Runs & traces | `pipelines.list`, `recent`, `count`, `traces`, `trace`, `trace_stats`, `delete_traces` |
| Schedules | `pipelines.list_schedules`, `get_schedule`, `upsert_schedule`, `pause_schedule`, `resume_schedule` |
| Overview | `pipelines.overview`, `model_prices`, `chart_data`, `cost_breakdown`, `server_version` |
| Templates | `templates.list`, `list_global`, `list_validated`, `save`, `update`, `duplicate`, `delete`, `share`, `share_targets`, `list_assignments`, `assign(id, user_id:` \| `project_id:)`, `unassign(id, user_id:` \| `project_id:)` |
| Phone | `phone.pair`, `list_devices`, `revoke_device`, `screen_tree`, `list_apps`, `set_allowed_apps`, `register_active_run` |
| HITL | `hitl.pending`, `history`, `approve`, `reject`, `bulk_decide`, `run_tool_stats`, `run_tool_stats_by_agent`, `run_messages`, `run_tool_calls` |
| Evals | `evals.list_runs`, `summary`, `run_detail`, `compare`, `memory_graph`, `run_memory`, `crew_memory`, `benchmarks` |
| Events | `events.on_run_update`, `on_init_phase`, `on_project_event`, `on_hitl_approval`, `on_pipeline_created`, `on_pipeline_updated`, `on_pipeline_deleted`, `leave_run`, `leave_project`, `close` |
| Billing | `billing.subscription`, `create_checkout`, `create_portal`, `plans`, `credits`, `ai_credits`, `portfolio_ideas_credits`, `risk_monitoring_credits` |
| Accounts | `accounts.export_data`, `remove_key`, `update_profile` |
| Runner | `runner.create_token`, `list_tokens`, `revoke_token` |
| Team | `team.list_members`, `invite`, `create_invite_link`, `accept_invite`, `update_member_role`, `remove_member`, `get_pipeline_visibility`, `set_pipeline_visibility` |
| Assistant | `assistant.get_profile`, `set_profile` |
| Bugs | `bugs.create`, `list_mine`, `get`, `add_comment`, `list_notifications`, `mark_notifications_read` |

Every area is also reachable through the domain namespaces: `melaya.agents.*` (pipelines/runs, hitl, assistant, phone, evals, models) and `melaya.platform.*` (projects, credentials, connectors, billing, team, templates, overview, runner, auth, mfa, accounts, bugs, events).

### Trading (preview — not generally available)

| Area | Methods |
|---|---|
| Reference | `market.list_exchanges`, `catalog_counts` |
| Market data | `market.ticker`, `orderbook`, `ohlcv`, `ohlcv_multi`, `trades`, `markets`, `currencies`, `market_constraints`, `status`, `time` |
| Batch / derivatives | `market.tickers`, `funding_rates`, `funding_rate_history`, `funding_rate_history_multi`, `open_interest`, `open_interest_history`, `open_interest_history_multi`, `instruments`, `liquidation_events` |
| Prediction markets | `market.prediction_markets` (polymarket, kalshi, drift_pm, sxbet, azuro, overtime) |
| Account | `account.keys`, `usage`, `api_key_status` |
| Strategies | `strategies.create`, `list`, `get`, `pause`, `resume`, `stop`, `delete`, `update_params`, `status`, `performance`, `executions`, `trades`, `logs` |
| AI optimizer | `strategies.ai_opt_start`, `ai_opt_status`, `ai_opt_approve`, `ai_opt_stop`, `ai_opt_runs` |
| Paper trading | `sim.balance`, `positions`, `open_orders`, `my_trades`, `create_order`, `cancel_order`, `list_accounts` |
| Backtesting | `backtest.start`, `job`, `results`, `trades`, `sweep`, `list`, `favorites`, `funding_range`, `cancel`, `delete`, `delete_all` |
| Public streaming | `stream.ticker`, `orderbook`, `ohlcv`, `trades`, `liquidations` |
| Private streaming | `stream.strategies`, `stream.private` |

Full docs: **[melaya.org/documentation](https://melaya.org/documentation)**.

## License

[Apache-2.0](../../LICENSE)
