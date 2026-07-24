package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Accounts API — profile management, GDPR export, API-key removal, and
 * credit balance reads.
 *
 * <p>Maps to {@code /api/v1/private/accounts/*} and {@code /api/v1/private/keys/*}.
 *
 * <p>Note: the <em>trading</em> account reads (exchange API keys, usage, api-key status)
 * live in {@link AccountAPI}. This class covers the platform-account operations
 * exposed under the {@code accounts} tRPC router.
 */
public class AccountsAPI {

    private final HttpClient http;

    AccountsAPI(HttpClient http) {
        this.http = http;
    }

    /**
     * GDPR Art 15/20 data export. Returns a JSON blob of all user data.
     * The response may be large; cache the result if needed.
     */
    public JsonNode exportMyData() {
        return http.post("/api/v1/private/accounts/export", null);
    }

    /**
     * Remove a stored CEX API key by ID.
     *
     * @param keyId the ID of the key to remove (from {@link AccountAPI#keys()})
     */
    public JsonNode removeKey(String keyId) {
        return http.delete("/api/v1/private/keys/" + keyId, null);
    }

    /**
     * Update the caller's display name, avatar, or settings.
     *
     * @param body a map of fields to update (e.g. {@code displayName}, {@code avatar})
     */
    public JsonNode updateProfile(Map<String, Object> body) {
        return http.patch("/api/v1/private/accounts/profile", body);
    }

    /**
     * Return the current credit balance and transaction history.
     * Covers all credit types (AI, trading, portfolio-ideas, etc.).
     */
    public JsonNode credits() {
        return http.get("/api/v1/private/accounts/credits", null);
    }

    /** Return the AI/LLM credit balance. */
    public JsonNode aiCredits() {
        return http.get("/api/v1/private/accounts/credits/ai", null);
    }

    /** Return the portfolio-ideas feature credit balance. */
    public JsonNode portfolioIdeasCredits() {
        return http.get("/api/v1/private/accounts/credits/portfolio-ideas", null);
    }

    /** Return the risk-monitoring feature credit balance. */
    public JsonNode riskMonitoringCredits() {
        return http.get("/api/v1/private/accounts/credits/risk-monitoring", null);
    }
}
