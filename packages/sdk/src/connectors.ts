/**
 * Project Connectors API — manage credentials at project scope.
 *
 * Maps to `/api/v1/private/projects/:projectId/connectors/*`. Project-scoped
 * connectors are isolated per project, letting separate projects use different
 * API keys for the same service.
 *
 * @example
 * ```ts
 * await melaya.connectors.set("proj_abc", "openai", { value: "sk-..." });
 * const services = await melaya.connectors.connectedServices("proj_abc");
 * ```
 */
import type { HttpClient } from "./client.js";
import type { ConnectedService, ProjectConnectorSetBody } from "./platform-types.js";

export class ConnectorsAPI {
  constructor(private readonly http: HttpClient) {}

  /** List connected services for a project. */
  async connectedServices(projectId: string): Promise<ConnectedService[]> {
    return this.http.get<ConnectedService[]>(
      `/api/v1/private/projects/${encodeURIComponent(projectId)}/connectors/services`,
    );
  }

  /** Store a connector credential at project scope. */
  async set(
    projectId: string,
    service: string,
    body: ProjectConnectorSetBody,
  ): Promise<{ ok: boolean }> {
    return this.http.put<{ ok: boolean }>(
      `/api/v1/private/projects/${encodeURIComponent(projectId)}/connectors/${encodeURIComponent(service)}`,
      body,
    );
  }

  /** Delete a project-scoped connector credential. */
  async delete(projectId: string, service: string): Promise<{ ok: boolean }> {
    return this.http.delete<{ ok: boolean }>(
      `/api/v1/private/projects/${encodeURIComponent(projectId)}/connectors/${encodeURIComponent(service)}`,
    );
  }

  /**
   * Get a short-lived env-handle token for project-scoped credentials.
   * The runner uses this token to decrypt credentials without a full session.
   */
  async envHandle(projectId: string): Promise<{ token: string; expiresAt?: string }> {
    return this.http.post<{ token: string; expiresAt?: string }>(
      `/api/v1/private/projects/${encodeURIComponent(projectId)}/connectors/env-handle`,
    );
  }

  async googleOAuthStart(
    projectId: string,
    body: Record<string, unknown> = {},
  ): Promise<Record<string, unknown>> {
    return this.http.post(
      `/api/v1/private/projects/${encodeURIComponent(projectId)}/connectors/google/oauth`,
      body,
    );
  }

  /**
   * Share the caller's OWN personal connector into a project (editor/owner
   * only). For Google, optionally scope the share to specific capabilities
   * (`gmail`, `calendar`, `drive`, `sheets`, `docs`, `search_console`,
   * `youtube`, `google_ads`, `analytics`, `meet`, `slides`).
   */
  async applyPersonal(
    projectId: string,
    service: string,
    googleCapabilities?: string[],
  ): Promise<{ ok: boolean } & Record<string, unknown>> {
    return this.http.post(
      `/api/v1/private/projects/${encodeURIComponent(projectId)}/connectors/${encodeURIComponent(service)}/apply-personal`,
      googleCapabilities ? { googleCapabilities } : {},
    );
  }

  /** List connectors shared into this project by other team members. */
  async sharedBy(projectId: string): Promise<unknown> {
    return this.http.get(`/api/v1/private/projects/${encodeURIComponent(projectId)}/connectors/shared-by`);
  }

  /** Status of the project's connected Google accounts and default assignment per capability. */
  async googleStatus(projectId: string): Promise<Record<string, unknown>> {
    return this.http.get(`/api/v1/private/projects/${encodeURIComponent(projectId)}/connectors/google/status`);
  }

  /** Set the project's default connected Google account for one capability. `accountId` is a 24-hex-char id. */
  async googleSetDefault(
    projectId: string,
    capability: string,
    accountId: string,
  ): Promise<{ ok: boolean }> {
    return this.http.put(
      `/api/v1/private/projects/${encodeURIComponent(projectId)}/connectors/google/default`,
      { capability, accountId },
    );
  }

  /** Disconnect a Google account from this project, optionally scoped to one capability. */
  async googleDisconnect(
    projectId: string,
    accountId: string,
    capability?: string,
  ): Promise<{ ok: boolean }> {
    return this.http.delete(
      `/api/v1/private/projects/${encodeURIComponent(projectId)}/connectors/google/access`,
      undefined,
      { accountId, capability },
    );
  }

  /** Probe a database connection at project scope from the caller's runner. Poll with `dbTestStatus()`. */
  async dbTestStart(
    projectId: string,
    service: string,
    credentials?: Record<string, string>,
  ): Promise<{ sessionId: string } & Record<string, unknown>> {
    return this.http.post(
      `/api/v1/private/projects/${encodeURIComponent(projectId)}/connectors/db-test`,
      { service, credentials },
    );
  }

  /** Poll a project-scoped database connection test started with `dbTestStart()`. */
  async dbTestStatus(projectId: string, sessionId: string): Promise<Record<string, unknown>> {
    return this.http.get(
      `/api/v1/private/projects/${encodeURIComponent(projectId)}/connectors/db-test/${encodeURIComponent(sessionId)}`,
    );
  }
}
