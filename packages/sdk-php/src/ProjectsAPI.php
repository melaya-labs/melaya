<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Projects API — create and list agent projects.
 *
 * Maps to /api/v1/private/projects. Projects are the top-level namespace
 * for pipelines, connectors, and team membership.
 *
 * @example
 * ```php
 * $sdk = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * $projects = $sdk->projects->list();
 * $newProject = $sdk->projects->create(['name' => 'my-agent-project']);
 *
 * $sdk->projects->rename('old-name', 'new-name');
 * ```
 */
class ProjectsAPI
{
    public function __construct(private readonly HttpClient $http) {}

    /**
     * List all projects the authenticated user can access
     * (owned projects + projects they are a member of).
     */
    public function list(): array
    {
        return $this->http->get('/api/v1/private/projects');
    }

    /**
     * Create a new agent project.
     *
     * @param array $body ['name' => '...', 'description' => '...']
     */
    public function create(array $body): array
    {
        return $this->http->post('/api/v1/private/projects', $body);
    }

    /**
     * Rename a project.
     *
     * @param string $oldName Current project name
     * @param string $newName New project name
     */
    public function rename(string $oldName, string $newName): array
    {
        return $this->http->patch('/api/v1/private/projects/rename', [
            'oldName' => $oldName,
            'newName' => $newName,
        ]);
    }

    /**
     * Get the projects list (runner-facing view).
     * Used by the runner CLI to discover which projects it can execute.
     */
    public function runnerProjects(): array
    {
        return $this->http->get('/api/v1/private/projects/runner');
    }
}
