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
}
