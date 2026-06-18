#!/usr/bin/env ruby
# frozen_string_literal: true
#
# Melaya Ruby SDK — FULL end-to-end smoke test (~70 checks)
#
# Usage:
#   MK=mk_... ruby e2e/smoke.rb
#
# Reads the API key from ENV["MK"].
# TLS verification is disabled via MELAYA_INSECURE_TLS=1 for dev-box interception.
# PAPER/SIM ONLY — never places live orders, always cleans up created resources.
#
# WIRED (not invoked — intentionally skipped):
#   strategies.ai_opt_start  — billable optimization run
#   strategies.ai_opt_approve — applies optimizer output
#   backtest.delete_all       — soft-deletes ALL non-favorited jobs

$LOAD_PATH.unshift(File.join(__dir__, "../lib"))
require "melaya"
require "timeout"

ENV["MELAYA_INSECURE_TLS"] = "1"

api_key = ENV["MK"]
if api_key.nil? || api_key.empty?
  warn "ERROR: set MK=mk_... in your environment"
  exit 1
end

melaya = Melaya::Client.new(api_key: api_key)

# ── result store ─────────────────────────────────────────────────────────────
# Each entry: [category, name, status, detail]
# status: "PASS" | "FAIL" | "WIRED" | "SKIP"
RESULTS = []

def rec(cat, name, status, detail = "")
  RESULTS << [cat, name, status, detail.to_s[0, 80]]
end

def short(r)
  JSON.dump(r)[0, 80]
rescue StandardError
  r.to_s[0, 80]
end

# Run a check with optional retry on cold-cache (retry: true → sleep 1.6s + retry once).
# +validate+ is a callable that returns true/false given the result.
# Marks PASS on first truthy validate, FAIL after exhausting retries.
def chk(cat, name, validate: nil, retry_once: false)
  attempts = retry_once ? 2 : 1
  last_err  = nil
  result    = nil

  attempts.times do |i|
    sleep 1.6 if i > 0
    begin
      result = yield
      if validate.nil? || validate.call(result)
        rec(cat, name, "PASS", short(result))
        return result
      end
      last_err = "invalid shape: #{short(result)}"
    rescue Melaya::MelayaError => e
      last_err = "#{e.status} #{e.code} #{e.message}"[0, 80]
    rescue StandardError => e
      last_err = "#{e.class}: #{e.message}"[0, 80]
    end
  end

  rec(cat, name, "FAIL", last_err.to_s)
  nil
end

RHAI = 'fn evaluate() { let qty = param("qty"); if qty == () { qty = 0.001; } emit_long(qty); }'

SPOT = { exchange: "binance", symbol: "BTC/USDT", market: "spot" }
PERP = { exchange: "binanceusdm", symbol: "BTC/USDT:USDT" }

# ═══════════════════════════════════════════════════════════════════
#  1. MARKET (22)
# ═══════════════════════════════════════════════════════════════════

puts "\n-- market --"

chk("market", "list_exchanges",
    validate: ->(r) { r.is_a?(Array) && r.length >= 60 }) {
  melaya.market.list_exchanges
}

ticker_last = nil
chk("market", "ticker",
    validate: ->(r) { r.is_a?(Hash) && (r["last"] || r["bid"]) },
    retry_once: true) {
  t = melaya.market.ticker(**SPOT)
  ticker_last = t["last"].to_f if t.is_a?(Hash)
  t
}

chk("market", "orderbook",
    validate: ->(r) { r.is_a?(Hash) && r["bids"] },
    retry_once: true) {
  melaya.market.orderbook(**SPOT, limit: 5)
}

chk("market", "ohlcv",
    validate: ->(r) { r.is_a?(Array) && r.length >= 1 },
    retry_once: true) {
  melaya.market.ohlcv(**SPOT, timeframe: "1h", limit: 10)
}

chk("market", "trades",
    validate: ->(r) { r.is_a?(Array) && r.length >= 1 },
    retry_once: true) {
  melaya.market.trades(**SPOT)
}

chk("market", "markets",
    validate: ->(r) { r.is_a?(Array) && r.length >= 1 }) {
  melaya.market.markets(exchange: "binance")
}

chk("market", "currencies",
    validate: ->(r) { r.is_a?(Array) && r.length >= 1 },
    retry_once: true) {
  melaya.market.currencies(exchange: "kraken")
}

chk("market", "status",
    validate: ->(r) { r.is_a?(Hash) && r.length > 0 }) {
  melaya.market.status(exchange: "binance")
}

chk("market", "time",
    validate: ->(r) { !r.nil? }) {
  melaya.market.time(exchange: "binance")
}

chk("market", "tickers",
    validate: ->(r) { r.is_a?(Hash) && r.length > 0 },
    retry_once: true) {
  melaya.market.tickers(exchange: "binance", symbols: ["BTC/USDT", "ETH/USDT"])
}

chk("market", "funding_rates",
    validate: ->(r) { r.is_a?(Hash) && r.length > 0 },
    retry_once: true) {
  melaya.market.funding_rates(exchange: "binanceusdm", symbols: [PERP[:symbol]])
}

chk("market", "funding_rate_history",
    validate: ->(r) { r.is_a?(Array) && r.length >= 1 },
    retry_once: true) {
  melaya.market.funding_rate_history(exchange: "binanceusdm", symbol: PERP[:symbol], hours: 24)
}

chk("market", "open_interest",
    validate: ->(r) { r.is_a?(Hash) && r.length > 0 },
    retry_once: true) {
  melaya.market.open_interest(exchange: "binanceusdm", symbols: [PERP[:symbol]])
}

chk("market", "open_interest_history",
    validate: ->(r) { r.is_a?(Array) && r.length >= 1 },
    retry_once: true) {
  melaya.market.open_interest_history(exchange: "binanceusdm", symbol: PERP[:symbol], hours: 24)
}

chk("market", "instruments",
    validate: ->(r) { r.is_a?(Hash) && r.length > 0 }) {
  melaya.market.instruments(exchange: "binanceusdm")
}

chk("market", "liquidation_events",
    validate: ->(r) { r.is_a?(Array) }) {
  melaya.market.liquidation_events(exchange: "binanceusdm", limit: 10)
}

chk("market", "ohlcv_multi",
    validate: ->(r) { r.is_a?(Hash) && r.length > 0 },
    retry_once: true) {
  melaya.market.ohlcv_multi(exchange: "binance", symbols: ["BTC/USDT", "ETH/USDT"],
                             timeframe: "1h", limit: 5, market: "spot")
}

chk("market", "market_constraints",
    validate: ->(r) { !r.nil? }) {
  melaya.market.market_constraints(exchange: "binanceusdm", symbol: PERP[:symbol])
}

chk("market", "funding_rate_history_multi",
    validate: ->(r) { r.is_a?(Hash) && r.length > 0 },
    retry_once: true) {
  melaya.market.funding_rate_history_multi(
    exchanges: ["binanceusdm", "bybitlinear"], symbol: PERP[:symbol], hours: 24)
}

chk("market", "open_interest_history_multi",
    validate: ->(r) { r.is_a?(Hash) && r.length > 0 },
    retry_once: true) {
  melaya.market.open_interest_history_multi(
    exchanges: ["binanceusdm", "bybitlinear"], symbol: PERP[:symbol], hours: 24)
}

chk("market", "prediction_markets",
    validate: ->(r) { r.is_a?(Array) && r.length >= 1 },
    retry_once: true) {
  melaya.market.prediction_markets(venue: "polymarket")
}

chk("market", "catalog_counts",
    validate: ->(r) { r.is_a?(Hash) && r.fetch("tools", 0).to_i > 0 }) {
  melaya.market.catalog_counts
}

# ═══════════════════════════════════════════════════════════════════
#  2. ACCOUNT (3)
# ═══════════════════════════════════════════════════════════════════

puts "\n-- account --"

account_keys = chk("account", "keys",
                   validate: ->(r) { r.is_a?(Array) }) {
  melaya.account.keys
}

chk("account", "usage",
    validate: ->(r) { r.is_a?(Hash) && r["tier"] }) {
  melaya.account.usage
}

chk("account", "api_key_status",
    validate: ->(r) { r.is_a?(Hash) && r.length > 0 }) {
  melaya.account.api_key_status
}

# ═══════════════════════════════════════════════════════════════════
#  3. STRATEGIES — reads on existing strategy (9)
# ═══════════════════════════════════════════════════════════════════

puts "\n-- strategies --"

strategy_list = chk("strategies", "list",
                    validate: ->(r) { r.is_a?(Array) && r.length >= 1 }) {
  melaya.strategies.list
}

read_sid = strategy_list&.first&.fetch("strategyId", nil)

if read_sid
  chk("strategies", "get",
      validate: ->(r) { r.is_a?(Hash) && r["strategyId"] == read_sid }) {
    melaya.strategies.get(read_sid)
  }

  chk("strategies", "status",
      validate: ->(r) { r.is_a?(Hash) && r.length > 0 }) {
    melaya.strategies.status(read_sid)
  }

  chk("strategies", "executions",
      validate: ->(r) { r.is_a?(Array) }) {
    melaya.strategies.executions(read_sid)
  }

  chk("strategies", "trades",
      validate: ->(r) { r.is_a?(Array) }) {
    melaya.strategies.trades(read_sid)
  }

  chk("strategies", "performance",
      validate: ->(r) { r.is_a?(Array) }) {
    melaya.strategies.performance(read_sid)
  }

  chk("strategies", "logs",
      validate: ->(r) { r.is_a?(Array) }) {
    melaya.strategies.logs(read_sid)
  }

  chk("strategies", "ai_opt_status",
      validate: ->(r) { r.is_a?(Hash) && r.length > 0 }) {
    melaya.strategies.ai_opt_status(read_sid)
  }

  chk("strategies", "ai_opt_runs",
      validate: ->(r) { !r.nil? }) {
    melaya.strategies.ai_opt_runs(read_sid)
  }
else
  %w[get status executions trades performance logs ai_opt_status ai_opt_runs].each do |n|
    rec("strategies", n, "SKIP", "no existing strategy in list")
  end
end

# ═══════════════════════════════════════════════════════════════════
#  4. STRATEGIES — lifecycle on fresh custom paper strategy (5 + teardown)
# ═══════════════════════════════════════════════════════════════════

paper_sid = nil

created = chk("strategies", "create(custom,paper)",
               validate: ->(r) { r.is_a?(Hash) && r["ok"] && r["strategyId"] }) {
  melaya.strategies.create(
    name: "ruby-sdk-smoke-#{Time.now.to_i}",
    strategy_type: "custom",
    exchange: "binanceusdm",
    symbol: "BTC/USDT:USDT",
    market: "FUTURES",
    dry_run: true,
    params: { "language" => "rhai", "definition" => RHAI, "qty" => 0.001 }
  )
}
paper_sid = created["strategyId"] if created

if paper_sid
  chk("strategies", "pause",
      validate: ->(r) { r.is_a?(Hash) && r["ok"] }) {
    melaya.strategies.pause(paper_sid)
  }

  chk("strategies", "resume",
      validate: ->(r) { r.is_a?(Hash) && r["ok"] }) {
    melaya.strategies.resume(paper_sid)
  }

  chk("strategies", "update_params",
      validate: ->(r) { r.is_a?(Hash) && r["ok"] }) {
    melaya.strategies.update_params(paper_sid, { "qty" => 0.002 })
  }

  chk("strategies", "ai_opt_stop",
      validate: ->(r) { r.is_a?(Hash) && r["ok"] }) {
    melaya.strategies.ai_opt_stop(paper_sid)
  }
else
  %w[pause resume update_params ai_opt_stop].each do |n|
    rec("strategies", n, "SKIP", "create failed")
  end
end

rec("strategies", "ai_opt_start",  "WIRED", "not invoked (billable optimization)")
rec("strategies", "ai_opt_approve", "WIRED", "not invoked (applies optimizer output)")

# ═══════════════════════════════════════════════════════════════════
#  5. SIM (7) — on the fresh paper strategy
# ═══════════════════════════════════════════════════════════════════

puts "\n-- sim --"

if paper_sid
  chk("sim", "balance",
      validate: ->(r) { r.is_a?(Hash) && !r["total"].nil? }) {
    melaya.sim.balance(strategy_id: paper_sid)
  }

  chk("sim", "positions",
      validate: ->(r) { r.is_a?(Array) }) {
    melaya.sim.positions(strategy_id: paper_sid)
  }

  chk("sim", "list_accounts",
      validate: ->(r) { r.is_a?(Array) }) {
    melaya.sim.list_accounts
  }

  chk("sim", "my_trades",
      validate: ->(r) { r.is_a?(Array) }) {
    melaya.sim.my_trades(strategy_id: paper_sid)
  }

  # Fetch live price for a far-below-market resting limit
  px = 60_000.0
  begin
    t = melaya.market.ticker(**PERP)
    px = (t["last"] || t["bid"] || 60_000).to_f if t.is_a?(Hash)
  rescue StandardError
    # keep default
  end
  limit_price = (px * 0.5).round(2)

  sim_order_id = nil
  ordr = chk("sim", "create_order(limit,resting)",
             validate: ->(r) { r.is_a?(Hash) && (r["order_id"] || r["orderId"] || r["id"]) }) {
    melaya.sim.create_order(
      strategy_id: paper_sid,
      exchange:    "binanceusdm",
      symbol:      "BTC/USDT:USDT",
      side:        "buy",
      type:        "limit",
      price:       limit_price,
      amount:      0.001,
      market:      "FUTURES"
    )
  }
  if ordr
    sim_order_id = ordr["order_id"] || ordr["orderId"] || ordr["id"] ||
                   (ordr["order"].is_a?(Hash) ? (ordr["order"]["orderId"] || ordr["order"]["id"]) : nil)
  end

  open_orders = chk("sim", "open_orders",
                    validate: ->(r) { r.is_a?(Array) }) {
    melaya.sim.open_orders(strategy_id: paper_sid)
  }
  # fallback: pull id from the order book if create_order didn't surface it
  if sim_order_id.nil? && open_orders.is_a?(Array) && !open_orders.empty?
    first = open_orders.first
    sim_order_id = first["orderId"] || first["order_id"] || first["id"]
  end

  if sim_order_id
    chk("sim", "cancel_order",
        validate: ->(r) { r.is_a?(Hash) && r.length > 0 }) {
      melaya.sim.cancel_order(
        strategy_id: paper_sid,
        order_id:    sim_order_id,
        exchange:    "binanceusdm",
        symbol:      "BTC/USDT:USDT"
      )
    }
  else
    rec("sim", "cancel_order", "SKIP", "no resting order id")
  end
else
  %w[balance positions list_accounts my_trades create_order(limit,resting) open_orders cancel_order].each do |n|
    rec("sim", n, "SKIP", "no paper strategy")
  end
end

# ═══════════════════════════════════════════════════════════════════
#  6. BACKTEST (10)
# ═══════════════════════════════════════════════════════════════════

puts "\n-- backtest --"

now_ms = (Time.now.to_i * 1000)

# 6a. start(custom) → job(poll) → results → trades
bt = chk("backtest", "start(custom)",
         validate: ->(r) { r.is_a?(Hash) && r["job_id"] }) {
  melaya.backtest.start(
    "strategyType" => "custom",
    "exchange"     => "binance",
    "symbol"       => "BTC/USDT",
    "timeframe"    => "1h",
    "since_ms"     => now_ms - 60 * 86_400_000,
    "until_ms"     => now_ms,
    "initial_equity" => 10_000,
    "language"     => "rhai",
    "definition"   => RHAI,
    "custom_code"  => RHAI,
    "params"       => { "qty" => 0.001 }
  )
}
main_job_id = bt&.fetch("job_id", nil)

if main_job_id
  # Poll to done (max 120s)
  job_status = nil
  deadline   = Time.now + 120
  loop do
    sleep 2
    begin
      j = melaya.backtest.job(main_job_id)
      job_status = j["status"].to_s.downcase
    rescue StandardError
      # keep polling
    end
    break if %w[done error failed halted cancelled].include?(job_status)
    break if Time.now > deadline
  end

  chk("backtest", "job(poll)",
      validate: ->(r) { r.is_a?(Hash) && r["job_id"] == main_job_id }) {
    melaya.backtest.job(main_job_id)
  }

  if job_status == "done"
    chk("backtest", "results",
        validate: ->(r) { r.is_a?(Hash) && r.length > 0 }) {
      melaya.backtest.results(main_job_id)
    }
    # total_trades may be 0 — that is a PASS
    chk("backtest", "trades",
        validate: ->(r) { r.is_a?(Array) }) {
      melaya.backtest.trades(main_job_id, limit: 10)
    }
  else
    rec("backtest", "results", "SKIP", "job status: #{job_status}")
    rec("backtest", "trades",  "SKIP", "job status: #{job_status}")
  end
else
  %w[job(poll) results trades].each { |n| rec("backtest", n, "SKIP", "start failed") }
end

# 6b. list
chk("backtest", "list",
    validate: ->(r) { r.is_a?(Array) }) {
  melaya.backtest.list(limit: 5)
}

# 6c. favorites
chk("backtest", "favorites",
    validate: ->(r) { r.is_a?(Array) }) {
  melaya.backtest.favorites(limit: 5)
}

# 6d. funding_range
chk("backtest", "funding_range",
    validate: ->(r) { r.nil? || r.is_a?(Numeric) }) {
  melaya.backtest.funding_range(exchange: "binanceusdm", symbol: PERP[:symbol])
}

# 6e. start(grid_sweep) → sweep
sweep_bt = chk("backtest", "start(grid_sweep)",
               validate: ->(r) { r.is_a?(Hash) && r["job_id"] }) {
  melaya.backtest.start(
    "mode"         => "grid_sweep",
    "strategyType" => "custom",
    "exchange"     => "binance",
    "symbol"       => "BTC/USDT",
    "timeframe"    => "1h",
    "since_ms"     => now_ms - 30 * 86_400_000,
    "until_ms"     => now_ms,
    "language"     => "rhai",
    "definition"   => RHAI,
    "custom_code"  => RHAI,
    "paramRanges"  => { "qty" => [0.001, 0.002] }
  )
}

if sweep_bt && sweep_bt["job_id"]
  chk("backtest", "sweep",
      validate: ->(r) { r.is_a?(Hash) && r.length > 0 }) {
    melaya.backtest.sweep(sweep_bt["job_id"], limit: 10)
  }
else
  rec("backtest", "sweep", "SKIP", "no sweep parent job")
end

# 6f. start(for-cancel) → cancel → delete
cancel_bt = chk("backtest", "start(for-cancel)",
                validate: ->(r) { r.is_a?(Hash) && r["job_id"] }) {
  melaya.backtest.start(
    "strategyType" => "custom",
    "exchange"     => "binance",
    "symbol"       => "ETH/USDT",
    "timeframe"    => "1h",
    "since_ms"     => now_ms - 365 * 86_400_000,
    "until_ms"     => now_ms,
    "language"     => "rhai",
    "definition"   => RHAI,
    "custom_code"  => RHAI,
    "params"       => { "qty" => 0.001 }
  )
}

if cancel_bt && cancel_bt["job_id"]
  cancel_job_id = cancel_bt["job_id"]
  chk("backtest", "cancel",
      validate: ->(r) { r.is_a?(Hash) && r.length > 0 }) {
    melaya.backtest.cancel(cancel_job_id)
  }
  chk("backtest", "delete",
      validate: ->(r) { r.is_a?(Hash) && r["ok"] }) {
    melaya.backtest.delete(cancel_job_id)
  }
else
  rec("backtest", "cancel", "SKIP", "no cancel job")
  rec("backtest", "delete", "SKIP", "no cancel job")
end

rec("backtest", "delete_all", "WIRED", "not invoked (soft-deletes ALL non-favorited jobs)")

# ═══════════════════════════════════════════════════════════════════
#  7. STREAMS (7) — public (5) + private (2)
# ═══════════════════════════════════════════════════════════════════

puts "\n-- stream --"

def stream_check(cat, name, melaya_client, timeout_sec: 12, &opener)
  frames = 0
  handler = proc do |_frame|
    frames += 1
    throw :got_frame
  end
  begin
    catch(:got_frame) do
      Timeout.timeout(timeout_sec) do
        opener.call(melaya_client, handler)
      end
    end
    if frames >= 1
      RESULTS << [cat, name, "PASS", "received #{frames} frame(s)"]
    else
      RESULTS << [cat, name, "FAIL", "0 frames in #{timeout_sec}s"]
    end
  rescue Timeout::Error
    # A timeout after at least 1 frame → PASS (unlikely with break, but safe)
    if frames >= 1
      RESULTS << [cat, name, "PASS", "received #{frames} frame(s) (timeout)"]
    else
      RESULTS << [cat, name, "FAIL", "timeout — no frames in #{timeout_sec}s"]
    end
  rescue Melaya::MelayaError => e
    RESULTS << [cat, name, "FAIL", "MelayaError(#{e.status}): #{e.message}"[0, 70]]
  rescue StandardError => e
    RESULTS << [cat, name, "FAIL", "#{e.class}: #{e.message}"[0, 70]]
  end
end

stream_check("stream", "ticker", melaya) do |m, blk|
  m.stream.ticker(**SPOT, &blk)
end

stream_check("stream", "orderbook", melaya) do |m, blk|
  m.stream.orderbook(**SPOT, limit: 10, &blk)
end

stream_check("stream", "ohlcv", melaya) do |m, blk|
  m.stream.ohlcv(**SPOT, timeframe: "1m", &blk)
end

stream_check("stream", "trades", melaya) do |m, blk|
  m.stream.trades(**SPOT, &blk)
end

stream_check("stream", "liquidations", melaya) do |m, blk|
  m.stream.liquidations(exchange: "binanceusdm", &blk)
end

stream_check("stream", "strategies(private)", melaya) do |m, blk|
  m.stream.strategies(&blk)
end

if account_keys.is_a?(Array) && !account_keys.empty?
  k = account_keys.first
  stream_check("stream", "private(account)", melaya) do |m, blk|
    m.stream.private(
      exchange:    k["exchange"],
      market:      k["market"],
      api_key_id:  k["apiKeyId"],
      &blk
    )
  end
else
  rec("stream", "private(account)", "SKIP", "no connected exchange key")
end

# ═══════════════════════════════════════════════════════════════════
#  TEARDOWN
# ═══════════════════════════════════════════════════════════════════

puts "\n-- teardown --"

if paper_sid
  chk("teardown", "strategies.stop",
      validate: ->(r) { r.is_a?(Hash) && r["ok"] }) {
    melaya.strategies.stop(paper_sid)
  }
  chk("teardown", "strategies.delete",
      validate: ->(r) { r.is_a?(Hash) && r["ok"] }) {
    melaya.strategies.delete(paper_sid)
  }
end

begin
  melaya.close if melaya.respond_to?(:close)
rescue StandardError
  # best-effort
end

# ═══════════════════════════════════════════════════════════════════
#  REPORT
# ═══════════════════════════════════════════════════════════════════

puts "\n#{"=" * 70}"
puts "MELAYA SDK — FULL ENDPOINT VALIDATION (Ruby)"
puts "=" * 70

categories = RESULTS.map { |r| r[0] }.uniq
n_pass = n_fail = n_wired = n_skip = 0

categories.each do |cat|
  puts "\n-- #{cat} --"
  RESULTS.select { |r| r[0] == cat }.each do |_, name, status, detail|
    printf "  %-5s %-35s %s\n", status, name, detail
    case status
    when "PASS"  then n_pass  += 1
    when "FAIL"  then n_fail  += 1
    when "WIRED" then n_wired += 1
    when "SKIP"  then n_skip  += 1
    end
  end
end

total = n_pass + n_fail + n_wired + n_skip
puts "\n#{"=" * 70}"
printf "PASS %-3d  FAIL %-3d  WIRED(not-invoked) %-3d  SKIP %-3d  | total %d\n",
       n_pass, n_fail, n_wired, n_skip, total
if n_fail == 0
  puts "RESULT: GO — every invoked endpoint validated."
else
  puts "RESULT: NO-GO — #{n_fail} failing."
end
puts "=" * 70

exit(n_fail > 0 ? 1 : 0)
