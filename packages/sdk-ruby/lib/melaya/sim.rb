# frozen_string_literal: true

module Melaya
  # Paper-trading (sim broker) API.
  #
  # The sim broker synthesises fills from Melaya's live ticker tape and keeps a
  # virtual wallet per strategy — no venue-side state changes, no exchange
  # credentials needed. Every call is scoped to a +strategy_id+.
  #
  # Create a paper strategy first:
  #   result = melaya.strategies.create(name: "test", strategy_type: "custom", ...)
  #   sid = result["strategyId"]
  class SimAPI
    def initialize(http)
      @http = http
    end

    # Paper accounts (one virtual wallet per paper strategy).
    def list_accounts
      r = @http.get("/api/v1/private/sim/list-accounts")
      r.is_a?(Array) ? r : (r.is_a?(Hash) ? (r["accounts"] || []) : [])
    end

    # Virtual balance for a paper strategy (equity, realized/unrealized PnL, free/used).
    # @param strategy_id [String]
    # @param asset [String, nil]
    def balance(strategy_id:, asset: nil)
      @http.get("/api/v1/private/sim/balance",
        "strategy_id" => strategy_id, "asset" => asset
      )
    end

    # Open paper positions for a strategy.
    def positions(strategy_id:)
      r = @http.get("/api/v1/private/sim/positions", "strategy_id" => strategy_id)
      r.is_a?(Array) ? r : (r.is_a?(Hash) ? (r["positions"] || []) : [])
    end

    # Resting paper orders for a strategy.
    def open_orders(strategy_id:)
      r = @http.get("/api/v1/private/sim/open-orders", "strategy_id" => strategy_id)
      r.is_a?(Array) ? r : (r.is_a?(Hash) ? (r["orders"] || []) : [])
    end

    # Filled paper trades for a strategy.
    def my_trades(strategy_id:)
      r = @http.get("/api/v1/private/sim/my-trades", "strategy_id" => strategy_id)
      r.is_a?(Array) ? r : (r.is_a?(Hash) ? (r["trades"] || []) : [])
    end

    # Place a paper order. Fills synthesise from the live ticker; nothing hits the venue.
    #
    # @param strategy_id [String]
    # @param exchange [String]
    # @param symbol [String]
    # @param side [String] "buy" or "sell"
    # @param amount [Numeric]
    # @param type [String] "market" or "limit" (default "market")
    # @param price [Numeric, nil] required for limit orders
    # @param market [String, nil]
    # @param leverage [Numeric, nil]
    # @param reduce_only [Boolean, nil]
    # @param sl_price [Numeric, nil]
    # @param tp_price [Numeric, nil]
    # @param client_order_id [String, nil]
    # @param params [Hash, nil]
    def create_order(strategy_id:, exchange:, symbol:, side:, amount:,
                     type: "market", price: nil, market: nil, leverage: nil,
                     reduce_only: nil, sl_price: nil, tp_price: nil,
                     client_order_id: nil, params: nil)
      body = {
        "strategy_id"      => strategy_id,
        "exchange"         => exchange,
        "symbol"           => symbol,
        "side"             => side,
        "amount"           => amount,
        "order_type"       => type,
        "orderType"        => type,
        "price"            => price,
        "market"           => market,
        "market_type"      => market,
        "leverage"         => leverage,
        "reduceOnly"       => reduce_only,
        "slPrice"          => sl_price,
        "tpPrice"          => tp_price,
        "client_order_id"  => client_order_id,
        "clientOrderId"    => client_order_id,
        "params"           => params,
      }.reject { |_, v| v.nil? }
      @http.post("/api/v1/private/sim/create-order", body)
    end

    # Cancel a resting paper order.
    #
    # @param strategy_id [String]
    # @param order_id [String]
    # @param symbol [String, nil]
    # @param exchange [String, nil]
    def cancel_order(strategy_id:, order_id:, symbol: nil, exchange: nil)
      body = {
        "strategy_id" => strategy_id,
        "order_id"    => order_id,
        "orderId"     => order_id,
        "symbol"      => symbol,
        "exchange"    => exchange,
      }.reject { |_, v| v.nil? }
      @http.post("/api/v1/private/sim/cancel-order", body)
    end
  end
end
