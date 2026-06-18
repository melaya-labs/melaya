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
}
