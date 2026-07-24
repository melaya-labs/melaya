# frozen_string_literal: true

require "net/http"
require "uri"
require "json"
require "openssl"

require_relative "errors"

module Melaya
  # Internal HTTP client. Supports Bearer JWT *and* mk_* platform API key.
  # The credential is sent ONLY via the Authorization header — never in the URL
  # query string, so it cannot leak into access logs or proxies. TLS is
  # enforced by default; never log secrets.
  #
  # Retry policy: bounded exponential back-off with jitter on network errors,
  # 429, and 5xx — but ONLY for idempotent GET requests (max 2 retries).
  # POST/PUT/PATCH/DELETE are never retried. Retry-After header is honoured
  # on 429. Per-request timeout default: 30 seconds (configurable).
  class HttpClient
    DEFAULT_BASE_URL    = "https://api.melaya.org"
    DEFAULT_TIMEOUT_MS  = 30_000  # milliseconds

    # Maximum additional retries after the first attempt (2 retries = 3 total
    # attempts) for idempotent GET requests only.
    MAX_GET_RETRIES = 2
    RETRY_STATUSES  = [429, 500, 502, 503, 504].freeze

    # @param api_key [String] mk_* platform key or Bearer JWT
    # @param base_url [String]
    # @param verify_ssl [Boolean]
    # @param timeout_ms [Integer] per-request timeout in milliseconds (default 30 000)
    def initialize(api_key:, base_url: DEFAULT_BASE_URL, verify_ssl: true,
                   timeout_ms: DEFAULT_TIMEOUT_MS)
      raise ArgumentError, "Melaya: TLS certificate verification cannot be disabled." unless verify_ssl
      # Never store in a way that could leak to logs accidentally — keep as
      # an opaque token string only accessible through the private accessor.
      @_tok       = api_key.freeze
      @base_uri   = URI.parse(base_url.chomp("/"))
      @verify_ssl = true
      @timeout_s  = (timeout_ms / 1000.0).ceil
    end

    # ── Public verb helpers ────────────────────────────────────────────────────

    def get(path, params = {})
      request(:get, path, params: params)
    end

    def post(path, body = nil)
      request(:post, path, body: body)
    end

    def put(path, body = nil)
      request(:put, path, body: body)
    end

    def patch(path, body = nil)
      request(:patch, path, body: body)
    end

    def delete(path, params = {})
      request(:delete, path, params: params)
    end

    private

    def build_uri(path, params = {})
      uri = URI.parse("#{@base_uri}#{path}")
      # SECURITY: the credential is never placed in the query string; it is
      # sent only via the Authorization header (see make_request).
      query = {}
      params.each { |k, v| query[k.to_s] = v.to_s unless v.nil? }
      uri.query = URI.encode_www_form(query) unless query.empty?
      uri
    end

    def make_request(method, uri, body)
      req = case method
            when :get    then Net::HTTP::Get.new(uri)
            when :post   then Net::HTTP::Post.new(uri)
            when :put    then Net::HTTP::Put.new(uri)
            when :patch  then Net::HTTP::Patch.new(uri)
            when :delete then Net::HTTP::Delete.new(uri)
            else raise ArgumentError, "Unknown HTTP method: #{method}"
            end

      # Authorization: never expose token in error output below
      req["Authorization"] = "Bearer #{@_tok}"
      req["Accept"]        = "application/json"
      req["User-Agent"]    = "melaya-ruby/#{Melaya::VERSION}"

      if body
        req["Content-Type"] = "application/json"
        req.body = JSON.generate(body)
      end

      req
    end

    def request(method, path, params: {}, body: nil)
      uri = build_uri(path, params)

      http = Net::HTTP.new(uri.host, uri.port)
      http.use_ssl      = uri.scheme == "https"
      http.verify_mode  = OpenSSL::SSL::VERIFY_PEER
      http.open_timeout = @timeout_s
      http.read_timeout = @timeout_s

      # Only GET requests are retried (idempotent); all mutating verbs fail fast.
      retryable = (method == :get)
      attempt   = 0

      retry_after_hdr = nil
      begin
        attempt += 1
        retry_after_hdr = nil  # reset on each attempt
        req  = make_request(method, uri, body)
        resp = http.request(req)
        # Snapshot Retry-After before parse() consumes the response object,
        # so we can honour the header even after the MelayaError is raised.
        retry_after_hdr = resp["retry-after"] || resp["Retry-After"]
        parse(resp)
      rescue MelayaError => e
        if retryable && RETRY_STATUSES.include?(e.status) && attempt <= MAX_GET_RETRIES
          # Build a minimal resp-like object carrying only the header we need,
          # so _backoff_delay can honour Retry-After without holding the socket.
          hdr_carrier = { "retry-after" => retry_after_hdr }
          delay = _backoff_delay(attempt, e, hdr_carrier)
          sleep(delay)
          retry
        end
        raise
      rescue Errno::ECONNREFUSED, Net::OpenTimeout, Net::ReadTimeout
        raise unless retryable && attempt <= MAX_GET_RETRIES
        sleep(_backoff_delay(attempt, nil))
        retry
      end
    end

    # Exponential backoff with ±25 % jitter; honours Retry-After on 429.
    # Base: 2^(attempt-1) seconds, capped at 16 s before jitter.
    #
    # Retry-After resolution order (first match wins):
    #   1. HTTP `Retry-After` response header — seconds integer or HTTP-date
    #   2. JSON body `retryAfter` / `retry_after` field (legacy fallback)
    #   3. Exponential back-off
    def _backoff_delay(attempt, err, resp = nil)
      if err.is_a?(MelayaError) && err.status == 429
        # 1. HTTP Retry-After header (preferred, RFC 7231)
        if resp.respond_to?(:[]) && (ra_hdr = resp["retry-after"] || resp["Retry-After"])
          secs = _parse_retry_after_header(ra_hdr)
          return [secs, 0.5].max if secs
        end

        # 2. JSON body fallback ("retryAfter" or "retry_after")
        if err.respond_to?(:body) && err.body.is_a?(Hash)
          ra = err.body["retryAfter"] || err.body["retry_after"]
          return [ra.to_f, 0.5].max if ra
        end
      end
      base  = [2**(attempt - 1), 16].min.to_f
      jitter = base * 0.25 * (rand - 0.5) * 2  # ±25 %
      [base + jitter, 0.1].max
    end

    # Parse an RFC 7231 Retry-After value: either a delay-seconds integer
    # or an HTTP-date string. Returns seconds as Float, or nil if unparseable.
    def _parse_retry_after_header(value)
      str = value.to_s.strip
      # Delay-seconds: plain non-negative integer
      if str =~ /\A\d+\z/
        return str.to_f
      end
      # HTTP-date (e.g. "Wed, 21 Oct 2099 07:28:00 GMT")
      begin
        require "time"
        target = Time.httpdate(str)
        delay  = target - Time.now
        return [delay, 0.0].max
      rescue ArgumentError, TypeError
        nil
      end
    end

    def parse(resp)
      text = resp.body.to_s.strip
      data = begin
        text.empty? ? nil : JSON.parse(text)
      rescue JSON::ParserError
        text
      end

      status = resp.code.to_i
      if status >= 400
        # Two error envelope shapes:
        #   1. { error: 'tier_insufficient', tier: '...' }  -> 403
        #   2. { error: '...', message: '...', code: '...' }
        # Extract error code safely — never echo raw body in message
        err_code = data.is_a?(Hash) ? data["error"] : nil

        if status == 403 && err_code == "tier_insufficient"
          raise TierInsufficientError.new(tier: data.is_a?(Hash) ? data["tier"] : nil, body: data)
        end
        if status == 429
          # Raised here; the GET retry loop above may swallow-and-retry it —
          # callers only see it once retries are exhausted.
          ra = _parse_retry_after_header(resp["retry-after"] || resp["Retry-After"])
          raise RateLimitError.new(retry_after: ra, body: data)
        end

        msg = "Melaya API #{resp.code}" + (err_code ? " (#{err_code})" : "")
        raise MelayaError.new(msg, status: status, code: err_code, body: data)
      end

      # The API may wrap payload in { "ok": false, ... } for request-level failures.
      if data.is_a?(Hash) && data["ok"] == false
        err_code = data["error"]
        msg = "Melaya API request failed" + (err_code ? ": #{err_code}" : "")
        raise MelayaError.new(msg, status: resp.code.to_i, code: err_code, body: data)
      end

      data
    end
  end
end
