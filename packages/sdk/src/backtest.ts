/**
 * Backtest API — run strategies against historical data on the Rust engine.
 *
 * Start a single run or a parameter sweep (grid / random), poll the job,
 * then pull metrics, the equity curve, and the trade list. All backtests run
 * natively on Melaya's in-house engine — no per-venue SDK in the loop.
 *
 * Maps to `https://api.melaya.org/api/v1/private/backtest/*` on the private plane.
 */
import type { HttpClient } from "./client.js";
import type { BacktestStart, BacktestJob, BacktestResult } from "./types.js";

export class BacktestAPI {
  constructor(private readonly http: HttpClient) {}

  /**
   * Start a backtest. `mode` defaults to a single run; pass `grid_sweep` /
   * `random_sweep` with `paramRanges` to fan out a parameter search. Returns
   * the job id(s) — poll with {@link job}.
   */
  async start(body: BacktestStart): Promise<{ job_id: string; count?: number; [k: string]: unknown }> {
    return await this.http.post<{ job_id: string; count?: number }>("/api/v1/private/backtest/start", body);
  }

  /** Job status + progress (`status`, `progress_pct`, ...). */
  async job(jobId: string): Promise<BacktestJob> {
    return await this.http.get<BacktestJob>(`/api/v1/private/backtest/jobs/${jobId}`);
  }

  /** Metrics, equity curve, and OHLCV for a completed job. */
  async results(jobId: string): Promise<BacktestResult> {
    return (await this.http.get<{ result: BacktestResult }>(`/api/v1/private/backtest/results/${jobId}`)).result;
  }

  /** The trade list for a completed job (default 500, max 5000 per call). */
  async trades(jobId: string, q: { limit?: number; offset?: number } = {}): Promise<unknown[]> {
    return (await this.http.get<{ trades: unknown[] }>(`/api/v1/private/backtest/trades/${jobId}`, q)).trades;
  }

  /** Ranked children of a sweep parent (default objective: sharpe DESC). */
  async sweep(parentId: string, q: { objective?: string; limit?: number } = {}): Promise<Record<string, unknown>> {
    return await this.http.get<Record<string, unknown>>(`/api/v1/private/backtest/sweep/${parentId}`, q);
  }

  /** Your backtest jobs, newest first. */
  async list(q: { limit?: number; offset?: number } = {}): Promise<unknown[]> {
    return (await this.http.get<{ data: { jobs: unknown[] } }>("/api/v1/private/backtest", q)).data.jobs;
  }

  /** Your favorited backtest jobs (Forge tier and above). */
  async favorites(q: { limit?: number; offset?: number } = {}): Promise<unknown[]> {
    return (await this.http.get<{ data: { jobs: unknown[] } }>("/api/v1/private/backtest/favorites", q)).data.jobs;
  }

  /** Earliest funding-rate timestamp available for an exchange+symbol (ms, or null). */
  async fundingRange(q: { exchange: string; symbol: string }): Promise<number | null> {
    return (await this.http.get<{ earliest_ms: number | null }>("/api/v1/private/backtest/funding-range", q)).earliest_ms;
  }

  /** Cancel an in-flight job. */
  async cancel(jobId: string): Promise<Record<string, unknown>> {
    return await this.http.post<Record<string, unknown>>(`/api/v1/private/backtest/${jobId}/cancel`);
  }

  /** Soft-delete a single job. */
  async delete(jobId: string): Promise<{ ok: boolean; success?: boolean }> {
    return await this.http.delete<{ ok: boolean; success?: boolean }>(`/api/v1/private/backtest/${jobId}`);
  }

  /** Soft-delete every non-favorited job. Returns the count deleted. */
  async deleteAll(): Promise<{ ok: boolean; deleted: number }> {
    return await this.http.delete<{ ok: boolean; deleted: number }>("/api/v1/private/backtest");
  }
}
