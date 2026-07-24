package org.melaya

import org.json.JSONObject

/**
 * Accounts API — user profile updates, GDPR data export, CEX key management, and credit balances.
 *
 * Note: exchange API key reads (listing connected keys) live in [AccountAPI].
 * This class covers the /api/v1/private/accounts/ and /api/v1/private/keys/ paths.
 *
 * Paths:
 *   - `POST   /api/v1/private/accounts/export`                    — GDPR data export
 *   - `PATCH  /api/v1/private/accounts/profile`                   — update display name / avatar
 *   - `DELETE /api/v1/private/keys/:keyId`                        — remove a CEX API key
 *   - `GET    /api/v1/private/accounts/credits`                   — credit balance + history
 *   - `GET    /api/v1/private/accounts/credits/ai`                — AI credit balance
 *   - `GET    /api/v1/private/accounts/credits/portfolio-ideas`   — portfolio-ideas credits
 *   - `GET    /api/v1/private/accounts/credits/risk-monitoring`   — risk-monitoring credits
 *
 * @example
 * ```kotlin
 * val credits  = melaya.accounts.credits()
 * val aiCredit = melaya.accounts.aiCredits()
 * melaya.accounts.updateProfile(mapOf("displayName" to "Alex"))
 * ```
 */
class AccountsAPI internal constructor(private val http: HttpClient) {

    /**
     * GDPR Art 15/20 data export.
     * Returns a JSON blob of all user data stored on the platform.
     */
    fun exportMyData(): JSONObject {
        return http.post("/api/v1/private/accounts/export").asObject()
    }

    /**
     * Update the user's display name, avatar URL, or other settings.
     * Only the fields present in [profile] are updated.
     */
    fun updateProfile(profile: Map<String, Any?>): JSONObject {
        return http.patch("/api/v1/private/accounts/profile", profile).asObject()
    }

    /** Remove a stored CEX API key by [keyId]. */
    fun removeKey(keyId: String): JSONObject {
        return http.delete("/api/v1/private/keys/${enc(keyId)}").asObject()
    }

    /** Return current credit balance and transaction history. */
    fun credits(): JSONObject {
        return http.get("/api/v1/private/accounts/credits").asObject()
    }

    /** Return AI/LLM credit balance. */
    fun aiCredits(): JSONObject {
        return http.get("/api/v1/private/accounts/credits/ai").asObject()
    }

    /** Return portfolio-ideas feature credit balance. */
    fun portfolioIdeasCredits(): JSONObject {
        return http.get("/api/v1/private/accounts/credits/portfolio-ideas").asObject()
    }

    /** Return risk-monitoring feature credit balance. */
    fun riskMonitoringCredits(): JSONObject {
        return http.get("/api/v1/private/accounts/credits/risk-monitoring").asObject()
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
