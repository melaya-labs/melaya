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
	"io"
	"net/url"
	"strconv"
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

// Create creates a new pipeline. name and project are required. The pipeline
// itself is generated ONLY from the config's steps[] — a top-level agents[]
// array alone produces an EMPTY pipeline, so embed each agent's full
// definition (role, instruction, model, agent_tools, human_approval_tools)
// inside its own step. See PipelineConfig for the field reference.
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

// Get returns the server's ENVELOPE for one pipeline:
// { name, client, config, code, docs } — NOT the bare pipeline config. The
// editable pipeline configuration (steps[], hitl_mode, connector_source, …)
// lives under the envelope's "config" key. project is optional and narrows
// the tenant scope.
//
// To edit and save a pipeline, mutate envelope["config"] and pass THAT to
// Update — not the envelope itself:
//
//	envelope, _ := m.Pipelines.Get(ctx, "daily-digest", "acme")
//	config := envelope["config"].(map[string]interface{}) // mutate steps[], etc. here
//	_, err := m.Pipelines.Update(ctx, "daily-digest", melaya.PipelineUpdateBody{
//	    Config: config, Project: "acme",
//	})
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
// project. This is the path for editing per-agent prompts or swapping models
// — body.Config should be the "config" key from a prior Get() envelope
// (mutated as needed), not the envelope itself. See Get's doc comment for a
// full edit-then-update example.
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
// progress via Platform.Events.OnRunUpdate or poll RunStatus. Pass
// opts.RunInputs to thread a brief and/or named input values (including
// uploaded files — see UploadRunFile) through to the run; the response echoes
// the resolved RunInputs back when any were sent.
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

// UploadRunFile uploads a file for use in a future Run call, returning a
// single-use file_id (valid 24h) to pass as a RunInputs.Values entry:
// map[string]interface{}{"file_id": result.FileID}. key identifies which
// run-input slot the file fills (matches one of the pipeline's declared
// inputs[]).
//
// POST /api/v1/private/pipelines/{name}/run-files?key={key}[&project=]
// multipart/form-data, single field "file".
func (p *PipelinesAPI) UploadRunFile(ctx context.Context, name, key string, file io.Reader, filename string, opts *UploadRunFileOptions) (*RunFileUploadResult, error) {
	q := map[string]string{"key": key}
	contentType := ""
	if opts != nil {
		if opts.Project != "" {
			q["project"] = opts.Project
		}
		contentType = opts.ContentType
	}
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/run-files"
	data, err := p.h.postMultipart(ctx, path, q, "file", filename, contentType, file, nil)
	if err != nil {
		return nil, err
	}
	var v RunFileUploadResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// SetInputs replaces ONLY the pipeline's declared run inputs (config.inputs),
// without touching the rest of the config. Max 30; an empty slice removes them
// all. Editor or owner only; a bad declaration returns HTTP 422
// "run_inputs_invalid: <reason>". Keep keys stable: {{inputs.<key>}}
// placeholders in agent instructions use them.
//
// PUT /api/v1/private/pipelines/{name}/inputs
func (p *PipelinesAPI) SetInputs(ctx context.Context, name, project string, inputs []PipelineInputDeclaration) (*PipelineInputsUpdateResult, error) {
	if inputs == nil {
		inputs = []PipelineInputDeclaration{}
	}
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/inputs"
	data, err := p.h.put(ctx, path, map[string]interface{}{"inputs": inputs, "project": project})
	if err != nil {
		return nil, err
	}
	var v PipelineInputsUpdateResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// RunInputs returns the persisted run-inputs record for a run: the brief,
// values, and file references originally passed to Run. runID is 16 hex
// characters.
//
// GET /api/v1/private/pipelines/{name}/runs/{runId}/inputs
func (p *PipelinesAPI) RunInputs(ctx context.Context, name, runID string) (*PipelineRunInputs, error) {
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/runs/" + url.PathEscape(runID) + "/inputs"
	data, err := p.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v PipelineRunInputs
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// RunInputFile downloads one uploaded run-input file by its position (0-99)
// in the run's original run_inputs.values file list. Returns the raw file
// bytes — this is a binary download, do NOT JSON-decode the result.
//
// GET /api/v1/private/pipelines/{name}/runs/{runId}/inputs/files/{index}
func (p *PipelinesAPI) RunInputFile(ctx context.Context, name, runID string, index int) ([]byte, error) {
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/runs/" + url.PathEscape(runID) +
		"/inputs/files/" + strconv.Itoa(index)
	return p.h.getBytes(ctx, path, nil)
}

// RunActive reports whether a run is still active (a liveness poll for
// cloud-spawn runs).
//
// GET /api/v1/private/pipelines/{name}/runs/{runId}/active
func (p *PipelinesAPI) RunActive(ctx context.Context, name, runID string) (bool, error) {
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/runs/" + url.PathEscape(runID) + "/active"
	data, err := p.h.get(ctx, path, nil)
	if err != nil {
		return false, err
	}
	var v struct {
		Active bool `json:"active"`
	}
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Active, nil
}

// ── Static-context documents ────────────────────────────────────────────────
// Documents injected verbatim into an agent's context. Allowed extensions:
// .txt .md .pdf .csv .json .docx .doc .pptx .xlsx.

// ListDocs lists the static-context documents attached to a pipeline.
//
// GET /api/v1/private/pipelines/{name}/docs
func (p *PipelinesAPI) ListDocs(ctx context.Context, name string) (interface{}, error) {
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/docs"
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

// UploadDoc uploads a static-context document for a pipeline. Allowed
// extensions: .txt .md .pdf .csv .json .docx .doc .pptx .xlsx.
//
// POST /api/v1/private/pipelines/{name}/docs, multipart field "file".
func (p *PipelinesAPI) UploadDoc(ctx context.Context, name string, file io.Reader, filename string, opts *UploadDocOptions) (interface{}, error) {
	contentType := ""
	if opts != nil {
		contentType = opts.ContentType
	}
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/docs"
	data, err := p.h.postMultipart(ctx, path, nil, "file", filename, contentType, file, nil)
	if err != nil {
		return nil, err
	}
	var v interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// DeleteDoc deletes a static-context document by filename. The response shape
// is whatever the builder reports (proxied as-is), so it is returned untyped.
//
// DELETE /api/v1/private/pipelines/{name}/docs/{filename}
func (p *PipelinesAPI) DeleteDoc(ctx context.Context, name, filename string) (interface{}, error) {
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/docs/" + url.PathEscape(filename)
	data, err := p.h.del(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ── RAG (retrieval) documents ───────────────────────────────────────────────
// Documents embedded and queried by similarity, rather than injected verbatim.

// UploadRetrievalDoc uploads a RAG (retrieval-mode) document for a pipeline.
//
// POST /api/v1/private/pipelines/{name}/docs/retrieval, multipart field "file".
func (p *PipelinesAPI) UploadRetrievalDoc(ctx context.Context, name string, file io.Reader, filename string, opts *UploadDocOptions) (interface{}, error) {
	contentType := ""
	if opts != nil {
		contentType = opts.ContentType
	}
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/docs/retrieval"
	data, err := p.h.postMultipart(ctx, path, nil, "file", filename, contentType, file, nil)
	if err != nil {
		return nil, err
	}
	var v interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// IngestRetrieval embeds changed retrieval documents with the pipeline's
// configured embedder. This can take minutes on a large corpus — if you have
// many or large documents, construct the Client with a longer
// Options.Timeout (e.g. 300s) before calling this method, since every request
// on a Client shares its HTTP timeout. body is sent as-is (an empty/nil body
// is sent as {}).
//
// POST /api/v1/private/pipelines/{name}/docs/retrieval/ingest
func (p *PipelinesAPI) IngestRetrieval(ctx context.Context, name string, body map[string]interface{}) (interface{}, error) {
	if body == nil {
		body = map[string]interface{}{}
	}
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/docs/retrieval/ingest"
	data, err := p.h.post(ctx, path, body)
	if err != nil {
		return nil, err
	}
	var v interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// DeleteRetrievalDoc deletes a RAG retrieval document by filename. The
// response shape is whatever the builder reports (proxied as-is), so it is
// returned untyped.
//
// DELETE /api/v1/private/pipelines/{name}/docs/retrieval/{filename}
func (p *PipelinesAPI) DeleteRetrievalDoc(ctx context.Context, name, filename string) (interface{}, error) {
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/docs/retrieval/" + url.PathEscape(filename)
	data, err := p.h.del(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// DocsPreview returns per-document extraction stats of the static-context
// documents (characters kept per file), with caps for the model the agents
// use. Set opts.ModelName / opts.ModelProvider to see what fits that model's
// context window; opts may be nil.
//
// GET /api/v1/private/pipelines/{name}/docs/preview[?model_name=&model_provider=]
func (p *PipelinesAPI) DocsPreview(ctx context.Context, name string, opts *DocsPreviewOptions) (interface{}, error) {
	q := map[string]string{}
	if opts != nil {
		q["model_name"] = opts.ModelName
		q["model_provider"] = opts.ModelProvider
	}
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/docs/preview"
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

// RetrievalPreview returns stats of the pipeline's retrieval store
// (documents, chunks, embedder).
//
// GET /api/v1/private/pipelines/{name}/docs/retrieval/preview
func (p *PipelinesAPI) RetrievalPreview(ctx context.Context, name string) (interface{}, error) {
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/docs/retrieval/preview"
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

// TestRetrieve runs a sample query against the pipeline's retrieval store and
// returns the passages agents would get. limit is 1-20; pass 0 to use the
// server default (5). Needs an embedder configured; a store built with another
// embedder answers HTTP 409 (re-ingest).
//
// POST /api/v1/private/pipelines/{name}/docs/retrieval/test_retrieve
func (p *PipelinesAPI) TestRetrieve(ctx context.Context, name, query string, limit int) (interface{}, error) {
	body := map[string]interface{}{"query": query}
	if limit > 0 {
		body["limit"] = limit
	}
	path := "/api/v1/private/pipelines/" + url.PathEscape(name) + "/docs/retrieval/test_retrieve"
	data, err := p.h.post(ctx, path, body)
	if err != nil {
		return nil, err
	}
	var v interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
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

// ── Tool-call audit ──────────────────────────────────────────────────────────

// ProjectToolCalls returns a project's tool-call audit ledger: every tool
// invocation across the project's runs (tool, invoking agent, pipeline + run,
// who ran it, status, latency, HITL approval provenance, 4KB-truncated
// input/output). Keyset-paginated for the "recent"/"oldest" sorts — pass the
// result's NextCursor fields back as params.BeforeCreatedAt/BeforeID to page
// further. The "slowest"/"fastest" sorts return a bounded top-N snapshot
// instead (Capped=true, no cursor). params may be nil for the defaults.
//
// GET /api/v1/private/projects/{project}/tool-calls
func (p *PipelinesAPI) ProjectToolCalls(ctx context.Context, project string, params *ProjectToolCallsParams) (*ProjectToolCallsResult, error) {
	q := map[string]string{}
	if params != nil {
		if params.BeforeCreatedAt != "" {
			q["beforeCreatedAt"] = params.BeforeCreatedAt
		}
		if params.BeforeID != "" {
			q["beforeId"] = params.BeforeID
		}
		if params.Limit > 0 {
			q["limit"] = strconv.Itoa(params.Limit)
		}
		if params.Tool != "" {
			q["tool"] = params.Tool
		}
		if params.Agent != "" {
			q["agent"] = params.Agent
		}
		if params.RunID != "" {
			q["runId"] = params.RunID
		}
		if params.Status != "" {
			q["status"] = params.Status
		}
		if params.Search != "" {
			q["search"] = params.Search
		}
		if params.ConnectorSource != "" {
			q["connectorSource"] = params.ConnectorSource
		}
		if params.Approval != "" {
			q["approval"] = params.Approval
		}
		if params.Provider != "" {
			q["provider"] = params.Provider
		}
		if params.Sort != "" {
			q["sort"] = params.Sort
		}
	}
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/tool-calls"
	data, err := p.h.get(ctx, path, q)
	if err != nil {
		return nil, err
	}
	var v ProjectToolCallsResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// ProjectToolCallFacets returns the distinct tools (with call counts),
// agents, approvers, and providers seen in a project's tool-call ledger —
// powers the audit UI's filter dropdowns.
//
// GET /api/v1/private/projects/{project}/tool-calls/facets
func (p *PipelinesAPI) ProjectToolCallFacets(ctx context.Context, project string) (*ProjectToolCallFacets, error) {
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/tool-calls/facets"
	data, err := p.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v ProjectToolCallFacets
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// ToolCallDetail returns the full, untruncated input/output for one tool-call
// span within a run (access-checked against the run).
//
// GET /api/v1/private/runs/{runId}/tool-calls/{spanId}
func (p *PipelinesAPI) ToolCallDetail(ctx context.Context, runID, spanID string) (*ToolCallDetail, error) {
	path := "/api/v1/private/runs/" + url.PathEscape(runID) + "/tool-calls/" + url.PathEscape(spanID)
	data, err := p.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v ToolCallDetail
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
