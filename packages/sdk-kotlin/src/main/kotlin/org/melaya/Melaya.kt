package org.melaya

import okhttp3.OkHttpClient
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

const val DEFAULT_BASE_URL = "https://api.melaya.org"
const val DEFAULT_WS_URL   = "wss://wss.melaya.org"

/**
 * The Melaya SDK entry point.
 *
 * ```kotlin
 * val melaya = Melaya(apiKey = System.getenv("MK"))
 * val ticker = melaya.market.ticker(exchange = "binance", symbol = "BTC/USDT", market = "spot")
 * println(ticker.optDouble("last"))
 * ```
 *
 * Set `MELAYA_INSECURE_TLS=1` in the environment to disable certificate
 * verification (for corporate proxies / dev-box intercepts).  Secure by default.
 */
class Melaya @JvmOverloads constructor(
    apiKey: String,
    baseUrl: String = DEFAULT_BASE_URL,
    wsUrl: String   = DEFAULT_WS_URL,
) {
    init {
        require(apiKey.isNotBlank()) {
            "Melaya: apiKey is required (create one at melaya.org → Settings → API Keys)."
        }
        require(apiKey.startsWith("mk_")) {
            "Melaya: API keys must be prefixed 'mk_'."
        }
    }

    private val okHttp: OkHttpClient = buildOkHttpClient()

    private val http = HttpClient(apiKey, baseUrl, okHttp)

    /** REST market-data + reference endpoints (public plane). */
    val market = MarketAPI(http)

    /** Authenticated account reads: connected keys, tier limits, usage. */
    val account = AccountAPI(http)

    /** Paper trading (sim broker): virtual balance, positions, and orders. */
    val sim = SimAPI(http)

    /** Live trading (real funds): order placement, positions, balance on a connected venue. */
    val trade = TradeAPI(http)

    /** Launch, control, and inspect trading strategies (paper + live). */
    val strategies = StrategiesAPI(http)

    /** Historical backtests + parameter sweeps on the Rust engine. */
    val backtest = BacktestAPI(http)

    /** WebSocket streaming endpoints (public market data + private feeds). */
    val stream = StreamAPI(apiKey, wsUrl, http, okHttp)

    private fun buildOkHttpClient(): OkHttpClient {
        val insecure = System.getenv("MELAYA_INSECURE_TLS") == "1"
        return if (insecure) {
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslCtx = SSLContext.getInstance("TLS").also { it.init(null, trustAll, null) }
            OkHttpClient.Builder()
                .sslSocketFactory(sslCtx.socketFactory, trustAll[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        } else {
            OkHttpClient.Builder().build()
        }
    }
}
