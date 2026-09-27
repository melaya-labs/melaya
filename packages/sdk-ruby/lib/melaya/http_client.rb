# frozen_string_literal: true

require "net/http"
require "uri"
require "json"
require "openssl"
require "securerandom"

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
    #
    # Every verb accepts an optional trailing +timeout_s+ override for that
    # single call (e.g. a slow RAG ingest job); it defaults to the client's own
    # timeout. NOTE: it is a plain positional parameter, not a keyword — many
    # call sites pass a bare `"key" => value` Hash literal as +body+/+params+,
    # and Ruby 3's keyword/Hash separation would otherwise raise
    # "unknown keyword" on every one of them if this were `timeout_s:`.

    def get(path, params = {}, timeout_s = nil)
      request(:get, path, params: params, timeout_s: timeout_s)
    end

    def post(path, body = nil, timeout_s = nil)
      request(:post, path, body: body, timeout_s: timeout_s)
    end

    def put(path, body = nil, timeout_s = nil)
      request(:put, path, body: body, timeout_s: timeout_s)
    end

    def patch(path, body = nil, timeout_s = nil)
      request(:patch, path, body: body, timeout_s: timeout_s)
    end

    # +body+ is optional: some bridged DELETE routes take structured input
    # (e.g. googleDisconnect's { accountId, capability? }) that should not be
    # exposed in a URL, so it travels as a JSON body instead of query params.
    def delete(path, params = {}, body = nil, timeout_s = nil)
      request(:delete, path, params: params, body: body, timeout_s: timeout_s)
    end

    # GET that returns the RAW response body (String, binary encoding) instead
    # of JSON-parsing it — for binary downloads like +runInputFile+. Errors are
    # still parsed and raised exactly like the JSON verb helpers. Retried like
    # any other GET (idempotent).
    def get_bytes(path, params = {}, timeout_s = nil)
      request(:get, path, params: params, timeout_s: timeout_s, raw: true)
    end

    # Upload a single file as `multipart/form-data` with one file part named
    # +field_name+. Never retried (a partial multipart re-send could double an
    # upload with side effects), and the response is parsed exactly like the
    # JSON POST helper (same error type).
    #
    # @param path [String]
    # @param query [Hash] query-string params (e.g. { "key" => ..., "project" => ... })
    # @param field_name [String] the multipart field name the server expects (e.g. "file")
    # @param bytes [String] raw file content
    # @param filename [String] filename reported in the part's Content-Disposition
    # @param content_type [String, nil] defaults to "application/octet-stream"
    def post_multipart(path, query, field_name, bytes, filename, content_type = nil)
      uri = build_uri(path, query || {})
      boundary = "MelayaFormBoundary#{SecureRandom.hex(16)}"

      http = Net::HTTP.new(uri.host, uri.port)
      http.use_ssl      = uri.scheme == "https"
      http.verify_mode  = OpenSSL::SSL::VERIFY_PEER
      http.open_timeout = @timeout_s
      http.read_timeout = @timeout_s

      req = Net::HTTP::Post.new(uri)
      req["Authorization"] = "Bearer #{@_tok}"
      req["Accept"]        = "application/json"
      req["User-Agent"]    = "melaya-ruby/#{Melaya::VERSION}"
      req["Content-Type"]  = "multipart/form-data; boundary=#{boundary}"
      req.body = multipart_body(boundary, field_name, bytes, filename, content_type)

      resp = http.request(req)
      parse(resp)
    end

    private

    # Builds a single-file multipart/form-data body by hand (no dependency on
    # any multipart-encoding gem).
    def multipart_body(boundary, field_name, bytes, filename, content_type)
      ct = content_type || "application/octet-stream"
      head =
        "--#{boundary}\r\n" \
        "Content-Disposition: form-data; name=\"#{field_name}\"; filename=\"#{escape_multipart_value(filename)}\"\r\n" \
        "Content-Type: #{ct}\r\n\r\n"
      tail = "\r\n--#{boundary}--\r\n"
      (head.b + bytes.to_s.b + tail.b)
    end

    # Escapes double quotes / newlines out of a Content-Disposition value.
    def escape_multipart_value(value)
      value.to_s.gsub("\\", "\\\\\\\\").gsub('"', '\\"').tr("\r\n", "  ")
    end

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

    def request(method, path, params: {}, body: nil, timeout_s: nil, raw: false)
      uri = build_uri(path, params)

      eff_timeout = timeout_s ? [timeout_s.to_f, 0.001].max.ceil : @timeout_s

      http = Net::HTTP.new(uri.host, uri.port)
      http.use_ssl      = uri.scheme == "https"
      http.verify_mode  = OpenSSL::SSL::VERIFY_PEER
      http.open_timeout = eff_timeout
      http.read_timeout = eff_timeout

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
        raw ? parse_raw(resp) : parse(resp)
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
      raise_for_status!(resp, data, status) if status >= 400

      # The API may wrap payload in { "ok": false, ... } for request-level failures.
      if data.is_a?(Hash) && data["ok"] == false
        err_code = data["error"]
        msg = "Melaya API request failed" + (err_code ? ": #{err_code}" : "")
        raise MelayaError.new(msg, status: resp.code.to_i, code: err_code, body: data)
      end

      data
    end

    # Like +parse+, but for a raw binary body (a file download): on success
    # the response body is returned unparsed; on error the same JSON error
    # envelopes and exception types apply.
    def parse_raw(resp)
      status = resp.code.to_i
      if status >= 400
        text = resp.body.to_s.strip
        data = begin
          text.empty? ? nil : JSON.parse(text)
        rescue JSON::ParserError
          text
        end
        raise_for_status!(resp, data, status)
      end
      resp.body.to_s
    end

    # Shared 4xx/5xx handling for both +parse+ and +parse_raw+. Two error
    # envelope shapes:
    #   1. { error: 'tier_insufficient', tier: '...' }  -> 403
    #   2. { error: '...', message: '...', code: '...' }
    # Extract error code safely — never echo raw body in message.
    def raise_for_status!(resp, data, status)
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
  end
end
