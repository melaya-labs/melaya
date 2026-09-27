# frozen_string_literal: true

module Melaya
  # Pipelines API — overview dashboard, pipeline run listing, traces, and
  # cron-based scheduling.
  #
  # Maps to:
  #   /api/v1/private/overview/*         — dashboard + run listing
  #   /api/v1/private/runs/:id/traces/*  — distributed traces
  #   /api/v1/private/pipeline-schedule  — cron scheduling
  #   /api/v1/version                    — server version (public)
  #
  # @example
  #   runs = melaya.pipelines.list(project: "my-project", limit: 20)
  #   melaya.pipelines.upsert_schedule("my-project", "nightly-report",
  #     cron: "0 2 * * *")
  class PipelinesAPI
    def initialize(http)
      @http = http
    end

    # ── Overview ───────────────────────────────────────────────────────────────

    # GET /api/v1/private/overview
    # Dashboard overview: usage stats, active strategies, recent runs.
    def overview
      @http.get("/api/v1/private/overview")
    end

    # GET /api/v1/private/overview/model-prices
    # Get pricing data for available AI models.
    def model_prices
      @http.get("/api/v1/private/overview/model-prices")
    end

    # GET /api/v1/private/overview/chart
    # Get chart data for overview dashboard (cost/usage over time).
    def chart_data(params = {})
      @http.get("/api/v1/private/overview/chart", params)
    end

    # GET /api/v1/private/overview/cost-breakdown
    # Get cost breakdown by model/provider.
    def cost_breakdown(params = {})
      @http.get("/api/v1/private/overview/cost-breakdown", params)
    end

    # GET /api/v1/private/overview/pipeline-count
    # Count of pipeline runs grouped by status.
    def count
      @http.get("/api/v1/private/overview/pipeline-count")
    end

    # GET /api/v1/private/overview/pipelines
    # Paginated list of pipeline runs.
    # @param project [String, nil]
    # @param pipeline_name [String, nil]
    # @param status [String, nil]
    # @param limit [Integer, nil]
    # @param offset [Integer, nil]
    def list(project: nil, pipeline_name: nil, status: nil, limit: nil, offset: nil)
      @http.get("/api/v1/private/overview/pipelines",
        compact("project" => project, "pipelineName" => pipeline_name,
                "status"  => status,  "limit"        => limit,
                "offset"  => offset))
    end

    # GET /api/v1/private/overview/pipelines/recent
    # Most recent pipeline runs for a dashboard widget.
    def recent
      @http.get("/api/v1/private/overview/pipelines/recent")
    end

    # ── Traces ─────────────────────────────────────────────────────────────────

    # GET /api/v1/private/runs/:runId/traces
    # List traces for a run (paginated).
    #
    # Returns a paginated envelope, NOT a flat array:
    #   {
    #     "data" => {
    #       "list"     => Array<Hash>,   # trace summaries
    #       "total"    => Integer,        # total matching traces
    #       "page"     => Integer,
    #       "pageSize" => Integer
    #     }
    #   }
    #
    # @param run_id [String]
    # @param params [Hash] optional pagination / filter params (page:, pageSize:, ...)
    # @return [Hash] paginated envelope as described above
    def traces(run_id, params = {})
      @http.get("/api/v1/private/runs/#{enc(run_id)}/traces", params)
    end

    # GET /api/v1/private/runs/:runId/traces/:traceId
    # Get a single trace by ID.
    def trace(run_id, trace_id)
      @http.get("/api/v1/private/runs/#{enc(run_id)}/traces/#{enc(trace_id)}")
    end

    # GET /api/v1/private/runs/:runId/traces/:traceId/stats
    # Get statistics for a specific trace.
    def trace_stats(run_id, trace_id)
      @http.get("/api/v1/private/runs/#{enc(run_id)}/traces/#{enc(trace_id)}/stats")
    end

    # DELETE /api/v1/private/runs/:runId/traces
    # Delete all traces (and their spans) for a run by runId.
    # No request body required — the runId in the path identifies the target.
    #
    # @param run_id [String]
    # @return [Hash] { "deletedSpans" => Integer, "requestedTraces" => Integer (optional) }
    def delete_traces(run_id)
      @http.delete("/api/v1/private/runs/#{enc(run_id)}/traces")
    end

    # ── Tool-call audit ──────────────────────────────────────────────────────────
    # Project-wide tool-invocation ledger — the same feed behind the Logs page.
    # Every tool call across the project's runs, with HITL/connector/provider
    # provenance. Argument/result previews are 4KB-truncated here; use
    # +tool_call_detail+ for the untruncated pair on one call.

    # GET /api/v1/private/projects/:project/tool-calls
    # Keyset-paginated; pass the previous page's +nextCursor+ fields back as
    # +before_created_at+/+before_id+ to continue.
    # @param project [String]
    # @param before_created_at [String, nil] keyset cursor (paired with before_id)
    # @param before_id [String, nil]
    # @param limit [Integer, nil] 1..100, default 30
    # @param tool [String, nil] exact tool name
    # @param agent [String, nil] invoking agent name (substring match)
    # @param run_id [String, nil]
    # @param status [String, nil] "ok" | "error"
    # @param search [String, nil] tool-name search
    # @param connector_source [String, nil] "project" | "personal"
    # @param approval [String, nil] "auto" | "approved" | "by:<username>"
    # @param provider [String, nil] AI provider that produced the tool call
    # @param sort [String, nil] "recent" | "oldest" | "slowest" | "fastest"
    # @return [Hash] { "items" => Array<Hash>, "nextCursor" => Hash|nil, "capped" => Boolean }
    def project_tool_calls(project, before_created_at: nil, before_id: nil, limit: nil,
                            tool: nil, agent: nil, run_id: nil, status: nil, search: nil,
                            connector_source: nil, approval: nil, provider: nil, sort: nil)
      params = compact(
        "beforeCreatedAt" => before_created_at,
        "beforeId"        => before_id,
        "limit"           => limit,
        "tool"            => tool,
        "agent"           => agent,
        "runId"           => run_id,
        "status"          => status,
        "search"          => search,
        "connectorSource" => connector_source,
        "approval"        => approval,
        "provider"        => provider,
        "sort"            => sort
      )
      @http.get("/api/v1/private/projects/#{enc(project)}/tool-calls", params)
    end

    # GET /api/v1/private/projects/:project/tool-calls/facets
    # Distinct tools (with call counts) and agents seen in the project's
    # tool-call ledger — powers the audit UI's filter dropdowns.
    # @param project [String]
    # @return [Hash] { "tools" => [{ "name" => String, "count" => Integer }], "agents" => Array<String> }
    def project_tool_call_facets(project)
      @http.get("/api/v1/private/projects/#{enc(project)}/tool-calls/facets")
    end

    # GET /api/v1/private/runs/:runId/tool-calls/:spanId
    # Full (untruncated) arguments + result for a single tool-call span.
    # @param run_id [String]
    # @param span_id [String]
    def tool_call_detail(run_id, span_id)
      @http.get("/api/v1/private/runs/#{enc(run_id)}/tool-calls/#{enc(span_id)}")
    end

    # ── Schedule ───────────────────────────────────────────────────────────────

    # GET /api/v1/private/pipeline-schedule
    # List all pipeline schedules accessible to the caller.
    def list_schedules
      @http.get("/api/v1/private/pipeline-schedule")
    end

    # GET /api/v1/private/pipeline-schedule/:project/:pipelineName
    # Get schedule status for a pipeline.
    # @param project [String]
    # @param pipeline_name [String]
    def get_schedule(project, pipeline_name)
      @http.get("/api/v1/private/pipeline-schedule/#{enc(project)}/#{enc(pipeline_name)}")
    end

    # PUT /api/v1/private/pipeline-schedule/:project/:pipelineName
    # Create or update a pipeline schedule (cron expression + optional config).
    # @param project [String]
    # @param pipeline_name [String]
    # @param cron [String] cron expression e.g. "0 2 * * *"
    # @param config [Hash, nil] optional pipeline config overrides
    def upsert_schedule(project, pipeline_name, cron:, config: nil)
      body = compact("cron" => cron, "config" => config)
      @http.put("/api/v1/private/pipeline-schedule/#{enc(project)}/#{enc(pipeline_name)}", body)
    end

    # POST /api/v1/private/pipeline-schedule/:project/:pipelineName/pause
    # Pause a pipeline schedule.
    def pause_schedule(project, pipeline_name)
      @http.post("/api/v1/private/pipeline-schedule/#{enc(project)}/#{enc(pipeline_name)}/pause")
    end

    # POST /api/v1/private/pipeline-schedule/:project/:pipelineName/resume
    # Resume a paused pipeline schedule.
    def resume_schedule(project, pipeline_name)
      @http.post("/api/v1/private/pipeline-schedule/#{enc(project)}/#{enc(pipeline_name)}/resume")
    end

    # ── Pipeline lifecycle (CRUD + run + outputs + AI build) ──────────────────

    # GET /api/v1/private/pipelines
    # List all pipeline configs accessible to the caller.
    # Returns { "pipelines" => [...] }.
    def list_pipelines
      @http.get("/api/v1/private/pipelines")
    end

    # POST /api/v1/private/pipelines
    # Create a new pipeline config.
    # @param name [String] pipeline name
    # @param project [String] owning project
    # @param description [String, nil]
    # @param config [Hash] additional config keys merged into the request body
    # @return [Hash] created pipeline config
    def create(name:, project:, description: nil, **config)
      body = compact(
        "name"        => name,
        "project"     => project,
        "description" => description
      ).merge(stringify_keys(config))
      @http.post("/api/v1/private/pipelines", body)
    end

    # GET /api/v1/private/pipelines/:name
    # Fetch a single pipeline by name.
    #
    # Returns an ENVELOPE, not a bare config:
    #   { "name" => String, "client" => ..., "config" => Hash, "code" => String, "docs" => ... }
    # To edit and save, mutate +envelope["config"]+ and pass THAT to +update+ —
    # see the example below.
    #
    # @param name [String] pipeline name
    # @param project [String, nil] owning project (disambiguates when multiple projects share a name)
    # @return [Hash] envelope: { "name", "client", "config", "code", "docs" }
    #
    # @example Edit one agent's model, then save
    #   envelope = melaya.pipelines.get("daily-digest", project: "acme")
    #   config   = envelope["config"]
    #   config["steps"][0]["agent"]["model"] = { "provider" => "anthropic", "name" => "claude-opus-4-8" }
    #   melaya.pipelines.update("daily-digest", config: config, project: "acme")
    def get(name, project: nil)
      params = compact("project" => project)
      @http.get("/api/v1/private/pipelines/#{enc(name)}", params)
    end

    # PUT /api/v1/private/pipelines/:name
    # Replace a pipeline's config. Pass the FULL config Hash — typically
    # +envelope["config"]+ returned by +get+, mutated in place. This is the
    # path for editing a per-agent prompt/instruction or swapping a model on
    # one or all agents.
    #
    # The run is generated ONLY from +config["steps"]+ — a top-level
    # +config["agents"]+ list alone produces an EMPTY pipeline. Every agent a
    # step runs must be embedded inline on that step, e.g.:
    #   { "kind" => "agent", "agent" => {
    #       "name" => "researcher", "role" => "...", "instruction" => "...",
    #       "model" => { "provider" => "anthropic", "name" => "claude-sonnet-4-6" },
    #       "agent_tools" => [...], "human_approval_tools" => [...] } }
    # There is no +prompt+ field — the two prompt fields are +instruction+
    # (the task) and, optionally, +system_prompt_override+.
    #
    # Other config fields worth knowing:
    #   "hitl_mode"          — "safe" (default) | "autonomous" | "payments_only".
    #                          Only "safe" honours each agent's +human_approval_tools+.
    #   "connector_source"   — "personal" | "project"
    #   "force_local_runner" — Boolean
    #   "inputs"             — Array of declared run-input fields (see +run+)
    #
    # @param name [String] pipeline name
    # @param config [Hash] full pipeline config payload (see +get+)
    # @param project [String, nil] owning project
    # @return [Hash] updated pipeline config
    def update(name, config:, project: nil)
      body = compact("config" => config, "project" => project)
      @http.put("/api/v1/private/pipelines/#{enc(name)}", body)
    end

    # DELETE /api/v1/private/pipelines/:name
    # Delete a pipeline config.
    # @param name [String] pipeline name
    # @param project [String, nil] owning project
    # @return [Hash] empty hash on success
    def delete_pipeline(name, project: nil)
      params = compact("project" => project)
      @http.delete("/api/v1/private/pipelines/#{enc(name)}", params)
    end

    # POST /api/v1/private/pipelines/:name/run
    # Enqueue a pipeline run.
    # @param name [String] pipeline name
    # @param project [String, nil]
    # @param execution_target [String, nil] used ONLY for the tier check made
    #   at enqueue time. Where the run actually EXECUTES is decided by the
    #   pipeline's own stored config (local model providers / +force_local_runner+),
    #   not by this value.
    # @param studio_url [String, nil] override studio URL
    # @param env_overrides [Hash, nil] per-run environment variable overrides,
    #   layered over the caller's stored credentials. +MEL_*+ and +MELAYA_*+
    #   keys are always stripped server-side — they can never be overridden
    #   from the client.
    # @param run_inputs [Hash, nil] free-form run inputs:
    #   +{ brief: String, values: { key => value_or_file_ref } }+.
    #   A file value inside +values+ may be +{ "file_id" => ... }+ (from
    #   +upload_run_file+), +{ "url" => ... }+ (≤25 MB, https only), or
    #   +{ "base64" => ..., "name" => ... }+ (≤7 MB).
    # @return [Hash] { "run_id" => String, "queued" => Boolean, "run_inputs" => Hash (optional echo) }
    def run(name, project: nil, execution_target: nil, studio_url: nil, env_overrides: nil, run_inputs: nil)
      body = compact(
        "project"         => project,
        "executionTarget" => execution_target,
        "studio_url"      => studio_url,
        "env_overrides"   => env_overrides,
        "run_inputs"      => run_inputs
      )
      @http.post("/api/v1/private/pipelines/#{enc(name)}/run", body.empty? ? nil : body)
    end

    # POST /api/v1/private/pipelines/:name/run-files?key=...[&project=...]
    # Upload a file for a LATER run (before calling +run+), as
    # +multipart/form-data+ with a single field "file". Returns
    # +{ "file_id" => String, ... }+ — single-use, valid 24 h. Reference it
    # from +run+'s +run_inputs+ as +values: { <key> => { "file_id" => file_id } }+.
    # @param name [String] pipeline name
    # @param key [String] the declared run-input key this file is for
    # @param file [String, IO] raw file bytes, or an IO/File-like object (must respond to +#read+)
    # @param project [String, nil]
    # @param filename [String, nil] defaults to the file's own name, else "file"
    # @param content_type [String, nil] defaults to "application/octet-stream"
    # @return [Hash] { "file_id" => String, ... }
    def upload_run_file(name, key, file, project: nil, filename: nil, content_type: nil)
      bytes, fname = file_payload(file, filename)
      query = compact("key" => key, "project" => project)
      @http.post_multipart(
        "/api/v1/private/pipelines/#{enc(name)}/run-files", query,
        "file", bytes, fname, content_type
      )
    end

    # GET /api/v1/private/pipelines/:name/runs/:runId/inputs
    # What a run was started with (brief, values, files echo).
    # @param name [String] pipeline name
    # @param run_id [String] 16 hex-char run id
    # @return [Hash]
    def run_inputs(name, run_id)
      @http.get("/api/v1/private/pipelines/#{enc(name)}/runs/#{enc(run_id)}/inputs")
    end

    # GET /api/v1/private/pipelines/:name/runs/:runId/inputs/files/:index
    # Download one input file attached to a run.
    #
    # Returns RAW BYTES — do not JSON-parse the result.
    #
    # @param name [String] pipeline name
    # @param run_id [String] 16 hex-char run id
    # @param index [Integer] file index, 0..99
    # @return [String] raw binary file content
    def run_input_file(name, run_id, index)
      @http.get_bytes("/api/v1/private/pipelines/#{enc(name)}/runs/#{enc(run_id)}/inputs/files/#{index}")
    end

    # GET /api/v1/private/pipelines/:name/runs/:runId/active
    # Liveness poll for a run (cloud-spawn process presence).
    # @param name [String] pipeline name
    # @param run_id [String] run identifier
    # @return [Hash] { "active" => Boolean }
    def run_active(name, run_id)
      @http.get("/api/v1/private/pipelines/#{enc(name)}/runs/#{enc(run_id)}/active")
    end

    # GET /api/v1/private/pipelines/:name/runs
    # List all run IDs for a pipeline.
    # @param name [String] pipeline name
    # @return [Hash] { "run_ids" => Array<String> }
    def run_ids(name)
      @http.get("/api/v1/private/pipelines/#{enc(name)}/runs")
    end

    # GET /api/v1/private/pipelines/:name/runs/:run_id
    # Get the status of a specific pipeline run.
    # @param name [String] pipeline name
    # @param run_id [String] run identifier
    # @return [Hash] { "runId", "status", "createdAt", "executionTarget", "cost" }
    def run_status(name, run_id)
      @http.get("/api/v1/private/pipelines/#{enc(name)}/runs/#{enc(run_id)}")
    end

    # DELETE /api/v1/private/pipelines/:name/runs/:run_id
    # Cancel an in-progress pipeline run.
    # @param name [String] pipeline name
    # @param run_id [String] run identifier
    # @return [Hash] empty hash on success
    def cancel_run(name, run_id)
      @http.delete("/api/v1/private/pipelines/#{enc(name)}/runs/#{enc(run_id)}")
    end

    # GET /api/v1/private/pipelines/:name/outputs
    # List all output artifacts produced by a pipeline.
    # @param name [String] pipeline name
    # @return artifacts listing
    def outputs(name)
      @http.get("/api/v1/private/pipelines/#{enc(name)}/outputs")
    end

    # GET /api/v1/private/pipelines/:name/outputs/:path
    # Fetch a specific output artifact. Each segment of +path+ is individually
    # URL-encoded so slashes in segment values are preserved as separators.
    # @param name [String] pipeline name
    # @param path [String] artifact path, e.g. "reports/2024-01/summary.json"
    # @param download [Boolean] if true, adds +?download=1+ to trigger a download response
    # @return artifact content or download redirect
    def output(name, path, download: false)
      encoded_path = path.to_s.split("/").map { |seg| enc(seg) }.join("/")
      params = download ? { "download" => 1 } : {}
      @http.get("/api/v1/private/pipelines/#{enc(name)}/outputs/#{encoded_path}", params)
    end

    # POST /api/v1/private/pipelines/preview-code
    # Generate a code preview for a pipeline config without saving it.
    # @param config [Hash] pipeline config to preview
    # @return preview payload
    def preview_code(config)
      @http.post("/api/v1/private/pipelines/preview-code", config)
    end

    # GET /api/v1/private/pipelines/tools
    # Fetch the registry of tools available to pipeline steps.
    # @return [Hash] tool registry
    def tools
      @http.get("/api/v1/private/pipelines/tools")
    end

    # GET /api/v1/private/pipelines/subagents
    # Fetch the registry of sub-agent definitions available to pipelines.
    # @return [Hash] sub-agent registry
    def subagents
      @http.get("/api/v1/private/pipelines/subagents")
    end

    # POST /api/v1/private/templates/:template_id/instantiate
    # Instantiate a platform template into a new pipeline config.
    # @param template_id [String] the template to instantiate
    # @param name [String] name for the resulting pipeline
    # @param project [String] target project
    # @param overrides [Hash, nil] optional config overrides applied on top of the template defaults
    # @return [Hash] { "pipeline" => config }
    def instantiate_template(template_id, name:, project:, overrides: nil)
      body = compact("name" => name, "project" => project, "overrides" => overrides)
      @http.post("/api/v1/private/templates/#{enc(template_id)}/instantiate", body)
    end

    # POST /api/v1/private/ai/build-pipeline/sync
    # Generate a pipeline config from a natural-language brief using AI.
    # @param brief [Hash] free-form brief payload sent to the AI builder
    # @return [Hash] generated pipeline config
    def build_with_ai(brief)
      @http.post("/api/v1/private/ai/build-pipeline/sync", brief)
    end

    # ── Static-context documents ────────────────────────────────────────────────
    # Files an agent reads as part of its context (never chunked/embedded).
    # See also "RAG (retrieval) documents" below for the embedded-search store.

    # GET /api/v1/private/pipelines/:name/docs
    # List static-context documents attached to a pipeline.
    # @param name [String] pipeline name
    def list_docs(name)
      @http.get("/api/v1/private/pipelines/#{enc(name)}/docs")
    end

    # POST /api/v1/private/pipelines/:name/docs
    # Upload one static-context document as +multipart/form-data+ (field
    # "file"). Allowed extensions: .txt .md .pdf .csv .json .docx .doc .pptx .xlsx.
    # @param name [String] pipeline name
    # @param file [String, IO] raw file bytes, or an IO/File-like object
    # @param filename [String, nil] defaults to the file's own name, else "file"
    # @param content_type [String, nil] defaults to "application/octet-stream"
    def upload_doc(name, file, filename: nil, content_type: nil)
      bytes, fname = file_payload(file, filename)
      @http.post_multipart("/api/v1/private/pipelines/#{enc(name)}/docs", {}, "file", bytes, fname, content_type)
    end

    # DELETE /api/v1/private/pipelines/:name/docs/:filename
    # Remove one static-context document.
    # @param name [String] pipeline name
    # @param filename [String]
    def delete_doc(name, filename)
      @http.delete("/api/v1/private/pipelines/#{enc(name)}/docs/#{enc(filename)}")
    end

    # ── RAG (retrieval) documents ────────────────────────────────────────────────
    # Files chunked and embedded into the pipeline's own retrieval store, for
    # agents that search over a document set rather than reading it whole.

    # POST /api/v1/private/pipelines/:name/docs/retrieval
    # Upload one retrieval-mode document as +multipart/form-data+ (field "file").
    # @param name [String] pipeline name
    # @param file [String, IO] raw file bytes, or an IO/File-like object
    # @param filename [String, nil] defaults to the file's own name, else "file"
    # @param content_type [String, nil] defaults to "application/octet-stream"
    def upload_retrieval_doc(name, file, filename: nil, content_type: nil)
      bytes, fname = file_payload(file, filename)
      @http.post_multipart("/api/v1/private/pipelines/#{enc(name)}/docs/retrieval", {}, "file", bytes, fname, content_type)
    end

    # POST /api/v1/private/pipelines/:name/docs/retrieval/ingest
    # Embed changed retrieval documents with the pipeline's configured
    # embedder. Can take minutes, so this defaults to a 300 s request timeout —
    # pass +timeout_s:+ to override.
    # @param name [String] pipeline name
    # @param body [Hash] request body (empty by default)
    # @param timeout_s [Numeric] per-call timeout override (default 300)
    def ingest_retrieval(name, body: {}, timeout_s: 300)
      @http.post("/api/v1/private/pipelines/#{enc(name)}/docs/retrieval/ingest", body, timeout_s)
    end

    # DELETE /api/v1/private/pipelines/:name/docs/retrieval/:filename
    # Remove one retrieval-mode document (and its chunks).
    # @param name [String] pipeline name
    # @param filename [String]
    def delete_retrieval_doc(name, filename)
      @http.delete("/api/v1/private/pipelines/#{enc(name)}/docs/retrieval/#{enc(filename)}")
    end

    # ── Misc ───────────────────────────────────────────────────────────────────

    # GET /api/v1/version (public)
    # Get current server version string.
    def server_version
      @http.get("/api/v1/version")
    end

    private

    def enc(s)
      URI.encode_www_form_component(s.to_s)
    end

    def compact(hash)
      hash.reject { |_, v| v.nil? }
    end

    def stringify_keys(hash)
      hash.transform_keys(&:to_s)
    end

    # Resolves a caller-supplied +file+ (raw bytes String, or an IO/File-like
    # object responding to +#read+) into [bytes, filename] for a multipart
    # upload. +filename_override+ wins when given; otherwise an IO's own
    # +#path+ basename is used, falling back to "file".
    def file_payload(file, filename_override)
      if file.respond_to?(:read)
        bytes = file.read
        name  = filename_override || (file.respond_to?(:path) ? File.basename(file.path) : "file")
      else
        bytes = file.to_s
        name  = filename_override || "file"
      end
      [bytes, name]
    end
  end
end
