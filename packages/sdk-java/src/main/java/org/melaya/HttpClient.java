package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Internal HTTP client. Sends the API key ONLY as an {@code Authorization: Bearer}
 * header on every call — never in the URL query string, so the key cannot leak
 * into access logs, proxies, or referrer headers. TLS certificate and hostname
 * verification always use the JVM trust configuration.
 */
public class HttpClient {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final java.net.http.HttpClient http;

    public HttpClient(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        this.http = java.net.http.HttpClient.newBuilder().build();
    }

    /** Builds a full URL with the given query params.
     *
     * SECURITY: the API key is NOT injected here. It travels only in the
     * {@code Authorization: Bearer} header (set on every request below), so it
     * can never leak into access logs, proxies, or referrer headers. */
    String buildUrl(String path, Map<String, Object> query) {
        StringBuilder sb = new StringBuilder(baseUrl);
        if (!path.startsWith("/")) sb.append("/");
        sb.append(path);
        Map<String, Object> params = new LinkedHashMap<>();
        if (query != null) {
            for (Map.Entry<String, Object> e : query.entrySet()) {
                if (e.getValue() != null) params.put(e.getKey(), e.getValue());
            }
        }
        boolean first = true;
        for (Map.Entry<String, Object> e : params.entrySet()) {
            sb.append(first ? "?" : "&");
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
              .append("=")
              .append(URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
            first = false;
        }
        return sb.toString();
    }

    public JsonNode get(String path, Map<String, Object> query) {
        String url = buildUrl(path, query);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        return execute(req);
    }

    public JsonNode post(String path, Object body) {
        String url = buildUrl(path, null);
        String json;
        try {
            json = body == null ? "" : MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return execute(req);
    }

    public JsonNode put(String path, Object body) {
        String url = buildUrl(path, null);
        String json;
        try {
            json = body == null ? "" : MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return execute(req);
    }

    public JsonNode patch(String path, Object body) {
        String url = buildUrl(path, null);
        String json;
        try {
            json = body == null ? "" : MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return execute(req);
    }

    public JsonNode delete(String path, Map<String, Object> query) {
        String url = buildUrl(path, query);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .DELETE()
                .build();
        return execute(req);
    }

    /** Per-request timeout in milliseconds (default 30 s). */
    static final long REQUEST_TIMEOUT_MS = 30_000;

    /**
     * Max retries for idempotent GET requests on network error / 429 / 5xx.
     * Non-GET methods are never retried.
     */
    private static final int MAX_GET_RETRIES = 2;

    /** Base backoff for retries (ms). Actual delay = base * 2^attempt + jitter(0..200 ms). */
    private static final long RETRY_BASE_MS = 500;

    private JsonNode execute(HttpRequest req) {
        boolean isGet = req.method().equalsIgnoreCase("GET");
        int maxRetries = isGet ? MAX_GET_RETRIES : 0;
        IOException lastIoEx = null;

        // Rebuild the request with a per-request timeout.
        req = HttpRequest.newBuilder(req, (k, v) -> true)
                .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
                .build();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = resp.statusCode();
                String rawBody = resp.body();
                JsonNode data = null;
                if (rawBody != null && !rawBody.isBlank()) {
                    try {
                        data = MAPPER.readTree(rawBody);
                    } catch (Exception e) {
                        // not JSON; wrap as text node
                        data = MAPPER.getNodeFactory().textNode(rawBody);
                    }
                }
                // Retry GET on 429 or 5xx (bounded, with Retry-After support)
                if (isGet && (status == 429 || status >= 500) && attempt < maxRetries) {
                    long delay = backoffDelay(attempt);
                    String retryAfter = resp.headers().firstValue("Retry-After").orElse(null);
                    if (retryAfter != null) {
                        try { delay = Long.parseLong(retryAfter.trim()) * 1000L; } catch (NumberFormatException ignored) {}
                    }
                    sleep(delay, req);
                    continue;
                }
                if (status >= 400) {
                    String code = null;
                    String message = null;
                    if (data != null && data.isObject()) {
                        if (data.has("error")) code = data.get("error").asText(null);
                        if (data.has("message")) message = data.get("message").asText(null);
                    }
                    // Never surface the raw auth header in exceptions
                    String detail = code != null ? " (" + code + ")" : (message != null ? " (" + message + ")" : "");
                    throw new MelayaException(
                            "Melaya API " + status + detail,
                            status, code, data);
                }
                // Check ok: false envelope
                if (data != null && data.isObject()) {
                    JsonNode okNode = data.get("ok");
                    if (okNode != null && okNode.isBoolean() && !okNode.asBoolean()) {
                        String code = data.has("error") ? data.get("error").asText(null) : null;
                        throw new MelayaException(
                                "Melaya API request failed" + (code != null ? ": " + code : ""),
                                status, code, data);
                    }
                }
                return data;
            } catch (MelayaException me) {
                throw me; // never retry business-logic errors
            } catch (IOException e) {
                lastIoEx = e;
                if (isGet && attempt < maxRetries) {
                    sleep(backoffDelay(attempt), req);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("HTTP request interrupted: " + req.uri(), e);
            }
        }
        throw new RuntimeException(
                "HTTP request failed after " + maxRetries + " retries: " + req.uri(), lastIoEx);
    }

    /** Exponential backoff with up to 200 ms of random jitter. */
    private static long backoffDelay(int attempt) {
        long base = RETRY_BASE_MS * (1L << attempt); // 500, 1000
        long jitter = ThreadLocalRandom.current().nextLong(0, 200);
        return base + jitter;
    }

    private static void sleep(long ms, HttpRequest req) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("HTTP retry interrupted: " + req.uri(), ie);
        }
    }

    public String getApiKey() { return apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public java.net.http.HttpClient getHttpImpl() { return http; }
}
