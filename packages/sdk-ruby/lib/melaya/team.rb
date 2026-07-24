# frozen_string_literal: true

module Melaya
  # Team API — manage project team membership, roles, and invitations.
  #
  # Maps to:
  #   /api/v1/private/projects/:project/members/*
  #   /api/v1/private/team/*
  #   /api/v1/private/projects/:project/pipelines/:pipeline/visibility
  #
  # @example
  #   members = melaya.team.list_members("my-project")
  #   melaya.team.invite("my-project", username: "alice")
  #   link = melaya.team.create_invite_link("my-project")
  class TeamAPI
    def initialize(http)
      @http = http
    end

    # GET /api/v1/private/projects/:project/members
    # List members of a project team.
    # @param project [String] project name
    def list_members(project)
      @http.get("/api/v1/private/projects/#{enc(project)}/members")
    end

    # POST /api/v1/private/projects/:project/members/invite
    # Invite a user to a project team by username.
    # @param project [String]
    # @param username [String]
    def invite(project, username:)
      @http.post("/api/v1/private/projects/#{enc(project)}/members/invite",
        "username" => username)
    end

    # POST /api/v1/private/projects/:project/invite-link
    # Create a shareable invite link for a project.
    # Returns the URL to send to new team members.
    # @param project [String]
    def create_invite_link(project)
      @http.post("/api/v1/private/projects/#{enc(project)}/invite-link")
    end

    # POST /api/v1/private/team/invite/accept
    # Accept a project invite using the token from an invite link.
    # @param token [String]
    def accept_invite(token)
      @http.post("/api/v1/private/team/invite/accept", "token" => token)
    end

    # PATCH /api/v1/private/projects/:project/members/:userId
    # Update a team member's role in a project.
    # @param project [String]
    # @param user_id [String]
    # @param role [String] one of "owner", "editor", "viewer"
    def update_member_role(project, user_id, role:)
      @http.patch(
        "/api/v1/private/projects/#{enc(project)}/members/#{enc(user_id)}",
        "role" => role
      )
    end

    # DELETE /api/v1/private/projects/:project/members/:userId
    # Remove a member from a project team.
    # @param project [String]
    # @param user_id [String]
    def remove_member(project, user_id)
      @http.delete("/api/v1/private/projects/#{enc(project)}/members/#{enc(user_id)}")
    end

    # ── Pipeline visibility ────────────────────────────────────────────────────

    # GET /api/v1/private/projects/:project/pipelines/:pipeline/visibility
    # Get visibility settings for a pipeline within a project.
    # @param project [String]
    # @param pipeline [String]
    def get_pipeline_visibility(project, pipeline)
      @http.get(
        "/api/v1/private/projects/#{enc(project)}/pipelines/#{enc(pipeline)}/visibility"
      )
    end

    # PUT /api/v1/private/projects/:project/pipelines/:pipeline/visibility
    # Set pipeline visibility within a project.
    # @param project [String]
    # @param pipeline [String]
    # @param body [Hash]
    def set_pipeline_visibility(project, pipeline, body = {})
      @http.put(
        "/api/v1/private/projects/#{enc(project)}/pipelines/#{enc(pipeline)}/visibility",
        body
      )
    end

    private

    def enc(s)
      URI.encode_www_form_component(s.to_s)
    end
  end
end

