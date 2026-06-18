# frozen_string_literal: true

require "net/http"
require "uri"
require "json"
require "openssl"

require_relative "errors"

module Melaya
  # Internal HTTP client. Injects the API key on every call as both
  # a query-param (?apiKey=) and Authorization: Bearer header.
  class HttpClient
    DEFAULT_BASE_URL = "https://api.melaya.org"

    def initialize(api_key:, base_url: DEFAULT_BASE_URL, verify_ssl: true)
      @api_key    = api_key
      @base_uri   = URI.parse(base_url.chomp("/"))
      @verify_ssl = verify_ssl
    end

    def get(path, params = {})
      request(:get, path, params: params)
    end

    def post(path, body = nil)
      request(:post, path, body: body)
    end

    def delete(path, params = {})
      request(:delete, path, params: params)
    end

    private

    def build_uri(path, params = {})
      uri = URI.parse("#{@base_uri}#{path}")
      query = { "apiKey" => @api_key }
      params.each { |k, v| query[k.to_s] = v.to_s unless v.nil? }
      uri.query = URI.encode_www_form(query)
      uri
    end

    def request(method, path, params: {}, body: nil)
      uri = build_uri(path, params)

      http = Net::HTTP.new(uri.host, uri.port)
      http.use_ssl = uri.scheme == "https"
      http.verify_mode = @verify_ssl ? OpenSSL::SSL::VERIFY_PEER : OpenSSL::SSL::VERIFY_NONE
      http.open_timeout = 15
      http.read_timeout = 60

      req = case method
            when :get    then Net::HTTP::Get.new(uri)
            when :post   then Net::HTTP::Post.new(uri)
            when :delete then Net::HTTP::Delete.new(uri)
            else raise ArgumentError, "Unknown HTTP method: #{method}"
            end

      req["Authorization"] = "Bearer #{@api_key}"
      req["Accept"]        = "application/json"

      if body
        req["Content-Type"] = "application/json"
        req.body = JSON.generate(body)
      end

      resp = http.request(req)
      parse(resp)
    end

    def parse(resp)
      text = resp.body.to_s.strip
      data = begin
        text.empty? ? nil : JSON.parse(text)
      rescue JSON::ParserError
        text
      end

      if resp.code.to_i >= 400
        code = data.is_a?(Hash) ? data["error"] : nil
        msg  = "Melaya API #{resp.code}" + (code ? " (#{code})" : "")
        raise MelayaError.new(msg, status: resp.code.to_i, code: code, body: data)
      end

      # The API wraps every payload in { "ok": true/false, ... }.
      # ok:false is a request-level failure — raise instead of returning silently.
      if data.is_a?(Hash) && data["ok"] == false
        code = data["error"]
        msg  = "Melaya API request failed" + (code ? ": #{code}" : "")
        raise MelayaError.new(msg, status: resp.code.to_i, code: code, body: data)
      end

      data
    end
  end
end
