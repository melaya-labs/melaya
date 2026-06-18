<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Strategies API — launch, control, and inspect trading strategies.
 *
 * Maps to https://api.melaya.org/api/v1/strategies/*.
 */
class StrategiesAPI
{
    public function __construct(private readonly HttpClient $http) {}

    /** Every strategy you own (running, paused, paper, and live). */
    public function list(): array
    {
        return $this->http->get('/api/v1/strategies/list')['strategies'];
    }

    /** A single strategy by id. */
    public function get(string $strategyId): array
    {
        return $this->http->get("/api/v1/strategies/{$strategyId}")['strategy'];
    }

    /**
     * Launch a strategy. Pass `dryRun: true` for paper; `dryRun: false` places
     * real orders and requires a connected `apiKeyId`. Returns the new id.
     */
    public function create(array $body): array
    {
        return $this->http->post('/api/v1/strategies', $body);
    }

    /** Pause a running strategy (it stops entering new cycles until resumed). */
    public function pause(string $strategyId): array
    {
        return $this->http->post("/api/v1/strategies/{$strategyId}/pause");
    }

    /** Resume a paused strategy. */
    public function resume(string $strategyId): array
    {
        return $this->http->post("/api/v1/strategies/{$strategyId}/resume");
    }

    /** Stop a strategy and tear down its runner. */
    public function stop(string $strategyId): array
    {
        return $this->http->post("/api/v1/strategies/{$strategyId}/stop");
    }

    /** Soft-delete a strategy. */
    public function delete(string $strategyId): array
    {
        return $this->http->delete("/api/v1/strategies/{$strategyId}");
    }

    /** Update a running strategy's params (e.g. universe, cadence, risk caps). */
    public function updateParams(string $strategyId, array $params): array
    {
        return $this->http->post("/api/v1/strategies/{$strategyId}/update-params", $params);
    }

    /** Live runtime status of a strategy's runner (container health, tick count). */
    public function status(string $strategyId): array
    {
        return $this->http->get("/api/v1/strategies/{$strategyId}/status");
    }

    /** Performance series for a strategy (equity, PnL over time). */
    public function performance(string $strategyId): array
    {
        return $this->http->get("/api/v1/strategies/{$strategyId}/performance")['rows'];
    }

    /** Execution (order) rows for a strategy. */
    public function executions(string $strategyId): array
    {
        return $this->http->get("/api/v1/strategies/{$strategyId}/executions")['rows'];
    }

    /** Trade (fill) rows for a strategy. */
    public function trades(string $strategyId): array
    {
        return $this->http->get("/api/v1/strategies/{$strategyId}/trades")['rows'];
    }

    /** Log rows for a strategy (cycle markers, persona messages, errors). */
    public function logs(string $strategyId): array
    {
        return $this->http->get("/api/v1/strategies/{$strategyId}/logs")['rows'];
    }

    // ── AI parameter optimizer ────────────────────────────────────────────────

    /**
     * Kick off an AI-driven parameter optimization.
     * `paramBounds` maps each param to a [min, max] range.
     * Returns the optimization runId.
     */
    public function aiOptStart(string $strategyId, array $paramBounds, string $objective = 'sharpe', int $maxIterations = 3, ?bool $requireApproval = null): array
    {
        $body = ['paramBounds' => $paramBounds, 'objective' => $objective, 'maxIterations' => $maxIterations];
        if ($requireApproval !== null) {
            $body['requireApproval'] = $requireApproval;
        }
        return $this->http->post("/api/v1/strategies/{$strategyId}/ai-opt/start", $body);
    }

    /** Current optimization status for a strategy. */
    public function aiOptStatus(string $strategyId): array
    {
        return $this->http->get("/api/v1/strategies/{$strategyId}/ai-opt/status");
    }

    /** Approve and apply the optimizer's proposed params to the running strategy. */
    public function aiOptApprove(string $strategyId, array $body = []): array
    {
        return $this->http->post("/api/v1/strategies/{$strategyId}/ai-opt/approve", $body);
    }

    /** Stop an in-progress optimization. */
    public function aiOptStop(string $strategyId): array
    {
        return $this->http->post("/api/v1/strategies/{$strategyId}/ai-opt/stop");
    }

    /** Past optimization runs for a strategy. */
    public function aiOptRuns(string $strategyId): mixed
    {
        return $this->http->get("/api/v1/strategies/{$strategyId}/ai-opt/runs");
    }
}
