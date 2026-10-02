package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.melaya.MarketAPI.params;

/**
 * Event triggers: read, diagnose and dry-run the triggers that start your
 * pipelines (webhooks, push events, polls of a connected app, stream sources).
 *
 * <p>Maps to {@code /api/v1/private/triggers/*}. This module is read and
 * diagnose only. Creating, editing, deleting a trigger and rotating its
 * signing secret stay in the Agent Builder and the MCP server, where push
 * consent and autonomy grants are enforced. No method here ever returns a
 * signing secret.
 *
 * <p>Covers:
 * <ul>
 *   <li>{@code GET  /triggers}, {@code /triggers/{id}}: list and get</li>
 *   <li>{@code GET  /triggers/{id}/deliveries}, {@code /stats}, {@code /approvals}: history and pending approvals</li>
 *   <li>{@code GET  /triggers/events}: the live event log, including events that wrote no receipt</li>
 *   <li>{@code POST /triggers/{id}/test}: a dry run; the action never executes</li>
 *   <li>{@code GET  /triggers/{id}/poll}, {@code POST /poll/test}, {@code POST /poll/sync}: poll triggers</li>
 *   <li>{@code GET  /triggers/presets}, {@code /limits}, {@code /sources}: presets, plan caps, stream sources</li>
 * </ul>
 *
 * @example
 * <pre>{@code
 * JsonNode triggers = melaya.agents().triggers().list("my-project", null);
 * String id = triggers.get(0).get("id").asText();
 *
 * // Why did nothing run? Read the verdicts and the live log.
 * JsonNode stats  = melaya.agents().triggers().stats(id, 24);
 * JsonNode events = melaya.agents().triggers().events(id, null, List.of("filtered", "failed"), 50);
 *
 * // Dry-run a sample event: prefilter, decide and routing run, the action does not.
 * JsonNode test = melaya.agents().triggers().test(id, Map.of("subject", "Refund request"));
 *
 * // Poll triggers: see what the next poll would find, publishing nothing.
 * JsonNode dry = melaya.agents().triggers().pollTest(id);
 * }</pre>
 */
public class TriggersAPI {

    private static final String BASE = "/api/v1/private/triggers";

    private final HttpClient http;

    TriggersAPI(HttpClient http) {
        this.http = http;
    }

    // ── Triggers ──────────────────────────────────────────────────────────────

    /**
     * List the caller's event triggers.
     *
     * <p>Maps to {@code GET /api/v1/private/triggers?project=&pipelineName=}.
     * Returns an array of trigger records: {@code { id, publicId, name, kind,
     * project, pipelineName, enabled, pausedReason, signingScheme, sourceId,
     * config, maxEventsPerMin, maxRunsPerDay, maxConcurrentRuns,
     * consecutiveFailures, lastEventAt, createdAt, updatedAt, ... }}.
     * {@code config} depends on the trigger kind and is left as raw JSON.
     *
     * @param project      optional project filter; pass {@code null} to omit
     * @param pipelineName optional pipeline filter; pass {@code null} to omit
     */
    public JsonNode list(String project, String pipelineName) {
        return http.get(BASE, params("project", project, "pipelineName", pipelineName));
    }

    /**
     * Get one trigger with its config. Secrets are never included.
     *
     * <p>Maps to {@code GET /api/v1/private/triggers/{id}}.
     *
     * @param id the trigger ID (UUID)
     */
    public JsonNode get(String id) {
        return http.get(BASE + "/" + encode(id), null);
    }

    /**
     * Recent delivery receipts of a trigger, newest first.
     *
     * <p>Maps to {@code GET /api/v1/private/triggers/{id}/deliveries?limit=}.
     * Returns an array of {@code { id, triggerId, eventId, source, receivedAt,
     * verdict, decision, action, runId, detail, latencyMs, timings,
     * resultExcerpt, redelivered }}.
     *
     * @param id    the trigger ID (UUID)
     * @param limit optional page size, 1-200 (server default 50); may be {@code null}
     */
    public JsonNode deliveries(String id, Integer limit) {
        return http.get(BASE + "/" + encode(id) + "/deliveries", params("limit", limit));
    }

    /**
     * Delivery counts by verdict over a time window.
     *
     * <p>Maps to {@code GET /api/v1/private/triggers/{id}/stats?hours=}.
     * Returns {@code { hours, byVerdict: { [verdict]: { n, p50, p95 } }, filtered, sampled }}.
     * {@code filtered} is {@code null} when the counter store is unavailable.
     *
     * @param id    the trigger ID (UUID)
     * @param hours optional window, 1-168 (server default 24); may be {@code null}
     */
    public JsonNode stats(String id, Integer hours) {
        return http.get(BASE + "/" + encode(id) + "/stats", params("hours", hours));
    }

    /**
     * Approvals still waiting on this trigger's runs and tool calls.
     *
     * <p>Maps to {@code GET /api/v1/private/triggers/{id}/approvals}.
     * Returns an array of {@code { requestId, triggerId, deliveryId, eventId,
     * source, service, tool, argsPreview, createdAt, expiresAt }}
     * ({@code createdAt} and {@code expiresAt} are epoch milliseconds).
     * Approve or reject them in the Melaya app.
     *
     * @param id the trigger ID (UUID)
     */
    public JsonNode pendingApprovals(String id) {
        return http.get(BASE + "/" + encode(id) + "/approvals", null);
    }

    /**
     * Dry-run one event through the trigger: prefilter, decide and routing
     * run for real, but the action never executes. A test can never refund,
     * post, or start a run. The trigger's rate limits still apply.
     *
     * <p>Maps to {@code POST /api/v1/private/triggers/{id}/test} with body
     * {@code { payload }}. Returns {@code { accepted, eventId, reason? }};
     * {@code reason} is set when {@code accepted} is false (for example
     * {@code "disabled"} or {@code "rate_limited"}). Follow the outcome in
     * {@link #events} or {@link #deliveries} with the returned {@code eventId}.
     *
     * @param id      the trigger ID (UUID)
     * @param payload the sample event payload (any JSON-serializable value);
     *                {@code null} sends no payload and the server uses {@code {}}
     */
    public JsonNode test(String id, Object payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (payload != null) body.put("payload", payload);
        return http.post(BASE + "/" + encode(id) + "/test", body);
    }

    /**
     * Recent live trigger events, newest first. Includes outcomes that write
     * no receipt: filtered events, shed load, ingress rejections (signature,
     * rate limit, duplicate, size), feed refusals and push lifecycle notes.
     * The log keeps the last 500 events for 24 hours.
     *
     * <p>Maps to {@code GET /api/v1/private/triggers/events?triggerId=&since=&verdicts=&limit=}.
     * Returns {@code { events: [{ triggerId, deliveryId, eventId, source,
     * verdict, action?, runId?, detail?, latencyMs?, resultExcerpt?, at }],
     * scanned, retention: { maxEvents, ttlSec } }}. To follow the log, pass the
     * newest {@code at} you have seen as {@code since}.
     *
     * @param triggerId optional trigger ID to scope to; may be {@code null}
     * @param since     optional lower bound, epoch milliseconds; may be {@code null}
     * @param verdicts  optional verdict filter, sent as a comma list (for example
     *                  {@code "filtered"}, {@code "failed"}, {@code "dispatched"}); may be {@code null}
     * @param limit     optional page size, 1-200; may be {@code null}
     */
    public JsonNode events(String triggerId, Long since, Collection<String> verdicts, Integer limit) {
        String verdictList = verdicts == null || verdicts.isEmpty() ? null : String.join(",", verdicts);
        return http.get(BASE + "/events", params(
                "triggerId", triggerId,
                "since", since,
                "verdicts", verdictList,
                "limit", limit));
    }

    // ── Poll triggers ─────────────────────────────────────────────────────────

    /**
     * Runtime state of a poll trigger.
     *
     * <p>Maps to {@code GET /api/v1/private/triggers/{triggerId}/poll}.
     * Returns {@code { synced, status, lastError, lastPolledAt, nextPollAt,
     * armed, baselinePending, seenCount, itemsPublished, consecutiveErrors,
     * requestedIntervalSec, effectiveIntervalSec, tierFloorSec }}. When
     * {@code synced} is false the poller never started; see {@link #pollSync}.
     *
     * @param triggerId the trigger ID (UUID)
     * @throws MelayaException with status 400 if the trigger does not poll a connected app
     */
    public JsonNode pollStatus(String triggerId) {
        return http.get(BASE + "/" + encode(triggerId) + "/poll", null);
    }

    /**
     * Dry poll: call the poll tool now and report what it found and would
     * publish, publishing nothing.
     *
     * <p>Maps to {@code POST /api/v1/private/triggers/{triggerId}/poll/test}
     * with body {@code { dry: true }}. Returns {@code { dry: true, ok: true,
     * found, baseline, wouldPublish, items: [{ id, preview }], samplePayload }}.
     * When {@code baseline} is true, the first real poll records these items
     * and publishes nothing. Pass {@code samplePayload} to {@link #test} to
     * dry-run the rest of the trigger.
     *
     * <p>When the tool call fails, the server answers {@code 200} with
     * {@code { dry: true, ok: false, error }}. That result is returned as is,
     * not thrown, so you can read {@code error}. The server allows one dry
     * poll every 10 seconds per user (a {@code 429} is thrown as a
     * {@link MelayaException}).
     *
     * @param triggerId the trigger ID (UUID)
     */
    public JsonNode pollTest(String triggerId) {
        try {
            return http.post(BASE + "/" + encode(triggerId) + "/poll/test", Map.of("dry", true));
        } catch (MelayaException e) {
            // A failed dry poll is a diagnostic answer, not a request failure.
            if (e.getStatus() < 400 && e.getBody() instanceof JsonNode
                    && ((JsonNode) e.getBody()).path("dry").asBoolean(false)) {
                return (JsonNode) e.getBody();
            }
            throw e;
        }
    }

    /**
     * Queue a real poll now, ahead of the schedule. Any new items are
     * published and can start runs.
     *
     * <p>Maps to {@code POST /api/v1/private/triggers/{triggerId}/poll/test}
     * with body {@code { dry: false }}. Returns {@code { dry: false, queued }}.
     *
     * @param triggerId the trigger ID (UUID)
     * @throws MelayaException with status 412 if the trigger is turned off
     */
    public JsonNode pollNow(String triggerId) {
        return http.post(BASE + "/" + encode(triggerId) + "/poll/test", Map.of("dry", false));
    }

    /**
     * Re-create a poll trigger's runtime row from its saved config. Re-arms a
     * trigger whose poller never started.
     *
     * <p>Maps to {@code POST /api/v1/private/triggers/{triggerId}/poll/sync}.
     * Returns {@code { result }}.
     *
     * @param triggerId the trigger ID (UUID)
     */
    public JsonNode pollSync(String triggerId) {
        return http.post(BASE + "/" + encode(triggerId) + "/poll/sync", Map.of());
    }

    // ── Presets, limits, sources ──────────────────────────────────────────────

    /**
     * Trigger presets available to the caller, based on connected services
     * and plan.
     *
     * <p>Maps to {@code GET /api/v1/private/triggers/presets}.
     * Returns {@code { tier, tierFloorSec, presets: [...], beta }}.
     */
    public JsonNode presets() {
        return http.get(BASE + "/presets", null);
    }

    /**
     * Plan caps and current usage for triggers and sources, the poll
     * interval floor, and approval TTL bounds.
     *
     * <p>Maps to {@code GET /api/v1/private/triggers/limits}.
     * Returns {@code { tierClass, beta, triggers: { used, cap }, sources: { used, cap },
     * eventsPerMin: { perUser, perTriggerMax, perTriggerDefault },
     * pollIntervalFloorSec, approvalTtlSec: { min, max, default } }}.
     */
    public JsonNode limits() {
        return http.get(BASE + "/limits", null);
    }

    /**
     * List the caller's WebSocket / SSE stream sources.
     *
     * <p>Maps to {@code GET /api/v1/private/triggers/sources}. Returns an array.
     */
    public JsonNode sources() {
        return http.get(BASE + "/sources", null);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"); // path segment: space is %20, never +
        } catch (Exception e) {
            return s;
        }
    }
}
