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

    # POST /api/v1/private/projects/:project/connectors/:service/apply-personal
    # Share the caller's OWN personal connector credential into the project
    # pool (editor or owner only). Values stay server-side.
    # @param project [String]
    # @param service [String]
    # @param google_capabilities [Array<String>, nil] restrict a Google
    #   connector to these capabilities only, e.g. "gmail", "calendar",
    #   "drive", "sheets", "docs", "search_console", "youtube", "google_ads",
    #   "analytics", "meet", "slides"
    def apply_personal(project, service, google_capabilities: nil)
      body = compact("googleCapabilities" => google_capabilities)
      @http.post("/api/v1/private/projects/#{enc(project)}/connectors/#{enc(service)}/apply-personal", body)
    end

    # GET /api/v1/private/projects/:project/connectors/shared-by
    # Which member shared each connected project connector (usernames only —
    # values are never returned).
    # @param project [String]
    def shared_by(project)
      @http.get("/api/v1/private/projects/#{enc(project)}/connectors/shared-by")
    end

    # ── Google OAuth (status / defaults / disconnect) ─────────────────────────

    # GET /api/v1/private/projects/:project/connectors/google/status
    # List the Google OAuth capabilities actually granted to a project.
    # @param project [String]
    def google_status(project)
      @http.get("/api/v1/private/projects/#{enc(project)}/connectors/google/status")
    end

    # PUT /api/v1/private/projects/:project/connectors/google/default
    # Select the project's connected Google account used by one capability.
    # @param project [String]
    # @param capability [String] e.g. "gmail", "calendar", "drive", "sheets", ...
    # @param account_id [String] 24-hex-char connected-account id
    def google_set_default(project, capability, account_id)
      @http.put("/api/v1/private/projects/#{enc(project)}/connectors/google/default",
        "capability" => capability, "accountId" => account_id)
    end

    # DELETE /api/v1/private/projects/:project/connectors/google/access
    # Disconnect one Google product, or an entire Google account, from a project.
    # @param project [String]
    # @param account_id [String] 24-hex-char connected-account id
    # @param capability [String, nil] omit to disconnect the whole account
    def google_disconnect(project, account_id, capability: nil)
      body = compact("accountId" => account_id, "capability" => capability)
      @http.delete("/api/v1/private/projects/#{enc(project)}/connectors/google/access", {}, body)
    end

    # ── Database connector test (runner-probed) ───────────────────────────────

    # POST /api/v1/private/projects/:project/connectors/db-test
    # Test a project database connector from the user's own runner (reaches
    # IP-allow-listed / VPC hosts a cloud probe never could).
    # @param project [String]
    # @param service [String] one of "postgres", "mysql", "snowflake", "databricks", "sqlite"
    # @param credentials [Hash, nil] freshly-typed credentials to test instead of the stored ones
    # @return [Hash] { "sessionId" => String, ... }
    def db_test_start(project, service, credentials: nil)
      body = compact("service" => service, "credentials" => credentials)
      @http.post("/api/v1/private/projects/#{enc(project)}/connectors/db-test", body)
    end

    # GET /api/v1/private/projects/:project/connectors/db-test/:sessionId
    # Poll a project DB connector runner-test result.
    # @param project [String]
    # @param session_id [String]
    def db_test_status(project, session_id)
      @http.get("/api/v1/private/projects/#{enc(project)}/connectors/db-test/#{enc(session_id)}")
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
