# frozen_string_literal: true

module Melaya
  # Raised for non-2xx REST responses or ok:false envelopes.
  class MelayaError < StandardError
    attr_reader :status, :code, :body

    def initialize(message, status: 0, code: nil, body: nil)
      super(message)
      @status = status
      @code   = code
      @body   = body
    end
  end
end
