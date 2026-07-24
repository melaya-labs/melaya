import type { HttpClient } from "./client.js";

export type AccountPlatformResult = Record<string, unknown>;

/** User profile, GDPR export, credit balances, and stored CEX-key lifecycle. */
export class AccountsAPI {
  constructor(private readonly http: HttpClient) {}

  exportMyData(): Promise<AccountPlatformResult> {
    return this.http.post("/api/v1/private/accounts/export");
  }

  removeKey(keyId: string): Promise<AccountPlatformResult> {
    return this.http.delete(`/api/v1/private/keys/${encodeURIComponent(keyId)}`);
  }

  updateProfile(body: AccountPlatformResult): Promise<AccountPlatformResult> {
    return this.http.patch("/api/v1/private/accounts/profile", body);
  }

  credits(): Promise<AccountPlatformResult> {
    return this.http.get("/api/v1/private/accounts/credits");
  }

  aiCredits(): Promise<AccountPlatformResult> {
    return this.http.get("/api/v1/private/accounts/credits/ai");
  }

  portfolioIdeasCredits(): Promise<AccountPlatformResult> {
    return this.http.get("/api/v1/private/accounts/credits/portfolio-ideas");
  }

  riskMonitoringCredits(): Promise<AccountPlatformResult> {
    return this.http.get("/api/v1/private/accounts/credits/risk-monitoring");
  }
}
