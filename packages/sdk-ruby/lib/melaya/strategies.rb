# frozen_string_literal: true

module Melaya
  # Strategies API — launch, control, and inspect trading strategies.
  #
  # A strategy is a server-managed runner (the Trading Engine, or an Agentic
  # Trading Crew) that trades a universe on a cadence with server-side SL/TP
  # and safety rails. Launch in paper mode (dry_run: true) or live
  # (dry_run: false, which requires a connected exchange key).
  #
  # Maps to https://api.melaya.org/api/v1/strategies/* on the private plane.
  class StrategiesAPI
    def initialize(http)
      @http = http
    end

    # Every strategy you own (running, paused, paper, and live).
    def list
      @http.get("/api/v1/strategies/list")["strategies"]
    end

    # A single strategy by id.
    # @param strategy_id [String]
    def get(strategy_id)
      @http.get("/api/v1/strategies/#{strategy_id}")["strategy"]
    end

    # Launch a strategy. Pass dry_run: true for paper; live needs api_key_id.
    # Returns the full response hash (includes "strategyId").
    #
    # @param name [String]
    # @param strategy_type [String] e.g. "custom"
    # @param exchange [String]
    # @param market [String, nil]
    # @param symbol [String, nil]
    # @param api_key_id [String, nil]
    # @param params [Hash, nil]
    # @param runtime_mode [String, nil]
    # @param dry_run [Boolean]
    # @param key_bindings [Hash, nil]
    def create(name:, strategy_type:, exchange:, market: nil, symbol: nil,
               api_key_id: nil, params: nil, runtime_mode: nil, dry_run: true,
               key_bindings: nil)
      body = {
        "name"         => name,
        "strategyType" => strategy_type,
        "exchange"     => exchange,
        "market"       => market,
        "symbol"       => symbol,
        "apiKeyId"     => api_key_id,
        "params"       => params,
        "runtimeMode"  => runtime_mode,
        "dryRun"       => dry_run,
        "keyBindings"  => key_bindings,
      }.reject { |_, v| v.nil? }
      @http.post("/api/v1/strategies", body)
    end

    # Pause a running strategy (stops entering new cycles until resumed).
    def pause(strategy_id)
      @http.post("/api/v1/strategies/#{strategy_id}/pause")
    end

    # Resume a paused strategy.
    def resume(strategy_id)
      @http.post("/api/v1/strategies/#{strategy_id}/resume")
    end

    # Stop a strategy and tear down its runner. Cancels any in-flight approvals.
    def stop(strategy_id)
      @http.post("/api/v1/strategies/#{strategy_id}/stop")
    end

    # Soft-delete a strategy.
    def delete(strategy_id)
      @http.delete("/api/v1/strategies/#{strategy_id}")
    end

    # Update a running strategy's params (e.g. universe, cadence, risk caps).
    # @param strategy_id [String]
    # @param params [Hash]
    def update_params(strategy_id, params)
      @http.post("/api/v1/strategies/#{strategy_id}/update-params", params)
    end

    # Live runtime status of a strategy's runner (container health, tick count).
    def status(strategy_id)
      @http.get("/api/v1/strategies/#{strategy_id}/status")
    end

    # Performance series for a strategy (equity, PnL over time).
    def performance(strategy_id)
      @http.get("/api/v1/strategies/#{strategy_id}/performance")["rows"]
    end

    # Execution (order) rows for a strategy.
    def executions(strategy_id)
      @http.get("/api/v1/strategies/#{strategy_id}/executions")["rows"]
    end

    # Trade (fill) rows for a strategy.
    def trades(strategy_id)
      @http.get("/api/v1/strategies/#{strategy_id}/trades")["rows"]
    end

    # Log rows for a strategy (cycle markers, persona messages, errors).
    def logs(strategy_id)
      @http.get("/api/v1/strategies/#{strategy_id}/logs")["rows"]
    end

    # ── AI parameter optimizer ────────────────────────────────────────────────

    # Kick off an AI-driven parameter optimization.
    # +param_bounds+ maps each param name to a [min, max] array.
    # +objective+ defaults to "sharpe"; +max_iterations+ is clamped to 1-20.
    # Returns a hash including "runId".
    #
    # @param strategy_id [String]
    # @param param_bounds [Hash] e.g. { "qty" => [0.001, 0.1] }
    # @param objective [String]
    # @param max_iterations [Integer]
    # @param require_approval [Boolean, nil]
    def ai_opt_start(strategy_id, param_bounds:, objective: "sharpe",
                     max_iterations: 3, require_approval: nil)
      body = {
        "paramBounds"     => param_bounds,
        "objective"       => objective,
        "maxIterations"   => max_iterations,
      }
      body["requireApproval"] = require_approval unless require_approval.nil?
      @http.post("/api/v1/strategies/#{strategy_id}/ai-opt/start", body)
    end

    # Current optimization status for a strategy.
    def ai_opt_status(strategy_id)
      @http.get("/api/v1/strategies/#{strategy_id}/ai-opt/status")
    end

    # Approve and apply the optimizer's proposed params to the running strategy.
    # @param body [Hash] optional extra params
    def ai_opt_approve(strategy_id, body = {})
      @http.post("/api/v1/strategies/#{strategy_id}/ai-opt/approve", body)
    end

    # Stop an in-progress optimization.
    def ai_opt_stop(strategy_id)
      @http.post("/api/v1/strategies/#{strategy_id}/ai-opt/stop")
    end

    # Past optimization runs for a strategy.
    def ai_opt_runs(strategy_id)
      @http.get("/api/v1/strategies/#{strategy_id}/ai-opt/runs")
    end
  end
end
