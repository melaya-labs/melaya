<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Triggers API: read, diagnose and dry-run the event triggers that start your
 * pipelines. Maps to /api/v1/private/triggers/*.
 *
 * Read-only apart from the dry runs and poll controls. Creating, updating,
 * deleting a trigger and rotating its signing secret stay in the Agent Builder
 * and the MCP server, where push consent and autonomy grants are enforced. No
 * method here returns a signing secret.
 *
 * {@see test()} and {@see pollTest()} are dry runs: the trigger's action never
 * executes. {@see pollNow()} queues a real poll.
 *
 * @example
 * ```php
 * $m = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * $triggers = $m->agents->triggers->list(['project' => 'support']);
 * $stats    = $m->agents->triggers->stats($triggers[0]['id'], 24);
 *
 * // What happened recently, including events that wrote no receipt.
 * $live = $m->agents->triggers->events(['verdicts' => ['rejected', 'failed'], 'limit' => 50]);
 *
 * // Dry run one event (the action never runs).
 * $test = $m->agents->triggers->test($triggers[0]['id'], ['type' => 'ping']);
 * // $test === ['accepted' => true, 'eventId' => 'test-...']
 * ```
 */
class TriggersAPI
{
    private const BASE = '/api/v1/private/triggers';

    public function __construct(private readonly HttpClient $http) {}

    // ── Triggers ─────────────────────────────────────────────────────────────

    /**
     * List your event triggers.
     *
     * @param array{project?: string, pipelineName?: string} $params Optional filters.
     * @return array<int, array<string, mixed>> Trigger records: id, publicId, name, kind,
     *         project, pipelineName, enabled, pausedReason, signingScheme, sourceId, config,
     *         rate caps, consecutiveFailures, lastEventAt, createdAt, updatedAt, webhookUrl,
     *         projectAccess.
     */
    public function list(array $params = []): array
    {
        return $this->http->get(self::BASE, $params);
    }

    /**
     * One trigger with its config. Secrets are never included.
     *
     * @param string $id Trigger id (will be rawurlencoded).
     */
    public function get(string $id): array
    {
        return $this->http->get(self::BASE . '/' . rawurlencode($id));
    }

    /**
     * Recent deliveries (receipts) of a trigger: verdict, decision answers,
     * action, run id and timings.
     *
     * @param string   $id    Trigger id.
     * @param int|null $limit 1-200; server default 50.
     */
    public function deliveries(string $id, ?int $limit = null): array
    {
        return $this->http->get(self::BASE . '/' . rawurlencode($id) . '/deliveries', ['limit' => $limit]);
    }

    /**
     * Delivery counts by verdict over a window.
     *
     * @param string   $id    Trigger id.
     * @param int|null $hours 1-168; server default 24.
     * @return array{hours: int, byVerdict: array<string, array{n: int, p50: ?float, p95: ?float}>, filtered: ?int, sampled: bool}
     */
    public function stats(string $id, ?int $hours = null): array
    {
        return $this->http->get(self::BASE . '/' . rawurlencode($id) . '/stats', ['hours' => $hours]);
    }

    /**
     * Approvals still waiting on this trigger's runs and tool calls. Read only:
     * decide them in the Melaya app.
     *
     * @param string $id Trigger id.
     */
    public function pendingApprovals(string $id): array
    {
        return $this->http->get(self::BASE . '/' . rawurlencode($id) . '/approvals');
    }

    /**
     * Dry run one event through the prefilter, the decide step and routing.
     * The action never executes. The trigger's rate limits still apply, so a
     * refused test comes back with `accepted: false` and a `reason`
     * (`disabled`, `rate_limited`, ...). Follow the outcome with
     * {@see events()} for this trigger.
     *
     * @param string $id      Trigger id.
     * @param mixed  $payload The event payload to test with. Omit for an empty object.
     * @return array{accepted: bool, eventId: string, reason?: string}
     */
    public function test(string $id, mixed $payload = null): array
    {
        $body = $payload === null ? new \stdClass() : ['payload' => $payload];
        return $this->http->post(self::BASE . '/' . rawurlencode($id) . '/test', $body);
    }

    /**
     * Recent live trigger events, newest first (the last 500 events or 24
     * hours), including the outcomes that write no receipt: filtered, shed,
     * ingress rejections, feed refusals and push lifecycle notes. To follow new
     * events, pass `since` = the newest `at` already seen.
     *
     * @param array{triggerId?: string, since?: int, verdicts?: string[]|string, limit?: int} $params
     *        `since` is epoch milliseconds; `verdicts` is a list (or a comma list) such as
     *        `['rejected', 'filtered', 'failed']`; `limit` is 1-200.
     * @return array{events: array<int, array<string, mixed>>, scanned: int, retention: array{maxEvents: int, ttlSec: int}}
     */
    public function events(array $params = []): array
    {
        if (isset($params['verdicts']) && is_array($params['verdicts'])) {
            $params['verdicts'] = implode(',', $params['verdicts']);
        }
        return $this->http->get(self::BASE . '/events', $params);
    }

    // ── Poll triggers ────────────────────────────────────────────────────────

    /**
     * Runtime state of a poll trigger: synced, status, lastError, lastPolledAt,
     * nextPollAt, armed, baselinePending, seenCount, itemsPublished,
     * consecutiveErrors, requestedIntervalSec, effectiveIntervalSec, tierFloorSec.
     *
     * @param string $triggerId Trigger id.
     */
    public function pollStatus(string $triggerId): array
    {
        return $this->http->get(self::BASE . '/' . rawurlencode($triggerId) . '/poll');
    }

    /**
     * Dry poll: calls the poll tool now and returns what it found and would
     * publish. Nothing is published. A poll that fails is returned as
     * `['dry' => true, 'ok' => false, 'error' => code]` instead of thrown. Dry
     * polls are throttled to one every few seconds (`poll_dry_run_throttled`,
     * HTTP 429).
     *
     * @param string $triggerId Trigger id.
     * @return array{dry: true, ok: bool, error?: string, found?: int, baseline?: bool, wouldPublish?: int, items?: array<int, array{id: string, preview: string}>, samplePayload?: mixed}
     */
    public function pollTest(string $triggerId): array
    {
        try {
            return $this->http->post(self::BASE . '/' . rawurlencode($triggerId) . '/poll/test', ['dry' => true]);
        } catch (MelayaException $e) {
            // A failed dry poll is a normal 200 result, not a request failure.
            if ($e->status < 400 && is_array($e->body) && array_key_exists('dry', $e->body)) {
                return $e->body;
            }
            throw $e;
        }
    }

    /**
     * Queue a real poll now. The trigger must be enabled (`trigger_disabled`
     * otherwise). Follow the outcome with {@see pollStatus()} and {@see events()}.
     *
     * @param string $triggerId Trigger id.
     * @return array{dry: false, queued: bool}
     */
    public function pollNow(string $triggerId): array
    {
        return $this->http->post(self::BASE . '/' . rawurlencode($triggerId) . '/poll/test', ['dry' => false]);
    }

    /**
     * Re-create a poll trigger's runtime row from its saved config. Re-arms a
     * trigger whose poller never started.
     *
     * @param string $triggerId Trigger id.
     * @return array{result: mixed}
     */
    public function pollSync(string $triggerId): array
    {
        return $this->http->post(self::BASE . '/' . rawurlencode($triggerId) . '/poll/sync', new \stdClass());
    }

    // ── Account-wide ─────────────────────────────────────────────────────────

    /**
     * Trigger presets available to you, given your connected services and plan.
     *
     * @return array{tier: string, tierFloorSec: int, presets: array<int, array<string, mixed>>, beta: mixed}
     */
    public function presets(): array
    {
        return $this->http->get(self::BASE . '/presets');
    }

    /**
     * Plan caps and usage: triggers and sources used against the cap, events
     * per minute, poll interval floor and approval TTL bounds.
     */
    public function limits(): array
    {
        return $this->http->get(self::BASE . '/limits');
    }

    /** Your WebSocket and SSE stream sources. */
    public function sources(): array
    {
        return $this->http->get(self::BASE . '/sources');
    }
}
