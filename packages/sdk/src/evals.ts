/**
 * Evaluations API — per-run loop / evaluation metrics, quality + cost
 * summaries, and the benchmark catalogue.
 *
 * Covers the `/api/v1/private/evals/*` surface (5 GET endpoints). Every read is
 * tenant-scoped server-side: you only see runs you own or runs in a project you
 * belong to. A cross-tenant or unknown `runId` is reported as
 * 404 `not_found` so existence cannot be probed. There is no tier gate.
 *
 * The API key is always sent as `Authorization: Bearer <key>` by the shared
 * HttpClient — never in the query string.
 */
import type { HttpClient } from "./client.js";

const enc = encodeURIComponent;

/** Time window over `runs.created_at`. `week` = last 7 days, `month` = last
 *  30 days, `all` = no bound. */
export type EvalRange = "week" | "month" | "all";

/** Per-tool forensic issue attached to a phase verdict (Wave 5). */
export interface EvalToolIssue {
  tool: string;
  /** empty | error | auth | transient | oversized | contract | bad_args | stall | coverage */
  status: string;
  /** info | warn | high */
  severity: string;
  detail: string;
}

/** Compact eval roll-up over a run's `eval_metrics.phases`. */
export interface EvalSummary {
  evaluated: boolean;
  phaseCount: number;
  finalized: number;
  failed: number;
  avgScore: number | null;
  failureKinds: Record<string, number>;
  toolIssues: Record<string, number>;
  toolIssueCount: number;
}

/** Estimated run cost (from spans tokens priced per model/provider). */
export interface EvalRunCost {
  costUsd: number;
  inTokens: number;
  outTokens: number;
}

/** One row of `listRuns()` / one entry of `compare()`. */
export interface EvalRunSummary {
  id: string;
  project: string | null;
  name: string;
  status: string;
  createdAt: string;
  isAccepted: boolean | null;
  evaluationTimestamp: string | null;
  eval: EvalSummary;
  cost: EvalRunCost;
  costPerAccepted: number | null;
}

/** One phase's verdict as returned by `runDetail()` (and re-used by the
 *  Agent Memory API's `runMemory()`). */
export interface EvalPhaseVerdict {
  phaseId: string;
  decision: string;
  failure_kind: string;
  score: number;
  attempt_idx: number;
  evaluator_id: string;
  mode: string;
  agent_label: string;
  reason: string;
  tool_issues?: EvalToolIssue[];
  tool_summary?: string;
  ts: string;
}

/** Window-complete aggregates returned by `summary()`. */
export interface EvalSummaryAggregate {
  totalRuns: number;
  evaluated: number;
  finalized: number;
  failed: number;
  acceptanceRate: number | null;
  avgScore: number | null;
  totalCost: number;
  inTokens: number;
  outTokens: number;
  costPerAccepted: number | null;
  failureKinds: Record<string, number>;
}

/** Full per-run detail returned by `runDetail()`. */
export interface EvalRunDetail {
  id: string;
  project: string | null;
  name: string;
  status: string;
  createdAt: string;
  isAccepted: boolean | null;
  retryParentId: string | null;
  evaluationTimestamp: string | null;
  summary: EvalSummary;
  cost: EvalRunCost;
  costPerAccepted: number | null;
  phases: EvalPhaseVerdict[];
}

/** One entry of the benchmark catalogue (scaffold; empty today). */
export interface EvalBenchmark {
  id: string;
  name: string;
  description: string;
}

/** Params for `listRuns()`. */
export interface EvalListRunsParams {
  /** Restrict to runs in this exact project. */
  project?: string;
  /** Max rows (1-200). Defaults to 50 server-side. */
  limit?: number;
  /** Only return runs that have been evaluated (eval_metrics present). */
  withEvalOnly?: boolean;
  /** Time window over runs.created_at. Defaults to `all`. */
  range?: EvalRange;
}

/** Params for `summary()`. */
export interface EvalSummaryParams {
  project?: string;
  range?: EvalRange;
}

export class EvalsAPI {
  constructor(private readonly http: HttpClient) {}

  /**
   * List the caller's runs, newest first, each with a rolled-up eval summary
   * and estimated cost.
   *
   * @example
   * ```ts
   * const runs = await melaya.agents.evals.listRuns({ project: "acme", range: "week" });
   * ```
   */
  async listRuns(params?: EvalListRunsParams): Promise<EvalRunSummary[]> {
    return this.http.get<EvalRunSummary[]>("/api/v1/private/evals/runs", {
      project: params?.project,
      limit: params?.limit,
      withEvalOnly: params?.withEvalOnly,
      range: params?.range,
    });
  }

  /** Window-complete quality + cost aggregates over every run in the window. */
  async summary(params?: EvalSummaryParams): Promise<EvalSummaryAggregate> {
    return this.http.get<EvalSummaryAggregate>("/api/v1/private/evals/summary", {
      project: params?.project,
      range: params?.range,
    });
  }

  /**
   * One run's per-phase verdicts with scores and tool forensics.
   * A cross-tenant / unknown `runId` throws a `MelayaError` with status 404.
   */
  async runDetail(runId: string): Promise<EvalRunDetail> {
    return this.http.get<EvalRunDetail>(`/api/v1/private/evals/runs/${enc(runId)}`);
  }

  /**
   * Compare exactly two runs side by side. Each run is access-checked
   * individually, so you can never smuggle in another tenant's run.
   *
   * @param runIds A tuple of exactly two run ids.
   */
  async compare(runIds: [string, string]): Promise<{ runs: EvalRunSummary[] }> {
    // Sent as repeated `runIds` query keys so the server's array schema
    // (length exactly 2) validates over the REST bridge.
    const qs = new URLSearchParams();
    for (const id of runIds) qs.append("runIds", id);
    return this.http.get<{ runs: EvalRunSummary[] }>(
      `/api/v1/private/evals/compare?${qs.toString()}`,
    );
  }

  /** Benchmark / eval-suite catalogue (scaffold; empty today). */
  async benchmarks(): Promise<{ benchmarks: EvalBenchmark[] }> {
    return this.http.get<{ benchmarks: EvalBenchmark[] }>("/api/v1/private/evals/benchmarks");
  }
}

