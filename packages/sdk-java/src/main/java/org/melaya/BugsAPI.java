package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Bugs API — submit and track bug reports.
 *
 * <p>Maps to {@code /api/v1/private/bugs/*}.
 */
public class BugsAPI {

    private final HttpClient http;

    BugsAPI(HttpClient http) {
        this.http = http;
    }

    /**
     * Submit a bug report.
     *
     * @param body map containing the bug report fields (e.g. {@code title}, {@code description},
     *             {@code severity}, {@code reproSteps})
     */
    public JsonNode create(Map<String, Object> body) {
        return http.post("/api/v1/private/bugs", body);
    }

    /** List bug reports submitted by the caller. */
    public JsonNode listMine() {
        return http.get("/api/v1/private/bugs/mine", null);
    }

    /**
     * Get a single bug report by ID.
     *
     * @param bugId the bug report ID
     */
    public JsonNode get(String bugId) {
        return http.get("/api/v1/private/bugs/" + bugId, null);
    }

    /**
     * Add a comment to a bug report.
     *
     * @param bugId the bug report ID
     * @param body  map containing {@code text}
     */
    public JsonNode addComment(String bugId, Map<String, Object> body) {
        return http.post("/api/v1/private/bugs/" + bugId + "/comments", body);
    }

    /** List unread bug-related notifications for the caller. */
    public JsonNode listNotifications() {
        return http.get("/api/v1/private/bugs/notifications", null);
    }

    /** Mark bug notifications as read. */
    public JsonNode markNotificationsRead() {
        return http.post("/api/v1/private/bugs/notifications/read", null);
    }
}
