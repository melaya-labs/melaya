# frozen_string_literal: true

module Melaya
  # Projects API — create and list agent projects.
  #
  # Projects are the top-level namespace for pipelines, connectors, and team
  # membership in the Melaya platform.
  #
  # Maps to /api/v1/private/projects/*.
  #
  # @example
  #   projects = melaya.projects.list
  #   project  = melaya.projects.create(name: "my-project", description: "...")
  #   melaya.projects.rename(old_name: "my-project", new_name: "renamed-project")
  class ProjectsAPI
    def initialize(http)
      @http = http
    end

    # GET /api/v1/private/projects
    # List all projects the authenticated user can access
    # (owned projects + projects they are a member of).
    def list
      @http.get("/api/v1/private/projects")
    end

    # POST /api/v1/private/projects
    # Create a new agent project.
    # @param name [String]
    # @param description [String, nil]
    def create(name:, description: nil)
      body = compact("name" => name, "description" => description)
      @http.post("/api/v1/private/projects", body)
    end

    # PATCH /api/v1/private/projects/rename
    # Rename a project (body: oldName, newName).
    # @param old_name [String]
    # @param new_name [String]
    def rename(old_name:, new_name:)
      @http.patch("/api/v1/private/projects/rename",
        "oldName" => old_name,
        "newName" => new_name)
    end

    # GET /api/v1/private/projects/runner
    # Get projects list (runner-facing, authed).
    def runner_projects
      @http.get("/api/v1/private/projects/runner")
    end

    private

    def compact(hash)
      hash.reject { |_, v| v.nil? }
    end
  end
end
