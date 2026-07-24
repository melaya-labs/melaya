# frozen_string_literal: true

module Melaya
  # Auth API — login, MFA, registration, password management, and session tokens.
  #
  # Most endpoints are public (no Bearer token needed for login/register).
  # However, the SDK HttpClient always sends the mk_* API key which is fine —
  # the server simply ignores it for public routes.
  #
  # Maps to /api/v1/private/auth/* and /api/v1/auth/*.
  class AuthAPI
    def initialize(http)
      @http = http
    end

    # POST /api/v1/private/auth/login
    # Password + username login; returns session JWT + optional MFA challenge token.
    # @param username [String]
    # @param password [String]
    def login(username:, password:)
      @http.post("/api/v1/private/auth/login",
        "username" => username,
        "password" => password)
    end

    # POST /api/v1/private/auth/mfa/verify
    # Resolve MFA challenge after login.
    # @param challenge_token [String]
    # @param code [String] TOTP or recovery code
    def verify_mfa(challenge_token:, code:)
      @http.post("/api/v1/private/auth/mfa/verify",
        "challengeToken" => challenge_token,
        "code"           => code)
    end

    # POST /api/v1/private/auth/register
    # Create a new user account; sends email verification.
    def register(username:, email:, password:)
      @http.post("/api/v1/private/auth/register",
        "username" => username,
        "email"    => email,
        "password" => password)
    end

    # POST /api/v1/private/auth/verify-signup
    # Confirm email address from signup link token.
    # @param token [String]
    def verify_signup(token:)
      @http.post("/api/v1/private/auth/verify-signup", "token" => token)
    end

    # POST /api/v1/private/auth/resend-verification
    # Re-send email verification message.
    # @param email [String]
    def resend_verification(email:)
      @http.post("/api/v1/private/auth/resend-verification", "email" => email)
    end

    # GET /api/v1/private/auth/me
    # Return current authenticated user profile.
    def me
      @http.get("/api/v1/private/auth/me")
    end

    # GET /api/v1/private/auth/check
    # Lightweight session validity check; returns ok: true.
    def check
      @http.get("/api/v1/private/auth/check")
    end

    # POST /api/v1/private/auth/change-password
    # Change password (current + new).
    # @param current_password [String]
    # @param new_password [String]
    def change_password(current_password:, new_password:)
      @http.post("/api/v1/private/auth/change-password",
        "currentPassword" => current_password,
        "newPassword"     => new_password)
    end

    # POST /api/v1/private/auth/forgot-password
    # Initiate password reset flow; sends email with reset token.
    # @param email [String]
    def forgot_password(email:)
      @http.post("/api/v1/private/auth/forgot-password", "email" => email)
    end

    # POST /api/v1/private/auth/reset-password
    # Complete password reset using token from email.
    # @param token [String]
    # @param new_password [String]
    def reset_password(token:, new_password:)
      @http.post("/api/v1/private/auth/reset-password",
        "token"       => token,
        "newPassword" => new_password)
    end

    # POST /api/v1/private/auth/mobile-handoff
    # Create a short-lived handoff token for mobile app deep-link auth.
    def mobile_handoff
      @http.post("/api/v1/private/auth/mobile-handoff")
    end

    # GET /api/v1/private/auth/permissions
    # Return caller's permission flags (capabilities, tier, feature gates).
    def permissions
      @http.get("/api/v1/private/auth/permissions")
    end

    # POST /api/v1/private/auth/refresh
    # Rotate session JWT (sliding expiry); returns new token.
    def refresh
      @http.post("/api/v1/private/auth/refresh")
    end

    # ── MFA ───────────────────────────────────────────────────────────────────

    # GET /api/v1/private/mfa/status
    # Return MFA enrollment status for the caller.
    def mfa_status
      @http.get("/api/v1/private/mfa/status")
    end

    # POST /api/v1/private/mfa/setup
    # Initiate TOTP setup; returns QR/secret.
    def mfa_setup
      @http.post("/api/v1/private/mfa/setup")
    end

    # POST /api/v1/private/mfa/confirm
    # Confirm TOTP setup with first valid code; activates MFA.
    # @param code [String]
    def mfa_confirm(code:)
      @http.post("/api/v1/private/mfa/confirm", "code" => code)
    end
  end
end
