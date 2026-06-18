# frozen_string_literal: true

module Melaya
  # Live trading API — credentialed order placement, account state, and
  # position management on a CONNECTED exchange.
  #
  # Every method POSTs to +https://api.melaya.org/api/v1/private/<op>+; the server
  # resolves your connected exchange credential (referenced by +api_key_id+ — see
  # AccountAPI#keys) and forwards the call to the venue through Melaya's in-house
  # Rust engine. Responses share an envelope:
  # +{ ok, exchange, operation, orderId, clientOrderId, payload, data, ... }+.
  #
  # WARNING: these hit the REAL venue with REAL funds. The write methods
  # (create_order, cancel_order, amend_order, cancel_all_orders, cancel_plan_orders,
  # close_position, set_leverage, set_margin_mode, set_position_mode) move money or
  # change account state. For risk-free testing use the sim (paper) broker or a
  # paper strategy instead.
  class TradeAPI
    def initialize(http)
      @http = http
    end

    # ── Account state (reads) ──────────────────────────────────────────────────

    # Live account balance on a connected venue.
    def balance(exchange:, api_key_id: nil, key_id: nil, market_type: nil, params: nil)
      op("balance", exchange: exchange, apiKeyId: api_key_id, keyId: key_id,
         marketType: market_type, params: params)
    end

    # Live open positions.
    def positions(exchange:, api_key_id: nil, market_type: nil, symbol: nil, params: nil)
      op("positions", exchange: exchange, apiKeyId: api_key_id,
         marketType: market_type, symbol: symbol, params: params)
    end

    # Historical positions (venue-dependent).
    def positions_history(exchange:, api_key_id: nil, market_type: nil, symbol: nil)
      op("positions-history", exchange: exchange, apiKeyId: api_key_id,
         marketType: market_type, symbol: symbol)
    end

    # Resting (open) orders.
    def open_orders(exchange:, api_key_id: nil, market_type: nil, symbol: nil)
      op("open-orders", exchange: exchange, apiKeyId: api_key_id,
         marketType: market_type, symbol: symbol)
    end

    # All orders (open + recent).
    def orders(exchange:, api_key_id: nil, market_type: nil, symbol: nil)
      op("orders", exchange: exchange, apiKeyId: api_key_id,
         marketType: market_type, symbol: symbol)
    end

    # Closed/filled orders.
    def closed_orders(exchange:, api_key_id: nil, market_type: nil, symbol: nil)
      op("closed-orders", exchange: exchange, apiKeyId: api_key_id,
         marketType: market_type, symbol: symbol)
    end

    # Your trade (fill) history.
    def my_trades(exchange:, api_key_id: nil, market_type: nil, symbol: nil)
      op("my-trades", exchange: exchange, apiKeyId: api_key_id,
         marketType: market_type, symbol: symbol)
    end

    # Extended trade history (venue-dependent).
    def my_trades_history(exchange:, api_key_id: nil, market_type: nil, symbol: nil)
      op("my-trades-history", exchange: exchange, apiKeyId: api_key_id,
         marketType: market_type, symbol: symbol)
    end

    # Resting conditional/plan (trigger) orders.
    def plan_orders(exchange:, api_key_id: nil, market_type: nil, symbol: nil)
      op("plan-orders", exchange: exchange, apiKeyId: api_key_id,
         marketType: market_type, symbol: symbol)
    end

    # Current leverage for a symbol.
    def leverage(exchange:, api_key_id: nil, symbol: nil, market_type: nil)
      op("leverage", exchange: exchange, apiKeyId: api_key_id,
         symbol: symbol, marketType: market_type)
    end

    # Leverage tiers / brackets for a symbol.
    def leverage_tiers(exchange:, api_key_id: nil, symbol: nil, market_type: nil)
      op("leverage-tiers", exchange: exchange, apiKeyId: api_key_id,
         symbol: symbol, marketType: market_type)
    end

    # ── Order placement & management (LIVE writes — real funds) ───────────────

    # Place a live order on the venue. WARNING: real money.
    # stop_price, take_profit_price, and reduce_only are folded into +params+.
    def create_order(exchange:, symbol:, side:, amount:,
                     api_key_id: nil, type: "market", price: nil,
                     market_type: nil, stop_price: nil, take_profit_price: nil,
                     reduce_only: nil, leverage: nil, client_order_id: nil, params: nil)
      p = (params || {}).dup
      p["stopPrice"]       = stop_price       unless stop_price.nil?
      p["takeProfitPrice"] = take_profit_price unless take_profit_price.nil?
      p["reduceOnly"]      = reduce_only       unless reduce_only.nil?
      op("create-order",
         exchange: exchange, apiKeyId: api_key_id, symbol: symbol,
         side: side, amount: amount, type: type, price: price,
         marketType: market_type, leverage: leverage,
         clientOrderId: client_order_id, params: p.empty? ? nil : p)
    end

    # Cancel a live order by id. WARNING.
    def cancel_order(exchange:, api_key_id: nil, order_id: nil, client_order_id: nil,
                     symbol: nil, market_type: nil)
      op("cancel-order", exchange: exchange, apiKeyId: api_key_id,
         orderId: order_id, clientOrderId: client_order_id,
         symbol: symbol, marketType: market_type)
    end

    # Amend (modify) a live order. WARNING.
    def amend_order(exchange:, api_key_id: nil, order_id: nil, symbol: nil,
                    amount: nil, price: nil)
      op("amend-order", exchange: exchange, apiKeyId: api_key_id,
         orderId: order_id, symbol: symbol, amount: amount, price: price)
    end

    # Cancel every open order (optionally scoped to a symbol). WARNING.
    def cancel_all_orders(exchange:, api_key_id: nil, symbol: nil, market_type: nil)
      op("cancel-all-orders", exchange: exchange, apiKeyId: api_key_id,
         symbol: symbol, marketType: market_type)
    end

    # Cancel resting plan/trigger orders. WARNING.
    def cancel_plan_orders(exchange:, api_key_id: nil, symbol: nil, market_type: nil)
      op("cancel-plan-orders", exchange: exchange, apiKeyId: api_key_id,
         symbol: symbol, marketType: market_type)
    end

    # Close an open position (market reduce-only). WARNING.
    def close_position(exchange:, symbol:, api_key_id: nil, market_type: nil)
      op("close-position", exchange: exchange, apiKeyId: api_key_id,
         symbol: symbol, marketType: market_type)
    end

    # Set leverage for a symbol. WARNING.
    def set_leverage(exchange:, symbol:, leverage:, api_key_id: nil, market_type: nil)
      op("set-leverage", exchange: exchange, apiKeyId: api_key_id,
         symbol: symbol, leverage: leverage, marketType: market_type)
    end

    # Set margin mode (cross/isolated). WARNING.
    def set_margin_mode(exchange:, margin_mode:, api_key_id: nil, symbol: nil, market_type: nil)
      op("set-margin-mode", exchange: exchange, apiKeyId: api_key_id,
         marginMode: margin_mode, symbol: symbol, marketType: market_type)
    end

    # Set position mode (one-way / hedge). WARNING.
    def set_position_mode(exchange:, api_key_id: nil, hedged: nil, mode: nil, market_type: nil)
      op("set-position-mode", exchange: exchange, apiKeyId: api_key_id,
         hedged: hedged, mode: mode, marketType: market_type)
    end

    private

    def op(path_op, **kwargs)
      body = kwargs.reject { |_, v| v.nil? }
      @http.post("/api/v1/private/#{path_op}", body)
    end
  end
end
