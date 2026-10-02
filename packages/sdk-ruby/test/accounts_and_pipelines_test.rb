# frozen_string_literal: true

# Offline unit tests for connector accounts (personal + project), platform API
# key rotation, pipeline docs/retrieval previews, set_inputs, run_messages and
# usage_summary. No network: Net::HTTP#request is intercepted below (same
# pattern as test/triggers_test.rb), so every call still goes through the real
# HttpClient (URL building, JSON body, response parsing).
#
# Run: ruby -Ilib test/accounts_and_pipelines_test.rb

$LOAD_PATH.unshift(File.expand_path("../lib", __dir__))
require "minitest/autorun"
require "json"
require "melaya"

unless defined?(FakeNetHTTP)
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
end

class AccountsAndPipelinesTest < Minitest::Test
  C = "/api/v1/private/credentials"
  P = "/api/v1/private/projects"
  PL = "/api/v1/private/pipelines"
  ACCT = { "id" => "a1", "label" => "Shop EU", "isDefault" => true, "createdAt" => nil }.freeze

  def setup
    FakeNetHTTP.requests  = []
    FakeNetHTTP.responses = []
    @m = Melaya::Client.new(api_key: "mk_test", base_url: "https://unit-test.invalid")
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

  def assert_req(method, path)
    assert_equal method, last.method
    assert_equal path, last.path
  end

  # ── Personal connector accounts ────────────────────────────────────────────

  def test_credentials_accounts
    respond([ACCT])
    out = @m.credentials.accounts("shopify")
    assert_req "GET", "#{C}/shopify/accounts"
    assert_equal true, out[0]["isDefault"]
    assert_nil out[0]["createdAt"]
  end

  def test_credentials_accounts_encodes_space
    respond([])
    @m.credentials.accounts("my service")
    assert_req "GET", "#{C}/my%20service/accounts"
  end

  def test_credentials_add_account_camel_case_and_omits_nils
    respond([ACCT])
    @m.credentials.add_account("shopify", fields: { "SHOP" => "x" }, label: "Shop EU",
                               current_label: "Shop US", make_default: true)
    assert_req "POST", "#{C}/shopify/accounts"
    assert_equal({ "label" => "Shop EU", "fields" => { "SHOP" => "x" },
                   "currentLabel" => "Shop US", "makeDefault" => true }, last_body)

    respond([ACCT])
    @m.credentials.add_account("shopify", fields: { "SHOP" => "y" })
    assert_equal({ "fields" => { "SHOP" => "y" } }, last_body)
  end

  def test_credentials_set_default_account
    respond([ACCT])
    @m.credentials.set_default_account("shopify", "a1")
    assert_req "PUT", "#{C}/shopify/accounts/default"
    assert_equal({ "accountId" => "a1" }, last_body)
  end

  def test_credentials_identify_account
    respond([ACCT])
    @m.credentials.identify_account("shopify", "a 1")
    assert_req "POST", "#{C}/shopify/accounts/a%201/identify"
    assert_equal({}, last_body)
  end

  def test_credentials_rename_account
    respond([ACCT])
    @m.credentials.rename_account("shopify", "a1", label: "Main")
    assert_req "PUT", "#{C}/shopify/accounts/a1"
    assert_equal({ "label" => "Main" }, last_body)
  end

  def test_credentials_remove_account
    respond([])
    assert_equal [], @m.credentials.remove_account("shopify", "a/1")
    assert_req "DELETE", "#{C}/shopify/accounts/a%2F1"
  end

  # ── Project connector accounts ─────────────────────────────────────────────

  def test_connectors_accounts_encodes_space
    respond([ACCT])
    out = @m.connectors.accounts("My Project", "shopify")
    assert_req "GET", "#{P}/My%20Project/connectors/shopify/accounts"
    assert_equal "a1", out[0]["id"]
  end

  def test_connectors_add_account
    respond([ACCT])
    @m.connectors.add_account("ops", "shopify", fields: { "SHOP" => "x" }, make_default: false)
    assert_req "POST", "#{P}/ops/connectors/shopify/accounts"
    assert_equal({ "fields" => { "SHOP" => "x" }, "makeDefault" => false }, last_body)
  end

  def test_connectors_set_default_account
    respond([ACCT])
    @m.connectors.set_default_account("ops", "shopify", "a1")
    assert_req "PUT", "#{P}/ops/connectors/shopify/accounts/default"
    assert_equal({ "accountId" => "a1" }, last_body)
  end

  def test_connectors_rename_account
    respond([ACCT])
    @m.connectors.rename_account("ops", "shopify", "a1", label: "EU")
    assert_req "PUT", "#{P}/ops/connectors/shopify/accounts/a1"
    assert_equal({ "label" => "EU" }, last_body)
  end

  def test_connectors_remove_account
    respond([])
    @m.connectors.remove_account("ops", "shopify", "a1")
    assert_req "DELETE", "#{P}/ops/connectors/shopify/accounts/a1"
  end

  # ── Platform API key ───────────────────────────────────────────────────────

  def test_rotate_api_key
    respond({ "apiKey" => "mk_new" })
    out = @m.account.rotate_api_key
    assert_req "POST", "/api/v1/private/api-key"
    assert_equal({}, last_body)
    assert_equal "mk_new", out["apiKey"]
  end

  def test_revoke_api_key
    respond({ "ok" => true })
    out = @m.account.revoke_api_key
    assert_req "DELETE", "/api/v1/private/api-key"
    assert_equal true, out["ok"]
  end

  def test_api_key_usage
    respond({ "requests" => 12 })
    out = @m.account.api_key_usage
    assert_req "GET", "/api/v1/private/api-key/usage"
    assert_equal 12, out["requests"]
  end

  # ── Pipelines ──────────────────────────────────────────────────────────────

  def test_docs_preview_sends_model_query
    respond({ "docs" => [] })
    @m.pipelines.docs_preview("my pipe", model_name: "qwen3.7-plus", model_provider: "qwen")
    assert_equal "GET", last.method
    assert_equal "#{PL}/my%20pipe/docs/preview", URI(last.path).path
    assert_equal({ "model_name" => "qwen3.7-plus", "model_provider" => "qwen" }, last_query)
  end

  def test_docs_preview_omits_nil_query
    respond({})
    @m.pipelines.docs_preview("p")
    assert_req "GET", "#{PL}/p/docs/preview"
  end

  def test_retrieval_preview
    respond({ "chunks" => 4 })
    out = @m.pipelines.retrieval_preview("p")
    assert_req "GET", "#{PL}/p/docs/retrieval/preview"
    assert_equal 4, out["chunks"]
  end

  def test_test_retrieve
    respond({ "passages" => [] })
    @m.pipelines.test_retrieve("p", query: "refund policy", limit: 3)
    assert_req "POST", "#{PL}/p/docs/retrieval/test_retrieve"
    assert_equal({ "query" => "refund policy", "limit" => 3 }, last_body)

    respond({ "passages" => [] })
    @m.pipelines.test_retrieve("p", query: "x")
    assert_equal({ "query" => "x" }, last_body)
  end

  def test_set_inputs
    decl = [{ "key" => "topic", "label" => "Topic", "type" => "text", "required" => true }]
    respond({ "name" => "p", "inputs" => decl })
    out = @m.pipelines.set_inputs("my pipe", project: "Ops", inputs: decl)
    assert_req "PUT", "#{PL}/my%20pipe/inputs"
    assert_equal({ "inputs" => decl, "project" => "Ops" }, last_body)
    assert_equal "topic", out["inputs"][0]["key"]
  end

  def test_run_messages_path
    respond({ "messages" => [] })
    @m.hitl.run_messages("abc 123", limit: 10)
    assert_equal "GET", last.method
    assert_equal "/api/v1/private/runs/abc%20123/messages", URI(last.path).path
    assert_equal({ "limit" => "10" }, last_query)
  end

  def test_usage_summary
    respond({ "pipelineCount" => 3 })
    out = @m.pipelines.usage_summary
    assert_req "GET", "/api/v1/private/overview/usage"
    assert_equal 3, out["pipelineCount"]
  end
end
