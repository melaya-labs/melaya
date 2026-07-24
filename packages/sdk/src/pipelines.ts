/**
 * Pipelines API — overview, runs, traces, and schedules.
 *
 * Covers the `/api/v1/private/overview/pipeline*` endpoints for listing and
 * counting runs, plus `/api/v1/private/runs/:runId/traces` for trace access
 * and `/api/v1/private/pipeline-schedule` for cron scheduling.
 */
import type { HttpClient } from "./client.js";
import type {
  DeleteTracesResult,
  OverviewSummary,
  PipelineCountByStatus,
  PipelineListParams,
  PipelineRun,
  PipelineSchedule,
  PipelineScheduleUpsertBody,
  Trace,
  TracePage,
  TraceStats,
} from "./platform-types.js";

// ── Embeddable lifecycle types ────────────────────────────────────────────────
// These cover the `/api/v1/private/pipelines/*` surface that lets you build,
// run, and read agent pipelines end to end from your own app.

/** Full pipeline configuration (agents, prompts, models, tools, wiring). */
export type PipelineConfig = Record<string, unknown> & { name?: string; project?: string };

/** Body for `create()`. `name` + `project` are required; the rest is the
 *  pipeline config (agents, prompts, models, wiring). */
export interface PipelineCreateBody {
  name: string;
  project: string;
  description?: string;
  [key: string]: unknown;
}

/** Options for `run()`. All optional — omit for a plain cloud-spawn run. */
export interface PipelineRunOptions {
  /** Project the pipeline belongs to (narrows tenant scope). */
  project?: string;
  /** Where the run executes. Defaults to the pipeline's own setting. */
  executionTarget?: "local-runner" | "cloud-spawn";
  /** Optional studio URL for run-event callbacks. */
  studio_url?: string;
  /** Per-run env var overrides layered over the caller's stored credentials. */
  env_overrides?: Record<string, string>;
}

/** Accepted-run envelope returned by `run()`. */
export interface PipelineRunAccepted {
  run_id: string;
  queued: boolean;
}

/** Run status + best-effort cost returned by `runStatus()`. */
export interface PipelineRunStatus {
  runId: string;
  status: string;
  createdAt: string;
  executionTarget: "local-runner" | "cloud-spawn";
  /** Cost breakdown from the in-process tracker; null for subprocess runs. */
  cost: unknown | null;
}

/** Body for `instantiateTemplate()`. */
export interface TemplateInstantiateBody {
  name: string;
  project: string;
  /** Config keys to override on top of the template payload. */
  overrides?: Record<string, unknown>;
}

const enc = encodeURIComponent;

export class PipelinesAPI {
  constructor(private readonly http: HttpClient) {}

  // ── Embeddable lifecycle: build → run → read ────────────────────────────────
  // Full CRUD + run + outputs over `/api/v1/private/pipelines/*`. Every method
  // is tier-gated server-side (Forge+ to create/run) and tenant-scoped to the
  // caller's projects. Prompt/model edits per agent go through `update()` with
  // the full config body.

  /** List pipelines visible to the caller. */
  async listPipelines(): Promise<{ pipelines: PipelineConfig[] }> {
    return this.http.get<{ pipelines: PipelineConfig[] }>("/api/v1/private/pipelines");
  }

  /**
   * Create a pipeline. `name` + `project` are required; include the agent
   * config (agents, prompts, models, wiring) in the same body.
   *
   * @example
   * ```ts
   * await melaya.agents.pipelines.create({
   *   name: "daily-digest", project: "acme",
   *   agents: [{ id: "researcher", model: "claude-sonnet-4-6", prompt: "..." }],
   * });
   * ```
   */
  async create(body: PipelineCreateBody): Promise<PipelineConfig> {
    return this.http.post<PipelineConfig>("/api/v1/private/pipelines", body);
  }

  /** Get one pipeline's full config. */
  async get(name: string, project?: string): Promise<PipelineConfig> {
    return this.http.get<PipelineConfig>(
      `/api/v1/private/pipelines/${enc(name)}`,
      project ? { project } : undefined,
    );
  }

  /**
   * Update a pipeline's config — the path for editing per-agent prompts or
   * swapping a model on one or all agents. Pass the full config plus `project`.
   *
   * @example
   * ```ts
   * const cfg = await melaya.agents.pipelines.get("daily-digest", "acme");
   * cfg.agents = cfg.agents.map(a => ({ ...a, model: "claude-opus-4-8" })); // all agents
   * await melaya.agents.pipelines.update("daily-digest", cfg, "acme");
   * ```
   */
  async update(name: string, config: PipelineConfig, project: string): Promise<PipelineConfig> {
    return this.http.put<PipelineConfig>(`/api/v1/private/pipelines/${enc(name)}`, {
      config,
      project,
    });
  }

  /** Delete a pipeline. */
  async remove(name: string, project: string): Promise<{ ok?: boolean } & Record<string, unknown>> {
    return this.http.delete(`/api/v1/private/pipelines/${enc(name)}`, { project });
  }

  /**
   * Run a pipeline. Returns immediately with a `run_id`; subscribe to progress
   * via `melaya.platform.events.onRunUpdate(run_id, ...)` or poll `runStatus()`.
   *
   * @example
   * ```ts
   * const { run_id } = await melaya.agents.pipelines.run("daily-digest", { project: "acme" });
   * melaya.platform.events.onRunUpdate(run_id, (e) => console.log(e.event_type, e.status));
   * ```
   */
  async run(name: string, opts?: PipelineRunOptions): Promise<PipelineRunAccepted> {
    return this.http.post<PipelineRunAccepted>(
      `/api/v1/private/pipelines/${enc(name)}/run`,
      opts ?? {},
    );
  }

  /** List run IDs for a pipeline (filtered to the caller's tier retention window). */
  async runIds(name: string): Promise<{ run_ids: string[] } & Record<string, unknown>> {
    return this.http.get(`/api/v1/private/pipelines/${enc(name)}/runs`);
  }

  /** Get status + best-effort cost for one run. */
  async runStatus(name: string, runId: string): Promise<PipelineRunStatus> {
    return this.http.get<PipelineRunStatus>(
      `/api/v1/private/pipelines/${enc(name)}/runs/${enc(runId)}`,
    );
  }

  /** Cancel / kill an in-flight run. */
  async cancelRun(name: string, runId: string): Promise<{ ok?: boolean } & Record<string, unknown>> {
    return this.http.delete(`/api/v1/private/pipelines/${enc(name)}/runs/${enc(runId)}`);
  }

  /** List the artifacts a pipeline has produced. */
  async outputs(name: string): Promise<unknown> {
    return this.http.get(`/api/v1/private/pipelines/${enc(name)}/outputs`);
  }

  /**
   * Read one output artifact by relative path. Text/JSON artifacts are parsed;
   * pass `{ download: true }` to force a download disposition on the wire.
   */
  async output(name: string, path: string, opts?: { download?: boolean }): Promise<unknown> {
    const rel = path.split("/").map(enc).join("/");
    return this.http.get(
      `/api/v1/private/pipelines/${enc(name)}/outputs/${rel}`,
      opts?.download ? { download: "1" } : undefined,
    );
  }

  /** Read-only codegen preview for a config (no persistence, no tier gate). */
  async previewCode(config: PipelineConfig): Promise<unknown> {
    return this.http.post(`/api/v1/private/pipelines/preview-code`, config);
  }

  /** The builder tool registry available to pipeline agents. */
  async tools(): Promise<unknown> {
    return this.http.get(`/api/v1/private/pipelines/tools`);
  }

  /** The builder sub-agent / crew registry. */
  async subagents(): Promise<unknown> {
    return this.http.get(`/api/v1/private/pipelines/subagents`);
  }

  /**
   * Live platform catalog counts (agentic tools, subagents, by category). Public.
   * Moved here from `MarketAPI` — use `melaya.agents.pipelines.catalogCounts()`.
   */
  async catalogCounts(): Promise<{ tools: number; subagents: number; byCategory?: unknown }> {
    return this.http.get<{ tools: number; subagents: number; byCategory?: unknown }>("/api/v1/public/catalog-counts");
  }

  /**
   * Dashboard usage summary (pipeline count, RAG usage, plan limits) for the sidebar/overview.
   * Moved here from `AccountAPI` — use `melaya.agents.pipelines.usageSummary()`.
   */
  async usageSummary(): Promise<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>("/api/v1/private/overview/usage");
  }

  /**
   * Instantiate a pipeline from a template you can access. Merges the template
   * payload with your `overrides`, then creates it under `name` + `project`.
   */
  async instantiateTemplate(
    templateId: string,
    body: TemplateInstantiateBody,
  ): Promise<{ pipeline: PipelineConfig }> {
    return this.http.post<{ pipeline: PipelineConfig }>(
      `/api/v1/private/templates/${enc(templateId)}/instantiate`,
      body,
    );
  }

  /**
   * Build a pipeline from a natural-language brief (synchronous — returns the
   * generated config). For streamed progress, POST to
   * `/api/v1/private/ai/build-pipeline` and read the SSE stream directly.
   */
  async buildWithAI(brief: Record<string, unknown>): Promise<unknown> {
    return this.http.post(`/api/v1/private/ai/build-pipeline/sync`, brief);
  }

  // ── Overview ────────────────────────────────────────────────────────────────

  /** Dashboard overview: usage stats, active strategies, recent runs. */
  async overview(): Promise<OverviewSummary> {
    return this.http.get<OverviewSummary>("/api/v1/private/overview");
  }

  async modelPrices(): Promise<Record<string, unknown>> {
    return this.http.get("/api/v1/private/overview/model-prices");
  }

  async chartData(params: Record<string, string | number | boolean | undefined> = {}): Promise<Record<string, unknown>> {
    return this.http.get("/api/v1/private/overview/chart", params);
  }

  async costBreakdown(params: Record<string, string | number | boolean | undefined> = {}): Promise<Record<string, unknown>> {
    return this.http.get("/api/v1/private/overview/cost-breakdown", params);
  }

  /** Count of pipeline runs grouped by status. */
  async count(): Promise<PipelineCountByStatus> {
    return this.http.get<PipelineCountByStatus>("/api/v1/private/overview/pipeline-count");
  }

  /**
   * Paginated list of pipeline runs.
   *
   * @example
   * ```ts
   * const runs = await melaya.pipelines.list({ project: "my-project", limit: 20 });
   * ```
   */
  async list(params?: PipelineListParams): Promise<PipelineRun[]> {
    return this.http.get<PipelineRun[]>("/api/v1/private/overview/pipelines", {
      ...(params as Record<string, string | number | boolean | undefined | null>),
    });
  }

  /** Most recent pipeline runs for a dashboard widget. */
  async recent(): Promise<PipelineRun[]> {
    return this.http.get<PipelineRun[]>("/api/v1/private/overview/pipelines/recent");
  }

  async serverVersion(): Promise<Record<string, unknown>> {
    return this.http.get("/api/v1/version");
  }

  // ── Traces ──────────────────────────────────────────────────────────────────

  /**
   * List traces for a run.
   * Returns a paginated envelope: `{ data: { list, total, page, pageSize } }`.
   * Access results via `.data.list`.
   */
  async traces(runId: string, params?: { page?: number; pageSize?: number }): Promise<{ data: TracePage }> {
    return this.http.get<{ data: TracePage }>(
      `/api/v1/private/runs/${encodeURIComponent(runId)}/traces`,
      params as Record<string, string | number | undefined>,
    );
  }

  /** Get a single trace by ID. */
  async trace(runId: string, traceId: string): Promise<Trace> {
    return this.http.get<Trace>(
      `/api/v1/private/runs/${encodeURIComponent(runId)}/traces/${encodeURIComponent(traceId)}`,
    );
  }

  /** Get statistics for a specific trace. */
  async traceStats(runId: string, traceId: string): Promise<TraceStats> {
    return this.http.get<TraceStats>(
      `/api/v1/private/runs/${encodeURIComponent(runId)}/traces/${encodeURIComponent(traceId)}/stats`,
    );
  }

  /**
   * Delete all traces (and their spans) for a run.
   * Issues DELETE /api/v1/private/runs/:runId/traces — no request body.
   */
  async deleteTraces(runId: string): Promise<DeleteTracesResult> {
    return this.http.delete<DeleteTracesResult>(
      `/api/v1/private/runs/${encodeURIComponent(runId)}/traces`,
    );
  }

  // ── Schedule ────────────────────────────────────────────────────────────────

  /** List all pipeline schedules accessible to the caller. */
  async listSchedules(): Promise<PipelineSchedule[]> {
    return this.http.get<PipelineSchedule[]>("/api/v1/private/pipeline-schedule");
  }

  /** Get the schedule for a specific pipeline. */
  async getSchedule(project: string, pipelineName: string): Promise<PipelineSchedule> {
    return this.http.get<PipelineSchedule>(
      `/api/v1/private/pipeline-schedule/${encodeURIComponent(project)}/${encodeURIComponent(pipelineName)}`,
    );
  }

  /**
   * Create or update a pipeline schedule (cron expression + optional config).
   *
   * @example
   * ```ts
   * await melaya.pipelines.upsertSchedule("my-project", "nightly-report", { cron: "0 2 * * *" });
   * ```
   */
  async upsertSchedule(
    project: string,
    pipelineName: string,
    body: PipelineScheduleUpsertBody,
  ): Promise<PipelineSchedule> {
    return this.http.put<PipelineSchedule>(
      `/api/v1/private/pipeline-schedule/${encodeURIComponent(project)}/${encodeURIComponent(pipelineName)}`,
      body,
    );
  }

  /** Pause a pipeline schedule. */
  async pauseSchedule(project: string, pipelineName: string): Promise<{ ok: boolean }> {
    return this.http.post<{ ok: boolean }>(
      `/api/v1/private/pipeline-schedule/${encodeURIComponent(project)}/${encodeURIComponent(pipelineName)}/pause`,
    );
  }

  /** Resume a paused pipeline schedule. */
  async resumeSchedule(project: string, pipelineName: string): Promise<{ ok: boolean }> {
    return this.http.post<{ ok: boolean }>(
      `/api/v1/private/pipeline-schedule/${encodeURIComponent(project)}/${encodeURIComponent(pipelineName)}/resume`,
    );
  }
}
