/**
 * Projects API — create and list agent projects.
 *
 * Maps to `/api/v1/private/projects`. Projects are the top-level namespace
 * for pipelines, connectors, and team membership.
 */
import type { HttpClient } from "./client.js";
import type { Project, ProjectCreate } from "./platform-types.js";

export class ProjectsAPI {
  constructor(private readonly http: HttpClient) {}

  /**
   * List all projects the authenticated user can access
   * (owned projects + projects they are a member of).
   */
  async list(): Promise<Project[]> {
    return this.http.get<Project[]>("/api/v1/private/projects");
  }

  /** Create a new agent project. */
  async create(body: ProjectCreate): Promise<Project> {
    return this.http.post<Project>("/api/v1/private/projects", body);
  }

  /** Rename a project. */
  async rename(oldName: string, newName: string): Promise<{ ok: boolean }> {
    return this.http.patch<{ ok: boolean }>("/api/v1/private/projects/rename", {
      oldName,
      newName,
    });
  }

  async runnerProjects(): Promise<Project[]> {
    return this.http.get<Project[]>("/api/v1/private/projects/runner");
  }
}
