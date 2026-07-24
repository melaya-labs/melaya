// Pipelines API — embeddable lifecycle (build/run/read) plus overview, traces,
// and schedules.
//
// Embeddable lifecycle:  /api/v1/private/pipelines/*
// Overview:             /api/v1/private/overview/pipeline*
// Traces:               /api/v1/private/runs/:runId/traces
// Schedules:            /api/v1/private/pipeline-schedule
package melaya

import (
	"context"
	"fmt"
	"net/url"
	"strings"
)

// PipelinesAPI wraps pipeline embeddable lifecycle, overview, trace, and
// schedule endpoints.
type PipelinesAPI struct {
	h *httpClient
}

// ── Embeddable lifecycle: build → run → read ──────────────────────────────────
//
// Full CRUD + run + outputs over /api/v1/private/pipelines/*. Every method is
// tier-gated server-side (Forge+ to create/run) and tenant-scoped to the
// caller's projects.

// ListPipelines returns all pipelines visible to the caller.
//
// GET /api/v1/private/pipelines
func (p *PipelinesAPI) ListPipelines(ctx context.Context) ([]PipelineConfig, error) {
	data, err := p.h.get(ctx, "/api/v1/private/pipelines", nil)
	if err != nil {
		return nil, err
	}
	var env struct {
		Pipelines []PipelineConfig `json:"pipelines"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Pipelines, nil
}

// Create creates a new pipeline. name and project are required; include the
// full agent config (agents, prompts, models, wiring) in the same body map.
//
// POST /api/v1/private/pipelines
func (p *PipelinesAPI) Create(ctx context.Context, body PipelineConfig) (PipelineConfig, error) {
	data, err := p.h.post(ctx, "/api/v1/private/pipelines", body)
	if err != nil {
		return nil, err
	}
	var v PipelineConfig
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Get returns the full config for one pipeline. project is optional and narrows
// the tenant scope.
//
// GET /api/v1/private/pipelines/{name}?project=
func (p *PipelinesAPI) Get(ctx context.Context, name, project string) (PipelineConfig, error) {
	q := map[string]string{}
	if project != "" {
		q["project"] = project
	}
	path := "/api/v1/private/pipelines/" + url.PathEscape(name)
	data, err := p.h.get(ctx, path, q)
	if err != nil {
		return nil, err
	}
	var v PipelineConfig
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Update replaces a pipeline's config. Pass the full config plus the owning
// project. This is the path for editing per-agent prompts or swapping models.
//
// PUT /api/v1/private/pipelines/{name}
func (p *PipelinesAPI) Update(ctx context.Context, name string, body PipelineUpdateBody) (PipelineConfig, error) {
	path := "/api/v1/private/pipelines/" + url.PathEscape(name)
	data, err := p.h.put(ctx, path, body)
	if err != nil {
		return nil, err
	}
	var v PipelineConfig
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Delete deletes a pipeline.
//
// DELETE /api/v1/private/pipelines/{name}?project=
func (p *PipelinesAPI) Delete(ctx context.Context, name, project string) error {
	q := map[string]string{}
	if project != "" {
		q["project"] = project
	}
	path := "/api/v1/private/pipelines/" + url.PathEscape(name)
	_, err := p.h.del(ctx, path, q)
	return err
}

// Run triggers a pipeline run. Returns immediately with a run_id; subscribe to
// progress via Platform.Events.OnRunUpdate or poll RunStatus.
//
// POST /api/v1/private/pipelines/{name}/run
func (p *PipelinesAPI) Run(ctx context.Context, name string, opts *PipelineRunOptions) (*PipelineRunAccepted, error) {
	var body interface{} = map[string]interface{}{}
	if opts != nil {
		body = opts
	}
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/run"
	data, err := p.h.post(ctx, path, body)
	if err != nil {
		return nil, err
	}
	var v PipelineRunAccepted
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// RunIDs lists run IDs for a pipeline, filtered to the caller's tier retention window.
//
// GET /api/v1/private/pipelines/{name}/runs
func (p *PipelinesAPI) RunIDs(ctx context.Context, name string) ([]string, error) {
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/runs"
	data, err := p.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var env struct {
		RunIDs []string `json:"run_ids"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.RunIDs, nil
}

// RunStatus returns the status and best-effort cost for one run.
//
// GET /api/v1/private/pipelines/{name}/runs/{runID}
func (p *PipelinesAPI) RunStatus(ctx context.Context, name, runID string) (*PipelineRunStatus, error) {
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/runs/" + url.PathEscape(runID)
	data, err := p.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v PipelineRunStatus
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// CancelRun cancels or kills an in-flight run.
//
// DELETE /api/v1/private/pipelines/{name}/runs/{runID}
func (p *PipelinesAPI) CancelRun(ctx context.Context, name, runID string) error {
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/runs/" + url.PathEscape(runID)
	_, err := p.h.del(ctx, path, nil)
	return err
}

// Outputs lists the artifacts a pipeline has produced.
//
// GET /api/v1/private/pipelines/{name}/outputs
func (p *PipelinesAPI) Outputs(ctx context.Context, name string) (interface{}, error) {
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/outputs"
	data, err := p.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Output reads one output artifact by relative path. Each path segment is
// url-path-escaped individually. Pass download=true to force a download
// disposition on the wire.
//
// GET /api/v1/private/pipelines/{name}/outputs/{path}
func (p *PipelinesAPI) Output(ctx context.Context, name, artifactPath string, download bool) (interface{}, error) {
	segments := strings.Split(artifactPath, "/")
	escaped := make([]string, len(segments))
	for i, seg := range segments {
		escaped[i] = url.PathEscape(seg)
	}
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/outputs/" + strings.Join(escaped, "/")
	q := map[string]string{}
	if download {
		q["download"] = "1"
	}
	data, err := p.h.get(ctx, path, q)
	if err != nil {
		return nil, err
	}
	var v interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// PreviewCode generates a read-only code preview for a config (no persistence,
// no tier gate).
//
// POST /api/v1/private/pipelines/preview-code
func (p *PipelinesAPI) PreviewCode(ctx context.Context, config PipelineConfig) (interface{}, error) {
	data, err := p.h.post(ctx, "/api/v1/private/pipelines/preview-code", config)
	if err != nil {
		return nil, err
	}
	var v interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Tools returns the builder tool registry available to pipeline agents.
//
// GET /api/v1/private/pipelines/tools
func (p *PipelinesAPI) Tools(ctx context.Context) (interface{}, error) {
	data, err := p.h.get(ctx, "/api/v1/private/pipelines/tools", nil)
	if err != nil {
		return nil, err
	}
	var v interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Subagents returns the builder sub-agent/crew registry.
//
// GET /api/v1/private/pipelines/subagents
func (p *PipelinesAPI) Subagents(ctx context.Context) (interface{}, error) {
	data, err := p.h.get(ctx, "/api/v1/private/pipelines/subagents", nil)
	if err != nil {
		return nil, err
	}
	var v interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// InstantiateTemplate instantiates a pipeline from a template the caller can
// access. Merges the template payload with overrides, then creates the pipeline
// under name + project.
//
// POST /api/v1/private/templates/{templateID}/instantiate
func (p *PipelinesAPI) InstantiateTemplate(ctx context.Context, templateID string, body TemplateInstantiateBody) (*TemplateInstantiateResult, error) {
	path := "/api/v1/private/templates/" + url.PathEscape(templateID) + "/instantiate"
	data, err := p.h.post(ctx, path, body)
	if err != nil {
		return nil, err
	}
	var v TemplateInstantiateResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// BuildWithAI builds a pipeline from a natural-language brief (synchronous —
// returns the generated config). For streamed progress, use the SSE endpoint
// /api/v1/private/ai/build-pipeline directly.
//
// POST /api/v1/private/ai/build-pipeline/sync
func (p *PipelinesAPI) BuildWithAI(ctx context.Context, brief map[string]interface{}) (interface{}, error) {
	data, err := p.h.post(ctx, "/api/v1/private/ai/build-pipeline/sync", brief)
	if err != nil {
		return nil, err
	}
	var v interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ── Overview ─────────────────────────────────────────────────────────────────

// Overview returns the dashboard summary (usage stats, active strategies, recent runs).
//
// GET /api/v1/private/overview
func (p *PipelinesAPI) Overview(ctx context.Context) (*OverviewSummary, error) {
	data, err := p.h.get(ctx, "/api/v1/private/overview", nil)
	if err != nil {
		return nil, err
	}
	var v OverviewSummary
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Count returns pipeline run counts grouped by status.
//
// GET /api/v1/private/overview/pipeline-count
func (p *PipelinesAPI) Count(ctx context.Context) (*PipelineCountByStatus, error) {
	data, err := p.h.get(ctx, "/api/v1/private/overview/pipeline-count", nil)
	if err != nil {
		return nil, err
	}
	var v PipelineCountByStatus
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// List returns a paginated list of pipeline runs.
//
// GET /api/v1/private/overview/pipelines
func (p *PipelinesAPI) List(ctx context.Context, params *PipelineListParams) ([]PipelineRun, error) {
	q := map[string]string{}
	if params != nil {
		if params.Project != "" {
			q["project"] = params.Project
		}
		if params.PipelineName != "" {
			q["pipelineName"] = params.PipelineName
		}
		if params.Status != "" {
			q["status"] = params.Status
		}
		if params.Limit != nil {
			q["limit"] = fmt.Sprintf("%d", *params.Limit)
		}
		if params.Offset != nil {
			q["offset"] = fmt.Sprintf("%d", *params.Offset)
		}
		if params.Page != nil {
			q["page"] = fmt.Sprintf("%d", *params.Page)
		}
	}
	data, err := p.h.get(ctx, "/api/v1/private/overview/pipelines", q)
	if err != nil {
		return nil, err
	}
	var v []PipelineRun
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Recent returns the most recent pipeline runs for dashboard widgets.
//
// GET /api/v1/private/overview/pipelines/recent
func (p *PipelinesAPI) Recent(ctx context.Context) ([]PipelineRun, error) {
	data, err := p.h.get(ctx, "/api/v1/private/overview/pipelines/recent", nil)
	if err != nil {
		return nil, err
	}
	var v []PipelineRun
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ── Traces ───────────────────────────────────────────────────────────────────

// Traces returns the paginated trace list for a run.
// The server returns { data: { list, total, page, pageSize } }.
//
// GET /api/v1/private/runs/:runId/traces
func (p *PipelinesAPI) Traces(ctx context.Context, runID string) (*TracePage, error) {
	path := "/api/v1/private/runs/" + url.PathEscape(runID) + "/traces"
	data, err := p.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var env struct {
		Data TracePage `json:"data"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return &env.Data, nil
}

// Trace returns a single trace by ID.
//
// GET /api/v1/private/runs/:runId/traces/:traceId
func (p *PipelinesAPI) Trace(ctx context.Context, runID, traceID string) (*Trace, error) {
	path := "/api/v1/private/runs/" + url.PathEscape(runID) + "/traces/" + url.PathEscape(traceID)
	data, err := p.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v Trace
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// TraceStats returns statistics for a specific trace.
//
// GET /api/v1/private/runs/:runId/traces/:traceId/stats
func (p *PipelinesAPI) TraceStats(ctx context.Context, runID, traceID string) (*TraceStats, error) {
	path := "/api/v1/private/runs/" + url.PathEscape(runID) + "/traces/" + url.PathEscape(traceID) + "/stats"
	data, err := p.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v TraceStats
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// DeleteTraces deletes all traces for a run by runId (no body required).
// Returns the number of spans deleted.
//
// DELETE /api/v1/private/runs/:runId/traces
func (p *PipelinesAPI) DeleteTraces(ctx context.Context, runID string) (*DeleteTracesResult, error) {
	path := "/api/v1/private/runs/" + url.PathEscape(runID) + "/traces"
	data, err := p.h.del(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v DeleteTracesResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// ── Schedules ────────────────────────────────────────────────────────────────

// ListSchedules lists all pipeline schedules accessible to the caller.
//
// GET /api/v1/private/pipeline-schedule
func (p *PipelinesAPI) ListSchedules(ctx context.Context) ([]PipelineSchedule, error) {
	data, err := p.h.get(ctx, "/api/v1/private/pipeline-schedule", nil)
	if err != nil {
		return nil, err
	}
	var v []PipelineSchedule
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// GetSchedule returns the schedule status for a specific pipeline.
//
// GET /api/v1/private/pipeline-schedule/:project/:pipelineName
func (p *PipelinesAPI) GetSchedule(ctx context.Context, project, pipelineName string) (*PipelineSchedule, error) {
	path := "/api/v1/private/pipeline-schedule/" + url.PathEscape(project) + "/" + url.PathEscape(pipelineName)
	data, err := p.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v PipelineSchedule
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// UpsertSchedule creates or updates a pipeline schedule.
//
// PUT /api/v1/private/pipeline-schedule/:project/:pipelineName
func (p *PipelinesAPI) UpsertSchedule(ctx context.Context, project, pipelineName string, body PipelineScheduleUpsertBody) (*PipelineSchedule, error) {
	path := "/api/v1/private/pipeline-schedule/" + url.PathEscape(project) + "/" + url.PathEscape(pipelineName)
	data, err := p.h.put(ctx, path, body)
	if err != nil {
		return nil, err
	}
	var v PipelineSchedule
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// PauseSchedule pauses a pipeline schedule.
//
// POST /api/v1/private/pipeline-schedule/:project/:pipelineName/pause
func (p *PipelinesAPI) PauseSchedule(ctx context.Context, project, pipelineName string) (bool, error) {
	path := "/api/v1/private/pipeline-schedule/" + url.PathEscape(project) + "/" + url.PathEscape(pipelineName) + "/pause"
	data, err := p.h.post(ctx, path, nil)
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}

// ResumeSchedule resumes a paused pipeline schedule.
//
// POST /api/v1/private/pipeline-schedule/:project/:pipelineName/resume
func (p *PipelinesAPI) ResumeSchedule(ctx context.Context, project, pipelineName string) (bool, error) {
	path := "/api/v1/private/pipeline-schedule/" + url.PathEscape(project) + "/" + url.PathEscape(pipelineName) + "/resume"
	data, err := p.h.post(ctx, path, nil)
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}
