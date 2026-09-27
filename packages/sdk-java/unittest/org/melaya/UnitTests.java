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
        final String rawQuery;
        final Headers headers;
        final byte[] body;

        private CapturedRequest(String method, String path, String rawQuery, Headers headers, byte[] body) {
            this.method = method;
            this.path = path;
            this.rawQuery = rawQuery;
            this.headers = headers;
            this.body = body;
        }

        static CapturedRequest capture(HttpExchange exchange) throws IOException {
            byte[] body = exchange.getRequestBody().readAllBytes();
            return new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders(),
                    body);
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

        System.out.println();
        System.out.printf("UNIT TESTS: %d passed, %d failed (%d total)%n",
                passCount, failCount, passCount + failCount);
        System.exit(failCount == 0 ? 0 : 1);
    }
}
