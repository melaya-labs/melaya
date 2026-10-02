package org.melaya

import org.json.JSONObject

/**
 * Triggers API: read, diagnose and dry-run the event triggers that start your pipelines
 * (webhooks, push events, polls of a connected app, stream sources).
 *
 * This module is read and diagnose only. Creating, editing, deleting a trigger and rotating
 * its signing secret stay in the Agent Builder and the MCP server, where push consent and
 * autonomy grants are enforced. No method here ever returns a signing secret.
 *
 * Paths (base `/api/v1/private/triggers`):
 *   - `GET  /`                         list triggers (`?project=&pipelineName=`)
 *   - `GET  /:id`                      one trigger with its config
 *   - `GET  /:id/deliveries`           delivery receipts, newest first
 *   - `GET  /:id/stats`                delivery counts by verdict over a window
 *   - `GET  /:id/approvals`            approvals still waiting on this trigger
 *   - `POST /:id/test`                 dry run of one event; the action never executes
 *   - `GET  /events`                   live event log, including events with no receipt
 *   - `GET  /:triggerId/poll`          poll trigger runtime state
 *   - `POST /:triggerId/poll/test`     dry poll (`dry: true`) or a real poll now (`dry: false`)
 *   - `POST /:triggerId/poll/sync`     re-arm a poller from the saved config
 *   - `GET  /presets`, `/limits`, `/sources`
 *
 * @example
 * ```kotlin
 * val id = melaya.agents.triggers.list(project = "my-project")[0].getString("id")
 *
 * // Why did nothing run? Read the verdicts and the live log.
 * val stats  = melaya.agents.triggers.stats(id, hours = 24)
 * val events = melaya.agents.triggers.events(triggerId = id, verdicts = listOf("filtered", "failed"))
 *
 * // Dry-run a sample event: prefilter, decide and routing run, the action does not.
 * val test = melaya.agents.triggers.test(id, payload = mapOf("subject" to "Refund request"))
 *
 * // Poll triggers: see what the next poll would find, publishing nothing.
 * val dry = melaya.agents.triggers.pollTest(id)
 * ```
 */
class TriggersAPI internal constructor(private val http: HttpClient) {

    private val base = "/api/v1/private/triggers"

    // ── Triggers ─────────────────────────────────────────────────────────────

    /**
     * List the caller's event triggers.
     *
     * Each record carries `id, publicId, name, kind, project, pipelineName, enabled,
     * pausedReason, signingScheme, sourceId, config, maxEventsPerMin, maxRunsPerDay,
     * maxConcurrentRuns, consecutiveFailures, lastEventAt, createdAt, updatedAt`.
     * `config` depends on the trigger kind and is left as raw JSON.
     *
     * @param project      Filter by project name.
     * @param pipelineName Filter by pipeline name.
     */
    fun list(project: String? = null, pipelineName: String? = null): List<JSONObject> {
        val query = buildMap<String, Any?> {
            if (project != null)      put("project",      project)
            if (pipelineName != null) put("pipelineName", pipelineName)
        }
        return asObjects(http.get(base, query))
    }

    /** One trigger with its config, by [id] (UUID). Secrets are never included. */
    fun get(id: String): JSONObject {
        return http.get("$base/${enc(id)}").asObject()
    }

    /**
     * Recent delivery receipts of a trigger, newest first. Each receipt carries
     * `id, triggerId, eventId, source, receivedAt, verdict, decision, action, runId, detail,
     * latencyMs, timings, resultExcerpt, redelivered`.
     *
     * @param limit Page size, 1-200 (server default 50).
     */
    fun deliveries(id: String, limit: Int? = null): List<JSONObject> {
        val query = buildMap<String, Any?> { if (limit != null) put("limit", limit) }
        return asObjects(http.get("$base/${enc(id)}/deliveries", query))
    }

    /**
     * Delivery counts by verdict over a time window:
     * `{ hours, byVerdict: { <verdict>: { n, p50, p95 } }, filtered, sampled }`.
     * `filtered` is null when the counter store is unavailable.
     *
     * @param hours Window, 1-168 (server default 24).
     */
    fun stats(id: String, hours: Int? = null): JSONObject {
        val query = buildMap<String, Any?> { if (hours != null) put("hours", hours) }
        return http.get("$base/${enc(id)}/stats", query).asObject()
    }

    /**
     * Approvals still waiting on this trigger's runs and tool calls. Each carries
     * `requestId, triggerId, deliveryId, eventId, source, service, tool, argsPreview,
     * createdAt, expiresAt` (times in epoch milliseconds). Approve or reject them in the
     * Melaya app.
     */
    fun pendingApprovals(id: String): List<JSONObject> {
        return asObjects(http.get("$base/${enc(id)}/approvals"))
    }

    /**
     * Dry-run one event through the trigger: prefilter, decide and routing run for real,
     * but the action never executes. A test can never refund, post, or start a run. The
     * trigger's rate limits still apply.
     *
     * Returns `{ accepted, eventId, reason? }`; `reason` is set when `accepted` is false
     * (for example `"disabled"` or `"rate_limited"`). Follow the outcome in [events] or
     * [deliveries] with the returned `eventId`.
     *
     * @param payload Sample event payload (a map, list, [JSONObject] or scalar). When null,
     *   no payload is sent and the server uses `{}`.
     */
    fun test(id: String, payload: Any? = null): JSONObject {
        val body = buildMap<String, Any?> { if (payload != null) put("payload", payload) }
        return http.post("$base/${enc(id)}/test", body).asObject()
    }

    /**
     * Recent live trigger events, newest first. Includes outcomes that write no receipt:
     * filtered events, shed load, ingress rejections (signature, rate limit, duplicate,
     * size), feed refusals and push lifecycle notes. The log keeps the last 500 events for
     * 24 hours.
     *
     * Returns `{ events: [{ triggerId, deliveryId, eventId, source, verdict, action?, runId?,
     * detail?, latencyMs?, resultExcerpt?, at }], scanned, retention: { maxEvents, ttlSec } }`.
     * To follow the log, pass the newest `at` you have seen as [since].
     *
     * @param triggerId Scope to one trigger.
     * @param since     Lower bound, epoch milliseconds.
     * @param verdicts  Verdict filter, sent as a comma list (for example `"filtered"`,
     *   `"failed"`, `"dispatched"`).
     * @param limit     Page size, 1-200.
     */
    fun events(
        triggerId: String? = null,
        since: Long? = null,
        verdicts: List<String>? = null,
        limit: Int? = null,
    ): JSONObject {
        val query = buildMap<String, Any?> {
            if (triggerId != null)          put("triggerId", triggerId)
            if (since != null)              put("since",     since)
            if (!verdicts.isNullOrEmpty())  put("verdicts",  verdicts.joinToString(","))
            if (limit != null)              put("limit",     limit)
        }
        return http.get("$base/events", query).asObject()
    }

    // ── Poll triggers ────────────────────────────────────────────────────────

    /**
     * Runtime state of a poll trigger: `{ synced, status, lastError, lastPolledAt,
     * nextPollAt, armed, baselinePending, seenCount, itemsPublished, consecutiveErrors,
     * requestedIntervalSec, effectiveIntervalSec, tierFloorSec }`. When `synced` is false
     * the poller never started; see [pollSync]. Throws [MelayaException] (400) if the
     * trigger does not poll a connected app.
     */
    fun pollStatus(triggerId: String): JSONObject {
        return http.get("$base/${enc(triggerId)}/poll").asObject()
    }

    /**
     * Dry poll: call the poll tool now and report what it found and would publish,
     * publishing nothing. Sends `{ dry: true }`.
     *
     * Returns `{ dry: true, ok: true, found, baseline, wouldPublish, items: [{ id, preview }],
     * samplePayload }`. When `baseline` is true, the first real poll records these items and
     * publishes nothing. Pass `samplePayload` to [test] to dry-run the rest of the trigger.
     *
     * When the tool call fails the server answers 200 with `{ dry: true, ok: false, error }`.
     * That result is returned as is, not thrown, so you can read `error`. The server allows
     * one dry poll every 10 seconds per user (a 429 is thrown as [MelayaException]).
     */
    fun pollTest(triggerId: String): JSONObject {
        return try {
            http.post("$base/${enc(triggerId)}/poll/test", mapOf("dry" to true)).asObject()
        } catch (e: MelayaException) {
            // A failed dry poll is a diagnostic answer, not a request failure.
            val body = e.body as? JSONObject
            if (e.status < 400 && body != null && body.optBoolean("dry", false)) body else throw e
        }
    }

    /**
     * Queue a real poll now, ahead of the schedule. Any new items are published and can
     * start runs. Sends `{ dry: false }` and returns `{ dry: false, queued }`. Throws
     * [MelayaException] (412) if the trigger is turned off.
     */
    fun pollNow(triggerId: String): JSONObject {
        return http.post("$base/${enc(triggerId)}/poll/test", mapOf("dry" to false)).asObject()
    }

    /**
     * Re-create a poll trigger's runtime row from its saved config. Re-arms a trigger whose
     * poller never started. Returns `{ result }`.
     */
    fun pollSync(triggerId: String): JSONObject {
        return http.post("$base/${enc(triggerId)}/poll/sync").asObject()
    }

    // ── Presets, limits, sources ─────────────────────────────────────────────

    /** Trigger presets available to the caller: `{ tier, tierFloorSec, presets, beta }`. */
    fun presets(): JSONObject {
        return http.get("$base/presets").asObject()
    }

    /**
     * Plan caps and current usage: `{ tierClass, beta, triggers: { used, cap },
     * sources: { used, cap }, eventsPerMin: { perUser, perTriggerMax, perTriggerDefault },
     * pollIntervalFloorSec, approvalTtlSec: { min, max, default } }`.
     */
    fun limits(): JSONObject {
        return http.get("$base/limits").asObject()
    }

    /** The caller's WebSocket / SSE stream sources. */
    fun sources(): List<JSONObject> {
        return asObjects(http.get("$base/sources"))
    }

    private fun asObjects(r: Any?): List<JSONObject> = when (r) {
        is org.json.JSONArray -> r.toJsonObjects()
        else -> emptyList()
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20") // path segment: space is %20, never +
}
