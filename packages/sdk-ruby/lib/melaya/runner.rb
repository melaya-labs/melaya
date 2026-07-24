# frozen_string_literal: true

module Melaya
  # Runner API — mint, list, and revoke runner tokens.
  #
  # Runner tokens (mel_run_ prefix) authenticate the Melaya runner CLI process
  # that executes agent pipelines on your infrastructure.
  # The plaintext token is returned only once on creation — store it securely.
  #
  # Maps to /api/v1/private/runner/tokens/*.
  #
  # @example
  #   result = melaya.runner.create_token(label: "prod-server-1")
  #   token = result["token"]   # store securely — shown only once
  #
  #   tokens = melaya.runner.list_tokens
  #   melaya.runner.revoke_token(tokens.first["id"])
  class RunnerAPI
    def initialize(http)
      @http = http
    end

    # POST /api/v1/private/runner/tokens
    # Mint a new mel_run_ runner token.
    # @param label [String, nil] human-readable label for the token
    def create_token(label: nil)
      body = label ? { "label" => label } : nil
      @http.post("/api/v1/private/runner/tokens", body)
    end

    # GET /api/v1/private/runner/tokens
    # List all runner tokens for the caller (masked, with last_seen).
    def list_tokens
      @http.get("/api/v1/private/runner/tokens")
    end

    # DELETE /api/v1/private/runner/tokens/:tokenId
    # Revoke a runner token by ID.
    # @param token_id [String]
    def revoke_token(token_id)
      @http.delete("/api/v1/private/runner/tokens/#{URI.encode_www_form_component(token_id.to_s)}")
    end
  end
end
