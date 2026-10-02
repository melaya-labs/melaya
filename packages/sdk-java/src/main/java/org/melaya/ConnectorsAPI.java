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

    // ── Several accounts per project connector (owner only for writes) ────────
    // Agents use the DEFAULT account unless a tool call names another one. Only
    // labels and ids ever come back, never credential values.

    /**
     * Accounts connected to one project connector (labels and ids only).
     *
     * <p>Maps to {@code GET /api/v1/private/projects/{project}/connectors/{service}/accounts}.
     *
     * @return an array of {@code {id, label, isDefault, createdAt}} ({@code createdAt} may be null)
     */
    public JsonNode accounts(String project, String service) {
        return http.get(accountsPath(project, service), null);
    }

    /**
     * Add another account to a project connector (owner; the connection is tested first).
     *
     * <p>Maps to {@code POST /api/v1/private/projects/{project}/connectors/{service}/accounts}.
     *
     * @param project      the project name
     * @param service      the connector service id
     * @param label        the new account's label; may be {@code null}
     * @param fields       the connector's credential fields (same keys as {@link #set})
     * @param currentLabel names the existing single connection when it is adopted as the
     *                     first account; may be {@code null}
     * @param makeDefault  make the new account the default; may be {@code null}
     * @return the updated account list
     */
    public JsonNode addAccount(String project, String service, String label, Map<String, String> fields,
                               String currentLabel, Boolean makeDefault) {
        return http.post(accountsPath(project, service),
                CredentialsAPI.accountBody(label, fields, currentLabel, makeDefault));
    }

    /**
     * Choose which account the project connector uses (owner).
     *
     * <p>Maps to {@code PUT /api/v1/private/projects/{project}/connectors/{service}/accounts/default}.
     *
     * @return the updated account list
     */
    public JsonNode setDefaultAccount(String project, String service, String accountId) {
        return http.put(accountsPath(project, service) + "/default", Map.of("accountId", accountId));
    }

    /**
     * Rename one account of a project connector (owner, max 80 chars).
     *
     * <p>Maps to {@code PUT /api/v1/private/projects/{project}/connectors/{service}/accounts/{accountId}}.
     *
     * @return the updated account list
     */
    public JsonNode renameAccount(String project, String service, String accountId, String label) {
        return http.put(accountsPath(project, service) + "/" + encode(accountId), Map.of("label", label));
    }

    /**
     * Remove one account from a project connector (owner).
     *
     * <p>Maps to {@code DELETE /api/v1/private/projects/{project}/connectors/{service}/accounts/{accountId}}.
     *
     * @return the remaining account list
     */
    public JsonNode removeAccount(String project, String service, String accountId) {
        return http.delete(accountsPath(project, service) + "/" + encode(accountId), null);
    }

    private static String accountsPath(String project, String service) {
        return "/api/v1/private/projects/" + encode(project) + "/connectors/" + encode(service) + "/accounts";
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
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"); // path segment: space is %20, never +
        } catch (Exception e) {
            return s;
        }
    }
}
