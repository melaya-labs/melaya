/**
 * Team API — manage project team membership, roles, and invitations.
 *
 * Maps to `/api/v1/private/projects/:projectId/members/*` and
 * `/api/v1/private/team/*`.
 *
 * @example
 * ```ts
 * const members = await melaya.team.listMembers("proj_abc");
 * await melaya.team.invite("proj_abc", "alice");
 *
 * const { inviteLink } = await melaya.team.createInviteLink("proj_abc");
 * // share inviteLink with your team
 * ```
 */
import type { HttpClient } from "./client.js";
import type { TeamInviteResult, TeamMember, TeamRole } from "./platform-types.js";

export class TeamAPI {
  constructor(private readonly http: HttpClient) {}

  /** List members of a project team. */
  async listMembers(projectId: string): Promise<TeamMember[]> {
    return this.http.get<TeamMember[]>(
      `/api/v1/private/projects/${encodeURIComponent(projectId)}/members`,
    );
  }

  /** Invite a user to a project team by username. */
  async invite(projectId: string, username: string): Promise<{ ok: boolean }> {
    return this.http.post<{ ok: boolean }>(
      `/api/v1/private/projects/${encodeURIComponent(projectId)}/members/invite`,
      { username },
    );
  }

  /**
   * Create a shareable invite link for a project.
   * Returns the URL to send to new team members.
   */
  async createInviteLink(projectId: string): Promise<TeamInviteResult> {
    return this.http.post<TeamInviteResult>(
      `/api/v1/private/projects/${encodeURIComponent(projectId)}/invite-link`,
    );
  }

  /**
   * Accept a project invite using the token from an invite link.
   * Call after the user navigates to the invite URL.
   */
  async acceptInvite(token: string): Promise<{ ok: boolean }> {
    return this.http.post<{ ok: boolean }>("/api/v1/private/team/invite/accept", { token });
  }

  /** Update a team member's role in a project. */
  async updateMemberRole(
    projectId: string,
    userId: string,
    role: TeamRole,
  ): Promise<{ ok: boolean }> {
    return this.http.patch<{ ok: boolean }>(
      `/api/v1/private/projects/${encodeURIComponent(projectId)}/members/${encodeURIComponent(userId)}`,
      { role },
    );
  }

  /** Remove a member from a project team. */
  async removeMember(projectId: string, userId: string): Promise<{ ok: boolean }> {
    return this.http.delete<{ ok: boolean }>(
      `/api/v1/private/projects/${encodeURIComponent(projectId)}/members/${encodeURIComponent(userId)}`,
    );
  }

  // ── Pipeline visibility ──────────────────────────────────────────────────────

  /** Get visibility settings for a pipeline within a project. */
  async getPipelineVisibility(
    projectId: string,
    pipelineId: string,
  ): Promise<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(
      `/api/v1/private/projects/${encodeURIComponent(projectId)}/pipelines/${encodeURIComponent(pipelineId)}/visibility`,
    );
  }

  /** Set pipeline visibility within a project. */
  async setPipelineVisibility(
    projectId: string,
    pipelineId: string,
    body: Record<string, unknown>,
  ): Promise<{ ok: boolean }> {
    return this.http.put<{ ok: boolean }>(
      `/api/v1/private/projects/${encodeURIComponent(projectId)}/pipelines/${encodeURIComponent(pipelineId)}/visibility`,
      body,
    );
  }
}
