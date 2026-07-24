package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Project Connectors API — manage credentials at project scope.
 *
 * <p>Maps to {@code /api/v1/private/projects/:project/connectors/*}.
 * Project-scoped connectors are isolated per project, allowing different
 * projects to use different API keys for the same service.
 *
 * <p>For user-scoped credentials see {@link CredentialsAPI}.
 *
 * @example
 * <pre>{@code
 * melaya.connectors().set("my-project", "openai", Map.of("value", "sk-..."));
 * JsonNode services = melaya.connectors().connectedServices("my-project");
 * }</pre>
 */
public class ConnectorsAPI {

    private final HttpClient http;

    ConnectorsAPI(HttpClient http) {
        this.http = http;
    }

    /**
     * List connected services for a project.
     *
     * @param project the project name
     */
    public JsonNode connectedServices(String project) {
        return http.get("/api/v1/private/projects/" + encode(project) + "/connectors/services", null);
    }

    /**
     * Store or update a connector credential at project scope.
     *
     * @param project the project name
     * @param service the service/provider name
     * @param body    map containing at minimum {@code value}; optionally {@code key}, {@code label}
     */
    public JsonNode set(String project, String service, Map<String, Object> body) {
        return http.put(
                "/api/v1/private/projects/" + encode(project) + "/connectors/" + encode(service),
                body);
    }

    /**
     * Delete a project-scoped connector credential.
     *
     * @param project the project name
     * @param service the service name to remove
     */
    public JsonNode delete(String project, String service) {
        return http.delete(
                "/api/v1/private/projects/" + encode(project) + "/connectors/" + encode(service),
                null);
    }

    /**
     * Get a short-lived env-handle token for project-scoped credentials.
     * The runner uses this token to decrypt credentials without a full session.
     *
     * @param project the project name
     */
    public JsonNode getEnvHandle(String project) {
        return http.post(
                "/api/v1/private/projects/" + encode(project) + "/connectors/env-handle",
                null);
    }

    /**
     * Start a Google OAuth flow for a project-scoped connector.
     *
     * @param project the project name
     * @param body    OAuth flow config
     */
    public JsonNode googleOAuthStart(String project, Map<String, Object> body) {
        return http.post(
                "/api/v1/private/projects/" + encode(project) + "/connectors/google/oauth",
                body);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
