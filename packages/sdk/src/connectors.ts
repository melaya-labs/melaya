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
}
