<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Accounts API — user profile, credits, and GDPR data export.
 *
 * Maps to /api/v1/private/accounts/* and /api/v1/private/keys/:keyId.
 *
 * @example
 * ```php
 * $sdk = new Melaya\Melaya(apiKey: getenv('MK'));
 * $credits = $sdk->accounts->credits();
 * ```
 */
class AccountsAPI
{
    public function __construct(private readonly HttpClient $http) {}

    /**
     * Export all user data (GDPR Art 15/20).
     * Returns a JSON blob of all user data.
     */
    public function exportMyData(): array
    {
        return $this->http->post('/api/v1/private/accounts/export');
    }

    /**
     * Remove a stored CEX API key by its ID.
     *
     * @param string $keyId The key ID to remove (e.g. "BINANCEUSDM_0")
     */
    public function removeKey(string $keyId): array
    {
        return $this->http->delete('/api/v1/private/keys/' . rawurlencode($keyId));
    }

    /**
     * Update user display name, avatar, or settings.
     *
     * @param array $body Fields to update (e.g. ['displayName' => 'Alice'])
     */
    public function updateProfile(array $body): array
    {
        return $this->http->patch('/api/v1/private/accounts/profile', $body);
    }

    /** Return current credit balance and transaction history. */
    public function credits(): array
    {
        return $this->http->get('/api/v1/private/accounts/credits');
    }

    /** Return AI/LLM credit balance. */
    public function aiCredits(): array
    {
        return $this->http->get('/api/v1/private/accounts/credits/ai');
    }

    /** Return portfolio-ideas feature credit balance. */
    public function portfolioIdeasCredits(): array
    {
        return $this->http->get('/api/v1/private/accounts/credits/portfolio-ideas');
    }

    /** Return risk-monitoring feature credit balance. */
    public function riskMonitoringCredits(): array
    {
        return $this->http->get('/api/v1/private/accounts/credits/risk-monitoring');
    }
}
