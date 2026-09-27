<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Project Connectors API — manage credentials at project scope.
 *
 * Maps to /api/v1/private/projects/:project/connectors/*. Project-scoped
 * connectors are isolated per project, letting separate projects use different
 * API keys for the same service.
 *
 * @example
 * ```php
 * $sdk = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * $sdk->connectors->set('my-project', 'openai', ['value' => 'sk-...']);
 * $services = $sdk->connectors->connectedServices('my-project');
 *
 * $handle = $sdk->connectors->envHandle('my-project');
 * // $handle['token'] is used by the runner to decrypt credentials
 * ```
 */
class ConnectorsAPI
{
    public function __construct(private readonly HttpClient $http) {}

    /** List connected services for a project. */
    public function connectedServices(string $project): array
    {
        return $this->http->get(
            '/api/v1/private/projects/' . rawurlencode($project) . '/connectors/services'
        );
    }

    /**
     * Store a connector credential at project scope.
     *
     * @param string $project Project name
     * @param string $service Service name (e.g. 'openai')
     * @param array  $body    ['value' => '...', 'key' => '...', 'label' => '...']
     */
    public function set(string $project, string $service, array $body): array
    {
        return $this->http->put(
            '/api/v1/private/projects/' . rawurlencode($project) . '/connectors/' . rawurlencode($service),
            $body
        );
    }

    /** Delete a project-scoped connector credential. */
    public function delete(string $project, string $service): array
    {
        return $this->http->delete(
            '/api/v1/private/projects/' . rawurlencode($project) . '/connectors/' . rawurlencode($service)
        );
    }

    /**
     * Get a short-lived env-handle token for project-scoped credentials.
     * The runner uses this token to decrypt credentials without a full session.
     */
    public function envHandle(string $project): array
    {
        return $this->http->post(
            '/api/v1/private/projects/' . rawurlencode($project) . '/connectors/env-handle'
        );
    }

    /**
     * Start Google OAuth flow for a project-scoped connector.
     *
     * @param string $project Project name
     * @param array  $body    OAuth config body
     */
    public function googleOAuthStart(string $project, array $body = []): array
    {
        return $this->http->post(
            '/api/v1/private/projects/' . rawurlencode($project) . '/connectors/google/oauth',
            $body ?: null
        );
    }

    /**
     * Share the caller's OWN personal connector credential into the project
     * pool (requires editor/owner on the project). Values stay server-side.
     *
     * @param string        $project             Project name.
     * @param string        $service             Service name (e.g. 'openai').
     * @param string[]|null $googleCapabilities  For Google services: the capability
     *                                           list to share (e.g. ['gmail', 'calendar']).
     */
    public function applyPersonal(string $project, string $service, ?array $googleCapabilities = null): array
    {
        $body = $googleCapabilities !== null ? ['googleCapabilities' => $googleCapabilities] : [];
        return $this->http->post(
            '/api/v1/private/projects/' . rawurlencode($project) . '/connectors/' . rawurlencode($service) . '/apply-personal',
            $body ?: null
        );
    }

    /** Which member shared each connected project connector (usernames only, never values). */
    public function sharedBy(string $project): array
    {
        return $this->http->get('/api/v1/private/projects/' . rawurlencode($project) . '/connectors/shared-by');
    }

    /** List the Google OAuth capabilities actually granted to a project. */
    public function googleStatus(string $project): array
    {
        return $this->http->get('/api/v1/private/projects/' . rawurlencode($project) . '/connectors/google/status');
    }

    /**
     * Select the project's connected Google account used by one capability.
     *
     * @param string $project    Project name.
     * @param string $capability One of: gmail, calendar, drive, sheets, docs,
     *                           search_console, youtube, google_ads, analytics,
     *                           meet, slides.
     * @param string $accountId  24-hex-char connected-account id.
     */
    public function googleSetDefault(string $project, string $capability, string $accountId): array
    {
        return $this->http->put(
            '/api/v1/private/projects/' . rawurlencode($project) . '/connectors/google/default',
            ['capability' => $capability, 'accountId' => $accountId]
        );
    }

    /**
     * Disconnect one Google product, or an entire Google account, from a project.
     *
     * @param string      $project    Project name.
     * @param string      $accountId  24-hex-char connected-account id.
     * @param string|null $capability Omit to disconnect the whole account; pass
     *                                one capability to disconnect only that product.
     */
    public function googleDisconnect(string $project, string $accountId, ?string $capability = null): array
    {
        $body = ['accountId' => $accountId];
        if ($capability !== null) {
            $body['capability'] = $capability;
        }
        return $this->http->delete(
            '/api/v1/private/projects/' . rawurlencode($project) . '/connectors/google/access',
            [],
            $body,
        );
    }

    /**
     * Test a project database connector from the user's runner (reaches
     * IP-allow-listed / VPC hosts that Melaya's cloud cannot reach directly).
     *
     * @param string     $project     Project name.
     * @param string     $service     Database service id.
     * @param array|null $credentials Connection fields to test, if not already stored.
     * @return array{sessionId: string}
     */
    public function dbTestStart(string $project, string $service, ?array $credentials = null): array
    {
        $body = ['service' => $service];
        if ($credentials !== null) {
            $body['credentials'] = $credentials;
        }
        return $this->http->post(
            '/api/v1/private/projects/' . rawurlencode($project) . '/connectors/db-test',
            $body
        );
    }

    /** Poll a project database connector runner-test result. */
    public function dbTestStatus(string $project, string $sessionId): array
    {
        return $this->http->get(
            '/api/v1/private/projects/' . rawurlencode($project) . '/connectors/db-test/' . rawurlencode($sessionId)
        );
    }
}
