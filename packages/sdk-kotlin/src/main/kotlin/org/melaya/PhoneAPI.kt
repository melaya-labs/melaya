package org.melaya

import org.json.JSONObject

/**
 * Phone API — pair and control Android devices connected to the Melaya runner.
 *
 * Agents use these endpoints to drive a paired phone (tap, type, read screen state,
 * launch apps) via the Melaya APK. Pair the device first with [pair], then use the
 * other methods to query and control it.
 *
 * Paths:
 *   - `POST   /api/v1/private/phone/pair`           — start pairing (generates pairing code)
 *   - `GET    /api/v1/private/phone/devices`         — list paired devices
 *   - `DELETE /api/v1/private/phone/devices/:id`     — revoke a device
 *   - `GET    /api/v1/private/phone/screen-tree`     — current accessibility tree
 *   - `GET    /api/v1/private/phone/apps`            — list installed apps
 *   - `PUT    /api/v1/private/phone/apps/allowed`    — set allowed-apps allowlist
 *   - `POST   /api/v1/private/phone/active-run`      — register active pipeline run
 *
 * @example
 * ```kotlin
 * val pair = melaya.phone.pair()
 * println("Enter code on the Melaya APK: ${pair.getString("code")}")
 *
 * val devices = melaya.phone.listDevices()
 * val tree    = melaya.phone.screenTree()
 * ```
 */
class PhoneAPI internal constructor(private val http: HttpClient) {

    /** Start phone device pairing — generates a pairing code to enter on the Melaya APK. */
    fun pair(): JSONObject {
        return http.post("/api/v1/private/phone/pair").asObject()
    }

    /** List all paired phone devices for the authenticated user. */
    fun listDevices(): List<JSONObject> {
        val r = http.get("/api/v1/private/phone/devices")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("devices")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /** Revoke a paired phone device by [deviceId]. */
    fun revokeDevice(deviceId: String): JSONObject {
        return http.delete("/api/v1/private/phone/devices/${enc(deviceId)}").asObject()
    }

    /** Get the current accessibility tree from the paired phone's screen. */
    fun screenTree(): JSONObject {
        return http.get("/api/v1/private/phone/screen-tree").asObject()
    }

    /** List installed apps on the paired phone. */
    fun listApps(): List<JSONObject> {
        val r = http.get("/api/v1/private/phone/apps")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONObject("result")
                ?.optJSONArray("apps")
                ?.toJsonObjects()
                ?: emptyList()
            else -> emptyList()
        }
    }

    /**
     * Set the allowlist of apps that agents are permitted to interact with.
     * Pass a list of Android package names.
     */
    fun setAllowedApps(packageNames: List<String>): JSONObject {
        return http.put(
            "/api/v1/private/phone/apps/allowed",
            mapOf("apps" to packageNames.map { mapOf("package" to it) })
        ).asObject()
    }

    /** Register the currently active pipeline run on the phone (used by agents). */
    fun registerActiveRun(runId: String): JSONObject {
        return http.post(
            "/api/v1/private/phone/active-run",
            mapOf("runId" to runId)
        ).asObject()
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
