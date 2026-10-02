# frozen_string_literal: true

module Melaya
  # Accounts API — GDPR data export, API key management, and profile updates.
  #
  # Maps to /api/v1/private/accounts/* and /api/v1/private/keys/*.
  #
  # Note: credit balance endpoints live in BillingAPI to keep billing concerns
  # co-located. Key listing is in AccountAPI (trading plane) for backward
  # compatibility; this module covers the write/management side.
  class AccountsAPI
    def initialize(http)
      @http = http
    end

    # POST /api/v1/private/accounts/export
    # GDPR Art 15/20 data export; returns JSON blob of all user data.
    def export_data
      @http.post("/api/v1/private/accounts/export")
    end

    # POST /api/v1/private/accounts/resend-email-verification
    # Send a verification email to the signed-in account's saved address.
    def resend_email_verification
      @http.post("/api/v1/private/accounts/resend-email-verification")
    end

    # POST /api/v1/private/accounts/verify-email
    # Confirm saved-email ownership without creating a login session.
    # @param token [String] 64 hex-char verification token
    def verify_email(token)
      @http.post("/api/v1/private/accounts/verify-email", "token" => token)
    end

    # DELETE /api/v1/private/keys/:keyId
    # Remove a stored CEX API key.
    # @param key_id [String]
    def remove_key(key_id)
      @http.delete("/api/v1/private/keys/#{enc(key_id)}")
    end

    # PATCH /api/v1/private/accounts/profile
    # Update user display name, avatar, or settings.
    # @param body [Hash]
    def update_profile(body = {})
      @http.patch("/api/v1/private/accounts/profile", body)
    end

    private

    def enc(s)
      URI.encode_www_form_component(s.to_s).gsub("+", "%20") # path segment: space is %20, never +
    end
  end
end
