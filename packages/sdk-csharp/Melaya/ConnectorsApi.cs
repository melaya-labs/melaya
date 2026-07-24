using System.Text.Json;

namespace Melaya;

/// <summary>
/// Project Connectors API — manage credentials at project scope.
/// Maps to <c>/api/v1/private/projects/:project/connectors/*</c>.
/// Project-scoped connectors let separate projects use different API keys for the same service.
/// </summary>
/// <example>
/// <code>
/// await m.Connectors.SetAsync("my-project", "openai", new ProjectConnectorSetRequest { Value = "sk-..." });
/// var services = await m.Connectors.ConnectedServicesAsync("my-project");
/// </code>
/// </example>
public sealed class ConnectorsApi
{
    private readonly MelayaHttpClient _http;

    internal ConnectorsApi(MelayaHttpClient http) => _http = http;

    /// <summary>List connected services for a project.</summary>
    public async Task<List<ConnectedService>> ConnectedServicesAsync(string project, CancellationToken ct = default)
    {
        return await _http.GetAsync<List<ConnectedService>>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/connectors/services", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Store a connector credential at project scope.</summary>
    public async Task<BoolResult> SetAsync(string project, string service, ProjectConnectorSetRequest body, CancellationToken ct = default)
    {
        return await _http.PutAsync<BoolResult>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/connectors/{Uri.EscapeDataString(service)}",
            body, ct).ConfigureAwait(false);
    }

    /// <summary>Delete a project-scoped connector credential.</summary>
    public async Task<BoolResult> DeleteAsync(string project, string service, CancellationToken ct = default)
    {
        return await _http.DeleteAsync<BoolResult>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/connectors/{Uri.EscapeDataString(service)}",
            ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Get a short-lived env-handle token for project-scoped credentials.
    /// The runner uses this token to decrypt credentials without a full session.
    /// </summary>
    public async Task<JsonElement> EnvHandleAsync(string project, CancellationToken ct = default)
    {
        return await _http.PostAsync<JsonElement>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/connectors/env-handle",
            null, ct).ConfigureAwait(false);
    }

    /// <summary>Start Google OAuth flow for a project-scoped connector.</summary>
    public async Task<JsonElement> GoogleOAuthStartAsync(string project, object? body = null, CancellationToken ct = default)
    {
        return await _http.PostAsync<JsonElement>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/connectors/google/oauth",
            body, ct).ConfigureAwait(false);
    }
}
