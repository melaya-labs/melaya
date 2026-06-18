// Market API — normalized market-data across all 70+ venues.
package melaya

import (
	"context"
	"fmt"
)

// MarketAPI wraps /api/v1/market/* endpoints.
type MarketAPI struct {
	h *httpClient
}

// ListExchanges returns every exchange Melaya currently supports.
func (m *MarketAPI) ListExchanges(ctx context.Context) ([]ExchangeInfo, error) {
	data, err := m.h.get(ctx, "/api/v1/market/list-exchanges", nil)
	if err != nil {
		return nil, err
	}
	var env struct {
		Exchanges []ExchangeInfo `json:"exchanges"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Exchanges, nil
}

// Ticker returns the best bid/ask + 24h aggregates for one symbol.
func (m *MarketAPI) Ticker(ctx context.Context, q SymbolQuery) (*Ticker, error) {
	qp := map[string]string{"exchange": q.Exchange, "symbol": q.Symbol, "market": q.Market}
	data, err := m.h.get(ctx, "/api/v1/market/ticker", qp)
	if err != nil {
		return nil, err
	}
	var env struct {
		Ticker *Ticker `json:"ticker"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Ticker, nil
}

// Orderbook returns the order book to the given depth.
func (m *MarketAPI) Orderbook(ctx context.Context, q OrderBookQuery) (*OrderBook, error) {
	qp := map[string]string{
		"exchange": q.Exchange,
		"symbol":   q.Symbol,
		"market":   q.Market,
	}
	if q.Limit > 0 {
		qp["limit"] = fmt.Sprintf("%d", q.Limit)
	}
	data, err := m.h.get(ctx, "/api/v1/market/orderbook", qp)
	if err != nil {
		return nil, err
	}
	var env struct {
		Orderbook *OrderBook `json:"orderbook"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Orderbook, nil
}

// Ohlcv returns OHLCV candles for a timeframe.
func (m *MarketAPI) Ohlcv(ctx context.Context, q OhlcvQuery) ([]Candle, error) {
	qp := map[string]string{
		"exchange":  q.Exchange,
		"symbol":    q.Symbol,
		"market":    q.Market,
		"timeframe": q.Timeframe,
	}
	if q.Limit > 0 {
		qp["limit"] = fmt.Sprintf("%d", q.Limit)
	}
	data, err := m.h.get(ctx, "/api/v1/market/ohlcv", qp)
	if err != nil {
		return nil, err
	}
	var env struct {
		Candles []Candle `json:"candles"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Candles, nil
}

// Trades returns recent public trades for a symbol.
func (m *MarketAPI) Trades(ctx context.Context, q SymbolQuery) ([]Trade, error) {
	qp := map[string]string{"exchange": q.Exchange, "symbol": q.Symbol, "market": q.Market}
	data, err := m.h.get(ctx, "/api/v1/market/trades", qp)
	if err != nil {
		return nil, err
	}
	var env struct {
		Trades []Trade `json:"trades"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Trades, nil
}

// Markets returns tradable markets on a venue.
func (m *MarketAPI) Markets(ctx context.Context, q ExchangeQuery) ([]interface{}, error) {
	data, err := m.h.get(ctx, "/api/v1/market/markets", map[string]string{"exchange": q.Exchange})
	if err != nil {
		return nil, err
	}
	var env struct {
		Markets []interface{} `json:"markets"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Markets, nil
}

// Currencies returns listed currencies on a venue.
func (m *MarketAPI) Currencies(ctx context.Context, q ExchangeQuery) ([]interface{}, error) {
	data, err := m.h.get(ctx, "/api/v1/market/currencies", map[string]string{"exchange": q.Exchange})
	if err != nil {
		return nil, err
	}
	var env struct {
		Currencies []interface{} `json:"currencies"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Currencies, nil
}

// Status returns the operational status for a venue (ok/maintenance/degraded).
func (m *MarketAPI) Status(ctx context.Context, q ExchangeQuery) (*ExchangeStatus, error) {
	data, err := m.h.get(ctx, "/api/v1/market/status", map[string]string{"exchange": q.Exchange})
	if err != nil {
		return nil, err
	}
	var env struct {
		Status *ExchangeStatus `json:"status"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Status, nil
}

// Time returns the exchange server time.
func (m *MarketAPI) Time(ctx context.Context, q ExchangeQuery) (interface{}, error) {
	data, err := m.h.get(ctx, "/api/v1/market/time", map[string]string{"exchange": q.Exchange})
	if err != nil {
		return nil, err
	}
	var env struct {
		Time interface{} `json:"time"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Time, nil
}

// Tickers returns tickers for many symbols on one venue (POST, keyed by symbol).
func (m *MarketAPI) Tickers(ctx context.Context, body map[string]interface{}) (map[string]Ticker, error) {
	data, err := m.h.post(ctx, "/api/v1/market/tickers", body)
	if err != nil {
		return nil, err
	}
	var env struct {
		Tickers map[string]Ticker `json:"tickers"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Tickers, nil
}

// FundingRates returns the latest funding rates for perpetuals (keyed by symbol).
func (m *MarketAPI) FundingRates(ctx context.Context, body map[string]interface{}) (map[string]interface{}, error) {
	data, err := m.h.post(ctx, "/api/v1/market/funding-rates", body)
	if err != nil {
		return nil, err
	}
	var env struct {
		Rates map[string]interface{} `json:"rates"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Rates, nil
}

// FundingRateHistory returns funding-rate history.
func (m *MarketAPI) FundingRateHistory(ctx context.Context, body map[string]interface{}) ([]interface{}, error) {
	data, err := m.h.post(ctx, "/api/v1/market/funding-rate-history", body)
	if err != nil {
		return nil, err
	}
	var env struct {
		History []interface{} `json:"history"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.History, nil
}

// OpenInterest returns open interest for one or more perpetuals (keyed by symbol).
func (m *MarketAPI) OpenInterest(ctx context.Context, body map[string]interface{}) (map[string]interface{}, error) {
	data, err := m.h.post(ctx, "/api/v1/market/open-interest", body)
	if err != nil {
		return nil, err
	}
	var env struct {
		OpenInterest map[string]interface{} `json:"openInterest"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.OpenInterest, nil
}

// OpenInterestHistory returns open-interest history.
func (m *MarketAPI) OpenInterestHistory(ctx context.Context, body map[string]interface{}) ([]interface{}, error) {
	data, err := m.h.post(ctx, "/api/v1/market/open-interest-history", body)
	if err != nil {
		return nil, err
	}
	var env struct {
		History []interface{} `json:"history"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.History, nil
}

// Instruments returns instrument list + trading constraints.
func (m *MarketAPI) Instruments(ctx context.Context, body map[string]interface{}) (interface{}, error) {
	data, err := m.h.post(ctx, "/api/v1/market/instruments", body)
	if err != nil {
		return nil, err
	}
	var v interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// LiquidationEvents returns cross-exchange liquidation events (historical).
func (m *MarketAPI) LiquidationEvents(ctx context.Context, body map[string]interface{}) ([]Liquidation, error) {
	data, err := m.h.post(ctx, "/api/v1/market/liquidation-events", body)
	if err != nil {
		return nil, err
	}
	var env struct {
		Events []Liquidation `json:"events"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Events, nil
}

// OhlcvMulti returns multi-symbol OHLCV in one call (keyed by symbol). Each
// value is a per-symbol object ({ ok, candles: [...] }), so it is kept flexible.
func (m *MarketAPI) OhlcvMulti(ctx context.Context, body map[string]interface{}) (map[string]interface{}, error) {
	data, err := m.h.post(ctx, "/api/v1/market/ohlcv-multi", body)
	if err != nil {
		return nil, err
	}
	var env struct {
		PerSymbol map[string]interface{} `json:"perSymbol"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.PerSymbol, nil
}

// MarketConstraints returns trading constraints for one symbol.
func (m *MarketAPI) MarketConstraints(ctx context.Context, body map[string]interface{}) (interface{}, error) {
	data, err := m.h.post(ctx, "/api/v1/market/market-constraints", body)
	if err != nil {
		return nil, err
	}
	var env struct {
		Constraints interface{} `json:"constraints"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Constraints, nil
}

// FundingRateHistoryMulti returns funding-rate history across venues (keyed by exchange).
func (m *MarketAPI) FundingRateHistoryMulti(ctx context.Context, body map[string]interface{}) (map[string]interface{}, error) {
	data, err := m.h.post(ctx, "/api/v1/market/funding-rate-history-multi", body)
	if err != nil {
		return nil, err
	}
	var env struct {
		PerExchange map[string]interface{} `json:"perExchange"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.PerExchange, nil
}

// OpenInterestHistoryMulti returns open-interest history across venues (keyed by exchange).
func (m *MarketAPI) OpenInterestHistoryMulti(ctx context.Context, body map[string]interface{}) (map[string]interface{}, error) {
	data, err := m.h.post(ctx, "/api/v1/market/open-interest-history-multi", body)
	if err != nil {
		return nil, err
	}
	var env struct {
		PerExchange map[string]interface{} `json:"perExchange"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.PerExchange, nil
}

// PredictionMarkets returns prediction-market listings for a venue.
func (m *MarketAPI) PredictionMarkets(ctx context.Context, body map[string]interface{}) ([]interface{}, error) {
	if body == nil {
		body = map[string]interface{}{"venue": "polymarket"}
	}
	data, err := m.h.post(ctx, "/api/v1/market/pm-markets", body)
	if err != nil {
		return nil, err
	}
	var env struct {
		Markets []interface{} `json:"markets"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Markets, nil
}

// CatalogCounts returns live platform catalog counts (tools, subagents, by category).
func (m *MarketAPI) CatalogCounts(ctx context.Context) (*CatalogCounts, error) {
	data, err := m.h.get(ctx, "/api/v1/public/catalog-counts", nil)
	if err != nil {
		return nil, err
	}
	var v CatalogCounts
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

