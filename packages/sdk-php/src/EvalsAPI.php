<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Evals API — eval run results, summaries, memory graphs, and benchmarks.
 *
 * Maps to /api/v1/private/evals/*.
 *
 * @example
 * ```php
 * $sdk = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * $runs = $sdk->evals->listRuns();
 * $summary = $sdk->evals->summary();
 * $detail = $sdk->evals->runDetail($runId);
 * ```
 */
class EvalsAPI
{
    public function __construct(private readonly HttpClient $http) {}

    /** List eval run results for the caller's tenant. */
    public function listRuns(array $params = []): array
    {
        return $this->http->get('/api/v1/private/evals/runs', $params);
    }

    /** Get aggregate summary of eval results. */
    public function summary(): array
    {
        return $this->http->get('/api/v1/private/evals/summary');
    }

    /** Get detailed results for a specific eval run. */
    public function runDetail(string $runId): array
    {
        return $this->http->get('/api/v1/private/evals/runs/' . rawurlencode($runId));
    }

    /**
     * Compare results across multiple eval runs.
     *
     * @param array $params ['runIds' => ['r1', 'r2'], ...]
     */
    public function compare(array $params = []): array
    {
        return $this->http->get('/api/v1/private/evals/compare', $params);
    }

    /** Get memory graph visualization data for eval runs. */
    public function memoryGraph(): array
    {
        return $this->http->get('/api/v1/private/memory/graph');
    }

    /** Get memory usage for a specific eval run. */
    public function runMemory(string $runId): array
    {
        return $this->http->get('/api/v1/private/memory/runs/' . rawurlencode($runId));
    }

    /**
     * Get agent crew memory for a pipeline.
     * Pass pipeline=<name>&project=<name> as params.
     *
     * @param array $params ['pipeline' => '...', 'project' => '...']
     */
    public function crewMemory(array $params): array
    {
        return $this->http->get('/api/v1/private/memory/crew', $params);
    }

    /** Get benchmark scores across eval runs. */
    public function benchmarks(array $params = []): array
    {
        return $this->http->get('/api/v1/private/evals/benchmarks', $params);
    }

    // ── Crew memory edit/delete ──────────────────────────────────────────────
    // Maps to /api/v1/private/memory/crew/*. Editor/owner-gated, tenant-scoped.

    /**
     * Edit one persisted crew-memory entry.
     *
     * @param array $body ['pipeline' => '...', 'entryId' => '...', 'project' => '...',
     *                     'patch' => ['topic' => '...', 'content' => '...', 'tags' => [...]]]
     */
    public function editEntry(array $body): array
    {
        return $this->http->post('/api/v1/private/memory/crew/edit', $body);
    }

    /**
     * Delete one persisted crew-memory entry.
     *
     * @param array $body ['pipeline' => '...', 'entryId' => '...', 'project' => '...']
     */
    public function deleteEntry(array $body): array
    {
        return $this->http->post('/api/v1/private/memory/crew/delete', $body);
    }
}
