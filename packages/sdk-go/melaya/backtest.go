// Backtest API — run strategies against historical data on the Rust engine.
package melaya

import (
	"context"
	"fmt"
)

// BacktestAPI wraps /api/v1/private/backtest/* endpoints.
type BacktestAPI struct {
	h *httpClient
}

// Start starts a backtest. Returns the job id (and optionally a count for sweeps).
func (b *BacktestAPI) Start(ctx context.Context, body BacktestStart) (map[string]interface{}, error) {
	data, err := b.h.post(ctx, "/api/v1/private/backtest/start", body)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Job returns the status + progress for a backtest job.
func (b *BacktestAPI) Job(ctx context.Context, jobID string) (*BacktestJob, error) {
	data, err := b.h.get(ctx, fmt.Sprintf("/api/v1/private/backtest/jobs/%s", jobID), nil)
	if err != nil {
		return nil, err
	}
	var v BacktestJob
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Results returns metrics, equity curve, and OHLCV for a completed job.
func (b *BacktestAPI) Results(ctx context.Context, jobID string) (*BacktestResult, error) {
	data, err := b.h.get(ctx, fmt.Sprintf("/api/v1/private/backtest/results/%s", jobID), nil)
	if err != nil {
		return nil, err
	}
	var env struct {
		Result *BacktestResult `json:"result"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Result, nil
}

// Trades returns the trade list for a completed job.
func (b *BacktestAPI) Trades(ctx context.Context, jobID string, limit, offset int) ([]interface{}, error) {
	qp := map[string]string{}
	if limit > 0 {
		qp["limit"] = fmt.Sprintf("%d", limit)
	}
	if offset > 0 {
		qp["offset"] = fmt.Sprintf("%d", offset)
	}
	data, err := b.h.get(ctx, fmt.Sprintf("/api/v1/private/backtest/trades/%s", jobID), qp)
	if err != nil {
		return nil, err
	}
	var env struct {
		Trades []interface{} `json:"trades"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Trades, nil
}

// Sweep returns ranked children of a sweep parent job.
func (b *BacktestAPI) Sweep(ctx context.Context, parentID string, objective string, limit int) (map[string]interface{}, error) {
	qp := map[string]string{}
	if objective != "" {
		qp["objective"] = objective
	}
	if limit > 0 {
		qp["limit"] = fmt.Sprintf("%d", limit)
	}
	data, err := b.h.get(ctx, fmt.Sprintf("/api/v1/private/backtest/sweep/%s", parentID), qp)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// List returns your backtest jobs, newest first.
func (b *BacktestAPI) List(ctx context.Context, limit, offset int) ([]interface{}, error) {
	return b.jobList(ctx, "/api/v1/private/backtest", limit, offset)
}

// Favorites returns your favorited backtest jobs.
func (b *BacktestAPI) Favorites(ctx context.Context, limit, offset int) ([]interface{}, error) {
	return b.jobList(ctx, "/api/v1/private/backtest/favorites", limit, offset)
}

// FundingRange returns the earliest funding-rate timestamp for exchange+symbol (ms, or 0).
func (b *BacktestAPI) FundingRange(ctx context.Context, exchange, symbol string) (int64, error) {
	data, err := b.h.get(ctx, "/api/v1/private/backtest/funding-range", map[string]string{
		"exchange": exchange,
		"symbol":   symbol,
	})
	if err != nil {
		return 0, err
	}
	var env struct {
		EarliestMS *int64 `json:"earliest_ms"`
	}
	if err := unmarshal(data, &env); err != nil {
		return 0, err
	}
	if env.EarliestMS == nil {
		return 0, nil
	}
	return *env.EarliestMS, nil
}

// Cancel cancels an in-flight backtest job.
func (b *BacktestAPI) Cancel(ctx context.Context, jobID string) (map[string]interface{}, error) {
	data, err := b.h.post(ctx, fmt.Sprintf("/api/v1/private/backtest/%s/cancel", jobID), nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Delete soft-deletes a single backtest job.
func (b *BacktestAPI) Delete(ctx context.Context, jobID string) (map[string]interface{}, error) {
	data, err := b.h.del(ctx, fmt.Sprintf("/api/v1/private/backtest/%s", jobID), nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// DeleteAll soft-deletes every non-favorited backtest job.
func (b *BacktestAPI) DeleteAll(ctx context.Context) (map[string]interface{}, error) {
	data, err := b.h.del(ctx, "/api/v1/private/backtest", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ── helpers ───────────────────────────────────────────────────────────────────

func (b *BacktestAPI) jobList(ctx context.Context, path string, limit, offset int) ([]interface{}, error) {
	qp := map[string]string{}
	if limit > 0 {
		qp["limit"] = fmt.Sprintf("%d", limit)
	}
	if offset > 0 {
		qp["offset"] = fmt.Sprintf("%d", offset)
	}
	data, err := b.h.get(ctx, path, qp)
	if err != nil {
		return nil, err
	}
	// envelope: { data: { jobs: [...] } }
	var env struct {
		Data struct {
			Jobs []interface{} `json:"jobs"`
		} `json:"data"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Data.Jobs, nil
}
