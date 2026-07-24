# frozen_string_literal: true

module Melaya
  # Credentials API — store, retrieve, test, and delete secrets and
  # third-party service connections at user scope.
  #
  # Credentials are envelope-encrypted at rest. Use +Melaya::ConnectorsAPI+
  # for project-scoped connector credentials.
  #
  # Maps to /api/v1/private/credentials/*.
  #
  # @example
  #   melaya.credentials.set("openai", value: "sk-...")
  #   melaya.credentials.test("openai")
  #   melaya.credentials.delete("openai")
  class CredentialsAPI
    def initialize(http)
      @http = http
    end

    # GET /api/v1/private/credentials
    # List all stored credentials (services, OAuth connections, env handles).
    def list
      @http.get("/api/v1/private/credentials")
    end

    # GET /api/v1/private/credentials/services
    # List connected third-party services for the caller.
    def connected_services
      @http.get("/api/v1/private/credentials/services")
    end

    # GET /api/v1/private/credentials/:service
    # Get a stored credential value by service name.
    # @param service [String]
    # @param key [String, nil] optional named key within the service
    def get(service, key: nil)
      params = key ? { "key" => key } : {}
      @http.get("/api/v1/private/credentials/#{enc(service)}", params)
    end

    # PUT /api/v1/private/credentials/:service
    # Store or update a credential (envelope-encrypted at rest).
    # @param service [String]
    # @param value [String] the secret value
    # @param key [String, nil]
    # @param label [String, nil]
    def set(service, value:, key: nil, label: nil)
      body = compact("value" => value, "key" => key, "label" => label)
      @http.put("/api/v1/private/credentials/#{enc(service)}", body)
    end

    # DELETE /api/v1/private/credentials/:service
    # Delete a stored credential by service name.
    # @param service [String]
    def delete(service)
      @http.delete("/api/v1/private/credentials/#{enc(service)}")
    end

    # POST /api/v1/private/credentials/:service/test
    # Test a stored credential (e.g. validate API key against its service).
    # @param service [String]
    def test(service)
      @http.post("/api/v1/private/credentials/#{enc(service)}/test")
    end

    # ── Operator profile ───────────────────────────────────────────────────────

    # GET /api/v1/private/credentials/operator-profile
    # Get operator profile (persona config for agent context).
    def get_operator_profile
      @http.get("/api/v1/private/credentials/operator-profile")
    end

    # PUT /api/v1/private/credentials/operator-profile
    # Save operator profile.
    # @param profile [Hash]
    def set_operator_profile(profile)
      @http.put("/api/v1/private/credentials/operator-profile", profile)
    end

    # ── AI models ──────────────────────────────────────────────────────────────

    # GET /api/v1/private/credentials/models
    # List available AI models across all configured providers.
    # Collapses 19+ provider fan-out into a parameterized query.
    # @param provider [String, nil]
    # @param capability [String, nil]
    def list_models(provider: nil, capability: nil)
      @http.get("/api/v1/private/credentials/models",
        compact("provider" => provider, "capability" => capability))
    end

    # ── Melaya sub-accounts ────────────────────────────────────────────────────

    # GET /api/v1/private/credentials/melaya-accounts
    # List Melaya sub-accounts available to the caller.
    def melaya_accounts
      @http.get("/api/v1/private/credentials/melaya-accounts")
    end

    # ── RAG ────────────────────────────────────────────────────────────────────

    # POST /api/v1/private/rag/ingest
    # Start a RAG document ingestion job.
    # @param body [Hash]
    def rag_ingest_start(body = {})
      @http.post("/api/v1/private/rag/ingest", body)
    end

    # GET /api/v1/private/rag/ingest/:sessionId
    # Poll RAG ingestion job status.
    # @param session_id [String]
    def rag_ingest_status(session_id)
      @http.get("/api/v1/private/rag/ingest/#{enc(session_id)}")
    end

    # POST /api/v1/private/rag/retrieve
    # Start a RAG retrieval query.
    # @param body [Hash]
    def rag_retrieve_start(body = {})
      @http.post("/api/v1/private/rag/retrieve", body)
    end

    # GET /api/v1/private/rag/retrieve/:sessionId
    # Poll RAG retrieval result.
    # @param session_id [String]
    def rag_retrieve_status(session_id)
      @http.get("/api/v1/private/rag/retrieve/#{enc(session_id)}")
    end

    # ── Folder picker ──────────────────────────────────────────────────────────

    # POST /api/v1/private/rag/pick-folder
    # Initiate native folder picker for file ingestion.
    # @param body [Hash]
    def pick_folder_start(body = {})
      @http.post("/api/v1/private/rag/pick-folder", body)
    end

    # GET /api/v1/private/rag/pick-folder/:sessionId
    # Poll folder picker result.
    # @param session_id [String]
    def pick_folder_status(session_id)
      @http.get("/api/v1/private/rag/pick-folder/#{enc(session_id)}")
    end

    # ── LinkedIn OAuth ─────────────────────────────────────────────────────────

    # POST /api/v1/private/credentials/linkedin/connect
    # Start LinkedIn OAuth flow.
    def linkedin_connect_start(body = {})
      @http.post("/api/v1/private/credentials/linkedin/connect", body)
    end

    # DELETE /api/v1/private/credentials/linkedin/connect
    # Cancel an in-progress LinkedIn OAuth flow.
    def linkedin_connect_cancel
      @http.delete("/api/v1/private/credentials/linkedin/connect")
    end

    # GET /api/v1/private/credentials/linkedin/connect/status
    # Poll LinkedIn OAuth connection status.
    def linkedin_connect_status
      @http.get("/api/v1/private/credentials/linkedin/connect/status")
    end

    # ── Luma OAuth ────────────────────────────────────────────────────────────

    # POST /api/v1/private/credentials/luma/connect
    # Start Luma OAuth flow.
    def luma_connect_start(body = {})
      @http.post("/api/v1/private/credentials/luma/connect", body)
    end

    # GET /api/v1/private/credentials/luma/connect/status
    # Poll Luma OAuth connection status.
    def luma_connect_status
      @http.get("/api/v1/private/credentials/luma/connect/status")
    end

    # DELETE /api/v1/private/credentials/luma/connect
    # Cancel an in-progress Luma OAuth flow.
    def luma_connect_cancel
      @http.delete("/api/v1/private/credentials/luma/connect")
    end

    # GET /api/v1/private/credentials/luma/schema
    # Get the Luma event registration form schema.
    # @param params [Hash]
    def luma_schema(params = {})
      @http.get("/api/v1/private/credentials/luma/schema", params)
    end

    # ── Google OAuth ───────────────────────────────────────────────────────────

    # POST /api/v1/private/credentials/google/oauth
    # Start Google OAuth flow for credential storage.
    def google_oauth_start(body = {})
      @http.post("/api/v1/private/credentials/google/oauth", body)
    end

    # ── CLI auth ───────────────────────────────────────────────────────────────

    # POST /api/v1/private/credentials/cli-auth
    # Start CLI authentication flow (device-code style).
    def cli_auth_start(body = {})
      @http.post("/api/v1/private/credentials/cli-auth", body)
    end

    # ── NotebookLM ─────────────────────────────────────────────────────────────

    # POST /api/v1/private/credentials/notebooklm/login
    # Store NotebookLM credentials.
    def notebooklm_login(body = {})
      @http.post("/api/v1/private/credentials/notebooklm/login", body)
    end

    # GET /api/v1/private/credentials/notebooklm/status
    # Check NotebookLM connection status.
    def notebooklm_status
      @http.get("/api/v1/private/credentials/notebooklm/status")
    end

    # ── Telegram auth ──────────────────────────────────────────────────────────

    # POST /api/v1/private/credentials/telegram/auth
    # Start Telegram user auth (phone number step).
    def telegram_auth_start(body = {})
      @http.post("/api/v1/private/credentials/telegram/auth", body)
    end

    # POST /api/v1/private/credentials/telegram/auth/code
    # Submit Telegram SMS verification code.
    def telegram_auth_code(body = {})
      @http.post("/api/v1/private/credentials/telegram/auth/code", body)
    end

    # POST /api/v1/private/credentials/telegram/auth/2fa
    # Submit Telegram 2FA password.
    def telegram_auth_2fa(body = {})
      @http.post("/api/v1/private/credentials/telegram/auth/2fa", body)
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
