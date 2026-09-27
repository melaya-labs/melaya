# frozen_string_literal: true

module Melaya
  # Connector Tools API — call any of the user's connected-service tools
  # (Gmail, Slack, Stripe, ...) directly. Same surface the MCP server and the
  # Melaya Assistant use: list connected services, discover tools by keyword,
  # describe one, test a stored connector, start connecting a service, and
  # call a tool.
  #
  # Do not confuse this with +melaya.connectors+ (project-scoped credential
  # storage) — this module only calls already-connected tools.
  #
  # Maps to /api/v1/private/connector-tools/*.
  #
  # Read tools run immediately. A write tool defaults to +approval: "required"+
  # and is staged as the same approval card the Assistant raises in the
  # Melaya app: +call+ returns HTTP 202 (a success, not an error) with a
  # +requestId+; poll +call_status+ — or use +call_and_wait+ — until the user
  # has decided, and the write runs exactly once, on the first poll after
  # approval. Pass +approval: "none"+ to run a write immediately instead; it
  # is still audit-logged. Tools that move money or trade are always
  # refused, under BOTH approval modes. No method here ever accepts or
  # returns a credential value.
  #
  # @example
  #   services = melaya.agents.connector_tools.services
  #   found    = melaya.agents.connector_tools.search("unread email")
  #   info     = melaya.agents.connector_tools.describe("gmail_list_messages")
  #
  #   # Read — runs immediately
  #   read = melaya.agents.connector_tools.call("gmail_list_messages", args: { max_results: 5 })
  #   puts read["result"]
  #
  #   # Write — staged for approval by default
  #   staged = melaya.agents.connector_tools.call("gmail_send", args: { to: "a@b.com" })
  #   outcome = melaya.agents.connector_tools.call_status(staged["requestId"])
  #
  #   # Or block until the approval is decided (or times out)
  #   outcome = melaya.agents.connector_tools.call_and_wait("gmail_send", args: { to: "a@b.com" })
  #
  #   # Also reachable via the flat alias:
  #   melaya.connector_tools.services
  class ConnectorToolsAPI
    BASE = "/api/v1/private/connector-tools"

    # Statuses that end a +call_and_wait+ poll loop.
    TERMINAL_STATUSES = %w[done rejected expired].freeze

    # Defaults mirrored from the spec: poll every 3s, give up after 10 minutes.
    DEFAULT_POLL_INTERVAL_S = 3
    DEFAULT_WAIT_TIMEOUT_S  = 600

    def initialize(http)
      @http = http
    end

    # GET /connector-tools/services
    # Connected services and built-in tool counts. Names only.
    # @return [Hash] { "services" => [String], "builtIn" => "melaya_core",
    #   "toolCounts" => { service => { "readTools" => Integer, "writeTools" => Integer } } }
    def services
      @http.get("#{BASE}/services")
    end

    # GET /connector-tools/search?q=&limit=
    # Discover tools by plain business keywords (e.g. "unread email").
    # @param q [String] required — plain business keywords
    # @param limit [Integer, nil] 1-50, default 15
    # @return [Hash] { "query" => String, "services" => [String], "tools" => [Hash] }
    def search(q, limit: nil)
      @http.get("#{BASE}/search", compact("q" => q, "limit" => limit))
    end

    # GET /connector-tools/tools/:tool
    # Full description and parameters for one tool.
    # @param tool [String]
    # @return [Hash] ToolInfo — raises MelayaError (404) when unknown, or not
    #   unlocked by any of your connected services.
    def describe(tool)
      @http.get("#{BASE}/tools/#{enc(tool)}")
    end

    # POST /connector-tools/test { service }
    # Test the STORED credential for a connected service.
    # Raises MelayaError (504, code "timeout") if the service does not
    # answer within 30 seconds server-side.
    # @param service [String]
    # @return [Hash] { "service" => String, "success" => Boolean, "message" => String }
    def test(service)
      @http.post("#{BASE}/test", "service" => service)
    end

    # POST /connector-tools/connect { service }
    # Start connecting a service. Never accepts a secret — OAuth services
    # return an authorization URL to open; other kinds return where to store
    # the credential in the Melaya app.
    # @param service [String]
    # @return [Hash] { "service", "kind" => "oauth"|"oauth_unavailable"|"interactive_login"|"api_key",
    #   "authorizationUrl" => String (oauth only), "connectUrl" => String, "message" => String }
    def connect(service)
      @http.post("#{BASE}/connect", "service" => service)
    end

    # POST /connector-tools/call { tool, args, approval }
    #
    # A read tool (or a write with approval: "none") runs immediately and
    # returns 200 with the result. A write with approval: "required"
    # (default) is staged and returns 202 — a success, not an error — with a
    # +requestId+ to poll via +call_status+ (or +call_and_wait+).
    #
    # Money-moving/trading tools are refused under both approval modes, and
    # any other error response (400/403/404/502/503) is raised as a
    # MelayaError by the underlying HTTP client, same as every other call in
    # this SDK.
    #
    # @param tool [String]
    # @param args [Hash] tool arguments
    # @param approval [String] "required" (default, stage for approval) or "none" (run immediately)
    # @return [Hash] { "status" => "done", "tool", "readOnly", "result" => String } or
    #   { "status" => "pending_approval", "tool", "requestId", "message" }
    def call(tool, args: {}, approval: "required")
      @http.post("#{BASE}/call", "tool" => tool, "args" => args || {}, "approval" => approval)
    end

    # GET /connector-tools/calls/:requestId
    # Outcome of a staged write. Raises MelayaError (404) when the request is
    # unknown or has expired.
    # @param request_id [String]
    # @return [Hash] one of:
    #   { "requestId", "tool", "status" => "pending" | "running" | "expired" }
    #   { "requestId", "tool", "status" => "done", "ok" => Boolean, "result"/"error" => String }
    #   { "requestId", "tool", "status" => "rejected", "reason" => String }
    def call_status(request_id)
      @http.get("#{BASE}/calls/#{enc(request_id)}")
    end

    # Helper: call a tool and, if it is staged for approval, block polling
    # +call_status+ until the user has decided (done/rejected) or the
    # request expires, then return that outcome. If the tool ran immediately
    # (a read, or approval: "none"), returns that response as-is with no
    # polling. Synchronous/blocking, like the rest of this SDK.
    #
    # @param tool [String]
    # @param args [Hash]
    # @param approval [String] "required" (default) or "none"
    # @param poll_interval_s [Numeric] seconds between polls, default 3
    # @param timeout_s [Numeric] give up polling after this many seconds and
    #   return the last-seen (non-terminal) status, default 600 (10 minutes)
    # @return [Hash] the immediate +call+ response, or the final +call_status+ outcome
    def call_and_wait(tool, args: {}, approval: "required",
                       poll_interval_s: DEFAULT_POLL_INTERVAL_S,
                       timeout_s: DEFAULT_WAIT_TIMEOUT_S)
      resp = call(tool, args: args, approval: approval)
      return resp unless resp.is_a?(Hash) && resp["status"] == "pending_approval"

      request_id = resp["requestId"]
      deadline   = Time.now + timeout_s
      loop do
        outcome = call_status(request_id)
        return outcome if outcome.is_a?(Hash) && TERMINAL_STATUSES.include?(outcome["status"])
        return outcome if Time.now >= deadline

        sleep(poll_interval_s)
      end
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
