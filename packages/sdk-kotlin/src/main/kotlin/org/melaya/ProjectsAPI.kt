package org.melaya

import org.json.JSONObject

/**
 * Projects API — create and list agent projects.
 *
 * Projects are the top-level namespace for pipelines, connectors, and team membership.
 *
 * Paths:
 *   - `GET   /api/v1/private/projects`        — list accessible projects
 *   - `POST  /api/v1/private/projects`        — create a new project
 *   - `PATCH /api/v1/private/projects/rename` — rename a project
 *   - `GET   /api/v1/private/projects/runner` — runner-facing project list
 *
 * @example
 * ```kotlin
 * val projects = melaya.projects.list()
 * val created  = melaya.projects.create(name = "my-agents", description = "Production agents")
 * melaya.projects.rename(oldName = "my-agents", newName = "prod-agents")
 * ```
 */
class ProjectsAPI internal constructor(private val http: HttpClient) {

    /**
     * List all projects the authenticated user can access
     * (owned projects + projects they are a member of).
     */
    fun list(): List<JSONObject> {
        val r = http.get("/api/v1/private/projects")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("projects")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /** Create a new agent project. */
    fun create(name: String, description: String? = null): JSONObject {
        val body = buildMap<String, Any?> {
            put("name", name)
            if (description != null) put("description", description)
        }
        return http.post("/api/v1/private/projects", body).asObject()
    }

    /** Rename a project from [oldName] to [newName]. */
    fun rename(oldName: String, newName: String): JSONObject {
        return http.patch(
            "/api/v1/private/projects/rename",
            mapOf("oldName" to oldName, "newName" to newName)
        ).asObject()
    }

    /** Get the runner-facing project list (used internally by the runner CLI). */
    fun runnerProjects(): List<JSONObject> {
        val r = http.get("/api/v1/private/projects/runner")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("projects")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }
}
