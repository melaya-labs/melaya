<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Team API — manage project team membership, roles, and invitations.
 *
 * Maps to /api/v1/private/projects/:project/members/* and /api/v1/private/team/*.
 *
 * @example
 * ```php
 * $sdk = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * $members = $sdk->team->listMembers('my-project');
 * $sdk->team->invite('my-project', 'alice');
 *
 * $link = $sdk->team->createInviteLink('my-project');
 * // Share $link['inviteLink'] with new team members
 * ```
 */
class TeamAPI
{
    public function __construct(private readonly HttpClient $http) {}

    /** List members of a project team. */
    public function listMembers(string $project): array
    {
        return $this->http->get('/api/v1/private/projects/' . rawurlencode($project) . '/members');
    }

    /**
     * Invite a user to a project team by username.
     *
     * @param string $project  Project name
     * @param string $username The username to invite
     */
    public function invite(string $project, string $username): array
    {
        return $this->http->post(
            '/api/v1/private/projects/' . rawurlencode($project) . '/members/invite',
            ['username' => $username]
        );
    }

    /**
     * Create a shareable invite link for a project.
     * Returns ['ok' => true, 'inviteLink' => '...'].
     */
    public function createInviteLink(string $project): array
    {
        return $this->http->post('/api/v1/private/projects/' . rawurlencode($project) . '/invite-link');
    }

    /**
     * Accept a project invite using the token from an invite link.
     *
     * @param string $token The invite token from the invite URL
     */
    public function acceptInvite(string $token): array
    {
        return $this->http->post('/api/v1/private/team/invite/accept', ['token' => $token]);
    }

    /**
     * Update a team member's role in a project.
     *
     * @param string $project Project name
     * @param string $userId  The user's ID
     * @param string $role    One of 'owner' | 'editor' | 'viewer'
     */
    public function updateMemberRole(string $project, string $userId, string $role): array
    {
        return $this->http->patch(
            '/api/v1/private/projects/' . rawurlencode($project) . '/members/' . rawurlencode($userId),
            ['role' => $role]
        );
    }

    /** Remove a member from a project team. */
    public function removeMember(string $project, string $userId): array
    {
        return $this->http->delete(
            '/api/v1/private/projects/' . rawurlencode($project) . '/members/' . rawurlencode($userId)
        );
    }

    // ── Pipeline visibility ──────────────────────────────────────────────────

    /** Get visibility settings for a pipeline within a project. */
    public function getPipelineVisibility(string $project, string $pipeline): array
    {
        return $this->http->get(
            '/api/v1/private/projects/' . rawurlencode($project) . '/pipelines/' . rawurlencode($pipeline) . '/visibility'
        );
    }

    /**
     * Set pipeline visibility within a project.
     *
     * @param array $body Visibility settings
     */
    public function setPipelineVisibility(string $project, string $pipeline, array $body): array
    {
        return $this->http->put(
            '/api/v1/private/projects/' . rawurlencode($project) . '/pipelines/' . rawurlencode($pipeline) . '/visibility',
            $body
        );
    }
}

