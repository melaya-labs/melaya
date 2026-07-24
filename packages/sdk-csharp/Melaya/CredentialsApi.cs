using System.Text.Json;

namespace Melaya;

/// <summary>
/// Credentials API — store, retrieve, test, and delete secrets and third-party
/// service connections at user scope.
/// Maps to <c>/api/v1/private/credentials/*</c>. Credentials are envelope-encrypted at rest.
/// Use <see cref="ConnectorsApi"/> for project-scoped connector credentials.
/// </summary>
/// <example>
/// <code>
/// await m.Credentials.SetAsync("openai", new CredentialSetRequest { Value = "sk-..." });
/// var ok = await m.Credentials.TestAsync("openai");
/// </code>
/// </example>
public sealed class CredentialsApi
{
    private readonly MelayaHttpClient _http;

    internal CredentialsApi(MelayaHttpClient http) => _http = http;

    /// <summary>List all stored credentials (services, OAuth connections, env handles).</summary>
    public async Task<List<Credential>> ListAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<List<Credential>>("/api/v1/private/credentials", ct: ct).ConfigureAwait(false);
    }

    /// <summary>List connected third-party services.</summary>
    public async Task<List<ConnectedService>> ConnectedServicesAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<List<ConnectedService>>("/api/v1/private/credentials/services", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get a stored credential value by service name. Pass <paramref name="key"/> to retrieve a named sub-key.</summary>
    public async Task<Credential> GetAsync(string service, string? key = null, CancellationToken ct = default)
    {
        var q = key is not null ? new Dictionary<string, string?> { ["key"] = key } : null;
        return await _http.GetAsync<Credential>(
            $"/api/v1/private/credentials/{Uri.EscapeDataString(service)}", q, ct).ConfigureAwait(false);
    }

    /// <summary>Store or update a credential (envelope-encrypted at rest).</summary>
    public async Task<BoolResult> SetAsync(string service, CredentialSetRequest body, CancellationToken ct = default)
    {
        return await _http.PutAsync<BoolResult>(
            $"/api/v1/private/credentials/{Uri.EscapeDataString(service)}", body, ct).ConfigureAwait(false);
    }

    /// <summary>Delete a stored credential by service name.</summary>
    public async Task<BoolResult> DeleteAsync(string service, CancellationToken ct = default)
    {
        return await _http.DeleteAsync<BoolResult>(
            $"/api/v1/private/credentials/{Uri.EscapeDataString(service)}", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Test a stored credential (e.g. validate an API key against its target service).</summary>
    public async Task<CredentialTestResult> TestAsync(string service, CancellationToken ct = default)
    {
        return await _http.PostAsync<CredentialTestResult>(
            $"/api/v1/private/credentials/{Uri.EscapeDataString(service)}/test", null, ct).ConfigureAwait(false);
    }

    // ── Operator profile ──────────────────────────────────────────────────────

    /// <summary>Get the operator profile (persona config injected into agent context).</summary>
    public async Task<OperatorProfile> GetOperatorProfileAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<OperatorProfile>("/api/v1/private/credentials/operator-profile", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Save the operator profile.</summary>
    public async Task<BoolResult> SetOperatorProfileAsync(OperatorProfile profile, CancellationToken ct = default)
    {
        return await _http.PutAsync<BoolResult>("/api/v1/private/credentials/operator-profile", profile, ct).ConfigureAwait(false);
    }

    // ── AI models ─────────────────────────────────────────────────────────────

    /// <summary>
    /// List available AI models across all configured providers.
    /// Collapses 19+ provider fan-out into a parameterized query.
    /// </summary>
    public async Task<List<AiModel>> ListModelsAsync(string? provider = null, string? capability = null, CancellationToken ct = default)
    {
        var q = Q(("provider", provider), ("capability", capability));
        return await _http.GetAsync<List<AiModel>>("/api/v1/private/credentials/models", q, ct).ConfigureAwait(false);
    }

    // ── RAG ───────────────────────────────────────────────────────────────────

    /// <summary>Start a RAG document ingestion job.</summary>
    public async Task<JsonElement> RagIngestStartAsync(object body, CancellationToken ct = default)
    {
        return await _http.PostAsync<JsonElement>("/api/v1/private/rag/ingest", body, ct).ConfigureAwait(false);
    }

    /// <summary>Poll RAG ingestion job status.</summary>
    public async Task<JsonElement> RagIngestStatusAsync(string sessionId, CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>(
            $"/api/v1/private/rag/ingest/{Uri.EscapeDataString(sessionId)}", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Start a RAG retrieval query.</summary>
    public async Task<JsonElement> RagRetrieveStartAsync(object body, CancellationToken ct = default)
    {
        return await _http.PostAsync<JsonElement>("/api/v1/private/rag/retrieve", body, ct).ConfigureAwait(false);
    }

    /// <summary>Poll RAG retrieval result.</summary>
    public async Task<JsonElement> RagRetrieveStatusAsync(string sessionId, CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>(
            $"/api/v1/private/rag/retrieve/{Uri.EscapeDataString(sessionId)}", ct: ct).ConfigureAwait(false);
    }

    // ── Folder picker ─────────────────────────────────────────────────────────

    /// <summary>Initiate native folder picker for file ingestion.</summary>
    public async Task<JsonElement> PickFolderStartAsync(CancellationToken ct = default)
    {
        return await _http.PostAsync<JsonElement>("/api/v1/private/rag/pick-folder", null, ct).ConfigureAwait(false);
    }

    /// <summary>Poll folder picker result.</summary>
    public async Task<JsonElement> PickFolderStatusAsync(string sessionId, CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>(
            $"/api/v1/private/rag/pick-folder/{Uri.EscapeDataString(sessionId)}", ct: ct).ConfigureAwait(false);
    }

    // ── LinkedIn OAuth ────────────────────────────────────────────────────────

    /// <summary>Start LinkedIn OAuth flow.</summary>
    public async Task<JsonElement> LinkedInConnectStartAsync(CancellationToken ct = default)
    {
        return await _http.PostAsync<JsonElement>("/api/v1/private/credentials/linkedin/connect", null, ct).ConfigureAwait(false);
    }

    /// <summary>Cancel an in-progress LinkedIn OAuth flow.</summary>
    public async Task<BoolResult> LinkedInConnectCancelAsync(CancellationToken ct = default)
    {
        return await _http.DeleteAsync<BoolResult>("/api/v1/private/credentials/linkedin/connect", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Poll LinkedIn OAuth connection status.</summary>
    public async Task<JsonElement> LinkedInConnectStatusAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/private/credentials/linkedin/connect/status", ct: ct).ConfigureAwait(false);
    }

    // ── Luma OAuth ───────────────────────────────────────────────────────────

    /// <summary>Start Luma OAuth flow.</summary>
    public async Task<JsonElement> LumaConnectStartAsync(CancellationToken ct = default)
    {
        return await _http.PostAsync<JsonElement>("/api/v1/private/credentials/luma/connect", null, ct).ConfigureAwait(false);
    }

    /// <summary>Poll Luma OAuth connection status.</summary>
    public async Task<JsonElement> LumaConnectStatusAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/private/credentials/luma/connect/status", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Cancel an in-progress Luma OAuth flow.</summary>
    public async Task<BoolResult> LumaConnectCancelAsync(CancellationToken ct = default)
    {
        return await _http.DeleteAsync<BoolResult>("/api/v1/private/credentials/luma/connect", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get the Luma event registration form schema for a given event.</summary>
    public async Task<JsonElement> LumaRegistrationSchemaAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/private/credentials/luma/schema", ct: ct).ConfigureAwait(false);
    }

    // ── Google OAuth ──────────────────────────────────────────────────────────

    /// <summary>Start Google OAuth flow for credential storage.</summary>
    public async Task<JsonElement> GoogleOAuthStartAsync(object? body = null, CancellationToken ct = default)
    {
        return await _http.PostAsync<JsonElement>("/api/v1/private/credentials/google/oauth", body, ct).ConfigureAwait(false);
    }

    // ── CLI auth ──────────────────────────────────────────────────────────────

    /// <summary>Start CLI authentication flow (device-code style).</summary>
    public async Task<JsonElement> CliAuthStartAsync(CancellationToken ct = default)
    {
        return await _http.PostAsync<JsonElement>("/api/v1/private/credentials/cli-auth", null, ct).ConfigureAwait(false);
    }

    // ── NotebookLM ────────────────────────────────────────────────────────────

    /// <summary>Store NotebookLM credentials.</summary>
    public async Task<BoolResult> NotebookLMLoginAsync(object body, CancellationToken ct = default)
    {
        return await _http.PostAsync<BoolResult>("/api/v1/private/credentials/notebooklm/login", body, ct).ConfigureAwait(false);
    }

    /// <summary>Check NotebookLM connection status.</summary>
    public async Task<JsonElement> NotebookLMStatusAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/private/credentials/notebooklm/status", ct: ct).ConfigureAwait(false);
    }

    // ── Telegram auth ─────────────────────────────────────────────────────────

    /// <summary>Start Telegram user auth (phone number step).</summary>
    public async Task<JsonElement> TelegramAuthStartAsync(string phoneNumber, CancellationToken ct = default)
    {
        var body = new { phoneNumber };
        return await _http.PostAsync<JsonElement>("/api/v1/private/credentials/telegram/auth", body, ct).ConfigureAwait(false);
    }

    /// <summary>Submit Telegram SMS verification code.</summary>
    public async Task<JsonElement> TelegramAuthCodeAsync(string code, CancellationToken ct = default)
    {
        var body = new { code };
        return await _http.PostAsync<JsonElement>("/api/v1/private/credentials/telegram/auth/code", body, ct).ConfigureAwait(false);
    }

    /// <summary>Submit Telegram 2FA password.</summary>
    public async Task<JsonElement> TelegramAuth2FaAsync(string password, CancellationToken ct = default)
    {
        var body = new { password };
        return await _http.PostAsync<JsonElement>("/api/v1/private/credentials/telegram/auth/2fa", body, ct).ConfigureAwait(false);
    }

    // ── Melaya accounts ───────────────────────────────────────────────────────

    /// <summary>List Melaya sub-accounts available to the caller.</summary>
    public async Task<JsonElement> MelayaAccountsAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/private/credentials/melaya-accounts", ct: ct).ConfigureAwait(false);
    }

    private static Dictionary<string, string?> Q(params (string Key, string? Value)[] pairs)
    {
        var d = new Dictionary<string, string?>(pairs.Length);
        foreach (var (k, v) in pairs)
            if (v is not null) d[k] = v;
        return d;
    }
}
