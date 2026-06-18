package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal HTTP client. Injects {@code ?apiKey=} and {@code Authorization: Bearer} on every call.
 * TLS verification is disabled when the env var {@code MELAYA_INSECURE_TLS=1} is set.
 */
public class HttpClient {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final java.net.http.HttpClient http;

    public HttpClient(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        boolean insecure = "1".equals(System.getenv("MELAYA_INSECURE_TLS"));
        if (insecure) {
            this.http = buildInsecureClient();
        } else {
            this.http = java.net.http.HttpClient.newBuilder()
                    .build();
        }
    }

    /** Package-private: lets E2E pass its own pre-built HttpClient (for trust-all). */
    HttpClient(String apiKey, String baseUrl, java.net.http.HttpClient httpClient) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = httpClient;
    }

    private static java.net.http.HttpClient buildInsecureClient() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{
                    new X509TrustManager() {
                        public void checkClientTrusted(X509Certificate[] c, String a) {}
                        public void checkServerTrusted(X509Certificate[] c, String a) {}
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    }
            }, new SecureRandom());
            return java.net.http.HttpClient.newBuilder()
                    .sslContext(ctx)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build insecure TLS HttpClient", e);
        }
    }

    /** Build a trust-all SSLContext (for external use, e.g. WebSocket). */
    public static SSLContext trustAllSslContext() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{
                    new X509TrustManager() {
                        public void checkClientTrusted(X509Certificate[] c, String a) {}
                        public void checkServerTrusted(X509Certificate[] c, String a) {}
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    }
            }, new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build trust-all SSLContext", e);
        }
    }

    /** Builds a full URL with apiKey injected plus any additional query params. */
    String buildUrl(String path, Map<String, Object> query) {
        StringBuilder sb = new StringBuilder(baseUrl);
        if (!path.startsWith("/")) sb.append("/");
        sb.append(path);
        // Always include apiKey
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("apiKey", apiKey);
        if (query != null) {
            for (Map.Entry<String, Object> e : query.entrySet()) {
                if (e.getValue() != null) params.put(e.getKey(), e.getValue());
            }
        }
        sb.append("?");
        boolean first = true;
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (!first) sb.append("&");
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

    public JsonNode delete(String path, Map<String, Object> query) {
        String url = buildUrl(path, query);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .DELETE()
                .build();
        return execute(req);
    }

    private JsonNode execute(HttpRequest req) {
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = resp.body();
            JsonNode data = null;
            if (body != null && !body.isBlank()) {
                try {
                    data = MAPPER.readTree(body);
                } catch (Exception e) {
                    // not JSON; wrap as text node
                    data = MAPPER.getNodeFactory().textNode(body);
                }
            }
            int status = resp.statusCode();
            if (status >= 400) {
                String code = null;
                if (data != null && data.isObject() && data.has("error")) {
                    code = data.get("error").asText(null);
                }
                throw new MelayaException(
                        "Melaya API " + status + (code != null ? " (" + code + ")" : ""),
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
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("HTTP request failed: " + req.uri(), e);
        }
    }

    public String getApiKey() { return apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public java.net.http.HttpClient getHttpImpl() { return http; }
}
