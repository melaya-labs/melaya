package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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

    /**
     * POST with a per-call timeout override (e.g. a slow ingestion endpoint).
     * Never retried, same as {@link #post(String, Object)}.
     *
     * @param path      request path
     * @param body      request body (JSON-serialized); may be {@code null}
     * @param timeoutMs request timeout in milliseconds, overriding {@link #REQUEST_TIMEOUT_MS}
     */
    public JsonNode post(String path, Object body, long timeoutMs) {
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
        return execute(req, timeoutMs);
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

    /**
     * DELETE with a JSON request body (per the REST bridge rule: DELETE → path
     * params + query + JSON body). {@code body} may be {@code null}, in which
     * case this behaves exactly like {@link #delete(String, Map)}.
     */
    public JsonNode delete(String path, Map<String, Object> query, Object body) {
        String url = buildUrl(path, query);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey);
        if (body != null) {
            String json;
            try {
                json = MAPPER.writeValueAsString(body);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize request body", e);
            }
            builder.header("Content-Type", "application/json")
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        } else {
            builder.DELETE();
        }
        return execute(builder.build());
    }

    /**
     * POST a single file as {@code multipart/form-data; boundary=...}. Builds the
     * multipart body by hand (no third-party dependency). Sends exactly one file
     * part named {@code fieldName}. Never retried (POST is never retried — see
     * {@link #execute(HttpRequest)}). Parses the response exactly like
     * {@link #post(String, Object)} (same error type, same {@code ok: false} check).
     *
     * @param path        request path
     * @param query       optional query params (e.g. {@code key}, {@code project}); may be {@code null}
     * @param fieldName   the multipart field name (e.g. {@code "file"})
     * @param fileBytes   the file content
     * @param filename    the filename reported in the {@code Content-Disposition} header
     * @param contentType the file's MIME type (defaults to {@code application/octet-stream} if {@code null})
     */
    public JsonNode postMultipart(String path, Map<String, Object> query, String fieldName,
                                   byte[] fileBytes, String filename, String contentType) {
        String url = buildUrl(path, query);
        String boundary = "MelayaFormBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipartBody(boundary, fieldName,
                fileBytes != null ? fileBytes : new byte[0],
                filename != null ? filename : "file",
                contentType != null ? contentType : "application/octet-stream");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return execute(req);
    }

    /** Builds a single-file {@code multipart/form-data} body by hand. */
    private static byte[] buildMultipartBody(String boundary, String fieldName, byte[] fileBytes,
                                              String filename, String contentType) {
        String safeFilename = filename.replace("\\", "\\\\").replace("\"", "\\\"");
        String safeFieldName = fieldName.replace("\\", "\\\\").replace("\"", "\\\"");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"" + safeFieldName
                    + "\"; filename=\"" + safeFilename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(fileBytes);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // ByteArrayOutputStream never throws IOException in practice.
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }

    /**
     * GET raw bytes (e.g. a binary file download). Unlike {@link #get}, this never
     * attempts to JSON-parse a successful response body and does not apply the
     * {@code ok: false} envelope check. Retries like other GETs (network error / 429 / 5xx).
     */
    public byte[] getBytes(String path, Map<String, Object> query) {
        String url = buildUrl(path, query);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        return executeBytes(req);
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
        return execute(req, REQUEST_TIMEOUT_MS);
    }

    private JsonNode execute(HttpRequest req, long timeoutMs) {
        boolean isGet = req.method().equalsIgnoreCase("GET");
        int maxRetries = isGet ? MAX_GET_RETRIES : 0;
        IOException lastIoEx = null;

        // Rebuild the request with a per-request timeout.
        req = HttpRequest.newBuilder(req, (k, v) -> true)
                .timeout(Duration.ofMillis(timeoutMs))
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

    /**
     * Same retry/timeout/error-handling shape as {@link #execute(HttpRequest)}, but
     * returns the raw response bytes on success instead of parsing JSON, and never
     * applies the {@code ok: false} envelope check (used by {@link #getBytes}).
     */
    private byte[] executeBytes(HttpRequest req) {
        boolean isGet = req.method().equalsIgnoreCase("GET");
        int maxRetries = isGet ? MAX_GET_RETRIES : 0;
        IOException lastIoEx = null;

        req = HttpRequest.newBuilder(req, (k, v) -> true)
                .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
                .build();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
                int status = resp.statusCode();
                byte[] rawBody = resp.body();

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
                    JsonNode data = null;
                    if (rawBody != null && rawBody.length > 0) {
                        try {
                            data = MAPPER.readTree(rawBody);
                        } catch (Exception ignored) {
                            // not JSON; leave data null (no text-node fallback needed for an error body)
                        }
                    }
                    if (data != null && data.isObject()) {
                        if (data.has("error")) code = data.get("error").asText(null);
                        if (data.has("message")) message = data.get("message").asText(null);
                    }
                    String detail = code != null ? " (" + code + ")" : (message != null ? " (" + message + ")" : "");
                    throw new MelayaException(
                            "Melaya API " + status + detail,
                            status, code, data);
                }
                return rawBody != null ? rawBody : new byte[0];
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
