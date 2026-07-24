package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

import static org.melaya.MarketAPI.params;

/**
 * Backtest API — run strategies against historical data on the Rust engine.
 * Maps to {@code https://api.melaya.org/api/v1/private/backtest/*}.
 */
public class BacktestAPI {

    private final HttpClient http;

    BacktestAPI(HttpClient http) {
        this.http = http;
    }

    /**
     * Start a backtest. Returns the job id — poll with {@link #job(String)}.
     * {@code body} keys follow the SDK spec (strategyType, exchange, symbol,
     * timeframe, since_ms, until_ms, params, language, definition, ...).
     */
    public JsonNode start(Map<String, Object> body) {
        return http.post("/api/v1/private/backtest/start", body);
    }

    /** Job status and progress ({@code status}, {@code progress_pct}, ...). */
    public JsonNode job(String jobId) {
        return http.get("/api/v1/private/backtest/jobs/" + jobId, null);
    }

    /** Metrics, equity curve, and OHLCV for a completed job. */
    public JsonNode results(String jobId) {
        return http.get("/api/v1/private/backtest/results/" + jobId, null).get("result");
    }

    /** The trade list for a completed job. */
    public JsonNode trades(String jobId, Integer limit, Integer offset) {
        Map<String, Object> q = params("limit", limit, "offset", offset);
        return http.get("/api/v1/private/backtest/trades/" + jobId, q).get("trades");
    }

    /** Ranked children of a sweep parent. */
    public JsonNode sweep(String parentId, String objective, Integer limit) {
        Map<String, Object> q = params("objective", objective, "limit", limit);
        return http.get("/api/v1/private/backtest/sweep/" + parentId, q);
    }

    /** Your backtest jobs, newest first. */
    public JsonNode list(Integer limit, Integer offset) {
        Map<String, Object> q = params("limit", limit, "offset", offset);
        return http.get("/api/v1/private/backtest", q).get("data").get("jobs");
    }

    /** Your favorited backtest jobs. */
    public JsonNode favorites(Integer limit, Integer offset) {
        Map<String, Object> q = params("limit", limit, "offset", offset);
        return http.get("/api/v1/private/backtest/favorites", q).get("data").get("jobs");
    }

    /** Earliest funding-rate timestamp available for an exchange+symbol (ms, or null). */
    public JsonNode fundingRange(String exchange, String symbol) {
        Map<String, Object> q = params("exchange", exchange, "symbol", symbol);
        return http.get("/api/v1/private/backtest/funding-range", q).get("earliest_ms");
    }

    /** Cancel an in-flight job. */
    public JsonNode cancel(String jobId) {
        return http.post("/api/v1/private/backtest/" + jobId + "/cancel", null);
    }

    /** Soft-delete a single job. */
    public JsonNode delete(String jobId) {
        return http.delete("/api/v1/private/backtest/" + jobId, null);
    }

    /** Soft-delete every non-favorited job. */
    public JsonNode deleteAll() {
        return http.delete("/api/v1/private/backtest", null);
    }

    // ── Parameter-sweep optimization ──────────────────────────────────────────

    /**
     * Start a parameter sweep optimization (genetic/grid) over a strategy config.
     * Returns a job/run ID — poll with {@link #optimizeList()} or listen to events.
     *
     * @param body optimization config (strategy, param ranges, algorithm, etc.)
     */
    public JsonNode optimize(Map<String, Object> body) {
        return http.post("/api/v1/private/backtest/optimize", body);
    }

    /** List optimization sweep runs. */
    public JsonNode optimizeList() {
        return http.get("/api/v1/private/backtest/optimize", null);
    }

    /**
     * Get the status/progress of an optimization sweep run.
     *
     * @param optRunId the optimization run ID
     */
    public JsonNode optimizeStatus(String optRunId) {
        return http.get("/api/v1/private/backtest/optimize/" + optRunId + "/status", null);
    }

    /**
     * Cancel an in-progress optimization sweep.
     *
     * @param optRunId the optimization run ID
     */
    public JsonNode optimizeCancel(String optRunId) {
        return http.post("/api/v1/private/backtest/optimize/" + optRunId + "/cancel", null);
    }

    /**
     * Apply the best params from a completed optimization sweep to a strategy.
     *
     * @param optRunId the optimization run ID
     * @param body     optional body (e.g. target strategy ID to apply to)
     */
    public JsonNode optimizeApply(String optRunId, Map<String, Object> body) {
        return http.post("/api/v1/private/backtest/optimize/" + optRunId + "/apply", body);
    }
}
