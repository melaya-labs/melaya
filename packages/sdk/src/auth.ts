import type { HttpClient } from "./client.js";

export type AuthResult = Record<string, unknown>;

/** Authentication, registration, password, and session endpoints. */
export class AuthAPI {
  constructor(private readonly http: HttpClient) {}

  login(username: string, password: string): Promise<AuthResult> {
    return this.http.post("/api/v1/private/auth/login", { username, password });
  }

  verifyMfa(challengeToken: string, code: string): Promise<AuthResult> {
    return this.http.post("/api/v1/private/auth/mfa/verify", { challengeToken, code });
  }

  register(body: { username: string; email: string; password: string } & AuthResult): Promise<AuthResult> {
    return this.http.post("/api/v1/private/auth/register", body);
  }

  verifySignup(token: string): Promise<AuthResult> {
    return this.http.post("/api/v1/private/auth/verify-signup", { token });
  }

  resendVerification(email: string): Promise<AuthResult> {
    return this.http.post("/api/v1/private/auth/resend-verification", { email });
  }

  forgotPassword(email: string): Promise<AuthResult> {
    return this.http.post("/api/v1/private/auth/forgot-password", { email });
  }

  resetPassword(token: string, newPassword: string): Promise<AuthResult> {
    return this.http.post("/api/v1/private/auth/reset-password", { token, newPassword });
  }

  me(): Promise<AuthResult> {
    return this.http.get("/api/v1/private/auth/me");
  }

  check(): Promise<AuthResult> {
    return this.http.get("/api/v1/private/auth/check");
  }

  changePassword(currentPassword: string, newPassword: string): Promise<AuthResult> {
    return this.http.post("/api/v1/private/auth/change-password", { currentPassword, newPassword });
  }

  createMobileHandoff(): Promise<AuthResult> {
    return this.http.post("/api/v1/private/auth/mobile-handoff");
  }

  myPermissions(): Promise<AuthResult> {
    return this.http.get("/api/v1/private/auth/permissions");
  }

  refresh(): Promise<AuthResult> {
    return this.http.post("/api/v1/private/auth/refresh");
  }
}
