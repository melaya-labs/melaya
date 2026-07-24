<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Overview API — dashboard overview, model prices, chart data, cost breakdowns.
 *
 * Maps to /api/v1/private/overview/*.
 *
 * @example
 * ```php
 * $sdk = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * $summary = $sdk->overview->get();
 * echo $summary['totalRuns'];
 *
 * $chart = $sdk->overview->chartData();
 * ```
 */
class OverviewAPI
{
    public function __construct(private readonly HttpClient $http) {}

    /** Get dashboard overview (usage stats, active strategies, recent runs). */
    public function get(): array
    {
        return $this->http->get('/api/v1/private/overview');
    }

    /** Get the usage summary (pipeline count, RAG usage, plan limits) for the sidebar/dashboard. */
    public function usageSummary(): array
    {
        return $this->http->get('/api/v1/private/overview/usage');
    }

    /** Get pricing data for available AI models. */
    public function modelPrices(): array
    {
        return $this->http->get('/api/v1/private/overview/model-prices');
    }

    /** Get chart data for the overview dashboard (cost/usage over time). */
    public function chartData(): array
    {
        return $this->http->get('/api/v1/private/overview/chart');
    }

    /** Get cost breakdown by model/provider. */
    public function costBreakdown(): array
    {
        return $this->http->get('/api/v1/private/overview/cost-breakdown');
    }

    /** Get count of pipelines by status. */
    public function pipelineCount(): array
    {
        return $this->http->get('/api/v1/private/overview/pipeline-count');
    }

    /**
     * Get a paginated list of pipeline runs.
     *
     * @param array $params ['project' => '...', 'pipelineName' => '...', 'status' => '...', 'limit' => 20, 'offset' => 0]
     */
    public function pipelineList(array $params = []): array
    {
        return $this->http->get('/api/v1/private/overview/pipelines', $params);
    }

    /** Get the most recent pipeline runs for the dashboard widget. */
    public function recentPipelines(): array
    {
        return $this->http->get('/api/v1/private/overview/pipelines/recent');
    }
}
