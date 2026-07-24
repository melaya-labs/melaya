package org.melaya

import org.json.JSONObject

/**
 * Templates API — create, manage, and share pipeline templates.
 *
 * Templates bundle a pipeline definition into a reusable, shareable artifact.
 * Visibility levels: private (only you), team, community, assigned.
 *
 * Paths (/api/v1/private/user-templates/):
 *   - `GET    /api/v1/private/user-templates`                              — list visible templates
 *   - `POST   /api/v1/private/user-templates`                              — create template
 *   - `PATCH  /api/v1/private/user-templates/:id`                          — update template
 *   - `POST   /api/v1/private/user-templates/:sourceId/duplicate`          — duplicate template
 *   - `DELETE /api/v1/private/user-templates/:id`                          — delete template
 *   - `PUT    /api/v1/private/user-templates/:id/visibility`               — set visibility
 *   - `POST   /api/v1/private/user-templates/:templateId/assignments`      — add assignment (`{ userId }` XOR `{ projectId }` body)
 *   - `DELETE /api/v1/private/user-templates/:templateId/assignments`      — remove assignment (`userId` XOR `projectId` query params)
 *   - `GET    /api/v1/private/user-templates/share-targets`                — share target picker
 *   - `GET    /api/v1/private/user-templates/:templateId/assignments`      — list assignments
 *
 * Paths (/api/v1/private/templates/):
 *   - `GET    /api/v1/private/templates/validated`                         — validated template IDs
 *   - `GET    /api/v1/private/templates/global`                            — community templates
 *
 * @example
 * ```kotlin
 * val templates = melaya.templates.list()
 * val t = melaya.templates.save(name = "My report", payload = mapOf("type" to "report"))
 * melaya.templates.share(t.getString("id"), "team")
 * ```
 */
class TemplatesAPI internal constructor(private val http: HttpClient) {

    /**
     * List all templates visible to the caller — own + team + community + assigned,
     * filtered by Row-Level Security.
     */
    fun list(): List<JSONObject> {
        val r = http.get("/api/v1/private/user-templates")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("templates")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /** List all community-visibility (global) templates. */
    fun listGlobal(): List<JSONObject> {
        val r = http.get("/api/v1/private/templates/global")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("templates")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /** List IDs of all validated (platform-approved) templates. */
    fun listValidated(): List<String> {
        val r = http.get("/api/v1/private/templates/validated")
        return when (r) {
            is org.json.JSONArray -> (0 until r.length()).map { r.getString(it) }
            is JSONObject -> {
                val arr = r.optJSONArray("ids") ?: return emptyList()
                (0 until arr.length()).map { arr.getString(it) }
            }
            else -> emptyList()
        }
    }

    /**
     * Create a new private user template.
     *
     * @param name        Human-readable template name.
     * @param payload     The pipeline definition payload.
     * @param description Optional description.
     * @param category    Optional category label.
     */
    fun save(
        name: String,
        payload: Map<String, Any?>,
        description: String? = null,
        category: String? = null,
    ): JSONObject {
        val body = buildMap<String, Any?> {
            put("name", name)
            put("payload", payload)
            if (description != null) put("description", description)
            if (category != null)    put("category", category)
        }
        return http.post("/api/v1/private/user-templates", body).asObject()
    }

    /** Update name/description/category/payload of a private user template. */
    fun update(
        templateId: String,
        name: String? = null,
        description: String? = null,
        category: String? = null,
        payload: Map<String, Any?>? = null,
    ): JSONObject {
        val body = buildMap<String, Any?> {
            if (name != null)        put("name",        name)
            if (description != null) put("description", description)
            if (category != null)    put("category",    category)
            if (payload != null)     put("payload",     payload)
        }
        return http.patch("/api/v1/private/user-templates/${enc(templateId)}", body).asObject()
    }

    /** Duplicate a readable template into the caller's private library. */
    fun duplicate(templateId: String, newName: String? = null): JSONObject {
        val body = buildMap<String, Any?> {
            if (newName != null) put("newName", newName)
        }
        return http.post(
            "/api/v1/private/user-templates/${enc(templateId)}/duplicate",
            body
        ).asObject()
    }

    /** Delete (or soft-demote if shared) a template. */
    fun delete(templateId: String): JSONObject {
        return http.delete("/api/v1/private/user-templates/${enc(templateId)}").asObject()
    }

    /**
     * Change the visibility of a template.
     * Valid values: `"private"`, `"team"`, `"community"`, `"assigned"`.
     */
    fun share(templateId: String, visibility: String): JSONObject {
        return http.put(
            "/api/v1/private/user-templates/${enc(templateId)}/visibility",
            mapOf("visibility" to visibility)
        ).asObject()
    }

    // ── Assignments ──────────────────────────────────────────────────────────

    /** List all assignments (users / projects) for a template. */
    fun listAssignments(templateId: String): List<JSONObject> {
        val r = http.get("/api/v1/private/user-templates/${enc(templateId)}/assignments")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("assignments")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /**
     * Assign a template to a user or a project.
     *
     * Pass exactly one of [userId] or [projectId] (both are UUIDs). The server
     * contract is `{ userId }` XOR `{ projectId }` in the POST body.
     *
     * @param userId    Assign to this user ID (UUID).
     * @param projectId Assign to this project ID (UUID).
     */
    fun assign(
        templateId: String,
        userId: String? = null,
        projectId: String? = null,
    ): JSONObject {
        require((userId != null) xor (projectId != null)) {
            "Pass exactly one of userId or projectId."
        }
        val body = if (userId != null) mapOf("userId" to userId) else mapOf("projectId" to projectId)
        return http.post(
            "/api/v1/private/user-templates/${enc(templateId)}/assignments",
            body
        ).asObject()
    }

    /**
     * Remove an assignment from a template.
     *
     * Pass exactly one of [userId] or [projectId] (both are UUIDs). The server
     * reads `userId` / `projectId` from the DELETE query string (request bodies
     * on DELETE are ignored by the REST bridge).
     *
     * @param userId    Unassign this user ID (UUID).
     * @param projectId Unassign this project ID (UUID).
     */
    fun unassign(
        templateId: String,
        userId: String? = null,
        projectId: String? = null,
    ): JSONObject {
        require((userId != null) xor (projectId != null)) {
            "Pass exactly one of userId or projectId."
        }
        val query = if (userId != null) mapOf<String, Any?>("userId" to userId)
                    else mapOf<String, Any?>("projectId" to projectId)
        return http.delete(
            "/api/v1/private/user-templates/${enc(templateId)}/assignments",
            query = query
        ).asObject()
    }

    /** List projects the caller is a member of (for use in the share target picker). */
    fun shareTargets(): List<JSONObject> {
        val r = http.get("/api/v1/private/user-templates/share-targets")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("projects")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
