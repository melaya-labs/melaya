// Melaya Kotlin SDK -- quickstart / smoke test.
//
//   (add org.melaya:melaya-sdk-kotlin to your build)
//   MELAYA_API_KEY=mk_... kotlin examples/kotlin.kt
import org.melaya.Melaya

fun main() {
    val apiKey = System.getenv("MELAYA_API_KEY") ?: error("Set MELAYA_API_KEY=mk_...")
    val m = Melaya(apiKey = apiKey)

    // 1. How many venues are live?
    println("exchanges: ${m.market.listExchanges().size}")

    // 2. Normalized REST ticker
    val t = m.market.ticker("binance", "BTC/USDT", "spot")
    println("BTC/USDT last=${t.opt("last")} bid=${t.opt("bid")} ask=${t.opt("ask")}")

    // 3. Order book
    val book = m.market.orderbook("bybit", "BTC/USDT", "spot", limit = 5)
    println("top bid: ${book.getJSONArray("bids").get(0)}  top ask: ${book.getJSONArray("asks").get(0)}")

    // 4. Live stream -- print up to 3 ticker frames then stop
    m.stream.ticker("binance", "BTC/USDT", "spot").use { s ->
        s.awaitOpen(5_000)
        repeat(3) {
            val frame = s.poll(10_000) ?: return@repeat
            println("stream: ${frame.opt("last")}")
        }
    }

    // 5. Account -- connected keys + tier usage
    println("connected keys: ${m.account.keys().size}")
    println("tier: ${m.account.usage().opt("tier")}")

    // 6. Paper trading -- launch a paper strategy (no exchange key needed) and
    //    round-trip a synthetic order through the sim broker. Nothing hits a venue.
    val created = m.strategies.create(
        name = "SDK example (paper)",
        strategyType = "custom",                 // custom Rhai definition
        exchange = "binanceusdm", symbol = "BTC/USDT:USDT", market = "FUTURES",
        dryRun = true,                            // dryRun:false + apiKeyId => REAL orders
        params = mapOf("language" to "rhai", "definition" to "fn evaluate() { emit_long(param("qty")); }", "qty" to 0.001),
    )
    val sid = created.getString("strategyId")
    println("launched paper strategy $sid")
    val fill = m.sim.createOrder(sid, "binanceusdm", "BTC/USDT:USDT", "buy", 0.001,
        type = "market", market = "FUTURES")
    println("paper fill @ ${fill.opt("fill_price")}")
    println("paper balance: ${m.sim.balance(sid)}")
    m.strategies.stop(sid)

    // 7. Backtest on the Rust engine
    val bt = m.backtest.start(
        strategyType = "custom", exchange = "binance", symbol = "BTC/USDT", timeframe = "1h",
        language = "rhai", definition = "fn evaluate() { emit_long(param("qty")); }", params = mapOf("qty" to 0.001),
    )
    println("backtest job ${bt.opt("job_id")} started")
    println("done")
}
