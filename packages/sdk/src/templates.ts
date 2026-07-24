/**
 * Templates API — create, manage, and share pipeline templates.
 *
 * Maps to `/api/v1/private/user-templates/*` and
 * `/api/v1/private/templates/*`.
 *
 * Templates bundle a pipeline definition into a reusable, shareable artifact.
 * Visibility levels: `private` (only you) → `team` → `community` → `assigned`
 * (explicitly assigned to users or projects).
 *
 * @example
 * ```ts
 * const templates = await melaya.templates.list();
 * const { id } = await melaya.templates.save({ name: "My report", payload: { ... } });
 * await melaya.templates.share(id, "team");
 * ```
 */
import type { HttpClient } from "./client.js";
import type {
  TemplateAssignment,
  TemplateSaveBody,
  TemplateUpdateBody,
  TemplateVisibility,
  UserTemplate,
} from "./platform-types.js";

export class TemplatesAPI {
  constructor(private readonly http: HttpClient) {}

  /**
   * List all templates visible to the caller — own + team + community +
   * assigned — filtered by Row-Level Security.
   */
  async list(): Promise<UserTemplate[]> {
    return this.http.get<UserTemplate[]>("/api/v1/private/user-templates");
  }

  /** List all community-visibility (global) templates. */
  async listGlobal(): Promise<UserTemplate[]> {
    return this.http.get<UserTemplate[]>("/api/v1/private/templates/global");
  }

  /** List IDs of all validated (platform-approved) templates. */
  async listValidated(): Promise<string[]> {
    return this.http.get<string[]>("/api/v1/private/templates/validated");
  }

  /** Create a new private user template. */
  async save(body: TemplateSaveBody): Promise<UserTemplate> {
    return this.http.post<UserTemplate>("/api/v1/private/user-templates", body);
  }

  /** Update name/description/category/payload of a private user template. */
  async update(templateId: string, body: TemplateUpdateBody): Promise<UserTemplate> {
    return this.http.patch<UserTemplate>(
      `/api/v1/private/user-templates/${encodeURIComponent(templateId)}`,
      body,
    );
  }

  /** Duplicate a readable template into the caller's private library. */
  async duplicate(templateId: string): Promise<UserTemplate> {
    return this.http.post<UserTemplate>(
      `/api/v1/private/user-templates/${encodeURIComponent(templateId)}/duplicate`,
    );
  }

  /** Delete (or soft-demote if shared) a template. */
  async delete(templateId: string): Promise<{ ok: boolean }> {
    return this.http.delete<{ ok: boolean }>(
      `/api/v1/private/user-templates/${encodeURIComponent(templateId)}`,
    );
  }

  /**
   * Change the visibility of a template.
   *
   * @example
   * ```ts
   * await melaya.templates.share(templateId, "community");
   * ```
   */
  async share(templateId: string, visibility: TemplateVisibility): Promise<{ ok: boolean }> {
    return this.http.put<{ ok: boolean }>(
      `/api/v1/private/user-templates/${encodeURIComponent(templateId)}/visibility`,
      { visibility },
    );
  }

  // ── Assignments ──────────────────────────────────────────────────────────────

  /** List all assignments (users / projects) for a template. */
  async listAssignments(templateId: string): Promise<TemplateAssignment[]> {
    return this.http.get<TemplateAssignment[]>(
      `/api/v1/private/user-templates/${encodeURIComponent(templateId)}/assignments`,
    );
  }

  /**
   * Assign a template to a user or a project.
   * Pass exactly one of `userId` (uuid) or `projectId` (uuid) — the server
   * accepts one XOR the other in the JSON body.
   *
   * @example
   * ```ts
   * await melaya.templates.assign(templateId, { projectId: "..." });
   * ```
   */
  async assign(
    templateId: string,
    target: { userId?: string; projectId?: string },
  ): Promise<{ ok: boolean }> {
    return this.http.post<{ ok: boolean }>(
      `/api/v1/private/user-templates/${encodeURIComponent(templateId)}/assignments`,
      this.assignmentTarget(target),
    );
  }

  /**
   * Remove a user or project assignment from a template.
   * Pass exactly one of `userId` or `projectId`. The target is sent as query
   * params — DELETE bodies are ignored by the REST bridge.
   */
  async unassign(
    templateId: string,
    target: { userId?: string; projectId?: string },
  ): Promise<{ ok: boolean }> {
    return this.http.delete<{ ok: boolean }>(
      `/api/v1/private/user-templates/${encodeURIComponent(templateId)}/assignments`,
      this.assignmentTarget(target),
    );
  }

  /** Validate the userId-XOR-projectId assignment target. */
  private assignmentTarget(target: { userId?: string; projectId?: string }):
    | { userId: string }
    | { projectId: string } {
    if (target.userId && target.projectId) {
      throw new Error("Melaya: pass `userId` or `projectId`, not both.");
    }
    if (target.userId) return { userId: target.userId };
    if (target.projectId) return { projectId: target.projectId };
    throw new Error("Melaya: pass exactly one of `userId` or `projectId`.");
  }

  /** List projects the caller is a member of (for use in the share target picker). */
  async shareTargets(): Promise<Array<{ id: string; name: string }>> {
    return this.http.get<Array<{ id: string; name: string }>>(
      "/api/v1/private/user-templates/share-targets",
    );
  }
}
