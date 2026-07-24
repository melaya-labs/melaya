# frozen_string_literal: true

module Melaya
  # Phone API — pair and control Android devices connected to the Melaya runner.
  #
  # Agents use these endpoints to drive a paired phone (tap, type, read
  # screen state, launch apps) via the Melaya APK.
  #
  # Maps to /api/v1/private/phone/*.
  #
  # @example
  #   result = melaya.phone.pair
  #   puts "Pairing code: #{result["code"]}"
  #   # User enters code in the Melaya APK on the phone
  #
  #   devices = melaya.phone.list_devices
  #   tree = melaya.phone.screen_tree
  class PhoneAPI
    def initialize(http)
      @http = http
    end

    # POST /api/v1/private/phone/pair
    # Start phone device pairing — generates a pairing code for the Melaya APK.
    def pair
      @http.post("/api/v1/private/phone/pair")
    end

    # GET /api/v1/private/phone/devices
    # List all paired phone devices for the authenticated user.
    def list_devices
      @http.get("/api/v1/private/phone/devices").fetch("devices", [])
    end

    # DELETE /api/v1/private/phone/devices/:deviceId
    # Revoke a paired phone device by ID.
    # @param device_id [String]
    def revoke_device(device_id)
      @http.delete("/api/v1/private/phone/devices/#{enc(device_id)}")
    end

    # GET /api/v1/private/phone/screen-tree
    # Get the current accessibility tree from the paired phone's screen.
    def screen_tree
      @http.get("/api/v1/private/phone/screen-tree")
    end

    # GET /api/v1/private/phone/apps
    # List installed apps on the paired phone.
    def list_apps
      @http.get("/api/v1/private/phone/apps").dig("result", "apps") || []
    end

    # PUT /api/v1/private/phone/apps/allowed
    # Set the allowlist of apps that agents are permitted to interact with.
    # @param package_names [Array<String>]
    def set_allowed_apps(package_names)
      apps = package_names.map { |package| { "package" => package } }
      @http.put("/api/v1/private/phone/apps/allowed", "apps" => apps)
    end

    # POST /api/v1/private/phone/active-run
    # Register the currently active pipeline run on the phone (used by agents).
    # @param run_id [String]
    def register_active_run(run_id)
      @http.post("/api/v1/private/phone/active-run", "runId" => run_id)
    end

    private

    def enc(s)
      URI.encode_www_form_component(s.to_s)
    end
  end
end
