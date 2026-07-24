package org.melaya

import org.json.JSONObject

/**
 * HITL (Human-in-the-Loop) API — list, approve, and reject pending tool-call approvals.
 *
 * Paths:
 *   - `GET  /api/v1/private/hitl/approvals/pending`              — pending approvals
 *   - `GET  /api/v1/private/hitl/approvals/history`              — approval history
 *   - `POST /api/v1/private/hitl/approvals/:id/approve`          — approve a request
 *   - `POST /api/v1/private/hitl/approvals/:id/reject`           — reject a request
 *   - `POST /api/v1/private/hitl/approvals/bulk`                 — bulk approve/reject
 *   - `GET  /api/v1/private/hitl/runs/:runId/tool-stats`         — tool stats for a run
 *   - `GET  /api/v1/private/hitl/runs/:runId/messages`           — paginated run messages
 *   - `GET  /api/v1/private/hitl/runs/:runId/tool-stats/by-agent`— stats by agent
 *   - `GET  /api/v1/private/hitl/runs/:runId/tool-calls`         — all tool calls for a run
 *
 * @example
 * ```kotlin
 * val pending = melaya.hitl.pending()
 * for (req in pending) {
 *     melaya.hitl.approve(req.getString("requestId"), comment = "Looks good")
 * }
 * ```
 */
class HitlAPI internal constructor(private val http: HttpClient) {

    /** List all pending HITL tool-call approvals for the authenticated user. */
    fun pending(): List<JSONObject> {
        val r = http.get("/api/v1/private/hitl/approvals/pending")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("approvals")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /**
     * List historical (decided) HITL approval records.
     * @param limit  Maximum number of records to return.
     * @param offset Pagination offset.
     */
    fun history(limit: Int? = null, offset: Int? = null): List<JSONObject> {
        val query = buildMap<String, Any?> {
            if (limit != null)  put("limit",  limit)
            if (offset != null) put("offset", offset)
        }
        val r = http.get("/api/v1/private/hitl/approvals/history", query)
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("approvals")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /**
     * Approve a pending HITL tool-call approval.
     * @param requestId The `requestId` from the pending approval object.
     * @param comment   Optional approval comment logged with the decision.
     */
    fun approve(requestId: String, comment: String? = null): JSONObject {
        val body = buildMap<String, Any?> {
            if (comment != null) put("comment", comment)
        }
        return http.post(
            "/api/v1/private/hitl/approvals/${enc(requestId)}/approve",
            body
        ).asObject()
    }

    /**
     * Reject a pending HITL tool-call approval.
     * @param requestId The `requestId` from the pending approval object.
     * @param comment   Optional rejection reason.
     */
    fun reject(requestId: String, comment: String? = null): JSONObject {
        val body = buildMap<String, Any?> {
            if (comment != null) put("comment", comment)
        }
        return http.post(
            "/api/v1/private/hitl/approvals/${enc(requestId)}/reject",
            body
        ).asObject()
    }

    /**
     * Bulk approve or reject multiple pending approvals in one call.
     * @param requestIds List of `requestId` values to decide.
     * @param decision   Either `"approved"` or `"rejected"`.
     * @param comment    Optional comment applied to all decisions.
     */
    fun bulkDecide(
        requestIds: List<String>,
        decision: String,
        comment: String? = null,
    ): JSONObject {
        val body = buildMap<String, Any?> {
            put("requestIds", requestIds)
            put("decision", decision)
            if (comment != null) put("comment", comment)
        }
        return http.post("/api/v1/private/hitl/approvals/bulk", body).asObject()
    }

    // ── Run-level inspection ─────────────────────────────────────────────────

    /** Get tool-call statistics for a specific pipeline run. */
    fun runToolStats(runId: String): JSONObject {
        return http.get("/api/v1/private/hitl/runs/${enc(runId)}/tool-stats").asObject()
    }

    /**
     * Get paginated messages for a pipeline run.
     * @param limit  Maximum number of messages.
     * @param cursor Pagination cursor from a previous response.
     */
    fun runMessages(runId: String, limit: Int? = null, cursor: String? = null): List<JSONObject> {
        val query = buildMap<String, Any?> {
            if (limit != null)  put("limit",  limit)
            if (cursor != null) put("cursor", cursor)
        }
        val r = http.get("/api/v1/private/hitl/runs/${enc(runId)}/messages", query)
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("messages")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /** Get tool-call statistics broken down by agent for a run. */
    fun runToolStatsByAgent(runId: String): JSONObject {
        return http.get("/api/v1/private/hitl/runs/${enc(runId)}/tool-stats/by-agent").asObject()
    }

    /** Get all tool calls for a pipeline run. */
    fun runToolCalls(runId: String): List<JSONObject> {
        val r = http.get("/api/v1/private/hitl/runs/${enc(runId)}/tool-calls")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("toolCalls")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
