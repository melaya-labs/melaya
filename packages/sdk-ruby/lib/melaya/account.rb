# frozen_string_literal: true

module Melaya
  # Account API — authenticated reads about your Melaya account.
  #
  # Connected-exchange key references (masked), tier limits, and live usage
  # counters. Requires an mk_ key on the private plane.
  class AccountAPI
    def initialize(http)
      @http = http
    end

    # The exchange API keys connected to your account. +api_key+ is masked
    # (display-only); use +api_key_id+ (e.g. BINANCEUSDM_0) when launching
    # strategies or minting a private stream ticket.
    def keys
      @http.get("/api/v1/private/keys")["keys"]
    end

    # Tier, plan limits, and live usage counters (mirrors the dashboard's usage page).
    def usage
      @http.get("/api/v1/private/usage")
    end

    # Status of your platform API key (tier, max concurrent connections).
    def api_key_status
      @http.get("/api/v1/private/api-key")
    end

    # POST /api/v1/private/api-key
    # Generate a new platform API key, replacing the current one at once. The
    # new key is returned ONCE.
    #
    # WARNING: called with the key this client uses, every later call of this
    # client fails until you build a new Melaya::Client with the returned key.
    # @return [Hash] { "apiKey" => String }
    def rotate_api_key
      @http.post("/api/v1/private/api-key", {})
    end

    # DELETE /api/v1/private/api-key
    # Revoke the platform API key. Called with the key this client uses, this
    # client stops working immediately.
    # @return [Hash] { "ok" => Boolean }
    def revoke_api_key
      @http.delete("/api/v1/private/api-key")
    end

    # GET /api/v1/private/api-key/usage
    # Request counts of your platform API key (current key, merged with your
    # account totals).
    # @return [Hash]
    def api_key_usage
      @http.get("/api/v1/private/api-key/usage")
    end
  end
end
