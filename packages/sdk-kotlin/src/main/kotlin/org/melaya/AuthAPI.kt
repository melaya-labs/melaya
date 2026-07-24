package org.melaya

import org.json.JSONObject

/**
 * Auth API — login, registration, MFA, password management, and session management.
 *
 * Paths:
 *   - `POST /api/v1/private/auth/login` — password login; returns session JWT or MFA challenge
 *   - `POST /api/v1/private/auth/mfa/verify` — resolve MFA challenge
 *   - `POST /api/v1/private/auth/register` — create a new user account
 *   - `POST /api/v1/private/auth/verify-signup` — confirm email from signup token
 *   - `POST /api/v1/private/auth/resend-verification` — re-send email verification
 *   - `GET  /api/v1/private/auth/me` — current user profile
 *   - `GET  /api/v1/private/auth/check` — session validity check
 *   - `POST /api/v1/private/auth/change-password` — change password
 *   - `POST /api/v1/private/auth/forgot-password` — initiate password reset
 *   - `POST /api/v1/private/auth/reset-password` — complete password reset
 *   - `POST /api/v1/private/auth/mobile-handoff` — create mobile handoff token
 *   - `GET  /api/v1/private/auth/permissions` — caller permission flags
 *   - `POST /api/v1/private/auth/refresh` — rotate session JWT
 */
class AuthAPI internal constructor(private val http: HttpClient) {

    /**
     * Password + username login.
     * Returns a session JWT or an MFA challenge token if MFA is enrolled.
     * Does NOT require the `mk_` API key prefix — the SDK sends the configured key regardless.
     */
    fun login(username: String, password: String): JSONObject {
        return http.post(
            "/api/v1/private/auth/login",
            mapOf("username" to username, "password" to password)
        ).asObject()
    }

    /**
     * Resolve an MFA challenge after login.
     * Exchange the challenge token from [login] plus a TOTP code (or recovery code)
     * for a full session JWT.
     */
    fun verifyMfa(challengeToken: String, code: String): JSONObject {
        return http.post(
            "/api/v1/private/auth/mfa/verify",
            mapOf("challengeToken" to challengeToken, "code" to code)
        ).asObject()
    }

    /**
     * Create a new user account.
     * Sends an email verification message to [email].
     */
    fun register(username: String, email: String, password: String): JSONObject {
        return http.post(
            "/api/v1/private/auth/register",
            mapOf("username" to username, "email" to email, "password" to password)
        ).asObject()
    }

    /** Confirm email address from the link token received after [register]. */
    fun verifySignup(token: String): JSONObject {
        return http.post(
            "/api/v1/private/auth/verify-signup",
            mapOf("token" to token)
        ).asObject()
    }

    /** Re-send the email verification message (requires [email]). */
    fun resendVerification(email: String): JSONObject {
        return http.post(
            "/api/v1/private/auth/resend-verification",
            mapOf("email" to email)
        ).asObject()
    }

    /** Return the current authenticated user's profile (id, username, email, tier, role, capabilities). */
    fun me(): JSONObject {
        return http.get("/api/v1/private/auth/me").asObject()
    }

    /** Lightweight session validity check — returns `{"ok": true}` when the session is valid. */
    fun check(): JSONObject {
        return http.get("/api/v1/private/auth/check").asObject()
    }

    /** Change the authenticated user's password. Re-hashes and invalidates other sessions. */
    fun changePassword(currentPassword: String, newPassword: String): JSONObject {
        return http.post(
            "/api/v1/private/auth/change-password",
            mapOf("currentPassword" to currentPassword, "newPassword" to newPassword)
        ).asObject()
    }

    /** Initiate the password reset flow — sends a reset link to the [email]. */
    fun forgotPassword(email: String): JSONObject {
        return http.post(
            "/api/v1/private/auth/forgot-password",
            mapOf("email" to email)
        ).asObject()
    }

    /** Complete password reset using the [token] received by email and the desired [newPassword]. */
    fun resetPassword(token: String, newPassword: String): JSONObject {
        return http.post(
            "/api/v1/private/auth/reset-password",
            mapOf("token" to token, "newPassword" to newPassword)
        ).asObject()
    }

    /** Create a short-lived handoff token for mobile app deep-link auth. */
    fun createMobileHandoff(): JSONObject {
        return http.post("/api/v1/private/auth/mobile-handoff").asObject()
    }

    /** Return the caller's permission flags (capabilities, tier, feature gates). */
    fun permissions(): JSONObject {
        return http.get("/api/v1/private/auth/permissions").asObject()
    }

    /** Rotate the session JWT (sliding expiry). Returns a new token. */
    fun refresh(): JSONObject {
        return http.post("/api/v1/private/auth/refresh").asObject()
    }

}
