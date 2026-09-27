<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Connector Tools API — call the SAME connector tools the Melaya app and MCP
 * server expose (Gmail, Slack, Stripe, …), directly over a plain REST call.
 *
 * Maps to /api/v1/private/connector-tools/*. Do NOT confuse this with
 * {@see ConnectorsAPI} (`$m->connectors`), which stores project-scoped
 * connector CREDENTIALS; this module CALLS the tools those credentials unlock.
 *
 * Write policy — the caller chooses per call, via `$approval` on {@see call()}:
 *   - `"required"` (default) — the write is staged as the same approval card
 *     the Assistant raises in the Melaya app. `call()` returns HTTP 202
 *     `{ status: "pending_approval", requestId, ... }` — that 202 IS the
 *     success response, not an error. Poll {@see callStatus()} (or use
 *     {@see callAndWait()}) after the user approves it in the app; the write
 *     then runs exactly once.
 *   - `"none"` — the write runs immediately and is audit-logged.
 * Reads always run immediately, under either mode.
 * Tools that move money or trade (`movesMoney: true`) are always refused,
 * under BOTH approval modes — they run only from the Melaya app.
 * No method here ever accepts or returns a credential value.
 *
 * @example
 * ```php
 * $sdk = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * $services = $sdk->agents->connectorTools->services();
 * $found    = $sdk->agents->connectorTools->search('unread email', 5);
 * $info     = $sdk->agents->connectorTools->describe('gmail_list_messages');
 *
 * // Read tool — runs immediately.
 * $result = $sdk->agents->connectorTools->call('gmail_list_messages');
 *
 * // Write tool — staged for approval by default.
 * $staged  = $sdk->agents->connectorTools->call('gmail_send', ['to' => 'a@b.com']);
 * // $staged === ['status' => 'pending_approval', 'tool' => ..., 'requestId' => ..., 'message' => ...]
 * $outcome = $sdk->agents->connectorTools->callStatus($staged['requestId']);
 *
 * // Or block until the approval is decided (or timeout):
 * $outcome = $sdk->agents->connectorTools->callAndWait('gmail_send', ['to' => 'a@b.com']);
 *
 * // Skip the approval card entirely (still audit-logged):
 * $sdk->agents->connectorTools->call('gmail_send', ['to' => 'a@b.com'], 'none');
 * ```
 */
class ConnectorToolsAPI
{
    private const BASE = '/api/v1/private/connector-tools';

    public function __construct(private readonly HttpClient $http) {}

    /**
     * Connected services, the always-available `melaya_core` built-in, and a
     * read/write tool count per service.
     *
     * @return array{services: string[], builtIn: string, toolCounts: array<string, array{readTools: int, writeTools: int}>}
     */
    public function services(): array
    {
        return $this->http->get(self::BASE . '/services');
    }

    /**
     * Discover tools by plain business keywords (e.g. "unread email", "refund a charge") —
     * not a tool name.
     *
     * @param string   $q     Required: plain business keywords.
     * @param int|null $limit 1–50; server default 15.
     * @return array{query: string, services: string[], tools: array[]}
     */
    public function search(string $q, ?int $limit = null): array
    {
        $query = array_filter(
            ['q' => $q, 'limit' => $limit],
            static fn($v) => $v !== null && $v !== '',
        );
        return $this->http->get(self::BASE . '/search', $query);
    }

    /**
     * Full description of one tool: params, whether it is read-only, and
     * whether it moves money. Throws (404) when the tool is unknown, or not
     * unlocked by the caller's connected services.
     */
    public function describe(string $tool): array
    {
        return $this->http->get(self::BASE . '/tools/' . rawurlencode($tool));
    }

    /** Test the STORED credential for a connected service (30 s server-side timeout). */
    public function test(string $service): array
    {
        return $this->http->post(self::BASE . '/test', ['service' => $service]);
    }

    /**
     * Start connecting a service. OAuth services return a URL the user opens;
     * interactive-login / api_key services point to the Connectors page.
     * Never accepts a secret.
     *
     * @return array{service: string, kind: string, authorizationUrl?: string, connectUrl?: string, message: string}
     */
    public function connect(string $service): array
    {
        return $this->http->post(self::BASE . '/connect', ['service' => $service]);
    }

    /**
     * Call a connector tool.
     *
     * A read tool (or a write with `$approval === 'none'`) runs immediately
     * and returns `{ status: "done", tool, readOnly, result }`.
     *
     * A write with the default `$approval === 'required'` stages the same
     * approval card the Assistant raises and returns HTTP 202
     * `{ status: "pending_approval", tool, requestId, message }` — that 202 IS
     * the success response, not an error; poll {@see callStatus()}, or use
     * {@see callAndWait()}, for the eventual outcome.
     *
     * Money-moving or trading tools are refused under both approval modes and
     * throw a {@see MelayaException} (`errorCode === "money_moving_requires_app_approval"`).
     *
     * @param string $tool     Tool name, e.g. "gmail_send".
     * @param array  $args     Tool arguments.
     * @param string $approval "required" (default) or "none".
     */
    public function call(string $tool, array $args = [], string $approval = 'required'): array
    {
        return $this->http->post(self::BASE . '/call', [
            'tool'     => $tool,
            'args'     => $args,
            'approval' => $approval,
        ]);
    }

    /**
     * Outcome of a staged write. `status` is one of `"pending"`, `"running"`,
     * `"expired"`, `"done"` (with `ok` + `result`/`error`), or `"rejected"`
     * (with an optional `reason`). Throws (404) once the request is unknown
     * or has already expired.
     */
    public function callStatus(string $requestId): array
    {
        return $this->http->get(self::BASE . '/calls/' . rawurlencode($requestId));
    }

    /**
     * Call a tool and, if it stages an approval, block — polling
     * {@see callStatus()} — until it resolves to `"done"`, `"rejected"`, or
     * `"expired"`, or the timeout elapses, whichever comes first. Returns the
     * last outcome seen either way: a timeout comes back as whatever status
     * was last polled (typically still `"pending"`), it is never thrown.
     *
     * A tool that runs immediately (a read, or a write with
     * `$approval === 'none'`) returns straight from {@see call()} without
     * polling at all.
     *
     * @param string $tool           Tool name.
     * @param array  $args           Tool arguments.
     * @param string $approval       "required" (default) or "none".
     * @param int    $pollIntervalMs Delay between polls. Default 3000 (3 s).
     * @param int    $timeoutMs      Give up after this long. Default 600000 (10 min).
     */
    public function callAndWait(
        string $tool,
        array $args = [],
        string $approval = 'required',
        int $pollIntervalMs = 3_000,
        int $timeoutMs = 600_000,
    ): array {
        $result = $this->call($tool, $args, $approval);
        if (($result['status'] ?? null) !== 'pending_approval') {
            return $result;
        }

        $requestId = (string) $result['requestId'];
        $deadline  = microtime(true) + ($timeoutMs / 1000);

        while (true) {
            $status = $this->callStatus($requestId);
            if (in_array($status['status'] ?? null, ['done', 'rejected', 'expired'], true)) {
                return $status;
            }
            if (microtime(true) >= $deadline) {
                return $status;
            }
            usleep($pollIntervalMs * 1000);
        }
    }
}
