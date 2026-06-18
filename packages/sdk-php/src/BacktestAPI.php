<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Backtest API — run strategies against historical data on the Rust engine.
 *
 * Maps to https://api.melaya.org/api/v1/private/backtest/*.
 */
class BacktestAPI
{
    public function __construct(private readonly HttpClient $http) {}

    /**
     * Start a backtest. Returns the job id(s) — poll with job().
     * Pass `mode: 'grid_sweep'` / `'random_sweep'` with `paramRanges` for a sweep.
     */
    public function start(array $body): array
    {
        return $this->http->post('/api/v1/private/backtest/start', $body);
    }

    /** Job status + progress (`status`, `progress_pct`, ...). */
    public function job(string $jobId): array
    {
        return $this->http->get("/api/v1/private/backtest/jobs/{$jobId}");
    }

    /** Metrics, equity curve, and OHLCV for a completed job. */
    public function results(string $jobId): array
    {
        return $this->http->get("/api/v1/private/backtest/results/{$jobId}")['result'];
    }

    /** The trade list for a completed job (default 500, max 5000 per call). */
    public function trades(string $jobId, ?int $limit = null, ?int $offset = null): array
    {
        $q = array_filter(['limit' => $limit, 'offset' => $offset], fn($v) => $v !== null);
        return $this->http->get("/api/v1/private/backtest/trades/{$jobId}", $q)['trades'];
    }

    /** Ranked children of a sweep parent (default objective: sharpe DESC). */
    public function sweep(string $parentId, ?string $objective = null, ?int $limit = null): array
    {
        $q = array_filter(['objective' => $objective, 'limit' => $limit], fn($v) => $v !== null);
        return $this->http->get("/api/v1/private/backtest/sweep/{$parentId}", $q);
    }

    /** Your backtest jobs, newest first. */
    public function list(?int $limit = null, ?int $offset = null): array
    {
        $q = array_filter(['limit' => $limit, 'offset' => $offset], fn($v) => $v !== null);
        return $this->http->get('/api/v1/private/backtest', $q)['data']['jobs'];
    }

    /** Your favorited backtest jobs (Forge tier and above). */
    public function favorites(?int $limit = null, ?int $offset = null): array
    {
        $q = array_filter(['limit' => $limit, 'offset' => $offset], fn($v) => $v !== null);
        return $this->http->get('/api/v1/private/backtest/favorites', $q)['data']['jobs'];
    }

    /** Earliest funding-rate timestamp available for an exchange+symbol (ms, or null). */
    public function fundingRange(string $exchange, string $symbol): ?int
    {
        return $this->http->get('/api/v1/private/backtest/funding-range', ['exchange' => $exchange, 'symbol' => $symbol])['earliest_ms'];
    }

    /** Cancel an in-flight job. */
    public function cancel(string $jobId): array
    {
        return $this->http->post("/api/v1/private/backtest/{$jobId}/cancel");
    }

    /** Soft-delete a single job. */
    public function delete(string $jobId): array
    {
        return $this->http->delete("/api/v1/private/backtest/{$jobId}");
    }

    /** Soft-delete every non-favorited job. Returns the count deleted. */
    public function deleteAll(): array
    {
        return $this->http->delete('/api/v1/private/backtest');
    }
}
