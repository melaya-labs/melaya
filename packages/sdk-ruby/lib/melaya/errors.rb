# frozen_string_literal: true

module Melaya
  # Raised for non-2xx REST responses or ok:false response envelopes.
  #
  # Two server error shapes are normalised here:
  #   1. { error: 'tier_insufficient', tier: '...' }  → HTTP 403
  #   2. { error: '...', message: '...', code: '...' } → other 4xx/5xx
  class MelayaError < StandardError
    # HTTP status code (Integer), or 0 for transport/WS errors.
    attr_reader :status
    # Short machine-readable error code from the server (String or nil).
    attr_reader :code
    # Raw response body (Hash, String, or nil). Not included in #message so
    # secrets embedded in error payloads do not leak to log lines.
    attr_reader :body

    def initialize(message, status: 0, code: nil, body: nil)
      super(message)
      @status = status
      @code   = code
      @body   = body
    end
  end

  # Raised when the server returns { error: 'tier_insufficient' } (HTTP 403).
  # Check +#tier+ for the minimum required tier string.
  class TierInsufficientError < MelayaError
    attr_reader :tier

    def initialize(tier: nil, body: nil)
      @tier = tier
      super("Melaya: feature requires a higher tier (#{tier})", status: 403, code: "tier_insufficient", body: body)
    end
  end

  # Raised when the server returns HTTP 429 (rate-limited) after all retries
  # are exhausted.
  class RateLimitError < MelayaError
    # Retry-After header value in seconds, if present.
    attr_reader :retry_after

    def initialize(retry_after: nil, body: nil)
      @retry_after = retry_after
      super("Melaya: rate limit exceeded#{retry_after ? " (retry after #{retry_after}s)" : ""}",
            status: 429, code: "rate_limited", body: body)
    end
  end
end
