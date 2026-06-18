# frozen_string_literal: true

module Melaya
  # Backtest API — run strategies against historical data on the Rust engine.
  #
  # Start a single run or a parameter sweep (grid / random), poll the job,
  # then pull metrics, the equity curve, and the trade list. All backtests run
  # natively on Melaya's in-house engine — no per-venue SDK in the loop.
  #
  # Maps to https://api.melaya.org/api/v1/private/backtest/* on the private plane.
  class BacktestAPI
    def initialize(http)
      @http = http
    end

    # Start a backtest. +mode+ defaults to a single run; pass "grid_sweep" /
    # "random_sweep" with +param_ranges+ to fan out a parameter search.
    # Returns a hash with "job_id" (and optionally "count").
    #
    # @param body [Hash] see BacktestStart type in the reference SDKs
    def start(body)
      @http.post("/api/v1/private/backtest/start", body)
    end

    # Job status + progress (+status+, +progress_pct+, ...).
    # @param job_id [String]
    def job(job_id)
      @http.get("/api/v1/private/backtest/jobs/#{job_id}")
    end

    # Metrics, equity curve, and OHLCV for a completed job.
    # @param job_id [String]
    def results(job_id)
      @http.get("/api/v1/private/backtest/results/#{job_id}")["result"]
    end

    # The trade list for a completed job (default 500, max 5000 per call).
    # @param job_id [String]
    # @param limit [Integer, nil]
    # @param offset [Integer, nil]
    def trades(job_id, limit: nil, offset: nil)
      @http.get("/api/v1/private/backtest/trades/#{job_id}",
        "limit" => limit, "offset" => offset
      )["trades"]
    end

    # Ranked children of a sweep parent (default objective: sharpe DESC).
    # @param parent_id [String]
    # @param objective [String, nil]
    # @param limit [Integer, nil]
    def sweep(parent_id, objective: nil, limit: nil)
      @http.get("/api/v1/private/backtest/sweep/#{parent_id}",
        "objective" => objective, "limit" => limit
      )
    end

    # Your backtest jobs, newest first.
    # @param limit [Integer, nil]
    # @param offset [Integer, nil]
    def list(limit: nil, offset: nil)
      @http.get("/api/v1/private/backtest",
        "limit" => limit, "offset" => offset
      ).dig("data", "jobs")
    end

    # Your favorited backtest jobs (Forge tier and above).
    # @param limit [Integer, nil]
    # @param offset [Integer, nil]
    def favorites(limit: nil, offset: nil)
      @http.get("/api/v1/private/backtest/favorites",
        "limit" => limit, "offset" => offset
      ).dig("data", "jobs")
    end

    # Earliest funding-rate timestamp available for an exchange+symbol (ms, or nil).
    # @param exchange [String]
    # @param symbol [String]
    def funding_range(exchange:, symbol:)
      @http.get("/api/v1/private/backtest/funding-range",
        "exchange" => exchange, "symbol" => symbol
      )["earliest_ms"]
    end

    # Cancel an in-flight job.
    # @param job_id [String]
    def cancel(job_id)
      @http.post("/api/v1/private/backtest/#{job_id}/cancel")
    end

    # Soft-delete a single job.
    # @param job_id [String]
    def delete(job_id)
      @http.delete("/api/v1/private/backtest/#{job_id}")
    end

    # Soft-delete every non-favorited job. Returns a hash with "deleted" count.
    def delete_all
      @http.delete("/api/v1/private/backtest")
    end
  end
end
