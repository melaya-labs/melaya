package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Assistant API — get and save the caller's onboarding / persona profile.
 *
 * <p>Maps to {@code /api/v1/private/assistant/profile}. The profile is stored
 * envelope-encrypted (service={@code assistant_profile}) and used to personalise
 * the in-app assistant experience.
 *
 * @example
 * <pre>{@code
 * JsonNode profile = melaya.assistant().getProfile();
 * melaya.assistant().setProfile(Map.of(
 *     "name",  "Antoine",
 *     "goals", List.of("grow my trading edge")
 * ));
 * }</pre>
 */
public class AssistantAPI {

    private final HttpClient http;

    AssistantAPI(HttpClient http) {
        this.http = http;
    }

    /** Get the caller's assistant onboarding profile. */
    public JsonNode getProfile() {
        return http.get("/api/v1/private/assistant/profile", null);
    }

    /**
     * Save the caller's assistant onboarding profile.
     *
     * @param profile map with optional keys: {@code name}, {@code goals} (list),
     *                {@code context}, {@code preferences}
     */
    public JsonNode setProfile(Map<String, Object> profile) {
        return http.put("/api/v1/private/assistant/profile", profile);
    }
}
