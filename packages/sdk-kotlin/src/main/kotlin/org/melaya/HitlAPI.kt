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
 *   - `GET  /api/v1/private/projects/:project/tool-calls`        — project tool-call audit ledger
 *   - `GET  /api/v1/private/projects/:project/tool-calls/facets` — audit filter facets (tools, agents)
 *   - `GET  /api/v1/private/runs/:runId/tool-calls/:spanId`      — full untruncated tool-call detail
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

    // ── Project audit ────────────────────────────────────────────────────────

    /**
     * Project tool-call audit ledger: every tool invocation across the project's runs —
     * tool, invoking agent, pipeline + run, who ran it, status, latency, HITL approval
     * provenance, 4KB-truncated input/output. Keyset-paginated newest-first by default.
     *
     * @param project         The project name (URL-encoded automatically).
     * @param beforeCreatedAt Keyset cursor — pass `nextCursor.beforeCreatedAt` from a prior page.
     * @param beforeId        Keyset cursor — pass `nextCursor.beforeId` from a prior page.
     * @param limit           Page size, 1–100 (server default 30).
     * @param tool            Filter by tool name.
     * @param agent           Filter by invoking agent name.
     * @param runId           Filter to a single run.
     * @param status          Filter by outcome: `"ok"` | `"error"`.
     * @param search          Free-text search over tool input/output.
     * @param connectorSource Filter by credential pool: `"project"` | `"personal"`.
     * @param approval        Filter by HITL provenance: `"auto"`, `"approved"`, or `"by:<username>"`.
     * @param provider        Filter by model provider.
     * @param sort            `"recent"` (default) | `"oldest"` | `"slowest"` | `"fastest"`.
     * @return Object with `items` (tool-call records), `nextCursor` (`{beforeCreatedAt, beforeId}` or null), and `capped`.
     */
    fun projectToolCalls(
        project: String,
        beforeCreatedAt: String? = null,
        beforeId: String? = null,
        limit: Int? = null,
        tool: String? = null,
        agent: String? = null,
        runId: String? = null,
        status: String? = null,
        search: String? = null,
        connectorSource: String? = null,
        approval: String? = null,
        provider: String? = null,
        sort: String? = null,
    ): JSONObject {
        val query = buildMap<String, Any?> {
            if (beforeCreatedAt != null) put("beforeCreatedAt", beforeCreatedAt)
            if (beforeId != null)        put("beforeId",        beforeId)
            if (limit != null)           put("limit",           limit)
            if (tool != null)            put("tool",            tool)
            if (agent != null)           put("agent",           agent)
            if (runId != null)           put("runId",           runId)
            if (status != null)          put("status",          status)
            if (search != null)          put("search",          search)
            if (connectorSource != null) put("connectorSource", connectorSource)
            if (approval != null)        put("approval",        approval)
            if (provider != null)        put("provider",        provider)
            if (sort != null)            put("sort",            sort)
        }
        return http.get("/api/v1/private/projects/${enc(project)}/tool-calls", query).asObject()
    }

    /** Distinct tools (with call counts) and agents seen in the project's tool-call ledger — powers audit filters. */
    fun projectToolCallFacets(project: String): JSONObject {
        return http.get("/api/v1/private/projects/${enc(project)}/tool-calls/facets").asObject()
    }

    /** Full, untruncated input/output for a single tool-call span within a run (access-checked). */
    fun toolCallDetail(runId: String, spanId: String): JSONObject {
        return http.get("/api/v1/private/runs/${enc(runId)}/tool-calls/${enc(spanId)}").asObject()
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
