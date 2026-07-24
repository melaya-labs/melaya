package org.melaya

import org.json.JSONObject

/**
 * Assistant API — get and save the caller's onboarding / persona profile.
 *
 * The profile is stored envelope-encrypted (service=`assistant_profile`) and used
 * to personalise the Melaya AI assistant experience.
 *
 * Paths:
 *   - `GET /api/v1/private/assistant/profile` — get assistant profile
 *   - `PUT /api/v1/private/assistant/profile` — save assistant profile
 *
 * @example
 * ```kotlin
 * val profile = melaya.assistant.getProfile()
 * melaya.assistant.setProfile(mapOf("name" to "Alex", "goals" to listOf("grow my trading edge")))
 * ```
 */
class AssistantAPI internal constructor(private val http: HttpClient) {

    /** Get the caller's assistant onboarding profile (decrypted from service=`assistant_profile`). */
    fun getProfile(): JSONObject {
        return http.get("/api/v1/private/assistant/profile").asObject()
    }

    /**
     * Save the caller's assistant onboarding profile.
     *
     * @param profile A map with optional keys: `name`, `goals` (list), `context`, `preferences` (map).
     */
    fun setProfile(profile: Map<String, Any?>): JSONObject {
        return http.put("/api/v1/private/assistant/profile", profile).asObject()
    }
}
