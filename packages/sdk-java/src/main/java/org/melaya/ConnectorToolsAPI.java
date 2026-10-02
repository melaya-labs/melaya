package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.melaya.MarketAPI.params;

/**
 * Connector tool calls — the same tool surface the MCP server exposes (list
 * connected services, discover tools by business keyword, describe one, test
 * a stored credential, start connecting a service, and call a tool), over
 * plain REST for the SDKs.
 *
 * <p>Maps to {@code /api/v1/private/connector-tools/*}. This is distinct from
 * {@link ConnectorsAPI}, which stores project connector CREDENTIALS — this
 * module only calls the tools a stored credential unlocks, and no method here
 * ever accepts or returns a credential value.
 *
 * <p><strong>Write policy:</strong> read tools always run immediately. Write
 * tools default to {@code approval: "required"} — {@link #call} then returns
 * {@code 202} with a {@code requestId}, staged as the same approval card the
 * Assistant raises in the Melaya app; the write runs once, on the first poll
 * of {@link #callStatus} after the user approves it there. Pass
 * {@code approval: "none"} to run a write immediately instead — it is still
 * audit-logged. Tools that move money or trade are refused under BOTH
 * approval modes.
 *
 * @example
 * <pre>{@code
 * JsonNode services = melaya.agents().connectorTools().services();
 * JsonNode hits     = melaya.agents().connectorTools().search("unread email", null);
 * JsonNode read     = melaya.agents().connectorTools().call("gmail_list_messages", Map.of(), null);
 *
 * // A write, approved in the Melaya app, then polled to completion:
 * JsonNode outcome = melaya.agents().connectorTools()
 *         .callAndWait("gmail_send", Map.of("to", "a@b.c"), "required", null, null);
 * }</pre>
 */
public class ConnectorToolsAPI {

    private static final long DEFAULT_POLL_INTERVAL_MS = 3_000;
    private static final long DEFAULT_TIMEOUT_MS = 10 * 60 * 1000; // 10 minutes

    private final HttpClient http;

    ConnectorToolsAPI(HttpClient http) {
        this.http = http;
    }

    /**
     * List connected services, plus per-service read/write tool counts.
     *
     * @return {@code { services: string[], builtIn: "melaya_core", toolCounts: { [service]: { readTools, writeTools } } }}
     */
    public JsonNode services() {
        return http.get("/api/v1/private/connector-tools/services", null);
    }

    /**
     * Discover tools by plain business keywords (e.g. {@code "unread email"}).
     *
     * @param q     required search text
     * @param limit optional result cap, 1-50 (server default 15); may be {@code null}
     * @return {@code { query, services, tools: ToolInfo[] } }
     */
    public JsonNode search(String q, Integer limit) {
        Map<String, Object> query = params("q", q, "limit", limit);
        return http.get("/api/v1/private/connector-tools/search", query);
    }

    /**
     * Describe one tool: its service, description, read/write-ness, and
     * parameters.
     *
     * @param tool the tool name
     * @throws MelayaException with status 404 if the tool is unknown, or not
     *                         unlocked by your connected services
     */
    public JsonNode describe(String tool) {
        return http.get("/api/v1/private/connector-tools/tools/" + encode(tool), null);
    }

    /**
     * Test the STORED credential for a service. The server allows up to 30 s
     * before returning a {@code 504 timeout}.
     *
     * @param service the service name
     * @return {@code { service, success, message }}
     */
    public JsonNode test(String service) {
        return http.post("/api/v1/private/connector-tools/test", Map.of("service", service));
    }

    /**
     * Start connecting a service. Never takes a secret: OAuth services return
     * a {@code authorizationUrl} for the user to open; API-key and
     * interactive-login services return a {@code connectUrl} where the user
     * completes the connection in the Melaya app.
     *
     * @param service the service name
     * @return {@code { service, kind: "oauth"|"oauth_unavailable"|"interactive_login"|"api_key", authorizationUrl?, connectUrl?, message }}
     */
    public JsonNode connect(String service) {
        return http.post("/api/v1/private/connector-tools/connect", Map.of("service", service));
    }

    /** {@link #call(String, Map, String)} with no arguments and default approval ({@code "required"}). */
    public JsonNode call(String tool) {
        return call(tool, null, null);
    }

    /**
     * Call a connector tool.
     *
     * <p>A read tool, or a write called with {@code approval: "none"}, runs
     * immediately: {@code 200 { status: "done", tool, readOnly, result }}.
     *
     * <p>A write called with {@code approval: "required"} (the default when
     * {@code approval} is {@code null}) is staged instead. The server answers
     * {@code 202}, which this SDK parses and returns exactly like any other
     * response — it is NOT thrown — as
     * {@code { status: "pending_approval", tool, requestId, message }}. Poll
     * {@link #callStatus(String)} with that {@code requestId} once the user
     * has approved or rejected it in the Melaya app, or use
     * {@link #callAndWait} to block until it resolves.
     *
     * <p>Tools that move money or trade are refused under both approval
     * modes: the server answers {@code 403 money_moving_requires_app_approval}
     * and this SDK raises a {@link MelayaException}.
     *
     * @param tool     the tool name
     * @param args     tool arguments; may be {@code null} (sent as {@code {}})
     * @param approval {@code "required"} (default when {@code null}) or {@code "none"}
     */
    public JsonNode call(String tool, Map<String, Object> args, String approval) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tool", tool);
        body.put("args", args != null ? args : Map.of());
        if (approval != null) body.put("approval", approval);
        return http.post("/api/v1/private/connector-tools/call", body);
    }

    /**
     * Outcome of a staged write.
     *
     * @param requestId the {@code requestId} returned by a staged {@link #call}
     * @return {@code { requestId, tool?, status: "pending"|"running"|"expired" }},
     *         or {@code { requestId, status: "done", ok, result?, error? }},
     *         or {@code { requestId, status: "rejected", reason? }}
     * @throws MelayaException with status 404 if the request ID is unknown or expired
     */
    public JsonNode callStatus(String requestId) {
        return http.get("/api/v1/private/connector-tools/calls/" + encode(requestId), null);
    }

    /** {@link #callAndWait(String, Map, String, Long, Long)} with every default. */
    public JsonNode callAndWait(String tool, Map<String, Object> args) {
        return callAndWait(tool, args, null, null, null);
    }

    /**
     * Call a tool and, if it is staged for approval, block synchronously —
     * polling {@link #callStatus(String)} — until the outcome is
     * {@code "done"}, {@code "rejected"}, {@code "expired"}, or the timeout
     * elapses, whichever comes first. A read tool, or a write run with
     * {@code approval: "none"}, returns immediately from the first
     * {@link #call} response without polling.
     *
     * @param tool           the tool name
     * @param args           tool arguments; may be {@code null}
     * @param approval       {@code "required"} (default when {@code null}) or {@code "none"}
     * @param pollIntervalMs delay between polls; default 3000 ms when {@code null}
     * @param timeoutMs      give up and return the last {@code "pending"}/{@code "running"}
     *                       status after this long; default 10 minutes when {@code null}
     */
    public JsonNode callAndWait(String tool, Map<String, Object> args, String approval,
                                 Long pollIntervalMs, Long timeoutMs) {
        JsonNode first = call(tool, args, approval);
        if (!"pending_approval".equals(first.path("status").asText(""))) return first;

        String requestId = first.path("requestId").asText(null);
        long interval = pollIntervalMs != null ? pollIntervalMs : DEFAULT_POLL_INTERVAL_MS;
        long timeout = timeoutMs != null ? timeoutMs : DEFAULT_TIMEOUT_MS;
        long deadline = System.currentTimeMillis() + timeout;

        while (true) {
            JsonNode outcome = callStatus(requestId);
            String status = outcome.path("status").asText("");
            if (!"pending".equals(status) && !"running".equals(status)) return outcome;
            if (System.currentTimeMillis() >= deadline) return outcome;
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("callAndWait interrupted while polling " + requestId, e);
            }
        }
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"); // path segment: space is %20, never +
        } catch (Exception e) {
            return s;
        }
    }
}
