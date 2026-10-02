package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

import static org.melaya.MarketAPI.params;

/**
 * Credentials API — store, retrieve, test, and delete secrets and
 * third-party service connections at user scope.
 *
 * <p>Maps to {@code /api/v1/private/credentials/*}. Credentials are
 * envelope-encrypted at rest. For project-scoped credentials use
 * {@link ConnectorsAPI}.
 *
 * @example
 * <pre>{@code
 * melaya.credentials().set("openai", Map.of("value", "sk-..."));
 * JsonNode ok = melaya.credentials().test("openai");
 * }</pre>
 */
public class CredentialsAPI {

    private final HttpClient http;

    CredentialsAPI(HttpClient http) {
        this.http = http;
    }

    // ── Core CRUD ─────────────────────────────────────────────────────────────

    /** List all stored credentials (services, OAuth connections, env handles). */
    public JsonNode list() {
        return http.get("/api/v1/private/credentials", null);
    }

    /** List connected third-party services for the caller. */
    public JsonNode connectedServices() {
        return http.get("/api/v1/private/credentials/services", null);
    }

    /**
     * Get a stored credential value by service name.
     * Pass {@code key} to retrieve a specific named key within the service.
     *
     * @param service the service/provider name (e.g. {@code "openai"})
     * @param key     optional named key within the service; may be {@code null}
     */
    public JsonNode get(String service, String key) {
        Map<String, Object> q = key != null ? params("key", key) : null;
        return http.get("/api/v1/private/credentials/" + service, q);
    }

    /**
     * Store or update a credential (envelope-encrypted at rest).
     *
     * @param service the service name
     * @param body    map containing at minimum {@code value}; optionally {@code key}, {@code label}
     */
    public JsonNode set(String service, Map<String, Object> body) {
        return http.put("/api/v1/private/credentials/" + service, body);
    }

    /**
     * Delete a stored credential by service name.
     *
     * @param service the service name to delete
     */
    public JsonNode delete(String service) {
        return http.delete("/api/v1/private/credentials/" + service, null);
    }

    /**
     * Test a stored credential (e.g. validate an API key against its target service).
     *
     * @param service the service name to test
     */
    public JsonNode test(String service) {
        return http.post("/api/v1/private/credentials/" + service + "/test", null);
    }

    // ── Operator profile ──────────────────────────────────────────────────────

    /** Get the operator profile (persona config injected into agent context). */
    public JsonNode getOperatorProfile() {
        return http.get("/api/v1/private/credentials/operator-profile", null);
    }

    /**
     * Save the operator profile.
     *
     * @param profile map with optional keys: {@code name}, {@code persona}, {@code context}
     */
    public JsonNode setOperatorProfile(Map<String, Object> profile) {
        return http.put("/api/v1/private/credentials/operator-profile", profile);
    }

    // ── AI Models ─────────────────────────────────────────────────────────────

    /**
     * List available AI models across all configured providers.
     * Collapses 19+ provider fan-out into a parameterized query.
     *
     * @param provider   optional provider filter (e.g. {@code "openai"}); may be {@code null}
     * @param capability optional capability filter (e.g. {@code "chat"}); may be {@code null}
     */
    public JsonNode listModels(String provider, String capability) {
        Map<String, Object> q = params("provider", provider, "capability", capability);
        return http.get("/api/v1/private/credentials/models", q.isEmpty() ? null : q);
    }

    // ── Melaya sub-accounts ────────────────────────────────────────────────────

    /** List Melaya sub-accounts available to the caller. */
    public JsonNode melayaAccounts() {
        return http.get("/api/v1/private/credentials/melaya-accounts", null);
    }

    // ── RAG (retrieval-augmented generation) ─────────────────────────────────

    /**
     * Start a RAG document ingestion job. Returns a {@code sessionId} to poll.
     *
     * @param body ingestion config (e.g. path, source type, chunking strategy)
     */
    public JsonNode ragIngestStart(Map<String, Object> body) {
        return http.post("/api/v1/private/rag/ingest", body);
    }

    /**
     * Poll the status of a RAG ingestion job.
     *
     * @param sessionId returned by {@link #ragIngestStart}
     */
    public JsonNode ragIngestStatus(String sessionId) {
        return http.get("/api/v1/private/rag/ingest/" + sessionId, null);
    }

    /**
     * Start a RAG retrieval query. Returns a {@code sessionId} to poll.
     *
     * @param body retrieval query config (e.g. query text, top-k)
     */
    public JsonNode ragRetrieveStart(Map<String, Object> body) {
        return http.post("/api/v1/private/rag/retrieve", body);
    }

    /**
     * Poll the result of a RAG retrieval query.
     *
     * @param sessionId returned by {@link #ragRetrieveStart}
     */
    public JsonNode ragRetrieveStatus(String sessionId) {
        return http.get("/api/v1/private/rag/retrieve/" + sessionId, null);
    }

    // ── Folder picker ─────────────────────────────────────────────────────────

    /**
     * Initiate a native folder picker for file ingestion.
     * Returns a {@code sessionId} to poll with {@link #pickFolderStatus}.
     *
     * @param body optional config map (e.g. start path)
     */
    public JsonNode pickFolderStart(Map<String, Object> body) {
        return http.post("/api/v1/private/rag/pick-folder", body);
    }

    /**
     * Poll the folder picker result.
     *
     * @param sessionId returned by {@link #pickFolderStart}
     */
    public JsonNode pickFolderStatus(String sessionId) {
        return http.get("/api/v1/private/rag/pick-folder/" + sessionId, null);
    }

    // ── OAuth flows ───────────────────────────────────────────────────────────

    /** Start a LinkedIn OAuth flow. Returns a redirect/polling state. */
    public JsonNode linkedinConnectStart() {
        return http.post("/api/v1/private/credentials/linkedin/connect", null);
    }

    /** Cancel an in-progress LinkedIn OAuth flow. */
    public JsonNode linkedinConnectCancel() {
        return http.delete("/api/v1/private/credentials/linkedin/connect", null);
    }

    /** Poll LinkedIn OAuth connection status. */
    public JsonNode linkedinConnectStatus() {
        return http.get("/api/v1/private/credentials/linkedin/connect/status", null);
    }

    /** Start a Luma OAuth flow. Returns a redirect/polling state. */
    public JsonNode lumaConnectStart() {
        return http.post("/api/v1/private/credentials/luma/connect", null);
    }

    /** Poll Luma OAuth connection status. */
    public JsonNode lumaConnectStatus() {
        return http.get("/api/v1/private/credentials/luma/connect/status", null);
    }

    /** Cancel an in-progress Luma OAuth flow. */
    public JsonNode lumaConnectCancel() {
        return http.delete("/api/v1/private/credentials/luma/connect", null);
    }

    /** Get the Luma event registration form schema for a given event. */
    public JsonNode getLumaRegistrationSchema() {
        return http.get("/api/v1/private/credentials/luma/schema", null);
    }

    /** Start a Google OAuth flow for credential storage. */
    public JsonNode googleOAuthStart(Map<String, Object> body) {
        return http.post("/api/v1/private/credentials/google/oauth", body);
    }

    /** Start a CLI authentication flow (device-code style). */
    public JsonNode cliAuthStart(Map<String, Object> body) {
        return http.post("/api/v1/private/credentials/cli-auth", body);
    }

    // ── Third-party specific ──────────────────────────────────────────────────

    /**
     * Store NotebookLM credentials.
     *
     * @param body map containing the NotebookLM login credentials
     */
    public JsonNode notebookLMLogin(Map<String, Object> body) {
        return http.post("/api/v1/private/credentials/notebooklm/login", body);
    }

    /** Check NotebookLM connection status. */
    public JsonNode notebookLMStatus() {
        return http.get("/api/v1/private/credentials/notebooklm/status", null);
    }

    /**
     * Start Telegram user auth (phone number step).
     *
     * @param body map containing {@code phoneNumber}
     */
    public JsonNode telegramUserAuthStart(Map<String, Object> body) {
        return http.post("/api/v1/private/credentials/telegram/auth", body);
    }

    /**
     * Submit Telegram SMS verification code.
     *
     * @param body map containing {@code code}
     */
    public JsonNode telegramUserAuthCode(Map<String, Object> body) {
        return http.post("/api/v1/private/credentials/telegram/auth/code", body);
    }

    /**
     * Submit Telegram 2FA password.
     *
     * @param body map containing {@code password}
     */
    public JsonNode telegramUserAuth2fa(Map<String, Object> body) {
        return http.post("/api/v1/private/credentials/telegram/auth/2fa", body);
    }

    // ── Telegram QR login ────────────────────────────────────────────────────

    /**
     * Start Telegram user QR login. Returns {@code { handle, ... }}.
     *
     * @param apiId   Telegram API ID
     * @param apiHash Telegram API hash
     */
    public JsonNode telegramQrStart(long apiId, String apiHash) {
        return http.post("/api/v1/private/credentials/telegram/auth/qr/start",
                Map.of("api_id", apiId, "api_hash", apiHash));
    }

    /**
     * Poll Telegram user QR login.
     *
     * @param handle the handle returned by {@link #telegramQrStart} (starts with {@code "tgauth_"})
     */
    public JsonNode telegramQrPoll(String handle) {
        return http.post("/api/v1/private/credentials/telegram/auth/qr/poll", Map.of("handle", handle));
    }

    // ── WhatsApp Embedded Signup ─────────────────────────────────────────────

    /** WhatsApp Embedded Signup config ({@code appId}/{@code configId}). */
    public JsonNode whatsappSignupConfig() {
        return http.get("/api/v1/private/credentials/whatsapp/embedded-signup/config", null);
    }

    /**
     * Exchange a WhatsApp Embedded Signup code for a connected number.
     *
     * @param body map containing {@code code}, {@code phoneNumberId}, {@code wabaId},
     *             and optionally {@code project}
     */
    public JsonNode whatsappSignupExchange(Map<String, Object> body) {
        return http.post("/api/v1/private/credentials/whatsapp/embedded-signup/exchange", body);
    }

    // ── TikTok ────────────────────────────────────────────────────────────────

    /**
     * Get the connected TikTok account's creator info (nickname, allowed privacy
     * levels, interaction availability) for the compliant Post-to-TikTok approval UI.
     */
    public JsonNode tiktokCreatorInfo() {
        return http.get("/api/v1/private/credentials/tiktok/creator-info", null);
    }

    // ── Substack ──────────────────────────────────────────────────────────────

    /**
     * Ask Substack to email a sign-in link.
     *
     * @param email the email to send the sign-in link to
     */
    public JsonNode substackEmailLinkSend(String email) {
        return http.post("/api/v1/private/credentials/substack/email-link", Map.of("email", email));
    }

    /**
     * Finish Substack sign-in with the emailed link.
     *
     * @param link  the link from the emailed sign-in message
     * @param email optional email to disambiguate; may be {@code null}
     */
    public JsonNode substackEmailLinkRedeem(String link, String email) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("link", link);
        if (email != null) body.put("email", email);
        return http.post("/api/v1/private/credentials/substack/email-link/redeem", body);
    }

    // ── Several accounts per connector (personal scope) ──────────────────────
    // Field-based connectors can hold several accounts (two mailboxes, two shops).
    // Agents use the DEFAULT account unless a tool call names another one. Only
    // labels and ids ever come back, never credential values.

    /**
     * Accounts connected to one connector. An account with {@code id: "current"} is a
     * single connection made before accounts existed.
     *
     * <p>Maps to {@code GET /api/v1/private/credentials/{service}/accounts}.
     *
     * @param service the connector service id (URL-encoded automatically)
     * @return an array of {@code {id, label, isDefault, createdAt}} ({@code createdAt} may be null)
     */
    public JsonNode accounts(String service) {
        return http.get("/api/v1/private/credentials/" + encode(service) + "/accounts", null);
    }

    /**
     * Add another account to a field-based connector. The connection is tested first.
     *
     * <p>Maps to {@code POST /api/v1/private/credentials/{service}/accounts}.
     *
     * @param service      the connector service id
     * @param label        the new account's label; may be {@code null}
     * @param fields       the connector's credential fields (same keys as {@link #set})
     * @param currentLabel names the existing single connection when it is adopted as the
     *                     first account; may be {@code null}
     * @param makeDefault  make the new account the default; may be {@code null}
     * @return the updated account list
     */
    public JsonNode addAccount(String service, String label, Map<String, String> fields,
                               String currentLabel, Boolean makeDefault) {
        return http.post("/api/v1/private/credentials/" + encode(service) + "/accounts",
                accountBody(label, fields, currentLabel, makeDefault));
    }

    /**
     * Choose which account the connector (and so every agent) uses.
     *
     * <p>Maps to {@code PUT /api/v1/private/credentials/{service}/accounts/default}.
     *
     * @return the updated account list
     */
    public JsonNode setDefaultAccount(String service, String accountId) {
        return http.put("/api/v1/private/credentials/" + encode(service) + "/accounts/default",
                Map.of("accountId", accountId));
    }

    /**
     * Name an account after the identity its connector reports (runs the connection
     * test on it).
     *
     * <p>Maps to {@code POST /api/v1/private/credentials/{service}/accounts/{accountId}/identify}.
     *
     * @return the updated account list
     */
    public JsonNode identifyAccount(String service, String accountId) {
        return http.post("/api/v1/private/credentials/" + encode(service) + "/accounts/"
                + encode(accountId) + "/identify", Map.of());
    }

    /**
     * Rename one account (max 80 chars).
     *
     * <p>Maps to {@code PUT /api/v1/private/credentials/{service}/accounts/{accountId}}.
     *
     * @return the updated account list
     */
    public JsonNode renameAccount(String service, String accountId, String label) {
        return http.put("/api/v1/private/credentials/" + encode(service) + "/accounts/" + encode(accountId),
                Map.of("label", label));
    }

    /**
     * Remove one account from a connector.
     *
     * <p>Maps to {@code DELETE /api/v1/private/credentials/{service}/accounts/{accountId}}.
     *
     * @return the remaining account list
     */
    public JsonNode removeAccount(String service, String accountId) {
        return http.delete("/api/v1/private/credentials/" + encode(service) + "/accounts/" + encode(accountId),
                null);
    }

    /** Body of an add-account call; null values are left out. Shared with {@link ConnectorsAPI}. */
    static Map<String, Object> accountBody(String label, Map<String, String> fields,
                                           String currentLabel, Boolean makeDefault) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        if (label != null) body.put("label", label);
        body.put("fields", fields == null ? Map.of() : fields);
        if (currentLabel != null) body.put("currentLabel", currentLabel);
        if (makeDefault != null) body.put("makeDefault", makeDefault);
        return body;
    }

    // ── Google OAuth (personal) ──────────────────────────────────────────────

    /** List the Google OAuth capabilities actually granted to the caller. */
    public JsonNode googleStatus() {
        return http.get("/api/v1/private/credentials/google/status", null);
    }

    /**
     * Select the connected Google account used by one capability.
     *
     * @param capability a Google capability: {@code gmail}, {@code calendar}, {@code drive},
     *                   {@code sheets}, {@code docs}, {@code search_console}, {@code youtube},
     *                   {@code google_ads}, {@code analytics}, {@code meet}, {@code slides}
     * @param accountId  the 24-hex-char connected-account ID
     */
    public JsonNode googleSetDefault(String capability, String accountId) {
        return http.put("/api/v1/private/credentials/google/default",
                Map.of("capability", capability, "accountId", accountId));
    }

    /**
     * Disconnect one Google product, or an entire Google account.
     * Sent as a DELETE with a JSON body (not query params).
     *
     * @param accountId  the connected-account ID to disconnect
     * @param capability optional single capability to disconnect; {@code null} disconnects
     *                   the whole account
     */
    public JsonNode googleDisconnect(String accountId, String capability) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("accountId", accountId);
        if (capability != null) body.put("capability", capability);
        return http.delete("/api/v1/private/credentials/google/access", null, body);
    }

    // ── Database connector test ──────────────────────────────────────────────

    /**
     * Test a database connector from the user's runner (reaches IP-allow-listed /
     * VPC hosts). Returns {@code { sessionId, ... }} to poll with {@link #dbTestStatus}.
     *
     * @param service     the database service name
     * @param credentials optional connection credentials; may be {@code null}
     */
    public JsonNode dbTestStart(String service, Map<String, Object> credentials) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("service", service);
        if (credentials != null) body.put("credentials", credentials);
        return http.post("/api/v1/private/credentials/db-test", body);
    }

    /** Poll a database connector runner-test result. */
    public JsonNode dbTestStatus(String sessionId) {
        return http.get("/api/v1/private/credentials/db-test/" + sessionId, null);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"); // path segment: space is %20, never +
        } catch (Exception e) {
            return s;
        }
    }
}
