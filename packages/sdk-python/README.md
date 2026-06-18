# melaya (Python SDK)

Official Python SDK for the **[Melaya](https://melaya.org)** trading platform — normalized market data, paper + live trading, backtesting, and an AI agentic trading crew across **70+ venues**, powered by an in-house Rust engine.

## Install

```bash
pip install melaya            # REST
pip install "melaya[stream]"  # REST + WebSocket streaming
```

## Quick start

```python
from melaya import Melaya

m = Melaya(api_key="mk_...")  # keys are prefixed `mk_`

# Normalized ticker from any of 70+ venues
t = m.market.ticker(exchange="binance", symbol="BTC/USDT", market="spot")
print(t["last"], t["bid"], t["ask"])

# Order book + candles
book = m.market.orderbook(exchange="bybit", symbol="BTC/USDT", market="spot", limit=20)
candles = m.market.ohlcv(exchange="okx", symbol="ETH/USDT", timeframe="1h", limit=200)
```

## Streaming (async)

```python
import asyncio
from melaya import Melaya

async def main():
    m = Melaya(api_key="mk_...")
    async for t in m.stream.ticker(exchange="binance", symbol="BTC/USDT", market="spot"):
        print(t["last"])

asyncio.run(main())
```

## Trading

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

# Live private strategy feed (async; ticket minted automatically)
async for ev in m.stream.strategies():
    print(ev["type"], ev.get("strategyId"))
```

## Authentication

Create an API key in the dashboard (**melaya.org → Settings → API Keys**). Keys are prefixed `mk_`; the SDK sends it on every REST call and WebSocket connection. Public market-data and account/strategy reads work with the key alone. **Live** order placement and live strategy launches additionally require a connected exchange key — connect one in **Settings → Connectors**, then reference it by `api_key_id`. Paper trading and backtesting never touch a venue and need no exchange credentials.

## API surface

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
