// Evals API — eval run listing, detail, and comparison.
//
// Maps to /api/v1/private/evals/*.
package melaya

import (
	"context"
	"net/url"
)

// EvalsAPI wraps eval run endpoints.
type EvalsAPI struct {
	h *httpClient
}

// ListRuns lists eval run results for the caller's tenant.
//
// GET /api/v1/private/evals/runs
func (e *EvalsAPI) ListRuns(ctx context.Context) ([]EvalRun, error) {
	data, err := e.h.get(ctx, "/api/v1/private/evals/runs", nil)
	if err != nil {
		return nil, err
	}
	var v []EvalRun
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Summary returns an aggregate summary of eval results.
//
// GET /api/v1/private/evals/summary
func (e *EvalsAPI) Summary(ctx context.Context) (*EvalSummary, error) {
	data, err := e.h.get(ctx, "/api/v1/private/evals/summary", nil)
	if err != nil {
		return nil, err
	}
	var v EvalSummary
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// RunDetail returns detailed results for a specific eval run.
//
// GET /api/v1/private/evals/runs/:runId
func (e *EvalsAPI) RunDetail(ctx context.Context, runID string) (*EvalRun, error) {
	path := "/api/v1/private/evals/runs/" + url.PathEscape(runID)
	data, err := e.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v EvalRun
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Compare compares results across multiple eval runs.
//
// GET /api/v1/private/evals/compare
func (e *EvalsAPI) Compare(ctx context.Context, runIDs []string) (map[string]interface{}, error) {
	q := map[string]string{}
	if len(runIDs) > 0 {
		// Pass as comma-separated or repeated query param — server accepts both.
		q["runIds"] = joinStrings(runIDs, ",")
	}
	data, err := e.h.get(ctx, "/api/v1/private/evals/compare", q)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// MemoryGraph returns memory graph visualization data for eval runs.
//
// GET /api/v1/private/evals/memory-graph
func (e *EvalsAPI) MemoryGraph(ctx context.Context) (map[string]interface{}, error) {
	data, err := e.h.get(ctx, "/api/v1/private/evals/memory-graph", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// RunMemory returns memory usage for a specific eval run.
//
// GET /api/v1/private/evals/runs/:runId/memory
func (e *EvalsAPI) RunMemory(ctx context.Context, runID string) (map[string]interface{}, error) {
	path := "/api/v1/private/evals/runs/" + url.PathEscape(runID) + "/memory"
	data, err := e.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// CrewMemory returns agent crew memory for a pipeline.
// Pass pipeline name and project name as query parameters.
//
// GET /api/v1/private/evals/crew-memory
func (e *EvalsAPI) CrewMemory(ctx context.Context, pipeline, project string) (map[string]interface{}, error) {
	q := map[string]string{}
	if pipeline != "" {
		q["pipeline"] = pipeline
	}
	if project != "" {
		q["project"] = project
	}
	data, err := e.h.get(ctx, "/api/v1/private/evals/crew-memory", q)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Benchmarks returns benchmark scores across eval runs.
//
// GET /api/v1/private/evals/benchmarks
func (e *EvalsAPI) Benchmarks(ctx context.Context) (map[string]interface{}, error) {
	data, err := e.h.get(ctx, "/api/v1/private/evals/benchmarks", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// joinStrings joins a slice of strings with a separator.
func joinStrings(ss []string, sep string) string {
	if len(ss) == 0 {
		return ""
	}
	result := ss[0]
	for _, s := range ss[1:] {
		result += sep + s
	}
	return result
}
