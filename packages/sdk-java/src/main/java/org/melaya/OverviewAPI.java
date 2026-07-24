package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

import static org.melaya.MarketAPI.params;

/**
 * Overview API — dashboard metrics, model pricing, cost breakdown, and
 * pipeline run lists.
 *
 * <p>Maps to {@code /api/v1/private/overview/*}.
 */
public class OverviewAPI {

    private final HttpClient http;

    OverviewAPI(HttpClient http) {
        this.http = http;
    }

    /**
     * Get the dashboard overview — usage stats, active strategies, recent runs.
     */
    public JsonNode get() {
        return http.get("/api/v1/private/overview", null);
    }

    /** Get the usage summary (pipeline count, RAG usage, plan limits) for the sidebar/dashboard. */
    public JsonNode usageSummary() {
        return http.get("/api/v1/private/overview/usage", null);
    }

    /** Get pricing data for available AI models. */
    public JsonNode modelPrices() {
        return http.get("/api/v1/private/overview/model-prices", null);
    }

    /** Get chart data for the overview dashboard (cost/usage over time). */
    public JsonNode chartData() {
        return http.get("/api/v1/private/overview/chart", null);
    }

    /** Get cost breakdown by model/provider. */
    public JsonNode costBreakdown() {
        return http.get("/api/v1/private/overview/cost-breakdown", null);
    }

    /** Get count of pipelines by status. */
    public JsonNode pipelineCount() {
        return http.get("/api/v1/private/overview/pipeline-count", null);
    }

    /**
     * Get a paginated list of pipeline runs.
     *
     * @param project      optional project filter; may be {@code null}
     * @param pipelineName optional pipeline name filter; may be {@code null}
     * @param status       optional status filter (e.g. {@code "running"}); may be {@code null}
     * @param limit        optional page size; may be {@code null}
     * @param offset       optional page offset; may be {@code null}
     */
    public JsonNode pipelineList(String project, String pipelineName, String status,
                                 Integer limit, Integer offset) {
        Map<String, Object> q = params(
                "project", project,
                "pipelineName", pipelineName,
                "status", status,
                "limit", limit,
                "offset", offset);
        return http.get("/api/v1/private/overview/pipelines", q.isEmpty() ? null : q);
    }

    /** Get the most recent pipeline runs for a dashboard widget. */
    public JsonNode recentPipelines() {
        return http.get("/api/v1/private/overview/pipelines/recent", null);
    }
}
