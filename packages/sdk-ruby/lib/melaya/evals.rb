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

    # GET /api/v1/private/memory/graph
    # Get memory graph visualization data for eval runs.
    def memory_graph(params = {})
      @http.get("/api/v1/private/memory/graph", params)
    end

    # GET /api/v1/private/memory/runs/:runId
    # Get memory usage for a specific eval run.
    # @param run_id [String]
    def run_memory(run_id)
      @http.get("/api/v1/private/memory/runs/#{enc(run_id)}")
    end

    # GET /api/v1/private/memory/crew
    # Get agent crew memory for a pipeline.
    # @param pipeline [String]
    # @param project [String]
    def crew_memory(pipeline:, project:)
      @http.get("/api/v1/private/memory/crew",
        "pipeline" => pipeline,
        "project"  => project)
    end

    # POST /api/v1/private/memory/crew/edit
    # Edit one persisted crew-memory entry (editor/owner-gated, tenant-scoped).
    # @param pipeline [String]
    # @param entry_id [String]
    # @param patch [Hash] any of "topic", "content", "tags" (partial update)
    # @param project [String, nil]
    def edit_crew_memory_entry(pipeline:, entry_id:, patch:, project: nil)
      body = compact(
        "pipeline" => pipeline,
        "entryId"  => entry_id,
        "project"  => project,
        "patch"    => patch
      )
      @http.post("/api/v1/private/memory/crew/edit", body)
    end

    # POST /api/v1/private/memory/crew/delete
    # Delete one persisted crew-memory entry (editor/owner-gated, tenant-scoped).
    # @param pipeline [String]
    # @param entry_id [String]
    # @param project [String, nil]
    def delete_crew_memory_entry(pipeline:, entry_id:, project: nil)
      body = compact("pipeline" => pipeline, "entryId" => entry_id, "project" => project)
      @http.post("/api/v1/private/memory/crew/delete", body)
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

    def compact(hash)
      hash.reject { |_, v| v.nil? }
    end
  end
end
