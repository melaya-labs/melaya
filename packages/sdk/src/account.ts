/**
 * Account API — authenticated reads about your Melaya account.
 *
 * Maps to `https://api.melaya.org/api/v1/private/*`. Requires an `mk_` key on
 * the private plane (sent as `Authorization: Bearer mk_...`). These are reads
 * only: connected-exchange key references (masked), tier limits, and live
 * usage counters.
 */
import type { HttpClient } from "./client.js";
import type { ConnectedKey, UsageSummary } from "./types.js";

export class AccountAPI {
  constructor(private readonly http: HttpClient) {}

  /**
   * The exchange API keys connected to your account. `apiKey` is masked
   * (display-only); use `apiKeyId` (e.g. `BINANCEUSDM_0`) as the reference
   * when launching strategies or minting a private stream ticket.
   */
  async keys(): Promise<ConnectedKey[]> {
    return (await this.http.get<{ keys: ConnectedKey[] }>("/api/v1/private/keys")).keys;
  }

  /** Tier, plan limits, and live usage counters (mirrors the dashboard's usage page). */
  async usage(): Promise<UsageSummary> {
    return await this.http.get<UsageSummary>("/api/v1/private/usage");
  }

  /** Status of your platform API key (tier, max concurrent connections). */
  async apiKeyStatus(): Promise<Record<string, unknown>> {
    return await this.http.get<Record<string, unknown>>("/api/v1/private/api-key");
  }
}
