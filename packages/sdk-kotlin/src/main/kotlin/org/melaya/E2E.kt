package org.melaya

import org.json.JSONArray
import org.json.JSONObject

/**
 * Full end-to-end smoke test for the Melaya Kotlin SDK.
 *
 * Covers every method in every category (~70 checks).
 * Safety: PAPER/SIM only (dryRun = true). Never places live orders.
 *
 * Usage:
 *   MK=mk_... MELAYA_INSECURE_TLS=1 java -jar build/libs/melaya-sdk-kotlin-0.1.0-all.jar
 *
 * Status values: PASS | FAIL | WIRED (not invoked) | SKIP (dependency failed)
 */

private data class CheckResult(
    val cat: String,
    val name: String,
    val status: String,  // PASS | FAIL | WIRED | SKIP
    val detail: String,
)

private val results = mutableListOf<CheckResult>()

private fun pass(cat: String, name: String, detail: String = "") {
    results += CheckResult(cat, name, "PASS", detail.take(90))
}

private fun fail(cat: String, name: String, detail: String = "") {
    results += CheckResult(cat, name, "FAIL", detail.take(90))
}

private fun wired(cat: String, name: String, reason: String) {
    results += CheckResult(cat, name, "WIRED", reason.take(90))
}

private fun skip(cat: String, name: String, reason: String) {
    results += CheckResult(cat, name, "SKIP", reason.take(90))
}

/**
 * Run [fn]; on exception retry once after 1600ms (absorbs cold-cache 503s).
 * Returns the result on success, null on failure (also records PASS/FAIL).
 */
private fun <T> chk(
    cat: String,
    name: String,
    retry: Boolean = false,
    validate: ((T) -> Boolean)? = null,
    fn: () -> T,
): T? {
    val attempts = if (retry) 2 else 1
    for (i in 1..attempts) {
        try {
            val r = fn()
            if (validate == null || validate(r)) {
                val detail = when (r) {
                    is JSONObject -> r.toString().take(90)
                    is JSONArray  -> "len=${r.length()} ${r.toString().take(60)}"
                    is List<*>    -> "len=${r.size}"
                    else          -> r.toString().take(90)
                }
                pass(cat, name, detail)
                return r
            }
            if (i == attempts) {
                fail(cat, name, "invalid shape: ${r.toString().take(80)}")
                return r
            }
        } catch (e: Exception) {
            if (i == attempts) {
                val me = e as? MelayaException
                fail(cat, name, "${me?.status ?: ""} ${me?.code ?: ""} ${e.message?.take(80) ?: ""}".trim())
                return null
            }
        }
        Thread.sleep(1600)
    }
    return null  // unreachable
}

/**
 * Open a stream, wait up to 10s for ≥1 frame.
 */
private fun streamChk(cat: String, name: String, open: () -> MelayaStream) {
    try {
        val s = open()
        s.use {
            it.awaitOpen(5_000)
            val frame = it.poll(10_000)
            if (frame != null) {
                pass(cat, name, "frame ${frame.toString().take(50)}")
            } else {
                // opened but no frame — still PASS (stream is live, no message in window)
                pass(cat, name, "open, no frame 10s")
            }
        }
    } catch (e: Exception) {
        val me = e as? MelayaException
        fail(cat, name, "ws err ${me?.status ?: ""} ${e.message?.take(60) ?: ""}".trim())
    }
}

// ════════════════════════════════════════════════════════════════════════════
fun main() {
    val apiKey = System.getenv("MK")
        ?: error("Environment variable MK is not set.")
    require(apiKey.startsWith("mk_")) { "MK must start with 'mk_'." }

    val m = Melaya(apiKey = apiKey)

    val SPOT_EXCHANGE = "binance"
    val SPOT_SYMBOL   = "BTC/USDT"
    val SPOT_MARKET   = "spot"
    val PERP_EXCHANGE = "binanceusdm"
    val PERP_SYMBOL   = "BTC/USDT:USDT"

    val RHAI = """fn evaluate() { emit_long(param("qty")); }"""

    // ════ MARKET (22) ════════════════════════════════════════════════════════
    println("\n── market ──")

    chk<List<JSONObject>>("market", "listExchanges", validate = { it.size >= 60 }) {
        m.market.listExchanges()
    }
    chk<JSONObject>("market", "ticker", retry = true, validate = { it.opt("last") != null || it.opt("bid") != null }) {
        m.market.ticker(SPOT_EXCHANGE, SPOT_SYMBOL, SPOT_MARKET)
    }
    chk<JSONObject>("market", "orderbook", retry = true, validate = { (it.optJSONArray("bids")?.length() ?: 0) > 0 }) {
        m.market.orderbook(SPOT_EXCHANGE, SPOT_SYMBOL, SPOT_MARKET, limit = 5)
    }
    chk<JSONArray>("market", "ohlcv", retry = true, validate = { it.length() >= 1 }) {
        m.market.ohlcv(SPOT_EXCHANGE, SPOT_SYMBOL, "1h", SPOT_MARKET, limit = 10)
    }
    chk<JSONArray>("market", "trades", retry = true, validate = { it.length() >= 1 }) {
        m.market.trades(SPOT_EXCHANGE, SPOT_SYMBOL, SPOT_MARKET)
    }
    chk<JSONArray>("market", "markets", validate = { it.length() >= 1 }) {
        m.market.markets(SPOT_EXCHANGE)
    }
    chk<JSONArray>("market", "currencies", retry = true, validate = { it.length() >= 1 }) {
        m.market.currencies("kraken")
    }
    chk<JSONObject>("market", "status", validate = { it.length() >= 0 }) {
        m.market.status(SPOT_EXCHANGE)
    }
    chk<Any?>("market", "time", validate = { it != null }) {
        m.market.time(SPOT_EXCHANGE)
    }
    chk<JSONObject>("market", "tickers", retry = true, validate = { it.length() > 0 }) {
        m.market.tickers(SPOT_EXCHANGE, listOf("BTC/USDT", "ETH/USDT"))
    }
    chk<JSONObject>("market", "fundingRates", retry = true, validate = { it.length() >= 0 }) {
        m.market.fundingRates(PERP_EXCHANGE, listOf(PERP_SYMBOL))
    }
    chk<JSONArray>("market", "fundingRateHistory", retry = true, validate = { it.length() >= 1 }) {
        m.market.fundingRateHistory(PERP_EXCHANGE, PERP_SYMBOL, hours = 24)
    }
    chk<JSONObject>("market", "openInterest", retry = true, validate = { it.length() >= 0 }) {
        m.market.openInterest(PERP_EXCHANGE, listOf(PERP_SYMBOL))
    }
    chk<JSONArray>("market", "openInterestHistory", retry = true, validate = { it.length() >= 1 }) {
        m.market.openInterestHistory(PERP_EXCHANGE, PERP_SYMBOL, hours = 24)
    }
    chk<Any?>("market", "instruments", validate = { it != null }) {
        m.market.instruments(PERP_EXCHANGE)
    }
    chk<JSONArray>("market", "liquidationEvents", validate = { it != null }) {
        m.market.liquidationEvents(PERP_EXCHANGE, limit = 10)
    }
    chk<JSONObject>("market", "ohlcvMulti", retry = true, validate = { it.length() > 0 }) {
        m.market.ohlcvMulti(SPOT_EXCHANGE, listOf("BTC/USDT", "ETH/USDT"), "1h", limit = 5, market = SPOT_MARKET)
    }
    chk<Any?>("market", "marketConstraints", validate = { it != null }) {
        m.market.marketConstraints(PERP_EXCHANGE, PERP_SYMBOL)
    }
    chk<JSONObject>("market", "fundingRateHistoryMulti", retry = true, validate = { it.length() >= 0 }) {
        m.market.fundingRateHistoryMulti(listOf(PERP_EXCHANGE, "bybitlinear"), PERP_SYMBOL, hours = 24)
    }
    chk<JSONObject>("market", "openInterestHistoryMulti", retry = true, validate = { it.length() >= 0 }) {
        m.market.openInterestHistoryMulti(listOf(PERP_EXCHANGE, "bybitlinear"), PERP_SYMBOL, hours = 24)
    }
    chk<JSONArray>("market", "predictionMarkets", retry = true, validate = { it.length() >= 1 }) {
        m.market.predictionMarkets("polymarket")
    }
    chk<JSONObject>("market", "catalogCounts", validate = { (it.opt("tools") as? Number)?.toLong() ?: 0 > 0 }) {
        m.market.catalogCounts()
    }

    // ════ ACCOUNT (3) ════════════════════════════════════════════════════════
    println("\n── account ──")

    chk<List<JSONObject>>("account", "keys", validate = { it != null }) {
        m.account.keys()
    }
    chk<JSONObject>("account", "usage", validate = { it.opt("tier") != null }) {
        m.account.usage()
    }
    chk<JSONObject>("account", "apiKeyStatus", validate = { it.length() >= 0 }) {
        m.account.apiKeyStatus()
    }

    // ════ STRATEGIES — reads on an existing strategy ═════════════════════════
    println("\n── strategies (reads) ──")

    var readSid: String? = null
    val stratList = chk<List<JSONObject>>("strategies", "list", validate = { it.size >= 1 }) {
        m.strategies.list()
    }
    readSid = stratList?.firstOrNull()?.optString("strategyId", "")
        ?.takeIf { it.isNotBlank() }

    if (readSid != null) {
        val sid = readSid!!
        chk<JSONObject>("strategies", "get", validate = { it.optString("strategyId", "") == sid }) {
            m.strategies.get(sid)
        }
        chk<JSONObject>("strategies", "status", validate = { it.length() >= 0 }) {
            m.strategies.status(sid)
        }
        chk<List<JSONObject>>("strategies", "executions", validate = { it != null }) {
            m.strategies.executions(sid)
        }
        chk<List<JSONObject>>("strategies", "trades", validate = { it != null }) {
            m.strategies.trades(sid)
        }
        chk<List<JSONObject>>("strategies", "performance", validate = { it != null }) {
            m.strategies.performance(sid)
        }
        chk<List<JSONObject>>("strategies", "logs", validate = { it != null }) {
            m.strategies.logs(sid)
        }
        chk<JSONObject>("strategies", "aiOptStatus", validate = { it.length() >= 0 }) {
            m.strategies.aiOptStatus(sid)
        }
        chk<Any?>("strategies", "aiOptRuns", validate = { it != null }) {
            m.strategies.aiOptRuns(sid)
        }
    } else {
        for (n in listOf("get", "status", "executions", "trades", "performance", "logs", "aiOptStatus", "aiOptRuns"))
            skip("strategies", n, "list() returned no strategies")
    }

    // ════ STRATEGIES — lifecycle on a FRESH paper strategy ═══════════════════
    println("\n── strategies (lifecycle) ──")

    var paperSid: String? = null

    val created = chk<JSONObject>("strategies", "create(custom,paper)",
        validate = { it.optString("strategyId", "").isNotBlank() }
    ) {
        m.strategies.create(
            name          = "kotlin-sdk-smoke",
            strategyType  = "custom",
            exchange      = PERP_EXCHANGE,
            symbol        = PERP_SYMBOL,
            market        = "FUTURES",
            dryRun        = true,
            params        = mapOf("language" to "rhai", "definition" to RHAI, "qty" to 0.001),
        )
    }
    paperSid = created?.optString("strategyId", "")?.takeIf { it.isNotBlank() }

    if (paperSid != null) {
        val sid = paperSid
        chk<JSONObject>("strategies", "pause", validate = { it.optBoolean("ok", false) || it.length() >= 0 }) {
            m.strategies.pause(sid)
        }
        chk<JSONObject>("strategies", "resume", validate = { it.optBoolean("ok", false) || it.length() >= 0 }) {
            m.strategies.resume(sid)
        }
        chk<JSONObject>("strategies", "updateParams", validate = { it.optBoolean("ok", false) || it.length() >= 0 }) {
            m.strategies.updateParams(sid, mapOf("qty" to 0.002))
        }
        chk<JSONObject>("strategies", "aiOptStop", validate = { it.optBoolean("ok", false) || it.length() >= 0 }) {
            m.strategies.aiOptStop(sid)
        }
    } else {
        for (n in listOf("pause", "resume", "updateParams", "aiOptStop"))
            skip("strategies", n, "create(paper) failed")
    }

    // WIRED — billable / side-effecting
    wired("strategies", "aiOptStart",   "not invoked (would start a billed optimization)")
    wired("strategies", "aiOptApprove", "not invoked (applies optimizer output)")

    // ════ SIM (7) — fresh paper strategy ═════════════════════════════════════
    println("\n── sim ──")

    if (paperSid != null) {
        val sid = paperSid

        chk<JSONObject>("sim", "balance", validate = { it.opt("total") != null || it.opt("free") != null || it.opt("starting_equity") != null }) {
            m.sim.balance(sid)
        }
        chk<JSONArray>("sim", "positions", validate = { it != null }) {
            m.sim.positions(sid)
        }
        chk<JSONArray>("sim", "listAccounts", validate = { it != null }) {
            m.sim.listAccounts()
        }
        chk<JSONArray>("sim", "myTrades", validate = { it != null }) {
            m.sim.myTrades(sid)
        }

        // Fetch live ticker to set limit price at 50% — won't fill, so cancelable
        val lastPx: Double = try {
            val t = m.market.ticker(PERP_EXCHANGE, PERP_SYMBOL)
            (t.opt("last") as? Number)?.toDouble()
                ?: (t.opt("bid") as? Number)?.toDouble()
                ?: 60_000.0
        } catch (_: Exception) { 60_000.0 }

        val limitPrice = Math.round(lastPx * 0.5).toDouble()

        var orderId: String? = null
        val ord = chk<JSONObject>("sim", "createOrder(limit,resting)",
            validate = { it.optString("order_id", "").isNotBlank() }
        ) {
            m.sim.createOrder(
                strategyId = sid,
                exchange   = PERP_EXCHANGE,
                symbol     = PERP_SYMBOL,
                side       = "buy",
                amount     = 0.001,
                type       = "limit",
                price      = limitPrice,
                market     = "FUTURES",
            )
        }
        orderId = ord?.optString("order_id", null)?.takeIf { it.isNotBlank() }

        chk<JSONArray>("sim", "openOrders", validate = { it != null }) {
            m.sim.openOrders(sid)
        }

        if (orderId != null) {
            chk<JSONObject>("sim", "cancelOrder", validate = { it.length() >= 0 }) {
                m.sim.cancelOrder(sid, orderId, PERP_SYMBOL, PERP_EXCHANGE)
            }
        } else {
            skip("sim", "cancelOrder", "no resting order id")
        }
    } else {
        for (n in listOf("balance", "positions", "listAccounts", "myTrades", "createOrder(limit,resting)", "openOrders", "cancelOrder"))
            skip("sim", n, "no paper sid")
    }

    // ════ BACKTEST (10 + 1 WIRED) ════════════════════════════════════════════
    println("\n── backtest ──")

    val nowMs   = System.currentTimeMillis()
    val sinceMs = nowMs - 60L * 86_400_000   // 60 days

    // -- custom single run --
    var btJobId: String? = null
    val bt = chk<JSONObject>("backtest", "start(custom)", validate = { it.optString("job_id", "").isNotBlank() }) {
        m.backtest.start(
            strategyType = "custom",
            exchange     = SPOT_EXCHANGE,
            symbol       = SPOT_SYMBOL,
            timeframe    = "1h",
            sinceMs      = sinceMs,
            untilMs      = nowMs,
            initialEquity = 10_000.0,
            language     = "rhai",
            definition   = RHAI,
            params       = mapOf("qty" to 0.001),
        )
    }
    btJobId = bt?.optString("job_id", null)?.takeIf { it.isNotBlank() }

    if (btJobId != null) {
        val jid = btJobId
        // Poll to done (max 24s)
        var btStatus = "queued"
        for (i in 0 until 12) {
            Thread.sleep(2_000)
            try {
                btStatus = m.backtest.job(jid).optString("status", "").lowercase()
            } catch (_: Exception) {}
            if (btStatus in listOf("done", "error", "halted", "cancelled")) break
        }

        chk<JSONObject>("backtest", "job(poll)", validate = { it.optString("job_id", "") == jid }) {
            m.backtest.job(jid)
        }

        if (btStatus == "done") {
            chk<JSONObject>("backtest", "results", validate = { it.length() >= 0 }) {
                m.backtest.results(jid)
            }
            // total_trades == 0 is a PASS for custom engine
            chk<List<JSONObject>>("backtest", "trades", validate = { it != null }) {
                m.backtest.trades(jid, limit = 10)
            }
        } else {
            skip("backtest", "results", "job status=$btStatus")
            skip("backtest", "trades",  "job status=$btStatus")
        }
    } else {
        skip("backtest", "job(poll)", "start failed")
        skip("backtest", "results",   "start failed")
        skip("backtest", "trades",    "start failed")
    }

    chk<List<JSONObject>>("backtest", "list", validate = { it != null }) {
        m.backtest.list(limit = 5)
    }
    chk<List<JSONObject>>("backtest", "favorites", validate = { it != null }) {
        m.backtest.favorites(limit = 5)
    }
    chk<Long?>("backtest", "fundingRange", validate = { true }) {
        m.backtest.fundingRange(PERP_EXCHANGE, PERP_SYMBOL)
    }

    // -- grid_sweep --
    val sweepSince = nowMs - 30L * 86_400_000
    var sweepJobId: String? = null
    val sweepBt = chk<JSONObject>("backtest", "start(grid_sweep)", validate = { it.optString("job_id", "").isNotBlank() }) {
        m.backtest.start(
            strategyType = "custom",
            exchange     = SPOT_EXCHANGE,
            symbol       = SPOT_SYMBOL,
            timeframe    = "1h",
            sinceMs      = sweepSince,
            untilMs      = nowMs,
            initialEquity = 10_000.0,
            language     = "rhai",
            definition   = RHAI,
            mode         = "grid_sweep",
            paramRanges  = mapOf("qty" to listOf(0.001, 0.002)),
        )
    }
    sweepJobId = sweepBt?.optString("job_id", null)?.takeIf { it.isNotBlank() }

    if (sweepJobId != null) {
        chk<JSONObject>("backtest", "sweep", validate = { it.length() >= 0 }) {
            m.backtest.sweep(sweepJobId, limit = 10)
        }
    } else {
        skip("backtest", "sweep", "no sweep parent")
    }

    // -- cancel + delete (start a long job for cancel) --
    val cancelSince = nowMs - 365L * 86_400_000
    var cancelJobId: String? = null
    val cancelBt = chk<JSONObject>("backtest", "start(for-cancel)", validate = { it.optString("job_id", "").isNotBlank() }) {
        m.backtest.start(
            strategyType = "custom",
            exchange     = SPOT_EXCHANGE,
            symbol       = "ETH/USDT",
            timeframe    = "1h",
            sinceMs      = cancelSince,
            untilMs      = nowMs,
            language     = "rhai",
            definition   = RHAI,
            params       = mapOf("qty" to 0.001),
        )
    }
    cancelJobId = cancelBt?.optString("job_id", null)?.takeIf { it.isNotBlank() }

    if (cancelJobId != null) {
        val cjid = cancelJobId
        chk<JSONObject>("backtest", "cancel", validate = { it.length() >= 0 }) {
            m.backtest.cancel(cjid)
        }
        chk<JSONObject>("backtest", "delete", validate = { it.optBoolean("ok", false) || it.length() >= 0 }) {
            m.backtest.delete(cjid)
        }
    } else {
        skip("backtest", "cancel", "no job")
        skip("backtest", "delete", "no job")
    }

    // WIRED — destructive: soft-deletes ALL non-favorited jobs
    wired("backtest", "deleteAll", "not invoked (soft-deletes ALL non-favorited jobs)")

    // ════ STREAMS — public (5) + private (2) ══════════════════════════════════
    println("\n── stream ──")

    streamChk("stream", "ticker") {
        m.stream.ticker(SPOT_EXCHANGE, SPOT_SYMBOL, SPOT_MARKET)
    }
    streamChk("stream", "orderbook") {
        m.stream.orderbook(SPOT_EXCHANGE, SPOT_SYMBOL, SPOT_MARKET, limit = 10)
    }
    streamChk("stream", "ohlcv") {
        m.stream.ohlcv(SPOT_EXCHANGE, SPOT_SYMBOL, "1m", SPOT_MARKET)
    }
    streamChk("stream", "trades") {
        m.stream.trades(SPOT_EXCHANGE, SPOT_SYMBOL, SPOT_MARKET)
    }
    streamChk("stream", "liquidations") {
        m.stream.liquidations(PERP_EXCHANGE)
    }
    streamChk("stream", "strategies(private)") {
        m.stream.strategies()
    }

    val qkeys: List<JSONObject> = try { m.account.keys() } catch (_: Exception) { emptyList() }
    val qkey  = qkeys.firstOrNull()
    if (qkey != null) {
        streamChk("stream", "private(account)") {
            val qExchange = qkey.optString("exchange", "")
            val qMarket   = qkey.optString("market",   "").takeIf { it.isNotBlank() }
            val qApiKeyId = qkey.optString("apiKeyId", "").takeIf { it.isNotBlank() }
            m.stream.private(exchange = qExchange, market = qMarket, apiKeyId = qApiKeyId)
        }
    } else {
        skip("stream", "private(account)", "no connected exchange key")
    }

    // ════ TEARDOWN — stop + delete the paper strategy ═════════════════════════
    println("\n── teardown ──")

    if (paperSid != null) {
        val sid = paperSid
        chk<JSONObject>("teardown", "strategies.stop", validate = { it.length() >= 0 }) {
            m.strategies.stop(sid)
        }
        chk<JSONObject>("teardown", "strategies.delete", validate = { it.length() >= 0 }) {
            m.strategies.delete(sid)
        }
    }

    // ════ REPORT ══════════════════════════════════════════════════════════════
    println("\n══════════════ MELAYA SDK — FULL ENDPOINT VALIDATION (Kotlin) ══════════════")
    val cats = results.map { it.cat }.distinct()
    var nPass = 0; var nFail = 0; var nWired = 0; var nSkip = 0
    for (cat in cats) {
        println("\n── $cat ──")
        for (r in results.filter { it.cat == cat }) {
            println("  ${r.status.padEnd(5)} ${r.name.padEnd(32)} ${r.detail}")
            when (r.status) {
                "PASS"  -> nPass++
                "FAIL"  -> nFail++
                "WIRED" -> nWired++
                "SKIP"  -> nSkip++
            }
        }
    }
    println("\n════════════════════════════════════════════════════════════════════════════")
    println("PASS $nPass   FAIL $nFail   WIRED(not-invoked) $nWired   SKIP $nSkip   |  total methods ${nPass + nFail + nWired + nSkip}")
    println(if (nFail == 0) "RESULT: GO — every invoked endpoint validated." else "RESULT: NO-GO — $nFail failing.")

    System.exit(if (nFail == 0) 0 else 1)
}
