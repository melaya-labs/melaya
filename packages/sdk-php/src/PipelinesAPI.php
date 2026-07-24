<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Pipelines API — lifecycle management, run control, outputs, and more.
 *
 * Covers:
 *   - /api/v1/private/overview/pipeline*     — run overview / counts
 *   - /api/v1/private/runs/:runId/traces     — trace access
 *   - /api/v1/private/pipeline-schedule      — cron scheduling
 *   - /api/v1/private/pipelines              — pipeline lifecycle (CRUD + run)
 *   - /api/v1/private/templates/:id/instantiate — template instantiation
 *   - /api/v1/private/ai/build-pipeline/sync — AI-assisted pipeline generation
 *
 * @example
 * ```php
 * $sdk = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * // Overview
 * $runs = $sdk->agents->pipelines->list(['project' => 'my-project', 'limit' => 20]);
 * $traces = $sdk->agents->pipelines->traces($runId);
 * $sdk->agents->pipelines->upsertSchedule('my-project', 'nightly-report', [
 *     'cron' => '0 2 * * *',
 * ]);
 *
 * // Lifecycle
 * $all  = $sdk->agents->pipelines->listPipelines();
 * $cfg  = $sdk->agents->pipelines->create(['name' => 'my-pipe', 'project' => 'my-project']);
 * $run  = $sdk->agents->pipelines->run('my-pipe', ['project' => 'my-project']);
 * $stat = $sdk->agents->pipelines->runStatus('my-pipe', $run['run_id']);
 * ```
 */
class PipelinesAPI
{
    public function __construct(private readonly HttpClient $http) {}

    // ── Pipeline lifecycle ───────────────────────────────────────────────────

    /**
     * List all pipelines accessible to the caller.
     *
     * @return array{pipelines: array<int, array<string, mixed>>}
     */
    public function listPipelines(): array
    {
        return $this->http->get('/api/v1/private/pipelines');
    }

    /**
     * Create a new pipeline.
     *
     * @param array $body Required: `name`, `project`. Optional: `description`, plus any
     *                    additional config keys (e.g. `nodes`, `edges`, `trigger`).
     * @return array Pipeline config as returned by the server.
     */
    public function create(array $body): array
    {
        return $this->http->post('/api/v1/private/pipelines', $body);
    }

    /**
     * Get a pipeline configuration by name.
     *
     * @param string      $name    Pipeline name (will be rawurlencoded).
     * @param string|null $project Optional project filter.
     * @return array Pipeline config.
     */
    public function getPipeline(string $name, ?string $project = null): array
    {
        $query = $project !== null ? ['project' => $project] : [];
        return $this->http->get('/api/v1/private/pipelines/' . rawurlencode($name), $query);
    }

    /**
     * Update an existing pipeline.
     *
     * @param string $name    Pipeline name (will be rawurlencoded).
     * @param array  $body    Required: `config`, `project`.
     * @return array Updated pipeline config.
     */
    public function update(string $name, array $body): array
    {
        return $this->http->put('/api/v1/private/pipelines/' . rawurlencode($name), $body);
    }

    /**
     * Delete a pipeline.
     *
     * @param string      $name    Pipeline name (will be rawurlencoded).
     * @param string|null $project Optional project filter.
     * @return array Empty array on success.
     */
    public function deletePipeline(string $name, ?string $project = null): array
    {
        $query = $project !== null ? ['project' => $project] : [];
        return $this->http->delete('/api/v1/private/pipelines/' . rawurlencode($name), $query);
    }

    /**
     * Trigger a pipeline run.
     *
     * @param string $name Pipeline name (will be rawurlencoded).
     * @param array  $body Optional keys: `project`, `executionTarget`, `studio_url`, `env_overrides`.
     * @return array{run_id: string, queued: bool}
     */
    public function run(string $name, array $body = []): array
    {
        return $this->http->post('/api/v1/private/pipelines/' . rawurlencode($name) . '/run', $body);
    }

    /**
     * List all run IDs for a pipeline.
     *
     * @param string $name Pipeline name (will be rawurlencoded).
     * @return array{run_ids: array<int, string>}
     */
    public function runIds(string $name): array
    {
        return $this->http->get('/api/v1/private/pipelines/' . rawurlencode($name) . '/runs');
    }

    /**
     * Get the status of a specific pipeline run.
     *
     * @param string $name  Pipeline name (will be rawurlencoded).
     * @param string $runId Run ID (will be rawurlencoded).
     * @return array{runId: string, status: string, createdAt: string, executionTarget: string, cost: mixed}
     */
    public function runStatus(string $name, string $runId): array
    {
        return $this->http->get(
            '/api/v1/private/pipelines/' . rawurlencode($name) . '/runs/' . rawurlencode($runId)
        );
    }

    /**
     * Cancel a running pipeline run.
     *
     * @param string $name  Pipeline name (will be rawurlencoded).
     * @param string $runId Run ID (will be rawurlencoded).
     * @return array Empty array on success.
     */
    public function cancelRun(string $name, string $runId): array
    {
        return $this->http->delete(
            '/api/v1/private/pipelines/' . rawurlencode($name) . '/runs/' . rawurlencode($runId)
        );
    }

    /**
     * List all output artifacts for a pipeline.
     *
     * @param string $name Pipeline name (will be rawurlencoded).
     * @return mixed Artifact listing as returned by the server.
     */
    public function outputs(string $name): mixed
    {
        return $this->http->get('/api/v1/private/pipelines/' . rawurlencode($name) . '/outputs');
    }

    /**
     * Get or download a specific output artifact.
     *
     * Each segment of `$path` is rawurlencoded individually and joined with `/`.
     *
     * @param string $name     Pipeline name (will be rawurlencoded).
     * @param string $path     Artifact path, e.g. `"subdir/report.json"`. Each `/`-separated
     *                         segment is independently encoded — do NOT pre-encode.
     * @param bool   $download Set to true to receive the raw binary download (adds `?download=1`).
     * @return mixed Artifact data or raw binary string.
     */
    public function output(string $name, string $path, bool $download = false): mixed
    {
        $encodedSegments = implode(
            '/',
            array_map('rawurlencode', explode('/', $path))
        );
        $query = $download ? ['download' => '1'] : [];
        return $this->http->get(
            '/api/v1/private/pipelines/' . rawurlencode($name) . '/outputs/' . $encodedSegments,
            $query
        );
    }

    /**
     * Preview the generated code for a pipeline config without saving it.
     *
     * @param array $config Pipeline configuration body.
     * @return mixed Code preview as returned by the server.
     */
    public function previewCode(array $config): mixed
    {
        return $this->http->post('/api/v1/private/pipelines/preview-code', $config);
    }

    /**
     * Get the tool registry available to pipelines.
     *
     * @return mixed Tool registry.
     */
    public function tools(): mixed
    {
        return $this->http->get('/api/v1/private/pipelines/tools');
    }

    /**
     * Get the subagent registry available to pipelines.
     *
     * @return mixed Subagent registry.
     */
    public function subagents(): mixed
    {
        return $this->http->get('/api/v1/private/pipelines/subagents');
    }

    /**
     * Instantiate a pipeline template into a concrete pipeline.
     *
     * @param string $templateId Template ID (will be rawurlencoded).
     * @param array  $body       Required: `name`, `project`. Optional: `overrides`.
     * @return array{pipeline: array<string, mixed>}
     */
    public function instantiateTemplate(string $templateId, array $body): array
    {
        return $this->http->post(
            '/api/v1/private/templates/' . rawurlencode($templateId) . '/instantiate',
            $body
        );
    }

    /**
     * Generate a pipeline config from a natural-language brief using AI (synchronous).
     *
     * @param array $brief Freeform brief body sent to the AI builder.
     * @return mixed Generated pipeline config.
     */
    public function buildWithAI(array $brief): mixed
    {
        return $this->http->post('/api/v1/private/ai/build-pipeline/sync', $brief);
    }

    // ── Overview ─────────────────────────────────────────────────────────────

    /**
     * Paginated list of pipeline runs.
     *
     * @param array $params ['project' => '...', 'pipelineName' => '...', 'status' => '...', 'limit' => 20, 'offset' => 0]
     */
    public function list(array $params = []): array
    {
        return $this->http->get('/api/v1/private/overview/pipelines', $params);
    }

    /** Count of pipeline runs grouped by status. */
    public function count(): array
    {
        return $this->http->get('/api/v1/private/overview/pipeline-count');
    }

    /** Most recent pipeline runs for the dashboard widget. */
    public function recent(): array
    {
        return $this->http->get('/api/v1/private/overview/pipelines/recent');
    }

    // ── Traces ───────────────────────────────────────────────────────────────

    /**
     * Paginated list of traces for a pipeline run.
     *
     * @param array $params Optional: ['page' => 1, 'pageSize' => 10]
     * @return array{data: array{list: array<int, array<string, mixed>>, total: int, page: int, pageSize: int}}
     */
    public function traces(string $runId, array $params = []): array
    {
        return $this->http->get('/api/v1/private/runs/' . rawurlencode($runId) . '/traces', $params);
    }

    /** Get a single trace by ID. */
    public function trace(string $runId, string $traceId): array
    {
        return $this->http->get(
            '/api/v1/private/runs/' . rawurlencode($runId) . '/traces/' . rawurlencode($traceId)
        );
    }

    /** Get statistics for a specific trace. */
    public function traceStats(string $runId, string $traceId): array
    {
        return $this->http->get(
            '/api/v1/private/runs/' . rawurlencode($runId) . '/traces/' . rawurlencode($traceId) . '/stats'
        );
    }

    /**
     * Delete all traces for a run by runId (no body required).
     *
     * @return array{deletedSpans: int, requestedTraces?: int}
     */
    public function deleteTraces(string $runId): array
    {
        return $this->http->delete('/api/v1/private/runs/' . rawurlencode($runId) . '/traces');
    }

    // ── Schedule ─────────────────────────────────────────────────────────────

    /** List all pipeline schedules accessible to the caller. */
    public function listSchedules(): array
    {
        return $this->http->get('/api/v1/private/pipeline-schedule');
    }

    /** Get the schedule for a specific pipeline. */
    public function getSchedule(string $project, string $pipelineName): array
    {
        return $this->http->get(
            '/api/v1/private/pipeline-schedule/' . rawurlencode($project) . '/' . rawurlencode($pipelineName)
        );
    }

    /**
     * Create or update a pipeline schedule (cron expression + optional config).
     *
     * @param string $project      Project name
     * @param string $pipelineName Pipeline name
     * @param array  $body         ['cron' => '0 2 * * *', 'config' => [...]]
     */
    public function upsertSchedule(string $project, string $pipelineName, array $body): array
    {
        return $this->http->put(
            '/api/v1/private/pipeline-schedule/' . rawurlencode($project) . '/' . rawurlencode($pipelineName),
            $body
        );
    }

    /** Pause a pipeline schedule. */
    public function pauseSchedule(string $project, string $pipelineName): array
    {
        return $this->http->post(
            '/api/v1/private/pipeline-schedule/' . rawurlencode($project) . '/' . rawurlencode($pipelineName) . '/pause'
        );
    }

    /** Resume a paused pipeline schedule. */
    public function resumeSchedule(string $project, string $pipelineName): array
    {
        return $this->http->post(
            '/api/v1/private/pipeline-schedule/' . rawurlencode($project) . '/' . rawurlencode($pipelineName) . '/resume'
        );
    }
}
