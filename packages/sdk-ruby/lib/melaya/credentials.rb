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

    # ── Telegram user QR login (alternative to the phone-number flow above) ────

    # POST /api/v1/private/credentials/telegram/auth/qr/start
    # Start Telegram user QR login.
    # @param api_id [Integer]
    # @param api_hash [String]
    # @return [Hash] { "handle" => String, ... } — handle starts with "tgauth_"
    def telegram_qr_start(api_id, api_hash)
      @http.post("/api/v1/private/credentials/telegram/auth/qr/start",
        "api_id" => api_id, "api_hash" => api_hash)
    end

    # POST /api/v1/private/credentials/telegram/auth/qr/poll
    # Poll Telegram user QR login.
    # @param handle [String] starts with "tgauth_"
    def telegram_qr_poll(handle)
      @http.post("/api/v1/private/credentials/telegram/auth/qr/poll", "handle" => handle)
    end

    # ── Several accounts per connector (personal scope) ────────────────────────
    # Field-based connectors can hold several accounts (two mailboxes, two
    # shops). Agents use the DEFAULT account unless a tool call names another
    # one. Only labels and ids ever come back, never credential values.
    # Each account is a Hash: { "id", "label", "isDefault", "createdAt" }
    # ("isDefault" = the account agents use unless a tool call names another).

    # GET /api/v1/private/credentials/:service/accounts
    # Accounts connected to one connector. +"id" => "current"+ is a single
    # connection made before accounts existed.
    # @param service [String] connector service id
    # @return [Array<Hash>] [{ "id", "label", "isDefault", "createdAt" }]
    def accounts(service)
      @http.get("/api/v1/private/credentials/#{enc(service)}/accounts")
    end

    # POST /api/v1/private/credentials/:service/accounts
    # Add another account to a field-based connector. The connection is tested
    # first.
    # @param service [String] connector service id
    # @param fields [Hash] the connector's credential fields (same keys as +set+)
    # @param label [String, nil] name of the new account
    # @param current_label [String, nil] names the existing single connection
    #   when it is adopted as the first account
    # @param make_default [Boolean, nil] make the new account the default
    # @return [Array<Hash>] the updated account list
    def add_account(service, fields:, label: nil, current_label: nil, make_default: nil)
      body = compact("label" => label, "fields" => fields,
                     "currentLabel" => current_label, "makeDefault" => make_default)
      @http.post("/api/v1/private/credentials/#{enc(service)}/accounts", body)
    end

    # PUT /api/v1/private/credentials/:service/accounts/default
    # Choose which account the connector (and so every agent) uses.
    # @param service [String]
    # @param account_id [String]
    # @return [Array<Hash>] the updated account list
    def set_default_account(service, account_id)
      @http.put("/api/v1/private/credentials/#{enc(service)}/accounts/default",
        "accountId" => account_id)
    end

    # POST /api/v1/private/credentials/:service/accounts/:accountId/identify
    # Name an account after the identity its connector reports (runs the
    # connection test on it).
    # @param service [String]
    # @param account_id [String]
    # @return [Array<Hash>] the updated account list
    def identify_account(service, account_id)
      @http.post("/api/v1/private/credentials/#{enc(service)}/accounts/#{enc(account_id)}/identify", {})
    end

    # PUT /api/v1/private/credentials/:service/accounts/:accountId
    # Rename one account (max 80 chars).
    # @param service [String]
    # @param account_id [String]
    # @param label [String]
    # @return [Array<Hash>] the updated account list
    def rename_account(service, account_id, label:)
      @http.put("/api/v1/private/credentials/#{enc(service)}/accounts/#{enc(account_id)}",
        "label" => label)
    end

    # DELETE /api/v1/private/credentials/:service/accounts/:accountId
    # Remove one account from a connector.
    # @param service [String]
    # @param account_id [String]
    # @return [Array<Hash>] the remaining account list
    def remove_account(service, account_id)
      @http.delete("/api/v1/private/credentials/#{enc(service)}/accounts/#{enc(account_id)}")
    end

    # ── Google OAuth (status / defaults / disconnect) ──────────────────────────

    # GET /api/v1/private/credentials/google/status
    # List the Google OAuth capabilities actually granted to the caller.
    def google_status
      @http.get("/api/v1/private/credentials/google/status")
    end

    # PUT /api/v1/private/credentials/google/default
    # Select the connected Google account used by one capability.
    # @param capability [String] e.g. "gmail", "calendar", "drive", "sheets",
    #   "docs", "search_console", "youtube", "google_ads", "analytics", "meet", "slides"
    # @param account_id [String] 24-hex-char connected-account id
    def google_set_default(capability, account_id)
      @http.put("/api/v1/private/credentials/google/default",
        "capability" => capability, "accountId" => account_id)
    end

    # DELETE /api/v1/private/credentials/google/access
    # Disconnect one Google product, or an entire Google account.
    # @param account_id [String] 24-hex-char connected-account id
    # @param capability [String, nil] omit to disconnect the whole account
    def google_disconnect(account_id, capability: nil)
      body = compact("accountId" => account_id, "capability" => capability)
      @http.delete("/api/v1/private/credentials/google/access", {}, body)
    end

    # ── Database connector test (runner-probed) ────────────────────────────────

    # POST /api/v1/private/credentials/db-test
    # Test a database connector from the user's own runner (reaches
    # IP-allow-listed / VPC hosts a cloud probe never could).
    # @param service [String] one of "postgres", "mysql", "snowflake", "databricks", "sqlite"
    # @param credentials [Hash, nil] freshly-typed credentials to test instead of the stored ones
    # @return [Hash] { "sessionId" => String, ... }
    def db_test_start(service, credentials: nil)
      body = compact("service" => service, "credentials" => credentials)
      @http.post("/api/v1/private/credentials/db-test", body)
    end

    # GET /api/v1/private/credentials/db-test/:sessionId
    # Poll a DB connector runner-test result.
    # @param session_id [String]
    def db_test_status(session_id)
      @http.get("/api/v1/private/credentials/db-test/#{enc(session_id)}")
    end

    # ── WhatsApp Embedded Signup ────────────────────────────────────────────────

    # GET /api/v1/private/credentials/whatsapp/embedded-signup/config
    # WhatsApp Embedded Signup config (appId/configId) for the client SDK.
    def whatsapp_signup_config
      @http.get("/api/v1/private/credentials/whatsapp/embedded-signup/config")
    end

    # POST /api/v1/private/credentials/whatsapp/embedded-signup/exchange
    # Exchange a WhatsApp Embedded Signup code for a connected number.
    # @param code [String]
    # @param phone_number_id [String]
    # @param waba_id [String]
    # @param project [String, nil]
    def whatsapp_signup_exchange(code:, phone_number_id:, waba_id:, project: nil)
      body = compact(
        "code"          => code,
        "phoneNumberId" => phone_number_id,
        "wabaId"        => waba_id,
        "project"       => project
      )
      @http.post("/api/v1/private/credentials/whatsapp/embedded-signup/exchange", body)
    end

    # ── TikTok ──────────────────────────────────────────────────────────────────

    # GET /api/v1/private/credentials/tiktok/creator-info
    # The connected TikTok account's creator info (nickname, allowed privacy
    # levels, interaction availability) for the compliant Post-to-TikTok
    # approval UI.
    def tiktok_creator_info
      @http.get("/api/v1/private/credentials/tiktok/creator-info")
    end

    # ── Substack email-link sign-in ─────────────────────────────────────────────

    # POST /api/v1/private/credentials/substack/email-link
    # Ask Substack to email a sign-in link.
    # @param email [String]
    def substack_email_link_send(email)
      @http.post("/api/v1/private/credentials/substack/email-link", "email" => email)
    end

    # POST /api/v1/private/credentials/substack/email-link/redeem
    # Finish Substack sign-in with the emailed link.
    # @param link [String]
    # @param email [String, nil]
    def substack_email_link_redeem(link, email: nil)
      body = compact("link" => link, "email" => email)
      @http.post("/api/v1/private/credentials/substack/email-link/redeem", body)
    end

    private

    def enc(s)
      URI.encode_www_form_component(s.to_s).gsub("+", "%20") # path segment: space is %20, never +
    end

    def compact(hash)
      hash.reject { |_, v| v.nil? }
    end
  end
end
