using System.Text.Json;

namespace Melaya;

/// <summary>
/// Server / platform utility endpoints.
/// Maps to standalone public routes: <c>/api/v1/version</c>.
/// </summary>
public sealed class ServerApi
{
    private readonly MelayaHttpClient _http;

    internal ServerApi(MelayaHttpClient http) => _http = http;

    /// <summary>Get the current server version string. Public endpoint — no auth required.</summary>
    public async Task<JsonElement> VersionAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/version", ct: ct).ConfigureAwait(false);
    }
}
