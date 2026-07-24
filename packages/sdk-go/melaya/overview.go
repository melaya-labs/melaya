// Overview API — dashboard overview, model pricing, and cost breakdown.
//
// Maps to /api/v1/private/overview/*.
package melaya

import "context"

// OverviewAPI wraps dashboard overview endpoints.
type OverviewAPI struct {
	h *httpClient
}

// ModelPrices returns pricing data for available AI models.
//
// GET /api/v1/private/overview/model-prices
func (o *OverviewAPI) ModelPrices(ctx context.Context) ([]ModelPrice, error) {
	data, err := o.h.get(ctx, "/api/v1/private/overview/model-prices", nil)
	if err != nil {
		return nil, err
	}
	var v []ModelPrice
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ChartData returns chart data for the overview dashboard (cost/usage over time).
//
// GET /api/v1/private/overview/chart
func (o *OverviewAPI) ChartData(ctx context.Context) (map[string]interface{}, error) {
	data, err := o.h.get(ctx, "/api/v1/private/overview/chart", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// UsageSummary returns the usage summary (pipeline count, RAG usage, plan
// limits) shown in the sidebar/dashboard. No input.
//
// GET /api/v1/private/overview/usage
func (o *OverviewAPI) UsageSummary(ctx context.Context) (map[string]interface{}, error) {
	data, err := o.h.get(ctx, "/api/v1/private/overview/usage", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// CostBreakdown returns cost breakdown by model/provider.
//
// GET /api/v1/private/overview/cost-breakdown
func (o *OverviewAPI) CostBreakdown(ctx context.Context) (map[string]interface{}, error) {
	data, err := o.h.get(ctx, "/api/v1/private/overview/cost-breakdown", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}
