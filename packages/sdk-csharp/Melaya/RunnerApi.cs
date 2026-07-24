namespace Melaya;

/// <summary>
/// Runner API — mint, list, and revoke runner tokens.
/// Maps to <c>/api/v1/private/runner/tokens</c>.
/// Runner tokens (<c>mel_run_</c> prefix) authenticate the runner CLI process.
/// </summary>
/// <example>
/// <code>
/// var result = await m.Runner.CreateTokenAsync(new RunnerTokenCreateRequest { Label = "prod-server-1" });
/// // Store result.Token securely — it is shown only once.
///
/// var tokens = await m.Runner.ListTokensAsync();
/// await m.Runner.RevokeTokenAsync(tokens[0].Id!);
/// </code>
/// </example>
public sealed class RunnerApi
{
    private readonly MelayaHttpClient _http;

    internal RunnerApi(MelayaHttpClient http) => _http = http;

    /// <summary>
    /// Mint a new runner token.
    /// The plaintext token is returned only in this response — store it securely.
    /// </summary>
    public async Task<RunnerTokenCreateResult> CreateTokenAsync(RunnerTokenCreateRequest? body = null, CancellationToken ct = default)
    {
        return await _http.PostAsync<RunnerTokenCreateResult>("/api/v1/private/runner/tokens", body, ct).ConfigureAwait(false);
    }

    /// <summary>List all runner tokens for the caller (masked, with last_seen).</summary>
    public async Task<List<RunnerToken>> ListTokensAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<List<RunnerToken>>("/api/v1/private/runner/tokens", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Revoke a runner token by ID.</summary>
    public async Task<BoolResult> RevokeTokenAsync(string tokenId, CancellationToken ct = default)
    {
        return await _http.DeleteAsync<BoolResult>(
            $"/api/v1/private/runner/tokens/{Uri.EscapeDataString(tokenId)}", ct: ct).ConfigureAwait(false);
    }
}
