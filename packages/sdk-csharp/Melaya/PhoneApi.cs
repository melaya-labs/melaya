using System.Text.Json;

namespace Melaya;

/// <summary>
/// Phone API — pair and control Android devices connected to the Melaya runner.
/// Maps to <c>/api/v1/private/phone/*</c>.
/// </summary>
/// <example>
/// <code>
/// var pair = await m.Phone.PairAsync();
/// // Show pair.Code to the user — enter it in the Melaya APK on the phone.
///
/// var devices = await m.Phone.ListDevicesAsync();
/// var tree = await m.Phone.ScreenTreeAsync();
/// </code>
/// </example>
public sealed class PhoneApi
{
    private readonly MelayaHttpClient _http;

    internal PhoneApi(MelayaHttpClient http) => _http = http;

    /// <summary>Start phone device pairing — generates a pairing code to enter on the Melaya APK.</summary>
    public async Task<PhonePairResult> PairAsync(CancellationToken ct = default)
    {
        return await _http.PostAsync<PhonePairResult>("/api/v1/private/phone/pair", null, ct).ConfigureAwait(false);
    }

    /// <summary>List all paired phone devices for the authenticated user.</summary>
    public async Task<List<PhoneDevice>> ListDevicesAsync(CancellationToken ct = default)
    {
        var response = await _http.GetAsync<PhoneDevicesResult>(
            "/api/v1/private/phone/devices", ct: ct).ConfigureAwait(false);
        return response.Devices ?? [];
    }

    /// <summary>Revoke a paired phone device by ID.</summary>
    public async Task<BoolResult> RevokeDeviceAsync(string deviceId, CancellationToken ct = default)
    {
        return await _http.DeleteAsync<BoolResult>(
            $"/api/v1/private/phone/devices/{Uri.EscapeDataString(deviceId)}", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get the current accessibility tree from the paired phone's screen.</summary>
    public async Task<JsonElement> ScreenTreeAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/private/phone/screen-tree", ct: ct).ConfigureAwait(false);
    }

    /// <summary>List installed apps on the paired phone.</summary>
    public async Task<List<PhoneApp>> ListAppsAsync(CancellationToken ct = default)
    {
        var response = await _http.GetAsync<PhoneAppsResult>(
            "/api/v1/private/phone/apps", ct: ct).ConfigureAwait(false);
        return response.Result?.Apps ?? [];
    }

    /// <summary>Set the allowlist of apps that agents are permitted to interact with. Pass package names.</summary>
    public async Task<BoolResult> SetAllowedAppsAsync(IEnumerable<string> packageNames, CancellationToken ct = default)
    {
        var body = new { apps = packageNames.Select(package => new { package }) };
        return await _http.PutAsync<BoolResult>("/api/v1/private/phone/apps/allowed", body, ct).ConfigureAwait(false);
    }

    /// <summary>Register the currently active pipeline run on the phone (used by agents).</summary>
    public async Task<BoolResult> RegisterActiveRunAsync(string runId, CancellationToken ct = default)
    {
        var body = new { runId };
        return await _http.PostAsync<BoolResult>("/api/v1/private/phone/active-run", body, ct).ConfigureAwait(false);
    }
}
