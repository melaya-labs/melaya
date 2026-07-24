package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Phone API — pair and control Android devices connected to the Melaya runner.
 *
 * <p>Maps to {@code /api/v1/private/phone/*}. Agents use these endpoints to
 * drive a paired phone (tap, type, read screen state, launch apps) via the
 * Melaya APK.
 *
 * @example
 * <pre>{@code
 * JsonNode pair   = melaya.phone().pair();
 * String code     = pair.get("code").asText();
 * JsonNode devices = melaya.phone().listDevices();
 * JsonNode tree    = melaya.phone().screenTree();
 * }</pre>
 */
public class PhoneAPI {

    private final HttpClient http;

    PhoneAPI(HttpClient http) {
        this.http = http;
    }

    /**
     * Start phone device pairing — generates a pairing code to enter on the
     * Melaya APK.
     */
    public JsonNode pair() {
        return http.post("/api/v1/private/phone/pair", null);
    }

    /** List all paired phone devices for the authenticated user. */
    public JsonNode listDevices() {
        return http.get("/api/v1/private/phone/devices", null).path("devices");
    }

    /**
     * Revoke a paired phone device by ID.
     *
     * @param deviceId the device ID to revoke
     */
    public JsonNode revokeDevice(String deviceId) {
        return http.delete("/api/v1/private/phone/devices/" + deviceId, null);
    }

    /** Get the current accessibility tree from the paired phone's screen. */
    public JsonNode screenTree() {
        return http.get("/api/v1/private/phone/screen-tree", null);
    }

    /** List installed apps on the paired phone. */
    public JsonNode listApps() {
        return http.get("/api/v1/private/phone/apps", null).path("result").path("apps");
    }

    /**
     * Set the allowlist of apps that agents are permitted to interact with.
     *
     * @param packageNames Android package names to permit
     */
    public JsonNode setAllowedApps(List<String> packageNames) {
        List<Map<String, String>> apps = packageNames.stream()
                .map(packageName -> Map.of("package", packageName))
                .toList();
        return http.put("/api/v1/private/phone/apps/allowed", Map.of("apps", apps));
    }

    /**
     * Register the currently active pipeline run on the phone.
     * Used by agent pipelines that control the phone.
     *
     * @param runId the pipeline run ID
     */
    public JsonNode registerActiveRun(String runId) {
        return http.post("/api/v1/private/phone/active-run",
                MarketAPI.params("runId", runId));
    }
}
