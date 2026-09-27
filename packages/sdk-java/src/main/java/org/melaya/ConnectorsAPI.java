package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * Share the caller's OWN personal connector credential into the project
     * (editor/owner only). Values stay server-side — only the reference moves.
     *
     * <p>Maps to {@code POST /api/v1/private/projects/{project}/connectors/{service}/apply-personal}.
     *
     * @param project            the project name
     * @param service            the service/provider name
     * @param googleCapabilities optional Google capability strings to share
     *                           (e.g. {@code "gmail"}, {@code "calendar"}); may be {@code null}
     */
    public JsonNode applyPersonal(String project, String service, List<String> googleCapabilities) {
        Map<String, Object> body = googleCapabilities != null
                ? Map.of("googleCapabilities", googleCapabilities)
                : Map.of();
        return http.post(
                "/api/v1/private/projects/" + encode(project) + "/connectors/" + encode(service) + "/apply-personal",
                body);
    }

    /**
     * Which member shared each connected project connector (usernames only,
     * never values).
     *
     * <p>Maps to {@code GET /api/v1/private/projects/{project}/connectors/shared-by}.
     */
    public JsonNode sharedBy(String project) {
        return http.get("/api/v1/private/projects/" + encode(project) + "/connectors/shared-by", null);
    }

    /**
     * List the Google OAuth capabilities actually granted to a project.
     *
     * <p>Maps to {@code GET /api/v1/private/projects/{project}/connectors/google/status}.
     */
    public JsonNode googleStatus(String project) {
        return http.get("/api/v1/private/projects/" + encode(project) + "/connectors/google/status", null);
    }

    /**
     * Select the project's connected Google account used by one capability.
     *
     * <p>Maps to {@code PUT /api/v1/private/projects/{project}/connectors/google/default}.
     *
     * @param project    the project name
     * @param capability a Google capability: {@code gmail}, {@code calendar}, {@code drive},
     *                   {@code sheets}, {@code docs}, {@code search_console}, {@code youtube},
     *                   {@code google_ads}, {@code analytics}, {@code meet}, {@code slides}
     * @param accountId  the 24-hex-char connected-account ID
     */
    public JsonNode googleSetDefault(String project, String capability, String accountId) {
        return http.put(
                "/api/v1/private/projects/" + encode(project) + "/connectors/google/default",
                Map.of("capability", capability, "accountId", accountId));
    }

    /**
     * Disconnect one Google product, or an entire Google account, from a project.
     *
     * <p>Maps to {@code DELETE /api/v1/private/projects/{project}/connectors/google/access}
     * with a JSON body (not query params).
     *
     * @param project    the project name
     * @param accountId  the connected-account ID to disconnect
     * @param capability optional single capability to disconnect; {@code null} disconnects
     *                   the whole account
     */
    public JsonNode googleDisconnect(String project, String accountId, String capability) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", accountId);
        if (capability != null) body.put("capability", capability);
        return http.delete(
                "/api/v1/private/projects/" + encode(project) + "/connectors/google/access",
                null, body);
    }

    /**
     * Test a project database connector from the user's runner (reaches
     * IP-allow-listed / VPC hosts). Returns {@code { sessionId, ... }} to poll
     * with {@link #dbTestStatus}.
     *
     * <p>Maps to {@code POST /api/v1/private/projects/{project}/connectors/db-test}.
     *
     * @param project     the project name
     * @param service     the database service name
     * @param credentials optional connection credentials; may be {@code null}
     */
    public JsonNode dbTestStart(String project, String service, Map<String, Object> credentials) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", service);
        if (credentials != null) body.put("credentials", credentials);
        return http.post(
                "/api/v1/private/projects/" + encode(project) + "/connectors/db-test",
                body);
    }

    /**
     * Poll a project database connector runner-test result.
     *
     * <p>Maps to {@code GET /api/v1/private/projects/{project}/connectors/db-test/{sessionId}}.
     */
    public JsonNode dbTestStatus(String project, String sessionId) {
        return http.get(
                "/api/v1/private/projects/" + encode(project) + "/connectors/db-test/" + encode(sessionId),
                null);
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
