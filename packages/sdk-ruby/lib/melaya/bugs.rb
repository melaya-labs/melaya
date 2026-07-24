# frozen_string_literal: true

module Melaya
  # Bugs API — submit and track bug reports.
  #
  # Maps to /api/v1/private/bugs/*.
  #
  # @example
  #   melaya.bugs.create(title: "UI crash", description: "...")
  #   reports = melaya.bugs.list_mine
  class BugsAPI
    def initialize(http)
      @http = http
    end

    # POST /api/v1/private/bugs
    # Submit a bug report (user-facing feedback form).
    # @param title [String]
    # @param description [String]
    # @param extra [Hash] any additional fields
    def create(title:, description: nil, **extra)
      body = extra.transform_keys(&:to_s)
      body["title"]       = title
      body["description"] = description unless description.nil?
      @http.post("/api/v1/private/bugs", body)
    end

    # GET /api/v1/private/bugs/mine
    # List bug reports submitted by the caller.
    def list_mine
      @http.get("/api/v1/private/bugs/mine")
    end

    # GET /api/v1/private/bugs/:bugId
    # Get a single bug report by ID.
    # @param bug_id [String]
    def get(bug_id)
      @http.get("/api/v1/private/bugs/#{enc(bug_id)}")
    end

    # POST /api/v1/private/bugs/:bugId/comments
    # Add a comment to a bug report.
    # @param bug_id [String]
    # @param comment [String]
    def add_comment(bug_id, comment:)
      @http.post("/api/v1/private/bugs/#{enc(bug_id)}/comments", "comment" => comment)
    end

    # GET /api/v1/private/bugs/notifications
    # List unread bug-related notifications for the caller.
    def list_notifications
      @http.get("/api/v1/private/bugs/notifications")
    end

    # POST /api/v1/private/bugs/notifications/read
    # Mark bug notifications as read.
    def mark_notifications_read(body = {})
      @http.post("/api/v1/private/bugs/notifications/read", body)
    end

    private

    def enc(s)
      URI.encode_www_form_component(s.to_s)
    end
  end
end
