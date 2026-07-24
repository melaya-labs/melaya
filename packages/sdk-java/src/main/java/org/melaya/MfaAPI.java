package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * MFA (Multi-Factor Authentication) API — enroll, confirm, and check TOTP.
 *
 * <p>Maps to {@code /api/v1/private/mfa/*}.
 */
public class MfaAPI {

    private final HttpClient http;

    MfaAPI(HttpClient http) {
        this.http = http;
    }

    /** Return the caller's MFA enrollment status. */
    public JsonNode status() {
        return http.get("/api/v1/private/mfa/status", null);
    }

    /**
     * Initiate TOTP setup. Returns a QR-code URI and raw secret.
     * The caller must display the QR code and then confirm with {@link #confirmSetup}.
     */
    public JsonNode setup() {
        return http.post("/api/v1/private/mfa/setup", null);
    }

    /**
     * Confirm TOTP setup with the first valid code; activates MFA on the account.
     *
     * @param code the first TOTP code from the authenticator app
     */
    public JsonNode confirmSetup(String code) {
        return http.post("/api/v1/private/mfa/confirm",
                MarketAPI.params("code", code));
    }
}
