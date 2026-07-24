import type { HttpClient } from "./client.js";

/** TOTP MFA enrollment and status endpoints. */
export class MfaAPI {
  constructor(private readonly http: HttpClient) {}

  status(): Promise<Record<string, unknown>> {
    return this.http.get("/api/v1/private/mfa/status");
  }

  setup(): Promise<Record<string, unknown>> {
    return this.http.post("/api/v1/private/mfa/setup");
  }

  confirm(code: string): Promise<Record<string, unknown>> {
    return this.http.post("/api/v1/private/mfa/confirm", { code });
  }
}
