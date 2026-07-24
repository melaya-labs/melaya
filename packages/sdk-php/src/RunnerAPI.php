<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Runner API — mint, list, and revoke runner tokens.
 *
 * Maps to /api/v1/private/runner/tokens. Runner tokens (mel_run_ prefix)
 * authenticate the Melaya runner CLI process that executes agent pipelines
 * on your infrastructure.
 *
 * @example
 * ```php
 * $sdk = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * $result = $sdk->runner->createToken(['label' => 'prod-server-1']);
 * // $result['token'] is shown only once — store it securely.
 *
 * $tokens = $sdk->runner->listTokens();
 * $sdk->runner->revokeToken($tokens[0]['id']);
 * ```
 */
class RunnerAPI
{
    public function __construct(private readonly HttpClient $http) {}

    /**
     * Mint a new mel_run_ runner token.
     * The plaintext token is returned only in this response.
     *
     * @param array $body Optional body; may contain ['label' => '...']
     */
    public function createToken(array $body = []): array
    {
        return $this->http->post('/api/v1/private/runner/tokens', $body ?: null);
    }

    /** List all runner tokens for the caller (masked, with last_seen). */
    public function listTokens(): array
    {
        return $this->http->get('/api/v1/private/runner/tokens');
    }

    /** Revoke a runner token by ID. */
    public function revokeToken(string $tokenId): array
    {
        return $this->http->delete('/api/v1/private/runner/tokens/' . rawurlencode($tokenId));
    }
}
