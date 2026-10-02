package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock-transport unit tests for the Melaya Java SDK's 0.3 surface additions.
 *
 * <p>No test framework is added (no new dependency): each check is a plain
 * assertion run from {@code main()}, mirroring {@link E2E}'s manual PASS/FAIL
 * style. The "mock transport" is a real, ephemeral loopback HTTP server
 * ({@code com.sun.net.httpserver.HttpServer} — JDK-only, no extra dependency)
 * that {@link HttpClient} talks to exactly as it would talk to the real API,
 * letting each test assert on the actual bytes/headers sent over the wire.
 *
 * <pre>
 *   gradle unitTest
 * </pre>
 */
public class UnitTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static int passCount = 0;
    private static int failCount = 0;

    @FunctionalInterface
    interface Check {
        void run() throws Exception;
    }

    private static void check(String name, Check test) {
        try {
            test.run();
            passCount++;
            System.out.println("  PASS  " + name);
        } catch (Throwable t) {
            failCount++;
            System.out.println("  FAIL  " + name + "  — " + t);
        }
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(msg + " — expected <" + expected + "> but was <" + actual + ">");
        }
    }

    // ── mock-transport helpers ──────────────────────────────────────────────

    private static HttpServer startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.setExecutor(null);
        server.start();
        return server;
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respondJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
        exchange.close();
    }

    private static void respondBytes(HttpExchange exchange, int status, byte[] body, String contentType) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
        exchange.close();
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> out = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) return out;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            String v = eq >= 0 ? pair.substring(eq + 1) : "";
            out.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return out;
    }

    /** One captured HTTP request, read off the fake server's exchange. */
    static final class CapturedRequest {
        final String method;
        final String path;
        /** The path exactly as sent on the wire (percent-encoding kept). */
        final String rawPath;
        final String rawQuery;
        final Headers headers;
        final byte[] body;

        private CapturedRequest(String method, String path, String rawPath, String rawQuery,
                                Headers headers, byte[] body) {
            this.method = method;
            this.path = path;
            this.rawPath = rawPath;
            this.rawQuery = rawQuery;
            this.headers = headers;
            this.body = body;
        }

        static CapturedRequest capture(HttpExchange exchange) throws IOException {
            byte[] body = exchange.getRequestBody().readAllBytes();
            return new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawPath(),
                    exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders(),
                    body);
        }
    }

    /** A call made against the fake server: what was sent, and what the SDK returned. */
    static final class Roundtrip {
        final CapturedRequest req;
        final JsonNode resp;

        Roundtrip(CapturedRequest req, JsonNode resp) {
            this.req = req;
            this.resp = resp;
        }

        JsonNode body() throws IOException {
            return MAPPER.readTree(req.body);
        }
    }

    @FunctionalInterface
    interface ApiCall {
        JsonNode run(HttpClient http) throws Exception;
    }

    /** Serve {@code responseJson} once, run {@code call} against it, return the captured request. */
    private static Roundtrip roundtrip(String responseJson, ApiCall call) throws Exception {
        CapturedRequest[] captured = new CapturedRequest[1];
        HttpServer server = startServer(exchange -> {
            captured[0] = CapturedRequest.capture(exchange);
            respondJson(exchange, 200, responseJson);
        });
        try {
            JsonNode resp = call.run(new HttpClient("mk_test", baseUrl(server)));
            return new Roundtrip(captured[0], resp);
        } finally {
            server.stop(0);
        }
    }

    // ── tests ────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("── Melaya Java SDK — mock-transport unit tests ──");

        check("pipelines.run() sends run_inputs in the JSON body and echoes the response", () -> {
            CapturedRequest[] captured = new CapturedRequest[1];
            HttpServer server = startServer(exchange -> {
                captured[0] = CapturedRequest.capture(exchange);
                respondJson(exchange, 200,
                        "{\"run_id\":\"abc123\",\"queued\":true,\"run_inputs\":{\"brief\":\"hi\"}}");
            });
            try {
                HttpClient http = new HttpClient("mk_test", baseUrl(server));
                PipelinesAPI pipelines = new PipelinesAPI(http);

                Map<String, Object> values = new LinkedHashMap<>();
                values.put("doc", Map.of("file_id", "f_123"));
                Map<String, Object> runInputs = new LinkedHashMap<>();
                runInputs.put("brief", "Summarize the report");
                runInputs.put("values", values);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("project", "acme");
                body.put("run_inputs", runInputs);

                JsonNode resp = pipelines.run("nightly-report", body);

                assertEquals("POST", captured[0].method, "method");
                assertEquals("/api/v1/private/pipelines/nightly-report/run", captured[0].path, "path");
                assertEquals("Bearer mk_test", captured[0].headers.getFirst("Authorization"), "auth header");
                JsonNode sentBody = MAPPER.readTree(captured[0].body);
                assertEquals("Summarize the report", sentBody.at("/run_inputs/brief").asText(), "sent run_inputs.brief");
                assertEquals("f_123", sentBody.at("/run_inputs/values/doc/file_id").asText(),
                        "sent run_inputs.values.doc.file_id");
                assertEquals("abc123", resp.get("run_id").asText(), "response run_id");
                assertEquals("hi", resp.at("/run_inputs/brief").asText(), "response run_inputs echo");
            } finally {
                server.stop(0);
            }
        });

        check("pipelines.uploadRunFile() sends a real multipart/form-data body", () -> {
            CapturedRequest[] captured = new CapturedRequest[1];
            HttpServer server = startServer(exchange -> {
                captured[0] = CapturedRequest.capture(exchange);
                respondJson(exchange, 200, "{\"file_id\":\"file_abc\"}");
            });
            try {
                HttpClient http = new HttpClient("mk_test", baseUrl(server));
                PipelinesAPI pipelines = new PipelinesAPI(http);

                byte[] fileBytes = "hello world".getBytes(StandardCharsets.UTF_8);
                JsonNode resp = pipelines.uploadRunFile("nightly-report", "attachment", fileBytes,
                        Map.of("filename", "notes.txt", "contentType", "text/plain"));

                assertEquals("/api/v1/private/pipelines/nightly-report/run-files", captured[0].path, "path");
                assertEquals("attachment", parseQuery(captured[0].rawQuery).get("key"), "query.key");

                String contentType = captured[0].headers.getFirst("Content-Type");
                assertTrue(contentType != null && contentType.startsWith("multipart/form-data; boundary="),
                        "Content-Type should carry a boundary: " + contentType);
                String boundary = contentType.substring(contentType.indexOf("boundary=") + "boundary=".length());
                String bodyText = new String(captured[0].body, StandardCharsets.UTF_8);
                assertTrue(bodyText.contains("--" + boundary), "body should contain the boundary marker");
                assertTrue(bodyText.contains("Content-Disposition: form-data; name=\"file\"; filename=\"notes.txt\""),
                        "body should carry the file field name + given filename");
                assertTrue(bodyText.contains("Content-Type: text/plain"), "body should carry the given content type");
                assertTrue(bodyText.contains("hello world"), "body should contain the file bytes");

                assertEquals("file_abc", resp.get("file_id").asText(), "response file_id");
            } finally {
                server.stop(0);
            }
        });

        check("pipelines.runInputFile() returns raw bytes untouched (no JSON parsing)", () -> {
            byte[] expected = new byte[]{0x50, 0x4B, 0x03, 0x04, (byte) 0xFF, 0x00, 0x7B, 0x7D};
            HttpServer server = startServer(exchange ->
                    respondBytes(exchange, 200, expected, "application/octet-stream"));
            try {
                HttpClient http = new HttpClient("mk_test", baseUrl(server));
                PipelinesAPI pipelines = new PipelinesAPI(http);

                byte[] actual = pipelines.runInputFile("nightly-report", "0123456789abcdef", 0);

                assertTrue(Arrays.equals(expected, actual), "bytes should round-trip exactly");
            } finally {
                server.stop(0);
            }
        });

        check("pipelines.projectToolCalls() encodes the query string", () -> {
            CapturedRequest[] captured = new CapturedRequest[1];
            HttpServer server = startServer(exchange -> {
                captured[0] = CapturedRequest.capture(exchange);
                respondJson(exchange, 200, "{\"items\":[],\"nextCursor\":null,\"capped\":false}");
            });
            try {
                HttpClient http = new HttpClient("mk_test", baseUrl(server));
                PipelinesAPI pipelines = new PipelinesAPI(http);

                Map<String, Object> query = new LinkedHashMap<>();
                query.put("limit", 25);
                query.put("status", "ok");
                query.put("sort", "slowest");
                query.put("approval", "by:alice");

                JsonNode resp = pipelines.projectToolCalls("acme", query);

                assertEquals("/api/v1/private/projects/acme/tool-calls", captured[0].path, "path");
                Map<String, String> q = parseQuery(captured[0].rawQuery);
                assertEquals("25", q.get("limit"), "query.limit");
                assertEquals("ok", q.get("status"), "query.status");
                assertEquals("slowest", q.get("sort"), "query.sort");
                assertEquals("by:alice", q.get("approval"), "query.approval round-trips through percent-encoding");
                assertTrue(resp.get("items").isArray(), "response.items is an array");
            } finally {
                server.stop(0);
            }
        });

        check("connectors.applyPersonal() sends project+service in the path and a JSON body", () -> {
            CapturedRequest[] captured = new CapturedRequest[1];
            HttpServer server = startServer(exchange -> {
                captured[0] = CapturedRequest.capture(exchange);
                respondJson(exchange, 200, "{\"ok\":true}");
            });
            try {
                HttpClient http = new HttpClient("mk_test", baseUrl(server));
                ConnectorsAPI connectors = new ConnectorsAPI(http);

                JsonNode resp = connectors.applyPersonal("acme", "openai", List.of("gmail", "calendar"));

                assertEquals("POST", captured[0].method, "method");
                assertEquals("/api/v1/private/projects/acme/connectors/openai/apply-personal",
                        captured[0].path, "path");
                JsonNode sentBody = MAPPER.readTree(captured[0].body);
                assertTrue(sentBody.get("googleCapabilities").isArray(), "body.googleCapabilities is an array");
                assertEquals("gmail", sentBody.get("googleCapabilities").get(0).asText(), "body.googleCapabilities[0]");
                assertEquals("calendar", sentBody.get("googleCapabilities").get(1).asText(), "body.googleCapabilities[1]");
                assertTrue(resp.get("ok").asBoolean(), "response.ok");
            } finally {
                server.stop(0);
            }
        });

        check("credentials.googleDisconnect() sends DELETE with a JSON body", () -> {
            CapturedRequest[] captured = new CapturedRequest[1];
            HttpServer server = startServer(exchange -> {
                captured[0] = CapturedRequest.capture(exchange);
                respondJson(exchange, 200, "{\"ok\":true}");
            });
            try {
                HttpClient http = new HttpClient("mk_test", baseUrl(server));
                CredentialsAPI credentials = new CredentialsAPI(http);

                JsonNode resp = credentials.googleDisconnect("507f1f77bcf86cd799439011", "gmail");

                assertEquals("DELETE", captured[0].method, "method");
                assertEquals("/api/v1/private/credentials/google/access", captured[0].path, "path");
                assertEquals("application/json", captured[0].headers.getFirst("Content-Type"),
                        "a DELETE carrying a body must set Content-Type");
                JsonNode sentBody = MAPPER.readTree(captured[0].body);
                assertEquals("507f1f77bcf86cd799439011", sentBody.get("accountId").asText(), "body.accountId");
                assertEquals("gmail", sentBody.get("capability").asText(), "body.capability");
                assertTrue(resp.get("ok").asBoolean(), "response.ok");
            } finally {
                server.stop(0);
            }
        });

        check("a 404 response throws MelayaException with status + code (never silently null)", () -> {
            HttpServer server = startServer(exchange ->
                    respondJson(exchange, 404, "{\"error\":\"not_found\",\"message\":\"no such run\"}"));
            try {
                HttpClient http = new HttpClient("mk_test", baseUrl(server));
                PipelinesAPI pipelines = new PipelinesAPI(http);
                try {
                    pipelines.runStatus("nightly-report", "0123456789abcdef");
                    throw new AssertionError("expected a MelayaException for a 404 response");
                } catch (MelayaException e) {
                    assertEquals(404, e.getStatus(), "exception status");
                    assertEquals("not_found", e.getCode(), "exception code");
                }
            } finally {
                server.stop(0);
            }
        });

        check("connectorTools.search() encodes q and limit in the query string", () -> {
            CapturedRequest[] captured = new CapturedRequest[1];
            HttpServer server = startServer(exchange -> {
                captured[0] = CapturedRequest.capture(exchange);
                respondJson(exchange, 200, "{\"query\":\"unread email\",\"services\":[\"gmail\"],\"tools\":[]}");
            });
            try {
                HttpClient http = new HttpClient("mk_test", baseUrl(server));
                ConnectorToolsAPI connectorTools = new ConnectorToolsAPI(http);

                JsonNode resp = connectorTools.search("unread email", 5);

                assertEquals("GET", captured[0].method, "method");
                assertEquals("/api/v1/private/connector-tools/search", captured[0].path, "path");
                Map<String, String> q = parseQuery(captured[0].rawQuery);
                assertEquals("unread email", q.get("q"), "query.q round-trips through percent-encoding");
                assertEquals("5", q.get("limit"), "query.limit");
                assertEquals("unread email", resp.get("query").asText(), "response.query");
            } finally {
                server.stop(0);
            }
        });

        check("connectorTools.call() runs a read tool immediately (200)", () -> {
            CapturedRequest[] captured = new CapturedRequest[1];
            HttpServer server = startServer(exchange -> {
                captured[0] = CapturedRequest.capture(exchange);
                respondJson(exchange, 200,
                        "{\"status\":\"done\",\"tool\":\"gmail_list_messages\",\"readOnly\":true,\"result\":\"5 unread\"}");
            });
            try {
                HttpClient http = new HttpClient("mk_test", baseUrl(server));
                ConnectorToolsAPI connectorTools = new ConnectorToolsAPI(http);

                JsonNode resp = connectorTools.call("gmail_list_messages", Map.of(), null);

                assertEquals("POST", captured[0].method, "method");
                assertEquals("/api/v1/private/connector-tools/call", captured[0].path, "path");
                JsonNode sentBody = MAPPER.readTree(captured[0].body);
                assertEquals("gmail_list_messages", sentBody.get("tool").asText(), "sent body.tool");
                assertTrue(!sentBody.has("approval"), "approval omitted lets the server default to \"required\"");
                assertEquals("done", resp.get("status").asText(), "response.status");
                assertEquals("5 unread", resp.get("result").asText(), "response.result");
            } finally {
                server.stop(0);
            }
        });

        check("connectorTools.call() returns a 202 staged-approval body instead of throwing", () -> {
            HttpServer server = startServer(exchange ->
                    respondJson(exchange, 202,
                            "{\"status\":\"pending_approval\",\"tool\":\"gmail_send\",\"requestId\":\"req-1\",\"message\":\"Approve it in the Melaya app.\"}"));
            try {
                HttpClient http = new HttpClient("mk_test", baseUrl(server));
                ConnectorToolsAPI connectorTools = new ConnectorToolsAPI(http);

                JsonNode resp = connectorTools.call("gmail_send", Map.of("to", "a@b.c"), "required");

                assertEquals("pending_approval", resp.get("status").asText(),
                        "a 202 must be returned as parsed JSON, not thrown as an exception");
                assertEquals("req-1", resp.get("requestId").asText(), "response.requestId");
            } finally {
                server.stop(0);
            }
        });

        check("connectorTools.callStatus() reports a completed write", () -> {
            HttpServer server = startServer(exchange ->
                    respondJson(exchange, 200,
                            "{\"requestId\":\"req-1\",\"status\":\"done\",\"ok\":true,\"result\":\"wrote:gmail_send\"}"));
            try {
                HttpClient http = new HttpClient("mk_test", baseUrl(server));
                ConnectorToolsAPI connectorTools = new ConnectorToolsAPI(http);

                JsonNode resp = connectorTools.callStatus("req-1");

                assertEquals("done", resp.get("status").asText(), "response.status");
                assertTrue(resp.get("ok").asBoolean(), "response.ok");
                assertEquals("wrote:gmail_send", resp.get("result").asText(), "response.result");
            } finally {
                server.stop(0);
            }
        });

        check("connectorTools.call() raises MelayaException for a money-moving refusal (403)", () -> {
            HttpServer server = startServer(exchange ->
                    respondJson(exchange, 403,
                            "{\"error\":\"money_moving_requires_app_approval\",\"tool\":\"stripe_create_refund\",\"message\":\"Tools that move money or trade run only from the Melaya app.\"}"));
            try {
                HttpClient http = new HttpClient("mk_test", baseUrl(server));
                ConnectorToolsAPI connectorTools = new ConnectorToolsAPI(http);
                try {
                    connectorTools.call("stripe_create_refund", Map.of(), "none");
                    throw new AssertionError("expected a MelayaException for a money-moving refusal");
                } catch (MelayaException e) {
                    assertEquals(403, e.getStatus(), "exception status");
                    assertEquals("money_moving_requires_app_approval", e.getCode(), "exception code");
                }
            } finally {
                server.stop(0);
            }
        });

        check("connectorTools.callAndWait() polls a staged write through to done", () -> {
            java.util.concurrent.atomic.AtomicInteger pollCount = new java.util.concurrent.atomic.AtomicInteger(0);
            HttpServer server = startServer(exchange -> {
                String path = exchange.getRequestURI().getPath();
                if ("POST".equals(exchange.getRequestMethod()) && path.equals("/api/v1/private/connector-tools/call")) {
                    respondJson(exchange, 202,
                            "{\"status\":\"pending_approval\",\"tool\":\"gmail_send\",\"requestId\":\"req-2\",\"message\":\"pending\"}");
                    return;
                }
                // GET /calls/req-2 — pending on the first poll, done on the second.
                if (pollCount.getAndIncrement() == 0) {
                    respondJson(exchange, 200, "{\"requestId\":\"req-2\",\"tool\":\"gmail_send\",\"status\":\"pending\"}");
                } else {
                    respondJson(exchange, 200,
                            "{\"requestId\":\"req-2\",\"status\":\"done\",\"ok\":true,\"result\":\"wrote:gmail_send\"}");
                }
            });
            try {
                HttpClient http = new HttpClient("mk_test", baseUrl(server));
                ConnectorToolsAPI connectorTools = new ConnectorToolsAPI(http);

                JsonNode outcome = connectorTools.callAndWait("gmail_send", Map.of("to", "a@b.c"),
                        "required", 10L, 5_000L);

                assertEquals("done", outcome.get("status").asText(), "final status");
                assertTrue(outcome.get("ok").asBoolean(), "final ok");
                assertEquals(2, pollCount.get(), "polled callStatus exactly twice before settling on done");
            } finally {
                server.stop(0);
            }
        });

        // ── triggers ──────────────────────────────────────────────────────────

        String tid = "0b6f3c2e-7a41-4c1e-9d55-2f8a1b3c4d5e";

        check("triggers.list() sends project and pipelineName and decodes the array", () -> {
            CapturedRequest[] captured = new CapturedRequest[1];
            HttpServer server = startServer(exchange -> {
                captured[0] = CapturedRequest.capture(exchange);
                respondJson(exchange, 200,
                        "[{\"id\":\"" + tid + "\",\"name\":\"refunds\",\"kind\":\"poll\",\"enabled\":true,\"config\":{\"poll\":{\"service\":\"gmail\"}}}]");
            });
            try {
                TriggersAPI triggers = new TriggersAPI(new HttpClient("mk_test", baseUrl(server)));
                JsonNode resp = triggers.list("acme", "refund bot");

                assertEquals("GET", captured[0].method, "method");
                assertEquals("/api/v1/private/triggers", captured[0].path, "path");
                Map<String, String> q = parseQuery(captured[0].rawQuery);
                assertEquals("acme", q.get("project"), "query.project");
                assertEquals("refund bot", q.get("pipelineName"), "query.pipelineName");
                assertTrue(resp.isArray() && resp.size() == 1, "response is an array of one");
                assertEquals("gmail", resp.at("/0/config/poll/service").asText(), "config stays raw JSON");
            } finally {
                server.stop(0);
            }
        });

        check("triggers.list(null, null) sends no query string", () -> {
            CapturedRequest[] captured = new CapturedRequest[1];
            HttpServer server = startServer(exchange -> {
                captured[0] = CapturedRequest.capture(exchange);
                respondJson(exchange, 200, "[]");
            });
            try {
                new TriggersAPI(new HttpClient("mk_test", baseUrl(server))).list(null, null);
                assertTrue(captured[0].rawQuery == null, "no query string: " + captured[0].rawQuery);
            } finally {
                server.stop(0);
            }
        });

        check("triggers read calls hit the right GET paths and queries", () -> {
            List<CapturedRequest> seen = new java.util.ArrayList<>();
            HttpServer server = startServer(exchange -> {
                seen.add(CapturedRequest.capture(exchange));
                String path = exchange.getRequestURI().getPath();
                String json;
                if (path.endsWith("/stats")) json = "{\"hours\":6,\"byVerdict\":{\"filtered\":{\"n\":3,\"p50\":null,\"p95\":null}},\"filtered\":3,\"sampled\":false}";
                else if (path.endsWith("/limits")) json = "{\"tierClass\":\"pro\",\"triggers\":{\"used\":2,\"cap\":20},\"pollIntervalFloorSec\":60}";
                else if (path.endsWith("/presets")) json = "{\"tier\":\"pro\",\"tierFloorSec\":60,\"presets\":[],\"beta\":{\"allowed\":true}}";
                else if (path.endsWith("/deliveries") || path.endsWith("/approvals") || path.endsWith("/sources")) json = "[]";
                else json = "{\"id\":\"" + tid + "\",\"name\":\"refunds\"}";
                respondJson(exchange, 200, json);
            });
            try {
                TriggersAPI triggers = new TriggersAPI(new HttpClient("mk_test", baseUrl(server)));
                JsonNode one = triggers.get(tid);
                JsonNode deliveries = triggers.deliveries(tid, 25);
                JsonNode stats = triggers.stats(tid, 6);
                JsonNode approvals = triggers.pendingApprovals(tid);
                JsonNode presets = triggers.presets();
                JsonNode limits = triggers.limits();
                JsonNode sources = triggers.sources();

                String base = "/api/v1/private/triggers";
                String[] paths = {
                        base + "/" + tid, base + "/" + tid + "/deliveries", base + "/" + tid + "/stats",
                        base + "/" + tid + "/approvals", base + "/presets", base + "/limits", base + "/sources"};
                assertEquals(paths.length, seen.size(), "request count");
                for (int i = 0; i < paths.length; i++) {
                    assertEquals("GET", seen.get(i).method, "method #" + i);
                    assertEquals(paths[i], seen.get(i).path, "path #" + i);
                }
                assertEquals("25", parseQuery(seen.get(1).rawQuery).get("limit"), "deliveries query.limit");
                assertEquals("6", parseQuery(seen.get(2).rawQuery).get("hours"), "stats query.hours");
                assertTrue(seen.get(0).rawQuery == null, "get sends no query");

                assertEquals("refunds", one.get("name").asText(), "get decodes the record");
                assertTrue(deliveries.isArray() && approvals.isArray() && sources.isArray(), "array responses");
                assertEquals(3, stats.at("/byVerdict/filtered/n").asInt(), "stats byVerdict");
                assertEquals(60, presets.get("tierFloorSec").asInt(), "presets tierFloorSec");
                assertEquals(20, limits.at("/triggers/cap").asInt(), "limits triggers.cap");
            } finally {
                server.stop(0);
            }
        });

        check("triggers.events() sends triggerId, since, a comma list of verdicts and limit", () -> {
            CapturedRequest[] captured = new CapturedRequest[1];
            HttpServer server = startServer(exchange -> {
                captured[0] = CapturedRequest.capture(exchange);
                respondJson(exchange, 200,
                        "{\"events\":[{\"triggerId\":\"" + tid + "\",\"deliveryId\":null,\"eventId\":\"e1\",\"source\":\"poll\",\"verdict\":\"filtered\",\"at\":1790000000000}],"
                                + "\"scanned\":12,\"retention\":{\"maxEvents\":500,\"ttlSec\":86400}}");
            });
            try {
                TriggersAPI triggers = new TriggersAPI(new HttpClient("mk_test", baseUrl(server)));
                JsonNode resp = triggers.events(tid, 1789999999000L, Arrays.asList("filtered", "failed"), 50);

                assertEquals("GET", captured[0].method, "method");
                assertEquals("/api/v1/private/triggers/events", captured[0].path, "path");
                Map<String, String> q = parseQuery(captured[0].rawQuery);
                assertEquals(tid, q.get("triggerId"), "query.triggerId");
                assertEquals("1789999999000", q.get("since"), "query.since");
                assertEquals("filtered,failed", q.get("verdicts"), "query.verdicts is a comma list");
                assertEquals("50", q.get("limit"), "query.limit");
                assertEquals("filtered", resp.at("/events/0/verdict").asText(), "events[0].verdict");
                assertTrue(resp.at("/events/0/deliveryId").isNull(), "an event with no receipt has deliveryId null");
                assertEquals(500, resp.at("/retention/maxEvents").asInt(), "retention.maxEvents");
            } finally {
                server.stop(0);
            }
        });

        check("triggers.test() POSTs {payload} and decodes {accepted, eventId}", () -> {
            CapturedRequest[] captured = new CapturedRequest[1];
            HttpServer server = startServer(exchange -> {
                captured[0] = CapturedRequest.capture(exchange);
                respondJson(exchange, 200, "{\"accepted\":false,\"eventId\":\"test-1\",\"reason\":\"disabled\"}");
            });
            try {
                TriggersAPI triggers = new TriggersAPI(new HttpClient("mk_test", baseUrl(server)));
                JsonNode resp = triggers.test(tid, Map.of("subject", "Refund request"));

                assertEquals("POST", captured[0].method, "method");
                assertEquals("/api/v1/private/triggers/" + tid + "/test", captured[0].path, "path");
                JsonNode sent = MAPPER.readTree(captured[0].body);
                assertEquals("Refund request", sent.at("/payload/subject").asText(), "body.payload");
                assertTrue(!resp.get("accepted").asBoolean(), "accepted");
                assertEquals("test-1", resp.get("eventId").asText(), "eventId");
                assertEquals("disabled", resp.get("reason").asText(), "reason");
            } finally {
                server.stop(0);
            }
        });

        check("triggers.pollStatus() GETs the poll state", () -> {
            CapturedRequest[] captured = new CapturedRequest[1];
            HttpServer server = startServer(exchange -> {
                captured[0] = CapturedRequest.capture(exchange);
                respondJson(exchange, 200,
                        "{\"synced\":true,\"status\":\"ok\",\"lastError\":null,\"armed\":true,\"baselinePending\":false,"
                                + "\"seenCount\":40,\"itemsPublished\":7,\"consecutiveErrors\":0,"
                                + "\"requestedIntervalSec\":30,\"effectiveIntervalSec\":60,\"tierFloorSec\":60}");
            });
            try {
                TriggersAPI triggers = new TriggersAPI(new HttpClient("mk_test", baseUrl(server)));
                JsonNode resp = triggers.pollStatus(tid);

                assertEquals("GET", captured[0].method, "method");
                assertEquals("/api/v1/private/triggers/" + tid + "/poll", captured[0].path, "path");
                assertTrue(resp.get("synced").asBoolean(), "synced");
                assertEquals(60, resp.get("effectiveIntervalSec").asInt(), "effectiveIntervalSec");
            } finally {
                server.stop(0);
            }
        });

        check("triggers.pollTest() POSTs {dry:true} and decodes the dry poll", () -> {
            CapturedRequest[] captured = new CapturedRequest[1];
            HttpServer server = startServer(exchange -> {
                captured[0] = CapturedRequest.capture(exchange);
                respondJson(exchange, 200,
                        "{\"dry\":true,\"ok\":true,\"found\":2,\"baseline\":false,\"wouldPublish\":1,"
                                + "\"items\":[{\"id\":\"m1\",\"preview\":\"Refund\"}],\"samplePayload\":{\"id\":\"m1\"}}");
            });
            try {
                TriggersAPI triggers = new TriggersAPI(new HttpClient("mk_test", baseUrl(server)));
                JsonNode resp = triggers.pollTest(tid);

                assertEquals("POST", captured[0].method, "method");
                assertEquals("/api/v1/private/triggers/" + tid + "/poll/test", captured[0].path, "path");
                assertEquals("{\"dry\":true}", new String(captured[0].body, StandardCharsets.UTF_8), "body");
                assertEquals(1, resp.get("wouldPublish").asInt(), "wouldPublish");
                assertEquals("m1", resp.at("/items/0/id").asText(), "items[0].id");
                assertEquals("m1", resp.at("/samplePayload/id").asText(), "samplePayload");
            } finally {
                server.stop(0);
            }
        });

        check("triggers.pollTest() returns a failed dry poll {ok:false, error} instead of throwing", () -> {
            HttpServer server = startServer(exchange ->
                    respondJson(exchange, 200, "{\"dry\":true,\"ok\":false,\"error\":\"auth_expired\"}"));
            try {
                TriggersAPI triggers = new TriggersAPI(new HttpClient("mk_test", baseUrl(server)));
                JsonNode resp = triggers.pollTest(tid);
                assertTrue(!resp.get("ok").asBoolean(), "ok is false");
                assertEquals("auth_expired", resp.get("error").asText(), "error");
            } finally {
                server.stop(0);
            }
        });

        check("triggers.pollTest() still throws on a 429 throttle", () -> {
            HttpServer server = startServer(exchange ->
                    respondJson(exchange, 429, "{\"error\":\"poll_dry_run_throttled\"}"));
            try {
                TriggersAPI triggers = new TriggersAPI(new HttpClient("mk_test", baseUrl(server)));
                try {
                    triggers.pollTest(tid);
                    throw new AssertionError("expected a MelayaException for a 429");
                } catch (MelayaException e) {
                    assertEquals(429, e.getStatus(), "exception status");
                    assertEquals("poll_dry_run_throttled", e.getCode(), "exception code");
                }
            } finally {
                server.stop(0);
            }
        });

        check("triggers.pollNow() POSTs {dry:false} and pollSync() POSTs to /poll/sync", () -> {
            List<CapturedRequest> seen = new java.util.ArrayList<>();
            HttpServer server = startServer(exchange -> {
                seen.add(CapturedRequest.capture(exchange));
                if (exchange.getRequestURI().getPath().endsWith("/sync")) {
                    respondJson(exchange, 200, "{\"result\":\"armed\"}");
                } else {
                    respondJson(exchange, 200, "{\"dry\":false,\"queued\":true}");
                }
            });
            try {
                TriggersAPI triggers = new TriggersAPI(new HttpClient("mk_test", baseUrl(server)));
                JsonNode now = triggers.pollNow(tid);
                JsonNode sync = triggers.pollSync(tid);

                assertEquals("POST", seen.get(0).method, "pollNow method");
                assertEquals("/api/v1/private/triggers/" + tid + "/poll/test", seen.get(0).path, "pollNow path");
                assertEquals("{\"dry\":false}", new String(seen.get(0).body, StandardCharsets.UTF_8), "pollNow body");
                assertTrue(now.get("queued").asBoolean(), "queued");

                assertEquals("POST", seen.get(1).method, "pollSync method");
                assertEquals("/api/v1/private/triggers/" + tid + "/poll/sync", seen.get(1).path, "pollSync path");
                assertEquals("armed", sync.get("result").asText(), "result");
            } finally {
                server.stop(0);
            }
        });

        check("melaya.agents().triggers() and melaya.triggers() are the same module", () -> {
            Melaya melaya = new Melaya("mk_test", "http://127.0.0.1:1", "ws://127.0.0.1:1");
            assertTrue(melaya.agents().triggers() != null, "agents().triggers() is set");
            assertTrue(melaya.agents().triggers() == melaya.triggers(), "flat alias shares the instance");
        });

        // ── connector accounts, API key, retrieval previews, setInputs, runMessages ──
        // Values with a space prove every path segment goes through the SDK's encode
        // helper (java.net.URLEncoder: a space is sent as '+').

        final String accountsJson = "[{\"id\":\"a1\",\"label\":\"Work\",\"isDefault\":true,\"createdAt\":null}]";

        check("credentials.accounts() GETs the encoded accounts path and returns the array", () -> {
            Roundtrip r = roundtrip(accountsJson, http -> new CredentialsAPI(http).accounts("my svc"));
            assertEquals("GET", r.req.method, "method");
            assertEquals("/api/v1/private/credentials/my%20svc/accounts", r.req.rawPath, "raw path");
            assertTrue(r.resp.isArray(), "response is an array");
            assertTrue(r.resp.get(0).get("isDefault").asBoolean(), "[0].isDefault");
            assertTrue(r.resp.get(0).get("createdAt").isNull(), "[0].createdAt is null");
        });

        check("credentials.addAccount() POSTs {label, fields, currentLabel, makeDefault}", () -> {
            Roundtrip r = roundtrip(accountsJson, http -> new CredentialsAPI(http)
                    .addAccount("my svc", "Shop B", Map.of("apiKey", "k_2"), "Shop A", true));
            assertEquals("POST", r.req.method, "method");
            assertEquals("/api/v1/private/credentials/my%20svc/accounts", r.req.rawPath, "raw path");
            JsonNode b = r.body();
            assertEquals("Shop B", b.get("label").asText(), "body.label");
            assertEquals("k_2", b.at("/fields/apiKey").asText(), "body.fields.apiKey");
            assertEquals("Shop A", b.get("currentLabel").asText(), "body.currentLabel");
            assertTrue(b.get("makeDefault").asBoolean(), "body.makeDefault");
        });

        check("credentials.addAccount() leaves null options out of the body", () -> {
            Roundtrip r = roundtrip(accountsJson, http -> new CredentialsAPI(http)
                    .addAccount("shopify", null, Map.of("apiKey", "k_2"), null, null));
            assertEquals("{\"fields\":{\"apiKey\":\"k_2\"}}", new String(r.req.body, StandardCharsets.UTF_8), "body");
        });

        check("credentials.setDefaultAccount() PUTs {accountId} to /accounts/default", () -> {
            Roundtrip r = roundtrip(accountsJson, http -> new CredentialsAPI(http).setDefaultAccount("my svc", "acc 1"));
            assertEquals("PUT", r.req.method, "method");
            assertEquals("/api/v1/private/credentials/my%20svc/accounts/default", r.req.rawPath, "raw path");
            assertEquals("{\"accountId\":\"acc 1\"}", new String(r.req.body, StandardCharsets.UTF_8), "body");
        });

        check("credentials.identifyAccount() POSTs {} to /accounts/{id}/identify", () -> {
            Roundtrip r = roundtrip(accountsJson, http -> new CredentialsAPI(http).identifyAccount("my svc", "acc 1"));
            assertEquals("POST", r.req.method, "method");
            assertEquals("/api/v1/private/credentials/my%20svc/accounts/acc%201/identify", r.req.rawPath, "raw path");
            assertEquals("{}", new String(r.req.body, StandardCharsets.UTF_8), "body");
        });

        check("credentials.renameAccount() PUTs {label} to /accounts/{id}", () -> {
            Roundtrip r = roundtrip(accountsJson, http -> new CredentialsAPI(http).renameAccount("my svc", "acc 1", "Main"));
            assertEquals("PUT", r.req.method, "method");
            assertEquals("/api/v1/private/credentials/my%20svc/accounts/acc%201", r.req.rawPath, "raw path");
            assertEquals("{\"label\":\"Main\"}", new String(r.req.body, StandardCharsets.UTF_8), "body");
        });

        check("credentials.removeAccount() DELETEs /accounts/{id} with no body", () -> {
            Roundtrip r = roundtrip("[]", http -> new CredentialsAPI(http).removeAccount("my svc", "acc 1"));
            assertEquals("DELETE", r.req.method, "method");
            assertEquals("/api/v1/private/credentials/my%20svc/accounts/acc%201", r.req.rawPath, "raw path");
            assertEquals(0, r.req.body.length, "no body");
            assertTrue(r.resp.isArray() && r.resp.size() == 0, "remaining list is empty");
        });

        check("connectors.accounts() GETs the encoded project accounts path", () -> {
            Roundtrip r = roundtrip(accountsJson, http -> new ConnectorsAPI(http).accounts("my project", "my svc"));
            assertEquals("GET", r.req.method, "method");
            assertEquals("/api/v1/private/projects/my%20project/connectors/my%20svc/accounts", r.req.rawPath, "raw path");
            assertEquals("a1", r.resp.get(0).get("id").asText(), "[0].id");
        });

        check("connectors.addAccount() POSTs {label, fields, currentLabel, makeDefault}", () -> {
            Roundtrip r = roundtrip(accountsJson, http -> new ConnectorsAPI(http)
                    .addAccount("my project", "my svc", "Shop B", Map.of("apiKey", "k_2"), null, false));
            assertEquals("POST", r.req.method, "method");
            assertEquals("/api/v1/private/projects/my%20project/connectors/my%20svc/accounts", r.req.rawPath, "raw path");
            JsonNode b = r.body();
            assertEquals("Shop B", b.get("label").asText(), "body.label");
            assertEquals("k_2", b.at("/fields/apiKey").asText(), "body.fields.apiKey");
            assertTrue(!b.has("currentLabel"), "null currentLabel omitted");
            assertTrue(b.has("makeDefault") && !b.get("makeDefault").asBoolean(), "body.makeDefault false is sent");
        });

        check("connectors.setDefaultAccount() PUTs {accountId} to /accounts/default", () -> {
            Roundtrip r = roundtrip(accountsJson, http -> new ConnectorsAPI(http)
                    .setDefaultAccount("my project", "my svc", "acc 1"));
            assertEquals("PUT", r.req.method, "method");
            assertEquals("/api/v1/private/projects/my%20project/connectors/my%20svc/accounts/default", r.req.rawPath, "raw path");
            assertEquals("{\"accountId\":\"acc 1\"}", new String(r.req.body, StandardCharsets.UTF_8), "body");
        });

        check("connectors.renameAccount() PUTs {label} to /accounts/{id}", () -> {
            Roundtrip r = roundtrip(accountsJson, http -> new ConnectorsAPI(http)
                    .renameAccount("my project", "my svc", "acc 1", "Main"));
            assertEquals("PUT", r.req.method, "method");
            assertEquals("/api/v1/private/projects/my%20project/connectors/my%20svc/accounts/acc%201", r.req.rawPath, "raw path");
            assertEquals("{\"label\":\"Main\"}", new String(r.req.body, StandardCharsets.UTF_8), "body");
        });

        check("connectors.removeAccount() DELETEs /accounts/{id} with no body", () -> {
            Roundtrip r = roundtrip("[]", http -> new ConnectorsAPI(http).removeAccount("my project", "my svc", "acc 1"));
            assertEquals("DELETE", r.req.method, "method");
            assertEquals("/api/v1/private/projects/my%20project/connectors/my%20svc/accounts/acc%201", r.req.rawPath, "raw path");
            assertEquals(0, r.req.body.length, "no body");
        });

        check("account.rotateApiKey() POSTs {} to /api-key and returns {apiKey}", () -> {
            Roundtrip r = roundtrip("{\"apiKey\":\"mk_new\"}", http -> new AccountAPI(http).rotateApiKey());
            assertEquals("POST", r.req.method, "method");
            assertEquals("/api/v1/private/api-key", r.req.rawPath, "raw path");
            assertEquals("{}", new String(r.req.body, StandardCharsets.UTF_8), "body");
            assertEquals("mk_new", r.resp.get("apiKey").asText(), "response.apiKey");
        });

        check("account.revokeApiKey() DELETEs /api-key and returns {ok}", () -> {
            Roundtrip r = roundtrip("{\"ok\":true}", http -> new AccountAPI(http).revokeApiKey());
            assertEquals("DELETE", r.req.method, "method");
            assertEquals("/api/v1/private/api-key", r.req.rawPath, "raw path");
            assertEquals(0, r.req.body.length, "no body");
            assertTrue(r.resp.get("ok").asBoolean(), "response.ok");
        });

        check("account.apiKeyUsage() GETs /api-key/usage", () -> {
            Roundtrip r = roundtrip("{\"total\":42}", http -> new AccountAPI(http).apiKeyUsage());
            assertEquals("GET", r.req.method, "method");
            assertEquals("/api/v1/private/api-key/usage", r.req.rawPath, "raw path");
            assertEquals(null, r.req.rawQuery, "no query");
            assertEquals(42, r.resp.get("total").asInt(), "response.total");
        });

        check("pipelines.docsPreview() GETs /docs/preview with model_name + model_provider", () -> {
            Roundtrip r = roundtrip("{\"files\":[]}", http -> new PipelinesAPI(http)
                    .docsPreview("my pipe", "qwen 3.7", "openrouter"));
            assertEquals("GET", r.req.method, "method");
            assertEquals("/api/v1/private/pipelines/my%20pipe/docs/preview", r.req.rawPath, "raw path");
            Map<String, String> q = parseQuery(r.req.rawQuery);
            assertEquals("qwen 3.7", q.get("model_name"), "query.model_name");
            assertEquals("openrouter", q.get("model_provider"), "query.model_provider");
        });

        check("pipelines.docsPreview() omits null model params", () -> {
            Roundtrip r = roundtrip("{\"files\":[]}", http -> new PipelinesAPI(http).docsPreview("my pipe", null, null));
            assertEquals(null, r.req.rawQuery, "no query string");
        });

        check("pipelines.retrievalPreview() GETs /docs/retrieval/preview", () -> {
            Roundtrip r = roundtrip("{\"documents\":3}", http -> new PipelinesAPI(http).retrievalPreview("my pipe"));
            assertEquals("GET", r.req.method, "method");
            assertEquals("/api/v1/private/pipelines/my%20pipe/docs/retrieval/preview", r.req.rawPath, "raw path");
            assertEquals(3, r.resp.get("documents").asInt(), "response.documents");
        });

        check("pipelines.testRetrieve() POSTs {query, limit} to /docs/retrieval/test_retrieve", () -> {
            Roundtrip r = roundtrip("{\"passages\":[]}", http -> new PipelinesAPI(http)
                    .testRetrieve("my pipe", "refund policy", 3));
            assertEquals("POST", r.req.method, "method");
            assertEquals("/api/v1/private/pipelines/my%20pipe/docs/retrieval/test_retrieve", r.req.rawPath, "raw path");
            assertEquals("{\"query\":\"refund policy\",\"limit\":3}", new String(r.req.body, StandardCharsets.UTF_8), "body");
        });

        check("pipelines.testRetrieve() omits a null limit", () -> {
            Roundtrip r = roundtrip("{\"passages\":[]}", http -> new PipelinesAPI(http)
                    .testRetrieve("my pipe", "refund policy", null));
            assertEquals("{\"query\":\"refund policy\"}", new String(r.req.body, StandardCharsets.UTF_8), "body");
        });

        check("pipelines.setInputs() PUTs {inputs, project} to /inputs", () -> {
            Map<String, Object> decl = new LinkedHashMap<>();
            decl.put("key", "topic");
            decl.put("label", "Topic");
            decl.put("type", "text");
            Roundtrip r = roundtrip("{\"name\":\"my pipe\",\"inputs\":[{\"key\":\"topic\"}]}", http -> new PipelinesAPI(http)
                    .setInputs("my pipe", "my project", List.of(decl)));
            assertEquals("PUT", r.req.method, "method");
            assertEquals("/api/v1/private/pipelines/my%20pipe/inputs", r.req.rawPath, "raw path");
            JsonNode b = r.body();
            assertEquals("my project", b.get("project").asText(), "body.project");
            assertEquals("topic", b.at("/inputs/0/key").asText(), "body.inputs[0].key");
            assertEquals("text", b.at("/inputs/0/type").asText(), "body.inputs[0].type");
            assertEquals("topic", r.resp.at("/inputs/0/key").asText(), "response.inputs[0].key");
        });

        check("pipelines.setInputs() with null inputs sends an empty list", () -> {
            Roundtrip r = roundtrip("{\"inputs\":[]}", http -> new PipelinesAPI(http).setInputs("p", "acme", null));
            assertEquals("{\"inputs\":[],\"project\":\"acme\"}", new String(r.req.body, StandardCharsets.UTF_8), "body");
        });

        check("hitl.runMessages() GETs /runs/{runId}/messages (not /hitl/runs) with limit + cursor", () -> {
            Roundtrip r = roundtrip("{\"messages\":[]}", http -> new HitlAPI(http).runMessages("run 1", 50, "c 2"));
            assertEquals("GET", r.req.method, "method");
            assertEquals("/api/v1/private/runs/run%201/messages", r.req.rawPath, "raw path");
            Map<String, String> q = parseQuery(r.req.rawQuery);
            assertEquals("50", q.get("limit"), "query.limit");
            assertEquals("c 2", q.get("cursor"), "query.cursor");
        });

        System.out.println();
        System.out.printf("UNIT TESTS: %d passed, %d failed (%d total)%n",
                passCount, failCount, passCount + failCount);
        System.exit(failCount == 0 ? 0 : 1);
    }
}
