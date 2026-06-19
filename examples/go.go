// Melaya Go SDK -- quickstart / smoke test.
//
//   go get github.com/melaya-labs/melaya/packages/sdk-go/melaya
//   MELAYA_API_KEY=mk_... go run examples/go.go
package main

import (
	"context"
	"fmt"
	"os"
	"strings"
	"time"

	melaya "github.com/melaya-labs/melaya/packages/sdk-go/melaya"
)

func i64(v int64) *int64 { return &v }

func main() {
	apiKey := os.Getenv("MELAYA_API_KEY")
	if apiKey == "" {
		fmt.Fprintln(os.Stderr, "Set MELAYA_API_KEY=mk_...")
		os.Exit(1)
	}
	m, err := melaya.New(apiKey)
	if err != nil {
		panic(err)
	}
	ctx := context.Background()

	// 1. How many venues are live?
	ex, _ := m.Market.ListExchanges(ctx)
	fmt.Printf("exchanges: %d\n", len(ex))

	// 2. Normalized REST ticker
	t, _ := m.Market.Ticker(ctx, melaya.SymbolQuery{Exchange: "binance", Symbol: "BTC/USDT", Market: "spot"})
	fmt.Printf("BTC/USDT  last=%v bid=%v ask=%v\n", t.Last, t.Bid, t.Ask)

	// 3. Order book
	book, _ := m.Market.Orderbook(ctx, melaya.OrderBookQuery{
		SymbolQuery: melaya.SymbolQuery{Exchange: "bybit", Symbol: "BTC/USDT", Market: "spot"}, Limit: 5})
	fmt.Println("top bid:", book.Bids[0], "top ask:", book.Asks[0])

	// 4. Live stream -- print 3 ticker frames then stop
	s, _ := m.Stream.Ticker("binance", "BTC/USDT", "spot")
	for i := 0; i < 3; i++ {
		fmt.Println("stream:", <-s.Ch)
	}
	s.Close()

	// 5. Account -- connected keys + tier usage
	keys, _ := m.Account.Keys(ctx)
	ids := make([]string, 0, len(keys))
	for _, k := range keys {
		ids = append(ids, k.APIKeyID)
	}
	fmt.Println("connected keys:", strings.Join(ids, ", "))
	usage, _ := m.Account.Usage(ctx)
	fmt.Println("tier:", usage.Tier)

	// 6. Paper trading -- launch a paper strategy (no exchange key needed) and
	//    round-trip a synthetic order through the sim broker. Nothing hits a venue.
	created, _ := m.Strategies.Create(ctx, melaya.StrategyCreate{
		Name: "SDK example (paper)", StrategyType: "custom", // custom Rhai definition
		Exchange: "binanceusdm", Symbol: "BTC/USDT:USDT", Market: "FUTURES",
		DryRun: true, // DryRun:false + APIKeyID would place REAL orders
		Params: map[string]interface{}{"language": "rhai", "definition": ` + "`" + RHAI + "`" + r`, "qty": 0.001},
	})
	sid := created.StrategyID
	fmt.Printf("launched paper strategy %s\n", sid)
	fill, _ := m.Sim.CreateOrder(ctx, melaya.SimCreateOrder{
		StrategyID: sid, Exchange: "binanceusdm", Symbol: "BTC/USDT:USDT",
		Side: "buy", Type: "market", Amount: 0.001, Market: "FUTURES"})
	fmt.Printf("paper fill @ %v (order %v)\n", fill.FillPrice, fill.OrderID)
	bal, _ := m.Sim.Balance(ctx, sid, "")
	fmt.Println("paper balance:", bal.Total)
	m.Strategies.Stop(ctx, sid)

	// 7. Backtest on the Rust engine
	now := time.Now().UnixMilli()
	bt, _ := m.Backtest.Start(ctx, melaya.BacktestStart{
		StrategyType: "custom", Exchange: "binance", Symbol: "BTC/USDT", Timeframe: "1h",
		SinceMS: i64(now - 90*86400000), UntilMS: i64(now),
		Language: "rhai", Definition: `fn evaluate() { emit_long(param("qty")); }`, Params: map[string]interface{}{"qty": 0.001},
	})
	fmt.Printf("backtest job %v started\n", bt["job_id"])
	fmt.Println("done")
}
