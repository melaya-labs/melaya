package org.melaya

import org.json.JSONObject

/**
 * Runner API — mint, list, and revoke runner tokens (`mel_run_` prefix).
 *
 * Runner tokens authenticate the Melaya runner CLI process that executes
 * agent pipelines on your infrastructure.
 *
 * Paths:
 *   - `POST   /api/v1/private/runner/tokens`           — mint a new token
 *   - `GET    /api/v1/private/runner/tokens`           — list all tokens (masked)
 *   - `DELETE /api/v1/private/runner/tokens/:tokenId`  — revoke a token
 *
 * @example
 * ```kotlin
 * val result = melaya.runner.createToken(label = "prod-server-1")
 * // result.getString("token") — store securely, shown only once
 * val tokens = melaya.runner.listTokens()
 * melaya.runner.revokeToken(tokens[0].getString("id"))
 * ```
 */
class RunnerAPI internal constructor(private val http: HttpClient) {

    /**
     * Mint a new runner token.
     * The plaintext token is returned only in this response — store it securely.
     */
    fun createToken(label: String? = null): JSONObject {
        val body = buildMap<String, Any?> {
            if (label != null) put("label", label)
        }
        return http.post("/api/v1/private/runner/tokens", body).asObject()
    }

    /** List all runner tokens for the caller (masked, with last_seen timestamp). */
    fun listTokens(): List<JSONObject> {
        val r = http.get("/api/v1/private/runner/tokens")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("tokens")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /** Revoke a runner token by its [tokenId]. */
    fun revokeToken(tokenId: String): JSONObject {
        return http.delete("/api/v1/private/runner/tokens/${enc(tokenId)}").asObject()
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
