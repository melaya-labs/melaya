package org.melaya

import org.json.JSONObject

/**
 * Bugs API — submit and track in-app bug reports and feedback.
 *
 * Paths:
 *   - `POST  /api/v1/private/bugs`                          — submit a bug report
 *   - `GET   /api/v1/private/bugs/mine`                     — list own bug reports
 *   - `GET   /api/v1/private/bugs/:bugId`                   — get a bug report by ID
 *   - `POST  /api/v1/private/bugs/:bugId/comments`          — add a comment
 *   - `GET   /api/v1/private/bugs/notifications`            — unread bug notifications
 *   - `POST  /api/v1/private/bugs/notifications/read`       — mark notifications read
 *
 * @example
 * ```kotlin
 * val bug = melaya.bugs.create(title = "Crash on login", description = "Steps: ...")
 * val mine = melaya.bugs.listMine()
 * melaya.bugs.addComment(bug.getString("id"), "Reproduced on v2.3")
 * ```
 */
class BugsAPI internal constructor(private val http: HttpClient) {

    /**
     * Submit a bug report.
     *
     * @param title       Short title summarising the issue.
     * @param description Detailed reproduction steps and observed vs expected behaviour.
     * @param extra       Optional additional fields (severity, component, etc.).
     */
    fun create(
        title: String,
        description: String? = null,
        extra: Map<String, Any?> = emptyMap(),
    ): JSONObject {
        val body = buildMap<String, Any?> {
            put("title", title)
            if (description != null) put("description", description)
            putAll(extra)
        }
        return http.post("/api/v1/private/bugs", body).asObject()
    }

    /** List bug reports submitted by the authenticated caller. */
    fun listMine(): List<JSONObject> {
        val r = http.get("/api/v1/private/bugs/mine")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("bugs")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /** Get a single bug report by [bugId]. */
    fun get(bugId: String): JSONObject {
        return http.get("/api/v1/private/bugs/${enc(bugId)}").asObject()
    }

    /** Add a comment to a bug report. */
    fun addComment(bugId: String, comment: String): JSONObject {
        return http.post(
            "/api/v1/private/bugs/${enc(bugId)}/comments",
            mapOf("comment" to comment)
        ).asObject()
    }

    /** List unread bug-related notifications for the caller. */
    fun listNotifications(): List<JSONObject> {
        val r = http.get("/api/v1/private/bugs/notifications")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("notifications")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /** Mark bug notifications as read. */
    fun markNotificationsRead(body: Map<String, Any?> = emptyMap()): JSONObject {
        return http.post("/api/v1/private/bugs/notifications/read", body).asObject()
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
