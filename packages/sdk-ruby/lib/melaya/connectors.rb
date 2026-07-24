# frozen_string_literal: true

module Melaya
  # Project Connectors API — manage credentials at project scope.
  #
  # Project-scoped connectors are isolated per project, letting separate
  # projects use different API keys for the same service.
  #
  # Maps to /api/v1/private/projects/:project/connectors/*.
  #
  # @example
  #   melaya.connectors.set("my-project", "openai", value: "sk-...")
  #   services = melaya.connectors.connected_services("my-project")
  class ConnectorsAPI
    def initialize(http)
      @http = http
    end

    # GET /api/v1/private/projects/:project/connectors/services
    # List connected services for a project.
    # @param project [String] project name
    def connected_services(project)
      @http.get("/api/v1/private/projects/#{enc(project)}/connectors/services")
    end

    # PUT /api/v1/private/projects/:project/connectors/:service
    # Store a connector credential at project scope.
    # @param project [String]
    # @param service [String]
    # @param value [String]
    # @param key [String, nil]
    # @param label [String, nil]
    def set(project, service, value:, key: nil, label: nil)
      body = compact("value" => value, "key" => key, "label" => label)
      @http.put("/api/v1/private/projects/#{enc(project)}/connectors/#{enc(service)}", body)
    end

    # DELETE /api/v1/private/projects/:project/connectors/:service
    # Delete a project-scoped connector credential.
    # @param project [String]
    # @param service [String]
    def delete(project, service)
      @http.delete("/api/v1/private/projects/#{enc(project)}/connectors/#{enc(service)}")
    end

    # POST /api/v1/private/projects/:project/connectors/env-handle
    # Get a short-lived env-handle token for project-scoped credentials.
    # @param project [String]
    def env_handle(project)
      @http.post("/api/v1/private/projects/#{enc(project)}/connectors/env-handle")
    end

    # POST /api/v1/private/projects/:project/connectors/google/oauth
    # Start Google OAuth flow for project-scoped connector.
    # @param project [String]
    # @param body [Hash]
    def google_oauth_start(project, body = {})
      @http.post("/api/v1/private/projects/#{enc(project)}/connectors/google/oauth", body)
    end

    private

    def enc(s)
      URI.encode_www_form_component(s.to_s)
    end

    def compact(hash)
      hash.reject { |_, v| v.nil? }
    end
  end
end
