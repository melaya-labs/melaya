//! Melaya Rust SDK -- quickstart / smoke test.
//!
//!   # in a crate that depends on `melaya`:
//!   MELAYA_API_KEY=mk_... cargo run --example rust
use melaya::Melaya;
use serde_json::json;
use std::time::Duration;
use tokio::time::timeout;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let key = std::env::var("MELAYA_API_KEY").expect("Set MELAYA_API_KEY=mk_...");
    let m = Melaya::new(&key)?;

    // 1. How many venues are live?
    let exchanges = m.market.list_exchanges().await?;
    println!("exchanges: {}", exchanges.len());

    // 2. Normalized REST ticker
    let t = m.market.ticker("binance", "BTC/USDT", Some("spot")).await?;
    println!("BTC/USDT last={:?} bid={:?} ask={:?}", t.last, t.bid, t.ask);

    // 3. Order book
    let book = m.market.orderbook("bybit", "BTC/USDT", Some("spot"), Some(5)).await?;
    println!("top bid: {:?}  top ask: {:?}", book.bids.first(), book.asks.first());

    // 4. Live stream -- print up to 3 ticker frames then stop
    let mut s = m.stream.ticker("binance", "BTC/USDT", Some("spot")).await?;
    for _ in 0..3 {
        match timeout(Duration::from_secs(10), s.recv()).await {
            Ok(Some(frame)) => println!("stream: {}", frame["last"]),
            _ => break,
        }
    }

    // 5. Account -- connected keys + tier usage
    let keys = m.account.keys().await?;
    println!("connected keys: {}", keys.len());
    let usage = m.account.usage().await?;
    println!("tier: {}", usage["tier"]);

    // 6. Paper trading -- launch a paper strategy (no exchange key needed) and
    //    round-trip a synthetic order through the sim broker. Nothing hits a venue.
    let created = m.strategies.create(&json!({
        "name": "SDK example (paper)",
        "strategyType": "custom",            // custom Rhai definition
        "exchange": "binanceusdm", "symbol": "BTC/USDT:USDT", "market": "FUTURES",
        "dryRun": true,                       // dryRun:false + apiKeyId => REAL orders
        "params": { "language": "rhai", "definition": "fn evaluate() { emit_long(param("qty")); }", "qty": 0.001 }
    })).await?;
    let sid = created["strategyId"].as_str().unwrap();
    println!("launched paper strategy {sid}");
    let fill = m.sim.create_order(sid, "binanceusdm", "BTC/USDT:USDT", "buy", 0.001,
        Some("market"), None, Some("FUTURES"), None, None, None, None, None).await?;
    println!("paper fill @ {}", fill["fill_price"]);
    println!("paper balance: {:?}", m.sim.balance(sid, None).await?);
    m.strategies.stop(sid).await?;

    // 7. Backtest on the Rust engine
    let bt = m.backtest.start(&json!({
        "strategyType": "custom", "exchange": "binance", "symbol": "BTC/USDT", "timeframe": "1h",
        "language": "rhai", "definition": "fn evaluate() { emit_long(param("qty")); }", "params": { "qty": 0.001 }
    })).await?;
    println!("backtest job {} started", bt["job_id"]);
    println!("done");
    Ok(())
}
