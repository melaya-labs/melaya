# frozen_string_literal: true

module Melaya
  # Evals API — list and inspect agent evaluation run results.
  #
  # Maps to /api/v1/private/evals/*.
  #
  # @example
  #   runs    = melaya.evals.list_runs
  #   summary = melaya.evals.summary
  #   detail  = melaya.evals.run_detail(runs.first["id"])
  class EvalsAPI
    def initialize(http)
      @http = http
    end

    # GET /api/v1/private/evals/runs
    # List eval run results for the caller's tenant.
    def list_runs(params = {})
      @http.get("/api/v1/private/evals/runs", params)
    end

    # GET /api/v1/private/evals/summary
    # Get aggregate summary of eval results.
    def summary
      @http.get("/api/v1/private/evals/summary")
    end

    # GET /api/v1/private/evals/runs/:runId
    # Get detailed results for a specific eval run.
    # @param run_id [String]
    def run_detail(run_id)
      @http.get("/api/v1/private/evals/runs/#{enc(run_id)}")
    end

    # GET /api/v1/private/evals/compare
    # Compare results across multiple eval runs.
    # @param params [Hash] e.g. { "runIds" => "id1,id2" }
    def compare(params = {})
      @http.get("/api/v1/private/evals/compare", params)
    end

    # GET /api/v1/private/evals/memory-graph
    # Get memory graph visualization data for eval runs.
    def memory_graph(params = {})
      @http.get("/api/v1/private/evals/memory-graph", params)
    end

    # GET /api/v1/private/evals/runs/:runId/memory
    # Get memory usage for a specific eval run.
    # @param run_id [String]
    def run_memory(run_id)
      @http.get("/api/v1/private/evals/runs/#{enc(run_id)}/memory")
    end

    # GET /api/v1/private/evals/crew-memory
    # Get agent crew memory for a pipeline.
    # @param pipeline [String]
    # @param project [String]
    def crew_memory(pipeline:, project:)
      @http.get("/api/v1/private/evals/crew-memory",
        "pipeline" => pipeline,
        "project"  => project)
    end

    # GET /api/v1/private/evals/benchmarks
    # Get benchmark scores across eval runs.
    def benchmarks(params = {})
      @http.get("/api/v1/private/evals/benchmarks", params)
    end

    private

    def enc(s)
      URI.encode_www_form_component(s.to_s)
    end
  end
end
