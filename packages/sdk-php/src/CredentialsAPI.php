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

    /** Start Google OAuth flow for credential storage. */
    public function googleOAuthStart(array $body = []): array
    {
        return $this->http->post('/api/v1/private/credentials/google/oauth', $body ?: null);
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
