// HITL (Human-in-the-Loop) API — list, approve, and reject pending tool-call approvals.
//
// Maps to /api/v1/private/hitl/*.
//
// Example:
//
//	pending, _ := m.Hitl.Pending(ctx)
//	for _, req := range pending {
//	    m.Hitl.Approve(ctx, req.RequestID, &melaya.HitlDecideParams{Comment: "LGTM"})
//	}
package melaya

import (
	"context"
	"fmt"
	"net/url"
)

// HitlAPI wraps HITL approval queue endpoints.
type HitlAPI struct {
	h *httpClient
}

// Pending lists all pending HITL tool-call approvals for the authenticated user.
//
// GET /api/v1/private/hitl/approvals/pending
func (h *HitlAPI) Pending(ctx context.Context) ([]HitlApproval, error) {
	data, err := h.h.get(ctx, "/api/v1/private/hitl/approvals/pending", nil)
	if err != nil {
		return nil, err
	}
	var v []HitlApproval
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// History lists historical (decided) HITL approval records.
//
// GET /api/v1/private/hitl/approvals/history
func (h *HitlAPI) History(ctx context.Context, limit, offset *int) ([]HitlApprovalHistory, error) {
	q := map[string]string{}
	if limit != nil {
		q["limit"] = fmt.Sprintf("%d", *limit)
	}
	if offset != nil {
		q["offset"] = fmt.Sprintf("%d", *offset)
	}
	data, err := h.h.get(ctx, "/api/v1/private/hitl/approvals/history", q)
	if err != nil {
		return nil, err
	}
	var v []HitlApprovalHistory
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Approve approves a pending HITL tool-call approval.
//
// POST /api/v1/private/hitl/approvals/:requestId/approve
func (h *HitlAPI) Approve(ctx context.Context, requestID string, params *HitlDecideParams) (bool, error) {
	path := "/api/v1/private/hitl/approvals/" + url.PathEscape(requestID) + "/approve"
	data, err := h.h.post(ctx, path, params)
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}

// Reject rejects a pending HITL tool-call approval.
//
// POST /api/v1/private/hitl/approvals/:requestId/reject
func (h *HitlAPI) Reject(ctx context.Context, requestID string, params *HitlDecideParams) (bool, error) {
	path := "/api/v1/private/hitl/approvals/" + url.PathEscape(requestID) + "/reject"
	data, err := h.h.post(ctx, path, params)
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}

// BulkDecide bulk-approves or bulk-rejects multiple pending tool-calls in one call.
//
// POST /api/v1/private/hitl/approvals/bulk
func (h *HitlAPI) BulkDecide(ctx context.Context, body HitlBulkDecideBody) (*HitlBulkDecideResult, error) {
	data, err := h.h.post(ctx, "/api/v1/private/hitl/approvals/bulk", body)
	if err != nil {
		return nil, err
	}
	var v HitlBulkDecideResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// RunToolStats returns tool-call statistics for a specific pipeline run.
//
// GET /api/v1/private/hitl/runs/:runId/tool-stats
func (h *HitlAPI) RunToolStats(ctx context.Context, runID string) (*RunToolStats, error) {
	path := "/api/v1/private/hitl/runs/" + url.PathEscape(runID) + "/tool-stats"
	data, err := h.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v RunToolStats
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// RunToolStatsByAgent returns tool-call stats broken down by agent for a run.
//
// GET /api/v1/private/hitl/runs/:runId/tool-stats/by-agent
func (h *HitlAPI) RunToolStatsByAgent(ctx context.Context, runID string) (map[string]interface{}, error) {
	path := "/api/v1/private/hitl/runs/" + url.PathEscape(runID) + "/tool-stats/by-agent"
	data, err := h.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// RunMessages returns paginated messages for a pipeline run.
//
// GET /api/v1/private/hitl/runs/:runId/messages
func (h *HitlAPI) RunMessages(ctx context.Context, runID string, limit *int, cursor string) ([]RunMessage, error) {
	path := "/api/v1/private/hitl/runs/" + url.PathEscape(runID) + "/messages"
	q := map[string]string{}
	if limit != nil {
		q["limit"] = fmt.Sprintf("%d", *limit)
	}
	if cursor != "" {
		q["cursor"] = cursor
	}
	data, err := h.h.get(ctx, path, q)
	if err != nil {
		return nil, err
	}
	var v []RunMessage
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// RunToolCalls returns all tool calls for a pipeline run.
//
// GET /api/v1/private/hitl/runs/:runId/tool-calls
func (h *HitlAPI) RunToolCalls(ctx context.Context, runID string) ([]RunToolCall, error) {
	path := "/api/v1/private/hitl/runs/" + url.PathEscape(runID) + "/tool-calls"
	data, err := h.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v []RunToolCall
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}
