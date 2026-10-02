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

    /// <summary>
    /// Generate a new platform API key, replacing the current one at once. The new key is
    /// returned ONCE.
    /// <para>
    /// <b>Careful:</b> if this client was built with the key being rotated, every later call
    /// of this client fails until you build a new <see cref="MelayaClient"/> with the returned key.
    /// </para>
    /// </summary>
    public async Task<ApiKeyRotateResult> RotateApiKeyAsync(CancellationToken ct = default)
    {
        return await _http.PostAsync<ApiKeyRotateResult>("/api/v1/private/api-key", new { }, ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Revoke the platform API key.
    /// <para>
    /// <b>Careful:</b> if this client was built with that key, it stops working immediately.
    /// </para>
    /// </summary>
    public async Task<BoolResult> RevokeApiKeyAsync(CancellationToken ct = default)
    {
        return await _http.DeleteAsync<BoolResult>("/api/v1/private/api-key", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Request counts of your platform API key (current key, merged with your account totals).</summary>
    public async Task<JsonElement> ApiKeyUsageAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/private/api-key/usage", ct: ct).ConfigureAwait(false);
    }
}
