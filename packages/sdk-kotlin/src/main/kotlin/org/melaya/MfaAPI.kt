package org.melaya

import org.json.JSONObject

/**
 * MFA API — enroll and manage TOTP multi-factor authentication.
 *
 * Paths:
 *   - `GET  /api/v1/private/mfa/status`  — MFA enrollment status
 *   - `POST /api/v1/private/mfa/setup`   — initiate TOTP setup; returns QR/secret
 *   - `POST /api/v1/private/mfa/confirm` — confirm TOTP setup with first valid code
 */
class MfaAPI internal constructor(private val http: HttpClient) {

    /** Return MFA enrollment status for the caller. */
    fun status(): JSONObject {
        return http.get("/api/v1/private/mfa/status").asObject()
    }

    /**
     * Initiate TOTP setup.
     * Returns a QR code URL and/or the raw TOTP secret to add to an authenticator app.
     */
    fun setup(): JSONObject {
        return http.post("/api/v1/private/mfa/setup").asObject()
    }

    /**
     * Confirm TOTP setup with the first valid [code] from the authenticator app.
     * Activates MFA on the account.
     */
    fun confirmSetup(code: String): JSONObject {
        return http.post(
            "/api/v1/private/mfa/confirm",
            mapOf("code" to code)
        ).asObject()
    }
}
