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
}
