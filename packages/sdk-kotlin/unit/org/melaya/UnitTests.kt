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
 *
 * Covers (SDK triggers addition):
 *   - path, method, query and body of every `triggers` call, and response decoding
 *   - a failed dry poll (`200 {ok:false}`) is returned as is; a 429 throttle still throws
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

// ── Triggers ─────────────────────────────────────────────────────────────────

private const val TID = "0b6f3c2e-7a41-4c1e-9d55-2f8a1b3c4d5e"

/** `triggers.list()` sends project + pipelineName and decodes the array (config stays raw JSON). */
private fun testTriggersList() = withServer { server ->
    server.responseBody = """[{"id":"$TID","name":"refunds","kind":"poll","enabled":true,"config":{"poll":{"service":"gmail"}}}]"""

    val client = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl)
    val result = client.agents.triggers.list(project = "acme", pipelineName = "refund bot")

    val req = server.lastRequest!!
    check("triggers.list: GET method", req.method == "GET")
    check("triggers.list: path", req.path == "/api/v1/private/triggers")
    check("triggers.list: query has project", req.query.contains("project=acme"))
    check("triggers.list: query encodes pipelineName", req.query.contains("pipelineName=refund+bot"))
    check("triggers.list: decodes one record", result.size == 1 && result[0].optString("id") == TID)
    check(
        "triggers.list: config stays raw JSON",
        result[0].getJSONObject("config").getJSONObject("poll").optString("service") == "gmail",
    )
}

/** Read calls: get, deliveries, stats, pendingApprovals, presets, limits, sources. */
private fun testTriggersReads() = withServer { server ->
    server.responseQueue = ArrayDeque(
        listOf(
            200 to """{"id":"$TID","name":"refunds"}""",
            200 to """[{"id":"d1","verdict":"dispatched","runId":"r1"}]""",
            200 to """{"hours":6,"byVerdict":{"filtered":{"n":3,"p50":null,"p95":null}},"filtered":3,"sampled":false}""",
            200 to """[{"requestId":"a1","tool":"gmail_send","expiresAt":1790000000000}]""",
            200 to """{"tier":"pro","tierFloorSec":60,"presets":[],"beta":{"allowed":true}}""",
            200 to """{"tierClass":"pro","triggers":{"used":2,"cap":20},"pollIntervalFloorSec":60}""",
            200 to """[]""",
        )
    )

    val t = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl).triggers
    val one = t.get(TID)
    val deliveries = t.deliveries(TID, limit = 25)
    val stats = t.stats(TID, hours = 6)
    val approvals = t.pendingApprovals(TID)
    val presets = t.presets()
    val limits = t.limits()
    val sources = t.sources()

    val base = "/api/v1/private/triggers"
    val expected = listOf(
        "$base/$TID", "$base/$TID/deliveries", "$base/$TID/stats", "$base/$TID/approvals",
        "$base/presets", "$base/limits", "$base/sources",
    )
    val reqs = server.requests
    check("triggers reads: request count", reqs.size == expected.size, "count=${reqs.size}")
    check("triggers reads: all GET", reqs.all { it.method == "GET" })
    check("triggers reads: paths", reqs.map { it.path } == expected, reqs.map { it.path }.toString())
    check("triggers reads: get sends no query", reqs[0].query.isEmpty())
    check("triggers reads: deliveries limit=25", reqs[1].query == "limit=25")
    check("triggers reads: stats hours=6", reqs[2].query == "hours=6")

    check("triggers reads: get decodes", one.optString("name") == "refunds")
    check("triggers reads: deliveries decode", deliveries.size == 1 && deliveries[0].optString("runId") == "r1")
    check("triggers reads: stats byVerdict", stats.getJSONObject("byVerdict").getJSONObject("filtered").optInt("n") == 3)
    check("triggers reads: approvals decode", approvals.size == 1 && approvals[0].optString("requestId") == "a1")
    check("triggers reads: presets tierFloorSec", presets.optInt("tierFloorSec") == 60)
    check("triggers reads: limits triggers.cap", limits.getJSONObject("triggers").optInt("cap") == 20)
    check("triggers reads: empty sources", sources.isEmpty())
}

/** `triggers.events()` sends triggerId, since, verdicts as a comma list, and limit. */
private fun testTriggersEvents() = withServer { server ->
    server.responseBody = """{"events":[{"triggerId":"$TID","deliveryId":null,"eventId":"e1","source":"poll","verdict":"filtered","at":1790000000000}],"scanned":12,"retention":{"maxEvents":500,"ttlSec":86400}}"""

    val client = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl)
    val result = client.triggers.events(
        triggerId = TID,
        since = 1789999999000L,
        verdicts = listOf("filtered", "failed"),
        limit = 50,
    )

    val req = server.lastRequest!!
    check("triggers.events: GET method", req.method == "GET")
    check("triggers.events: path", req.path == "/api/v1/private/triggers/events")
    check("triggers.events: query has triggerId", req.query.contains("triggerId=$TID"))
    check("triggers.events: query has since", req.query.contains("since=1789999999000"))
    check("triggers.events: verdicts is a comma list", req.query.contains("verdicts=filtered%2Cfailed"), req.query)
    check("triggers.events: query has limit", req.query.contains("limit=50"))
    val ev = result.getJSONArray("events").getJSONObject(0)
    check("triggers.events: decodes verdict", ev.optString("verdict") == "filtered")
    check("triggers.events: no-receipt event has null deliveryId", ev.isNull("deliveryId"))
    check("triggers.events: retention", result.getJSONObject("retention").optInt("maxEvents") == 500)
}

/** `triggers.test()` POSTs `{payload}` and decodes `{accepted, eventId, reason}`. */
private fun testTriggersTest() = withServer { server ->
    server.responseBody = """{"accepted":false,"eventId":"test-1","reason":"disabled"}"""

    val client = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl)
    val result = client.triggers.test(TID, payload = mapOf("subject" to "Refund request"))

    val req = server.lastRequest!!
    check("triggers.test: POST method", req.method == "POST")
    check("triggers.test: path", req.path == "/api/v1/private/triggers/$TID/test")
    val body = JSONObject(req.bodyText())
    check("triggers.test: body carries payload", body.getJSONObject("payload").optString("subject") == "Refund request")
    check("triggers.test: decodes accepted", !result.optBoolean("accepted", true))
    check("triggers.test: decodes eventId", result.optString("eventId") == "test-1")
    check("triggers.test: decodes reason", result.optString("reason") == "disabled")
}

/** `pollStatus`, `pollTest` (dry true), `pollNow` (dry false), `pollSync`. */
private fun testTriggersPoll() = withServer { server ->
    server.responseQueue = ArrayDeque(
        listOf(
            200 to """{"synced":true,"status":"ok","lastError":null,"armed":true,"baselinePending":false,"seenCount":40,"itemsPublished":7,"consecutiveErrors":0,"requestedIntervalSec":30,"effectiveIntervalSec":60,"tierFloorSec":60}""",
            200 to """{"dry":true,"ok":true,"found":2,"baseline":false,"wouldPublish":1,"items":[{"id":"m1","preview":"Refund"}],"samplePayload":{"id":"m1"}}""",
            200 to """{"dry":false,"queued":true}""",
            200 to """{"result":"armed"}""",
        )
    )

    val t = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl).agents.triggers
    val status = t.pollStatus(TID)
    val dry = t.pollTest(TID)
    val now = t.pollNow(TID)
    val sync = t.pollSync(TID)

    val r = server.requests
    check("pollStatus: GET", r[0].method == "GET" && r[0].path == "/api/v1/private/triggers/$TID/poll")
    check("pollStatus: decodes", status.optBoolean("synced") && status.optInt("effectiveIntervalSec") == 60)
    check("pollTest: POST /poll/test", r[1].method == "POST" && r[1].path == "/api/v1/private/triggers/$TID/poll/test")
    check("pollTest: body dry=true", JSONObject(r[1].bodyText()).optBoolean("dry", false))
    check("pollTest: decodes wouldPublish", dry.optInt("wouldPublish") == 1)
    check("pollTest: decodes items", dry.getJSONArray("items").getJSONObject(0).optString("id") == "m1")
    check("pollTest: decodes samplePayload", dry.getJSONObject("samplePayload").optString("id") == "m1")
    check("pollNow: POST /poll/test", r[2].method == "POST" && r[2].path == "/api/v1/private/triggers/$TID/poll/test")
    check("pollNow: body dry=false", !JSONObject(r[2].bodyText()).optBoolean("dry", true))
    check("pollNow: decodes queued", now.optBoolean("queued"))
    check("pollSync: POST /poll/sync", r[3].method == "POST" && r[3].path == "/api/v1/private/triggers/$TID/poll/sync")
    check("pollSync: decodes result", sync.optString("result") == "armed")
}

/** A failed dry poll (`200 {dry:true, ok:false, error}`) is returned, not thrown; a 429 still throws. */
private fun testTriggersPollTestFailures() = withServer { server ->
    server.responseQueue = ArrayDeque(
        listOf(
            200 to """{"dry":true,"ok":false,"error":"auth_expired"}""",
            429 to """{"error":"poll_dry_run_throttled"}""",
        )
    )

    val client = Melaya(apiKey = "mk_test", baseUrl = server.baseUrl)
    val failed = try {
        client.triggers.pollTest(TID)
    } catch (e: MelayaException) {
        check("pollTest failed dry poll: must not throw", false, "threw MelayaException(${e.status}): ${e.message}")
        null
    }
    check("pollTest failed dry poll: ok false", failed?.optBoolean("ok", true) == false)
    check("pollTest failed dry poll: error", failed?.optString("error") == "auth_expired")

    try {
        client.triggers.pollTest(TID)
        check("pollTest 429: throws MelayaException", false, "no exception was thrown")
    } catch (e: MelayaException) {
        check("pollTest 429: status propagated", e.status == 429)
        check("pollTest 429: code propagated", e.code == "poll_dry_run_throttled")
    }
}

/** `melaya.triggers` and `melaya.agents.triggers` are the same instance. */
private fun testTriggersExposed() {
    val client = Melaya(apiKey = "mk_test", baseUrl = "http://127.0.0.1:1")
    check("triggers: flat alias shares the namespace instance", client.triggers === client.agents.triggers)
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
    testTriggersList()
    testTriggersReads()
    testTriggersEvents()
    testTriggersTest()
    testTriggersPoll()
    testTriggersPollTestFailures()
    testTriggersExposed()

    println()
    println("PASS $passCount   FAIL $failCount   |  total ${passCount + failCount}")
    println(if (failCount == 0) "RESULT: GO" else "RESULT: NO-GO — $failCount failing.")

    System.exit(if (failCount == 0) 0 else 1)
}
