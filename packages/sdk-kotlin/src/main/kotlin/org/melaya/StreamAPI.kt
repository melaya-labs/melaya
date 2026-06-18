package org.melaya

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A live stream of JSON frames from a Melaya WebSocket endpoint.
 *
 * Iterate with a callback via [forEach], poll individual frames with [poll],
 * or block waiting for the first frame with [awaitFirst].
 *
 * Always call [close] when done.
 */
class MelayaStream(
    url: String,
    okHttp: OkHttpClient,
) : AutoCloseable {

    private val queue = LinkedBlockingQueue<JSONObject>()
    private val closed = AtomicBoolean(false)
    private val openLatch = CountDownLatch(1)

    private val ws: WebSocket = okHttp.newWebSocket(
        Request.Builder().url(url).build(),
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                openLatch.countDown()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    queue.offer(JSONObject(text))
                } catch (_: Exception) {
                    // ignore non-JSON keep-alive frames
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                openLatch.countDown() // unblock waiters even on failure
                closed.set(true)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closed.set(true)
            }
        }
    )

    /**
     * Wait up to [timeoutMs] ms for the socket to open (optional; the socket
     * opens asynchronously and frames arrive in the queue regardless).
     */
    fun awaitOpen(timeoutMs: Long = 5_000): Boolean =
        openLatch.await(timeoutMs, TimeUnit.MILLISECONDS)

    /**
     * Poll for the next frame.  Blocks for at most [timeoutMs] milliseconds.
     * Returns null on timeout or when the socket is closed.
     */
    fun poll(timeoutMs: Long = 10_000): JSONObject? =
        queue.poll(timeoutMs, TimeUnit.MILLISECONDS)

    /**
     * Block until one frame arrives (up to [timeoutMs] ms) and return it.
     * Throws [MelayaException] if no frame arrives in time.
     */
    fun awaitFirst(timeoutMs: Long = 10_000): JSONObject =
        poll(timeoutMs) ?: throw MelayaException(
            "No frame received within ${timeoutMs}ms", 0
        )

    /**
     * Consume frames, calling [block] for each.  Returns after [maxFrames] frames
     * or [totalTimeoutMs] ms, whichever comes first.
     */
    fun forEach(
        maxFrames: Int = Int.MAX_VALUE,
        totalTimeoutMs: Long = 30_000,
        block: (JSONObject) -> Unit,
    ) {
        val deadline = System.currentTimeMillis() + totalTimeoutMs
        var count = 0
        while (count < maxFrames && !closed.get()) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            val frame = queue.poll(remaining, TimeUnit.MILLISECONDS) ?: break
            block(frame)
            count++
        }
    }

    override fun close() {
        ws.close(1000, "client closed")
        closed.set(true)
    }
}

/**
 * WebSocket streaming API.
 *
 * Public streams: open immediately with the API key as a query param.
 * Private streams: mint a short-lived `wsTicket` first via the REST API,
 * then open the socket with it.
 */
class StreamAPI internal constructor(
    private val apiKey: String,
    private val wsUrl: String,
    private val http: HttpClient,
    private val okHttp: OkHttpClient,
) {

    // ── Public streams ───────────────────────────────────────────────────────

    /** Live ticker frames. */
    fun ticker(exchange: String, symbol: String, market: String? = null): MelayaStream =
        openPublic("/ws/ticker", mapOf("exchange" to exchange, "symbol" to symbol, "market" to market))

    /** Live order-book frames. */
    fun orderbook(exchange: String, symbol: String, market: String? = null, limit: Int? = null): MelayaStream =
        openPublic("/ws/orderbook", mapOf("exchange" to exchange, "symbol" to symbol, "market" to market, "limit" to limit))

    /** Live OHLCV candle frames. */
    fun ohlcv(exchange: String, symbol: String, timeframe: String, market: String? = null): MelayaStream =
        openPublic("/ws/ohlcv", mapOf("exchange" to exchange, "symbol" to symbol, "timeframe" to timeframe, "market" to market))

    /** Live public-trade frames. */
    fun trades(exchange: String, symbol: String, market: String? = null): MelayaStream =
        openPublic("/ws/public-trades", mapOf("exchange" to exchange, "symbol" to symbol, "market" to market))

    /** Cross-exchange liquidation firehose. */
    fun liquidations(exchange: String? = null): MelayaStream =
        openPublic("/ws/liquidations", mapOf("exchange" to exchange))

    // ── Private streams (ticket-minted) ─────────────────────────────────────

    /**
     * Live strategy events for your account.  Mints a short-lived ticket,
     * then opens `/ws/strategies`.
     */
    fun strategies(): MelayaStream {
        val ticket = mintTicket(mapOf("stream" to "strategies"))
        return openWithTicket("/ws/strategies", ticket)
    }

    /**
     * Live private account feed for one connected exchange key
     * (balance, positions, your orders/fills).
     */
    fun private(
        exchange: String,
        market: String? = null,
        apiKeyId: String? = null,
        keyId: String? = null,
        symbol: String? = null,
    ): MelayaStream {
        val body = buildMap {
            put("stream", "private")
            put("exchange", exchange)
            if (market != null) put("market", market)
            if (apiKeyId != null) put("apiKeyId", apiKeyId)
            if (keyId != null) put("keyId", keyId)
            if (symbol != null) put("symbol", symbol)
        }
        val ticket = mintTicket(body)
        return openWithTicket("/ws/private", ticket)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun openPublic(path: String, params: Map<String, Any?>): MelayaStream {
        val base = wsUrl.trimEnd('/')
        val sb = StringBuilder("$base/${path.trimStart('/')}?apiKey=${encode(apiKey)}")
        for ((k, v) in params) if (v != null) sb.append("&${encode(k)}=${encode(v.toString())}")
        return MelayaStream(sb.toString(), okHttp)
    }

    private fun mintTicket(body: Map<String, Any?>): String {
        val response = http.post("/api/v1/private/private-ticket", body).asObject()
        return response.getString("wsTicket")
    }

    private fun openWithTicket(path: String, ticket: String): MelayaStream {
        val base = wsUrl.trimEnd('/')
        val url = "$base/${path.trimStart('/')}?wsTicket=${encode(ticket)}"
        return MelayaStream(url, okHttp)
    }

    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
