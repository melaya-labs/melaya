package org.melaya

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * Mock-transport unit tests for the Melaya Kotlin SDK.
 *
 * No live network calls: each test spins up a real local HTTP server on the JDK's own
 * `com.sun.net.httpserver` (part of the standard library — no test-framework dependency
 * needed), points a real [Melaya] client at it via `baseUrl`, captures the exact request
 * the SDK sent, and asserts on it against a canned response.
 *
 * Run with: `gradle unitTest` (see build.gradle's `unit` source set).
 *
 * Covers (SDK 0.3 additions):
 *   - `pipelines.run()` with a `run_inputs` body
 *   - one multipart upload (`pipelines.uploadRunFile`) — boundary + file part + filename
 *   - `pipelines.runInputFile()` returning raw bytes (not JSON-parsed)
 *   - `hitl.projectToolCalls()` query-string encoding
 *   - a bridged POST with path param + body (`connectors.applyPersonal`)
 *   - a DELETE with a JSON body (`credentials.googleDisconnect`)
 *
 * Covers (SDK connector-tools addition):
 *   - `connectorTools.search()` query-string encoding (`q` + `limit`)
 *   - `connectorTools.call()` on a read tool returns 200 with the result
 *   - `connectorTools.call()` on a staged write returns the 202 body directly (not thrown)
 *   - `connectorTools.callStatus()` parses a `done` outcome
 *   - a money-moving 403 raises [MelayaException] with the server's error code
 *   - `connectorTools.callAndWait()` polls a pending staged write to `done`
 */

// ── Mock HTTP transport ─────────────────────────────────────────────────────

private data class CapturedRequest(
    val method: String,
    val path: String,
    val query: String,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
) {
    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

    fun bodyText(): String = String(body, StandardCharsets.UTF_8)
}

private class MockServer {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    var lastRequest: CapturedRequest? = null
    var responseStatus: Int = 200
    var responseBody: String = "{}"
    var rawResponseBody: ByteArray? = null
    var responseContentType: String = "application/json"

    /**
     * Optional sequence of (status, body) pairs, consumed one per request in order — for
     * tests that need a stateful server (e.g. [callAndWait] polling from `pending` to `done`).
     * Falls back to [responseStatus] / [responseBody] once exhausted or when left null.
     */
    var responseQueue: ArrayDeque<Pair<Int, String>>? = null

    val requestCount: Int get() = requests.size
    val requests = mutableListOf<CapturedRequest>()

    val port: Int get() = server.address.port
    val baseUrl: String get() = "http://127.0.0.1:$port"

    init {
        server.createContext("/") { exchange: HttpExchange ->
            val bodyBytes = exchange.requestBody.readBytes()
            val captured = CapturedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                query = exchange.requestURI.rawQuery ?: "",
                headers = exchange.requestHeaders.toMap(),
                body = bodyBytes,
            )
            lastRequest = captured
            requests.add(captured)
            val queued = responseQueue?.removeFirstOrNull()
            val status = queued?.first ?: responseStatus
            val respBytes = rawResponseBody ?: (queued?.second ?: responseBody).toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", responseContentType)
            exchange.sendResponseHeaders(status, respBytes.size.toLong())
            exchange.responseBody.use { it.write(respBytes) }
        }
        server.start()
    }

    fun stop() = server.stop(0)
}

// ── Assertion helpers ────────────────────────────────────────────────────────

private var passCount = 0
private var failCount = 0

private fun check(name: String, condition: Boolean, detail: String = "") {
    if (condition) {
        println("  PASS  $name")
        passCount++
    } else {
        println("  FAIL  $name  ${detail.take(160)}")
        failCount++
    }
}

private fun withServer(block: (MockServer) -> Unit) {
    val server = MockServer()
    try {
        block(server)
    } catch (e: Exception) {
        check("unexpected exception", false, "${e::class.simpleName}: ${e.message}")
    } finally {
        server.stop()
    }
}

// ── Tests ─────────────────────────────────────────────────────────────────────

/** `pipelines.run()` with a `run_inputs` body (A.1). */
private fun testRunWithRunInputs() = withServer { server ->
    server.responseBody = """{"run_id":"run_abc123","queued":true,"run_inputs":{"brief":"echoed"}}"""

    val client = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl)
    val result = client.pipelines.run(
        name = "my-pipeline",
        project = "acme",
        runInputs = mapOf(
            "brief" to "Summarize the attached report",
            "values" to mapOf("doc" to mapOf("file_id" to "f_123")),
        ),
    )

    check("run: parses run_id", result.optString("run_id") == "run_abc123")
    check("run: parses run_inputs echo", result.has("run_inputs"))

    val req = server.lastRequest!!
    check("run: POST method", req.method == "POST")
    check("run: correct path", req.path == "/api/v1/private/pipelines/my-pipeline/run")

    val body = JSONObject(req.bodyText())
    check("run: body carries project", body.optString("project") == "acme")
    check("run: body has run_inputs", body.has("run_inputs"))
    val runInputs = body.getJSONObject("run_inputs")
    check("run: run_inputs.brief round-trips", runInputs.optString("brief") == "Summarize the attached report")
    check(
        "run: run_inputs.values.doc.file_id round-trips",
        runInputs.getJSONObject("values").getJSONObject("doc").optString("file_id") == "f_123",
    )
}

/** One multipart upload (`pipelines.uploadRunFile`) — boundary + filename + file content (A.2). */
private fun testMultipartUpload() = withServer { server ->
    server.responseBody = """{"file_id":"file_xyz"}"""

    val client = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl)
    val fileBytes = "hello world".toByteArray(StandardCharsets.UTF_8)
    val result = client.pipelines.uploadRunFile(
        name = "my-pipeline",
        key = "doc",
        file = fileBytes,
        project = "acme",
        filename = "report.txt",
        contentType = "text/plain",
    )

    check("uploadRunFile: parses file_id", result.optString("file_id") == "file_xyz")

    val req = server.lastRequest!!
    check("uploadRunFile: POST method", req.method == "POST")
    check("uploadRunFile: path", req.path == "/api/v1/private/pipelines/my-pipeline/run-files")
    check("uploadRunFile: query has key", req.query.contains("key=doc"))
    check("uploadRunFile: query has project", req.query.contains("project=acme"))

    val contentType = req.header("Content-Type") ?: ""
    check("uploadRunFile: Content-Type is multipart/form-data", contentType.contains("multipart/form-data"))
    check("uploadRunFile: Content-Type carries a boundary", contentType.contains("boundary="))

    val bodyText = String(req.body, StandardCharsets.ISO_8859_1)
    check("uploadRunFile: body has the file field name", bodyText.contains("name=\"file\""))
    check("uploadRunFile: body has the given filename", bodyText.contains("filename=\"report.txt\""))
    check("uploadRunFile: body carries the file content", bodyText.contains("hello world"))
}

/** `pipelines.runInputFile()` returns raw bytes, never JSON-parsed (A.4). */
private fun testRunInputFileRawBytes() = withServer { server ->
    val rawBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x01, 0x02, 0xFF.toByte())
    server.rawResponseBody = rawBytes
    server.responseContentType = "application/octet-stream"

    val client = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl)
    val bytes = client.pipelines.runInputFile("my-pipeline", "0123456789abcdef", 0)

    check("runInputFile: returns exact raw bytes", bytes.contentEquals(rawBytes))

    val req = server.lastRequest!!
    check("runInputFile: GET method", req.method == "GET")
    check(
        "runInputFile: correct path",
        req.path == "/api/v1/private/pipelines/my-pipeline/runs/0123456789abcdef/inputs/files/0",
    )
}

/** `hitl.projectToolCalls()` query-string encoding (B). */
private fun testProjectToolCallsQueryEncoding() = withServer { server ->
    server.responseBody = """{"items":[],"nextCursor":null,"capped":false}"""

    val client = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl)
    val result = client.hitl.projectToolCalls(
        project = "acme",
        limit = 10,
        status = "ok",
        sort = "recent",
        search = "hello world",
    )

    check("projectToolCalls: parses capped field", result.has("capped"))

    val req = server.lastRequest!!
    check("projectToolCalls: GET method", req.method == "GET")
    check("projectToolCalls: path", req.path == "/api/v1/private/projects/acme/tool-calls")
    val q = req.query
    check("projectToolCalls: query has limit=10", q.contains("limit=10"))
    check("projectToolCalls: query has status=ok", q.contains("status=ok"))
    check("projectToolCalls: query has sort=recent", q.contains("sort=recent"))
    check("projectToolCalls: query encodes the space in search", q.contains("search=hello+world"))
}

/** A bridged POST with a path param + body (`connectors.applyPersonal`) (B). */
private fun testApplyPersonalBridgedPost() = withServer { server ->
    server.responseBody = """{"ok":true}"""

    val client = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl)
    val result = client.connectors.applyPersonal("acme", "google", googleCapabilities = listOf("gmail", "calendar"))

    check("applyPersonal: parses ok", result.optBoolean("ok"))

    val req = server.lastRequest!!
    check("applyPersonal: POST method", req.method == "POST")
    check(
        "applyPersonal: path carries project + service",
        req.path == "/api/v1/private/projects/acme/connectors/google/apply-personal",
    )
    val body = JSONObject(req.bodyText())
    val caps = body.getJSONArray("googleCapabilities")
    check("applyPersonal: body carries googleCapabilities", caps.length() == 2 && caps.getString(0) == "gmail")
}

/** A DELETE with a JSON body (`credentials.googleDisconnect`) (B). */
private fun testGoogleDisconnectDeleteWithBody() = withServer { server ->
    server.responseBody = """{"ok":true}"""

    val client = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl)
    val result = client.credentials.googleDisconnect("507f1f77bcf86cd799439011", capability = "gmail")

    check("googleDisconnect: parses ok", result.optBoolean("ok"))

    val req = server.lastRequest!!
    check("googleDisconnect: DELETE method", req.method == "DELETE")
    check("googleDisconnect: path", req.path == "/api/v1/private/credentials/google/access")
    val body = JSONObject(req.bodyText())
    check("googleDisconnect: body carries accountId", body.optString("accountId") == "507f1f77bcf86cd799439011")
    check("googleDisconnect: body carries capability", body.optString("capability") == "gmail")
}

/** `connectorTools.search()` query-string encoding — `q` (with a space) + `limit` (C.1). */
private fun testConnectorToolsSearchQueryEncoding() = withServer { server ->
    server.responseBody = """{"query":"unread email","services":["gmail"],"tools":[]}"""

    val client = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl)
    val result = client.connectorTools.search("unread email", limit = 5)

    check("search: parses query", result.optString("query") == "unread email")

    val req = server.lastRequest!!
    check("search: GET method", req.method == "GET")
    check("search: path", req.path == "/api/v1/private/connector-tools/search")
    check("search: query encodes the space in q", req.query.contains("q=unread+email"))
    check("search: query has limit=5", req.query.contains("limit=5"))
}

/** `connectorTools.call()` on a read tool runs immediately and returns 200 with the result (C.2). */
private fun testConnectorToolsCallReadReturns200() = withServer { server ->
    server.responseBody = """{"status":"done","tool":"gmail_list_messages","readOnly":true,"result":"5 unread"}"""

    val client = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl)
    val result = client.agents.connectorTools.call("gmail_list_messages")

    check("call read: status done", result.optString("status") == "done")
    check("call read: result round-trips", result.optString("result") == "5 unread")

    val req = server.lastRequest!!
    check("call read: POST method", req.method == "POST")
    check("call read: path", req.path == "/api/v1/private/connector-tools/call")
    val body = JSONObject(req.bodyText())
    check("call read: body carries tool", body.optString("tool") == "gmail_list_messages")
    check("call read: approval defaults to required", body.optString("approval") == "required")
}

/** `connectorTools.call()` on a staged write returns the 202 body directly, never thrown (C.3). */
private fun testConnectorToolsCallWriteStaged202() = withServer { server ->
    server.responseStatus = 202
    server.responseBody = """{"status":"pending_approval","tool":"gmail_send","requestId":"req_123","message":"Approve it in the Melaya app."}"""

    val client = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl)
    val result = try {
        client.connectorTools.call("gmail_send", args = mapOf("to" to "a@b.com"))
    } catch (e: MelayaException) {
        check("call staged write: 202 must not throw", false, "threw MelayaException(${e.status}): ${e.message}")
        null
    }

    check("call staged write: returns pending_approval", result?.optString("status") == "pending_approval")
    check("call staged write: carries requestId", result?.optString("requestId") == "req_123")

    val body = JSONObject(server.lastRequest!!.bodyText())
    check("call staged write: body carries args", body.getJSONObject("args").optString("to") == "a@b.com")
}

/** `connectorTools.callStatus()` parses a `done` outcome (C.4). */
private fun testConnectorToolsCallStatusDone() = withServer { server ->
    server.responseBody = """{"requestId":"req_123","status":"done","ok":true,"result":"sent"}"""

    val client = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl)
    val result = client.connectorTools.callStatus("req_123")

    check("callStatus: status done", result.optString("status") == "done")
    check("callStatus: ok true", result.optBoolean("ok"))
    check("callStatus: result round-trips", result.optString("result") == "sent")

    val req = server.lastRequest!!
    check("callStatus: GET method", req.method == "GET")
    check("callStatus: path", req.path == "/api/v1/private/connector-tools/calls/req_123")
}

/** A money-moving tool is refused under either approval mode — raises [MelayaException] (C.5). */
private fun testConnectorToolsMoneyMovingRaisesException() = withServer { server ->
    server.responseStatus = 403
    server.responseBody = """{"error":"money_moving_requires_app_approval","message":"Tools that move money or trade run only from the Melaya app."}"""

    val client = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl)
    try {
        client.connectorTools.call("stripe_create_refund", approval = "none")
        check("money-moving: throws MelayaException", false, "no exception was thrown")
    } catch (e: MelayaException) {
        check("money-moving: status propagated", e.status == 403)
        check("money-moving: code propagated", e.code == "money_moving_requires_app_approval")
    }
}

/** `connectorTools.callAndWait()` polls a pending staged write through to `done` (C.6). */
private fun testConnectorToolsCallAndWaitPollsToDone() = withServer { server ->
    server.responseQueue = ArrayDeque(
        listOf(
            202 to """{"status":"pending_approval","tool":"gmail_send","requestId":"req_999","message":"pending"}""",
            200 to """{"requestId":"req_999","status":"pending"}""",
            200 to """{"requestId":"req_999","status":"running"}""",
            200 to """{"requestId":"req_999","status":"done","ok":true,"result":"sent"}""",
        )
    )

    val client = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl)
    val outcome = client.connectorTools.callAndWait(
        "gmail_send",
        args = mapOf("to" to "a@b.com"),
        pollIntervalMs = 5L,
        timeoutMs = 5_000L,
    )

    check("callAndWait: settles to done", outcome.optString("status") == "done")
    check("callAndWait: result round-trips", outcome.optString("result") == "sent")
    check("callAndWait: polled call + 3 status checks", server.requestCount == 4, "requestCount=${server.requestCount}")
}

/** A non-2xx response still raises [MelayaException] through the mock transport (sanity check). */
private fun testErrorResponseRaisesMelayaException() = withServer { server ->
    server.responseStatus = 403
    server.responseBody = """{"error":"forbidden"}"""

    val client = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl)
    try {
        client.pipelines.overview()
        check("error response: throws MelayaException", false, "no exception was thrown")
    } catch (e: MelayaException) {
        check("error response: status propagated", e.status == 403)
        check("error response: code propagated", e.code == "forbidden")
    }
}

// ════════════════════════════════════════════════════════════════════════════
fun main() {
    println("── Melaya Kotlin SDK — mock-transport unit tests ──")

    testRunWithRunInputs()
    testMultipartUpload()
    testRunInputFileRawBytes()
    testProjectToolCallsQueryEncoding()
    testApplyPersonalBridgedPost()
    testGoogleDisconnectDeleteWithBody()
    testConnectorToolsSearchQueryEncoding()
    testConnectorToolsCallReadReturns200()
    testConnectorToolsCallWriteStaged202()
    testConnectorToolsCallStatusDone()
    testConnectorToolsMoneyMovingRaisesException()
    testConnectorToolsCallAndWaitPollsToDone()
    testErrorResponseRaisesMelayaException()

    println()
    println("PASS $passCount   FAIL $failCount   |  total ${passCount + failCount}")
    println(if (failCount == 0) "RESULT: GO" else "RESULT: NO-GO — $failCount failing.")

    System.exit(if (failCount == 0) 0 else 1)
}
