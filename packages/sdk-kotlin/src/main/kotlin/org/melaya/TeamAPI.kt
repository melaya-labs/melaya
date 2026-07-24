package org.melaya

import org.json.JSONObject

/**
 * Team API — manage project team membership, roles, invitations, and pipeline visibility.
 *
 * Paths:
 *   - `GET    /api/v1/private/projects/:project/members`                          — list members
 *   - `POST   /api/v1/private/projects/:project/members/invite`                   — invite by username
 *   - `POST   /api/v1/private/projects/:project/invite-link`                      — create invite link
 *   - `POST   /api/v1/private/team/invite/accept`                                 — accept invite
 *   - `PATCH  /api/v1/private/projects/:project/members/:userId`                  — update role
 *   - `DELETE /api/v1/private/projects/:project/members/:userId`                  — remove member
 *   - `GET    /api/v1/private/projects/:project/pipelines/:pipeline/visibility`   — get visibility
 *   - `PUT    /api/v1/private/projects/:project/pipelines/:pipeline/visibility`   — set visibility
 *
 * @example
 * ```kotlin
 * val members = melaya.team.listMembers("my-project")
 * melaya.team.invite("my-project", "alice")
 * val link = melaya.team.createInviteLink("my-project")
 * ```
 */
class TeamAPI internal constructor(private val http: HttpClient) {

    /** List members of a project team. Path param is the project name. */
    fun listMembers(project: String): List<JSONObject> {
        val r = http.get("/api/v1/private/projects/${enc(project)}/members")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("members")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /** Invite a user to a project team by [username]. */
    fun invite(project: String, username: String): JSONObject {
        return http.post(
            "/api/v1/private/projects/${enc(project)}/members/invite",
            mapOf("username" to username)
        ).asObject()
    }

    /**
     * Create a shareable invite link for a project.
     * Returns the URL to send to new team members.
     */
    fun createInviteLink(project: String): JSONObject {
        return http.post("/api/v1/private/projects/${enc(project)}/invite-link").asObject()
    }

    /**
     * Accept a project invite using the [token] from an invite link.
     * Call after the user navigates to the invite URL.
     */
    fun acceptInvite(token: String): JSONObject {
        return http.post(
            "/api/v1/private/team/invite/accept",
            mapOf("token" to token)
        ).asObject()
    }

    /**
     * Update a team member's [role] in a project.
     * Valid roles: owner, editor, viewer.
     */
    fun updateMemberRole(project: String, userId: String, role: String): JSONObject {
        return http.patch(
            "/api/v1/private/projects/${enc(project)}/members/${enc(userId)}",
            mapOf("role" to role)
        ).asObject()
    }

    /** Remove a member from a project team. */
    fun removeMember(project: String, userId: String): JSONObject {
        return http.delete(
            "/api/v1/private/projects/${enc(project)}/members/${enc(userId)}"
        ).asObject()
    }

    // ── Pipeline visibility ───────────────────────────────────────────────────

    /** Get visibility settings for a pipeline within a project. */
    fun getPipelineVisibility(project: String, pipeline: String): JSONObject {
        return http.get(
            "/api/v1/private/projects/${enc(project)}/pipelines/${enc(pipeline)}/visibility"
        ).asObject()
    }

    /** Set pipeline visibility within a project. */
    fun setPipelineVisibility(
        project: String,
        pipeline: String,
        body: Map<String, Any?>,
    ): JSONObject {
        return http.put(
            "/api/v1/private/projects/${enc(project)}/pipelines/${enc(pipeline)}/visibility",
            body
        ).asObject()
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
