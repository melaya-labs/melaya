<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Bugs API — submit and track bug reports.
 *
 * Maps to /api/v1/private/bugs/*.
 *
 * @example
 * ```php
 * $sdk = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * $bug = $sdk->bugs->create(['title' => 'Pipeline stuck', 'description' => '...']);
 * $sdk->bugs->addComment($bug['id'], 'Still reproducible on v2.3');
 * ```
 */
class BugsAPI
{
    public function __construct(private readonly HttpClient $http) {}

    /**
     * Submit a bug report (user-facing feedback form).
     *
     * @param array $body ['title' => '...', 'description' => '...', 'severity' => '...']
     */
    public function create(array $body): array
    {
        return $this->http->post('/api/v1/private/bugs', $body);
    }

    /** List bug reports submitted by the caller. */
    public function listMine(): array
    {
        return $this->http->get('/api/v1/private/bugs/mine');
    }

    /** Get a single bug report by ID. */
    public function get(string $bugId): array
    {
        return $this->http->get('/api/v1/private/bugs/' . rawurlencode($bugId));
    }

    /**
     * Add a comment to a bug report.
     *
     * @param string $bugId   Bug report ID
     * @param string $comment Comment text
     */
    public function addComment(string $bugId, string $comment): array
    {
        return $this->http->post(
            '/api/v1/private/bugs/' . rawurlencode($bugId) . '/comments',
            ['comment' => $comment]
        );
    }

    /** List unread bug-related notifications for the caller. */
    public function listNotifications(): array
    {
        return $this->http->get('/api/v1/private/bugs/notifications');
    }

    /** Mark bug notifications as read. */
    public function markNotificationsRead(array $body = []): array
    {
        return $this->http->post('/api/v1/private/bugs/notifications/read', $body ?: null);
    }
}
