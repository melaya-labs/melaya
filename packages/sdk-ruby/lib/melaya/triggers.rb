# frozen_string_literal: true

module Melaya
  # Triggers API: read, diagnose and dry-run the event triggers that start
  # your pipelines.
  #
  # Maps to /api/v1/private/triggers/*.
  #
  # Read-only apart from the dry runs and poll controls. Creating, updating,
  # deleting a trigger and rotating its signing secret stay in the Agent
  # Builder and the MCP server, where push consent and autonomy grants are
  # enforced. No method here returns a signing secret.
  #
  # +test+ and +poll_test+ are dry runs: the trigger's action never executes.
  # +poll_now+ queues a real poll.
  #
  # @example
  #   triggers = melaya.agents.triggers.list(project: "support")
  #   stats    = melaya.agents.triggers.stats(triggers[0]["id"], hours: 24)
  #
  #   # What happened recently, including events that wrote no receipt.
  #   live = melaya.agents.triggers.events(verdicts: %w[rejected failed], limit: 50)
  #
  #   # Dry run one event (the action never runs).
  #   test = melaya.agents.triggers.test(triggers[0]["id"], payload: { type: "ping" })
  #   # => { "accepted" => true, "eventId" => "test-..." }
  #
  #   # Also reachable via the flat alias:
  #   melaya.triggers.limits
  class TriggersAPI
    BASE = "/api/v1/private/triggers"

    def initialize(http)
      @http = http
    end

    # ── Triggers ──────────────────────────────────────────────────────────────

    # GET /triggers?project=&pipelineName=
    # List your event triggers.
    # @param project [String, nil] project name filter
    # @param pipeline_name [String, nil] pipeline name filter
    # @return [Array<Hash>] trigger records: id, publicId, name, kind, project,
    #   pipelineName, enabled, pausedReason, signingScheme, sourceId, config,
    #   rate caps, consecutiveFailures, lastEventAt, createdAt, updatedAt,
    #   webhookUrl, projectAccess
    def list(project: nil, pipeline_name: nil)
      @http.get(BASE, compact("project" => project, "pipelineName" => pipeline_name))
    end

    # GET /triggers/:id
    # One trigger with its config. Secrets are never included.
    # @param id [String]
    # @return [Hash]
    def get(id)
      @http.get("#{BASE}/#{enc(id)}")
    end

    # GET /triggers/:id/deliveries?limit=
    # Recent deliveries (receipts) of a trigger: verdict, decision answers,
    # action, run id and timings.
    # @param id [String]
    # @param limit [Integer, nil] 1-200, server default 50
    # @return [Array<Hash>]
    def deliveries(id, limit: nil)
      @http.get("#{BASE}/#{enc(id)}/deliveries", compact("limit" => limit))
    end

    # GET /triggers/:id/stats?hours=
    # Delivery counts by verdict over a window.
    # @param id [String]
    # @param hours [Integer, nil] 1-168, server default 24
    # @return [Hash] { "hours" => Integer, "byVerdict" => { verdict => { "n", "p50", "p95" } },
    #   "filtered" => Integer or nil, "sampled" => Boolean }
    def stats(id, hours: nil)
      @http.get("#{BASE}/#{enc(id)}/stats", compact("hours" => hours))
    end

    # GET /triggers/:id/approvals
    # Approvals still waiting on this trigger's runs and tool calls. Read
    # only: decide them in the Melaya app.
    # @param id [String]
    # @return [Array<Hash>]
    def pending_approvals(id)
      @http.get("#{BASE}/#{enc(id)}/approvals")
    end

    # POST /triggers/:id/test { payload }
    # Dry run one event through the prefilter, the decide step and routing.
    # The action never executes. The trigger's rate limits still apply, so a
    # refused test comes back with "accepted" => false and a "reason"
    # ("disabled", "rate_limited", ...). Follow the outcome with +events+
    # for this trigger.
    # @param id [String]
    # @param payload [Object, nil] the event payload; omit for an empty object
    # @return [Hash] { "accepted" => Boolean, "eventId" => String, "reason" => String (optional) }
    def test(id, payload: nil)
      @http.post("#{BASE}/#{enc(id)}/test", payload.nil? ? {} : { "payload" => payload })
    end

    # GET /triggers/events?triggerId=&since=&verdicts=&limit=
    # Recent live trigger events, newest first (the last 500 events or 24
    # hours), including the outcomes that write no receipt: filtered, shed,
    # ingress rejections, feed refusals and push lifecycle notes. To follow
    # new events, pass +since+ = the newest "at" already seen.
    # @param trigger_id [String, nil] only this trigger's events
    # @param since [Integer, nil] only events after this time, epoch milliseconds
    # @param verdicts [Array<String>, String, nil] e.g. %w[rejected filtered failed]
    # @param limit [Integer, nil] 1-200
    # @return [Hash] { "events" => [Hash], "scanned" => Integer,
    #   "retention" => { "maxEvents" => Integer, "ttlSec" => Integer } }
    def events(trigger_id: nil, since: nil, verdicts: nil, limit: nil)
      v = verdicts.is_a?(Array) ? verdicts.join(",") : verdicts
      v = nil if v == ""
      @http.get("#{BASE}/events",
                compact("triggerId" => trigger_id, "since" => since, "verdicts" => v, "limit" => limit))
    end

    # ── Poll triggers ─────────────────────────────────────────────────────────

    # GET /triggers/:triggerId/poll
    # Runtime state of a poll trigger: synced, status, lastError,
    # lastPolledAt, nextPollAt, armed, baselinePending, seenCount,
    # itemsPublished, consecutiveErrors, requestedIntervalSec,
    # effectiveIntervalSec, tierFloorSec.
    # @param trigger_id [String]
    # @return [Hash]
    def poll_status(trigger_id)
      @http.get("#{BASE}/#{enc(trigger_id)}/poll")
    end

    # POST /triggers/:triggerId/poll/test { dry: true }
    # Dry poll: calls the poll tool now and returns what it found and would
    # publish. Nothing is published. A poll that fails is returned as
    # { "dry" => true, "ok" => false, "error" => code } instead of raised.
    # Dry polls are throttled to one every few seconds (HTTP 429).
    # @param trigger_id [String]
    # @return [Hash] { "dry", "ok", "found", "baseline", "wouldPublish",
    #   "items" => [{ "id", "preview" }], "samplePayload" } or { "dry", "ok" => false, "error" }
    def poll_test(trigger_id)
      @http.post("#{BASE}/#{enc(trigger_id)}/poll/test", "dry" => true)
    rescue MelayaError => e
      # A failed dry poll is a normal 200 result, not a request failure.
      raise unless e.status.to_i < 400 && e.body.is_a?(Hash) && e.body.key?("dry")

      e.body
    end

    # POST /triggers/:triggerId/poll/test { dry: false }
    # Queue a real poll now. The trigger must be enabled ("trigger_disabled"
    # otherwise). Follow the outcome with +poll_status+ and +events+.
    # @param trigger_id [String]
    # @return [Hash] { "dry" => false, "queued" => Boolean }
    def poll_now(trigger_id)
      @http.post("#{BASE}/#{enc(trigger_id)}/poll/test", "dry" => false)
    end

    # POST /triggers/:triggerId/poll/sync
    # Re-create a poll trigger's runtime row from its saved config. Re-arms a
    # trigger whose poller never started.
    # @param trigger_id [String]
    # @return [Hash] { "result" => Object }
    def poll_sync(trigger_id)
      @http.post("#{BASE}/#{enc(trigger_id)}/poll/sync", {})
    end

    # ── Account-wide ──────────────────────────────────────────────────────────

    # GET /triggers/presets
    # Trigger presets available to you, given your connected services and plan.
    # @return [Hash] { "tier" => String, "tierFloorSec" => Integer, "presets" => [Hash], "beta" => Hash }
    def presets
      @http.get("#{BASE}/presets")
    end

    # GET /triggers/limits
    # Plan caps and usage: triggers and sources used against the cap, events
    # per minute, poll interval floor and approval TTL bounds.
    # @return [Hash]
    def limits
      @http.get("#{BASE}/limits")
    end

    # GET /triggers/sources
    # Your WebSocket and SSE stream sources.
    # @return [Array<Hash>]
    def sources
      @http.get("#{BASE}/sources")
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
