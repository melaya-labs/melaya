<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Platform real-time events via Socket.IO v4 at /api/v1/events.
 *
 * PHP has no built-in async event loop, so this client operates in a
 * synchronous polling mode (Engine.IO HTTP long-poll transport). The caller
 * drives the loop with poll() / listen().
 *
 * Socket.IO room semantics (mirrors the server):
 *   - run:<runId>        — events for a specific pipeline run
 *   - project:<project>  — all events in a project
 *   - hitl:user:<userId> — HITL approval events (auto-joined by server on connect)
 *
 * @example
 * ```php
 * $sdk = new Melaya\Melaya(apiKey: getenv('MK'));
 * $events = $sdk->events;
 *
 * $events->connect();
 *
 * // Auto-joins run:run-123; the callback only fires for that run.
 * $events->onRunUpdate('run-123', function(array $payload) {
 *     echo $payload['event_type'] ?? '', PHP_EOL;
 * });
 *
 * // Drive the event loop (blocks for up to $timeoutSec per iteration)
 * for ($i = 0; $i < 30; $i++) {
 *     $events->poll();
 * }
 * $events->close();
 * ```
 *
 * Security: the API key is passed in the Authorization header and standard
 * Socket.IO auth payload. It is never placed in the URL or written to logs.
 */
class EventsClient
{
    /** Engine.IO session ID after handshake. */
    private ?string $sid = null;

    /** Registered event listeners: event => list<array{cb: callable, room: ?string}> */
    private array $listeners = [];

    /** Rooms currently joined (rejoined automatically after a re-handshake). */
    private array $joinedRooms = [];

    /** Ping interval in milliseconds returned by EIO handshake. */
    private int $pingIntervalMs = 25000;

    /** Whether the client is connected (sid != null). */
    private bool $connected = false;

    /** Total number of Socket.IO events dispatched since construction. */
    private int $dispatchCount = 0;

    public function __construct(
        private readonly string $apiKey,
        private readonly string $baseUrl = 'https://api.melaya.org',
        private readonly int    $pollTimeoutSec = 20,
    ) {}

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Perform the Engine.IO v4 handshake and send the Socket.IO connect packet.
     * Must be called before poll() or join methods.
     *
     * @throws MelayaException on handshake failure
     */
    public function connect(): void
    {
        // Step 1: Engine.IO GET handshake
        $raw = $this->pollGet();

        // EIO v4 batches packets with the 0x1e record separator; the open
        // packet is type '0' followed by a JSON payload.
        $open = null;
        foreach (explode("\x1e", $raw) as $packet) {
            if ($packet !== '' && $packet[0] === '0') {
                try {
                    $open = json_decode(substr($packet, 1), true, 512, JSON_THROW_ON_ERROR);
                } catch (\JsonException) {
                    $open = null;
                }
                break;
            }
        }
        if (!is_array($open)) {
            throw new MelayaException('MelayaEvents: unexpected EIO handshake response', 0);
        }
        $this->sid            = $open['sid'] ?? null;
        $this->pingIntervalMs = $open['pingInterval'] ?? 25000;

        if ($this->sid === null) {
            throw new MelayaException('MelayaEvents: no sid in EIO handshake response', 0);
        }

        // Step 2: Socket.IO connect packet with auth
        $authData = json_encode(['token' => $this->apiKey], JSON_THROW_ON_ERROR);
        $connectPacket = '40' . $authData; // EIO message (4) + SIO connect (0) + auth JSON
        $this->pollPost($connectPacket);

        $this->connected = true;

        // Re-join any rooms that were registered before connect()
        foreach ($this->joinedRooms as $room) {
            $this->sioEmitJoin($room);
        }
    }

    /** Close the event connection and release all listeners. */
    public function close(): void
    {
        $this->connected   = false;
        $this->sid         = null;
        $this->listeners   = [];
        $this->joinedRooms = [];
    }

    // ── Room management ───────────────────────────────────────────────────────

    /** Join the run:<runId> room to receive run-level events. */
    public function joinRun(string $runId): void
    {
        $this->joinRoom('run:' . $runId);
    }

    /** Leave the run:<runId> room. */
    public function leaveRun(string $runId): void
    {
        $this->leaveRoom('run:' . $runId);
    }

    /** Join the project:<project> room to receive all events in a project. */
    public function joinProject(string $project): void
    {
        $this->joinRoom('project:' . $project);
    }

    /** Leave a project room. */
    public function leaveProject(string $project): void
    {
        $this->leaveRoom('project:' . $project);
    }

    // ── Typed subscription helpers ────────────────────────────────────────────

    /**
     * Register a callback for run-level push events (event name: "pushEvent").
     * Auto-joins run:<runId>. The callback only fires for payloads whose runId
     * matches (payloads without a runId are delivered to all subscribers).
     */
    public function onRunUpdate(string $runId, callable $cb): void
    {
        $this->joinRun($runId);
        $this->on('pushEvent', $cb, 'run:' . $runId);
    }

    /**
     * Register a callback for run init-phase progress events (event name: "pushInitPhase").
     * Auto-joins run:<runId>. Scoped to that run's payloads.
     */
    public function onInitPhase(string $runId, callable $cb): void
    {
        $this->joinRun($runId);
        $this->on('pushInitPhase', $cb, 'run:' . $runId);
    }

    /**
     * Register a callback for all events in a project (event name: "pushEvent").
     * Auto-joins project:<project>. Scoped to that project's payloads.
     */
    public function onProjectEvent(string $project, callable $cb): void
    {
        $this->joinProject($project);
        $this->on('pushEvent', $cb, 'project:' . $project);
    }

    /**
     * Register a callback for HITL approval events (event name: "pushHitlApprovals").
     * The server auto-joins authenticated sockets to the hitl:user:<userId> room.
     */
    public function onHitlApproval(callable $cb): void
    {
        $this->on('pushHitlApprovals', $cb);
    }

    /** Register a callback for pipeline-created events within a project room. */
    public function onPipelineCreated(string $project, callable $cb): void
    {
        $this->joinProject($project);
        $this->on('pipelineCreated', $cb, 'project:' . $project);
    }

    /** Register a callback for pipeline-updated events within a project room. */
    public function onPipelineUpdated(string $project, callable $cb): void
    {
        $this->joinProject($project);
        $this->on('pipelineUpdated', $cb, 'project:' . $project);
    }

    /** Register a callback for pipeline-deleted events within a project room. */
    public function onPipelineDeleted(string $project, callable $cb): void
    {
        $this->joinProject($project);
        $this->on('pipelineDeleted', $cb, 'project:' . $project);
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    /**
     * Execute one HTTP long-poll cycle, dispatching any received events.
     * Call this in a loop to receive real-time events.
     *
     * @throws MelayaException if not connected (call connect() first)
     */
    public function poll(): void
    {
        if (!$this->connected || $this->sid === null) {
            throw new MelayaException('MelayaEvents: call connect() before poll()', 0);
        }

        try {
            $raw = $this->pollGet();
        } catch (MelayaException $e) {
            if ($e->status === 400) {
                // Engine.IO "Session ID unknown" — the session is dead.
                // Perform a full re-handshake; connect() rejoins all rooms.
                $this->reconnect();
                return;
            }
            throw $e;
        }
        $this->parsePollResponse($raw);
    }

    /**
     * Block and dispatch events until $timeoutSec total elapsed or $maxEvents received.
     *
     * @param int $maxEvents  Stop after this many events (0 = unlimited)
     * @param int $totalSec   Total wall-clock seconds to run
     */
    public function listen(int $maxEvents = 0, int $totalSec = 60): void
    {
        $deadline  = time() + $totalSec;
        $received  = 0;

        while (time() < $deadline) {
            try {
                $before = $this->dispatchCount;
                $this->poll();
                $received += $this->dispatchCount - $before;
                if ($maxEvents > 0 && $received >= $maxEvents) {
                    break;
                }
            } catch (MelayaException) {
                break;
            }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Register a raw Socket.IO event listener, optionally scoped to a room. */
    private function on(string $event, callable $cb, ?string $room = null): void
    {
        $this->listeners[$event][] = ['cb' => $cb, 'room' => $room];
    }

    private function emit(string $event, mixed $payload): void
    {
        $this->dispatchCount++;
        foreach ($this->listeners[$event] ?? [] as $entry) {
            if (!$this->matchesRoom($entry['room'], $payload)) {
                continue;
            }
            try {
                ($entry['cb'])($payload);
            } catch (\Throwable) {
                // Listener errors must not crash the client
            }
        }
    }

    /**
     * Room-scope filter (multi-room safety): when a listener is bound to a room
     * and the payload carries the corresponding ID, only dispatch on a match.
     * Payloads without an ID are dispatched to all listeners of that event.
     */
    private function matchesRoom(?string $room, mixed $payload): bool
    {
        if ($room === null || !is_array($payload)) {
            return true;
        }
        if (str_starts_with($room, 'run:')) {
            $runId = $payload['runId'] ?? $payload['run_id'] ?? null;
            return $runId === null || (string) $runId === substr($room, 4);
        }
        if (str_starts_with($room, 'project:')) {
            $project = $payload['projectId'] ?? $payload['project'] ?? null;
            return $project === null || (string) $project === substr($room, 8);
        }
        return true;
    }

    /** Tear down the dead session and perform a full Engine.IO re-handshake. */
    private function reconnect(): void
    {
        $this->connected = false;
        $this->sid       = null;
        $this->connect(); // rejoins $this->joinedRooms
    }

    private function joinRoom(string $room): void
    {
        if (in_array($room, $this->joinedRooms, true)) {
            return;
        }
        $this->joinedRooms[] = $room;
        if ($this->connected) {
            $this->sioEmitJoin($room);
        }
    }

    private function leaveRoom(string $room): void
    {
        // Remove from the rejoin-on-reconnect list
        $this->joinedRooms = array_values(array_filter(
            $this->joinedRooms,
            fn(string $r) => $r !== $room
        ));
        // Remove all listeners that were scoped to this room
        foreach ($this->listeners as $event => $entries) {
            $remaining = array_values(array_filter(
                $entries,
                static fn(array $e) => $e['room'] !== $room
            ));
            if ($remaining === []) {
                unset($this->listeners[$event]);
            } else {
                $this->listeners[$event] = $remaining;
            }
        }
        if ($this->connected) {
            // leaveRoom always passes the FULL room string to the server
            $this->sioEmit('leaveRoom', $room);
        }
    }

    /**
     * Emit the correct server join event for a room string.
     *
     * Server protocol (server/src/index.ts):
     *   "run:<id>"       → emit joinRunRoom(<bare runId>)
     *   "project:<name>" → emit joinProjectRoom(<bare project name>)
     *   anything else    → emit joinRunRoom(room) as a safe fallback
     */
    private function sioEmitJoin(string $room): void
    {
        if (str_starts_with($room, 'run:')) {
            $this->sioEmit('joinRunRoom', substr($room, 4));
        } elseif (str_starts_with($room, 'project:')) {
            $this->sioEmit('joinProjectRoom', substr($room, 8));
        } else {
            // Safe fallback — pass full string so server can decide
            $this->sioEmit('joinRunRoom', $room);
        }
    }

    /** Emit a Socket.IO event packet via HTTP POST. */
    private function sioEmit(string $event, mixed ...$args): void
    {
        if (!$this->connected) {
            return;
        }
        $packet = '42' . json_encode([$event, ...$args], JSON_THROW_ON_ERROR); // EIO msg (4) + SIO event (2)
        try {
            $this->pollPost($packet);
        } catch (MelayaException) {
            // Non-fatal — event will be replayed on reconnect
        }
    }

    /** Decode and dispatch a raw Engine.IO packet string. */
    private function handlePacket(string $raw): void
    {
        if ($raw === '') {
            return;
        }
        $type = $raw[0];

        // Engine.IO ping → respond with pong via POST
        if ($type === '2') {
            try {
                $this->pollPost('3');
            } catch (MelayaException) {
                // ignore
            }
            return;
        }

        // Only process EIO message packets (type '4')
        if ($type !== '4') {
            return;
        }

        // Socket.IO packet at index 1+
        $sioRaw  = substr($raw, 1);
        if ($sioRaw === '' || $sioRaw === false) {
            return;
        }
        $sioType = (int) $sioRaw[0];

        // SIO_EVENT = 2
        if ($sioType !== 2) {
            return;
        }

        $jsonPart = substr($sioRaw, 1);
        if ($jsonPart === '' || $jsonPart === false) {
            return;
        }

        try {
            $args = json_decode($jsonPart, true, 512, JSON_THROW_ON_ERROR);
        } catch (\JsonException) {
            return;
        }

        if (!is_array($args) || count($args) < 2) {
            return;
        }

        [$event, $payload] = [$args[0], $args[1]];
        $this->emit((string) $event, $payload);
    }

    /**
     * Parse a batched Engine.IO v4 poll response.
     * EIO v4 separates packets with the ASCII record separator (0x1e):
     * "<packet>\x1e<packet>\x1e..." — a lone packet has no separator.
     */
    private function parsePollResponse(string $text): void
    {
        foreach (explode("\x1e", $text) as $packet) {
            $this->handlePacket($packet);
        }
    }

    // ── Transport ─────────────────────────────────────────────────────────────

    private function pollUrl(array $extra = []): string
    {
        $base   = rtrim($this->baseUrl, '/');
        $params = [
            'EIO'       => '4',
            'transport' => 'polling',
        ];
        if ($this->sid !== null) {
            $params['sid'] = $this->sid;
        }
        $params = array_merge($params, $extra);
        return $base . '/api/v1/events/?' . http_build_query($params);
    }

    private function pollGet(): string
    {
        $ch = curl_init($this->pollUrl());
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT        => $this->pollTimeoutSec + 5,
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
            CURLOPT_HTTPHEADER     => [
                'Authorization: Bearer ' . $this->apiKey,
                'Accept: text/plain',
                'User-Agent: melaya-php-sdk/0.2.0',
            ],
        ]);

        $raw    = (string) curl_exec($ch);
        $status = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $err    = curl_error($ch);
        curl_close($ch);

        if ($err !== '') {
            throw new MelayaException('MelayaEvents: network error', 0);
        }
        if ($status >= 400) {
            throw new MelayaException('MelayaEvents: HTTP ' . $status, $status);
        }

        return $raw;
    }

    private function pollPost(string $body): void
    {
        $ch = curl_init($this->pollUrl());
        curl_setopt_array($ch, [
            CURLOPT_POST           => true,
            CURLOPT_POSTFIELDS     => $body,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT        => 10,
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
            CURLOPT_HTTPHEADER     => [
                'Authorization: Bearer ' . $this->apiKey,
                'Content-Type: text/plain;charset=UTF-8',
                'Content-Length: ' . strlen($body),
                'User-Agent: melaya-php-sdk/0.2.0',
            ],
        ]);

        $raw    = (string) curl_exec($ch);
        $status = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $err    = curl_error($ch);
        curl_close($ch);

        if ($err !== '') {
            throw new MelayaException('MelayaEvents: network error', 0);
        }
        if ($status >= 400) {
            throw new MelayaException('MelayaEvents: HTTP ' . $status, $status);
        }
    }
}
