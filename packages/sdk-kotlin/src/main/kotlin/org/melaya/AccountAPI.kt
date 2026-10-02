package org.melaya

import org.json.JSONObject

/**
 * Account API — authenticated reads about your Melaya account.
 *
 * Paths: `https://api.melaya.org/api/v1/private/…`
 */
class AccountAPI internal constructor(private val http: HttpClient) {

    /**
     * The exchange API keys connected to your account.
     * `apiKey` is masked (display-only); use `apiKeyId` as the reference
     * when launching strategies or minting a private stream ticket.
     */
    fun keys(): List<JSONObject> {
        return http.get("/api/v1/private/keys")
            .asObject().getArray("keys").toJsonObjects()
    }

    /** Tier, plan limits, and live usage counters. */
    fun usage(): JSONObject {
        return http.get("/api/v1/private/usage").asObject()
    }

    /** Status of your platform API key (tier, max concurrent connections). */
    fun apiKeyStatus(): JSONObject {
        return http.get("/api/v1/private/api-key").asObject()
    }

    /**
     * Generate a new platform API key, replacing the current one at once.
     * The new key is returned ONCE as `{apiKey}`.
     *
     * **Careful:** if this client was built with the key being replaced, every later
     * call of this client fails until you build a new [Melaya] client with the returned key.
     */
    fun rotateApiKey(): JSONObject {
        return http.post("/api/v1/private/api-key", emptyMap<String, Any?>()).asObject()
    }

    /**
     * Revoke the platform API key. Returns `{ok}`.
     *
     * **Careful:** if this client was built with that key, it stops working immediately.
     */
    fun revokeApiKey(): JSONObject {
        return http.delete("/api/v1/private/api-key").asObject()
    }

    /** Request counts of your platform API key (current key, merged with your account totals). */
    fun apiKeyUsage(): JSONObject {
        return http.get("/api/v1/private/api-key/usage").asObject()
    }
}
