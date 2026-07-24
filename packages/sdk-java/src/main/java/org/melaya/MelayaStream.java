package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;

/**
 * A live stream of normalized JSON frames from a Melaya WebSocket endpoint.
 *
 * <p>Usage:
 * <pre>{@code
 * try (MelayaStream stream = melaya.stream().ticker("binance", "BTC/USDT", "spot")) {
 *     JsonNode frame = stream.nextFrame(10, TimeUnit.SECONDS); // null on timeout
 * }
 * }</pre>
 */
public class MelayaStream implements AutoCloseable {

    private static final ObjectMapper MAPPER = HttpClient.MAPPER;
    private static final JsonNode POISON = MAPPER.getNodeFactory().nullNode();

    private final BlockingQueue<JsonNode> queue = new LinkedBlockingQueue<>();
    private final List<Consumer<JsonNode>> messageListeners = new ArrayList<>();
    private volatile boolean closed = false;
    /** Last WebSocket error, rethrown by the next {@link #nextFrame} call. */
    private volatile Exception pendingError = null;

    private final WebSocketClient ws;

    MelayaStream(String url) {
        WebSocketClient client;
        try {
            client = new WebSocketClient(new URI(url)) {
                @Override
                public void onOpen(ServerHandshake handshake) { /* connected */ }

                @Override
                public void onMessage(String message) {
                    if (closed) return;
                    JsonNode frame;
                    try {
                        frame = MAPPER.readTree(message);
                    } catch (Exception e) {
                        return; // ignore non-JSON keep-alive frames
                    }
                    for (Consumer<JsonNode> l : messageListeners) {
                        try { l.accept(frame); } catch (Exception ignored) {}
                    }
                    queue.offer(frame);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    if (!closed) {
                        closed = true;
                        queue.offer(POISON);
                    }
                }

                @Override
                public void onError(Exception ex) {
                    // stored and rethrown by the next nextFrame() call
                    pendingError = ex;
                }
            };

            boolean connected = client.connectBlocking(10, TimeUnit.SECONDS);
            if (!connected) {
                throw new RuntimeException(
                        "Failed to open WebSocket (not connected within 10 s): " + sanitize(url));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to open WebSocket: " + sanitize(url), e);
        }
        this.ws = client;
    }

    /** Strip the query string (which may carry {@code apiKey}/{@code wsTicket}) from URLs used in messages. */
    private static String sanitize(String url) {
        int q = url.indexOf('?');
        return q == -1 ? url : url.substring(0, q);
    }

    /**
     * Block until the next frame arrives or the timeout elapses.
     *
     * @return the next frame, or {@code null} on timeout
     * @throws InterruptedException if the thread is interrupted
     * @throws RuntimeException     if the WebSocket reported an error since the
     *                              last call — the underlying exception is the cause
     */
    public JsonNode nextFrame(long timeout, TimeUnit unit) throws InterruptedException {
        throwPendingError();
        JsonNode frame = queue.poll(timeout, unit);
        if (frame == null || frame == POISON) {
            throwPendingError();
            return null;
        }
        return frame;
    }

    private void throwPendingError() {
        Exception err = pendingError;
        if (err != null) {
            pendingError = null;
            throw new RuntimeException("WebSocket error: " + err.getMessage(), err);
        }
    }

    /** Register a listener that is called on every incoming frame. */
    public void onMessage(Consumer<JsonNode> listener) {
        messageListeners.add(listener);
    }

    /** Close the underlying WebSocket connection. */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try { ws.closeBlocking(); } catch (Exception ignored) {}
            queue.offer(POISON);
        }
    }

    public boolean isClosed() {
        return closed;
    }
}
