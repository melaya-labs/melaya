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
 * $cfg  = $sdk->agents->pipelines->create([
 *     'name'    => 'my-pipe',
 *     'project' => 'my-project',
 *     'steps'   => [[
 *         'kind'  => 'agent',
 *         'agent' => [
 *             'name'        => 'researcher',
 *             'role'        => 'Researcher',
 *             'instruction' => 'Summarize the latest news on the given topic.',
 *             'model'       => ['provider' => 'anthropic', 'name' => 'claude-sonnet-4-6'],
 *             'agent_tools' => ['web_search'],
 *             'human_approval_tools' => [],
 *         ],
 *     ]],
 * ]);
 * $run  = $sdk->agents->pipelines->run('my-pipe', ['project' => 'my-project']);
 * $stat = $sdk->agents->pipelines->runStatus('my-pipe', $run['run_id']);
 * ```
 *
 * ## Pipeline config
 *
 * A pipeline's runnable agents come ONLY from `config.steps[]`. A top-level
 * `agents[]` array with no matching `steps[]` entries produces an EMPTY
 * pipeline that never executes — always embed the full agent object inside
 * its step, as in the `create()` example above.
 *
 * There is no `prompt` field: the agent's task goes in `instruction`, and an
 * optional `system_prompt_override` replaces its default system prompt
 * entirely. Other config fields worth knowing:
 *   - `hitl_mode`: `"safe"` (default) | `"autonomous"` | `"payments_only"` —
 *     only `"safe"` honours each agent's `human_approval_tools`.
 *   - `connector_source`: `"personal"` | `"project"` — which credential pool
 *     the run's connectors are drawn from.
 *   - `force_local_runner`: pin execution to the caller's local runner
 *     regardless of `run()`'s `executionTarget`.
 *   - `inputs[]`: declares the run-time inputs (`brief` / `values`) a run
 *     accepts — see `run()` and `uploadRunFile()`.
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
     * Returns an ENVELOPE `{ name, client, config, code, docs }` — the runnable
     * agent config lives under `config`, not at the top level. To edit and save
     * a pipeline, mutate `envelope['config']` and pass THAT (not the envelope
     * itself) to `update()`:
     *
     * @example
     * ```php
     * $envelope = $sdk->agents->pipelines->getPipeline('my-pipe', 'my-project');
     * $config = $envelope['config'];
     * $config['steps'][0]['agent']['model'] = ['provider' => 'anthropic', 'name' => 'claude-opus-4-8'];
     * $sdk->agents->pipelines->update('my-pipe', ['config' => $config, 'project' => 'my-project']);
     * ```
     *
     * @param string      $name    Pipeline name (will be rawurlencoded).
     * @param string|null $project Optional project filter.
     * @return array{name: string, client: string, config: array<string, mixed>, code: mixed, docs: mixed} Pipeline envelope.
     */
    public function getPipeline(string $name, ?string $project = null): array
    {
        $query = $project !== null ? ['project' => $project] : [];
        return $this->http->get('/api/v1/private/pipelines/' . rawurlencode($name), $query);
    }

    /**
     * Update an existing pipeline. This is the path for editing per-agent
     * instructions or swapping a model on one or all agents.
     *
     * @param string $name Pipeline name (will be rawurlencoded).
     * @param array  $body Required: `config` — the FULL pipeline config, i.e.
     *                     `getPipeline()['config']` after your edits, NOT the
     *                     envelope itself — and `project`.
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
     * @param array  $body Optional keys:
     *   - `project`: project scope.
     *   - `executionTarget`: `"local-runner" | "cloud-spawn"` — used ONLY for the
     *     tier check at launch time. Where the run actually executes is decided
     *     by the pipeline's stored config (local model providers /
     *     `force_local_runner`), not by this value.
     *   - `studio_url`: optional run-event callback URL.
     *   - `env_overrides`: per-run env var overrides layered over the caller's
     *     stored credentials. `MEL_*` / `MELAYA_*` keys are stripped
     *     server-side — they can never override trusted identity/tier values.
     *   - `run_inputs`: `['brief' => string, 'values' => array<string, mixed>]`.
     *     A file value inside `values` may be `['file_id' => ...]` (from
     *     `uploadRunFile()`), `['url' => ...]` (≤25 MB, fetched server-side), or
     *     `['base64' => ..., 'name' => ...]` (≤7 MB).
     * @return array{run_id: string, queued: bool, run_inputs?: array<string, mixed>}
     *         `run_inputs` is echoed back only when the run declared one.
     */
    public function run(string $name, array $body = []): array
    {
        return $this->http->post('/api/v1/private/pipelines/' . rawurlencode($name) . '/run', $body);
    }

    /**
     * Upload a file for use in a later run.
     *
     * The returned `file_id` is single-use and valid for 24 hours — pass it as
     * `run_inputs.values.<key> = ['file_id' => $fileId]` on `run()`.
     *
     * @param string $name Pipeline name (will be rawurlencoded).
     * @param string $key  The run-input key this file belongs to (declared on
     *                     the pipeline's `inputs[]`).
     * @param string $file Raw file bytes.
     * @param array  $opts Optional: `project`, `filename` (default `"upload.bin"`),
     *                     `contentType`.
     * @return array{file_id: string}
     */
    public function uploadRunFile(string $name, string $key, string $file, array $opts = []): array
    {
        $query = ['key' => $key];
        if (isset($opts['project'])) {
            $query['project'] = $opts['project'];
        }
        return $this->http->postMultipart(
            '/api/v1/private/pipelines/' . rawurlencode($name) . '/run-files',
            $query,
            'file',
            $file,
            $opts['filename'] ?? 'upload.bin',
            $opts['contentType'] ?? null,
        );
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
     * Poll whether a run is still active (liveness check for cloud-spawn runs).
     *
     * @param string $name  Pipeline name (will be rawurlencoded).
     * @param string $runId Run ID (will be rawurlencoded).
     * @return array{active: bool}
     */
    public function runActive(string $name, string $runId): array
    {
        return $this->http->get(
            '/api/v1/private/pipelines/' . rawurlencode($name) . '/runs/' . rawurlencode($runId) . '/active'
        );
    }

    /**
     * Get what a run was started with (brief, values, file references).
     *
     * @param string $name  Pipeline name (will be rawurlencoded).
     * @param string $runId Run ID — 16 hex characters (will be rawurlencoded).
     * @return array{brief?: string, values?: array<string, mixed>, files?: array<int, mixed>}
     */
    public function runInputs(string $name, string $runId): array
    {
        return $this->http->get(
            '/api/v1/private/pipelines/' . rawurlencode($name) . '/runs/' . rawurlencode($runId) . '/inputs'
        );
    }

    /**
     * Download one binary file that was supplied as a run input.
     *
     * @param string $name  Pipeline name (will be rawurlencoded).
     * @param string $runId Run ID — 16 hex characters (will be rawurlencoded).
     * @param int    $index File index, 0..99.
     * @return string Raw file bytes — do NOT JSON-decode.
     */
    public function runInputFile(string $name, string $runId, int $index): string
    {
        return $this->http->getBytes(
            '/api/v1/private/pipelines/' . rawurlencode($name) . '/runs/' . rawurlencode($runId)
                . '/inputs/files/' . rawurlencode((string) $index)
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

    // ── Static-context documents ─────────────────────────────────────────────

    /**
     * List static-context documents attached to a pipeline (injected into agent
     * context; not chunked for retrieval — see the "Retrieval (RAG) documents"
     * methods below for that).
     *
     * @param string $name Pipeline name (will be rawurlencoded).
     */
    public function listDocs(string $name): array
    {
        return $this->http->get('/api/v1/private/pipelines/' . rawurlencode($name) . '/docs');
    }

    /**
     * Upload a static-context document. Allowed extensions: .txt .md .pdf .csv
     * .json .docx .doc .pptx .xlsx.
     *
     * @param string $name Pipeline name (will be rawurlencoded).
     * @param string $file Raw file bytes.
     * @param array  $opts Optional: `filename` (default `"upload.bin"`), `contentType`.
     */
    public function uploadDoc(string $name, string $file, array $opts = []): array
    {
        return $this->http->postMultipart(
            '/api/v1/private/pipelines/' . rawurlencode($name) . '/docs',
            [],
            'file',
            $file,
            $opts['filename'] ?? 'upload.bin',
            $opts['contentType'] ?? null,
        );
    }

    /** Delete a static-context document by filename. */
    public function deleteDoc(string $name, string $filename): array
    {
        return $this->http->delete(
            '/api/v1/private/pipelines/' . rawurlencode($name) . '/docs/' . rawurlencode($filename)
        );
    }

    // ── Retrieval (RAG) documents ────────────────────────────────────────────

    /**
     * Upload a retrieval-mode document (chunked + embedded for RAG lookup by
     * the agents, as opposed to a static-context document injected in full).
     *
     * @param string $name Pipeline name (will be rawurlencoded).
     * @param string $file Raw file bytes.
     * @param array  $opts Optional: `filename` (default `"upload.bin"`), `contentType`.
     */
    public function uploadRetrievalDoc(string $name, string $file, array $opts = []): array
    {
        return $this->http->postMultipart(
            '/api/v1/private/pipelines/' . rawurlencode($name) . '/docs/retrieval',
            [],
            'file',
            $file,
            $opts['filename'] ?? 'upload.bin',
            $opts['contentType'] ?? null,
        );
    }

    /**
     * Embed changed retrieval documents with the pipeline's configured
     * embedder. Can take minutes — sent with a 300 s timeout regardless of the
     * client's configured default.
     *
     * @param string $name Pipeline name (will be rawurlencoded).
     * @param array  $body Optional ingest options; sent as `{}` by default.
     */
    public function ingestRetrieval(string $name, array $body = []): array
    {
        return $this->http->post(
            '/api/v1/private/pipelines/' . rawurlencode($name) . '/docs/retrieval/ingest',
            $body,
            300,
        );
    }

    /** Delete a retrieval-mode document (and its chunks) by filename. */
    public function deleteRetrievalDoc(string $name, string $filename): array
    {
        return $this->http->delete(
            '/api/v1/private/pipelines/' . rawurlencode($name) . '/docs/retrieval/' . rawurlencode($filename)
        );
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

    // ── Tool-call audit ──────────────────────────────────────────────────────

    /**
     * Paginated tool-call audit ledger for a project: every tool invocation
     * across the project's runs — tool, invoking agent, pipeline + run, who ran
     * it, status, latency, and HITL approval provenance (auto vs approved +
     * approver). Input/output are truncated to 4 KB; use `toolCallDetail()` for
     * the full payload. Keyset-paginated via `nextCursor`.
     *
     * @param string $project Project name (will be rawurlencoded).
     * @param array  $params  Optional: `beforeCreatedAt`, `beforeId` (keyset cursor
     *   from a previous page's `nextCursor`), `limit` (1-100, default 30), `tool`,
     *   `agent`, `runId`, `status` (`"ok"|"error"`), `search`,
     *   `connectorSource` (`"project"|"personal"`),
     *   `approval` (`"auto"|"approved"|"by:<username>"`), `provider`,
     *   `sort` (`"recent"|"oldest"|"slowest"|"fastest"`).
     * @return array{items: array<int, array<string, mixed>>, nextCursor: array{beforeCreatedAt: string, beforeId: string}|null, capped: bool}
     */
    public function projectToolCalls(string $project, array $params = []): array
    {
        return $this->http->get(
            '/api/v1/private/projects/' . rawurlencode($project) . '/tool-calls',
            $params
        );
    }

    /**
     * Distinct tools (with call counts) and agents seen in a project's
     * tool-call ledger — powers the audit view's filters.
     *
     * @param string $project Project name (will be rawurlencoded).
     * @return array{tools: array<int, array{name: string, count: int}>, agents: array<int, string>}
     */
    public function projectToolCallFacets(string $project): array
    {
        return $this->http->get('/api/v1/private/projects/' . rawurlencode($project) . '/tool-calls/facets');
    }

    /**
     * Full, untruncated arguments and result for a single tool-call span in a run.
     *
     * @param string $runId  Run ID (will be rawurlencoded).
     * @param string $spanId Tool-call span ID (will be rawurlencoded).
     */
    public function toolCallDetail(string $runId, string $spanId): array
    {
        return $this->http->get(
            '/api/v1/private/runs/' . rawurlencode($runId) . '/tool-calls/' . rawurlencode($spanId)
        );
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
