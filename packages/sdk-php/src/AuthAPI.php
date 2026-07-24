<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Auth API — login, register, MFA, password management, session control.
 *
 * Maps to /api/v1/private/auth/* and /api/v1/auth/* (public endpoints).
 *
 * @example
 * ```php
 * $sdk = new Melaya\Melaya(apiKey: getenv('MK'));
 * $me  = $sdk->auth->me();
 * echo $me['username'];
 * ```
 */
class AuthAPI
{
    public function __construct(private readonly HttpClient $http) {}

    // ── Public (no auth required) ────────────────────────────────────────────

    /**
     * Login with username + password.
     * Returns a session JWT (and optional MFA challenge token if MFA is enrolled).
     */
    public function login(string $username, string $password): array
    {
        return $this->http->post('/api/v1/private/auth/login', [
            'username' => $username,
            'password' => $password,
        ]);
    }

    /**
     * Resolve an MFA challenge after login.
     * Exchange challenge token + TOTP code (or recovery code) for a full session JWT.
     */
    public function verifyMfa(string $challengeToken, string $code): array
    {
        return $this->http->post('/api/v1/private/auth/mfa/verify', [
            'challengeToken' => $challengeToken,
            'code'           => $code,
        ]);
    }

    /**
     * Register a new user account.
     * Sends an email verification link.
     */
    public function register(string $username, string $email, string $password): array
    {
        return $this->http->post('/api/v1/private/auth/register', [
            'username' => $username,
            'email'    => $email,
            'password' => $password,
        ]);
    }

    /** Confirm email address from the signup verification link token. */
    public function verifySignup(string $token): array
    {
        return $this->http->post('/api/v1/private/auth/verify-signup', ['token' => $token]);
    }

    /** Re-send the email verification message. */
    public function resendVerification(string $email): array
    {
        return $this->http->post('/api/v1/private/auth/resend-verification', ['email' => $email]);
    }

    /** Initiate a password reset flow; sends email with reset token. */
    public function forgotPassword(string $email): array
    {
        return $this->http->post('/api/v1/private/auth/forgot-password', ['email' => $email]);
    }

    /** Complete password reset using the token from the reset email. */
    public function resetPassword(string $token, string $newPassword): array
    {
        return $this->http->post('/api/v1/private/auth/reset-password', [
            'token'       => $token,
            'newPassword' => $newPassword,
        ]);
    }

    // ── Authenticated ────────────────────────────────────────────────────────

    /**
     * Return the current authenticated user profile.
     * Fields: id, username, email, tier, role, capabilities.
     */
    public function me(): array
    {
        return $this->http->get('/api/v1/private/auth/me');
    }

    /** Lightweight session validity check. Returns ['ok' => true] if valid. */
    public function check(): array
    {
        return $this->http->get('/api/v1/private/auth/check');
    }

    /** Change password (requires current password). Invalidates other sessions. */
    public function changePassword(string $currentPassword, string $newPassword): array
    {
        return $this->http->post('/api/v1/private/auth/change-password', [
            'currentPassword' => $currentPassword,
            'newPassword'     => $newPassword,
        ]);
    }

    /** Create a short-lived handoff token for mobile app deep-link auth. */
    public function createMobileHandoff(): array
    {
        return $this->http->post('/api/v1/private/auth/mobile-handoff');
    }

    /** Return the caller's permission flags (capabilities, tier, feature gates). */
    public function permissions(): array
    {
        return $this->http->get('/api/v1/private/auth/permissions');
    }

    /** Rotate the session JWT (sliding expiry). Returns a new token. */
    public function refresh(): array
    {
        return $this->http->post('/api/v1/private/auth/refresh');
    }

    // ── MFA management ───────────────────────────────────────────────────────

    /** Return MFA enrollment status for the caller. */
    public function mfaStatus(): array
    {
        return $this->http->get('/api/v1/private/mfa/status');
    }

    /** Initiate TOTP setup. Returns a QR code URL and secret. */
    public function mfaSetup(): array
    {
        return $this->http->post('/api/v1/private/mfa/setup');
    }

    /** Confirm TOTP setup with the first valid code. Activates MFA. */
    public function mfaConfirm(string $code): array
    {
        return $this->http->post('/api/v1/private/mfa/confirm', ['code' => $code]);
    }

    // ── Version ──────────────────────────────────────────────────────────────

    /** Get the current server version string (public). */
    public function version(): array
    {
        return $this->http->get('/api/v1/version');
    }
}
