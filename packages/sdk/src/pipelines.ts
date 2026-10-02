/**
 * Pipelines API — overview, runs, traces, and schedules.
 *
 * Covers the `/api/v1/private/overview/pipeline*` endpoints for listing and
 * counting runs, plus `/api/v1/private/runs/:runId/traces` for trace access
 * and `/api/v1/private/pipeline-schedule` for cron scheduling.
 */
import type { FileInput, HttpClient } from "./client.js";
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

/**
 * One agent's model reference inside a step. `provider` + `name` identify the
 * model exactly as configured in Melaya Connectors (e.g. `{ provider:
 * "anthropic", name: "claude-sonnet-4-6" }`).
 */
export interface PipelineAgentModel {
  provider: string;
  name: string;
}

/**
 * Full definition of one agent, embedded inside a `steps[]` entry.
 * There is no `prompt` field — `instruction` is the task, and
 * `system_prompt_override` replaces the agent's default system prompt.
 */
export interface PipelineAgentDef {
  name: string;
  role?: string;
  /** The task given to the agent. There is no `prompt` field — this is it. */
  instruction: string;
  model: PipelineAgentModel;
  agent_tools?: string[];
  /** Tool names that require human approval. Only honoured when the
   *  pipeline's `hitl_mode` is `"safe"` (the default). */
  human_approval_tools?: string[];
  /** Replaces the agent's default system prompt entirely, when set. */
  system_prompt_override?: string;
  [key: string]: unknown;
}

/**
 * One step in a pipeline's `steps[]` array — the ONLY source codegen reads to
 * generate a run. An `agents[]` array with no matching `steps[]` entries
 * produces an EMPTY pipeline.
 */
export interface PipelineStep {
  kind: "agent" | (string & {});
  agent?: PipelineAgentDef;
  [key: string]: unknown;
}

/** Full pipeline configuration (steps, models, tools, wiring). */
export type PipelineConfig = Record<string, unknown> & {
  name?: string;
  project?: string;
  /**
   * Steps executed in order. This is the ONLY thing codegen reads to build
   * the run — a top-level `agents[]` array is not enough on its own.
   */
  steps?: PipelineStep[];
  /**
   * Human-in-the-loop mode. `"safe"` (the default) honours each agent's
   * `human_approval_tools`; `"autonomous"` and `"payments_only"` do not.
   */
  hitl_mode?: "safe" | "autonomous" | "payments_only";
  /** Where connector credentials are sourced from for this pipeline's tool calls. */
  connector_source?: "personal" | "project";
  /** Force this pipeline to always execute on the caller's local runner. */
  force_local_runner?: boolean;
  /** Declared run-time inputs (brief/value/file schema) surfaced via `run_inputs`. */
  inputs?: unknown[];
};

/**
 * Envelope returned by `get()`. Edit `.config` and pass it back to
 * `update()` — do not construct a new config from scratch.
 */
export interface PipelineGetEnvelope {
  name: string;
  client?: string;
  config: PipelineConfig;
  code?: string;
  docs?: unknown;
  [key: string]: unknown;
}

/** Body for `create()`. `name` + `project` are required; the rest is the
 *  pipeline config (`steps[]`, models, wiring). */
export interface PipelineCreateBody {
  name: string;
  project: string;
  description?: string;
  [key: string]: unknown;
}

/**
 * A file reference inside `run_inputs.values`. Exactly one of `file_id`,
 * `url`, or `base64` should be set:
 * - `{ file_id }` — from `uploadRunFile()` (single-use, valid 24h).
 * - `{ url }` — fetched server-side (≤ 25 MB).
 * - `{ base64, name }` — inlined in the request (≤ 7 MB).
 */
export type PipelineRunFileValue =
  | { file_id: string }
  | { url: string }
  | { base64: string; name: string };

/** Run-time brief/values threaded into a pipeline's declared `inputs[]`. */
export interface PipelineRunInputs {
  /** Free-text brief handed to the pipeline's brief-shaped input, if declared. */
  brief?: string;
  /** Named input values — plain JSON values or a `PipelineRunFileValue`. */
  values?: Record<string, unknown>;
}

/** Options for `run()`. All optional — omit for a plain cloud-spawn run. */
export interface PipelineRunOptions {
  /** Project the pipeline belongs to (narrows tenant scope). */
  project?: string;
  /**
   * Used ONLY for the tier check at run time (e.g. gating cloud-spawn by
   * plan). It does NOT decide where the run actually executes — that is
   * controlled by the pipeline's stored config (its configured local model
   * providers, or `force_local_runner`).
   */
  executionTarget?: "local-runner" | "cloud-spawn";
  /** Optional studio URL for run-event callbacks. */
  studio_url?: string;
  /**
   * Per-run env var overrides layered over the caller's stored credentials.
   * Any `MEL_*` / `MELAYA_*` key is stripped server-side and never applied.
   */
  env_overrides?: Record<string, string>;
  /**
   * Brief/values for this run's declared `inputs[]`. File values may be
   * `{ file_id }` (from `uploadRunFile()`), `{ url }` (≤ 25 MB), or
   * `{ base64, name }` (≤ 7 MB).
   */
  run_inputs?: PipelineRunInputs;
}

/** Accepted-run envelope returned by `run()`. */
export interface PipelineRunAccepted {
  run_id: string;
  queued: boolean;
  /** Echoed back only when `run_inputs` was sent on the request. */
  run_inputs?: PipelineRunInputs;
}

/** Result of `uploadRunFile()`. `file_id` is single-use and valid 24h. */
export interface RunFileUploadResult {
  file_id: string;
  [key: string]: unknown;
}

/** One declared run input (`config.inputs[]`), as `setInputs()` takes it. */
export interface PipelineInputDeclaration {
  /** lower snake case, starts with a letter, max 40 chars, unique; `brief` is reserved */
  key: string;
  label?: string;
  type?: "text" | "long_text" | "number" | "boolean" | "choice" | "url" | "email" | "file" | "files";
  required?: boolean;
  /** not for file types; validated like a run value */
  default?: string | number | boolean;
  /** required for `choice` (max 50) */
  options?: string[];
  /** file types only: `pdf`, `office`, `spreadsheet`, `image`, `text` (empty = all) */
  accept?: Array<"pdf" | "office" | "spreadsheet" | "image" | "text">;
  description?: string;
}

/** Result of `setInputs()` — the normalized declaration the server stored. */
export interface PipelineInputsUpdateResult {
  name: string;
  inputs: PipelineInputDeclaration[];
  [key: string]: unknown;
}

/** Result of `runInputs()` — the brief/values/files recorded for one run. */
export interface PipelineRunInputsRecord {
  brief?: string;
  values?: Record<string, unknown>;
  files?: unknown[];
  [key: string]: unknown;
}

/** One recorded tool call, as returned by `projectToolCalls()` / `toolCallDetail()`. */
export interface ToolCall {
  spanId: string;
  runId: string;
  tool: string;
  agent?: string;
  status: "ok" | "error";
  provider?: string;
  connectorSource?: "project" | "personal";
  /** `"auto"`, `"approved"`, or `"by:<username>"`. */
  approval?: string;
  createdAt: string;
  [key: string]: unknown;
}

/** Opaque pagination cursor for `projectToolCalls()`. */
export interface ToolCallCursor {
  beforeCreatedAt: string;
  beforeId: string;
}

/** Query params for `projectToolCalls()`. */
export interface ToolCallListParams {
  beforeCreatedAt?: string;
  beforeId?: string;
  /** 1-100, default 30. */
  limit?: number;
  tool?: string;
  agent?: string;
  runId?: string;
  status?: "ok" | "error";
  search?: string;
  connectorSource?: "project" | "personal";
  /** `"auto"`, `"approved"`, or `"by:<username>"`. */
  approval?: string;
  provider?: string;
  sort?: "recent" | "oldest" | "slowest" | "fastest";
}

/** Page of tool-call history returned by `projectToolCalls()`. */
export interface ToolCallListResult {
  items: ToolCall[];
  nextCursor: ToolCallCursor | null;
  capped: boolean;
}

/** Facet counts for the tool-call audit filters. */
export interface ToolCallFacets {
  tools: { name: string; count: number }[];
  agents: string[];
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
   * Create a pipeline. `name` + `project` are required; include the pipeline
   * config in the same body. The run is generated ONLY from `steps[]` — a
   * top-level `agents[]` array with no matching steps produces an EMPTY
   * pipeline. There is no `prompt` field on an agent: `instruction` is the
   * task, and `system_prompt_override` replaces its system prompt entirely.
   *
   * See `PipelineConfig` for other config fields: `hitl_mode` (default
   * `"safe"`, which is the only mode that honours `human_approval_tools`),
   * `connector_source`, `force_local_runner`, and declared `inputs[]`.
   *
   * @example
   * ```ts
   * await melaya.agents.pipelines.create({
   *   name: "daily-digest",
   *   project: "acme",
   *   steps: [
   *     {
   *       kind: "agent",
   *       agent: {
   *         name: "researcher",
   *         role: "Research analyst",
   *         instruction: "Summarize today's top industry news into 5 bullets.",
   *         model: { provider: "anthropic", name: "claude-sonnet-4-6" },
   *         agent_tools: ["web_search"],
   *         human_approval_tools: [],
   *       },
   *     },
   *   ],
   * });
   * ```
   */
  async create(body: PipelineCreateBody): Promise<PipelineConfig> {
    return this.http.post<PipelineConfig>("/api/v1/private/pipelines", body);
  }

  /**
   * Get one pipeline's full config, wrapped in an envelope
   * `{ name, client, config, code, docs }`. Edit `.config` and pass it back
   * to `update()` — see its example.
   */
  async get(name: string, project?: string): Promise<PipelineGetEnvelope> {
    return this.http.get<PipelineGetEnvelope>(
      `/api/v1/private/pipelines/${enc(name)}`,
      project ? { project } : undefined,
    );
  }

  /**
   * Update a pipeline's config — the path for editing a step's instruction or
   * swapping a model. Pass the full config plus `project`. `get()` returns an
   * ENVELOPE, not a bare config — edit `envelope.config` and pass THAT back.
   *
   * @example
   * ```ts
   * const envelope = await melaya.agents.pipelines.get("daily-digest", "acme");
   * envelope.config.steps![0].agent!.model = { provider: "anthropic", name: "claude-opus-4-8" };
   * await melaya.agents.pipelines.update("daily-digest", envelope.config, "acme");
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
   *
   * @example Run-time inputs (brief + a previously uploaded file)
   * ```ts
   * const { file_id } = await melaya.agents.pipelines.uploadRunFile("daily-digest", "sourceDoc", bytes, {
   *   filename: "notes.pdf",
   * });
   * const { run_id, run_inputs } = await melaya.agents.pipelines.run("daily-digest", {
   *   project: "acme",
   *   run_inputs: { brief: "Focus on Q3 numbers", values: { sourceDoc: { file_id } } },
   * });
   * ```
   */
  async run(name: string, opts?: PipelineRunOptions): Promise<PipelineRunAccepted> {
    return this.http.post<PipelineRunAccepted>(
      `/api/v1/private/pipelines/${enc(name)}/run`,
      opts ?? {},
    );
  }

  /**
   * Upload a file for a subsequent `run()` call. Returns a single-use
   * `file_id` (valid 24h) to reference in `run_inputs.values.<key>`.
   */
  async uploadRunFile(
    name: string,
    key: string,
    file: FileInput,
    opts?: { project?: string; filename?: string; contentType?: string },
  ): Promise<RunFileUploadResult> {
    return this.http.postMultipart<RunFileUploadResult>(
      `/api/v1/private/pipelines/${enc(name)}/run-files`,
      { key, project: opts?.project },
      "file",
      file,
      opts?.filename ?? "file",
      opts?.contentType,
    );
  }

  /**
   * Replace ONLY the pipeline's declared run inputs (`config.inputs`), without
   * touching the rest of the config. Max 30; an empty list removes them all.
   * Editor or owner only. Throws 422 `run_inputs_invalid: <reason>` on a bad
   * declaration. Keep keys stable: `{{inputs.<key>}}` placeholders use them.
   *
   * ```ts
   * await melaya.agents.pipelines.setInputs("due-diligence", "acme", [
   *   { key: "company", label: "Company", type: "text", required: true },
   *   { key: "deck", label: "Pitch deck", type: "file", accept: ["pdf"] },
   * ]);
   * ```
   */
  async setInputs(
    name: string,
    project: string,
    inputs: PipelineInputDeclaration[],
  ): Promise<PipelineInputsUpdateResult> {
    return this.http.put<PipelineInputsUpdateResult>(
      `/api/v1/private/pipelines/${enc(name)}/inputs`,
      { inputs, project },
    );
  }

  /** Read back the brief/values/files recorded for one run's `run_inputs`. */
  async runInputs(name: string, runId: string): Promise<PipelineRunInputsRecord> {
    return this.http.get<PipelineRunInputsRecord>(
      `/api/v1/private/pipelines/${enc(name)}/runs/${enc(runId)}/inputs`,
    );
  }

  /**
   * Download one run-input file by its index (0-99) in `run_inputs.values`.
   * Returns raw bytes — this is a binary download, not JSON.
   */
  async runInputFile(name: string, runId: string, index: number): Promise<Uint8Array> {
    return this.http.getBytes(
      `/api/v1/private/pipelines/${enc(name)}/runs/${enc(runId)}/inputs/files/${index}`,
    );
  }

  /** Check whether a run is still active (queued or in progress). */
  async runActive(name: string, runId: string): Promise<{ active: boolean }> {
    return this.http.get<{ active: boolean }>(
      `/api/v1/private/pipelines/${enc(name)}/runs/${enc(runId)}/active`,
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

  // ── Static-context documents ─────────────────────────────────────────────────
  // Documents an agent reads as static context (not embedded/retrieved).

  /** List static-context documents uploaded for a pipeline. */
  async listDocs(name: string): Promise<unknown> {
    return this.http.get(`/api/v1/private/pipelines/${enc(name)}/docs`);
  }

  /**
   * Upload a static-context document. Allowed extensions: `.txt` `.md` `.pdf`
   * `.csv` `.json` `.docx` `.doc` `.pptx` `.xlsx`.
   */
  async uploadDoc(
    name: string,
    file: FileInput,
    opts?: { filename?: string; contentType?: string },
  ): Promise<unknown> {
    return this.http.postMultipart(
      `/api/v1/private/pipelines/${enc(name)}/docs`,
      undefined,
      "file",
      file,
      opts?.filename ?? "file",
      opts?.contentType,
    );
  }

  /** Delete a static-context document by filename. */
  async deleteDoc(name: string, filename: string): Promise<{ ok?: boolean } & Record<string, unknown>> {
    return this.http.delete(`/api/v1/private/pipelines/${enc(name)}/docs/${enc(filename)}`);
  }

  // ── RAG (retrieval) documents ────────────────────────────────────────────────
  // Documents embedded and retrieved at run time, as opposed to static context.

  /** Upload a document into the pipeline's retrieval corpus (not yet embedded — see `ingestRetrieval()`). */
  async uploadRetrievalDoc(
    name: string,
    file: FileInput,
    opts?: { filename?: string; contentType?: string },
  ): Promise<unknown> {
    return this.http.postMultipart(
      `/api/v1/private/pipelines/${enc(name)}/docs/retrieval`,
      undefined,
      "file",
      file,
      opts?.filename ?? "file",
      opts?.contentType,
    );
  }

  /**
   * Embed changed retrieval documents with the pipeline's configured
   * embedder. Can take minutes for a large corpus, so this call uses a 300s
   * timeout instead of the client default.
   */
  async ingestRetrieval(name: string, body: Record<string, unknown> = {}): Promise<unknown> {
    return this.http.post(
      `/api/v1/private/pipelines/${enc(name)}/docs/retrieval/ingest`,
      body,
      { timeoutMs: 300_000 },
    );
  }

  /** Delete a document from the pipeline's retrieval corpus by filename. */
  async deleteRetrievalDoc(
    name: string,
    filename: string,
  ): Promise<{ ok?: boolean } & Record<string, unknown>> {
    return this.http.delete(`/api/v1/private/pipelines/${enc(name)}/docs/retrieval/${enc(filename)}`);
  }

  /**
   * Per-document extraction stats of the static-context documents (characters
   * kept per file), with caps for the model the agents use. Pass the model to
   * see what fits its context window.
   */
  async docsPreview(name: string, opts?: { modelName?: string; modelProvider?: string }): Promise<unknown> {
    return this.http.get(`/api/v1/private/pipelines/${enc(name)}/docs/preview`, {
      model_name: opts?.modelName,
      model_provider: opts?.modelProvider,
    });
  }

  /** Stats of the pipeline's retrieval store (documents, chunks, embedder). */
  async retrievalPreview(name: string): Promise<unknown> {
    return this.http.get(`/api/v1/private/pipelines/${enc(name)}/docs/retrieval/preview`);
  }

  /**
   * Run a sample query against the pipeline's retrieval store and see the
   * passages agents would get. `limit` 1-20 (default 5). Needs an embedder
   * configured; a store built with another embedder answers 409 (re-ingest).
   */
  async testRetrieve(name: string, query: string, limit?: number): Promise<unknown> {
    return this.http.post(`/api/v1/private/pipelines/${enc(name)}/docs/retrieval/test_retrieve`, {
      query,
      ...(limit !== undefined ? { limit } : {}),
    });
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

  // ── Tool calls (audit) ────────────────────────────────────────────────────────
  // Per-project tool-call history/audit trail. Lives alongside traces since
  // both are run-observability surfaces over `/api/v1/private/{projects,runs}/*`.

  /**
   * Paginated tool-call audit trail for a project. Sort/filter server-side;
   * page with `nextCursor` (pass its `beforeCreatedAt`/`beforeId` back in
   * `params` for the next page).
   */
  async projectToolCalls(project: string, params?: ToolCallListParams): Promise<ToolCallListResult> {
    return this.http.get<ToolCallListResult>(
      `/api/v1/private/projects/${enc(project)}/tool-calls`,
      params as Record<string, string | number | boolean | undefined | null>,
    );
  }

  /** Facet counts (tool names + agents) for the tool-call audit filters. */
  async projectToolCallFacets(project: string): Promise<ToolCallFacets> {
    return this.http.get<ToolCallFacets>(`/api/v1/private/projects/${enc(project)}/tool-calls/facets`);
  }

  /** Full, untruncated input/output for one tool call by run + span id. */
  async toolCallDetail(runId: string, spanId: string): Promise<ToolCall> {
    return this.http.get<ToolCall>(
      `/api/v1/private/runs/${enc(runId)}/tool-calls/${enc(spanId)}`,
    );
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
