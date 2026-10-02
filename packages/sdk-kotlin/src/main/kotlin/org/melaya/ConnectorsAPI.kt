package org.melaya

import org.json.JSONObject

/**
 * Project Connectors API — manage credentials at project scope.
 *
 * Project-scoped connectors are isolated per project, letting different projects
 * use different API keys for the same service. For user-level credentials use [CredentialsAPI].
 *
 * Paths:
 *   - `GET    /api/v1/private/projects/:project/connectors/services`        — list connected services
 *   - `PUT    /api/v1/private/projects/:project/connectors/:service`         — store connector
 *   - `DELETE /api/v1/private/projects/:project/connectors/:service`         — delete connector
 *   - `POST   /api/v1/private/projects/:project/connectors/env-handle`       — get env-handle token
 *   - `POST   /api/v1/private/projects/:project/connectors/google/oauth`     — start Google OAuth
 *   - `POST   /api/v1/private/projects/:project/connectors/:service/apply-personal` — share caller's personal connector
 *   - `GET    /api/v1/private/projects/:project/connectors/shared-by`        — who shared each connector
 *   - `GET    /api/v1/private/projects/:project/connectors/google/status`    — granted Google capabilities
 *   - `PUT    /api/v1/private/projects/:project/connectors/google/default`   — set default Google account
 *   - `DELETE /api/v1/private/projects/:project/connectors/google/access`    — disconnect Google product/account
 *   - `GET    /api/v1/private/projects/:project/connectors/:service/accounts`         — list a connector's accounts
 *   - `POST   /api/v1/private/projects/:project/connectors/:service/accounts`         — add an account (owner)
 *   - `PUT    /api/v1/private/projects/:project/connectors/:service/accounts/default` — choose the default (owner)
 *   - `PUT    /api/v1/private/projects/:project/connectors/:service/accounts/:id`     — rename an account (owner)
 *   - `DELETE /api/v1/private/projects/:project/connectors/:service/accounts/:id`     — remove an account (owner)
 *   - `POST   /api/v1/private/projects/:project/connectors/db-test`          — start a runner DB test
 *   - `GET    /api/v1/private/projects/:project/connectors/db-test/:sessionId` — poll a runner DB test
 *
 * Google capability values: `gmail`, `calendar`, `drive`, `sheets`, `docs`, `search_console`,
 * `youtube`, `google_ads`, `analytics`, `meet`, `slides`. `accountId` is a 24-hex-char id.
 *
 * @example
 * ```kotlin
 * melaya.connectors.set("my-project", "openai", value = "sk-...")
 * val services = melaya.connectors.connectedServices("my-project")
 * ```
 */
class ConnectorsAPI internal constructor(private val http: HttpClient) {

    /** List connected services for a project. */
    fun connectedServices(project: String): List<JSONObject> {
        val r = http.get("/api/v1/private/projects/${enc(project)}/connectors/services")
        return when (r) {
            is org.json.JSONArray -> r.toJsonObjects()
            is JSONObject -> r.optJSONArray("services")?.toJsonObjects() ?: emptyList()
            else -> emptyList()
        }
    }

    /**
     * Store a connector credential at project scope.
     *
     * @param project The project name.
     * @param service The service name (e.g. `"openai"`, `"anthropic"`).
     * @param value   Plaintext credential value.
     * @param key     Optional named key within the service.
     * @param label   Optional human-readable label.
     */
    fun set(
        project: String,
        service: String,
        value: String,
        key: String? = null,
        label: String? = null,
    ): JSONObject {
        val body = buildMap<String, Any?> {
            put("value", value)
            if (key != null)   put("key",   key)
            if (label != null) put("label", label)
        }
        return http.put(
            "/api/v1/private/projects/${enc(project)}/connectors/${enc(service)}",
            body
        ).asObject()
    }

    /** Delete a project-scoped connector credential. */
    fun delete(project: String, service: String): JSONObject {
        return http.delete(
            "/api/v1/private/projects/${enc(project)}/connectors/${enc(service)}"
        ).asObject()
    }

    /**
     * Get a short-lived env-handle token for project-scoped credentials.
     * The runner uses this token to decrypt credentials without a full session.
     */
    fun envHandle(project: String): JSONObject {
        return http.post(
            "/api/v1/private/projects/${enc(project)}/connectors/env-handle"
        ).asObject()
    }

    /** Start Google OAuth flow for a project-scoped connector. */
    fun googleOAuthStart(project: String, body: Map<String, Any?> = emptyMap()): JSONObject {
        return http.post(
            "/api/v1/private/projects/${enc(project)}/connectors/google/oauth",
            body
        ).asObject()
    }

    /**
     * Share the caller's OWN personal connector credential into the project pool
     * (requires editor/owner role on the project). Values stay server-side.
     *
     * @param project            The project name.
     * @param service            The service to share (e.g. `"openai"`).
     * @param googleCapabilities Optional Google capabilities to share (Google services only).
     */
    fun applyPersonal(
        project: String,
        service: String,
        googleCapabilities: List<String>? = null,
    ): JSONObject {
        val body = buildMap<String, Any?> {
            if (googleCapabilities != null) put("googleCapabilities", googleCapabilities)
        }
        return http.post(
            "/api/v1/private/projects/${enc(project)}/connectors/${enc(service)}/apply-personal",
            body
        ).asObject()
    }

    // ── Several accounts per project connector (owner only for writes) ────────

    /** Accounts connected to one project connector (labels and ids only): `{id, label, isDefault, createdAt|null}` each. */
    fun accounts(project: String, service: String): List<JSONObject> {
        return http.get(
            "/api/v1/private/projects/${enc(project)}/connectors/${enc(service)}/accounts"
        ).asAccountList()
    }

    /**
     * Add another account to a project connector (owner; the connection is tested first).
     *
     * @param fields       The connector's credential fields (same keys as [set]).
     * @param label        Optional name for the new account.
     * @param currentLabel Names the existing single connection when it is adopted as the first account.
     * @param makeDefault  Make the new account the default one.
     * @return The updated account list.
     */
    fun addAccount(
        project: String,
        service: String,
        fields: Map<String, String>,
        label: String? = null,
        currentLabel: String? = null,
        makeDefault: Boolean? = null,
    ): List<JSONObject> {
        val body = buildMap<String, Any?> {
            if (label != null) put("label", label)
            put("fields", fields)
            if (currentLabel != null) put("currentLabel", currentLabel)
            if (makeDefault != null) put("makeDefault", makeDefault)
        }
        return http.post(
            "/api/v1/private/projects/${enc(project)}/connectors/${enc(service)}/accounts",
            body
        ).asAccountList()
    }

    /** Choose which account the project connector uses (owner). Returns the updated list. */
    fun setDefaultAccount(project: String, service: String, accountId: String): List<JSONObject> {
        return http.put(
            "/api/v1/private/projects/${enc(project)}/connectors/${enc(service)}/accounts/default",
            mapOf("accountId" to accountId)
        ).asAccountList()
    }

    /** Rename one account of a project connector (owner, max 80 chars). Returns the updated list. */
    fun renameAccount(project: String, service: String, accountId: String, label: String): List<JSONObject> {
        return http.put(
            "/api/v1/private/projects/${enc(project)}/connectors/${enc(service)}/accounts/${enc(accountId)}",
            mapOf("label" to label)
        ).asAccountList()
    }

    /** Remove one account from a project connector (owner). Returns the remaining list. */
    fun removeAccount(project: String, service: String, accountId: String): List<JSONObject> {
        return http.delete(
            "/api/v1/private/projects/${enc(project)}/connectors/${enc(service)}/accounts/${enc(accountId)}"
        ).asAccountList()
    }

    private fun Any?.asAccountList(): List<JSONObject> = when (this) {
        is org.json.JSONArray -> toJsonObjects()
        is JSONObject -> optJSONArray("accounts")?.toJsonObjects() ?: emptyList()
        else -> emptyList()
    }

    /** Which member shared each connected project connector (usernames only, never values). */
    fun sharedBy(project: String): JSONObject {
        return http.get("/api/v1/private/projects/${enc(project)}/connectors/shared-by").asObject()
    }

    // ── Google OAuth (capability-level, project scope) ──────────────────────────

    /** List the Google OAuth capabilities actually granted to a project. */
    fun googleStatus(project: String): JSONObject {
        return http.get("/api/v1/private/projects/${enc(project)}/connectors/google/status").asObject()
    }

    /** Select the project's connected Google account used for one [capability]. */
    fun googleSetDefault(project: String, capability: String, accountId: String): JSONObject {
        return http.put(
            "/api/v1/private/projects/${enc(project)}/connectors/google/default",
            mapOf("capability" to capability, "accountId" to accountId)
        ).asObject()
    }

    /**
     * Disconnect one Google product, or an entire Google account, from a project.
     *
     * @param accountId  24-hex-char connected-account id.
     * @param capability Optional single capability to disconnect; omit to disconnect the whole account.
     */
    fun googleDisconnect(project: String, accountId: String, capability: String? = null): JSONObject {
        val body = buildMap<String, Any?> {
            put("accountId", accountId)
            if (capability != null) put("capability", capability)
        }
        return http.delete(
            "/api/v1/private/projects/${enc(project)}/connectors/google/access",
            body = body
        ).asObject()
    }

    // ── Database connector test (project scope) ─────────────────────────────────

    /**
     * Probe a database connector from the user's own runner (reaches IP-allow-listed / VPC hosts).
     * @return Object with `sessionId` — poll it with [dbTestStatus].
     */
    fun dbTestStart(project: String, service: String, credentials: Map<String, String>? = null): JSONObject {
        val body = buildMap<String, Any?> {
            put("service", service)
            if (credentials != null) put("credentials", credentials)
        }
        return http.post("/api/v1/private/projects/${enc(project)}/connectors/db-test", body).asObject()
    }

    /** Poll a project DB connector runner-test result by [sessionId]. */
    fun dbTestStatus(project: String, sessionId: String): JSONObject {
        return http.get(
            "/api/v1/private/projects/${enc(project)}/connectors/db-test/${enc(sessionId)}"
        ).asObject()
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20") // path segment: space is %20, never +
}
