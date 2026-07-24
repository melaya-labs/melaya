/**
 * Runner API — mint, list, and revoke runner tokens.
 *
 * Maps to `/api/v1/private/runner/tokens`. Runner tokens (`mel_run_` prefix)
 * authenticate the `@melaya/runner` CLI process that executes agent pipelines
 * on your infrastructure.
 *
 * @example
 * ```ts
 * const { token } = await melaya.runner.createToken({ label: "prod-server-1" });
 * // Store token securely — it is shown only once.
 *
 * const tokens = await melaya.runner.listTokens();
 * await melaya.runner.revokeToken(tokens[0].id);
 * ```
 */
import type { HttpClient } from "./client.js";
import type { RunnerToken, RunnerTokenCreate, RunnerTokenCreateResult } from "./platform-types.js";

export class RunnerAPI {
  constructor(private readonly http: HttpClient) {}

  /**
   * Mint a new runner token.
   * The plaintext token is returned only in this response — store it securely.
   */
  async createToken(body?: RunnerTokenCreate): Promise<RunnerTokenCreateResult> {
    return this.http.post<RunnerTokenCreateResult>("/api/v1/private/runner/tokens", body);
  }

  /** List all runner tokens for the caller (masked, with last_seen). */
  async listTokens(): Promise<RunnerToken[]> {
    return this.http.get<RunnerToken[]>("/api/v1/private/runner/tokens");
  }

  /** Revoke a runner token by ID. */
  async revokeToken(tokenId: string): Promise<{ ok: boolean }> {
    return this.http.delete<{ ok: boolean }>(
      `/api/v1/private/runner/tokens/${encodeURIComponent(tokenId)}`,
    );
  }
}
