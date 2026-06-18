using System.Text.Json;

namespace Melaya;

/// <summary>
/// Authenticated account reads: connected keys, tier limits, usage.
/// Maps to <c>https://api.melaya.org/api/v1/private/*</c>.
/// </summary>
public sealed class AccountApi
{
    private readonly MelayaHttpClient _http;

    internal AccountApi(MelayaHttpClient http) => _http = http;

    /// <summary>
    /// The exchange API keys connected to your account.
    /// <c>ApiKey</c> is masked; use <c>ApiKeyId</c> when launching strategies.
    /// </summary>
    public async Task<List<JsonElement>> KeysAsync(CancellationToken ct = default)
    {
        var r = await _http.GetAsync<KeysEnvelope>("/api/v1/private/keys", ct: ct).ConfigureAwait(false);
        return r.Keys ?? [];
    }

    /// <summary>Tier, plan limits, and live usage counters.</summary>
    public async Task<JsonElement> UsageAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/private/usage", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Status of your platform API key (tier, max concurrent connections).</summary>
    public async Task<JsonElement> ApiKeyStatusAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/private/api-key", ct: ct).ConfigureAwait(false);
    }
}
