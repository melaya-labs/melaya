# Melaya Ruby SDK -- quickstart / smoke test.
#
#   gem install melaya
#   MELAYA_API_KEY=mk_... ruby examples/ruby.rb
require "melaya"

api_key = ENV["MELAYA_API_KEY"] or abort("Set MELAYA_API_KEY=mk_...")
m = Melaya::Client.new(api_key: api_key)

# 1. How many venues are live?
puts "exchanges: #{m.market.list_exchanges.length}"

# 2. Normalized REST ticker
t = m.market.ticker(exchange: "binance", symbol: "BTC/USDT", market: "spot")
puts "BTC/USDT last=#{t['last']} bid=#{t['bid']} ask=#{t['ask']}"

# 3. Order book
book = m.market.orderbook(exchange: "bybit", symbol: "BTC/USDT", market: "spot", limit: 5)
puts "top bid: #{book['bids'][0]}  top ask: #{book['asks'][0]}"

# 4. Live stream -- print 3 ticker frames then stop
n = 0
catch(:done) do
  m.stream.ticker(exchange: "binance", symbol: "BTC/USDT", market: "spot") do |frame|
    puts "stream: #{frame['last']}"
    n += 1
    throw :done if n >= 3
  end
end

# 5. Account -- connected keys + tier usage
puts "connected keys: #{m.account.keys.length}"
puts "tier: #{m.account.usage['tier']}"

# 6. Paper trading -- launch a paper strategy (no exchange key needed) and
#    round-trip a synthetic order through the sim broker. Nothing hits a venue.
created = m.strategies.create(
  name: "SDK example (paper)",
  strategy_type: "custom",                 # custom Rhai definition
  exchange: "binanceusdm", symbol: "BTC/USDT:USDT", market: "FUTURES",
  dry_run: true,                           # dry_run:false + api_key_id => REAL orders
  params: { "language" => "rhai", "definition" => "fn evaluate() { emit_long(param("qty")); }", "qty" => 0.001 },
)
sid = created["strategyId"]
puts "launched paper strategy #{sid}"
fill = m.sim.create_order(strategy_id: sid, exchange: "binanceusdm",
                          symbol: "BTC/USDT:USDT", side: "buy", amount: 0.001,
                          type: "market", market: "FUTURES")
puts "paper fill @ #{fill['fill_price']}"
puts "paper balance: #{m.sim.balance(strategy_id: sid)}"
m.strategies.stop(sid)

# 7. Backtest on the Rust engine
bt = m.backtest.start(
  "strategyType" => "custom", "exchange" => "binance", "symbol" => "BTC/USDT", "timeframe" => "1h",
  "language" => "rhai", "definition" => "fn evaluate() { emit_long(param("qty")); }", "params" => { "qty" => 0.001 },
)
puts "backtest job #{bt['job_id']} started"
puts "done"
