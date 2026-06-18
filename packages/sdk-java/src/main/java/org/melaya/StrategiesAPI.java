package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Strategies API — launch, control, and inspect trading strategies.
 * Maps to {@code https://api.melaya.org/api/v1/strategies/*}.
 */
public class StrategiesAPI {

    private final HttpClient http;

    StrategiesAPI(HttpClient http) {
        this.http = http;
    }

    /** Every strategy you own (running, paused, paper, and live). */
    public JsonNode list() {
        return http.get("/api/v1/strategies/list", null).get("strategies");
    }

    /** A single strategy by id. */
    public JsonNode get(String strategyId) {
        return http.get("/api/v1/strategies/" + strategyId, null).get("strategy");
    }

    /**
     * Launch a strategy. Pass {@code dryRun: true} for paper.
     * The {@code body} map should include: name, strategyType, exchange, symbol, market,
     * dryRun, params, and optionally apiKeyId/runtimeMode.
     */
    public JsonNode create(Map<String, Object> body) {
        return http.post("/api/v1/strategies", body);
    }

    /** Pause a running strategy. */
    public JsonNode pause(String strategyId) {
        return http.post("/api/v1/strategies/" + strategyId + "/pause", null);
    }

    /** Resume a paused strategy. */
    public JsonNode resume(String strategyId) {
        return http.post("/api/v1/strategies/" + strategyId + "/resume", null);
    }

    /** Stop a strategy and tear down its runner. */
    public JsonNode stop(String strategyId) {
        return http.post("/api/v1/strategies/" + strategyId + "/stop", null);
    }

    /** Soft-delete a strategy. */
    public JsonNode delete(String strategyId) {
        return http.delete("/api/v1/strategies/" + strategyId, null);
    }

    /** Update a running strategy's params. */
    public JsonNode updateParams(String strategyId, Map<String, Object> params) {
        return http.post("/api/v1/strategies/" + strategyId + "/update-params", params);
    }

    /** Live runtime status of a strategy's runner. */
    public JsonNode status(String strategyId) {
        return http.get("/api/v1/strategies/" + strategyId + "/status", null);
    }

    /** Performance series (equity, PnL over time). */
    public JsonNode performance(String strategyId) {
        return http.get("/api/v1/strategies/" + strategyId + "/performance", null).get("rows");
    }

    /** Execution (order) rows for a strategy. */
    public JsonNode executions(String strategyId) {
        return http.get("/api/v1/strategies/" + strategyId + "/executions", null).get("rows");
    }

    /** Trade (fill) rows for a strategy. */
    public JsonNode trades(String strategyId) {
        return http.get("/api/v1/strategies/" + strategyId + "/trades", null).get("rows");
    }

    /** Log rows for a strategy (cycle markers, persona messages, errors). */
    public JsonNode logs(String strategyId) {
        return http.get("/api/v1/strategies/" + strategyId + "/logs", null).get("rows");
    }

    // ── AI parameter optimizer ────────────────────────────────────────────────

    /** Kick off an AI-driven parameter optimization. */
    public JsonNode aiOptStart(String strategyId, Map<String, Object> body) {
        return http.post("/api/v1/strategies/" + strategyId + "/ai-opt/start", body);
    }

    /** Current optimization status for a strategy. */
    public JsonNode aiOptStatus(String strategyId) {
        return http.get("/api/v1/strategies/" + strategyId + "/ai-opt/status", null);
    }

    /** Approve and apply the optimizer's proposed params. */
    public JsonNode aiOptApprove(String strategyId, Map<String, Object> body) {
        return http.post("/api/v1/strategies/" + strategyId + "/ai-opt/approve",
                body != null ? body : Map.of());
    }

    /** Stop an in-progress optimization. */
    public JsonNode aiOptStop(String strategyId) {
        return http.post("/api/v1/strategies/" + strategyId + "/ai-opt/stop", null);
    }

    /** Past optimization runs for a strategy. */
    public JsonNode aiOptRuns(String strategyId) {
        return http.get("/api/v1/strategies/" + strategyId + "/ai-opt/runs", null);
    }
}
