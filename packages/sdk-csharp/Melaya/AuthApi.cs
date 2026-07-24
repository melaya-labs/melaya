using System.Text.Json;

namespace Melaya;

/// <summary>
/// Auth API — login, register, MFA, password management, and session utilities.
/// Maps to <c>/api/v1/private/auth/*</c> (authenticated) and public counterparts.
/// </summary>
public sealed class AuthApi
{
    private readonly MelayaHttpClient _http;

    internal AuthApi(MelayaHttpClient http) => _http = http;

    // ── Public endpoints ──────────────────────────────────────────────────────

    /// <summary>
    /// Password + username login. Returns a session JWT and optional MFA challenge token.
    /// On MFA-enabled accounts, use the returned <c>ChallengeToken</c> with <see cref="VerifyMfaAsync"/>.
    /// </summary>
    public async Task<LoginResult> LoginAsync(string username, string password, CancellationToken ct = default)
    {
        var body = new { username, password };
        return await _http.PostAsync<LoginResult>("/api/v1/private/auth/login", body, ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Resolve an MFA challenge after login. Exchange the challenge token + TOTP/recovery code
    /// for a full session JWT.
    /// </summary>
    public async Task<LoginResult> VerifyMfaAsync(string challengeToken, string code, CancellationToken ct = default)
    {
        var body = new { challengeToken, code };
        return await _http.PostAsync<LoginResult>("/api/v1/private/auth/mfa/verify", body, ct).ConfigureAwait(false);
    }

    /// <summary>Create a new user account. Sends email verification.</summary>
    public async Task<BoolResult> RegisterAsync(string username, string email, string password, CancellationToken ct = default)
    {
        var body = new { username, email, password };
        return await _http.PostAsync<BoolResult>("/api/v1/private/auth/register", body, ct).ConfigureAwait(false);
    }

    /// <summary>Confirm email address from signup link token.</summary>
    public async Task<BoolResult> VerifySignupAsync(string token, CancellationToken ct = default)
    {
        var body = new { token };
        return await _http.PostAsync<BoolResult>("/api/v1/private/auth/verify-signup", body, ct).ConfigureAwait(false);
    }

    /// <summary>Re-send email verification message.</summary>
    public async Task<BoolResult> ResendVerificationAsync(string email, CancellationToken ct = default)
    {
        var body = new { email };
        return await _http.PostAsync<BoolResult>("/api/v1/private/auth/resend-verification", body, ct).ConfigureAwait(false);
    }

    /// <summary>Initiate password reset flow; sends email with reset token.</summary>
    public async Task<BoolResult> ForgotPasswordAsync(string email, CancellationToken ct = default)
    {
        var body = new { email };
        return await _http.PostAsync<BoolResult>("/api/v1/private/auth/forgot-password", body, ct).ConfigureAwait(false);
    }

    /// <summary>Complete password reset using token from email.</summary>
    public async Task<BoolResult> ResetPasswordAsync(string token, string newPassword, CancellationToken ct = default)
    {
        var body = new { token, newPassword };
        return await _http.PostAsync<BoolResult>("/api/v1/private/auth/reset-password", body, ct).ConfigureAwait(false);
    }

    // ── Authenticated endpoints ───────────────────────────────────────────────

    /// <summary>Return current authenticated user profile (id, username, email, tier, role, capabilities).</summary>
    public async Task<UserProfile> MeAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<UserProfile>("/api/v1/private/auth/me", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Lightweight session validity check; returns <c>ok: true</c> if the token is valid.</summary>
    public async Task<BoolResult> CheckAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<BoolResult>("/api/v1/private/auth/check", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Change password (requires current + new; re-hashes and invalidates other sessions).</summary>
    public async Task<BoolResult> ChangePasswordAsync(string currentPassword, string newPassword, CancellationToken ct = default)
    {
        var body = new { currentPassword, newPassword };
        return await _http.PostAsync<BoolResult>("/api/v1/private/auth/change-password", body, ct).ConfigureAwait(false);
    }

    /// <summary>Create a short-lived handoff token for mobile app deep-link auth.</summary>
    public async Task<JsonElement> MobileHandoffAsync(CancellationToken ct = default)
    {
        return await _http.PostAsync<JsonElement>("/api/v1/private/auth/mobile-handoff", null, ct).ConfigureAwait(false);
    }

    /// <summary>Return caller's permission flags (capabilities, tier, feature gates).</summary>
    public async Task<JsonElement> PermissionsAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>("/api/v1/private/auth/permissions", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Rotate session JWT (sliding expiry); returns new token.</summary>
    public async Task<LoginResult> RefreshAsync(CancellationToken ct = default)
    {
        return await _http.PostAsync<LoginResult>("/api/v1/private/auth/refresh", null, ct).ConfigureAwait(false);
    }
}

/// <summary>
/// MFA API — enroll and manage TOTP multi-factor authentication.
/// Maps to <c>/api/v1/private/mfa/*</c>.
/// </summary>
public sealed class MfaApi
{
    private readonly MelayaHttpClient _http;

    internal MfaApi(MelayaHttpClient http) => _http = http;

    /// <summary>Return MFA enrollment status for the caller.</summary>
    public async Task<MfaStatus> StatusAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<MfaStatus>("/api/v1/private/mfa/status", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Initiate TOTP setup; returns QR code data and TOTP secret.</summary>
    public async Task<MfaSetupResult> SetupAsync(CancellationToken ct = default)
    {
        return await _http.PostAsync<MfaSetupResult>("/api/v1/private/mfa/setup", null, ct).ConfigureAwait(false);
    }

    /// <summary>Confirm TOTP setup with first valid code; activates MFA.</summary>
    public async Task<BoolResult> ConfirmSetupAsync(string code, CancellationToken ct = default)
    {
        var body = new { code };
        return await _http.PostAsync<BoolResult>("/api/v1/private/mfa/confirm", body, ct).ConfigureAwait(false);
    }
}
