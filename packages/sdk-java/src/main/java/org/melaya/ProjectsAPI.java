package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Projects API — create and list agent projects.
 *
 * <p>Projects are the top-level namespace for pipelines, connectors, and team
 * membership. Maps to {@code /api/v1/private/projects}.
 *
 * @example
 * <pre>{@code
 * JsonNode projects = melaya.projects().list();
 * JsonNode created  = melaya.projects().create(Map.of("name", "my-agents"));
 * }</pre>
 */
public class ProjectsAPI {

    private final HttpClient http;

    ProjectsAPI(HttpClient http) {
        this.http = http;
    }

    /**
     * List all projects the authenticated user can access
     * (owned projects + projects they are a member of).
     */
    public JsonNode list() {
        return http.get("/api/v1/private/projects", null);
    }

    /**
     * Create a new agent project.
     *
     * @param body map containing at minimum {@code name}; optionally {@code description}
     */
    public JsonNode create(Map<String, Object> body) {
        return http.post("/api/v1/private/projects", body);
    }

    /**
     * Rename a project.
     *
     * @param oldName current project name
     * @param newName desired new project name
     */
    public JsonNode rename(String oldName, String newName) {
        return http.patch("/api/v1/private/projects/rename",
                MarketAPI.params("oldName", oldName, "newName", newName));
    }

    /**
     * Get the projects list (runner-facing view).
     * Returns the same data as {@link #list()} but is intended for runner processes.
     */
    public JsonNode getRunnerProjects() {
        return http.get("/api/v1/private/projects/runner", null);
    }
}
