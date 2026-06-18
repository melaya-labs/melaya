package melaya

import "context"

// TradeAPI is the live, credentialed trading plane: order placement, account
// state, and position management on a CONNECTED exchange. Every method POSTs to
// /api/v1/private/<op>; the server resolves the connected key (by apiKeyId) and
// forwards to the venue via the in-house Rust engine.
//
// WARNING: the write methods (CreateOrder, CancelOrder, AmendOrder,
// CancelAllOrders, CancelPlanOrders, ClosePosition, SetLeverage, SetMarginMode,
// SetPositionMode) move REAL funds. Use the sim broker for risk-free testing.
type TradeAPI struct{ h *httpClient }

// LiveResult is the shared response envelope of every live op.
type LiveResult = map[string]interface{}

func (t *TradeAPI) op(ctx context.Context, op string, body map[string]interface{}) (LiveResult, error) {
	data, err := t.h.post(ctx, "/api/v1/private/"+op, body)
	if err != nil {
		return nil, err
	}
	var out LiveResult
	if err := unmarshal(data, &out); err != nil {
		return nil, err
	}
	return out, nil
}

// ── Account state (reads) ─────────────────────────────────────────────────

func (t *TradeAPI) Balance(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "balance", body)
}
func (t *TradeAPI) Positions(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "positions", body)
}
func (t *TradeAPI) PositionsHistory(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "positions-history", body)
}
func (t *TradeAPI) OpenOrders(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "open-orders", body)
}
func (t *TradeAPI) Orders(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "orders", body)
}
func (t *TradeAPI) ClosedOrders(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "closed-orders", body)
}
func (t *TradeAPI) MyTrades(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "my-trades", body)
}
func (t *TradeAPI) MyTradesHistory(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "my-trades-history", body)
}
func (t *TradeAPI) PlanOrders(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "plan-orders", body)
}
func (t *TradeAPI) Leverage(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "leverage", body)
}
func (t *TradeAPI) LeverageTiers(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "leverage-tiers", body)
}

// ── Order placement & management (LIVE writes — real funds) ────────────────

func (t *TradeAPI) CreateOrder(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "create-order", body)
}
func (t *TradeAPI) CancelOrder(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "cancel-order", body)
}
func (t *TradeAPI) AmendOrder(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "amend-order", body)
}
func (t *TradeAPI) CancelAllOrders(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "cancel-all-orders", body)
}
func (t *TradeAPI) CancelPlanOrders(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "cancel-plan-orders", body)
}
func (t *TradeAPI) ClosePosition(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "close-position", body)
}
func (t *TradeAPI) SetLeverage(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "set-leverage", body)
}
func (t *TradeAPI) SetMarginMode(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "set-margin-mode", body)
}
func (t *TradeAPI) SetPositionMode(ctx context.Context, body map[string]interface{}) (LiveResult, error) {
	return t.op(ctx, "set-position-mode", body)
}
