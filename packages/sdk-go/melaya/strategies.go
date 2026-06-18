// Strategies API — launch, control, and inspect trading strategies.
package melaya

import (
	"context"
	"fmt"
)

// StrategiesAPI wraps /api/v1/strategies/* endpoints.
type StrategiesAPI struct {
	h *httpClient
}

// List returns every strategy you own (running, paused, paper, live).
func (s *StrategiesAPI) List(ctx context.Context) ([]Strategy, error) {
	data, err := s.h.get(ctx, "/api/v1/strategies/list", nil)
	if err != nil {
		return nil, err
	}
	var env struct {
		Strategies []Strategy `json:"strategies"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Strategies, nil
}

// Get returns a single strategy by id.
func (s *StrategiesAPI) Get(ctx context.Context, strategyID string) (*Strategy, error) {
	data, err := s.h.get(ctx, "/api/v1/strategies/"+strategyID, nil)
	if err != nil {
		return nil, err
	}
	var env struct {
		Strategy *Strategy `json:"strategy"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Strategy, nil
}

// Create launches a new strategy. Use DryRun=true for paper mode.
// Extra is merged into the JSON body to support top-level fields like
// language/definition for custom strategies.
func (s *StrategiesAPI) Create(ctx context.Context, body StrategyCreate) (*StrategyCreateResult, error) {
	// Build a raw map so we can inject Extra fields without type-pollution.
	m := map[string]interface{}{
		"name":         body.Name,
		"strategyType": body.StrategyType,
		"exchange":     body.Exchange,
		"dryRun":       body.DryRun,
	}
	if body.Market != "" {
		m["market"] = body.Market
	}
	if body.Symbol != "" {
		m["symbol"] = body.Symbol
	}
	if body.APIKeyID != "" {
		m["apiKeyId"] = body.APIKeyID
	}
	if body.Params != nil {
		m["params"] = body.Params
	}
	if body.RuntimeMode != "" {
		m["runtimeMode"] = body.RuntimeMode
	}
	if body.KeyBindings != nil {
		m["keyBindings"] = body.KeyBindings
	}
	for k, v := range body.Extra {
		m[k] = v
	}

	data, err := s.h.post(ctx, "/api/v1/strategies", m)
	if err != nil {
		return nil, err
	}
	var v StrategyCreateResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Pause pauses a running strategy.
func (s *StrategiesAPI) Pause(ctx context.Context, strategyID string) (map[string]interface{}, error) {
	return s.simplePost(ctx, fmt.Sprintf("/api/v1/strategies/%s/pause", strategyID))
}

// Resume resumes a paused strategy.
func (s *StrategiesAPI) Resume(ctx context.Context, strategyID string) (map[string]interface{}, error) {
	return s.simplePost(ctx, fmt.Sprintf("/api/v1/strategies/%s/resume", strategyID))
}

// Stop stops a strategy and tears down its runner.
func (s *StrategiesAPI) Stop(ctx context.Context, strategyID string) (map[string]interface{}, error) {
	return s.simplePost(ctx, fmt.Sprintf("/api/v1/strategies/%s/stop", strategyID))
}

// Delete soft-deletes a strategy.
func (s *StrategiesAPI) Delete(ctx context.Context, strategyID string) (map[string]interface{}, error) {
	data, err := s.h.del(ctx, "/api/v1/strategies/"+strategyID, nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// UpdateParams updates a running strategy's params.
func (s *StrategiesAPI) UpdateParams(ctx context.Context, strategyID string, params map[string]interface{}) (map[string]interface{}, error) {
	data, err := s.h.post(ctx, fmt.Sprintf("/api/v1/strategies/%s/update-params", strategyID), params)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Status returns the live runtime status of a strategy's runner.
func (s *StrategiesAPI) Status(ctx context.Context, strategyID string) (map[string]interface{}, error) {
	data, err := s.h.get(ctx, fmt.Sprintf("/api/v1/strategies/%s/status", strategyID), nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Performance returns the performance series (equity, PnL over time) for a strategy.
func (s *StrategiesAPI) Performance(ctx context.Context, strategyID string) ([]interface{}, error) {
	return s.rowsGet(ctx, fmt.Sprintf("/api/v1/strategies/%s/performance", strategyID))
}

// Executions returns execution (order) rows for a strategy.
func (s *StrategiesAPI) Executions(ctx context.Context, strategyID string) ([]interface{}, error) {
	return s.rowsGet(ctx, fmt.Sprintf("/api/v1/strategies/%s/executions", strategyID))
}

// Trades returns trade (fill) rows for a strategy.
func (s *StrategiesAPI) Trades(ctx context.Context, strategyID string) ([]interface{}, error) {
	return s.rowsGet(ctx, fmt.Sprintf("/api/v1/strategies/%s/trades", strategyID))
}

// Logs returns log rows for a strategy.
func (s *StrategiesAPI) Logs(ctx context.Context, strategyID string) ([]interface{}, error) {
	return s.rowsGet(ctx, fmt.Sprintf("/api/v1/strategies/%s/logs", strategyID))
}

// ── AI parameter optimizer ────────────────────────────────────────────────────

// AIOptStart kicks off an AI-driven parameter optimization.
func (s *StrategiesAPI) AIOptStart(ctx context.Context, strategyID string, body map[string]interface{}) (map[string]interface{}, error) {
	data, err := s.h.post(ctx, fmt.Sprintf("/api/v1/strategies/%s/ai-opt/start", strategyID), body)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// AIOptStatus returns the current optimization status for a strategy.
func (s *StrategiesAPI) AIOptStatus(ctx context.Context, strategyID string) (map[string]interface{}, error) {
	data, err := s.h.get(ctx, fmt.Sprintf("/api/v1/strategies/%s/ai-opt/status", strategyID), nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// AIOptApprove approves and applies the optimizer's proposed params.
func (s *StrategiesAPI) AIOptApprove(ctx context.Context, strategyID string, body map[string]interface{}) (map[string]interface{}, error) {
	if body == nil {
		body = map[string]interface{}{}
	}
	data, err := s.h.post(ctx, fmt.Sprintf("/api/v1/strategies/%s/ai-opt/approve", strategyID), body)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// AIOptStop stops an in-progress optimization.
func (s *StrategiesAPI) AIOptStop(ctx context.Context, strategyID string) (map[string]interface{}, error) {
	return s.simplePost(ctx, fmt.Sprintf("/api/v1/strategies/%s/ai-opt/stop", strategyID))
}

// AIOptRuns returns past optimization runs for a strategy.
func (s *StrategiesAPI) AIOptRuns(ctx context.Context, strategyID string) (interface{}, error) {
	data, err := s.h.get(ctx, fmt.Sprintf("/api/v1/strategies/%s/ai-opt/runs", strategyID), nil)
	if err != nil {
		return nil, err
	}
	var v interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ── helpers ───────────────────────────────────────────────────────────────────

func (s *StrategiesAPI) simplePost(ctx context.Context, path string) (map[string]interface{}, error) {
	data, err := s.h.post(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

func (s *StrategiesAPI) rowsGet(ctx context.Context, path string) ([]interface{}, error) {
	data, err := s.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var env struct {
		Rows []interface{} `json:"rows"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Rows, nil
}

