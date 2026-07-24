package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

import static org.melaya.MarketAPI.params;

/**
 * Team API — manage project team membership, roles, and invitations.
 *
 * <p>Maps to {@code /api/v1/private/projects/:project/members/*} and
 * {@code /api/v1/private/team/*}.
 *
 * @example
 * <pre>{@code
 * JsonNode members = melaya.team().listMembers("my-project");
 * melaya.team().invite("my-project", "alice");
 * JsonNode link = melaya.team().createInviteLink("my-project");
 * }</pre>
 */
public class TeamAPI {

    private final HttpClient http;

    TeamAPI(HttpClient http) {
        this.http = http;
    }

    // ── Members ───────────────────────────────────────────────────────────────

    /**
     * List members of a project team.
     *
     * @param project the project name
     */
    public JsonNode listMembers(String project) {
        return http.get(
                "/api/v1/private/projects/" + encode(project) + "/members",
                null);
    }

    /**
     * Invite a user to a project team by username.
     *
     * @param project  the project name
     * @param username the username to invite
     */
    public JsonNode invite(String project, String username) {
        return http.post(
                "/api/v1/private/projects/" + encode(project) + "/members/invite",
                params("username", username));
    }

    /**
     * Create a shareable invite link for a project.
     * Returns the URL to share with new team members.
     *
     * @param project the project name
     */
    public JsonNode createInviteLink(String project) {
        return http.post(
                "/api/v1/private/projects/" + encode(project) + "/invite-link",
                null);
    }

    /**
     * Accept a project invite using the token from an invite link.
     *
     * @param token the invite token from the link
     */
    public JsonNode acceptInvite(String token) {
        return http.post("/api/v1/private/team/invite/accept", params("token", token));
    }

    /**
     * Update a team member's role in a project.
     *
     * @param project the project name
     * @param userId  the member's user ID
     * @param role    the new role ({@code "owner"}, {@code "editor"}, or {@code "viewer"})
     */
    public JsonNode updateMemberRole(String project, String userId, String role) {
        return http.patch(
                "/api/v1/private/projects/" + encode(project) + "/members/" + encode(userId),
                params("role", role));
    }

    /**
     * Remove a member from a project team.
     *
     * @param project the project name
     * @param userId  the member's user ID
     */
    public JsonNode removeMember(String project, String userId) {
        return http.delete(
                "/api/v1/private/projects/" + encode(project) + "/members/" + encode(userId),
                null);
    }

    // ── Pipeline visibility ───────────────────────────────────────────────────

    /**
     * Get visibility settings for a pipeline within a project.
     *
     * @param project  the project name
     * @param pipeline the pipeline name
     */
    public JsonNode getPipelineVisibility(String project, String pipeline) {
        return http.get(
                "/api/v1/private/projects/" + encode(project)
                        + "/pipelines/" + encode(pipeline) + "/visibility",
                null);
    }

    /**
     * Set pipeline visibility within a project.
     *
     * @param project  the project name
     * @param pipeline the pipeline name
     * @param body     visibility config map
     */
    public JsonNode setPipelineVisibility(String project, String pipeline, Map<String, Object> body) {
        return http.put(
                "/api/v1/private/projects/" + encode(project)
                        + "/pipelines/" + encode(pipeline) + "/visibility",
                body);
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

