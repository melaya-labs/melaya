package org.melaya

import org.json.JSONObject

/**
 * Credentials API — store, retrieve, test, and delete secrets and service connections at user scope.
 *
 * Credentials are envelope-encrypted at rest. For project-scoped connectors use [ConnectorsAPI].
 *
 * Paths (user-scoped):
 *   - `GET    /api/v1/private/credentials`                    — list all credentials
 *   - `GET    /api/v1/private/credentials/services`           — list connected services
 *   - `GET    /api/v1/private/credentials/operator-profile`   — get operator profile
 *   - `PUT    /api/v1/private/credentials/operator-profile`   — save operator profile
 *   - `GET    /api/v1/private/credentials/:service`           — get credential by service
 *   - `PUT    /api/v1/private/credentials/:service`           — store/update credential
 *   - `DELETE /api/v1/private/credentials/:service`           — delete credential
 *   - `POST   /api/v1/private/credentials/:service/test`      — test a credential
 *   - `GET    /api/v1/private/credentials/models`             — list available AI models
 *   - `GET    /api/v1/private/credentials/melaya-accounts`    — list Melaya sub-accounts
 *
 * RAG paths:
 *   - `POST /api/v1/private/rag/ingest`             — start RAG ingestion
 *   - `GET  /api/v1/private/rag/ingest/:sessionId`  — poll ingestion status
 *   - `POST /api/v1/private/rag/retrieve`           — start RAG retrieval
 *   - `GET  /api/v1/private/rag/retrieve/:sessionId`— poll retrieval status
 *
 * Folder picker:
 *   - `POST /api/v1/private/rag/pick-folder`             — start folder picker
 *   - `GET  /api/v1/private/rag/pick-folder/:sessionId`  — poll picker status
 *
 * OAuth flows (LinkedIn, Luma, Google, CLI, NotebookLM, Telegram):
 *   - `POST   /api/v1/private/credentials/linkedin/connect`         — start LinkedIn OAuth
 *   - `DELETE /api/v1/private/credentials/linkedin/connect`         — cancel LinkedIn OAuth
 *   - `GET    /api/v1/private/credentials/linkedin/connect/status`  — poll status
 *   - `POST   /api/v1/private/credentials/luma/connect`             — start Luma OAuth
 *   - `GET    /api/v1/private/credentials/luma/connect/status`      — poll status
 *   - `DELETE /api/v1/private/credentials/luma/connect`             — cancel Luma OAuth
 *   - `GET    /api/v1/private/credentials/luma/schema`              — Luma registration schema
 *   - `POST   /api/v1/private/credentials/google/oauth`             — start Google OAuth
 *   - `POST   /api/v1/private/credentials/cli-auth`                 — start CLI auth
 *   - `POST   /api/v1/private/credentials/notebooklm/login`         — store NotebookLM creds
 *   - `GET    /api/v1/private/credentials/notebooklm/status`        — NotebookLM status
 *   - `POST   /api/v1/private/credentials/telegram/auth`            — start Telegram auth
 *   - `POST   /api/v1/private/credentials/telegram/auth/code`       — submit SMS code
 *   - `POST   /api/v1/private/credentials/telegram/auth/2fa`        — submit 2FA password
 *
 * @example
 * ```kotlin
 * melaya.credentials.set("openai", value = "sk-...")
 * val ok = melaya.credentials.test("openai")
 * val models = melaya.credentials.listModels()
 * ```
 */
class CredentialsAPI internal constructor(private val http: HttpClient) {

    // ── Core CRUD ─────────────────────────────────────────────────────────────

    /** List all stored credentials (services, OAuth connections, env handles). */
    fun list(): List<JSONObject> {
        val r = http.get("/api/v1/private/credentials")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("credentials")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /** List connected third-party services for the caller. */
    fun connectedServices(): List<JSONObject> {
        val r = http.get("/api/v1/private/credentials/services")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("services")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /**
     * Get a stored credential by [service] name.
     * Pass [key] to retrieve a specific named key within the service.
     */
    fun get(service: String, key: String? = null): JSONObject {
        val query = buildMap<String, Any?> {
            if (key != null) put("key", key)
        }
        return http.get("/api/v1/private/credentials/${enc(service)}", query).asObject()
    }

    /**
     * Store or update a credential (envelope-encrypted at rest).
     *
     * @param service The service name (e.g. `"openai"`, `"telegram"`).
     * @param value   The plaintext secret to store.
     * @param key     Optional key name within the service (for multi-key services).
     * @param label   Optional human-readable label.
     */
    fun set(service: String, value: String, key: String? = null, label: String? = null): JSONObject {
        val body = buildMap<String, Any?> {
            put("value", value)
            if (key != null)   put("key",   key)
            if (label != null) put("label", label)
        }
        return http.put("/api/v1/private/credentials/${enc(service)}", body).asObject()
    }

    /** Delete a stored credential by [service] name. */
    fun delete(service: String): JSONObject {
        return http.delete("/api/v1/private/credentials/${enc(service)}").asObject()
    }

    /** Test a stored credential (e.g. validate an API key against its target service). */
    fun test(service: String): JSONObject {
        return http.post("/api/v1/private/credentials/${enc(service)}/test").asObject()
    }

    // ── Operator profile ─────────────────────────────────────────────────────

    /** Get the operator profile (persona config injected into agent context). */
    fun getOperatorProfile(): JSONObject {
        return http.get("/api/v1/private/credentials/operator-profile").asObject()
    }

    /** Save the operator profile. */
    fun setOperatorProfile(profile: Map<String, Any?>): JSONObject {
        return http.put("/api/v1/private/credentials/operator-profile", profile).asObject()
    }

    // ── AI models ────────────────────────────────────────────────────────────

    /**
     * List available AI models across all configured providers.
     * Collapses 19+ provider fan-out into a parameterized query.
     *
     * @param provider   Filter to a specific provider (e.g. `"anthropic"`, `"openai"`).
     * @param capability Filter by capability (e.g. `"vision"`, `"tool_use"`).
     */
    fun listModels(provider: String? = null, capability: String? = null): List<JSONObject> {
        val query = buildMap<String, Any?> {
            if (provider != null)   put("provider",   provider)
            if (capability != null) put("capability", capability)
        }
        val r = http.get("/api/v1/private/credentials/models", query)
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("models")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /** List Melaya sub-accounts available to the caller. */
    fun melayaAccounts(): List<JSONObject> {
        val r = http.get("/api/v1/private/credentials/melaya-accounts")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("accounts")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    // ── RAG ──────────────────────────────────────────────────────────────────

    /** Start a RAG document ingestion job. Returns a `sessionId` to poll. */
    fun ragIngestStart(body: Map<String, Any?> = emptyMap()): JSONObject {
        return http.post("/api/v1/private/rag/ingest", body).asObject()
    }

    /** Poll RAG ingestion job status by [sessionId]. */
    fun ragIngestStatus(sessionId: String): JSONObject {
        return http.get("/api/v1/private/rag/ingest/${enc(sessionId)}").asObject()
    }

    /** Start a RAG retrieval query. Returns a `sessionId` to poll. */
    fun ragRetrieveStart(body: Map<String, Any?> = emptyMap()): JSONObject {
        return http.post("/api/v1/private/rag/retrieve", body).asObject()
    }

    /** Poll RAG retrieval result by [sessionId]. */
    fun ragRetrieveStatus(sessionId: String): JSONObject {
        return http.get("/api/v1/private/rag/retrieve/${enc(sessionId)}").asObject()
    }

    // ── Folder picker ─────────────────────────────────────────────────────────

    /** Initiate native folder picker for file ingestion. Returns a `sessionId`. */
    fun pickFolderStart(body: Map<String, Any?> = emptyMap()): JSONObject {
        return http.post("/api/v1/private/rag/pick-folder", body).asObject()
    }

    /** Poll folder picker result by [sessionId]. */
    fun pickFolderStatus(sessionId: String): JSONObject {
        return http.get("/api/v1/private/rag/pick-folder/${enc(sessionId)}").asObject()
    }

    // ── LinkedIn OAuth ────────────────────────────────────────────────────────

    /** Start LinkedIn OAuth flow. Returns a `sessionId` / redirect URL. */
    fun linkedinConnectStart(body: Map<String, Any?> = emptyMap()): JSONObject {
        return http.post("/api/v1/private/credentials/linkedin/connect", body).asObject()
    }

    /** Cancel an in-progress LinkedIn OAuth flow. */
    fun linkedinConnectCancel(): JSONObject {
        return http.delete("/api/v1/private/credentials/linkedin/connect").asObject()
    }

    /** Poll LinkedIn OAuth connection status. */
    fun linkedinConnectStatus(): JSONObject {
        return http.get("/api/v1/private/credentials/linkedin/connect/status").asObject()
    }

    // ── Luma OAuth ───────────────────────────────────────────────────────────

    /** Start Luma OAuth flow. */
    fun lumaConnectStart(body: Map<String, Any?> = emptyMap()): JSONObject {
        return http.post("/api/v1/private/credentials/luma/connect", body).asObject()
    }

    /** Poll Luma OAuth connection status. */
    fun lumaConnectStatus(): JSONObject {
        return http.get("/api/v1/private/credentials/luma/connect/status").asObject()
    }

    /** Cancel an in-progress Luma OAuth flow. */
    fun lumaConnectCancel(): JSONObject {
        return http.delete("/api/v1/private/credentials/luma/connect").asObject()
    }

    /** Get the Luma event registration form schema for a given event. */
    fun getLumaRegistrationSchema(eventId: String? = null): JSONObject {
        val query = buildMap<String, Any?> {
            if (eventId != null) put("eventId", eventId)
        }
        return http.get("/api/v1/private/credentials/luma/schema", query).asObject()
    }

    // ── Google OAuth ──────────────────────────────────────────────────────────

    /** Start Google OAuth flow for credential storage. */
    fun googleOAuthStart(body: Map<String, Any?> = emptyMap()): JSONObject {
        return http.post("/api/v1/private/credentials/google/oauth", body).asObject()
    }

    // ── CLI auth ──────────────────────────────────────────────────────────────

    /** Start CLI authentication flow (device-code style). */
    fun cliAuthStart(body: Map<String, Any?> = emptyMap()): JSONObject {
        return http.post("/api/v1/private/credentials/cli-auth", body).asObject()
    }

    // ── NotebookLM ────────────────────────────────────────────────────────────

    /** Store NotebookLM credentials. */
    fun notebookLMLogin(body: Map<String, Any?> = emptyMap()): JSONObject {
        return http.post("/api/v1/private/credentials/notebooklm/login", body).asObject()
    }

    /** Check NotebookLM connection status. */
    fun notebookLMStatus(): JSONObject {
        return http.get("/api/v1/private/credentials/notebooklm/status").asObject()
    }

    // ── Telegram auth ─────────────────────────────────────────────────────────

    /** Start Telegram user auth — initiates the phone-number step. */
    fun telegramAuthStart(phoneNumber: String): JSONObject {
        return http.post(
            "/api/v1/private/credentials/telegram/auth",
            mapOf("phoneNumber" to phoneNumber)
        ).asObject()
    }

    /** Submit the Telegram SMS verification code. */
    fun telegramAuthCode(code: String, extra: Map<String, Any?> = emptyMap()): JSONObject {
        val body = buildMap<String, Any?> { put("code", code); putAll(extra) }
        return http.post("/api/v1/private/credentials/telegram/auth/code", body).asObject()
    }

    /** Submit Telegram 2FA password. */
    fun telegramAuth2fa(password: String): JSONObject {
        return http.post(
            "/api/v1/private/credentials/telegram/auth/2fa",
            mapOf("password" to password)
        ).asObject()
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
