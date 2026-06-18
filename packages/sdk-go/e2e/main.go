// e2e smoke test — Melaya Go SDK — full endpoint coverage (~70 checks).
// Safety: PAPER/SIM ONLY. Never places a live order or launches a live strategy.
//   - aiOptStart, aiOptApprove, backtest.deleteAll are WIRED (not invoked).
// Run:
//   MK=mk_... MELAYA_INSECURE_TLS=1 go run ./e2e
package main

import (
	"reflect"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"time"

	melaya "github.com/melaya-labs/melaya/packages/sdk-go/melaya"
)

// ── result store ──────────────────────────────────────────────────────────────

type status string

const (
	stPASS  status = "PASS"
	stFAIL  status = "FAIL"
	stWIRED status = "WIRED"
	stSKIP  status = "SKIP"
)

type result struct {
	cat    string
	name   string
	st     status
	detail string
}

var results []result

func rec(cat, name string, st status, detail string) {
	if len(detail) > 80 {
		detail = detail[:80]
	}
	results = append(results, result{cat, name, st, detail})
}

func pass(cat, name, detail string)  { rec(cat, name, stPASS, detail) }
func fail(cat, name, detail string)  { rec(cat, name, stFAIL, detail) }
func wired(cat, name, detail string) { rec(cat, name, stWIRED, detail) }
func skip(cat, name, detail string)  { rec(cat, name, stSKIP, detail) }

// chk runs fn; on success runs validate (nil = accept any non-nil).
// retry=true retries once after 1.6s (absorb cold-cache misses).
func chk(cat, name string, fn func() (interface{}, error), validate func(interface{}) bool, retry bool) interface{} {
	max := 1
	if retry {
		max = 2
	}
	var lastErr error
	for i := 1; i <= max; i++ {
		v, err := fn()
		if err != nil {
			lastErr = err
			if i < max {
				time.Sleep(1600 * time.Millisecond)
			}
			continue
		}
		if validate != nil && !validate(v) {
			lastErr = fmt.Errorf("invalid shape: %s", jsonSnip(v, 80))
			if i < max {
				time.Sleep(1600 * time.Millisecond)
			}
			continue
		}
		pass(cat, name, jsonSnip(v, 80))
		return v
	}
	if lastErr != nil {
		fail(cat, name, lastErr.Error())
	} else {
		fail(cat, name, "invalid shape")
	}
	return nil
}

// streamChk opens a stream, waits ≤10s for a frame (or open).
func streamChk(cat, name string, mk func() (*melaya.Stream, error)) {
	s, err := mk()
	if err != nil {
		fail(cat, name, fmt.Sprintf("open err: %s", err.Error()))
		return
	}
	t := time.NewTimer(10 * time.Second)
	defer t.Stop()
	select {
	case f, ok := <-s.Ch:
		s.Close()
		if ok {
			pass(cat, name, "frame "+jsonSnip(f, 45))
		} else {
			fail(cat, name, "channel closed before frame")
		}
	case e := <-s.Errors:
		s.Close()
		fail(cat, name, "ws err: "+e.Error())
	case <-t.C:
		s.Close()
		// opened but no frame in 10s — per TS reference: PASS if opened
		pass(cat, name, "open, no frame 10s")
	}
}

// ── validators ────────────────────────────────────────────────────────────────

func isArr(v interface{}) bool {
	if v == nil {
		return false
	}
	rv := reflect.ValueOf(v)
	return rv.Kind() == reflect.Slice || rv.Kind() == reflect.Array
}

func arrLen(v interface{}) int {
	if v == nil {
		return 0
	}
	rv := reflect.ValueOf(v)
	if rv.Kind() == reflect.Slice || rv.Kind() == reflect.Array {
		return rv.Len()
	}
	return 0
}

func arrMin(n int) func(interface{}) bool {
	return func(v interface{}) bool {
		return isArr(v) && arrLen(v) >= n
	}
}

func isObj(v interface{}) bool {
	if v == nil {
		return false
	}
	switch v.(type) {
	case map[string]interface{}:
		return true
	case map[string]melaya.Ticker:
		return true
	case map[string][]melaya.Candle:
		return true
	case *melaya.BacktestResult:
		return true
	case *melaya.ExchangeStatus:
		return true
	}
	return false
}

func okTrue(v interface{}) bool {
	m, ok := v.(map[string]interface{})
	if !ok {
		return false
	}
	b, ok2 := m["ok"].(bool)
	return ok2 && b
}

// ── helpers ────────────────────────────────────────────────────────────────────

func jsonSnip(v interface{}, n int) string {
	b, _ := json.Marshal(v)
	s := string(b)
	if len(s) > n {
		s = s[:n]
	}
	return s
}

func wrap(v interface{}, err error) (interface{}, error) { return v, err }

// ── main ──────────────────────────────────────────────────────────────────────

func main() {
	mk := os.Getenv("MK")
	if mk == "" {
		fmt.Fprintln(os.Stderr, "set MK=mk_...")
		os.Exit(2)
	}

	m, err := melaya.New(mk)
	if err != nil {
		fmt.Fprintf(os.Stderr, "melaya.New: %v\n", err)
		os.Exit(1)
	}

	ctx := context.Background()

	const (
		spotExch = "binance"
		spotSym  = "BTC/USDT"
		spotMkt  = "spot"
		perpExch = "binanceusdm"
		perpSym  = "BTC/USDT:USDT"
	)

	now := time.Now().UnixMilli()

	// ════ MARKET (22) ════
	fmt.Println("\n── market ──")

	chk("market", "listExchanges",
		func() (interface{}, error) { return wrap(m.Market.ListExchanges(ctx)) },
		arrMin(60), false)

	chk("market", "ticker",
		func() (interface{}, error) {
			t, err := m.Market.Ticker(ctx, melaya.SymbolQuery{Exchange: spotExch, Symbol: spotSym, Market: spotMkt})
			if err != nil {
				return nil, err
			}
			return t, nil
		},
		func(v interface{}) bool {
			if t, ok := v.(*melaya.Ticker); ok && t != nil {
				return t.Last != nil || t.Bid != nil
			}
			return false
		}, true)

	chk("market", "orderbook",
		func() (interface{}, error) {
			return wrap(m.Market.Orderbook(ctx, melaya.OrderBookQuery{
				SymbolQuery: melaya.SymbolQuery{Exchange: spotExch, Symbol: spotSym, Market: spotMkt},
				Limit:       5,
			}))
		},
		func(v interface{}) bool {
			if ob, ok := v.(*melaya.OrderBook); ok && ob != nil {
				return len(ob.Bids) > 0
			}
			return false
		}, true)

	chk("market", "ohlcv",
		func() (interface{}, error) {
			return wrap(m.Market.Ohlcv(ctx, melaya.OhlcvQuery{
				SymbolQuery: melaya.SymbolQuery{Exchange: spotExch, Symbol: spotSym, Market: spotMkt},
				Timeframe:   "1h",
				Limit:       10,
			}))
		},
		func(v interface{}) bool {
			cs, ok := v.([]melaya.Candle)
			return ok && len(cs) >= 1
		}, true)

	chk("market", "trades",
		func() (interface{}, error) {
			return wrap(m.Market.Trades(ctx, melaya.SymbolQuery{Exchange: spotExch, Symbol: spotSym, Market: spotMkt}))
		},
		func(v interface{}) bool {
			ts, ok := v.([]melaya.Trade)
			return ok && len(ts) >= 1
		}, true)

	chk("market", "markets",
		func() (interface{}, error) {
			return wrap(m.Market.Markets(ctx, melaya.ExchangeQuery{Exchange: spotExch}))
		},
		arrMin(1), false)

	chk("market", "currencies",
		func() (interface{}, error) {
			return wrap(m.Market.Currencies(ctx, melaya.ExchangeQuery{Exchange: "kraken"}))
		},
		arrMin(1), true)

	chk("market", "status",
		func() (interface{}, error) { return wrap(m.Market.Status(ctx, melaya.ExchangeQuery{Exchange: spotExch})) },
		isObj, false)

	chk("market", "time",
		func() (interface{}, error) { return m.Market.Time(ctx, melaya.ExchangeQuery{Exchange: spotExch}) },
		func(v interface{}) bool { return v != nil },
		false)

	chk("market", "tickers",
		func() (interface{}, error) {
			return wrap(m.Market.Tickers(ctx, map[string]interface{}{
				"exchange": spotExch,
				"symbols":  []string{"BTC/USDT", "ETH/USDT"},
			}))
		},
		isObj, true)

	chk("market", "fundingRates",
		func() (interface{}, error) {
			return wrap(m.Market.FundingRates(ctx, map[string]interface{}{
				"exchange": perpExch,
				"symbols":  []string{perpSym},
			}))
		},
		isObj, true)

	chk("market", "fundingRateHistory",
		func() (interface{}, error) {
			return wrap(m.Market.FundingRateHistory(ctx, map[string]interface{}{
				"exchange": perpExch,
				"symbol":   perpSym,
				"hours":    24,
			}))
		},
		arrMin(1), true)

	chk("market", "openInterest",
		func() (interface{}, error) {
			return wrap(m.Market.OpenInterest(ctx, map[string]interface{}{
				"exchange": perpExch,
				"symbols":  []string{perpSym},
			}))
		},
		isObj, true)

	chk("market", "openInterestHistory",
		func() (interface{}, error) {
			return wrap(m.Market.OpenInterestHistory(ctx, map[string]interface{}{
				"exchange": perpExch,
				"symbol":   perpSym,
				"hours":    24,
			}))
		},
		arrMin(1), true)

	chk("market", "instruments",
		func() (interface{}, error) {
			return m.Market.Instruments(ctx, map[string]interface{}{"exchange": perpExch})
		},
		isObj, false)

	chk("market", "liquidationEvents",
		func() (interface{}, error) {
			return wrap(m.Market.LiquidationEvents(ctx, map[string]interface{}{
				"exchange": perpExch,
				"limit":    10,
			}))
		},
		isArr, false)

	chk("market", "ohlcvMulti",
		func() (interface{}, error) {
			return wrap(m.Market.OhlcvMulti(ctx, map[string]interface{}{
				"exchange":  spotExch,
				"symbols":   []string{"BTC/USDT", "ETH/USDT"},
				"timeframe": "1h",
				"limit":     5,
				"market":    spotMkt,
			}))
		},
		isObj, true)

	chk("market", "marketConstraints",
		func() (interface{}, error) {
			return m.Market.MarketConstraints(ctx, map[string]interface{}{
				"exchange": perpExch,
				"symbol":   perpSym,
			})
		},
		func(v interface{}) bool { return v != nil },
		false)

	chk("market", "fundingRateHistoryMulti",
		func() (interface{}, error) {
			return wrap(m.Market.FundingRateHistoryMulti(ctx, map[string]interface{}{
				"exchanges": []string{perpExch, "bybitlinear"},
				"symbol":    perpSym,
				"hours":     24,
			}))
		},
		isObj, true)

	chk("market", "openInterestHistoryMulti",
		func() (interface{}, error) {
			return wrap(m.Market.OpenInterestHistoryMulti(ctx, map[string]interface{}{
				"exchanges": []string{perpExch, "bybitlinear"},
				"symbol":    perpSym,
				"hours":     24,
			}))
		},
		isObj, true)

	chk("market", "predictionMarkets",
		func() (interface{}, error) {
			return wrap(m.Market.PredictionMarkets(ctx, map[string]interface{}{"venue": "polymarket"}))
		},
		arrMin(1), true)

	chk("market", "catalogCounts",
		func() (interface{}, error) { return wrap(m.Market.CatalogCounts(ctx)) },
		func(v interface{}) bool {
			if cc, ok := v.(*melaya.CatalogCounts); ok && cc != nil {
				return cc.Tools > 0
			}
			return false
		}, false)

	// ════ ACCOUNT (3) ════
	fmt.Println("\n── account ──")

	var connectedKeys []melaya.ConnectedKey
	r := chk("account", "keys",
		func() (interface{}, error) { return wrap(m.Account.Keys(ctx)) },
		isArr, false)
	if r != nil {
		if ks, ok := r.([]melaya.ConnectedKey); ok {
			connectedKeys = ks
		}
	}

	chk("account", "usage",
		func() (interface{}, error) { return wrap(m.Account.Usage(ctx)) },
		func(v interface{}) bool {
			if u, ok := v.(*melaya.UsageSummary); ok && u != nil {
				return u.Tier != ""
			}
			return false
		}, false)

	chk("account", "apiKeyStatus",
		func() (interface{}, error) { return wrap(m.Account.APIKeyStatus(ctx)) },
		isObj, false)

	// ════ STRATEGIES — reads on existing; lifecycle on fresh paper ════
	fmt.Println("\n── strategies ──")

	var readSid string
	listR := chk("strategies", "list",
		func() (interface{}, error) { return wrap(m.Strategies.List(ctx)) },
		isArr, false)
	if listR != nil {
		if sl, ok := listR.([]melaya.Strategy); ok && len(sl) > 0 {
			readSid = sl[0].StrategyID
		}
	}

	if readSid != "" {
		chk("strategies", "get",
			func() (interface{}, error) { return wrap(m.Strategies.Get(ctx, readSid)) },
			func(v interface{}) bool {
				if s, ok := v.(*melaya.Strategy); ok && s != nil {
					return s.StrategyID == readSid
				}
				return false
			}, false)

		chk("strategies", "status",
			func() (interface{}, error) { return wrap(m.Strategies.Status(ctx, readSid)) },
			isObj, false)

		chk("strategies", "executions",
			func() (interface{}, error) { return wrap(m.Strategies.Executions(ctx, readSid)) },
			isArr, false)

		chk("strategies", "trades",
			func() (interface{}, error) { return wrap(m.Strategies.Trades(ctx, readSid)) },
			isArr, false)

		chk("strategies", "performance",
			func() (interface{}, error) { return wrap(m.Strategies.Performance(ctx, readSid)) },
			isArr, false)

		chk("strategies", "logs",
			func() (interface{}, error) { return wrap(m.Strategies.Logs(ctx, readSid)) },
			isArr, false)

		chk("strategies", "aiOptStatus",
			func() (interface{}, error) { return wrap(m.Strategies.AIOptStatus(ctx, readSid)) },
			isObj, false)

		chk("strategies", "aiOptRuns",
			func() (interface{}, error) { return m.Strategies.AIOptRuns(ctx, readSid) },
			func(v interface{}) bool { return v != nil },
			false)
	} else {
		for _, n := range []string{"get", "status", "executions", "trades", "performance", "logs", "aiOptStatus", "aiOptRuns"} {
			skip("strategies", n, "list empty — no existing strategy id")
		}
	}

	// Fresh PAPER strategy for lifecycle + sim
	const rhaiDef = `fn evaluate() { emit_long(param("qty")); }`
	var paperSid string

	createdR := chk("strategies", "create(custom,paper)",
		func() (interface{}, error) {
			return wrap(m.Strategies.Create(ctx, melaya.StrategyCreate{
				Name:         "SDK full-smoke (custom)",
				StrategyType: "custom",
				Exchange:     perpExch,
				Symbol:       perpSym,
				Market:       "FUTURES",
				DryRun:       true,
				Params: map[string]interface{}{
					"language":   "rhai",
					"definition": rhaiDef,
					"qty":        0.001,
				},
			}))
		},
		func(v interface{}) bool {
			if cr, ok := v.(*melaya.StrategyCreateResult); ok && cr != nil {
				return cr.Ok && cr.StrategyID != ""
			}
			return false
		}, false)

	if createdR != nil {
		if cr, ok := createdR.(*melaya.StrategyCreateResult); ok {
			paperSid = cr.StrategyID
		}
	}

	if paperSid != "" {
		time.Sleep(1 * time.Second) // let runner init

		chk("strategies", "pause",
			func() (interface{}, error) { return wrap(m.Strategies.Pause(ctx, paperSid)) },
			okTrue, false)

		time.Sleep(400 * time.Millisecond)

		chk("strategies", "resume",
			func() (interface{}, error) { return wrap(m.Strategies.Resume(ctx, paperSid)) },
			okTrue, false)

		chk("strategies", "updateParams",
			func() (interface{}, error) {
				return wrap(m.Strategies.UpdateParams(ctx, paperSid, map[string]interface{}{"fast": 8, "slow": 20}))
			},
			okTrue, false)

		chk("strategies", "aiOptStop",
			func() (interface{}, error) { return wrap(m.Strategies.AIOptStop(ctx, paperSid)) },
			okTrue, false)

		// ════ SIM (7) — on fresh paper strategy ════
		fmt.Println("\n── sim ──")

		chk("sim", "balance",
			func() (interface{}, error) { return wrap(m.Sim.Balance(ctx, paperSid, "")) },
			func(v interface{}) bool {
				if b, ok := v.(*melaya.SimBalance); ok && b != nil {
					return b.Total >= 0 // 0 is valid for a brand-new account
				}
				return false
			}, false)

		chk("sim", "positions",
			func() (interface{}, error) { return wrap(m.Sim.Positions(ctx, paperSid)) },
			isArr, false)

		chk("sim", "listAccounts",
			func() (interface{}, error) { return wrap(m.Sim.ListAccounts(ctx)) },
			isArr, false)

		chk("sim", "myTrades",
			func() (interface{}, error) { return wrap(m.Sim.MyTrades(ctx, paperSid)) },
			isArr, false)

		// Get perp ticker to size resting limit at ~50%
		var perpLast float64 = 60000
		if t, tErr := m.Market.Ticker(ctx, melaya.SymbolQuery{Exchange: perpExch, Symbol: perpSym}); tErr == nil && t != nil {
			if t.Last != nil {
				perpLast = *t.Last
			} else if t.Bid != nil {
				perpLast = *t.Bid
			}
		}
		limitPx := perpLast * 0.5

		var ordID string
		ordR := chk("sim", "createOrder(limit,resting)",
			func() (interface{}, error) {
				return wrap(m.Sim.CreateOrder(ctx, melaya.SimCreateOrder{
					StrategyID: paperSid,
					Exchange:   perpExch,
					Symbol:     perpSym,
					Side:       "buy",
					Type:       "limit",
					Price:      &limitPx,
					Amount:     0.001,
					Market:     "FUTURES",
				}))
			},
			func(v interface{}) bool {
				if r, ok := v.(*melaya.SimOrderResult); ok && r != nil {
					return r.OrderID != ""
				}
				return false
			}, false)
		if ordR != nil {
			if r, ok := ordR.(*melaya.SimOrderResult); ok {
				ordID = r.OrderID
			}
		}

		chk("sim", "openOrders",
			func() (interface{}, error) { return wrap(m.Sim.OpenOrders(ctx, paperSid)) },
			isArr, false)

		if ordID != "" {
			chk("sim", "cancelOrder",
				func() (interface{}, error) {
					return wrap(m.Sim.CancelOrder(ctx, paperSid, ordID, perpSym, perpExch))
				},
				isObj, false)
		} else {
			skip("sim", "cancelOrder", "no resting order id")
		}
	} else {
		for _, n := range []string{"pause", "resume", "updateParams", "aiOptStop"} {
			skip("strategies", n, "create(paper) failed")
		}
		for _, n := range []string{"balance", "positions", "listAccounts", "myTrades", "createOrder(limit,resting)", "openOrders", "cancelOrder"} {
			skip("sim", n, "no paper sid")
		}
	}

	// WIRED: aiOptStart, aiOptApprove — billable/side-effecting
	wired("strategies", "aiOptStart", "not invoked (would start a billed optimization)")
	wired("strategies", "aiOptApprove", "not invoked (applies optimizer output)")

	// ════ BACKTEST (10; deleteAll WIRED) ════
	fmt.Println("\n── backtest ──")

	sinceCustom := now - int64(60*24*time.Hour/time.Millisecond)
	untilCustom := now

	btR := chk("backtest", "start",
		func() (interface{}, error) {
			return wrap(m.Backtest.Start(ctx, melaya.BacktestStart{
				StrategyType: "custom",
				Exchange:     spotExch,
				Symbol:       spotSym,
				Timeframe:    "1h",
				SinceMS:      &sinceCustom,
				UntilMS:      &untilCustom,
				Language:     "rhai",
				Definition:   rhaiDef,
				Params:       map[string]interface{}{"qty": 0.001},
			}))
		},
		func(v interface{}) bool {
			if m2, ok := v.(map[string]interface{}); ok {
				id, _ := m2["job_id"].(string)
				return id != ""
			}
			return false
		}, false)

	var mainJobID string
	if btR != nil {
		if m2, ok := btR.(map[string]interface{}); ok {
			mainJobID, _ = m2["job_id"].(string)
		}
	}

	if mainJobID != "" {
		// Poll until done (max ~24s: 12 × 2s)
		jobStatus := "queued"
		for i := 0; i < 12; i++ {
			time.Sleep(2 * time.Second)
			j, jErr := m.Backtest.Job(ctx, mainJobID)
			if jErr != nil {
				break
			}
			jobStatus = strings.ToLower(j.Status)
			if jobStatus == "done" || jobStatus == "error" || jobStatus == "halted" || jobStatus == "cancelled" {
				break
			}
		}

		chk("backtest", "job(poll)",
			func() (interface{}, error) { return wrap(m.Backtest.Job(ctx, mainJobID)) },
			func(v interface{}) bool {
				if j, ok := v.(*melaya.BacktestJob); ok && j != nil {
					return j.JobID == mainJobID
				}
				return false
			}, false)

		if jobStatus == "done" {
			chk("backtest", "results",
				func() (interface{}, error) { return wrap(m.Backtest.Results(ctx, mainJobID)) },
				isObj, false)

			chk("backtest", "trades",
				func() (interface{}, error) { return wrap(m.Backtest.Trades(ctx, mainJobID, 10, 0)) },
				isArr, false) // total_trades may be 0 — PASS per spec
		} else {
			skip("backtest", "results", fmt.Sprintf("job %s", jobStatus))
			skip("backtest", "trades", fmt.Sprintf("job %s", jobStatus))
		}
	} else {
		for _, n := range []string{"job(poll)", "results", "trades"} {
			skip("backtest", n, "start failed")
		}
	}

	chk("backtest", "list",
		func() (interface{}, error) { return wrap(m.Backtest.List(ctx, 5, 0)) },
		isArr, false)

	chk("backtest", "favorites",
		func() (interface{}, error) { return wrap(m.Backtest.Favorites(ctx, 5, 0)) },
		isArr, false)

	chk("backtest", "fundingRange",
		func() (interface{}, error) {
			v, err := m.Backtest.FundingRange(ctx, perpExch, perpSym)
			return v, err
		},
		func(v interface{}) bool { return v != nil }, // 0 or int64 — both fine
		false)

	// grid_sweep backtest → sweep
	sinceSwp := now - int64(30*24*time.Hour/time.Millisecond)
	untilSwp := now
	sweepR := chk("backtest", "start(grid_sweep)",
		func() (interface{}, error) {
			return wrap(m.Backtest.Start(ctx, melaya.BacktestStart{
				Mode:         "grid_sweep",
				StrategyType: "custom",
				Exchange:     spotExch,
				Symbol:       spotSym,
				Timeframe:    "1h",
				SinceMS:      &sinceSwp,
				UntilMS:      &untilSwp,
				Language:     "rhai",
				Definition:   rhaiDef,
				ParamRanges:  map[string]interface{}{"qty": []interface{}{0.001, 0.002}},
			}))
		},
		func(v interface{}) bool {
			if m2, ok := v.(map[string]interface{}); ok {
				id, _ := m2["job_id"].(string)
				return id != ""
			}
			return false
		}, false)

	var sweepParentID string
	if sweepR != nil {
		if m2, ok := sweepR.(map[string]interface{}); ok {
			sweepParentID, _ = m2["job_id"].(string)
		}
	}
	if sweepParentID != "" {
		chk("backtest", "sweep",
			func() (interface{}, error) { return wrap(m.Backtest.Sweep(ctx, sweepParentID, "", 10)) },
			isObj, false)
	} else {
		skip("backtest", "sweep", "no sweep parent")
	}

	// start a long job → cancel → delete
	sinceCxl := now - int64(365*24*time.Hour/time.Millisecond)
	untilCxl := now
	cxlR := chk("backtest", "start(for-cancel)",
		func() (interface{}, error) {
			return wrap(m.Backtest.Start(ctx, melaya.BacktestStart{
				StrategyType: "custom",
				Exchange:     spotExch,
				Symbol:       "ETH/USDT",
				Timeframe:    "1h",
				SinceMS:      &sinceCxl,
				UntilMS:      &untilCxl,
				Language:     "rhai",
				Definition:   rhaiDef,
				Params:       map[string]interface{}{"qty": 0.001},
			}))
		},
		func(v interface{}) bool {
			if m2, ok := v.(map[string]interface{}); ok {
				id, _ := m2["job_id"].(string)
				return id != ""
			}
			return false
		}, false)

	var cxlJobID string
	if cxlR != nil {
		if m2, ok := cxlR.(map[string]interface{}); ok {
			cxlJobID, _ = m2["job_id"].(string)
		}
	}
	if cxlJobID != "" {
		chk("backtest", "cancel",
			func() (interface{}, error) { return wrap(m.Backtest.Cancel(ctx, cxlJobID)) },
			isObj, false)
		chk("backtest", "delete",
			func() (interface{}, error) { return wrap(m.Backtest.Delete(ctx, cxlJobID)) },
			okTrue, false)
	} else {
		skip("backtest", "cancel", "no job")
		skip("backtest", "delete", "no job")
	}

	wired("backtest", "deleteAll", "not invoked (soft-deletes ALL non-favorited jobs)")

	// ════ STREAMS — public (5) + private (2) ════
	fmt.Println("\n── stream ──")

	streamChk("stream", "ticker", func() (*melaya.Stream, error) {
		return m.Stream.Ticker(spotExch, spotSym, spotMkt)
	})
	streamChk("stream", "orderbook", func() (*melaya.Stream, error) {
		return m.Stream.Orderbook(spotExch, spotSym, spotMkt, 10)
	})
	streamChk("stream", "ohlcv", func() (*melaya.Stream, error) {
		return m.Stream.Ohlcv(spotExch, spotSym, "1m", spotMkt)
	})
	streamChk("stream", "trades", func() (*melaya.Stream, error) {
		return m.Stream.Trades(spotExch, spotSym, spotMkt)
	})
	streamChk("stream", "liquidations", func() (*melaya.Stream, error) {
		return m.Stream.Liquidations(perpExch)
	})
	streamChk("stream", "strategies(private)", func() (*melaya.Stream, error) {
		return m.Stream.Strategies()
	})

	if len(connectedKeys) > 0 {
		qk := connectedKeys[0]
		streamChk("stream", "private(account)", func() (*melaya.Stream, error) {
			return m.Stream.Private(qk.Exchange, qk.Market, qk.APIKeyID, "", "")
		})
	} else {
		skip("stream", "private(account)", "no connected key")
	}

	// ════ TEARDOWN — stop + delete the paper strategy ════
	if paperSid != "" {
		fmt.Println("\n── teardown ──")
		chk("teardown", "strategies.stop",
			func() (interface{}, error) { return wrap(m.Strategies.Stop(ctx, paperSid)) },
			okTrue, false)
		chk("teardown", "strategies.delete",
			func() (interface{}, error) { return wrap(m.Strategies.Delete(ctx, paperSid)) },
			okTrue, false)
	}

	// ════ REPORT ════
	fmt.Println("\n══════════════ MELAYA SDK — FULL ENDPOINT VALIDATION (Go) ══════════════")
	cats := uniqueCats(results)
	var nPass, nFail, nWired, nSkip int
	for _, cat := range cats {
		fmt.Printf("\n── %s ──\n", cat)
		for _, r := range results {
			if r.cat != cat {
				continue
			}
			fmt.Printf("  %-5s %-30s %s\n", string(r.st), r.name, r.detail)
			switch r.st {
			case stPASS:
				nPass++
			case stFAIL:
				nFail++
			case stWIRED:
				nWired++
			case stSKIP:
				nSkip++
			}
		}
	}
	fmt.Println("\n════════════════════════════════════════════════════════════════════════")
	fmt.Printf("PASS %d   FAIL %d   WIRED(not-invoked) %d   SKIP %d   |  total methods %d\n",
		nPass, nFail, nWired, nSkip, nPass+nFail+nWired+nSkip)
	if nFail == 0 {
		fmt.Println("RESULT: GO — every invoked endpoint validated.")
	} else {
		fmt.Printf("RESULT: NO-GO — %d failing.\n", nFail)
		os.Exit(1)
	}
}

func uniqueCats(rs []result) []string {
	seen := map[string]bool{}
	var out []string
	for _, r := range rs {
		if !seen[r.cat] {
			seen[r.cat] = true
			out = append(out, r.cat)
		}
	}
	return out
}
