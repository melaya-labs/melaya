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

    /// <summary>
    /// Share the caller's OWN personal connector into a project (requires editor/owner role).
    /// </summary>
    /// <param name="project">Project name.</param>
    /// <param name="service">Connector service id.</param>
    /// <param name="googleCapabilities">
    /// For Google connectors, the capabilities to share: any of <c>gmail</c>, <c>calendar</c>,
    /// <c>drive</c>, <c>sheets</c>, <c>docs</c>, <c>search_console</c>, <c>youtube</c>,
    /// <c>google_ads</c>, <c>analytics</c>, <c>meet</c>, <c>slides</c>.
    /// </param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<BoolResult> ApplyPersonalAsync(
        string project, string service, IEnumerable<string>? googleCapabilities = null, CancellationToken ct = default)
    {
        var body = new { googleCapabilities };
        return await _http.PostAsync<BoolResult>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/connectors/{Uri.EscapeDataString(service)}/apply-personal",
            body, ct).ConfigureAwait(false);
    }

    /// <summary>List connectors shared into a project by other members.</summary>
    public async Task<JsonElement> SharedByAsync(string project, CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/connectors/shared-by", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get the project's Google connector status (linked accounts, default per capability).</summary>
    public async Task<JsonElement> GoogleStatusAsync(string project, CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/connectors/google/status", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Set the default Google account for a capability within a project.</summary>
    /// <param name="project">Project name.</param>
    /// <param name="capability">One of the Google capability strings (e.g. <c>gmail</c>, <c>drive</c>).</param>
    /// <param name="accountId">24-hex-char account id.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<BoolResult> GoogleSetDefaultAsync(string project, string capability, string accountId, CancellationToken ct = default)
    {
        var body = new { capability, accountId };
        return await _http.PutAsync<BoolResult>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/connectors/google/default", body, ct).ConfigureAwait(false);
    }

    /// <summary>Disconnect a Google account (optionally scoped to one capability) from a project.</summary>
    /// <param name="project">Project name.</param>
    /// <param name="accountId">24-hex-char account id.</param>
    /// <param name="capability">Optional capability to revoke; omit to disconnect the whole account.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<BoolResult> GoogleDisconnectAsync(string project, string accountId, string? capability = null, CancellationToken ct = default)
    {
        var body = new { accountId, capability };
        return await _http.DeleteWithBodyAsync<BoolResult>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/connectors/google/access", body, ct).ConfigureAwait(false);
    }

    // ── Several accounts per project connector (owner only for writes) ───────

    /// <summary>Accounts connected to one project connector (labels and ids only, never credential values).</summary>
    /// <param name="project">Project name.</param>
    /// <param name="service">Connector service id.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<List<ConnectorAccount>> AccountsAsync(string project, string service, CancellationToken ct = default)
    {
        return await _http.GetAsync<List<ConnectorAccount>>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/connectors/{Uri.EscapeDataString(service)}/accounts",
            ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Add another account to a project connector (owner only; the connection is tested first).
    /// Returns the updated list.
    /// </summary>
    /// <param name="project">Project name.</param>
    /// <param name="service">Connector service id.</param>
    /// <param name="fields">The connector's credential fields.</param>
    /// <param name="label">Optional label for the new account.</param>
    /// <param name="currentLabel">Names the existing single connection when it is adopted as the first account.</param>
    /// <param name="makeDefault">Make the new account the default one.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<List<ConnectorAccount>> AddAccountAsync(
        string project,
        string service,
        IDictionary<string, string> fields,
        string? label = null,
        string? currentLabel = null,
        bool? makeDefault = null,
        CancellationToken ct = default)
    {
        var body = new { label, fields, currentLabel, makeDefault };
        return await _http.PostAsync<List<ConnectorAccount>>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/connectors/{Uri.EscapeDataString(service)}/accounts",
            body, ct).ConfigureAwait(false);
    }

    /// <summary>Choose which account the project connector uses (owner only). Returns the updated list.</summary>
    /// <param name="project">Project name.</param>
    /// <param name="service">Connector service id.</param>
    /// <param name="accountId">Account id, as returned by <see cref="AccountsAsync"/>.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<List<ConnectorAccount>> SetDefaultAccountAsync(
        string project, string service, string accountId, CancellationToken ct = default)
    {
        var body = new { accountId };
        return await _http.PutAsync<List<ConnectorAccount>>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/connectors/{Uri.EscapeDataString(service)}/accounts/default",
            body, ct).ConfigureAwait(false);
    }

    /// <summary>Rename one account of a project connector (owner only, max 80 chars). Returns the updated list.</summary>
    /// <param name="project">Project name.</param>
    /// <param name="service">Connector service id.</param>
    /// <param name="accountId">Account id.</param>
    /// <param name="label">New label.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<List<ConnectorAccount>> RenameAccountAsync(
        string project, string service, string accountId, string label, CancellationToken ct = default)
    {
        var body = new { label };
        return await _http.PutAsync<List<ConnectorAccount>>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/connectors/{Uri.EscapeDataString(service)}/accounts/{Uri.EscapeDataString(accountId)}",
            body, ct).ConfigureAwait(false);
    }

    /// <summary>Remove one account from a project connector (owner only). Returns the remaining list.</summary>
    /// <param name="project">Project name.</param>
    /// <param name="service">Connector service id.</param>
    /// <param name="accountId">Account id.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<List<ConnectorAccount>> RemoveAccountAsync(
        string project, string service, string accountId, CancellationToken ct = default)
    {
        return await _http.DeleteAsync<List<ConnectorAccount>>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/connectors/{Uri.EscapeDataString(service)}/accounts/{Uri.EscapeDataString(accountId)}",
            ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Start a database connectivity probe from the user's own runner, at project scope.
    /// </summary>
    /// <param name="project">Project name.</param>
    /// <param name="service">Database service id.</param>
    /// <param name="credentials">Connection credentials to probe with (host, port, user, etc.).</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<DbTestStartResult> DbTestStartAsync(
        string project, string service, Dictionary<string, string>? credentials = null, CancellationToken ct = default)
    {
        var body = new { service, credentials };
        return await _http.PostAsync<DbTestStartResult>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/connectors/db-test", body, ct).ConfigureAwait(false);
    }

    /// <summary>Poll a project-scoped database connectivity probe by session id.</summary>
    public async Task<JsonElement> DbTestStatusAsync(string project, string sessionId, CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>(
            $"/api/v1/private/projects/{Uri.EscapeDataString(project)}/connectors/db-test/{Uri.EscapeDataString(sessionId)}",
            ct: ct).ConfigureAwait(false);
    }
}
