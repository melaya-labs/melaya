# melaya

Official Ruby SDK for the **[Melaya](https://melaya.org)** trading platform — normalized market data, paper + live trading, backtesting, and an AI agentic trading crew across **70+ venues**, powered by an in-house Rust engine.

- Zero runtime gem dependencies (stdlib `net/http`, `openssl`, `json` only).
- Pure Ruby WebSocket client (RFC 6455) — no external gem required for streaming.
- Full market data, strategies, sim trading, backtesting, and streaming from one client.

## Install

Add to your `Gemfile`:

```ruby
gem "melaya", path: "path/to/sdk-ruby"   # local checkout
```

Or once published to RubyGems:

```bash
gem install melaya
```

## Quick start

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

## Streaming

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

## Trading

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

Create an API key in the dashboard (**melaya.org → Settings → API Keys**). Keys are prefixed `mk_`; the SDK sends it automatically on every REST call and WebSocket connection.

Public market-data and account/strategy reads work with the `mk_` key alone. **Live** order placement and live strategy launches additionally require a connected exchange key — connect one in **Settings → Connectors**, then reference it by `apiKeyId`. Paper trading and backtesting never touch a venue and need no exchange credentials.

## TLS verification

The SDK verifies TLS certificates by default. To disable on a dev box with TLS interception:

```bash
MELAYA_INSECURE_TLS=1 ruby your_script.rb
```

Or pass `verify_ssl: false` to the constructor. **Never disable TLS in production.**

## API surface

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

Full docs: **[melaya.org/docs](https://melaya.org/docs)**.

## License

[Apache-2.0](../../LICENSE)
