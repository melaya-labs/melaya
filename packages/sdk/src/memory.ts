/**
 * Agent Memory API — the memory-constellation graph, one run's agent memory +
 * context drill-down, and cross-run persistent crew memory.
 *
 * Covers the `/api/v1/private/memory/*` surface (3 GET endpoints). Every read is
 * tenant-scoped server-side: you only see runs you own or runs in a project you
 * belong to. A cross-tenant or unknown `runId` is reported as
 * 404 `not_found` so existence cannot be probed. There is no tier gate.
 *
 * The API key is always sent as `Authorization: Bearer <key>` by the shared
 * HttpClient — never in the query string.
 */
import type { HttpClient } from "./client.js";
import type { EvalSummary, EvalPhaseVerdict } from "./evals.js";

const enc = encodeURIComponent;

/** Time window over `runs.created_at`. `week` = last 7 days, `month` = last
 *  30 days, `all` = no bound. */
export type MemoryRange = "week" | "month" | "all";

/** A compact preview of one stored agent message. */
export interface MemoryMsgPreview {
  role: string;
  name: string;
  text: string;
  tools: { kind: "tool_use" | "tool_result"; name: string }[];
  ts: string;
}

/** Agent memory + context drill-down returned by `runMemory()`. */
export interface RunMemory {
  id: string;
  name: string;
  project: string | null;
  status: string;
  createdAt: string;
  retryParentId: string | null;
  summary: EvalSummary;
  phases: EvalPhaseVerdict[];
  messages: MemoryMsgPreview[];
  tokens: number;
  spanCount: number;
  rag: { available: boolean; chunks: Array<{ id: string; text: string; score: number }> };
}

/** A node in the memory-constellation graph. */
export type MemoryGraphNode = Record<string, unknown> & {
  id: string;
  type: "pipeline" | "run" | "phase" | "tool";
  label: string;
  group: string;
};

/** An edge in the memory-constellation graph. */
export interface MemoryGraphEdge {
  source: string;
  target: string;
  kind: "contains" | "flow" | "retry" | "uses";
}

/** The memory-constellation graph returned by `graph()`. */
export interface MemoryGraph {
  nodes: MemoryGraphNode[];
  edges: MemoryGraphEdge[];
  stats: { runs: number; pipelines: number; nodes: number; edges: number };
}

/** One persisted cross-run crew memory entry. */
export interface CrewMemoryEntry {
  created?: string;
  topic?: string;
  content?: string;
  tags?: string[];
  run_id?: string;
}

/** Result of `crew()`. `local: true` means memory is kept on the user's
 *  runner and not stored on Melaya. */
export interface CrewMemoryResult {
  local: boolean;
  available: boolean;
  entries: CrewMemoryEntry[];
}

/** Params for `graph()`. */
export interface MemoryGraphParams {
  project?: string;
  /** Time window. Defaults to `month` for this endpoint. */
  range?: MemoryRange;
  /** Max runs materialized into the graph (1-80). Defaults to 40. */
  limit?: number;
}

/** Params for `crew()`. */
export interface CrewMemoryParams {
  /** Pipeline / crew name whose persistent memory to read (required). */
  pipeline: string;
  /** Optional project to disambiguate the pipeline name. */
  project?: string;
}

export class MemoryAPI {
  constructor(private readonly http: HttpClient) {}

  /** Memory-constellation nodes + edges for the 3D memory panel. */
  async graph(params?: MemoryGraphParams): Promise<MemoryGraph> {
    return this.http.get<MemoryGraph>("/api/v1/private/memory/graph", {
      project: params?.project,
      range: params?.range,
      limit: params?.limit,
    });
  }

  /**
   * One run's agent memory, context, phases, tokens and span count.
   * Access-checked exactly like the eval run-detail read; 404 for
   * unknown/cross-tenant runs.
   */
  async runMemory(runId: string, params?: { limit?: number }): Promise<RunMemory> {
    return this.http.get<RunMemory>(
      `/api/v1/private/memory/runs/${enc(runId)}`,
      { limit: params?.limit },
    );
  }

  /**
   * Cross-run persistent crew memory for a pipeline (keyed by pipeline name,
   * not a run id). Scoped to the caller's own memory directory.
   */
  async crew(params: CrewMemoryParams): Promise<CrewMemoryResult> {
    return this.http.get<CrewMemoryResult>("/api/v1/private/memory/crew", {
      pipeline: params.pipeline,
      project: params.project,
    });
  }
}

