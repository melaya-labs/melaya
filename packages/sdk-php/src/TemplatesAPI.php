<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Templates API — create, manage, and share pipeline templates.
 *
 * Maps to /api/v1/private/user-templates/* and /api/v1/private/templates/*.
 *
 * Templates bundle a pipeline definition into a reusable, shareable artifact.
 * Visibility levels: 'private' (only you) → 'team' → 'community'.
 *
 * @example
 * ```php
 * $sdk = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * $templates = $sdk->templates->list();
 * $t = $sdk->templates->save(['name' => 'My report', 'payload' => [...]]);
 * $sdk->templates->share($t['id'], 'team');
 * ```
 */
class TemplatesAPI
{
    public function __construct(private readonly HttpClient $http) {}

    // ── User templates ───────────────────────────────────────────────────────

    /**
     * List all templates visible to the caller (own + team + community + assigned),
     * filtered by Row-Level Security.
     */
    public function list(): array
    {
        return $this->http->get('/api/v1/private/user-templates');
    }

    /**
     * Create a new private user template.
     *
     * @param array $body ['name' => '...', 'description' => '...', 'category' => '...', 'payload' => [...]]
     */
    public function save(array $body): array
    {
        return $this->http->post('/api/v1/private/user-templates', $body);
    }

    /**
     * Update name/description/category/payload of a private user template.
     *
     * @param string $templateId Template UUID
     * @param array  $body       Fields to update
     */
    public function update(string $templateId, array $body): array
    {
        return $this->http->patch('/api/v1/private/user-templates/' . rawurlencode($templateId), $body);
    }

    /**
     * Duplicate a readable template into the caller's private library.
     *
     * @param string $templateId Source template ID
     * @param string|null $newName   Optional new name for the duplicate
     */
    public function duplicate(string $templateId, ?string $newName = null): array
    {
        $body = $newName !== null ? ['newName' => $newName] : null;
        return $this->http->post('/api/v1/private/user-templates/' . rawurlencode($templateId) . '/duplicate', $body);
    }

    /** Delete (or soft-demote if shared) a template. */
    public function delete(string $templateId): array
    {
        return $this->http->delete('/api/v1/private/user-templates/' . rawurlencode($templateId));
    }

    /**
     * Change the visibility of a template.
     *
     * @param string $templateId  Template UUID
     * @param string $visibility  One of 'private' | 'team' | 'community' | 'assigned'
     */
    public function share(string $templateId, string $visibility): array
    {
        return $this->http->put(
            '/api/v1/private/user-templates/' . rawurlencode($templateId) . '/visibility',
            ['visibility' => $visibility]
        );
    }

    // ── Assignments ──────────────────────────────────────────────────────────

    /** List all assignments (users / projects) for a template. */
    public function listAssignments(string $templateId): array
    {
        return $this->http->get('/api/v1/private/user-templates/' . rawurlencode($templateId) . '/assignments');
    }

    /**
     * Assign a template to a user or project.
     *
     * Exactly one of `userId` or `projectId` (both UUIDs) must be provided.
     *
     * @param string $templateId Template UUID
     * @param array  $target     ['userId' => '...'] XOR ['projectId' => '...']
     */
    public function assign(string $templateId, array $target): array
    {
        return $this->http->post(
            '/api/v1/private/user-templates/' . rawurlencode($templateId) . '/assignments',
            $this->assignmentInput($target)
        );
    }

    /**
     * Remove an assignment from a template.
     *
     * Exactly one of `userId` or `projectId` (both UUIDs) must be provided.
     * The server reads DELETE parameters from the query string (request bodies
     * on DELETE are ignored), so the target is sent as query params.
     *
     * @param string $templateId Template UUID
     * @param array  $target     ['userId' => '...'] XOR ['projectId' => '...']
     */
    public function unassign(string $templateId, array $target): array
    {
        return $this->http->delete(
            '/api/v1/private/user-templates/' . rawurlencode($templateId) . '/assignments',
            $this->assignmentInput($target)
        );
    }

    /**
     * Validate and normalize an assignment target.
     *
     * @param array $target ['userId' => '...'] XOR ['projectId' => '...']
     * @return array{userId?: string, projectId?: string}
     */
    private function assignmentInput(array $target): array
    {
        $userId    = $target['userId']    ?? null;
        $projectId = $target['projectId'] ?? null;
        if (($userId === null) === ($projectId === null)) {
            throw new \InvalidArgumentException(
                'Melaya templates: provide exactly one of `userId` or `projectId`.'
            );
        }
        return $userId !== null ? ['userId' => $userId] : ['projectId' => $projectId];
    }

    /** List projects the caller is a member of (for use in the share target picker). */
    public function shareTargets(): array
    {
        return $this->http->get('/api/v1/private/user-templates/share-targets');
    }

    // ── Validated / global templates ─────────────────────────────────────────

    /** List IDs of all validated (platform-approved) templates. */
    public function listValidated(): array
    {
        return $this->http->get('/api/v1/private/templates/validated');
    }

    /** List all community-visibility (global) templates. */
    public function listGlobal(): array
    {
        return $this->http->get('/api/v1/private/templates/global');
    }
}
