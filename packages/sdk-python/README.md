# melaya (Python SDK)

> **Current production scope:** Agent Builder and Mobile Device Control are available now. Melaya Trading namespaces are preview-only and not generally available; do not use them with real funds.

Official SDK for the **[Melaya](https://melaya.org)** Agent Builder and flagship Mobile Device Control APIs. Trading namespaces are included only as a preview of a later product.

## Install

```bash
pip install melaya            # REST
pip install "melaya[stream]"  # REST + WebSocket streaming
```

## Quick start: Agent Builder & Device Control

Pair an Android phone, then create and run an agent pipeline that operates it. Configure provider credentials through Melaya Connectors first — never put a provider key in pipeline configuration or per-run overrides.

```python
from melaya import Melaya

m = Melaya(api_key="mk_...")  # keys are prefixed `mk_`

# 1. Pair a phone — enter the code in the Melaya APK
pairing = m.agents.phone.pair()
print(pairing["code"])

devices = m.agents.phone.list_devices()
apps = m.agents.phone.list_apps()
m.agents.phone.set_allowed_apps(["com.android.chrome"])

# 2. Create an agent pipeline with a minimal tool allowlist
m.agents.pipelines.create(
    name="mobile-review",
    project="Operations",
    model_provider="anthropic",
    model_name="claude-sonnet-4-6",
    agents=[{
        "name": "mobile-operator",
        "role": "Careful mobile operator",
        "instruction": "Read before acting. Never send, publish, or delete.",
        "agent_tools": [
            "phone_get_screen_tree", "phone_current_app", "phone_open_app",
            "phone_click_text", "phone_back", "phone_wait",
        ],
    }],
    steps=[{"kind": "agent", "agent": {"name": "mobile-operator"}}],
    maxCostUsd=1.00,
)

# 3. Run it and attach the run to the paired phone
run = m.agents.pipelines.run("mobile-review", project="Operations")
run_id = run["run_id"]
m.agents.phone.register_active_run(run_id)

status = m.agents.pipelines.run_status("mobile-review", run_id)
print(status["status"])
```

Real-time run updates arrive over Socket.IO (async):

```python
import asyncio
from melaya import Melaya

async def main():
    m = Melaya(api_key="mk_...")
    await m.events.connect()
    m.events.on_run_update(run_id, lambda e: print(e["event_type"]))
    await m.events.wait_closed()

asyncio.run(main())
```

## Market data quick start (preview)

```python
from melaya import Melaya

m = Melaya(api_key="mk_...")

# Normalized ticker from any of 70+ venues
t = m.market.ticker(exchange="binance", symbol="BTC/USDT", market="spot")
print(t["last"], t["bid"], t["ask"])

# Order book + candles
book = m.market.orderbook(exchange="bybit", symbol="BTC/USDT", market="spot", limit=20)
candles = m.market.ohlcv(exchange="okx", symbol="ETH/USDT", timeframe="1h", limit=200)
```

## Streaming (async, preview)

```python
import asyncio
from melaya import Melaya

async def main():
    m = Melaya(api_key="mk_...")
    async for t in m.stream.ticker(exchange="binance", symbol="BTC/USDT", market="spot"):
        print(t["last"])

asyncio.run(main())
```

## Trading (preview — not for real funds)

The same client covers your account, paper trading, live strategies, and backtests. Reads need only your `mk_` key; live order placement needs a connected exchange key (`m.account.keys()`).

```python
# Account
keys = m.account.keys()            # [{"apiKeyId": "BINANCEUSDM_0", "exchange": ..., "market": ...}]
usage = m.account.usage()

# Strategies — create() launches immediately. Paper (dry_run) needs no exchange key.
# SDK-launchable strategies are `custom` Rhai definitions (an `evaluate()` that
# emits signals: emit_long / emit_short / emit_close).
res = m.strategies.create(
    name="My first bot", strategy_type="custom",
    exchange="binanceusdm", symbol="BTC/USDT:USDT", market="FUTURES", dry_run=True,
    params={"language": "rhai",
            "definition": 'fn evaluate() { emit_long(param("qty")); }',
            "qty": 0.001},   # dry_run=False + api_key_id places real orders
)
sid = res["strategyId"]
m.strategies.pause(sid)
m.strategies.resume(sid)
trades = m.strategies.trades(sid)

# Paper trading (sim broker) — synthetic fills, no venue state
bal = m.sim.balance(strategy_id=sid)
fill = m.sim.create_order(strategy_id=sid, exchange="binanceusdm",
                          symbol="BTC/USDT:USDT", side="buy", type="market",
                          amount=0.001, market="FUTURES")

# Backtest on the Rust engine
import time
start = m.backtest.start({"strategyType": "custom", "exchange": "binance",
                          "symbol": "BTC/USDT", "timeframe": "1h", "language": "rhai",
                          "definition": 'fn evaluate() { emit_long(param("qty")); }',
                          "params": {"qty": 0.001}})
job_id = start["job_id"]
while m.backtest.job(job_id)["status"] not in ("done", "error"):
    time.sleep(2)
result = m.backtest.results(job_id)   # metrics, equity_curve, ohlcv

# Live private strategy feed (async; a fresh ticket is minted per connection)
async for ev in m.stream.strategies():
    print(ev["type"], ev.get("strategyId"))
```

## Authentication

Create an API key in the dashboard (**melaya.org → Settings → API Keys**). Keys are prefixed `mk_`. On REST calls the SDK sends the key **only** as an `Authorization: Bearer mk_...` header — never in a query string. Public WebSocket market-data streams pass the key as an `?apiKey=` query parameter in the `wss://` URL (server protocol); private WebSocket streams never expose the key — they use a short-lived, one-shot `?wsTicket=` minted fresh for every connection. Public market-data and account/strategy reads work with the key alone. **Live** order placement and live strategy launches additionally require a connected exchange key — connect one in **Settings → Connectors**, then reference it by `api_key_id`. Paper trading and backtesting never touch a venue and need no exchange credentials.

## API surface

### Generally available

| Area | Methods |
|---|---|
| Auth | `auth.login`, `register`, `me`, `check`, `refresh`, `verify_mfa`, `verify_signup`, `resend_verification`, `change_password`, `forgot_password`, `reset_password`, `create_mobile_handoff`, `my_permissions` |
| MFA | `mfa.status`, `setup`, `confirm` |
| Accounts | `accounts.update_profile`, `credits`, `ai_credits`, `portfolio_ideas_credits`, `risk_monitoring_credits`, `export_my_data`, `remove_key` |
| Projects | `projects.list`, `create`, `rename`, `runner_projects` |
| Connectors | `connectors.connected_services`, `set`, `delete`, `env_handle`, `google_oauth_start` |
| Credentials | `credentials.list`, `connected_services`, `get`, `set`, `delete`, `test`, `list_models`, `rag_ingest_start`, `rag_ingest_status`, `rag_retrieve_start`, `rag_retrieve_status` |
| Pipelines | `pipelines.create`, `list_pipelines`, `get`, `update`, `remove`, `run`, `run_status`, `run_ids`, `cancel_run`, `outputs`, `output`, `list`, `recent`, `traces`, `trace`, `trace_stats`, `delete_traces`, `tools`, `subagents`, `preview_code`, `build_with_ai`, `instantiate_template`, `list_schedules`, `get_schedule`, `upsert_schedule`, `pause_schedule`, `resume_schedule` |
| Templates | `templates.list`, `list_global`, `list_validated`, `save`, `update`, `duplicate`, `delete`, `share`, `list_assignments`, `assign` / `unassign` (exactly one of `user_id` or `project_id`), `share_targets` |
| Phone (Device Control) | `phone.pair`, `list_devices`, `revoke_device`, `screen_tree`, `list_apps`, `set_allowed_apps`, `register_active_run` |
| HITL | `hitl.pending`, `history`, `approve`, `reject`, `bulk_decide`, `run_messages`, `run_tool_calls`, `run_tool_stats`, `run_tool_stats_by_agent` |
| Evals | `evals.list_runs`, `summary`, `run_detail`, `compare`, `memory_graph`, `run_memory`, `crew_memory`, `benchmarks` |
| Events (real-time) | `events.connect`, `on_run_update`, `on_init_phase`, `on_project_event`, `on_hitl_approval`, `on_pipeline_created`, `on_pipeline_updated`, `on_pipeline_deleted`, `leave_run`, `leave_project`, `wait_closed`, `close` |
| Billing | `billing.subscription`, `plans`, `create_checkout`, `create_portal` |
| Runner | `runner.create_token`, `list_tokens`, `revoke_token` |
| Team | `team.list_members`, `invite`, `create_invite_link`, `accept_invite`, `update_member_role`, `remove_member`, `get_pipeline_visibility`, `set_pipeline_visibility` |
| Assistant | `assistant.get_profile`, `set_profile` |
| Bugs | `bugs.create`, `list_mine`, `get`, `add_comment`, `list_notifications`, `mark_notifications_read` |

### Trading preview (not for real funds)

| Area | Methods |
|---|---|
| Reference | `market.list_exchanges()`, `catalog_counts()` |
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
| Live trading | `trade.balance`, `positions`, `open_orders`, `orders`, `closed_orders`, `my_trades`, `my_trades_history`, `plan_orders`, `positions_history`, `leverage`, `leverage_tiers`, `create_order`, `cancel_order`, `amend_order`, `cancel_all_orders`, `cancel_plan_orders`, `close_position`, `set_leverage`, `set_margin_mode`, `set_position_mode` |

Full docs: **[melaya.org/docs](https://melaya.org/docs)**.

## License

[Apache-2.0](../../LICENSE)
