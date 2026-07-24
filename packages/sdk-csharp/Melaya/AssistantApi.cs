namespace Melaya;

/// <summary>
/// Assistant API — get and save the caller's onboarding / persona profile.
/// Maps to <c>/api/v1/private/assistant/profile</c>.
/// The profile is stored envelope-encrypted (service=<c>assistant_profile</c>)
/// and used to personalise the in-app Assistant experience.
/// </summary>
/// <example>
/// <code>
/// var profile = await m.Assistant.GetProfileAsync();
/// await m.Assistant.SetProfileAsync(new AssistantProfile { Name = "Alex", Goals = new[] { "grow my trading edge" }.ToList() });
/// </code>
/// </example>
public sealed class AssistantApi
{
    private readonly MelayaHttpClient _http;

    internal AssistantApi(MelayaHttpClient http) => _http = http;

    /// <summary>Get the caller's assistant onboarding profile.</summary>
    public async Task<AssistantProfile> GetProfileAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<AssistantProfile>("/api/v1/private/assistant/profile", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Save the caller's assistant onboarding profile.</summary>
    public async Task<BoolResult> SetProfileAsync(AssistantProfile profile, CancellationToken ct = default)
    {
        return await _http.PutAsync<BoolResult>("/api/v1/private/assistant/profile", profile, ct).ConfigureAwait(false);
    }
}
