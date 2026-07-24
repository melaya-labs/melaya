package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Platform real-time events via Socket.IO at {@code /api/v1/events}.
 *
 * <p>This client implements the Engine.IO v4 / Socket.IO v4 wire protocol
 * using an HTTP long-poll handshake followed by a WebSocket upgrade (mirroring
 * the TypeScript SDK {@code events.ts}).
 *
 * <p><b>Room semantics</b> (mirror the server):
 * <ul>
 *   <li>{@code run:<runId>} — subscribe to events for a specific pipeline run</li>
 *   <li>{@code project:<project>} — subscribe to all events in a project</li>
 *   <li>{@code hitl:user:<userId>} — HITL approval events (joined automatically
 *       by the server for the authenticated user on connect)</li>
 * </ul>
 *
 * @example
 * <pre>{@code
 * MelayaEvents events = melaya.events();
 * Runnable unsub = events.onRunUpdate("run-123", frame -> System.out.println(frame));
 * events.onHitlApproval(frame -> System.out.println("HITL: " + frame));
 * // ...
 * unsub.run();      // unsubscribe
 * events.close();   // disconnect
 * }</pre>
 */
public class MelayaEvents implements AutoCloseable {

    // Engine.IO v4 packet type characters
    private static final char EIO_OPEN    = '0';
    private static final char EIO_PING    = '2';
    private static final char EIO_PONG    = '3';
    private static final char EIO_MESSAGE = '4';

    // Socket.IO packet types
    private static final int SIO_CONNECT = 0;
    private static final int SIO_EVENT   = 2;

    private static final ObjectMapper MAPPER = HttpClient.MAPPER;
    private static final Logger LOG = Logger.getLogger(MelayaEvents.class.getName());
    /** Reconnect backoff steps (ms): 1 s, 2 s, 4 s, 8 s, cap at 30 s. */
    private static final long[] RECONNECT_BACKOFF_MS = {1_000, 2_000, 4_000, 8_000, 16_000, 30_000};
    private static final int CONNECT_RETRY_MS   = 5_000;

    /** Current reconnect attempt index, reset to 0 on successful connect. */
    private volatile int reconnectAttempt = 0;

    private final String apiKey;
    private final String baseUrl;          // e.g. https://api.melaya.org
    private final java.net.http.HttpClient httpClient;

    private volatile String sid = null;
    private volatile WebSocketClient ws = null;
    private volatile boolean closed = false;
    /** Set once the first subscription triggers the lazy connect. */
    private final AtomicBoolean connectRequested = new AtomicBoolean(false);

    private final Set<String> joinedRooms = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ConcurrentHashMap<String, Set<Consumer<JsonNode>>> listeners = new ConcurrentHashMap<>();
    /** Per-room unsubscribe handles so leaveRun/leaveProject can drop that room's listeners. */
    private final ConcurrentHashMap<String, Set<Runnable>> roomUnsubs = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "melaya-events");
        t.setDaemon(true);
        return t;
    });

    /**
     * Create a MelayaEvents client.
     *
     * <p>Constructing the client does <b>not</b> open a network connection.
     * The Engine.IO handshake is initiated lazily on the first subscription
     * ({@code on*}/join) call. All internal threads are daemon threads, so an
     * idle client never keeps the JVM alive.
     *
     * @param apiKey  your {@code mk_} platform API key
     * @param baseUrl REST base URL (e.g. {@code https://api.melaya.org})
     */
    public MelayaEvents(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = java.net.http.HttpClient.newBuilder()
                .build();
        // Lazy: no connection is opened until the first on*/join call.
    }

    // ── Public subscription API ───────────────────────────────────────────────

    /**
     * Subscribe to run events for a specific pipeline run.
     * Automatically joins the {@code run:<runId>} Socket.IO room.
     *
     * <p>When the payload carries a {@code runId}, events for other runs are
     * filtered out, so subscribing to several runs at once never cross-delivers.
     *
     * @param runId the pipeline run ID
     * @param cb    callback invoked on each {@code pushEvent} for this run
     * @return an unsubscribe {@link Runnable} — call it to remove the listener
     */
    public Runnable onRunUpdate(String runId, Consumer<JsonNode> cb) {
        return onRoom("run:" + runId, "pushEvent", filterById("runId", "run_id", runId, cb));
    }

    /**
     * Subscribe to init-phase progress events for a run.
     * Automatically joins the {@code run:<runId>} room. Payloads carrying a
     * {@code runId} for a different run are filtered out.
     *
     * @param runId the pipeline run ID
     * @param cb    callback invoked on each {@code pushInitPhase} event
     * @return an unsubscribe {@link Runnable}
     */
    public Runnable onInitPhase(String runId, Consumer<JsonNode> cb) {
        return onRoom("run:" + runId, "pushInitPhase", filterById("runId", "run_id", runId, cb));
    }

    /**
     * Subscribe to all events in a project (all runs, pipeline CRUD).
     * Automatically joins the {@code project:<project>} room. Payloads carrying
     * a {@code project} field for a different project are filtered out.
     *
     * @param project the project name
     * @param cb      callback invoked on each {@code pushEvent} or pipeline CRUD event
     * @return an unsubscribe {@link Runnable}
     */
    public Runnable onProjectEvent(String project, Consumer<JsonNode> cb) {
        return onRoom("project:" + project, "pushEvent", filterById("project", null, project, cb));
    }

    /**
     * Subscribe to HITL approval invalidation events.
     * The server joins authenticated sockets to the HITL room automatically on connect.
     *
     * @param cb callback invoked on each {@code pushHitlApprovals} event
     * @return an unsubscribe {@link Runnable}
     */
    public Runnable onHitlApproval(Consumer<JsonNode> cb) {
        return on("pushHitlApprovals", cb);
    }

    /** Subscribe to pipeline-created events within a project room. */
    public Runnable onPipelineCreated(String project, Consumer<JsonNode> cb) {
        return onRoom("project:" + project, "pipelineCreated", filterById("project", null, project, cb));
    }

    /** Subscribe to pipeline-updated events within a project room. */
    public Runnable onPipelineUpdated(String project, Consumer<JsonNode> cb) {
        return onRoom("project:" + project, "pipelineUpdated", filterById("project", null, project, cb));
    }

    /** Subscribe to pipeline-deleted events within a project room. */
    public Runnable onPipelineDeleted(String project, Consumer<JsonNode> cb) {
        return onRoom("project:" + project, "pipelineDeleted", filterById("project", null, project, cb));
    }

    /**
     * Leave a run room and stop receiving its events.
     *
     * @param runId the pipeline run ID
     */
    public void leaveRun(String runId) {
        leaveRoom("run:" + runId);
    }

    /**
     * Leave a project room.
     *
     * @param project the project name
     */
    public void leaveProject(String project) {
        leaveRoom("project:" + project);
    }

    /** Close the Socket.IO connection and release all listeners. */
    @Override
    public void close() {
        closed = true;
        scheduler.shutdownNow();
        WebSocketClient w = ws;
        if (w != null) {
            try { w.closeBlocking(); } catch (Exception ignored) {}
        }
        ws = null;
        listeners.clear();
        joinedRooms.clear();
        roomUnsubs.clear();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Register a raw Socket.IO event listener. Returns an unsubscribe Runnable. */
    private Runnable on(String event, Consumer<JsonNode> cb) {
        ensureConnect();
        listeners.computeIfAbsent(event, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(cb);
        return () -> {
            Set<Consumer<JsonNode>> set = listeners.get(event);
            if (set != null) set.remove(cb);
        };
    }

    /**
     * Join a room, register a listener, and track the unsubscribe handle per
     * room so {@link #leaveRun}/{@link #leaveProject} can remove it.
     */
    private Runnable onRoom(String room, String event, Consumer<JsonNode> cb) {
        joinRoom(room);
        Runnable unsub = on(event, cb);
        Set<Runnable> set = roomUnsubs.computeIfAbsent(
                room, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        Runnable tracked = new Runnable() {
            @Override public void run() {
                unsub.run();
                Set<Runnable> s = roomUnsubs.get(room);
                if (s != null) s.remove(this);
            }
        };
        set.add(tracked);
        return tracked;
    }

    /**
     * Wrap a callback so payloads that carry an ID field ({@code idField} or
     * the optional {@code altIdField}) not matching {@code expected} are dropped.
     * Payloads without the field pass through unchanged, so multi-room
     * subscribers never receive cross-room events.
     */
    private static Consumer<JsonNode> filterById(
            String idField, String altIdField, String expected, Consumer<JsonNode> cb) {
        return payload -> {
            if (payload != null && payload.isObject()) {
                JsonNode id = payload.get(idField);
                if ((id == null || id.isNull()) && altIdField != null) id = payload.get(altIdField);
                if (id != null && !id.isNull() && !expected.equals(id.asText())) return;
            }
            cb.accept(payload);
        };
    }

    private void emit(String event, JsonNode payload) {
        Set<Consumer<JsonNode>> set = listeners.get(event);
        if (set == null) return;
        for (Consumer<JsonNode> l : set) {
            try { l.accept(payload); } catch (Exception ignored) {}
        }
    }

    /** Decode a Socket.IO event array and dispatch. */
    private void handleSioEvent(String sioRaw) {
        // sioRaw starts with "2" followed by JSON array: ["eventName", payload]
        try {
            String json = sioRaw.substring(1); // strip SIO type digit
            JsonNode arr = MAPPER.readTree(json);
            if (!arr.isArray() || arr.size() < 2) return;
            String eventName = arr.get(0).asText();
            JsonNode payload = arr.get(1);
            emit(eventName, payload);
        } catch (Exception ignored) {}
    }

    /** Handle a single Engine.IO packet string. */
    private void handlePacket(String raw) {
        if (raw == null || raw.isEmpty()) return;
        char type = raw.charAt(0);
        if (type == EIO_PING) {
            wsSend(String.valueOf(EIO_PONG));
            return;
        }
        if (type != EIO_MESSAGE) return;
        String sioRaw = raw.substring(1);
        if (sioRaw.isEmpty()) return;
        int sioType;
        try { sioType = Integer.parseInt(String.valueOf(sioRaw.charAt(0))); }
        catch (NumberFormatException e) { return; }

        if (sioType == SIO_EVENT && sioRaw.length() > 1) {
            handleSioEvent(sioRaw);
        }
        // SIO_CONNECT (0) is handled implicitly by the connect flow
    }

    /** Parse Engine.IO concatenated poll response ({@code "<len>:<packet>..."} format). */
    private void parsePollResponse(String text) {
        int i = 0;
        while (i < text.length()) {
            int colon = text.indexOf(':', i);
            if (colon == -1) { handlePacket(text.substring(i)); break; }
            int len;
            try { len = Integer.parseInt(text, i, colon, 10); }
            catch (NumberFormatException e) { handlePacket(text.substring(i)); break; }
            String packet = text.substring(colon + 1, Math.min(colon + 1 + len, text.length()));
            handlePacket(packet);
            i = colon + 1 + len;
        }
    }

    /**
     * Emit the correct server-side join event for a room string.
     * <ul>
     *   <li>{@code run:<id>}     → emit {@code joinRunRoom} with bare {@code <id>}</li>
     *   <li>{@code project:<p>}  → emit {@code joinProjectRoom} with bare {@code <p>}</li>
     *   <li>anything else        → emit {@code joinRunRoom} with the full string (safe fallback)</li>
     * </ul>
     */
    private void emitJoin(String room) {
        if (room.startsWith("run:")) {
            sioEmit("joinRunRoom", room.substring(4));
        } else if (room.startsWith("project:")) {
            sioEmit("joinProjectRoom", room.substring(8));
        } else {
            sioEmit("joinRunRoom", room); // safe fallback
        }
    }

    private void joinRoom(String room) {
        ensureConnect();
        if (joinedRooms.add(room) && sid != null) {
            emitJoin(room);
        }
    }

    private void leaveRoom(String room) {
        // Remove from the rejoin-on-reconnect list …
        joinedRooms.remove(room);
        // … drop every listener registered for this room …
        Set<Runnable> unsubs = roomUnsubs.remove(room);
        if (unsubs != null) {
            for (Runnable r : unsubs) {
                try { r.run(); } catch (Exception ignored) {}
            }
        }
        // … and tell the server. Server expects leaveRoom with the FULL room
        // string ("run:<id>" or "project:<p>").
        if (sid != null) sioEmit("leaveRoom", room);
    }

    private void replayJoins() {
        for (String room : joinedRooms) {
            emitJoin(room);
        }
    }

    private void wsSend(String data) {
        WebSocketClient w = ws;
        if (w != null && w.isOpen()) {
            try { w.send(data); } catch (Exception ignored) {}
        }
    }

    /** Build a Socket.IO event packet string. */
    private void sioEmit(String event, Object... args) {
        try {
            List<Object> list = new ArrayList<>();
            list.add(event);
            Collections.addAll(list, args);
            String json = MAPPER.writeValueAsString(list);
            wsSend(EIO_MESSAGE + "" + SIO_EVENT + json);
        } catch (Exception ignored) {}
    }

    /** Build the Socket.IO connect packet with Bearer auth. */
    private String sioConnectPacket() {
        try {
            String authJson = MAPPER.writeValueAsString(
                    Collections.singletonMap("token", apiKey));
            return EIO_MESSAGE + "" + SIO_CONNECT + authJson;
        } catch (Exception e) {
            return EIO_MESSAGE + "" + SIO_CONNECT + "{\"auth\":{}}";
        }
    }

    private String pollUrl() {
        return baseUrl + "/api/v1/events/?EIO=4&transport=polling"
                + (sid != null ? "&sid=" + URLEncoder.encode(sid, StandardCharsets.UTF_8) : "");
    }

    private String wsUrl() {
        String wsBase = baseUrl.replace("https://", "wss://").replace("http://", "ws://");
        return wsBase + "/api/v1/events/?EIO=4&transport=websocket"
                + (sid != null ? "&sid=" + URLEncoder.encode(sid, StandardCharsets.UTF_8) : "");
    }

    /** Kick off the lazy connect exactly once, on the first subscription/join. */
    private void ensureConnect() {
        if (!closed && connectRequested.compareAndSet(false, true)) {
            scheduleConnect(0);
        }
    }

    private void scheduleConnect(long delayMs) {
        if (closed) return;
        scheduler.schedule(this::connect, delayMs, TimeUnit.MILLISECONDS);
    }

    private void connect() {
        if (closed) return;
        try {
            // Step 1: Engine.IO HTTP handshake
            HttpRequest handshake = HttpRequest.newBuilder()
                    .uri(URI.create(pollUrl()))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(handshake, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String text = resp.body();
            int jsonStart = text.indexOf('{');
            if (jsonStart == -1) throw new IllegalStateException("Unexpected EIO handshake: " + text);
            JsonNode openData = MAPPER.readTree(text.substring(jsonStart));
            sid = openData.get("sid").asText();

            // Step 2: Socket.IO connect packet via POST
            HttpRequest connectPost = HttpRequest.newBuilder()
                    .uri(URI.create(pollUrl()))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "text/plain;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(sioConnectPacket(), StandardCharsets.UTF_8))
                    .build();
            httpClient.send(connectPost, HttpResponse.BodyHandlers.discarding());

            // Step 3: Upgrade to WebSocket
            upgradeToWs();

            // Replay queued room joins
            replayJoins();

        } catch (Exception e) {
            sid = null;
            if (!closed) {
                LOG.log(Level.WARNING, "Melaya events: connect failed ("
                        + e.getClass().getSimpleName() + ": " + e.getMessage()
                        + "); retrying in " + CONNECT_RETRY_MS + " ms");
                scheduleConnect(CONNECT_RETRY_MS);
            }
        }
    }

    private void upgradeToWs() {
        if (closed) return;
        String url = wsUrl();
        WebSocketClient client;
        boolean connected;
        try {
            client = new WebSocketClient(new URI(url)) {
                @Override public void onOpen(ServerHandshake h) {
                    // Engine.IO WebSocket upgrade probe
                    send("2probe");
                    send("5"); // upgrade packet
                }
                @Override public void onMessage(String message) {
                    handlePacket(message);
                }
                @Override public void onClose(int code, String reason, boolean remote) {
                    ws = null;
                    if (!closed) {
                        sid = null;
                        long delay = RECONNECT_BACKOFF_MS[
                                Math.min(reconnectAttempt, RECONNECT_BACKOFF_MS.length - 1)];
                        reconnectAttempt++;
                        LOG.log(Level.WARNING, "Melaya events: WebSocket closed (code=" + code
                                + ", remote=" + remote + "); reconnecting in " + delay + " ms");
                        scheduleConnect(delay);
                    }
                }
                @Override public void onError(Exception ex) {
                    // onClose follows — reconnect is scheduled there
                    LOG.log(Level.WARNING, "Melaya events: WebSocket error ("
                            + ex.getClass().getSimpleName() + ": " + ex.getMessage() + ")");
                }
            };
            // Daemon: the WebSocket read/write threads must never keep the JVM alive.
            client.setDaemon(true);
            connected = client.connectBlocking(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (!closed) {
                long delay = RECONNECT_BACKOFF_MS[
                        Math.min(reconnectAttempt, RECONNECT_BACKOFF_MS.length - 1)];
                reconnectAttempt++;
                LOG.log(Level.WARNING, "Melaya events: WebSocket upgrade failed ("
                        + e.getClass().getSimpleName() + ": " + e.getMessage()
                        + "); reconnecting in " + delay + " ms");
                scheduleConnect(delay);
            }
            return;
        }
        if (!connected) {
            // connectBlocking timed out or the handshake was rejected
            if (!closed) {
                long delay = RECONNECT_BACKOFF_MS[
                        Math.min(reconnectAttempt, RECONNECT_BACKOFF_MS.length - 1)];
                reconnectAttempt++;
                LOG.log(Level.WARNING,
                        "Melaya events: WebSocket did not open within 10 s; reconnecting in " + delay + " ms");
                scheduleConnect(delay);
            }
            return;
        }
        reconnectAttempt = 0; // successful upgrade — reset backoff
        ws = client;
    }
}
