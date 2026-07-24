package org.melaya

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Platform real-time events via Socket.IO v4 at `/api/v1/events`.
 *
 * Implements the Engine.IO v4 + Socket.IO v4 wire protocol using OkHttp's
 * WebSocket client. The connection strategy mirrors the TypeScript SDK:
 *
 *   1. Perform the Engine.IO HTTP long-poll handshake to obtain a `sid`.
 *   2. Post the Socket.IO connect packet with the Bearer token as auth data.
 *   3. Upgrade to WebSocket.
 *   4. Replay queued room joins (and reset the back-off counter).
 *   5. Reconnect with exponential back-off on failure or disconnect,
 *      logging each failure via `java.util.logging`.
 *
 * The connection is opened **lazily** on the first subscription or room join —
 * constructing [MelayaEvents] (or [Melaya]) performs no events I/O, so
 * REST-only usage never opens a socket. All internal threads are daemon
 * threads, so an open events connection does not prevent JVM exit; still call
 * [close] for a prompt, clean shutdown.
 *
 * Room semantics (server-side):
 *   - `run:<runId>`        — events for a specific pipeline run
 *   - `project:<project>`  — all events in a project (runs + pipeline CRUD)
 *   - `hitl:user:<userId>` — HITL approval events; joined automatically by the server
 *
 * @example
 * ```kotlin
 * val events = melaya.events
 * val unsubRun     = events.onRunUpdate("run-123") { e -> println(e.optString("event_type")) }
 * val unsubHitl    = events.onHitlApproval { e -> println("HITL: ${e.optString("type")}") }
 * val unsubProject = events.onProjectEvent("my-project") { e -> println(e) }
 *
 * // Clean up
 * unsubRun()
 * events.leaveRun("run-123")
 * events.close()
 * ```
 */
class MelayaEvents internal constructor(
    private val baseUrl: String,
    /** The `mk_` platform API key — sent as Bearer token in the connect packet. */
    private val apiKey: String,
    private val okHttp: OkHttpClient,
) : AutoCloseable {

    // ── Engine.IO / Socket.IO packet type constants ──────────────────────────

    private val EIO_OPEN    = '0'
    private val EIO_PING    = '2'
    private val EIO_PONG    = '3'
    private val EIO_MESSAGE = '4'

    private val SIO_CONNECT = 0
    private val SIO_EVENT   = 2

    // ── State ────────────────────────────────────────────────────────────────

    private val log = java.util.logging.Logger.getLogger(MelayaEvents::class.java.name)

    private val closed        = AtomicBoolean(false)
    private val started       = AtomicBoolean(false)
    private val sid           = AtomicReference<String?>(null)
    private val ws            = AtomicReference<WebSocket?>(null)
    private var reconnectAttempt = 0

    /** rooms currently joined (or pending join after connect) — replayed on reconnect */
    private val joinedRooms = ConcurrentHashMap.newKeySet<String>()

    /** event name → list of callbacks */
    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<(JSONObject) -> Unit>>()

    /** room → (event, callback) pairs registered through that room's on* helpers */
    private val roomSubs = ConcurrentHashMap<String, CopyOnWriteArrayList<Pair<String, (JSONObject) -> Unit>>>()

    // ── Public subscription API ───────────────────────────────────────────────

    /**
     * Subscribe to run events for a specific pipeline run.
     * Automatically joins the `run:<runId>` Socket.IO room.
     *
     * When the payload carries a `runId` it is filtered against [runId], so
     * subscribers to multiple run rooms never receive cross-room events.
     *
     * Returns an unsubscribe function.
     */
    fun onRunUpdate(runId: String, cb: (JSONObject) -> Unit): () -> Unit {
        joinRoom("run:$runId")
        return on("pushEvent", runFiltered(runId, cb), room = "run:$runId")
    }

    /**
     * Subscribe to init-phase progress events for a run.
     * Automatically joins the `run:<runId>` room.
     *
     * When the payload carries a `runId` it is filtered against [runId].
     *
     * Returns an unsubscribe function.
     */
    fun onInitPhase(runId: String, cb: (JSONObject) -> Unit): () -> Unit {
        joinRoom("run:$runId")
        return on("pushInitPhase", runFiltered(runId, cb), room = "run:$runId")
    }

    /**
     * Subscribe to all events in a project (all runs, pipeline CRUD).
     * Automatically joins the `project:<project>` room.
     *
     * When the payload carries a `projectId` it is filtered against [project],
     * so subscribers to multiple project rooms never receive cross-room events.
     *
     * Returns an unsubscribe function.
     */
    fun onProjectEvent(project: String, cb: (JSONObject) -> Unit): () -> Unit {
        joinRoom("project:$project")
        return on("pushEvent", projectFiltered(project, cb), room = "project:$project")
    }

    /**
     * Subscribe to HITL approval invalidation events.
     * The server automatically joins authenticated sockets to the
     * `hitl:user:<userId>` room on connect — no explicit join needed.
     *
     * Returns an unsubscribe function.
     */
    fun onHitlApproval(cb: (JSONObject) -> Unit): () -> Unit {
        return on("pushHitlApprovals", cb)
    }

    /** Subscribe to pipeline creation events within a project room. */
    fun onPipelineCreated(project: String, cb: (JSONObject) -> Unit): () -> Unit {
        joinRoom("project:$project")
        return on("pipelineCreated", projectFiltered(project, cb), room = "project:$project")
    }

    /** Subscribe to pipeline update events within a project room. */
    fun onPipelineUpdated(project: String, cb: (JSONObject) -> Unit): () -> Unit {
        joinRoom("project:$project")
        return on("pipelineUpdated", projectFiltered(project, cb), room = "project:$project")
    }

    /** Subscribe to pipeline deletion events within a project room. */
    fun onPipelineDeleted(project: String, cb: (JSONObject) -> Unit): () -> Unit {
        joinRoom("project:$project")
        return on("pipelineDeleted", projectFiltered(project, cb), room = "project:$project")
    }

    /**
     * Stop listening to a specific run: leaves the `run:<runId>` room, removes
     * every listener registered through that room's `on*` helpers, and drops
     * the room from the rejoin-on-reconnect list.
     */
    fun leaveRun(runId: String) = leaveRoom("run:$runId")

    /**
     * Leave a project room: removes every listener registered through that
     * room's `on*` helpers and drops the room from the rejoin-on-reconnect list.
     */
    fun leaveProject(project: String) = leaveRoom("project:$project")

    /** Close the Socket.IO connection and release all listeners. */
    override fun close() {
        closed.set(true)
        ws.getAndSet(null)?.close(1000, "client closed")
        listeners.clear()
        roomSubs.clear()
        joinedRooms.clear()
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Lazily open the Socket.IO connection on the first subscription or room
     * join. Constructing [MelayaEvents] performs no network I/O, so REST-only
     * usage never opens (or waits on) an events socket.
     */
    private fun ensureStarted() {
        if (closed.get()) return
        if (started.compareAndSet(false, true)) connect()
    }

    /** Wrap [cb] so it only fires when the payload's `runId` (if present) matches [runId]. */
    private fun runFiltered(runId: String, cb: (JSONObject) -> Unit): (JSONObject) -> Unit = { e ->
        val id = e.optString("runId", "")
        if (id.isEmpty() || id == runId) cb(e)
    }

    /** Wrap [cb] so it only fires when the payload's `projectId` (if present) matches [project]. */
    private fun projectFiltered(project: String, cb: (JSONObject) -> Unit): (JSONObject) -> Unit = { e ->
        val id = e.optString("projectId", "")
        if (id.isEmpty() || id == project) cb(e)
    }

    private fun on(event: String, cb: (JSONObject) -> Unit, room: String? = null): () -> Unit {
        ensureStarted()
        listeners.getOrPut(event) { CopyOnWriteArrayList() }.add(cb)
        if (room != null) roomSubs.getOrPut(room) { CopyOnWriteArrayList() }.add(event to cb)
        return {
            listeners[event]?.remove(cb)
            if (room != null) roomSubs[room]?.removeAll { it.second === cb }
        }
    }

    private fun emit(event: String, payload: JSONObject) {
        listeners[event]?.forEach { cb ->
            try { cb(payload) } catch (_: Exception) { /* listener errors must not crash the client */ }
        }
    }

    private fun handleSioEvent(args: JSONArray) {
        if (args.length() < 2) return
        val event   = args.optString(0) ?: return
        val payload = args.optJSONObject(1) ?: JSONObject()
        emit(event, payload)
    }

    private fun handlePacket(raw: String) {
        if (raw.isEmpty()) return
        when (raw[0]) {
            EIO_PING -> wsSend(EIO_PONG.toString())
            EIO_MESSAGE -> {
                val sioRaw = raw.drop(1)
                if (sioRaw.isEmpty()) return
                val sioType = sioRaw[0].digitToIntOrNull() ?: return
                if (sioType != SIO_EVENT) return
                try {
                    val args = JSONArray(sioRaw.drop(1))
                    handleSioEvent(args)
                } catch (_: Exception) { /* ignore malformed packets */ }
            }
            else -> { /* ignore */ }
        }
    }

    /**
     * Map a logical room string to the correct server event name + bare arg.
     *
     * Server contract (server/src/index.ts):
     *   "run:<runId>"       -> emit joinRunRoom(runId)
     *   "project:<project>" -> emit joinProjectRoom(project)
     *   anything else       -> no-op (hitl room is auto-joined by the server)
     */
    private fun emitJoin(room: String) {
        when {
            room.startsWith("run:")     -> sioEmit("joinRunRoom",     room.substring(4))
            room.startsWith("project:") -> sioEmit("joinProjectRoom", room.substring(8))
            // hitl:user:<userId> is auto-joined by the server on connect — no emit needed
        }
    }

    private fun joinRoom(room: String) {
        ensureStarted()
        if (joinedRooms.add(room) && sid.get() != null) {
            emitJoin(room)
        }
        // if not connected yet, joinedRooms will be replayed in replayJoins()
    }

    private fun leaveRoom(room: String) {
        joinedRooms.remove(room)  // no longer rejoined on reconnect
        // Drop every listener registered through this room's on* helpers.
        roomSubs.remove(room)?.forEach { (event, cb) -> listeners[event]?.remove(cb) }
        // Server expects leaveRoom with the FULL room string ("run:<runId>" / "project:<project>")
        if (sid.get() != null) sioEmit("leaveRoom", room)
    }

    private fun replayJoins() {
        joinedRooms.forEach { room -> emitJoin(room) }
    }

    private fun sioEmit(event: String, vararg args: Any) {
        val arr = JSONArray().put(event)
        args.forEach { arr.put(it) }
        wsSend("$EIO_MESSAGE$SIO_EVENT$arr")
    }

    private fun sioConnectPacket(): String {
        val authData = JSONObject().put("token", apiKey)
        return "$EIO_MESSAGE${SIO_CONNECT}$authData"
    }

    private fun wsSend(data: String) {
        ws.get()?.send(data)
    }

    // ── Connection lifecycle ──────────────────────────────────────────────────

    private fun pollUrl(extra: Map<String, String> = emptyMap()): String {
        val base = baseUrl.trimEnd('/')
        val sb = StringBuilder("$base/api/v1/events/?EIO=4&transport=polling")
        sid.get()?.let { sb.append("&sid=${enc(it)}") }
        extra.forEach { (k, v) -> sb.append("&${enc(k)}=${enc(v)}") }
        return sb.toString()
    }

    private fun wsUrl(): String {
        val base = baseUrl.trimEnd('/')
            .replace("^https://".toRegex(), "wss://")
            .replace("^http://".toRegex(),  "ws://")
        val sb = StringBuilder("$base/api/v1/events/?EIO=4&transport=websocket")
        sid.get()?.let { sb.append("&sid=${enc(it)}") }
        return sb.toString()
    }

    /** Bounded exponential backoff: 1s, 2s, 4s, 8s, … capped at 30s, with ±20% jitter. */
    private fun reconnectDelayMs(): Long {
        val base = minOf(1_000L shl minOf(reconnectAttempt, 5), 30_000L)
        val jitter = (base * 0.2 * Math.random()).toLong()
        reconnectAttempt++
        return base + jitter
    }

    /**
     * Log the failure and retry after a backoff delay on a daemon thread —
     * never blocks the calling (OkHttp) thread and never keeps the JVM alive.
     */
    private fun scheduleReconnect(reason: String) {
        if (closed.get()) return
        val delayMs = reconnectDelayMs()
        log.warning("Melaya events connection lost ($reason); reconnecting in ${delayMs}ms (attempt $reconnectAttempt)")
        Thread {
            try { Thread.sleep(delayMs) } catch (_: InterruptedException) { return@Thread }
            connect()
        }.also { it.isDaemon = true; it.name = "melaya-events-reconnect" }.start()
    }

    private fun connect() {
        if (closed.get()) return
        Thread {
            try {
                // Step 1: Engine.IO long-poll handshake
                val handshakeReq = Request.Builder()
                    .url(pollUrl())
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                val handshakeResp: Response = okHttp.newCall(handshakeReq).execute()
                val text = handshakeResp.body?.string() ?: throw IllegalStateException("Empty EIO handshake")
                val jsonStart = text.indexOf('{')
                if (jsonStart == -1) throw IllegalStateException("Unexpected EIO response: $text")
                val openData = JSONObject(text.substring(jsonStart))
                sid.set(openData.getString("sid"))

                // Step 2: Send SIO connect packet with auth
                val connectBody = sioConnectPacket()
                    .toRequestBody("text/plain;charset=UTF-8".toMediaType())
                val connectReq = Request.Builder()
                    .url(pollUrl())
                    .header("Authorization", "Bearer $apiKey")
                    .post(connectBody)
                    .build()
                okHttp.newCall(connectReq).execute().close()

                // Step 3: Upgrade to WebSocket
                if (!closed.get()) upgradeToWs()

                // Step 4: Replay pending room joins; a successful connection resets the backoff
                reconnectAttempt = 0
                replayJoins()

            } catch (e: Exception) {
                scheduleReconnect("${e.javaClass.simpleName}: ${e.message ?: "handshake failed"}")
            }
        }.also { it.isDaemon = true; it.name = "melaya-events-connect" }.start()
    }

    private fun upgradeToWs() {
        val req = Request.Builder()
            .url(wsUrl())
            .header("Authorization", "Bearer $apiKey")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Engine.IO WebSocket upgrade probe
                webSocket.send("2probe")
                webSocket.send("5")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handlePacket(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ws.compareAndSet(webSocket, null)
                if (!closed.get()) {
                    sid.set(null)
                    scheduleReconnect("websocket failure: ${t.javaClass.simpleName}")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ws.compareAndSet(webSocket, null)
                if (!closed.get()) {
                    sid.set(null)
                    scheduleReconnect("websocket closed (code=$code)")
                }
            }
        }

        ws.set(okHttp.newWebSocket(req, listener))
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

}
