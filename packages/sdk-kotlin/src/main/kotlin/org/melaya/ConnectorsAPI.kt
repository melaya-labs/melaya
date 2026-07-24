package org.melaya

import org.json.JSONObject

/**
 * Project Connectors API — manage credentials at project scope.
 *
 * Project-scoped connectors are isolated per project, letting different projects
 * use different API keys for the same service. For user-level credentials use [CredentialsAPI].
 *
 * Paths:
 *   - `GET    /api/v1/private/projects/:project/connectors/services`        — list connected services
 *   - `PUT    /api/v1/private/projects/:project/connectors/:service`         — store connector
 *   - `DELETE /api/v1/private/projects/:project/connectors/:service`         — delete connector
 *   - `POST   /api/v1/private/projects/:project/connectors/env-handle`       — get env-handle token
 *   - `POST   /api/v1/private/projects/:project/connectors/google/oauth`     — start Google OAuth
 *
 * @example
 * ```kotlin
 * melaya.connectors.set("my-project", "openai", value = "sk-...")
 * val services = melaya.connectors.connectedServices("my-project")
 * ```
 */
class ConnectorsAPI internal constructor(private val http: HttpClient) {

    /** List connected services for a project. */
    fun connectedServices(project: String): List<JSONObject> {
        val r = http.get("/api/v1/private/projects/${enc(project)}/connectors/services")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("services")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /**
     * Store a connector credential at project scope.
     *
     * @param project The project name.
     * @param service The service name (e.g. `"openai"`, `"anthropic"`).
     * @param value   Plaintext credential value.
     * @param key     Optional named key within the service.
     * @param label   Optional human-readable label.
     */
    fun set(
        project: String,
        service: String,
        value: String,
        key: String? = null,
        label: String? = null,
    ): JSONObject {
        val body = buildMap<String, Any?> {
            put("value", value)
            if (key != null)   put("key",   key)
            if (label != null) put("label", label)
        }
        return http.put(
            "/api/v1/private/projects/${enc(project)}/connectors/${enc(service)}",
            body
        ).asObject()
    }

    /** Delete a project-scoped connector credential. */
    fun delete(project: String, service: String): JSONObject {
        return http.delete(
            "/api/v1/private/projects/${enc(project)}/connectors/${enc(service)}"
        ).asObject()
    }

    /**
     * Get a short-lived env-handle token for project-scoped credentials.
     * The runner uses this token to decrypt credentials without a full session.
     */
    fun envHandle(project: String): JSONObject {
        return http.post(
            "/api/v1/private/projects/${enc(project)}/connectors/env-handle"
        ).asObject()
    }

    /** Start Google OAuth flow for a project-scoped connector. */
    fun googleOAuthStart(project: String, body: Map<String, Any?> = emptyMap()): JSONObject {
        return http.post(
            "/api/v1/private/projects/${enc(project)}/connectors/google/oauth",
            body
        ).asObject()
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
