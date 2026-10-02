<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Account API — authenticated reads about your Melaya account.
 *
 * Maps to https://api.melaya.org/api/v1/private/*.
 * Connected-exchange key references (masked), tier limits, and live usage counters.
 */
class AccountAPI
{
    public function __construct(private readonly HttpClient $http) {}

    /**
     * The exchange API keys connected to your account.
     * `apiKey` is masked (display-only); use `apiKeyId` (e.g. `BINANCEUSDM_0`)
     * as the reference when launching strategies or minting a private stream ticket.
     */
    public function keys(): array
    {
        return $this->http->get('/api/v1/private/keys')['keys'];
    }

    /** Tier, plan limits, and live usage counters (mirrors the dashboard usage page). */
    public function usage(): array
    {
        return $this->http->get('/api/v1/private/usage');
    }

    /** Status of your platform API key (tier, max concurrent connections). */
    public function apiKeyStatus(): array
    {
        return $this->http->get('/api/v1/private/api-key');
    }

    /**
     * Generate a new platform API key, replacing the current one at once. The
     * new key is returned ONCE.
     *
     * WARNING: if this client was built with the key being rotated, every
     * later call of this client fails until you build a new client with the
     * returned key.
     *
     * Maps to POST /api/v1/private/api-key.
     *
     * @return array{apiKey: string}
     */
    public function rotateApiKey(): array
    {
        return $this->http->post('/api/v1/private/api-key', new \stdClass());
    }

    /**
     * Revoke the platform API key. If this client uses that key, it stops
     * working immediately.
     *
     * Maps to DELETE /api/v1/private/api-key.
     *
     * @return array{ok: bool}
     */
    public function revokeApiKey(): array
    {
        return $this->http->delete('/api/v1/private/api-key');
    }

    /**
     * Request counts of your platform API key (current key, merged with your
     * account totals).
     *
     * Maps to GET /api/v1/private/api-key/usage.
     */
    public function apiKeyUsage(): array
    {
        return $this->http->get('/api/v1/private/api-key/usage');
    }
}
