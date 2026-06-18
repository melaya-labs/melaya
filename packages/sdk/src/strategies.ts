/**
 * Strategies API — launch, control, and inspect trading strategies.
 *
 * A strategy is a server-managed runner (the Trading Engine, or an Agentic
 * Trading Crew) that trades a universe on a cadence with server-side SL/TP and
 * safety rails. Launch in paper mode (`dryRun: true`) or live (`dryRun: false`,
 * which requires a connected exchange key — see `melaya.account.keys()`).
 *
 * Maps to `https://api.melaya.org/api/v1/strategies/*` on the private plane.
 */
import type { HttpClient } from "./client.js";
import type { Strategy, StrategyCreate, StrategyCreateResult } from "./types.js";

export class StrategiesAPI {
  constructor(private readonly http: HttpClient) {}

  /** Every strategy you own (running, paused, paper, and live). */
  async list(): Promise<Strategy[]> {
    return (await this.http.get<{ strategies: Strategy[] }>("/api/v1/strategies/list")).strategies;
  }

  /** A single strategy by id. */
  async get(strategyId: string): Promise<Strategy> {
    return (await this.http.get<{ strategy: Strategy }>(`/api/v1/strategies/${strategyId}`)).strategy;
  }

  /**
   * Launch a strategy. Pass `dryRun: true` for paper; `dryRun: false` places
   * real orders and requires a connected `apiKeyId`. Returns the new id.
   */
  async create(body: StrategyCreate): Promise<StrategyCreateResult> {
    return await this.http.post<StrategyCreateResult>("/api/v1/strategies", body);
  }

  /** Pause a running strategy (it stops entering new cycles until resumed). */
  async pause(strategyId: string): Promise<{ ok: boolean; success?: boolean }> {
    return await this.http.post<{ ok: boolean; success?: boolean }>(`/api/v1/strategies/${strategyId}/pause`);
  }

  /** Resume a paused strategy. */
  async resume(strategyId: string): Promise<{ ok: boolean; success?: boolean }> {
    return await this.http.post<{ ok: boolean; success?: boolean }>(`/api/v1/strategies/${strategyId}/resume`);
  }

  /** Stop a strategy and tear down its runner. Cancels any in-flight approvals. */
  async stop(strategyId: string): Promise<{ ok: boolean; success?: boolean }> {
    return await this.http.post<{ ok: boolean; success?: boolean }>(`/api/v1/strategies/${strategyId}/stop`);
  }

  /** Soft-delete a strategy. */
  async delete(strategyId: string): Promise<{ ok: boolean; success?: boolean }> {
    return await this.http.delete<{ ok: boolean; success?: boolean }>(`/api/v1/strategies/${strategyId}`);
  }

  /** Update a running strategy's params (e.g. universe, cadence, risk caps). */
  async updateParams(strategyId: string, params: Record<string, unknown>): Promise<{ ok: boolean; success?: boolean }> {
    return await this.http.post<{ ok: boolean; success?: boolean }>(`/api/v1/strategies/${strategyId}/update-params`, params);
  }

  /** Live runtime status of a strategy's runner (container health, tick count). */
  async status(strategyId: string): Promise<Record<string, unknown>> {
    return await this.http.get<Record<string, unknown>>(`/api/v1/strategies/${strategyId}/status`);
  }

  /** Performance series for a strategy (equity, PnL over time). */
  async performance(strategyId: string): Promise<unknown[]> {
    return (await this.http.get<{ rows: unknown[] }>(`/api/v1/strategies/${strategyId}/performance`)).rows;
  }

  /** Execution (order) rows for a strategy. */
  async executions(strategyId: string): Promise<unknown[]> {
    return (await this.http.get<{ rows: unknown[] }>(`/api/v1/strategies/${strategyId}/executions`)).rows;
  }

  /** Trade (fill) rows for a strategy. */
  async trades(strategyId: string): Promise<unknown[]> {
    return (await this.http.get<{ rows: unknown[] }>(`/api/v1/strategies/${strategyId}/trades`)).rows;
  }

  /** Log rows for a strategy (cycle markers, persona messages, errors). */
  async logs(strategyId: string): Promise<unknown[]> {
    return (await this.http.get<{ rows: unknown[] }>(`/api/v1/strategies/${strategyId}/logs`)).rows;
  }

  // ── AI parameter optimizer ────────────────────────────────────────────────

  /**
   * Kick off an AI-driven parameter optimization. `paramBounds` maps each
   * param to a `[min, max]` range; `objective` defaults to `sharpe`,
   * `maxIterations` is clamped to 1-20. Returns the optimization `runId`.
   */
  async aiOptStart(strategyId: string, body: { paramBounds: Record<string, [number, number]>; objective?: string; maxIterations?: number; requireApproval?: boolean }): Promise<{ ok: boolean; runId: string }> {
    return await this.http.post<{ ok: boolean; runId: string }>(`/api/v1/strategies/${strategyId}/ai-opt/start`, body);
  }

  /** Current optimization status for a strategy. */
  async aiOptStatus(strategyId: string): Promise<Record<string, unknown>> {
    return await this.http.get<Record<string, unknown>>(`/api/v1/strategies/${strategyId}/ai-opt/status`);
  }

  /** Approve and apply the optimizer's proposed params to the running strategy. */
  async aiOptApprove(strategyId: string, body: Record<string, unknown> = {}): Promise<Record<string, unknown>> {
    return await this.http.post<Record<string, unknown>>(`/api/v1/strategies/${strategyId}/ai-opt/approve`, body);
  }

  /** Stop an in-progress optimization. */
  async aiOptStop(strategyId: string): Promise<{ ok: boolean; success?: boolean }> {
    return await this.http.post<{ ok: boolean; success?: boolean }>(`/api/v1/strategies/${strategyId}/ai-opt/stop`);
  }

  /** Past optimization runs for a strategy. */
  async aiOptRuns(strategyId: string): Promise<unknown> {
    return await this.http.get<unknown>(`/api/v1/strategies/${strategyId}/ai-opt/runs`);
  }
}
