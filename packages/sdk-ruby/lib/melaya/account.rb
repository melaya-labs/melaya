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
  end
end
