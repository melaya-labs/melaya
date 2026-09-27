package org.melaya

import org.json.JSONObject

private val CONNECTOR_TOOLS_TERMINAL_STATUSES = setOf("done", "rejected", "expired")

/**
 * Connector Tools API — call ANY unlocked connector tool directly over REST, the same
 * surface the Melaya Assistant and MCP server use to reach a connected service (Gmail,
 * Slack, Stripe, …). Do NOT confuse this with [ConnectorsAPI], which only stores
 * project-scoped credentials.
 *
 * Reads run immediately. Writes default to an approval card raised in the Melaya app
 * (the same card the Assistant raises) under `approval = "required"`; pass
 * `approval = "none"` to run a write immediately instead — it is still audit-logged.
 * Tools that move money or trade are refused under BOTH approval modes and only ever
 * run from the Melaya app. No method here ever accepts or returns a credential value.
 *
 * Paths (base `/api/v1/private/connector-tools`):
 *   - `GET  /services`         — connected services + read/write tool counts
 *   - `GET  /search`           — discover tools by plain business keywords
 *   - `GET  /tools/:tool`      — describe one tool (params, `readOnly`, `movesMoney`)
 *   - `POST /test`             — test the stored credential for a service
 *   - `POST /connect`          — start connecting a service (never takes a secret)
 *   - `POST /call`             — call a tool; a staged write returns 202, not an error
 *   - `GET  /calls/:requestId` — outcome of a staged write
 *
 * @example
 * ```kotlin
 * val hits = melaya.agents.connectorTools.search("unread email").getJSONArray("tools")
 * val read = melaya.connectorTools.call(hits.getJSONObject(0).getString("name"))
 * println(read.getString("result"))
 *
 * // A write defaults to an approval card in the Melaya app; block until it settles.
 * val outcome = melaya.connectorTools.callAndWait(
 *     "gmail_send",
 *     args = mapOf("to" to "a@b.com", "subject" to "Hi", "body" to "…"),
 * )
 * ```
 */
class ConnectorToolsAPI internal constructor(private val http: HttpClient) {

    private val base = "/api/v1/private/connector-tools"

    /** Connected services + built-in namespace + per-service read/write tool counts. */
    fun services(): JSONObject {
        return http.get("$base/services").asObject()
    }

    /**
     * Discover unlocked tools by plain business keywords (e.g. `"unread email"`, `"refund"`).
     *
     * @param q     Required search text.
     * @param limit Max results, 1-50 (server default 15).
     */
    fun search(q: String, limit: Int? = null): JSONObject {
        val query = buildMap<String, Any?> {
            put("q", q)
            if (limit != null) put("limit", limit)
        }
        return http.get("$base/search", query).asObject()
    }

    /** Full description of one tool (params, `readOnly`, `movesMoney`) — 404 when unknown or not unlocked. */
    fun describe(tool: String): JSONObject {
        return http.get("$base/tools/${enc(tool)}").asObject()
    }

    /** Test the STORED credential for [service] (server-side 30s timeout surfaces as a `timeout` error). */
    fun test(service: String): JSONObject {
        return http.post("$base/test", mapOf("service" to service)).asObject()
    }

    /**
     * Start connecting [service]. Returns a URL or instruction for the user to complete
     * themselves in a browser or the Melaya app — this call never accepts or returns a secret.
     */
    fun connect(service: String): JSONObject {
        return http.post("$base/connect", mapOf("service" to service)).asObject()
    }

    /**
     * Call a connector tool.
     *
     * A read tool, or a write with `approval = "none"`, runs immediately and returns
     * `{status: "done", tool, readOnly, result}`. A write with `approval = "required"`
     * (the default) is staged as an approval card in the Melaya app and returns **202**
     * `{status: "pending_approval", tool, requestId, message}` — poll [callStatus] with
     * `requestId`, or use [callAndWait]. That 202 is a normal, successful return value
     * here, never a thrown error.
     *
     * Tools that move money or trade are refused under both approval modes (a thrown
     * [MelayaException] with `code == "money_moving_requires_app_approval"`).
     *
     * @param tool     Tool name, from [search] or [describe].
     * @param args     Tool arguments.
     * @param approval `"required"` (default: stages an approval card) or `"none"` (runs immediately, audit-logged).
     */
    fun call(
        tool: String,
        args: Map<String, Any?> = emptyMap(),
        approval: String = "required",
    ): JSONObject {
        val body = mapOf("tool" to tool, "args" to args, "approval" to approval)
        return http.post("$base/call", body).asObject()
    }

    /** Outcome of a staged write started by [call]. `status` is `pending`, `running`, `done`, `rejected`, or `expired`. */
    fun callStatus(requestId: String): JSONObject {
        return http.get("$base/calls/${enc(requestId)}").asObject()
    }

    /**
     * Call a tool and block until it settles: calls [call], and — only when that response
     * is a staged `pending_approval` write — polls [callStatus] until `status` is `done`,
     * `rejected`, or `expired`, or [timeoutMs] elapses, whichever comes first. A read, or a
     * write run with `approval = "none"`, never polls: [call]'s own response is already final.
     *
     * @param pollIntervalMs Delay between polls while `pending`/`running`. Default 3 000 ms.
     * @param timeoutMs      Give up and return the last-seen outcome after this long. Default 600 000 ms (10 min).
     */
    fun callAndWait(
        tool: String,
        args: Map<String, Any?> = emptyMap(),
        approval: String = "required",
        pollIntervalMs: Long = 3_000L,
        timeoutMs: Long = 600_000L,
    ): JSONObject {
        val first = call(tool, args, approval)
        if (first.optString("status") != "pending_approval") return first

        val requestId = first.getString("requestId")
        val deadline = System.currentTimeMillis() + timeoutMs
        var outcome = callStatus(requestId)
        while (outcome.optString("status") !in CONNECTOR_TOOLS_TERMINAL_STATUSES &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(pollIntervalMs)
            outcome = callStatus(requestId)
        }
        return outcome
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
