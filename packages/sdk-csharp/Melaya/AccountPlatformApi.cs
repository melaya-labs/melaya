using System.Text.Json;

namespace Melaya;

/// <summary>
/// Account Platform API — user data export, credit balances, API key management, and profile updates.
/// Maps to <c>/api/v1/private/accounts/*</c> and <c>/api/v1/private/keys/*</c>.
/// </summary>
public sealed class AccountPlatformApi
{
    private readonly MelayaHttpClient _http;

    internal AccountPlatformApi(MelayaHttpClient http) => _http = http;

    /// <summary>GDPR Art 15/20 data export; returns JSON blob of all user data.</summary>
    public async Task<JsonElement> ExportMyDataAsync(CancellationToken ct = default)
    {
        return await _http.PostAsync<JsonElement>("/api/v1/private/accounts/export", null, ct).ConfigureAwait(false);
    }

    /// <summary>Update user display name, avatar, or settings.</summary>
    public async Task<BoolResult> UpdateProfileAsync(object body, CancellationToken ct = default)
    {
        return await _http.PatchAsync<BoolResult>("/api/v1/private/accounts/profile", body, ct).ConfigureAwait(false);
    }

    /// <summary>Return current credit balance and transaction history.</summary>
    public async Task<CreditBalance> CreditsAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<CreditBalance>("/api/v1/private/accounts/credits", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Return AI/LLM credit balance.</summary>
    public async Task<CreditBalance> AiCreditsAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<CreditBalance>("/api/v1/private/accounts/credits/ai", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Return portfolio-ideas feature credit balance.</summary>
    public async Task<CreditBalance> PortfolioIdeasCreditsAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<CreditBalance>("/api/v1/private/accounts/credits/portfolio-ideas", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Return risk-monitoring feature credit balance.</summary>
    public async Task<CreditBalance> RiskMonitoringCreditsAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<CreditBalance>("/api/v1/private/accounts/credits/risk-monitoring", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Remove a stored CEX API key by ID.</summary>
    public async Task<BoolResult> RemoveKeyAsync(string keyId, CancellationToken ct = default)
    {
        return await _http.DeleteAsync<BoolResult>(
            $"/api/v1/private/keys/{Uri.EscapeDataString(keyId)}", ct: ct).ConfigureAwait(false);
    }
}
