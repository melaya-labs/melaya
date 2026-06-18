// Sim API — paper-trading (sim broker) per-strategy.
package melaya

import "context"

// SimAPI wraps /api/v1/private/sim/* endpoints.
type SimAPI struct {
	h *httpClient
}

// ListAccounts returns all paper accounts (one virtual wallet per paper strategy).
func (s *SimAPI) ListAccounts(ctx context.Context) ([]interface{}, error) {
	data, err := s.h.get(ctx, "/api/v1/private/sim/list-accounts", nil)
	if err != nil {
		return nil, err
	}
	// Response may be a bare array or {accounts:[]}
	var arr []interface{}
	if err := unmarshal(data, &arr); err == nil {
		return arr, nil
	}
	var env struct {
		Accounts []interface{} `json:"accounts"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Accounts, nil
}

// Balance returns the virtual balance for a paper strategy.
func (s *SimAPI) Balance(ctx context.Context, strategyID string, asset string) (*SimBalance, error) {
	qp := map[string]string{"strategy_id": strategyID}
	if asset != "" {
		qp["asset"] = asset
	}
	data, err := s.h.get(ctx, "/api/v1/private/sim/balance", qp)
	if err != nil {
		return nil, err
	}
	var v SimBalance
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Positions returns open paper positions for a strategy.
func (s *SimAPI) Positions(ctx context.Context, strategyID string) ([]interface{}, error) {
	data, err := s.h.get(ctx, "/api/v1/private/sim/positions", map[string]string{"strategy_id": strategyID})
	if err != nil {
		return nil, err
	}
	var arr []interface{}
	if err := unmarshal(data, &arr); err == nil {
		return arr, nil
	}
	var env struct {
		Positions []interface{} `json:"positions"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Positions, nil
}

// OpenOrders returns resting paper orders for a strategy.
func (s *SimAPI) OpenOrders(ctx context.Context, strategyID string) ([]interface{}, error) {
	data, err := s.h.get(ctx, "/api/v1/private/sim/open-orders", map[string]string{"strategy_id": strategyID})
	if err != nil {
		return nil, err
	}
	var arr []interface{}
	if err := unmarshal(data, &arr); err == nil {
		return arr, nil
	}
	var env struct {
		Orders []interface{} `json:"orders"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Orders, nil
}

// MyTrades returns filled paper trades for a strategy.
func (s *SimAPI) MyTrades(ctx context.Context, strategyID string) ([]interface{}, error) {
	data, err := s.h.get(ctx, "/api/v1/private/sim/my-trades", map[string]string{"strategy_id": strategyID})
	if err != nil {
		return nil, err
	}
	var arr []interface{}
	if err := unmarshal(data, &arr); err == nil {
		return arr, nil
	}
	var env struct {
		Trades []interface{} `json:"trades"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Trades, nil
}

// CreateOrder places a paper order. Fills synthesize from the live ticker.
func (s *SimAPI) CreateOrder(ctx context.Context, o SimCreateOrder) (*SimOrderResult, error) {
	orderType := o.Type
	if orderType == "" {
		orderType = "market"
	}
	body := map[string]interface{}{
		"strategy_id": o.StrategyID,
		"exchange":    o.Exchange,
		"symbol":      o.Symbol,
		"side":        o.Side,
		"amount":      o.Amount,
		"order_type":  orderType,
		"orderType":   orderType,
	}
	if o.Price != nil {
		body["price"] = *o.Price
	}
	if o.Market != "" {
		body["market"] = o.Market
		body["market_type"] = o.Market
	}
	if o.Leverage != nil {
		body["leverage"] = *o.Leverage
	}
	if o.ReduceOnly {
		body["reduceOnly"] = true
	}
	if o.SLPrice != nil {
		body["slPrice"] = *o.SLPrice
	}
	if o.TPPrice != nil {
		body["tpPrice"] = *o.TPPrice
	}
	if o.ClientOrderID != "" {
		body["client_order_id"] = o.ClientOrderID
		body["clientOrderId"] = o.ClientOrderID
	}
	if o.Params != nil {
		body["params"] = o.Params
	}

	data, err := s.h.post(ctx, "/api/v1/private/sim/create-order", body)
	if err != nil {
		return nil, err
	}
	var v SimOrderResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// CancelOrder cancels a resting paper order.
func (s *SimAPI) CancelOrder(ctx context.Context, strategyID, orderID, symbol, exchange string) (map[string]interface{}, error) {
	body := map[string]interface{}{
		"strategy_id": strategyID,
		"order_id":    orderID,
		"orderId":     orderID,
	}
	if symbol != "" {
		body["symbol"] = symbol
	}
	if exchange != "" {
		body["exchange"] = exchange
	}
	data, err := s.h.post(ctx, "/api/v1/private/sim/cancel-order", body)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

