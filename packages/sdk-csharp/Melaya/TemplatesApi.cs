namespace Melaya;

/// <summary>
/// Templates API — create, manage, and share pipeline templates.
/// Maps to <c>/api/v1/private/user-templates/*</c> and <c>/api/v1/private/templates/*</c>.
/// </summary>
/// <example>
/// <code>
/// var templates = await m.Templates.ListAsync();
/// var tmpl = await m.Templates.SaveAsync(new TemplateSaveRequest { Name = "Daily report", Payload = ... });
/// await m.Templates.ShareAsync(tmpl.Id!, "team");
/// </code>
/// </example>
public sealed class TemplatesApi
{
    private readonly MelayaHttpClient _http;

    internal TemplatesApi(MelayaHttpClient http) => _http = http;

    /// <summary>List all templates visible to the caller — own + team + community + assigned (RLS-filtered).</summary>
    public async Task<List<UserTemplate>> ListAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<List<UserTemplate>>("/api/v1/private/user-templates", ct: ct).ConfigureAwait(false);
    }

    /// <summary>List all community-visibility (global) templates.</summary>
    public async Task<List<UserTemplate>> ListGlobalAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<List<UserTemplate>>("/api/v1/private/templates/global", ct: ct).ConfigureAwait(false);
    }

    /// <summary>List IDs of all validated (platform-approved) templates.</summary>
    public async Task<List<string>> ListValidatedAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<List<string>>("/api/v1/private/templates/validated", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Create a new private user template.</summary>
    public async Task<UserTemplate> SaveAsync(TemplateSaveRequest body, CancellationToken ct = default)
    {
        return await _http.PostAsync<UserTemplate>("/api/v1/private/user-templates", body, ct).ConfigureAwait(false);
    }

    /// <summary>Update name/description/category/payload of a private user template.</summary>
    public async Task<UserTemplate> UpdateAsync(string templateId, TemplateUpdateRequest body, CancellationToken ct = default)
    {
        return await _http.PatchAsync<UserTemplate>(
            $"/api/v1/private/user-templates/{Uri.EscapeDataString(templateId)}", body, ct).ConfigureAwait(false);
    }

    /// <summary>Duplicate a readable template into the caller's private library.</summary>
    public async Task<UserTemplate> DuplicateAsync(string templateId, string? newName = null, CancellationToken ct = default)
    {
        var body = newName is not null ? (object)new { newName } : new { };
        return await _http.PostAsync<UserTemplate>(
            $"/api/v1/private/user-templates/{Uri.EscapeDataString(templateId)}/duplicate", body, ct).ConfigureAwait(false);
    }

    /// <summary>Delete (or soft-demote if shared) a template.</summary>
    public async Task<BoolResult> DeleteAsync(string templateId, CancellationToken ct = default)
    {
        return await _http.DeleteAsync<BoolResult>(
            $"/api/v1/private/user-templates/{Uri.EscapeDataString(templateId)}", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Change the visibility of a template (<c>private</c>, <c>team</c>, <c>community</c>).</summary>
    public async Task<BoolResult> ShareAsync(string templateId, string visibility, CancellationToken ct = default)
    {
        var body = new { visibility };
        return await _http.PutAsync<BoolResult>(
            $"/api/v1/private/user-templates/{Uri.EscapeDataString(templateId)}/visibility", body, ct).ConfigureAwait(false);
    }

    // ── Assignments ───────────────────────────────────────────────────────────

    /// <summary>List all assignments (users / projects) for a template.</summary>
    public async Task<List<TemplateAssignment>> ListAssignmentsAsync(string templateId, CancellationToken ct = default)
    {
        return await _http.GetAsync<List<TemplateAssignment>>(
            $"/api/v1/private/user-templates/{Uri.EscapeDataString(templateId)}/assignments", ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Assign a template to a user or project.
    /// Set exactly one of <see cref="TemplateAssignRequest.UserId"/> or
    /// <see cref="TemplateAssignRequest.ProjectId"/>; the target is posted as
    /// <c>{ userId }</c> or <c>{ projectId }</c>.
    /// </summary>
    public async Task<BoolResult> AssignAsync(string templateId, TemplateAssignRequest body, CancellationToken ct = default)
    {
        ValidateTarget(body);
        return await _http.PostAsync<BoolResult>(
            $"/api/v1/private/user-templates/{Uri.EscapeDataString(templateId)}/assignments", body, ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Remove an assignment from a template.
    /// Set exactly one of <see cref="TemplateAssignRequest.UserId"/> or
    /// <see cref="TemplateAssignRequest.ProjectId"/>; the target is sent as a
    /// <c>userId</c> / <c>projectId</c> query parameter because the server
    /// ignores DELETE request bodies.
    /// </summary>
    public async Task<BoolResult> UnassignAsync(string templateId, TemplateAssignRequest body, CancellationToken ct = default)
    {
        ValidateTarget(body);
        var query = new Dictionary<string, string?>
        {
            ["userId"]    = body.UserId,
            ["projectId"] = body.ProjectId,
        };
        return await _http.DeleteAsync<BoolResult>(
            $"/api/v1/private/user-templates/{Uri.EscapeDataString(templateId)}/assignments", query, ct).ConfigureAwait(false);
    }

    private static void ValidateTarget(TemplateAssignRequest body)
    {
        if ((body.UserId is null) == (body.ProjectId is null))
            throw new ArgumentException(
                "Melaya: set exactly one of `UserId` or `ProjectId` on TemplateAssignRequest.", nameof(body));
    }

    /// <summary>List projects the caller is a member of (for use in the share target picker).</summary>
    public async Task<List<ProjectShareTarget>> ShareTargetsAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<List<ProjectShareTarget>>("/api/v1/private/user-templates/share-targets", ct: ct).ConfigureAwait(false);
    }
}
