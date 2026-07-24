package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import static org.melaya.MarketAPI.params;

/**
 * Auth API — session management, password flows, MFA, and user registration.
 *
 * <p>Public endpoints do not require a Bearer token; they are included for
 * completeness so SDK consumers can drive the full auth lifecycle.
 *
 * <p>Maps to {@code /api/v1/private/auth/*} and {@code /api/v1/auth/*}.
 */
public class AuthAPI {

    private final HttpClient http;

    AuthAPI(HttpClient http) {
        this.http = http;
    }

    // ── Public (no auth required) ─────────────────────────────────────────────

    /**
     * Password + username login. Returns session JWT or an MFA challenge token.
     *
     * @param username caller's username
     * @param password caller's password
     */
    public JsonNode login(String username, String password) {
        return http.post("/api/v1/private/auth/login",
                params("username", username, "password", password));
    }

    /**
     * Resolve an MFA challenge after login. Exchange the challenge token +
     * TOTP/recovery code for a full session JWT.
     *
     * @param challengeToken the token returned from {@link #login}
     * @param code           TOTP code or recovery code
     */
    public JsonNode verifyMfa(String challengeToken, String code) {
        return http.post("/api/v1/private/auth/mfa/verify",
                params("challengeToken", challengeToken, "code", code));
    }

    /**
     * Create a new user account. Sends an email verification message.
     *
     * @param username desired username
     * @param email    registration email
     * @param password desired password
     */
    public JsonNode register(String username, String email, String password) {
        return http.post("/api/v1/private/auth/register",
                params("username", username, "email", email, "password", password));
    }

    /**
     * Confirm email address from a signup-link token.
     *
     * @param token the token embedded in the verification email link
     */
    public JsonNode verifySignup(String token) {
        return http.post("/api/v1/private/auth/verify-signup", params("token", token));
    }

    /**
     * Re-send the email verification message.
     *
     * @param email the address to resend to
     */
    public JsonNode resendVerification(String email) {
        return http.post("/api/v1/private/auth/resend-verification", params("email", email));
    }

    /**
     * Initiate a password reset flow. Sends an email with a reset token.
     *
     * @param email the account's email address
     */
    public JsonNode forgotPassword(String email) {
        return http.post("/api/v1/private/auth/forgot-password", params("email", email));
    }

    /**
     * Complete a password reset using the token from the reset email.
     *
     * @param token       the reset token from the email link
     * @param newPassword the desired new password
     */
    public JsonNode resetPassword(String token, String newPassword) {
        return http.post("/api/v1/private/auth/reset-password",
                params("token", token, "newPassword", newPassword));
    }

    /**
     * Return the current server version string (public — no auth required).
     * Maps to {@code GET /api/v1/version}.
     */
    public JsonNode version() {
        return http.get("/api/v1/version", null);
    }

    // ── Authenticated ─────────────────────────────────────────────────────────

    /**
     * Return the current authenticated user profile
     * (id, username, email, tier, role, capabilities).
     */
    public JsonNode me() {
        return http.get("/api/v1/private/auth/me", null);
    }

    /** Lightweight session validity check; returns {@code {ok: true}}. */
    public JsonNode check() {
        return http.get("/api/v1/private/auth/check", null);
    }

    /**
     * Change the caller's password. Re-hashes and invalidates other sessions.
     *
     * @param currentPassword the caller's current password
     * @param newPassword      the desired new password
     */
    public JsonNode changePassword(String currentPassword, String newPassword) {
        return http.post("/api/v1/private/auth/change-password",
                params("currentPassword", currentPassword, "newPassword", newPassword));
    }

    /**
     * Create a short-lived handoff token for mobile app deep-link auth.
     */
    public JsonNode createMobileHandoff() {
        return http.post("/api/v1/private/auth/mobile-handoff", null);
    }

    /**
     * Return the caller's permission flags (capabilities, tier, feature gates).
     */
    public JsonNode myPermissions() {
        return http.get("/api/v1/private/auth/permissions", null);
    }

    /** Rotate the session JWT (sliding expiry); returns a new token. */
    public JsonNode refresh() {
        return http.post("/api/v1/private/auth/refresh", null);
    }
}
