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
}
