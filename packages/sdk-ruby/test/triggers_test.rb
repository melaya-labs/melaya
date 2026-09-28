# frozen_string_literal: true

# Offline unit tests for Melaya::TriggersAPI. No network: Net::HTTP#request is
# intercepted below, so every call still goes through the real HttpClient
# (URL building, JSON body, response parsing, error mapping).
#
# Run: ruby -Ilib test/triggers_test.rb

$LOAD_PATH.unshift(File.expand_path("../lib", __dir__))
require "minitest/autorun"
require "json"
require "melaya"

module FakeNetHTTP
  class << self
    attr_accessor :requests, :responses
  end
  self.requests  = []
  self.responses = []

  def request(req, *_args)
    FakeNetHTTP.requests << req
    status, body = FakeNetHTTP.responses.shift || [200, "{}"]
    klass = Net::HTTPResponse::CODE_TO_OBJ[status.to_s] || Net::HTTPResponse
    resp = klass.new("1.1", status.to_s, "")
    resp.instance_variable_set(:@body, body)
    resp.instance_variable_set(:@read, true)
    resp
  end
end
Net::HTTP.prepend(FakeNetHTTP)

class TriggersTest < Minitest::Test
  T = "/api/v1/private/triggers"

  def setup
    FakeNetHTTP.requests  = []
    FakeNetHTTP.responses = []
    @m = Melaya::Client.new(api_key: "mk_test", base_url: "https://unit-test.invalid")
    @t = @m.agents.triggers
  end

  def respond(body, status = 200)
    FakeNetHTTP.responses << [status, body.is_a?(String) ? body : JSON.generate(body)]
  end

  def last
    FakeNetHTTP.requests.last
  end

  def last_query
    URI.decode_www_form(URI(last.path).query.to_s).to_h
  end

  def last_body
    JSON.parse(last.body)
  end

  def test_exposed_on_agents_and_flat_alias
    assert_same @m.triggers, @m.agents.triggers
    assert_instance_of Melaya::TriggersAPI, @t
  end

  def test_list_sends_filters_and_decodes
    respond([{ "id" => "t1", "kind" => "webhook", "enabled" => true }])
    out = @t.list(project: "support", pipeline_name: "refunds")
    assert_equal "GET", last.method
    assert_equal T, URI(last.path).path
    assert_equal({ "project" => "support", "pipelineName" => "refunds" }, last_query)
    assert_equal "t1", out[0]["id"]
    assert_equal true, out[0]["enabled"]
    assert_equal "Bearer mk_test", last["Authorization"]
  end

  def test_list_without_filters_has_no_query
    respond([])
    assert_equal [], @t.list
    assert_nil URI(last.path).query
  end

  def test_get_escapes_id
    respond({ "id" => "a/b", "kind" => "poll" })
    out = @t.get("a/b")
    assert_equal "GET", last.method
    assert_equal "#{T}/a%2Fb", last.path
    assert_equal "poll", out["kind"]
  end

  def test_deliveries_sends_limit
    respond([{ "id" => "d1", "verdict" => "dispatched" }])
    out = @t.deliveries("t1", limit: 20)
    assert_equal "#{T}/t1/deliveries?limit=20", last.path
    assert_equal "dispatched", out[0]["verdict"]
  end

  def test_stats_sends_hours_and_decodes
    respond({ "hours" => 48, "byVerdict" => { "dispatched" => { "n" => 12, "p50" => 120.5, "p95" => 900 } },
              "filtered" => 3, "sampled" => false })
    out = @t.stats("t1", hours: 48)
    assert_equal "#{T}/t1/stats?hours=48", last.path
    assert_equal 12, out["byVerdict"]["dispatched"]["n"]
    assert_equal 3, out["filtered"]
  end

  def test_pending_approvals
    respond([{ "requestId" => "r1" }])
    out = @t.pending_approvals("t1")
    assert_equal "GET", last.method
    assert_equal "#{T}/t1/approvals", last.path
    assert_equal "r1", out[0]["requestId"]
  end

  def test_test_posts_payload_and_decodes
    respond({ "accepted" => false, "eventId" => "test-1", "reason" => "rate_limited" })
    out = @t.test("t1", payload: { "type" => "refund.created", "amount" => 12 })
    assert_equal "POST", last.method
    assert_equal "#{T}/t1/test", last.path
    assert_equal({ "payload" => { "type" => "refund.created", "amount" => 12 } }, last_body)
    assert_equal "application/json", last["Content-Type"]
    assert_equal false, out["accepted"]
    assert_equal "rate_limited", out["reason"]
  end

  def test_test_without_payload_sends_empty_object
    respond({ "accepted" => true, "eventId" => "test-2" })
    @t.test("t1")
    assert_equal "{}", last.body
  end

  def test_events_sends_all_filters_with_comma_verdicts
    respond({ "events" => [{ "triggerId" => "t1", "deliveryId" => nil, "verdict" => "rejected", "at" => 1_790_000_000_000 }],
              "scanned" => 40, "retention" => { "maxEvents" => 500, "ttlSec" => 86_400 } })
    out = @t.events(trigger_id: "t1", since: 1_789_999_999_000, verdicts: %w[rejected failed], limit: 50)
    assert_equal "GET", last.method
    assert_equal "#{T}/events", URI(last.path).path
    assert_equal({ "triggerId" => "t1", "since" => "1789999999000", "verdicts" => "rejected,failed", "limit" => "50" },
                 last_query)
    assert_nil out["events"][0]["deliveryId"]
    assert_equal 500, out["retention"]["maxEvents"]
  end

  def test_events_omits_empty_verdicts
    respond({ "events" => [], "scanned" => 0 })
    @t.events(verdicts: [])
    assert_nil URI(last.path).query
  end

  def test_poll_status
    respond({ "synced" => true, "status" => "ok", "armed" => true, "effectiveIntervalSec" => 300 })
    out = @t.poll_status("t1")
    assert_equal "GET", last.method
    assert_equal "#{T}/t1/poll", last.path
    assert_equal 300, out["effectiveIntervalSec"]
  end

  def test_poll_test_posts_dry_true
    respond({ "dry" => true, "ok" => true, "found" => 3, "baseline" => false, "wouldPublish" => 2,
              "items" => [{ "id" => "i1", "preview" => "a" }], "samplePayload" => { "title" => "a" } })
    out = @t.poll_test("t1")
    assert_equal "POST", last.method
    assert_equal "#{T}/t1/poll/test", last.path
    assert_equal({ "dry" => true }, last_body)
    assert_equal 2, out["wouldPublish"]
    assert_equal "i1", out["items"][0]["id"]
  end

  def test_poll_test_failed_poll_is_returned_not_raised
    respond({ "dry" => true, "ok" => false, "error" => "tool_failed" })
    out = @t.poll_test("t1")
    assert_equal false, out["ok"]
    assert_equal "tool_failed", out["error"]
  end

  def test_poll_test_throttled_raises
    respond({ "error" => "poll_dry_run_throttled" }, 429)
    err = assert_raises(Melaya::MelayaError) { @t.poll_test("t1") }
    assert_equal 429, err.status
    assert_equal 1, FakeNetHTTP.requests.size # POST is never retried
  end

  def test_poll_now_posts_dry_false
    respond({ "dry" => false, "queued" => true })
    out = @t.poll_now("t1")
    assert_equal "#{T}/t1/poll/test", last.path
    assert_equal({ "dry" => false }, last_body)
    assert_equal true, out["queued"]
  end

  def test_poll_sync
    respond({ "result" => "armed" })
    out = @t.poll_sync("t1")
    assert_equal "POST", last.method
    assert_equal "#{T}/t1/poll/sync", last.path
    assert_equal "armed", out["result"]
  end

  def test_presets_limits_sources
    respond({ "tier" => "pro", "tierFloorSec" => 60, "presets" => [], "beta" => { "allowed" => true } })
    assert_equal "pro", @t.presets["tier"]
    assert_equal "#{T}/presets", last.path

    respond({ "tierClass" => "pro", "triggers" => { "used" => 2, "cap" => 20 } })
    assert_equal 20, @t.limits["triggers"]["cap"]
    assert_equal "#{T}/limits", last.path

    respond([{ "id" => "s1" }])
    assert_equal "s1", @t.sources[0]["id"]
    assert_equal "#{T}/sources", last.path
  end
end
