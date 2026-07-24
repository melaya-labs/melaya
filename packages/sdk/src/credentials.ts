/**
 * Credentials API — store, retrieve, test, and delete secrets and third-party
 * service connections at user scope.
 *
 * Maps to `/api/v1/private/credentials/*`. Credentials are envelope-encrypted
 * at rest. Use `melaya.connectors` for project-scoped connector credentials.
 *
 * @example
 * ```ts
 * await melaya.credentials.set("openai", { value: "sk-...", label: "OpenAI prod key" });
 * const ok = await melaya.credentials.test("openai");
 * ```
 */
import type { HttpClient } from "./client.js";
import type {
  AIModel,
  ConnectedService,
  Credential,
  CredentialSetBody,
  CredentialTestResult,
  OperatorProfile,
} from "./platform-types.js";

export class CredentialsAPI {
  constructor(private readonly http: HttpClient) {}

  /** List all stored credentials (services, OAuth connections, env handles). */
  async list(): Promise<Credential[]> {
    return this.http.get<Credential[]>("/api/v1/private/credentials");
  }

  /** List connected third-party services. */
  async connectedServices(): Promise<ConnectedService[]> {
    return this.http.get<ConnectedService[]>("/api/v1/private/credentials/services");
  }

  /**
   * Get a stored credential value by service name.
   * Pass `key` to retrieve a specific named key within the service.
   */
  async get(service: string, key?: string): Promise<Credential> {
    return this.http.get<Credential>(
      `/api/v1/private/credentials/${encodeURIComponent(service)}`,
      key ? { key } : undefined,
    );
  }

  /**
   * Store or update a credential (envelope-encrypted at rest).
   *
   * @example
   * ```ts
   * await melaya.credentials.set("telegram", { value: "my-bot-token" });
   * ```
   */
  async set(service: string, body: CredentialSetBody): Promise<{ ok: boolean }> {
    return this.http.put<{ ok: boolean }>(
      `/api/v1/private/credentials/${encodeURIComponent(service)}`,
      body,
    );
  }

  /** Delete a stored credential by service name. */
  async delete(service: string): Promise<{ ok: boolean }> {
    return this.http.delete<{ ok: boolean }>(
      `/api/v1/private/credentials/${encodeURIComponent(service)}`,
    );
  }

  /** Test a stored credential (e.g. validate an API key against its target service). */
  async test(service: string): Promise<CredentialTestResult> {
    return this.http.post<CredentialTestResult>(
      `/api/v1/private/credentials/${encodeURIComponent(service)}/test`,
    );
  }

  // ── Operator profile ─────────────────────────────────────────────────────────

  /** Get the operator profile (persona config injected into agent context). */
  async getOperatorProfile(): Promise<OperatorProfile> {
    return this.http.get<OperatorProfile>("/api/v1/private/credentials/operator-profile");
  }

  /** Save the operator profile. */
  async setOperatorProfile(profile: OperatorProfile): Promise<{ ok: boolean }> {
    return this.http.put<{ ok: boolean }>(
      "/api/v1/private/credentials/operator-profile",
      profile,
    );
  }

  // ── AI models ────────────────────────────────────────────────────────────────

  async getLumaRegistrationSchema(eventId?: string): Promise<Record<string, unknown>> {
    return this.http.get("/api/v1/private/credentials/luma/schema", { eventId });
  }

  async ragIngestStart(body: Record<string, unknown>): Promise<Record<string, unknown>> {
    return this.http.post("/api/v1/private/rag/ingest", body);
  }

  async ragIngestStatus(sessionId: string): Promise<Record<string, unknown>> {
    return this.http.get(`/api/v1/private/rag/ingest/${encodeURIComponent(sessionId)}`);
  }

  async ragRetrieveStart(body: Record<string, unknown>): Promise<Record<string, unknown>> {
    return this.http.post("/api/v1/private/rag/retrieve", body);
  }

  async ragRetrieveStatus(sessionId: string): Promise<Record<string, unknown>> {
    return this.http.get(`/api/v1/private/rag/retrieve/${encodeURIComponent(sessionId)}`);
  }

  async pickFolderStart(body: Record<string, unknown> = {}): Promise<Record<string, unknown>> {
    return this.http.post("/api/v1/private/rag/pick-folder", body);
  }

  async pickFolderStatus(sessionId: string): Promise<Record<string, unknown>> {
    return this.http.get(`/api/v1/private/rag/pick-folder/${encodeURIComponent(sessionId)}`);
  }

  async linkedinConnectStart(body: Record<string, unknown> = {}): Promise<Record<string, unknown>> {
    return this.http.post("/api/v1/private/credentials/linkedin/connect", body);
  }

  async linkedinConnectCancel(): Promise<Record<string, unknown>> {
    return this.http.delete("/api/v1/private/credentials/linkedin/connect");
  }

  async linkedinConnectStatus(): Promise<Record<string, unknown>> {
    return this.http.get("/api/v1/private/credentials/linkedin/connect/status");
  }

  async lumaConnectStart(body: Record<string, unknown> = {}): Promise<Record<string, unknown>> {
    return this.http.post("/api/v1/private/credentials/luma/connect", body);
  }

  async lumaConnectCancel(): Promise<Record<string, unknown>> {
    return this.http.delete("/api/v1/private/credentials/luma/connect");
  }

  async lumaConnectStatus(): Promise<Record<string, unknown>> {
    return this.http.get("/api/v1/private/credentials/luma/connect/status");
  }

  async melayaAccounts(): Promise<Record<string, unknown>[]> {
    return this.http.get("/api/v1/private/credentials/melaya-accounts");
  }

  async googleOAuthStart(body: Record<string, unknown> = {}): Promise<Record<string, unknown>> {
    return this.http.post("/api/v1/private/credentials/google/oauth", body);
  }

  async cliAuthStart(body: Record<string, unknown> = {}): Promise<Record<string, unknown>> {
    return this.http.post("/api/v1/private/credentials/cli-auth", body);
  }

  async notebookLMLogin(body: Record<string, unknown>): Promise<Record<string, unknown>> {
    return this.http.post("/api/v1/private/credentials/notebooklm/login", body);
  }

  async notebookLMStatus(): Promise<Record<string, unknown>> {
    return this.http.get("/api/v1/private/credentials/notebooklm/status");
  }

  async telegramAuthStart(phone: string): Promise<Record<string, unknown>> {
    return this.http.post("/api/v1/private/credentials/telegram/auth", { phone });
  }

  async telegramAuthCode(code: string): Promise<Record<string, unknown>> {
    return this.http.post("/api/v1/private/credentials/telegram/auth/code", { code });
  }

  async telegramAuth2fa(password: string): Promise<Record<string, unknown>> {
    return this.http.post("/api/v1/private/credentials/telegram/auth/2fa", { password });
  }

  /**
   * List available AI models across all configured providers.
   * Collapses 19+ provider fan-out into a parameterized query.
   *
   * @example
   * ```ts
   * const models = await melaya.credentials.listModels();
   * ```
   */
  async listModels(params?: { provider?: string; capability?: string }): Promise<AIModel[]> {
    return this.http.get<AIModel[]>("/api/v1/private/credentials/models", {
      ...(params as Record<string, string | undefined | null>),
    });
  }
}
