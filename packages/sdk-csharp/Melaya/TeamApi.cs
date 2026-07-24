using System.Text.Json;

namespace Melaya;

/// <summary>
/// Team API — manage project team membership, roles, and invitations.
/// Maps to <c>/api/v1/private/projects/:project/members/*</c> and <c>/api/v1/private/team/*</c>.
/// </summary>
/// <example>
/// <code>
/// var members = await m.Team.ListMembersAsync("my-project");
/// await m.Team.InviteAsync("my-project", "alice");
/// var link = await m.Team.CreateInviteLinkAsync("my-project");
/// // share link.InviteLink
/// </code>
/// </example>
public sealed class TeamApi
{
    private readonly MelayaHttpClient _http;

    internal TeamApi(MelayaHttpClient http) => _http = http;

    /// <summary>List members of a project team.</summary>
    public async Task<List<TeamMember>> ListMembersAsync(string project, CancellationToken ct = default)
    {
        return await _http.GetAsync<List<TeamMember>>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/members", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Invite a user to a project team by username.</summary>
    public async Task<BoolResult> InviteAsync(string project, string username, CancellationToken ct = default)
    {
        var body = new { username };
        return await _http.PostAsync<BoolResult>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/members/invite", body, ct).ConfigureAwait(false);
    }

    /// <summary>Create a shareable invite link for a project.</summary>
    public async Task<TeamInviteResult> CreateInviteLinkAsync(string project, CancellationToken ct = default)
    {
        return await _http.PostAsync<TeamInviteResult>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/invite-link", null, ct).ConfigureAwait(false);
    }

    /// <summary>Accept a project invite using the token from an invite link.</summary>
    public async Task<BoolResult> AcceptInviteAsync(string token, CancellationToken ct = default)
    {
        var body = new { token };
        return await _http.PostAsync<BoolResult>("/api/v1/private/team/invite/accept", body, ct).ConfigureAwait(false);
    }

    /// <summary>Update a team member's role in a project.</summary>
    public async Task<BoolResult> UpdateMemberRoleAsync(string project, string userId, string role, CancellationToken ct = default)
    {
        var body = new { role };
        return await _http.PatchAsync<BoolResult>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/members/{Uri.EscapeDataString(userId)}",
            body, ct).ConfigureAwait(false);
    }

    /// <summary>Remove a member from a project team.</summary>
    public async Task<BoolResult> RemoveMemberAsync(string project, string userId, CancellationToken ct = default)
    {
        return await _http.DeleteAsync<BoolResult>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/members/{Uri.EscapeDataString(userId)}",
            ct: ct).ConfigureAwait(false);
    }

    // ── Pipeline visibility ───────────────────────────────────────────────────

    /// <summary>Get visibility settings for a pipeline within a project.</summary>
    public async Task<JsonElement> GetPipelineVisibilityAsync(string project, string pipeline, CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/pipelines/{Uri.EscapeDataString(pipeline)}/visibility",
            ct: ct).ConfigureAwait(false);
    }

    /// <summary>Set pipeline visibility within a project.</summary>
    public async Task<BoolResult> SetPipelineVisibilityAsync(string project, string pipeline, object body, CancellationToken ct = default)
    {
        return await _http.PutAsync<BoolResult>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/pipelines/{Uri.EscapeDataString(pipeline)}/visibility",
            body, ct).ConfigureAwait(false);
    }
}
