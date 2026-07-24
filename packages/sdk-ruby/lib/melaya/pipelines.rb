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
    # Fetch a single pipeline config by name.
    # @param name [String] pipeline name
    # @param project [String, nil] owning project (disambiguates when multiple projects share a name)
    # @return [Hash] pipeline config
    def get(name, project: nil)
      params = compact("project" => project)
      @http.get("/api/v1/private/pipelines/#{enc(name)}", params)
    end

    # PUT /api/v1/private/pipelines/:name
    # Replace a pipeline's config.
    # @param name [String] pipeline name
    # @param config [Hash] full pipeline config payload
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
    # @param execution_target [String, nil] runner target identifier
    # @param studio_url [String, nil] override studio URL
    # @param env_overrides [Hash, nil] environment variable overrides
    # @return [Hash] { "run_id" => String, "queued" => Boolean }
    def run(name, project: nil, execution_target: nil, studio_url: nil, env_overrides: nil)
      body = compact(
        "project"         => project,
        "executionTarget" => execution_target,
        "studio_url"      => studio_url,
        "env_overrides"   => env_overrides
      )
      @http.post("/api/v1/private/pipelines/#{enc(name)}/run", body.empty? ? nil : body)
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
  end
end
