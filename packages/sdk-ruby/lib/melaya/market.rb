# frozen_string_literal: true

module Melaya
  # REST market-data API — normalized across all 70+ venues.
  #
  # Every method maps to a documented endpoint under
  # https://api.melaya.org/api/v1/market/*. Unwraps the inner data key from the
  # {ok, <data>} envelope.
  class MarketAPI
    def initialize(http)
      @http = http
    end

    # List the exchanges Melaya supports right now (the source of truth).
    def list_exchanges
      @http.get("/api/v1/market/list-exchanges")["exchanges"]
    end

    # Best bid/ask, last price, and 24h aggregates for one symbol.
    # @param exchange [String]
    # @param symbol [String]
    # @param market [String, nil] e.g. "spot", "future", "swap"
    def ticker(exchange:, symbol:, market: nil)
      @http.get("/api/v1/market/ticker",
        "exchange" => exchange, "symbol" => symbol, "market" => market
      )["ticker"]
    end

    # Order book to a given depth.
    def orderbook(exchange:, symbol:, limit: nil, market: nil)
      @http.get("/api/v1/market/orderbook",
        "exchange" => exchange, "symbol" => symbol, "limit" => limit, "market" => market
      )["orderbook"]
    end

    # OHLCV candles. Each candle is [timestamp, open, high, low, close, volume].
    def ohlcv(exchange:, symbol:, timeframe:, limit: nil, market: nil)
      @http.get("/api/v1/market/ohlcv",
        "exchange" => exchange, "symbol" => symbol, "timeframe" => timeframe,
        "limit" => limit, "market" => market
      )["candles"]
    end

    # Recent public trades.
    def trades(exchange:, symbol:, market: nil)
      @http.get("/api/v1/market/trades",
        "exchange" => exchange, "symbol" => symbol, "market" => market
      )["trades"]
    end

    # Tradable markets on a venue.
    def markets(exchange:)
      @http.get("/api/v1/market/markets", "exchange" => exchange)["markets"]
    end

    # Listed currencies on a venue. (Not supported on every venue.)
    def currencies(exchange:)
      @http.get("/api/v1/market/currencies", "exchange" => exchange)["currencies"]
    end

    # Operational status: ok / maintenance / degraded.
    def status(exchange:)
      @http.get("/api/v1/market/status", "exchange" => exchange)["status"]
    end

    # Exchange server time.
    def time(exchange:)
      @http.get("/api/v1/market/time", "exchange" => exchange)["time"]
    end

    # ── Batch / derivatives (POST) ──────────────────────────────────────────

    # Tickers for many symbols on one venue in a single call. Keyed by symbol.
    def tickers(exchange:, symbols:, market: nil)
      body = compact("exchange" => exchange, "symbols" => symbols, "market" => market)
      @http.post("/api/v1/market/tickers", body)["tickers"]
    end

    # Latest funding rates for perpetuals. Keyed by symbol.
    def funding_rates(exchange:, symbols:, market: nil)
      body = compact("exchange" => exchange, "symbols" => symbols, "market" => market)
      @http.post("/api/v1/market/funding-rates", body)["rates"]
    end

    # Funding-rate history.
    def funding_rate_history(exchange:, symbol:, hours: nil, market: nil)
      body = compact("exchange" => exchange, "symbol" => symbol, "hours" => hours, "market" => market)
      @http.post("/api/v1/market/funding-rate-history", body)["history"]
    end

    # Open interest for one or more perpetuals. Keyed by symbol.
    def open_interest(exchange:, symbols:, market: nil)
      body = compact("exchange" => exchange, "symbols" => symbols, "market" => market)
      @http.post("/api/v1/market/open-interest", body)["openInterest"]
    end

    # Open-interest history.
    def open_interest_history(exchange:, symbol:, hours: nil, market: nil)
      body = compact("exchange" => exchange, "symbol" => symbol, "hours" => hours, "market" => market)
      @http.post("/api/v1/market/open-interest-history", body)["history"]
    end

    # Instrument list + trading constraints (tick size, min notional, qty step).
    def instruments(exchange:, market: nil)
      body = compact("exchange" => exchange, "market" => market)
      @http.post("/api/v1/market/instruments", body)
    end

    # Cross-exchange liquidation events (historical query).
    def liquidation_events(exchange: nil, symbol: nil, since_ms: nil, limit: nil)
      body = compact("exchange" => exchange, "symbol" => symbol, "sinceMs" => since_ms, "limit" => limit)
      @http.post("/api/v1/market/liquidation-events", body)["events"]
    end

    # Multi-symbol OHLCV in one call. Returns candle arrays keyed by symbol.
    def ohlcv_multi(exchange:, symbols:, timeframe:, limit: nil, market: nil)
      body = compact("exchange" => exchange, "symbols" => symbols, "timeframe" => timeframe,
                     "limit" => limit, "market" => market)
      @http.post("/api/v1/market/ohlcv-multi", body)["perSymbol"]
    end

    # Trading constraints for one symbol (tick size, min notional, qty step, leverage).
    def market_constraints(exchange:, symbol:, market: nil)
      body = compact("exchange" => exchange, "symbol" => symbol, "market" => market)
      @http.post("/api/v1/market/market-constraints", body)["constraints"]
    end

    # Funding-rate history for one symbol across several venues. Keyed by exchange.
    def funding_rate_history_multi(exchanges:, symbol:, hours: nil)
      body = compact("exchanges" => exchanges, "symbol" => symbol, "hours" => hours)
      @http.post("/api/v1/market/funding-rate-history-multi", body)["perExchange"]
    end

    # Open-interest history for one symbol across several venues. Keyed by exchange.
    def open_interest_history_multi(exchanges:, symbol:, hours: nil)
      body = compact("exchanges" => exchanges, "symbol" => symbol, "hours" => hours)
      @http.post("/api/v1/market/open-interest-history-multi", body)["perExchange"]
    end

    # Prediction-market listings for a venue (polymarket, kalshi, drift_pm, sxbet, azuro, overtime).
    def prediction_markets(venue: "polymarket")
      @http.post("/api/v1/market/pm-markets", { "venue" => venue })["markets"]
    end

    # Live platform catalog counts (agentic tools, subagents, by category). Public.
    def catalog_counts
      @http.get("/api/v1/public/catalog-counts")
    end

    private

    def compact(hash)
      hash.reject { |_, v| v.nil? }
    end
  end
end
