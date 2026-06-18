# melaya-go

Official Go SDK for the [Melaya](https://melaya.org) unified market-data and trading API.

## Install

```sh
go get github.com/melaya-labs/melaya-go
```

Requires Go 1.22+. The only external dependency is `github.com/gorilla/websocket` for WebSocket streaming.

## Quick start

```go
package main

import (
    "context"
    "fmt"
    "log"

    melaya "github.com/melaya-labs/melaya-go/melaya"
)

func main() {
    m, err := melaya.New("mk_YOUR_KEY_HERE")
    if err != nil { log.Fatal(err) }

    ctx := context.Background()

    // Market data
    t, err := m.Market.Ticker(ctx, melaya.SymbolQuery{
        Exchange: "binance", Symbol: "BTC/USDT", Market: "spot",
    })
    if err != nil { log.Fatal(err) }
    fmt.Printf("BTC/USDT last: %v\n", *t.Last)

    // Launch a custom paper strategy
    result, err := m.Strategies.Create(ctx, melaya.StrategyCreate{
        Name:         "my-rhai-strategy",
        StrategyType: "custom",
        Exchange:     "binance",
        Symbol:       "BTC/USDT",
        Market:       "spot",
        DryRun:       true,  // paper mode — no real orders
        Params: map[string]interface{}{
            "language":   "rhai",
            "definition": `fn evaluate() { emit_long(param("qty")); }`,
            "qty":        0.001,
        },
    })
    if err != nil { log.Fatal(err) }
    fmt.Printf("Strategy created: %s\n", result.StrategyID)

    // Clean up
    m.Strategies.Stop(ctx, result.StrategyID)
    m.Strategies.Delete(ctx, result.StrategyID)

    // Run a backtest
    since := int64(1_700_000_000_000) // ms
    until := int64(1_700_604_800_000) // ms (~7 days later)
    bt, err := m.Backtest.Start(ctx, melaya.BacktestStart{
        StrategyType: "custom",
        Exchange:     "binance",
        Symbol:       "BTC/USDT",
        Timeframe:    "1h",
        SinceMS:      &since,
        UntilMS:      &until,
        Language:     "rhai",
        Definition:   `fn evaluate() { emit_long(param("qty")); }`,
        Params:       map[string]interface{}{"qty": 0.001},
    })
    if err != nil { log.Fatal(err) }
    fmt.Printf("Backtest job: %v\n", bt["job_id"])
}
```

## Auth

API keys are created at **melaya.org → Settings → API Keys**. Keys must be prefixed `mk_`.

Pass the key to `melaya.New(...)` — the SDK injects it as both:
- Query param: `?apiKey=mk_...`
- Header: `Authorization: Bearer mk_...`

Never hard-code keys in source files. Use environment variables:

```go
m, _ := melaya.New(os.Getenv("MELAYA_API_KEY"))
```

## TLS (dev boxes)

Set `MELAYA_INSECURE_TLS=1` to disable certificate verification. The SDK is secure by default; only enable this for local dev/test environments where TLS is intercepted.

## Method table

### Market (`m.Market.*`)

| Method | Description |
|---|---|
| `ListExchanges(ctx)` | All supported exchanges |
| `Ticker(ctx, SymbolQuery)` | Best bid/ask + 24h aggregates |
| `Orderbook(ctx, OrderBookQuery)` | Order book to a given depth |
| `Ohlcv(ctx, OhlcvQuery)` | OHLCV candles |
| `Trades(ctx, SymbolQuery)` | Recent public trades |
| `Markets(ctx, ExchangeQuery)` | Tradable markets on a venue |
| `Currencies(ctx, ExchangeQuery)` | Listed currencies |
| `Status(ctx, ExchangeQuery)` | Operational status |
| `Time(ctx, ExchangeQuery)` | Exchange server time |
| `Tickers(ctx, body)` | Multi-symbol tickers (POST) |
| `FundingRates(ctx, body)` | Latest funding rates |
| `FundingRateHistory(ctx, body)` | Funding-rate history |
| `OpenInterest(ctx, body)` | Open interest |
| `OpenInterestHistory(ctx, body)` | Open-interest history |
| `Instruments(ctx, body)` | Instruments + constraints |
| `LiquidationEvents(ctx, body)` | Historical liquidation events |
| `OhlcvMulti(ctx, body)` | Multi-symbol OHLCV |
| `MarketConstraints(ctx, body)` | Trading constraints for one symbol |
| `FundingRateHistoryMulti(ctx, body)` | Funding rate history across venues |
| `OpenInterestHistoryMulti(ctx, body)` | Open interest history across venues |
| `PredictionMarkets(ctx, body)` | Prediction market listings |
| `CatalogCounts(ctx)` | Platform catalog counts |

### Account (`m.Account.*`)

| Method | Description |
|---|---|
| `Keys(ctx)` | Connected exchange API keys |
| `Usage(ctx)` | Tier + live usage counters |
| `APIKeyStatus(ctx)` | Platform key status |

### Sim (`m.Sim.*`)

| Method | Description |
|---|---|
| `ListAccounts(ctx)` | Paper accounts |
| `Balance(ctx, strategyID, asset)` | Virtual balance |
| `Positions(ctx, strategyID)` | Open paper positions |
| `OpenOrders(ctx, strategyID)` | Resting paper orders |
| `MyTrades(ctx, strategyID)` | Filled paper trades |
| `CreateOrder(ctx, SimCreateOrder)` | Place a paper order |
| `CancelOrder(ctx, strategyID, orderID, symbol, exchange)` | Cancel a paper order |

### Strategies (`m.Strategies.*`)

| Method | Description |
|---|---|
| `List(ctx)` | All your strategies |
| `Get(ctx, strategyID)` | Single strategy by id |
| `Create(ctx, StrategyCreate)` | Launch a strategy |
| `Pause(ctx, strategyID)` | Pause |
| `Resume(ctx, strategyID)` | Resume |
| `Stop(ctx, strategyID)` | Stop and tear down |
| `Delete(ctx, strategyID)` | Soft-delete |
| `UpdateParams(ctx, strategyID, params)` | Update running params |
| `Status(ctx, strategyID)` | Runtime status |
| `Performance(ctx, strategyID)` | Equity / PnL series |
| `Executions(ctx, strategyID)` | Order rows |
| `Trades(ctx, strategyID)` | Fill rows |
| `Logs(ctx, strategyID)` | Log rows |
| `AIOptStart(ctx, strategyID, body)` | Start AI optimizer |
| `AIOptStatus(ctx, strategyID)` | Optimizer status |
| `AIOptApprove(ctx, strategyID, body)` | Apply proposed params |
| `AIOptStop(ctx, strategyID)` | Stop optimizer |
| `AIOptRuns(ctx, strategyID)` | Past optimizer runs |

### Backtest (`m.Backtest.*`)

| Method | Description |
|---|---|
| `Start(ctx, BacktestStart)` | Start a backtest |
| `Job(ctx, jobID)` | Job status + progress |
| `Results(ctx, jobID)` | Metrics + equity curve |
| `Trades(ctx, jobID, limit, offset)` | Trade list |
| `Sweep(ctx, parentID, objective, limit)` | Sweep children |
| `List(ctx, limit, offset)` | Your jobs |
| `Favorites(ctx, limit, offset)` | Favorited jobs |
| `FundingRange(ctx, exchange, symbol)` | Earliest funding timestamp |
| `Cancel(ctx, jobID)` | Cancel in-flight job |
| `Delete(ctx, jobID)` | Delete one job |
| `DeleteAll(ctx)` | Delete all non-favorited jobs |

### Stream (`m.Stream.*`)

| Method | Description |
|---|---|
| `Ticker(exchange, symbol, market)` | Live ticker frames |
| `Orderbook(exchange, symbol, market, limit)` | Live order-book frames |
| `Ohlcv(exchange, symbol, timeframe, market)` | Live OHLCV frames |
| `Trades(exchange, symbol, market)` | Live public-trade frames |
| `Liquidations(exchange)` | Liquidation firehose |
| `Strategies()` | Private: strategy events |
| `Private(exchange, market, apiKeyID, keyID, symbol)` | Private: account feed |

Each stream method returns a `*Stream` with a `Ch <-chan Frame` channel and a `Close()` method.

```go
s, err := m.Stream.Ticker("binance", "BTC/USDT", "spot")
if err != nil { log.Fatal(err) }
defer s.Close()
for frame := range s.Ch {
    fmt.Println(frame["last"])
}
```

## License

MIT
