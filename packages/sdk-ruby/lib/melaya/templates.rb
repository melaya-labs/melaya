# frozen_string_literal: true

module Melaya
  # Templates API — create, manage, share, and assign pipeline templates.
  #
  # Templates bundle a pipeline definition into a reusable, shareable artifact.
  # Visibility levels: private → team → community → assigned.
  #
  # Maps to:
  #   /api/v1/private/user-templates/*    — CRUD + share + assignments
  #   /api/v1/private/templates/*         — global/validated lists
  #
  # @example
  #   templates = melaya.templates.list
  #   t = melaya.templates.save(name: "My report", payload: { steps: [] })
  #   melaya.templates.share(t["id"], "team")
  #   melaya.templates.delete(t["id"])
  class TemplatesAPI
    def initialize(http)
      @http = http
    end

    # GET /api/v1/private/user-templates
    # List all templates visible to the caller (own + team + community + assigned).
    def list
      @http.get("/api/v1/private/user-templates")
    end

    # GET /api/v1/private/templates/global
    # List all community-visibility (global) templates.
    def list_global
      @http.get("/api/v1/private/templates/global")
    end

    # GET /api/v1/private/templates/validated
    # List IDs of all validated (platform-approved) templates.
    def list_validated
      @http.get("/api/v1/private/templates/validated")
    end

    # POST /api/v1/private/user-templates
    # Create a new private user template.
    # @param name [String]
    # @param payload [Hash]
    # @param description [String, nil]
    # @param category [String, nil]
    def save(name:, payload:, description: nil, category: nil)
      body = compact(
        "name"        => name,
        "payload"     => payload,
        "description" => description,
        "category"    => category
      )
      @http.post("/api/v1/private/user-templates", body)
    end

    # PATCH /api/v1/private/user-templates/:id
    # Update name/description/category/payload of a private user template.
    # @param template_id [String]
    # @param body [Hash]
    def update(template_id, body = {})
      @http.patch("/api/v1/private/user-templates/#{enc(template_id)}", body)
    end

    # POST /api/v1/private/user-templates/:sourceId/duplicate
    # Duplicate a readable template into the caller's private library.
    # @param template_id [String] the source template to copy
    # @param new_name [String, nil]
    def duplicate(template_id, new_name: nil)
      body = new_name ? { "newName" => new_name } : nil
      @http.post("/api/v1/private/user-templates/#{enc(template_id)}/duplicate", body)
    end

    # DELETE /api/v1/private/user-templates/:id
    # Delete (or soft-demote if shared) a template.
    # @param template_id [String]
    def delete(template_id)
      @http.delete("/api/v1/private/user-templates/#{enc(template_id)}")
    end

    # PUT /api/v1/private/user-templates/:id/visibility
    # Change the visibility of a template.
    # @param template_id [String]
    # @param visibility [String] "private", "team", "community", or "assigned"
    def share(template_id, visibility)
      @http.put("/api/v1/private/user-templates/#{enc(template_id)}/visibility",
        "visibility" => visibility)
    end

    # GET /api/v1/private/user-templates/share-targets
    # List projects the caller is a member of (for the share target picker UI).
    def share_targets
      @http.get("/api/v1/private/user-templates/share-targets")
    end

    # ── Assignments ────────────────────────────────────────────────────────────

    # GET /api/v1/private/user-templates/:templateId/assignments
    # List all assignments (users / projects) for a template.
    # @param template_id [String]
    def list_assignments(template_id)
      @http.get("/api/v1/private/user-templates/#{enc(template_id)}/assignments")
    end

    # POST /api/v1/private/user-templates/:templateId/assignments
    # Assign a template to a user or project.
    # Provide exactly one of +user_id:+ or +project_id:+ (both UUIDs);
    # the server rejects requests carrying both or neither.
    # @param template_id [String]
    # @param user_id [String, nil] target user UUID
    # @param project_id [String, nil] target project UUID
    def assign(template_id, user_id: nil, project_id: nil)
      @http.post("/api/v1/private/user-templates/#{enc(template_id)}/assignments",
        assignment_target(user_id, project_id))
    end

    # DELETE /api/v1/private/user-templates/:templateId/assignments
    # Remove an assignment from a template.
    # Provide exactly one of +user_id:+ or +project_id:+ (both UUIDs).
    # The target is sent as query params — the server ignores DELETE
    # request bodies.
    # @param template_id [String]
    # @param user_id [String, nil] target user UUID
    # @param project_id [String, nil] target project UUID
    def unassign(template_id, user_id: nil, project_id: nil)
      @http.delete("/api/v1/private/user-templates/#{enc(template_id)}/assignments",
        assignment_target(user_id, project_id))
    end

    private

    # Exactly one of user_id / project_id must be given.
    def assignment_target(user_id, project_id)
      if user_id.nil? == project_id.nil?
        raise ArgumentError, "Melaya: provide exactly one of user_id: or project_id:"
      end
      user_id ? { "userId" => user_id } : { "projectId" => project_id }
    end

    def enc(s)
      URI.encode_www_form_component(s.to_s)
    end

    def compact(hash)
      hash.reject { |_, v| v.nil? }
    end
  end
end
