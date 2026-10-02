<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Credentials API — store, retrieve, test, and delete secrets and third-party
 * service connections at user scope.
 *
 * Maps to /api/v1/private/credentials/*. Credentials are envelope-encrypted at rest.
 * Use ConnectorsAPI for project-scoped connector credentials.
 *
 * @example
 * ```php
 * $sdk = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * $sdk->credentials->set('openai', ['value' => 'sk-...', 'label' => 'OpenAI prod']);
 * $result = $sdk->credentials->test('openai');
 * if ($result['ok']) { echo 'Key is valid'; }
 *
 * $models = $sdk->credentials->listModels(['provider' => 'anthropic']);
 * ```
 */
class CredentialsAPI
{
    public function __construct(private readonly HttpClient $http) {}

    // ── Core CRUD ────────────────────────────────────────────────────────────

    /** List all stored credentials (services, OAuth connections, env handles). */
    public function list(): array
    {
        return $this->http->get('/api/v1/private/credentials');
    }

    /** List connected third-party services. */
    public function connectedServices(): array
    {
        return $this->http->get('/api/v1/private/credentials/services');
    }

    /**
     * Get a stored credential value by service name.
     * Pass $key to retrieve a specific named key within the service.
     */
    public function get(string $service, ?string $key = null): array
    {
        $query = $key !== null ? ['key' => $key] : [];
        return $this->http->get('/api/v1/private/credentials/' . rawurlencode($service), $query);
    }

    /**
     * Store or update a credential (envelope-encrypted at rest).
     *
     * @param string $service Service name (e.g. 'openai', 'telegram')
     * @param array  $body    ['value' => '...', 'key' => '...', 'label' => '...']
     */
    public function set(string $service, array $body): array
    {
        return $this->http->put('/api/v1/private/credentials/' . rawurlencode($service), $body);
    }

    /** Delete a stored credential by service name. */
    public function delete(string $service): array
    {
        return $this->http->delete('/api/v1/private/credentials/' . rawurlencode($service));
    }

    /** Test a stored credential (e.g. validate API key against its target service). */
    public function test(string $service): array
    {
        return $this->http->post('/api/v1/private/credentials/' . rawurlencode($service) . '/test');
    }

    // ── Operator profile ─────────────────────────────────────────────────────

    /** Get the operator profile (persona config injected into agent context). */
    public function getOperatorProfile(): array
    {
        return $this->http->get('/api/v1/private/credentials/operator-profile');
    }

    /**
     * Save the operator profile.
     *
     * @param array $profile ['name' => '...', 'persona' => '...', 'context' => '...']
     */
    public function setOperatorProfile(array $profile): array
    {
        return $this->http->put('/api/v1/private/credentials/operator-profile', $profile);
    }

    // ── AI models ────────────────────────────────────────────────────────────

    /**
     * List available AI models across all configured providers.
     * Collapses 19+ provider fan-out into a parameterized query.
     *
     * @param array $params ['provider' => '...', 'capability' => '...']
     */
    public function listModels(array $params = []): array
    {
        return $this->http->get('/api/v1/private/credentials/models', $params);
    }

    // ── Melaya sub-accounts ──────────────────────────────────────────────────

    /** List Melaya sub-accounts available to the caller. */
    public function melayaAccounts(): array
    {
        return $this->http->get('/api/v1/private/credentials/melaya-accounts');
    }

    // ── RAG ─────────────────────────────────────────────────────────────────

    /**
     * Start a RAG document ingestion job.
     *
     * @param array $body Document ingestion config
     */
    public function ragIngestStart(array $body): array
    {
        return $this->http->post('/api/v1/private/rag/ingest', $body);
    }

    /** Poll RAG ingestion job status. */
    public function ragIngestStatus(string $sessionId): array
    {
        return $this->http->get('/api/v1/private/rag/ingest/' . rawurlencode($sessionId));
    }

    /**
     * Start a RAG retrieval query.
     *
     * @param array $body Retrieval query config
     */
    public function ragRetrieveStart(array $body): array
    {
        return $this->http->post('/api/v1/private/rag/retrieve', $body);
    }

    /** Poll RAG retrieval result. */
    public function ragRetrieveStatus(string $sessionId): array
    {
        return $this->http->get('/api/v1/private/rag/retrieve/' . rawurlencode($sessionId));
    }

    // ── Folder picker ────────────────────────────────────────────────────────

    /** Initiate native folder picker for file ingestion. */
    public function pickFolderStart(array $body = []): array
    {
        return $this->http->post('/api/v1/private/rag/pick-folder', $body ?: null);
    }

    /** Poll folder picker result. */
    public function pickFolderStatus(string $sessionId): array
    {
        return $this->http->get('/api/v1/private/rag/pick-folder/' . rawurlencode($sessionId));
    }

    // ── LinkedIn OAuth ───────────────────────────────────────────────────────

    /** Start LinkedIn OAuth flow. */
    public function linkedinConnectStart(array $body = []): array
    {
        return $this->http->post('/api/v1/private/credentials/linkedin/connect', $body ?: null);
    }

    /** Cancel an in-progress LinkedIn OAuth flow. */
    public function linkedinConnectCancel(): array
    {
        return $this->http->delete('/api/v1/private/credentials/linkedin/connect');
    }

    /** Poll LinkedIn OAuth connection status. */
    public function linkedinConnectStatus(): array
    {
        return $this->http->get('/api/v1/private/credentials/linkedin/connect/status');
    }

    // ── Luma OAuth ──────────────────────────────────────────────────────────

    /** Start Luma OAuth flow. */
    public function lumaConnectStart(array $body = []): array
    {
        return $this->http->post('/api/v1/private/credentials/luma/connect', $body ?: null);
    }

    /** Poll Luma OAuth connection status. */
    public function lumaConnectStatus(): array
    {
        return $this->http->get('/api/v1/private/credentials/luma/connect/status');
    }

    /** Cancel an in-progress Luma OAuth flow. */
    public function lumaConnectCancel(): array
    {
        return $this->http->delete('/api/v1/private/credentials/luma/connect');
    }

    /** Get the Luma event registration form schema for a given event. */
    public function getLumaRegistrationSchema(array $params = []): array
    {
        return $this->http->get('/api/v1/private/credentials/luma/schema', $params);
    }

    // ── Google OAuth ─────────────────────────────────────────────────────────

    // ── Several accounts per connector (personal scope) ──────────────────────
    // Field-based connectors can hold several accounts (two mailboxes, two
    // shops). Agents use the DEFAULT account unless a tool call names another
    // one. Only labels and ids ever come back, never credential values.

    /**
     * Accounts connected to one connector. An `id` of `"current"` is a single
     * connection made before accounts existed.
     *
     * Maps to GET /api/v1/private/credentials/{service}/accounts.
     *
     * @param string $service Connector service id (will be rawurlencoded).
     * @return list<array{id: string, label: string, isDefault: bool, createdAt: ?string}>
     */
    public function accounts(string $service): array
    {
        return $this->http->get(self::accountsPath($service));
    }

    /**
     * Add another account to a field-based connector. The connection is tested
     * first.
     *
     * Maps to POST /api/v1/private/credentials/{service}/accounts.
     *
     * @param string                $service      Connector service id (will be rawurlencoded).
     * @param array<string, string> $fields       The connector's credential fields (same keys as `set()`).
     * @param string|null           $label        Display label for the new account.
     * @param string|null           $currentLabel Names the existing single connection when it is
     *                                            adopted as the first account.
     * @param bool|null             $makeDefault  Make the new account the default one.
     * @return list<array{id: string, label: string, isDefault: bool, createdAt: ?string}> The updated list.
     */
    public function addAccount(
        string $service,
        array $fields,
        ?string $label = null,
        ?string $currentLabel = null,
        ?bool $makeDefault = null,
    ): array {
        return $this->http->post(
            self::accountsPath($service),
            self::accountBody($fields, $label, $currentLabel, $makeDefault)
        );
    }

    /**
     * Choose which account the connector (and so every agent) uses.
     *
     * Maps to PUT /api/v1/private/credentials/{service}/accounts/default.
     *
     * @return list<array{id: string, label: string, isDefault: bool, createdAt: ?string}> The updated list.
     */
    public function setDefaultAccount(string $service, string $accountId): array
    {
        return $this->http->put(self::accountsPath($service) . '/default', ['accountId' => $accountId]);
    }

    /**
     * Name an account after the identity its connector reports (runs the
     * connection test on it).
     *
     * Maps to POST /api/v1/private/credentials/{service}/accounts/{accountId}/identify.
     *
     * @return list<array{id: string, label: string, isDefault: bool, createdAt: ?string}> The updated list.
     */
    public function identifyAccount(string $service, string $accountId): array
    {
        return $this->http->post(
            self::accountsPath($service) . '/' . rawurlencode($accountId) . '/identify',
            new \stdClass()
        );
    }

    /**
     * Rename one account (max 80 chars).
     *
     * Maps to PUT /api/v1/private/credentials/{service}/accounts/{accountId}.
     *
     * @return list<array{id: string, label: string, isDefault: bool, createdAt: ?string}> The updated list.
     */
    public function renameAccount(string $service, string $accountId, string $label): array
    {
        return $this->http->put(
            self::accountsPath($service) . '/' . rawurlencode($accountId),
            ['label' => $label]
        );
    }

    /**
     * Remove one account from a connector.
     *
     * Maps to DELETE /api/v1/private/credentials/{service}/accounts/{accountId}.
     *
     * @return list<array{id: string, label: string, isDefault: bool, createdAt: ?string}> The remaining list.
     */
    public function removeAccount(string $service, string $accountId): array
    {
        return $this->http->delete(self::accountsPath($service) . '/' . rawurlencode($accountId));
    }

    private static function accountsPath(string $service): string
    {
        return '/api/v1/private/credentials/' . rawurlencode($service) . '/accounts';
    }

    /**
     * Body shared by the personal and project add-account calls; null fields
     * are omitted.
     *
     * @internal
     * @param array<string, string> $fields
     * @return array<string, mixed>
     */
    public static function accountBody(array $fields, ?string $label, ?string $currentLabel, ?bool $makeDefault): array
    {
        $body = [
            'label'        => $label,
            'fields'       => (object) $fields,
            'currentLabel' => $currentLabel,
            'makeDefault'  => $makeDefault,
        ];
        return array_filter($body, static fn($v) => $v !== null);
    }

    /** Start Google OAuth flow for credential storage. */
    public function googleOAuthStart(array $body = []): array
    {
        return $this->http->post('/api/v1/private/credentials/google/oauth', $body ?: null);
    }

    /** List the Google OAuth capabilities actually granted to the caller. */
    public function googleStatus(): array
    {
        return $this->http->get('/api/v1/private/credentials/google/status');
    }

    /**
     * Select the connected Google account used by one capability.
     *
     * @param string $capability One of: gmail, calendar, drive, sheets, docs,
     *                           search_console, youtube, google_ads, analytics,
     *                           meet, slides.
     * @param string $accountId  24-hex-char connected-account id.
     */
    public function googleSetDefault(string $capability, string $accountId): array
    {
        return $this->http->put('/api/v1/private/credentials/google/default', [
            'capability' => $capability,
            'accountId'  => $accountId,
        ]);
    }

    /**
     * Disconnect one Google product, or an entire Google account.
     *
     * @param string      $accountId  24-hex-char connected-account id.
     * @param string|null $capability Omit to disconnect the whole account; pass
     *                                one capability to disconnect only that product.
     */
    public function googleDisconnect(string $accountId, ?string $capability = null): array
    {
        $body = ['accountId' => $accountId];
        if ($capability !== null) {
            $body['capability'] = $capability;
        }
        return $this->http->delete('/api/v1/private/credentials/google/access', [], $body);
    }

    // ── Database connector test ──────────────────────────────────────────────

    /**
     * Test a database connector from the user's runner (reaches
     * IP-allow-listed / VPC hosts that Melaya's cloud cannot reach directly).
     *
     * @param string     $service     Database service id.
     * @param array|null $credentials Connection fields to test, if not already stored.
     * @return array{sessionId: string}
     */
    public function dbTestStart(string $service, ?array $credentials = null): array
    {
        $body = ['service' => $service];
        if ($credentials !== null) {
            $body['credentials'] = $credentials;
        }
        return $this->http->post('/api/v1/private/credentials/db-test', $body);
    }

    /** Poll a database connector runner-test result. */
    public function dbTestStatus(string $sessionId): array
    {
        return $this->http->get('/api/v1/private/credentials/db-test/' . rawurlencode($sessionId));
    }

    // ── Telegram QR login ────────────────────────────────────────────────────

    /**
     * Start Telegram user QR login.
     *
     * @return array{handle: string}
     */
    public function telegramQrStart(int $apiId, string $apiHash): array
    {
        return $this->http->post('/api/v1/private/credentials/telegram/auth/qr/start', [
            'api_id'   => $apiId,
            'api_hash' => $apiHash,
        ]);
    }

    /** Poll Telegram user QR login. @param string $handle Handle from `telegramQrStart()`, starts with "tgauth_". */
    public function telegramQrPoll(string $handle): array
    {
        return $this->http->post('/api/v1/private/credentials/telegram/auth/qr/poll', ['handle' => $handle]);
    }

    // ── WhatsApp Embedded Signup ──────────────────────────────────────────────

    /** WhatsApp Embedded Signup config (appId/configId). */
    public function whatsappSignupConfig(): array
    {
        return $this->http->get('/api/v1/private/credentials/whatsapp/embedded-signup/config');
    }

    /**
     * WhatsApp Embedded Signup code exchange.
     *
     * @param array $body ['code' => ..., 'phoneNumberId' => ..., 'wabaId' => ..., 'project' => ...]
     */
    public function whatsappSignupExchange(array $body): array
    {
        return $this->http->post('/api/v1/private/credentials/whatsapp/embedded-signup/exchange', $body);
    }

    // ── TikTok ────────────────────────────────────────────────────────────────

    /**
     * Get the connected TikTok account's creator info (nickname, allowed
     * privacy levels, interaction availability) for the compliant
     * Post-to-TikTok approval UI.
     */
    public function tiktokCreatorInfo(): array
    {
        return $this->http->get('/api/v1/private/credentials/tiktok/creator-info');
    }

    // ── Substack email-link login ─────────────────────────────────────────────

    /** Ask Substack to email a sign-in link. */
    public function substackEmailLinkSend(string $email): array
    {
        return $this->http->post('/api/v1/private/credentials/substack/email-link', ['email' => $email]);
    }

    /** Finish Substack sign-in with the emailed link. */
    public function substackEmailLinkRedeem(string $link, ?string $email = null): array
    {
        $body = ['link' => $link];
        if ($email !== null) {
            $body['email'] = $email;
        }
        return $this->http->post('/api/v1/private/credentials/substack/email-link/redeem', $body);
    }

    // ── CLI auth ─────────────────────────────────────────────────────────────

    /** Start CLI authentication flow (device-code style). */
    public function cliAuthStart(array $body = []): array
    {
        return $this->http->post('/api/v1/private/credentials/cli-auth', $body ?: null);
    }

    // ── NotebookLM ───────────────────────────────────────────────────────────

    /** Store NotebookLM credentials. */
    public function notebookLMLogin(array $body): array
    {
        return $this->http->post('/api/v1/private/credentials/notebooklm/login', $body);
    }

    /** Check NotebookLM connection status. */
    public function notebookLMStatus(): array
    {
        return $this->http->get('/api/v1/private/credentials/notebooklm/status');
    }

    // ── Telegram auth ────────────────────────────────────────────────────────

    /** Start Telegram user auth (phone number step). */
    public function telegramAuthStart(string $phoneNumber): array
    {
        return $this->http->post('/api/v1/private/credentials/telegram/auth', [
            'phoneNumber' => $phoneNumber,
        ]);
    }

    /** Submit Telegram SMS verification code. */
    public function telegramAuthCode(string $code): array
    {
        return $this->http->post('/api/v1/private/credentials/telegram/auth/code', ['code' => $code]);
    }

    /** Submit Telegram 2FA password. */
    public function telegramAuth2fa(string $password): array
    {
        return $this->http->post('/api/v1/private/credentials/telegram/auth/2fa', ['password' => $password]);
    }
}
