# frozen_string_literal: true

require "socket"
require "openssl"
require "uri"
require "base64"
require "digest"
require "json"
require "securerandom"

require_relative "errors"

module Melaya
  # A minimal RFC 6455 WebSocket client built on stdlib TCP+TLS.
  # No external gem required. Supports text frames; yields parsed JSON frames.
  #
  # Usage (block form — closes automatically):
  #   MelayaWebSocket.connect(url, verify_ssl: true) do |ws|
  #     ws.each_frame { |frame| puts frame.inspect; break }
  #   end
  #
  # Usage (manual):
  #   ws = MelayaWebSocket.new(url, verify_ssl: true)
  #   ws.connect
  #   ws.each_frame { |f| ... }
  #   ws.close
  class MelayaWebSocket
    GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    def self.connect(url, verify_ssl: true, &block)
      ws = new(url, verify_ssl: verify_ssl)
      ws.connect
      if block_given?
        begin
          block.call(ws)
        ensure
          ws.close
        end
      else
        ws
      end
    end

    def initialize(url, verify_ssl: true)
      @uri        = URI.parse(url)
      @verify_ssl = verify_ssl
      @socket     = nil
      @closed     = false
      @buf        = String.new("", encoding: "BINARY")
    end

    def connect
      tcp = TCPSocket.new(@uri.host, @uri.port)

      @socket = if @uri.scheme == "wss"
        ctx = OpenSSL::SSL::SSLContext.new
        ctx.verify_mode = @verify_ssl ? OpenSSL::SSL::VERIFY_PEER : OpenSSL::SSL::VERIFY_NONE
        ssl = OpenSSL::SSL::SSLSocket.new(tcp, ctx)
        ssl.hostname = @uri.host
        ssl.connect
        ssl
      else
        tcp
      end

      handshake
      self
    end

    # Iterate over incoming JSON frames. Yields each parsed frame Hash.
    # Blocks until the socket closes or the block calls +break+.
    def each_frame
      loop do
        frame = read_frame
        break if frame.nil? # connection closed

        next if frame.empty? # ping/pong or non-text

        begin
          yield JSON.parse(frame)
        rescue JSON::ParserError
          next # ignore non-JSON keep-alive text
        end
      end
    rescue IOError, Errno::ECONNRESET, EOFError
      # socket closed by remote
    ensure
      close
    end

    def close
      return if @closed
      @closed = true
      begin
        # Send a close frame (opcode 0x8) with no body, masked
        send_frame(0x8, "")
      rescue StandardError
        # best-effort
      ensure
        @socket&.close rescue nil
      end
    end

    private

    def handshake
      key    = Base64.strict_encode64(SecureRandom.bytes(16))
      path   = @uri.request_uri
      host   = @uri.host + (@uri.port && ![80, 443].include?(@uri.port) ? ":#{@uri.port}" : "")

      request = [
        "GET #{path} HTTP/1.1",
        "Host: #{host}",
        "Upgrade: websocket",
        "Connection: Upgrade",
        "Sec-WebSocket-Key: #{key}",
        "Sec-WebSocket-Version: 13",
        "\r\n",
      ].join("\r\n")

      @socket.write(request)

      # Read response headers
      response = String.new("", encoding: "BINARY")
      loop do
        line = @socket.readline
        response << line
        break if line == "\r\n"
      end

      unless response.include?("101")
        raise MelayaError.new("WebSocket handshake failed: #{response.lines.first.strip}", status: 0)
      end

      expected = Base64.strict_encode64(Digest::SHA1.digest("#{key}#{GUID}"))
      unless response.include?(expected)
        raise MelayaError.new("WebSocket Sec-WebSocket-Accept mismatch", status: 0)
      end
    end

    # Read one complete WebSocket frame. Returns the payload string (text) or nil on close.
    # Skips ping frames (sends pong) and continuation frames (not used by Melaya).
    def read_frame
      loop do
        # Need at least 2 bytes for header
        fill_buf(2)
        b0 = @buf.getbyte(0)
        b1 = @buf.getbyte(1)
        @buf = @buf[2..]

        # fin     = (b0 & 0x80) != 0  # we accept single-frame messages
        opcode  = b0 & 0x0F
        masked  = (b1 & 0x80) != 0
        len     = b1 & 0x7F

        len = case len
              when 126
                fill_buf(2)
                v = @buf[0, 2].unpack1("n")
                @buf = @buf[2..]
                v
              when 127
                fill_buf(8)
                v = @buf[0, 8].unpack1("Q>")
                @buf = @buf[8..]
                v
              else
                len
              end

        mask_key = nil
        if masked
          fill_buf(4)
          mask_key = @buf[0, 4]
          @buf = @buf[4..]
        end

        fill_buf(len)
        payload = @buf[0, len].dup
        @buf = @buf[len..]

        if masked && mask_key
          payload.bytes.each_with_index { |b, i| payload.setbyte(i, b ^ mask_key.getbyte(i % 4)) }
        end

        case opcode
        when 0x1  # text
          return payload.force_encoding("UTF-8")
        when 0x2  # binary — treat as text (Melaya sends JSON)
          return payload.force_encoding("UTF-8")
        when 0x8  # close
          return nil
        when 0x9  # ping — send pong
          send_frame(0xA, payload)
          next
        when 0xA  # pong
          next
        else
          next # ignore unknown opcodes
        end
      end
    end

    # Ensure @buf has at least +n+ bytes.
    def fill_buf(n)
      while @buf.bytesize < n
        chunk = @socket.read(n - @buf.bytesize)
        raise EOFError, "connection closed" if chunk.nil? || chunk.empty?
        @buf = @buf + chunk
      end
    end

    # Send a WebSocket frame with masking (client->server MUST be masked).
    def send_frame(opcode, payload)
      payload = payload.dup.force_encoding("BINARY")
      len     = payload.bytesize

      header = String.new("", encoding: "BINARY")
      header << (0x80 | opcode).chr(Encoding::BINARY)

      mask_flag = 0x80 # clients must mask
      if len <= 125
        header << (mask_flag | len).chr(Encoding::BINARY)
      elsif len <= 65535
        header << (mask_flag | 126).chr(Encoding::BINARY)
        header << [len].pack("n")
      else
        header << (mask_flag | 127).chr(Encoding::BINARY)
        header << [len].pack("Q>")
      end

      mask_key = SecureRandom.bytes(4)
      header << mask_key

      masked_payload = payload.bytes.each_with_index.map { |b, i| b ^ mask_key.getbyte(i % 4) }.pack("C*")

      @socket.write(header + masked_payload)
    end
  end

  # WebSocket streaming API.
  #
  # Each method yields parsed JSON frame hashes to a block, or returns an
  # Enumerator if no block is given. The connection is closed when the block
  # returns or the Enumerator is exhausted.
  #
  # Public stream example:
  #   melaya.stream.ticker(exchange: "binance", symbol: "BTC/USDT", market: "spot") do |frame|
  #     puts frame["last"]
  #     break   # close after first frame
  #   end
  #
  # Private stream example (ticket minted automatically):
  #   melaya.stream.strategies do |ev|
  #     puts ev["type"], ev["strategyId"]
  #     break
  #   end
  class StreamAPI
    DEFAULT_WS_URL = "wss://wss.melaya.org"

    def initialize(api_key, ws_url, http, verify_ssl: true)
      @api_key    = api_key
      @ws_url     = ws_url.to_s.chomp("/")
      @http       = http
      @verify_ssl = verify_ssl
    end

    # Live ticker frames.
    def ticker(exchange:, symbol:, market: nil, &block)
      open_public("/ws/ticker", compact(exchange: exchange, symbol: symbol, market: market), &block)
    end

    # Live order-book frames.
    def orderbook(exchange:, symbol:, limit: nil, market: nil, &block)
      open_public("/ws/orderbook", compact(exchange: exchange, symbol: symbol, limit: limit, market: market), &block)
    end

    # Live OHLCV candle frames.
    def ohlcv(exchange:, symbol:, timeframe:, market: nil, &block)
      open_public("/ws/ohlcv", compact(exchange: exchange, symbol: symbol, timeframe: timeframe, market: market), &block)
    end

    # Live public-trade frames.
    def trades(exchange:, symbol:, market: nil, &block)
      open_public("/ws/public-trades", compact(exchange: exchange, symbol: symbol, market: market), &block)
    end

    # Cross-exchange liquidation firehose. Omit +exchange+ for all venues.
    def liquidations(exchange: nil, &block)
      open_public("/ws/liquidations", compact(exchange: exchange), &block)
    end

    # Live strategy events for your account (cycle markers, agent messages,
    # approval requests, executions, status). Mints a ticket, opens /ws/strategies.
    def strategies(&block)
      open_private("/ws/strategies", "strategies", {}, &block)
    end

    # Live private account feed for one connected exchange key (balance,
    # positions, your orders/fills). Pass +api_key_id+ from +account.keys()+.
    def private(exchange:, market: nil, api_key_id: nil, key_id: nil, symbol: nil, &block)
      params = compact(
        exchange: exchange, market: market,
        apiKeyId: api_key_id, keyId: key_id, symbol: symbol
      )
      open_private("/ws/private", "private", params, &block)
    end

    private

    def compact(hash)
      hash.reject { |_, v| v.nil? }
    end

    def build_url(path, params)
      q = params.merge("apiKey" => @api_key)
      "#{@ws_url}#{path}?#{URI.encode_www_form(q)}"
    end

    def open_public(path, params, &block)
      url = build_url(path, params.transform_keys(&:to_s))
      MelayaWebSocket.connect(url, verify_ssl: @verify_ssl) do |ws|
        ws.each_frame(&block)
      end
    end

    def open_private(path, stream, params, &block)
      body = { "stream" => stream }.merge(params.transform_keys(&:to_s))
      ticket = @http.post("/api/v1/private/private-ticket", body)["wsTicket"]
      url = "#{@ws_url}#{path}?#{URI.encode_www_form('wsTicket' => ticket)}"
      MelayaWebSocket.connect(url, verify_ssl: @verify_ssl) do |ws|
        ws.each_frame(&block)
      end
    end
  end
end
