# melaya — Official Rust SDK

Idiomatic async Rust client for the [Melaya](https://melaya.org) unified market-data,
strategy-execution, backtesting, and WebSocket streaming API.

---

## Installation

Add to your `Cargo.toml`:

```toml
[dependencies]
melaya = "0.1"
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
Every REST call sends the key as both `?apiKey=` and `Authorization: Bearer`.

---

## Quick start

### Market data

```rust
use melaya::Melaya;

#[tokio::main]
async fn main() {
    let m = Melaya::new(&std::env::var("MK").unwrap()).unwrap();

    let exchanges = m.market.list_exchanges().await.unwrap();
    println!("Supported exchanges: {}", exchanges.as_array().unwrap().len());

    let ticker = m.market.ticker("binance", "BTC/USDT", Some("spot")).await.unwrap();
    println!("BTC/USDT last: {}", ticker["last"]);

    let candles = m.market.ohlcv("binance", "BTC/USDT", "1h", Some("spot"), Some(24)).await.unwrap();
    println!("Got {} candles", candles.as_array().unwrap().len());
}
```

### Custom (Rhai) paper strategy

```rust
use melaya::Melaya;
use serde_json::json;

#[tokio::main]
async fn main() {
    let m = Melaya::new(&std::env::var("MK").unwrap()).unwrap();

    let result = m.strategies.create(&json!({
        "name": "my-rhai-paper",
        "strategyType": "custom",
        "exchange": "binance",
        "market": "spot",
        "symbol": "BTC/USDT",
        "dryRun": true,
        "params": {
            "language": "rhai",
            "definition": r#"fn evaluate() { emit_long(param("qty")); }"#,
            "qty": 0.001
        }
    })).await.unwrap();

    let sid = result["strategyId"].as_str().unwrap();
    println!("Started paper strategy: {sid}");

    // ...do things...

    m.strategies.stop(sid).await.unwrap();
    m.strategies.delete(sid).await.unwrap();
    println!("Cleaned up.");
}
```

### Backtest

```rust
use melaya::Melaya;
use serde_json::json;
use std::time::{SystemTime, UNIX_EPOCH};

#[tokio::main]
async fn main() {
    let m = Melaya::new(&std::env::var("MK").unwrap()).unwrap();

    let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as u64;
    let week_ago = now - 7 * 24 * 3600 * 1000;

    let bt = m.backtest.start(&json!({
        "strategyType": "custom",
        "language": "rhai",
        "definition": r#"fn evaluate() { emit_long(param("qty")); }"#,
        "exchange": "binance",
        "symbol": "BTC/USDT",
        "timeframe": "1h",
        "since_ms": week_ago,
        "until_ms": now,
        "params": { "qty": 0.001 }
    })).await.unwrap();

    let job_id = bt["job_id"].as_str().unwrap();
    println!("Backtest job: {job_id}");

    // poll to completion
    loop {
        tokio::time::sleep(std::time::Duration::from_secs(1)).await;
        let j = m.backtest.job(job_id).await.unwrap();
        let status = j["status"].as_str().unwrap_or("");
        println!("  status: {status}");
        if status == "done" || status == "failed" { break; }
    }

    let results = m.backtest.results(job_id).await.unwrap();
    println!("Metrics: {}", results["metrics"]);
}
```

### WebSocket streaming

```rust
use melaya::Melaya;

#[tokio::main]
async fn main() {
    let m = Melaya::new(&std::env::var("MK").unwrap()).unwrap();

    // Public ticker stream
    let mut stream = m.stream.ticker("binance", "BTC/USDT", Some("spot")).await.unwrap();
    while let Some(frame) = stream.recv().await {
        println!("tick: {}", frame["last"]);
    }
}
```

---

## TLS note (dev boxes with intercept proxies)

Set `MELAYA_INSECURE_TLS=1` to skip certificate verification. The library is
secure by default; this flag is only for dev environments where TLS is intercepted.

---

## Method reference

### `client.market`

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
| `funding_rate_history(exchange, symbol, hours, market)` | Funding rate history (POST) |
| `open_interest(exchange, symbols, market)` | Open interest (POST) |
| `open_interest_history(exchange, symbol, hours, market)` | OI history (POST) |
| `instruments(exchange, market)` | Instrument list (POST) |
| `liquidation_events(exchange, symbol, since_ms, limit)` | Historical liquidations (POST) |
| `ohlcv_multi(exchange, symbols, timeframe, limit, market)` | Multi-symbol OHLCV (POST) |
| `market_constraints(exchange, symbol, market)` | Trading constraints (POST) |
| `funding_rate_history_multi(exchanges, symbol, hours)` | Cross-venue funding history (POST) |
| `open_interest_history_multi(exchanges, symbol, hours)` | Cross-venue OI history (POST) |
| `prediction_markets(venue)` | Prediction market listings (POST) |
| `catalog_counts()` | Platform catalog counts |

### `client.account`

| Method | Description |
|---|---|
| `keys()` | Connected exchange keys (masked) |
| `usage()` | Tier + usage counters |
| `api_key_status()` | API key status |

### `client.sim`

| Method | Description |
|---|---|
| `list_accounts()` | All paper accounts |
| `balance(strategy_id, asset)` | Virtual balance |
| `positions(strategy_id)` | Open positions |
| `open_orders(strategy_id)` | Resting orders |
| `my_trades(strategy_id)` | Filled trades |
| `create_order(...)` | Place a paper order |
| `cancel_order(strategy_id, order_id, symbol, exchange)` | Cancel a paper order |

### `client.strategies`

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

### `client.backtest`

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

### `client.stream`

| Method | Description |
|---|---|
| `ticker(exchange, symbol, market)` | Live ticker frames |
| `orderbook(exchange, symbol, market, limit)` | Live order book |
| `ohlcv(exchange, symbol, timeframe, market)` | Live OHLCV |
| `trades(exchange, symbol, market)` | Live public trades |
| `liquidations(exchange)` | Liquidation firehose |
| `strategies()` | Private strategy events (mints ticket) |
| `private(exchange, market, api_key_id, key_id, symbol)` | Private account feed (mints ticket) |
