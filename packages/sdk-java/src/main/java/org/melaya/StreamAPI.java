package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import javax.net.ssl.SSLContext;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebSocket streaming API.
 *
 * <p>Public streams authenticate with {@code ?apiKey=mk_...}. Private streams first
 * mint a short-lived ticket via {@code POST /api/v1/private/private-ticket}, then
 * open the socket with {@code ?wsTicket=...}.
 *
 * <p>TLS verification is disabled when {@code MELAYA_INSECURE_TLS=1}.
 */
public class StreamAPI {

    private final String apiKey;
    private final String wsUrl;
    private final HttpClient http;
    private final SSLContext sslContext;

    StreamAPI(String apiKey, String wsUrl, HttpClient http) {
        this.apiKey = apiKey;
        this.wsUrl = wsUrl.endsWith("/") ? wsUrl.substring(0, wsUrl.length() - 1) : wsUrl;
        this.http = http;

        boolean insecure = "1".equals(System.getenv("MELAYA_INSECURE_TLS"));
        this.sslContext = insecure ? HttpClient.trustAllSslContext() : null;
    }

    /** Live ticker frames. */
    public MelayaStream ticker(String exchange, String symbol, String market) {
        return open("/ws/ticker", buildParams("exchange", exchange, "symbol", symbol, "market", market));
    }

    /** Live order-book frames. */
    public MelayaStream orderbook(String exchange, String symbol, String market, Integer limit) {
        return open("/ws/orderbook", buildParams("exchange", exchange, "symbol", symbol,
                "market", market, "limit", limit != null ? limit.toString() : null));
    }

    /** Live OHLCV candle frames. */
    public MelayaStream ohlcv(String exchange, String symbol, String timeframe, String market) {
        return open("/ws/ohlcv", buildParams("exchange", exchange, "symbol", symbol,
                "timeframe", timeframe, "market", market));
    }

    /** Live public-trade frames. */
    public MelayaStream trades(String exchange, String symbol, String market) {
        return open("/ws/public-trades", buildParams("exchange", exchange, "symbol", symbol, "market", market));
    }

    /** Cross-exchange liquidation firehose. */
    public MelayaStream liquidations(String exchange) {
        return open("/ws/liquidations", buildParams("exchange", exchange));
    }

    // ── Private feeds ─────────────────────────────────────────────────────────

    /**
     * Live strategy events for your account. Mints a ticket, then opens {@code /ws/strategies}.
     */
    public MelayaStream strategies() {
        return openPrivate("/ws/strategies", "strategies", Map.of());
    }

    /**
     * Live private account feed for one connected exchange key.
     * Pass {@code apiKeyId} from {@link AccountAPI#keys()}.
     */
    public MelayaStream privateStream(String exchange, String market, String apiKeyId, String keyId, String symbol) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (exchange != null) body.put("exchange", exchange);
        if (market != null) body.put("market", market);
        if (apiKeyId != null) body.put("apiKeyId", apiKeyId);
        if (keyId != null) body.put("keyId", keyId);
        if (symbol != null) body.put("symbol", symbol);
        return openPrivate("/ws/private", "private", body);
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private MelayaStream open(String path, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(wsUrl).append(path).append("?apiKey=")
                .append(encode(apiKey));
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getValue() != null) {
                sb.append("&").append(encode(e.getKey())).append("=").append(encode(e.getValue()));
            }
        }
        return new MelayaStream(sb.toString(), sslContext);
    }

    private MelayaStream openPrivate(String path, String stream, Map<String, Object> extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stream", stream);
        body.putAll(extra);
        JsonNode resp = http.post("/api/v1/private/private-ticket", body);
        String ticket = resp.get("wsTicket").asText();
        String url = wsUrl + path + "?wsTicket=" + encode(ticket);
        return new MelayaStream(url, sslContext);
    }

    private static Map<String, String> buildParams(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length - 1; i += 2) {
            if (kv[i + 1] != null) m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
