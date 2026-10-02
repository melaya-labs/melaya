package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Account API — authenticated reads about your Melaya account.
 * Maps to {@code https://api.melaya.org/api/v1/private/*}.
 */
public class AccountAPI {

    private final HttpClient http;

    AccountAPI(HttpClient http) {
        this.http = http;
    }

    /**
     * The exchange API keys connected to your account.
     * {@code apiKey} is masked (display-only); use {@code apiKeyId} when launching strategies.
     */
    public JsonNode keys() {
        return http.get("/api/v1/private/keys", null).get("keys");
    }

    /** Tier, plan limits, and live usage counters (mirrors the dashboard's usage page). */
    public JsonNode usage() {
        return http.get("/api/v1/private/usage", null);
    }

    /** Status of your platform API key (tier, max concurrent connections). */
    public JsonNode apiKeyStatus() {
        return http.get("/api/v1/private/api-key", null);
    }

    /**
     * Generate a new platform API key, replacing the current one at once. The new
     * key is returned ONCE.
     *
     * <p><strong>Careful:</strong> if this is called with the key this client uses, every
     * later call of this client fails until you build a new client with the returned key.
     *
     * <p>Maps to {@code POST /api/v1/private/api-key}.
     *
     * @return {@code {apiKey}}: the new key, shown only this once
     */
    public JsonNode rotateApiKey() {
        return http.post("/api/v1/private/api-key", java.util.Map.of());
    }

    /**
     * Revoke the platform API key.
     *
     * <p><strong>Careful:</strong> if this is called with the key this client uses, this
     * client stops working immediately.
     *
     * <p>Maps to {@code DELETE /api/v1/private/api-key}.
     *
     * @return {@code {ok}}
     */
    public JsonNode revokeApiKey() {
        return http.delete("/api/v1/private/api-key", null);
    }

    /**
     * Request counts of your platform API key (current key, merged with your account
     * totals).
     *
     * <p>Maps to {@code GET /api/v1/private/api-key/usage}.
     */
    public JsonNode apiKeyUsage() {
        return http.get("/api/v1/private/api-key/usage", null);
    }
}
