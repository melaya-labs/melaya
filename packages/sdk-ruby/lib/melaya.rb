# frozen_string_literal: true

require_relative "melaya/version"
require_relative "melaya/errors"
require_relative "melaya/http_client"
require_relative "melaya/market"
require_relative "melaya/account"
require_relative "melaya/sim"
require_relative "melaya/strategies"
require_relative "melaya/backtest"
require_relative "melaya/stream"
require_relative "melaya/trade"

module Melaya
  # The Melaya client.
  #
  # @example
  #   require "melaya"
  #   melaya = Melaya::Client.new(api_key: ENV["MK"])
  #
  #   # Market data
  #   t = melaya.market.ticker(exchange: "binance", symbol: "BTC/USDT", market: "spot")
  #   puts t["last"]
  #
  #   # Paper strategy
  #   result = melaya.strategies.create(
  #     name: "my-bot", strategy_type: "custom", exchange: "binanceusdm",
  #     symbol: "BTC/USDT:USDT", market: "FUTURES", dry_run: true,
  #     params: { language: "rhai", definition: 'fn evaluate() { emit_long(param("qty")); }', qty: 0.001 }
  #   )
  #   sid = result["strategyId"]
  #   melaya.strategies.stop(sid)
  #   melaya.strategies.delete(sid)
  class Client
    # REST market-data + reference endpoints (public plane).
    attr_reader :market
    # Authenticated account reads: connected keys, tier limits, usage.
    attr_reader :account
    # Paper trading (sim broker): virtual balance, positions, and orders.
    attr_reader :sim
    # Launch, control, and inspect trading strategies (paper + live).
    attr_reader :strategies
    # Historical backtests + parameter sweeps on the Rust engine.
    attr_reader :backtest
    # WebSocket streaming endpoints (public market data + private feeds).
    attr_reader :stream
    # Live trading — credentialed order placement and account state on a connected exchange. WARNING: real funds.
    attr_reader :trade

    # @param api_key [String] your Melaya API key, prefixed +mk_+
    # @param base_url [String] override the REST base URL
    # @param ws_url [String] override the WebSocket base URL
    # @param verify_ssl [Boolean] set false to skip TLS verification (dev-box only).
    #   Prefer using ENV["MELAYA_INSECURE_TLS"]="1" rather than passing this directly.
    def initialize(api_key:, base_url: HttpClient::DEFAULT_BASE_URL,
                   ws_url: StreamAPI::DEFAULT_WS_URL, verify_ssl: nil)
      raise ArgumentError, "Melaya: api_key is required (create one at melaya.org -> Settings -> API Keys)." \
        if api_key.nil? || api_key.empty?
      raise ArgumentError, "Melaya: API keys must be prefixed 'mk_'." \
        unless api_key.start_with?("mk_")

      ssl = if verify_ssl.nil?
        ENV["MELAYA_INSECURE_TLS"] != "1"
      else
        verify_ssl
      end

      http = HttpClient.new(api_key: api_key, base_url: base_url, verify_ssl: ssl)

      @market     = MarketAPI.new(http)
      @account    = AccountAPI.new(http)
      @sim        = SimAPI.new(http)
      @strategies = StrategiesAPI.new(http)
      @backtest   = BacktestAPI.new(http)
      @stream     = StreamAPI.new(api_key, ws_url, http, verify_ssl: ssl)
      @trade      = TradeAPI.new(http)
    end
  end
end
