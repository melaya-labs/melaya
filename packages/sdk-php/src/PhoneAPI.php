<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Phone API — pair and control Android devices connected to the Melaya runner.
 *
 * Maps to /api/v1/private/phone/*. Agents use these endpoints to drive a
 * paired phone (tap, type, read screen state, launch apps) via the Melaya APK.
 *
 * @example
 * ```php
 * $sdk = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * $pair = $sdk->phone->pair();
 * echo $pair['code']; // show this on screen; user enters it in the APK
 *
 * $devices = $sdk->phone->listDevices();
 * $tree = $sdk->phone->screenTree();
 * ```
 */
class PhoneAPI
{
    public function __construct(private readonly HttpClient $http) {}

    /**
     * Start phone device pairing.
     * Returns a pairing code to enter on the Melaya APK.
     */
    public function pair(): array
    {
        return $this->http->post('/api/v1/private/phone/pair');
    }

    /** List all paired phone devices for the authenticated user. */
    public function listDevices(): array
    {
        $response = $this->http->get('/api/v1/private/phone/devices');
        return $response['devices'] ?? [];
    }

    /** Revoke a paired phone device by ID. */
    public function revokeDevice(string $deviceId): array
    {
        return $this->http->delete('/api/v1/private/phone/devices/' . rawurlencode($deviceId));
    }

    /** Get the current accessibility tree from the paired phone's screen. */
    public function screenTree(): array
    {
        return $this->http->get('/api/v1/private/phone/screen-tree');
    }

    /** List installed apps on the paired phone. */
    public function listApps(): array
    {
        $response = $this->http->get('/api/v1/private/phone/apps');
        return $response['result']['apps'] ?? [];
    }

    /**
     * Set the allowlist of apps that agents are permitted to interact with.
     *
     * @param string[] $packageNames List of Android package names (e.g. ['com.twitter.android'])
     */
    public function setAllowedApps(array $packageNames): array
    {
        $apps = array_map(static fn (string $package): array => ['package' => $package], $packageNames);
        return $this->http->put('/api/v1/private/phone/apps/allowed', ['apps' => $apps]);
    }

    /** Register the currently active pipeline run on the phone (used by agents). */
    public function registerActiveRun(string $runId): array
    {
        return $this->http->post('/api/v1/private/phone/active-run', ['runId' => $runId]);
    }
}
