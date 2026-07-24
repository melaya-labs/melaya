# frozen_string_literal: true

module Melaya
  # Assistant API — get and save the caller's onboarding / persona profile.
  #
  # The profile is stored envelope-encrypted (service=assistant_profile) and
  # used to personalise the in-app assistant experience.
  #
  # Maps to /api/v1/private/assistant/profile.
  #
  # @example
  #   profile = melaya.assistant.get_profile
  #   melaya.assistant.set_profile(name: "Antoine", goals: ["grow my trading edge"])
  class AssistantAPI
    def initialize(http)
      @http = http
    end

    # GET /api/v1/private/assistant/profile
    # Get the caller's assistant onboarding profile.
    def get_profile
      @http.get("/api/v1/private/assistant/profile")
    end

    # PUT /api/v1/private/assistant/profile
    # Save the caller's assistant onboarding profile.
    # @param name [String, nil]
    # @param goals [Array<String>, nil]
    # @param context [String, nil]
    # @param preferences [Hash, nil]
    # @param extra [Hash] any additional profile fields
    def set_profile(name: nil, goals: nil, context: nil, preferences: nil, **extra)
      profile = extra.transform_keys(&:to_s)
      profile["name"]        = name        unless name.nil?
      profile["goals"]       = goals       unless goals.nil?
      profile["context"]     = context     unless context.nil?
      profile["preferences"] = preferences unless preferences.nil?
      @http.put("/api/v1/private/assistant/profile", profile)
    end
  end
end
