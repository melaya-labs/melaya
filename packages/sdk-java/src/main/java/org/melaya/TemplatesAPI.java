package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Templates API — create, manage, and share pipeline templates.
 *
 * <p>Maps to {@code /api/v1/private/user-templates/*} (user CRUD) and
 * {@code /api/v1/private/templates/*} (global / validated reads).
 *
 * <p>Visibility levels:
 * <ul>
 *   <li>{@code private} — only the creator</li>
 *   <li>{@code team} — members of the creator's projects</li>
 *   <li>{@code community} — all platform users</li>
 *   <li>{@code assigned} — explicitly assigned to users or projects</li>
 * </ul>
 *
 * @example
 * <pre>{@code
 * JsonNode templates = melaya.templates().list();
 * JsonNode created   = melaya.templates().save(Map.of(
 *     "name",    "My report",
 *     "payload", Map.of("steps", List.of("step1"))
 * ));
 * melaya.templates().share(created.get("id").asText(), "team");
 * }</pre>
 */
public class TemplatesAPI {

    private final HttpClient http;

    TemplatesAPI(HttpClient http) {
        this.http = http;
    }

    // ── User templates (CRUD) ─────────────────────────────────────────────────

    /**
     * List all templates visible to the caller — own + team + community +
     * assigned — filtered by Row-Level Security.
     */
    public JsonNode list() {
        return http.get("/api/v1/private/user-templates", null);
    }

    /**
     * Create a new private user template.
     *
     * @param body map containing {@code name} (required) and {@code payload} (required);
     *             optionally {@code description} and {@code category}
     */
    public JsonNode save(Map<String, Object> body) {
        return http.post("/api/v1/private/user-templates", body);
    }

    /**
     * Update name/description/category/payload of a private user template.
     *
     * @param templateId the template ID
     * @param body       fields to update
     */
    public JsonNode update(String templateId, Map<String, Object> body) {
        return http.patch("/api/v1/private/user-templates/" + encode(templateId), body);
    }

    /**
     * Duplicate a readable template into the caller's private library.
     *
     * @param templateId the source template ID
     */
    public JsonNode duplicate(String templateId) {
        return http.post(
                "/api/v1/private/user-templates/" + encode(templateId) + "/duplicate",
                null);
    }

    /**
     * Delete (or soft-demote if shared) a template.
     *
     * @param templateId the template ID
     */
    public JsonNode delete(String templateId) {
        return http.delete("/api/v1/private/user-templates/" + encode(templateId), null);
    }

    /**
     * Change the visibility of a template.
     *
     * @param templateId the template ID
     * @param visibility one of: {@code "private"}, {@code "team"}, {@code "community"}
     */
    public JsonNode share(String templateId, String visibility) {
        return http.put(
                "/api/v1/private/user-templates/" + encode(templateId) + "/visibility",
                MarketAPI.params("visibility", visibility));
    }

    // ── Assignments ───────────────────────────────────────────────────────────

    /**
     * List all assignments (users / projects) for a template.
     *
     * @param templateId the template ID
     */
    public JsonNode listAssignments(String templateId) {
        return http.get(
                "/api/v1/private/user-templates/" + encode(templateId) + "/assignments",
                null);
    }

    /**
     * Assign a template to a user or project.
     *
     * <p>Maps to {@code POST /api/v1/private/user-templates/:id/assignments}.
     * The server expects exactly one of {@code userId} or {@code projectId}
     * (both UUIDs) in the JSON body.
     *
     * @param templateId the template ID
     * @param target     map containing exactly one of {@code userId} or {@code projectId}
     *                   (UUID), e.g. {@code Map.of("userId", "...")} or
     *                   {@code Map.of("projectId", "...")}
     */
    public JsonNode assign(String templateId, Map<String, Object> target) {
        return http.post(
                "/api/v1/private/user-templates/" + encode(templateId) + "/assignments",
                target);
    }

    /**
     * Remove an assignment from a template.
     *
     * <p>Maps to {@code DELETE /api/v1/private/user-templates/:id/assignments}.
     * The server reads {@code userId}/{@code projectId} from <b>query params</b>
     * only — bodies on DELETE are ignored by the REST bridge.
     *
     * @param templateId the template ID
     * @param target     map containing exactly one of {@code userId} or {@code projectId}
     *                   (UUID) identifying the assignment to remove; sent as query params
     */
    public JsonNode unassign(String templateId, Map<String, Object> target) {
        return http.delete(
                "/api/v1/private/user-templates/" + encode(templateId) + "/assignments",
                target);
    }

    /** List projects the caller is a member of (for use in the share target picker). */
    public JsonNode shareTargets() {
        return http.get("/api/v1/private/user-templates/share-targets", null);
    }

    // ── Global / validated (read-only) ────────────────────────────────────────

    /** List all community-visibility (global) templates. */
    public JsonNode listGlobal() {
        return http.get("/api/v1/private/templates/global", null);
    }

    /** List IDs of all validated (platform-approved) templates. */
    public JsonNode listValidated() {
        return http.get("/api/v1/private/templates/validated", null);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
