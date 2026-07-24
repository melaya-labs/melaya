using System.Text.Json;

namespace Melaya;

/// <summary>
/// Projects API — create and list agent projects.
/// Maps to <c>/api/v1/private/projects</c>.
/// </summary>
public sealed class ProjectsApi
{
    private readonly MelayaHttpClient _http;

    internal ProjectsApi(MelayaHttpClient http) => _http = http;

    /// <summary>List all projects the authenticated user can access (owned + team member).</summary>
    public async Task<List<Project>> ListAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<List<Project>>("/api/v1/private/projects", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Create a new agent project.</summary>
    public async Task<Project> CreateAsync(ProjectCreateRequest body, CancellationToken ct = default)
    {
        return await _http.PostAsync<Project>("/api/v1/private/projects", body, ct).ConfigureAwait(false);
    }

    /// <summary>Rename a project.</summary>
    public async Task<BoolResult> RenameAsync(string oldName, string newName, CancellationToken ct = default)
    {
        var body = new { oldName, newName };
        return await _http.PatchAsync<BoolResult>("/api/v1/private/projects/rename", body, ct).ConfigureAwait(false);
    }

    /// <summary>Get projects list (runner-facing endpoint).</summary>
    public async Task<JsonElement> RunnerProjectsAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/private/projects/runner", ct: ct).ConfigureAwait(false);
    }
}
