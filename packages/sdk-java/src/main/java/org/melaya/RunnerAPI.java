package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Runner API — mint, list, and revoke runner tokens.
 *
 * <p>Runner tokens ({@code mel_run_} prefix) authenticate the Melaya runner CLI
 * process that executes agent pipelines on your own infrastructure.
 * The plaintext token is shown only once at creation time — store it securely.
 *
 * <p>Maps to {@code /api/v1/private/runner/tokens}.
 *
 * @example
 * <pre>{@code
 * JsonNode created = melaya.runner().createToken(Map.of("label", "prod-server-1"));
 * String token = created.get("token").asText(); // store this — shown once
 *
 * JsonNode tokens = melaya.runner().listTokens();
 * melaya.runner().revokeToken(tokens.get(0).get("id").asText());
 * }</pre>
 */
public class RunnerAPI {

    private final HttpClient http;

    RunnerAPI(HttpClient http) {
        this.http = http;
    }

    /**
     * Mint a new {@code mel_run_} runner token.
     * The plaintext token is returned only in this response — store it securely.
     *
     * @param body optional map; may contain {@code label} (String) to identify the token
     */
    public JsonNode createToken(Map<String, Object> body) {
        return http.post("/api/v1/private/runner/tokens", body);
    }

    /** List all runner tokens for the caller (masked values, with last_seen timestamps). */
    public JsonNode listTokens() {
        return http.get("/api/v1/private/runner/tokens", null);
    }

    /**
     * Revoke a runner token by ID. The token is immediately invalidated.
     *
     * @param tokenId the ID of the token to revoke
     */
    public JsonNode revokeToken(String tokenId) {
        return http.delete("/api/v1/private/runner/tokens/" + tokenId, null);
    }
}
