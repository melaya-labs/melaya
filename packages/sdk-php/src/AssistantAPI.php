<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Assistant API — get and save the caller's onboarding / persona profile.
 *
 * Maps to /api/v1/private/assistant/profile. The profile is stored
 * envelope-encrypted (service='assistant_profile') and used to personalise
 * the in-app Friday/assistant experience.
 *
 * @example
 * ```php
 * $sdk = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * $profile = $sdk->assistant->getProfile();
 * $sdk->assistant->setProfile(['name' => 'Antoine', 'goals' => ['grow my trading edge']]);
 * ```
 */
class AssistantAPI
{
    public function __construct(private readonly HttpClient $http) {}

    /** Get the caller's assistant onboarding profile. */
    public function getProfile(): array
    {
        return $this->http->get('/api/v1/private/assistant/profile');
    }

    /**
     * Save the caller's assistant onboarding profile.
     *
     * @param array $profile ['name' => '...', 'goals' => [...], 'context' => '...', 'preferences' => [...]]
     */
    public function setProfile(array $profile): array
    {
        return $this->http->put('/api/v1/private/assistant/profile', $profile);
    }
}
