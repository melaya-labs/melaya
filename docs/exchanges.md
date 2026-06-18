# Exchanges & the unified API

Melaya exposes **one normalized REST + WebSocket API over 70+ venues**, backed by an in-house Rust engine. You write your integration once against the Melaya schema; the engine handles each venue's symbol formats, rate limits, settlement suffixes, funding intervals, and connection lifecycles.

The 70+ venues break down as **60 spot exchanges**, **5 perpetual-futures venues** (binanceusdm, bingxfutures, bitgetfutures, bybitlinear, okxswap), and **6 prediction-market / DEX venues** (azuro, drift_pm, kalshi, overtime, polymarket, sxbet). A machine-readable dataset — id, display name, market type, auth requirements (passphrase / application-id), and native ticker-stream support — is published here: [`data/exchanges.json`](../data/exchanges.json) · [`data/exchanges.csv`](../data/exchanges.csv).

<p align="center">
  <img src="../assets/exchanges/ASCENDEX.png" height="30" alt="AscendEX" title="AscendEX"/>
  <img src="../assets/exchanges/AZURO.png" height="30" alt="Azuro" title="Azuro"/>
  <img src="../assets/exchanges/BACKPACK.png" height="30" alt="Backpack" title="Backpack"/>
  <img src="../assets/exchanges/BEQUANT.png" height="30" alt="Bequant" title="Bequant"/>
  <img src="../assets/exchanges/BIGONE.png" height="30" alt="BigONE" title="BigONE"/>
  <img src="../assets/exchanges/BINANCE.png" height="30" alt="Binance" title="Binance"/>
  <img src="../assets/exchanges/BINANCEUSDM.png" height="30" alt="Binance Perpetuals (USD-M)" title="Binance Perpetuals (USD-M)"/>
  <img src="../assets/exchanges/BINGX.png" height="30" alt="BingX" title="BingX"/>
  <img src="../assets/exchanges/BINGX.png" height="30" alt="BingX Perpetuals" title="BingX Perpetuals"/>
  <img src="../assets/exchanges/BITFINEX.png" height="30" alt="Bitfinex" title="Bitfinex"/>
  <img src="../assets/exchanges/BITGET.png" height="30" alt="Bitget" title="Bitget"/>
  <img src="../assets/exchanges/BITGET.png" height="30" alt="Bitget Perpetuals (USD-M)" title="Bitget Perpetuals (USD-M)"/>
  <img src="../assets/exchanges/BITHUMB.png" height="30" alt="Bithumb" title="Bithumb"/>
  <img src="../assets/exchanges/BITMART.png" height="30" alt="BitMart" title="BitMart"/>
  <img src="../assets/exchanges/BITOPRO.png" height="30" alt="BitoPro" title="BitoPro"/>
  <img src="../assets/exchanges/BITRUE.png" height="30" alt="Bitrue" title="Bitrue"/>
  <img src="../assets/exchanges/BITSO.png" height="30" alt="Bitso" title="Bitso"/>
  <img src="../assets/exchanges/BITSTAMP.png" height="30" alt="Bitstamp" title="Bitstamp"/>
  <img src="../assets/exchanges/BITVAVO.png" height="30" alt="Bitvavo" title="Bitvavo"/>
  <img src="../assets/exchanges/BTCMARKETS.png" height="30" alt="BTC Markets" title="BTC Markets"/>
  <img src="../assets/exchanges/BTCTURK.png" height="30" alt="BTCTurk" title="BTCTurk"/>
  <img src="../assets/exchanges/BTSE.png" height="30" alt="BTSE" title="BTSE"/>
  <img src="../assets/exchanges/BULLISH.png" height="30" alt="Bullish" title="Bullish"/>
  <img src="../assets/exchanges/BYBIT.png" height="30" alt="Bybit" title="Bybit"/>
  <img src="../assets/exchanges/BYBITLINEAR.png" height="30" alt="Bybit Perpetuals (linear)" title="Bybit Perpetuals (linear)"/>
  <img src="../assets/exchanges/CEXIO.png" height="30" alt="CEX.IO" title="CEX.IO"/>
  <img src="../assets/exchanges/Coinbase.png" height="30" alt="Coinbase" title="Coinbase"/>
  <img src="../assets/exchanges/COINCHECK.png" height="30" alt="Coincheck" title="Coincheck"/>
  <img src="../assets/exchanges/COINEX.png" height="30" alt="CoinEx" title="CoinEx"/>
  <img src="../assets/exchanges/COINMATE.png" height="30" alt="CoinMate" title="CoinMate"/>
  <img src="../assets/exchanges/coinmetro.png" height="30" alt="CoinMetro" title="CoinMetro"/>
  <img src="../assets/exchanges/COINONE.png" height="30" alt="CoinOne" title="CoinOne"/>
  <img src="../assets/exchanges/Coinstore.png" height="30" alt="Coinstore" title="Coinstore"/>
  <img src="../assets/exchanges/COINW.png" height="30" alt="CoinW" title="CoinW"/>
  <img src="../assets/exchanges/CRYPTOCOM.png" height="30" alt="Crypto.com" title="Crypto.com"/>
  <img src="../assets/exchanges/DEEPCOIN.png" height="30" alt="Deepcoin" title="Deepcoin"/>
  <img src="../assets/exchanges/DIGIFINEX.png" height="30" alt="Digifinex" title="Digifinex"/>
  <img src="../assets/exchanges/DRIFT.png" height="30" alt="Drift PM" title="Drift PM"/>
  <img src="../assets/exchanges/EXMO.png" height="30" alt="Exmo" title="Exmo"/>
  <img src="../assets/exchanges/FOXBIT.png" height="30" alt="Foxbit" title="Foxbit"/>
  <img src="../assets/exchanges/GEMINI.png" height="30" alt="Gemini" title="Gemini"/>
  <img src="../assets/exchanges/HASHKEY.png" height="30" alt="HashKey" title="HashKey"/>
  <img src="../assets/exchanges/HITBTC.png" height="30" alt="HitBTC" title="HitBTC"/>
  <img src="../assets/exchanges/HYPERLIQUID.png" height="30" alt="Hyperliquid Spot" title="Hyperliquid Spot"/>
  <img src="../assets/exchanges/indodax.png" height="30" alt="Indodax" title="Indodax"/>
  <img src="../assets/exchanges/KALSHI.png" height="30" alt="Kalshi" title="Kalshi"/>
  <img src="../assets/exchanges/KRAKEN.png" height="30" alt="Kraken" title="Kraken"/>
  <img src="../assets/exchanges/KUCOIN.png" height="30" alt="KuCoin" title="KuCoin"/>
  <img src="../assets/exchanges/LATOKEN.png" height="30" alt="LATOKEN" title="LATOKEN"/>
  <img src="../assets/exchanges/LBANK.png" height="30" alt="LBank" title="LBank"/>
  <img src="../assets/exchanges/LUNO.png" height="30" alt="Luno" title="Luno"/>
  <img src="../assets/exchanges/MEXC.png" height="30" alt="MEXC" title="MEXC"/>
  <img src="../assets/exchanges/NDAX.png" height="30" alt="NDAX" title="NDAX"/>
  <img src="../assets/exchanges/OKX.png" height="30" alt="OKX Spot" title="OKX Spot"/>
  <img src="../assets/exchanges/OKX.png" height="30" alt="OKX Perpetuals" title="OKX Perpetuals"/>
  <img src="../assets/exchanges/ONETRADING.png" height="30" alt="One Trading" title="One Trading"/>
  <img src="../assets/exchanges/OVERTIME.png" height="30" alt="Overtime Markets" title="Overtime Markets"/>
  <img src="../assets/exchanges/P2B.png" height="30" alt="P2B" title="P2B"/>
  <img src="../assets/exchanges/PAYMIUM.png" height="30" alt="Paymium" title="Paymium"/>
  <img src="../assets/exchanges/PHEMEX.png" height="30" alt="Phemex" title="Phemex"/>
  <img src="../assets/exchanges/POLONIEX.png" height="30" alt="Poloniex" title="Poloniex"/>
  <img src="../assets/exchanges/POLYMARKET.png" height="30" alt="Polymarket" title="Polymarket"/>
  <img src="../assets/exchanges/SXBET.png" height="30" alt="SX Bet" title="SX Bet"/>
  <img src="../assets/exchanges/TOOBIT.png" height="30" alt="Toobit" title="Toobit"/>
  <img src="../assets/exchanges/UPBIT.png" height="30" alt="Upbit" title="Upbit"/>
  <img src="../assets/exchanges/WEEX.png" height="30" alt="WEEX" title="WEEX"/>
  <img src="../assets/exchanges/WHITEBIT.png" height="30" alt="WhiteBIT" title="WhiteBIT"/>
  <img src="../assets/exchanges/WOOX.png" height="30" alt="WOO X" title="WOO X"/>
  <img src="../assets/exchanges/XT.png" height="30" alt="XT.com" title="XT.com"/>
  <img src="../assets/exchanges/ZEBPAY.png" height="30" alt="ZebPay" title="ZebPay"/>
  <img src="../assets/exchanges/ZONDA.png" height="30" alt="Zonda" title="Zonda"/>
</p>

- **REST base:** `https://api.melaya.org`
- **WebSocket base:** `wss://wss.melaya.org`
- **Auth:** API keys prefixed `mk_`, passed as `?apiKey=mk_...` (query) or `Authorization: Bearer mk_...` (header).

The live, always-current list of supported venues is available programmatically (the dataset above is a snapshot; this endpoint is the source of truth):

```
GET https://api.melaya.org/api/v1/market/list-exchanges
```

## Market data (REST)

| Endpoint | Returns |
|---|---|
| `GET /api/v1/market/ticker` | Best bid/ask, last, 24h aggregates |
| `GET /api/v1/market/orderbook` | Order book to a given depth |
| `GET /api/v1/market/ohlcv` | Candles for a timeframe |
| `GET /api/v1/market/trades` | Recent public trades |
| `GET /api/v1/market/markets` | Tradable markets on a venue |
| `GET /api/v1/market/currencies` | Listed currencies on a venue |
| `GET /api/v1/market/status` | Operational status: ok / maintenance / degraded |
| `GET /api/v1/market/time` | Exchange server time |

Batch and derivatives endpoints (POST) cover multi-symbol tickers, OHLCV, public trades, funding rates and history, open interest and history, instrument constraints, and cross-exchange liquidation events.

## Streaming (WebSocket)

Subscribe with your API key on the query string:

| Stream | Path |
|---|---|
| Ticker | `wss://wss.melaya.org/ws/ticker?apiKey=mk_...&exchange=binance&symbol=BTC/USDT&market=spot` |
| Order book | `wss://wss.melaya.org/ws/orderbook?apiKey=mk_...&exchange=bybit&symbol=BTC/USDT&limit=20&market=spot` |
| OHLCV | `wss://wss.melaya.org/ws/ohlcv?apiKey=mk_...&exchange=binance&symbol=BTC/USDT&timeframe=1m&market=spot` |
| Public trades | `wss://wss.melaya.org/ws/public-trades?apiKey=mk_...&exchange=binance&symbol=BTC/USDT&market=spot` |
| Liquidations | `wss://wss.melaya.org/ws/liquidations?apiKey=mk_...&exchange=binance` |

The ticker stream fires only when the normalized ticker advances (no duplicate frames). Liquidations can be filtered to one venue or consumed as a cross-exchange firehose.

> Authenticated account & trading streams (balances, orders, positions, agent strategies) are part of the Melaya product and require your connected exchange credentials — see the product docs at [melaya.org/docs](https://melaya.org/docs). This open SDK covers the public market-data surface above.

## Normalized schema

Regardless of venue, market data comes back in one shape — e.g. a ticker always exposes `bid`, `ask`, `last`, `high`, `low`, `baseVolume`, `quoteVolume`, and `timestamp`. This is the whole point: your code does not branch per exchange.

> Coverage and capabilities evolve. Always treat `GET /api/v1/market/list-exchanges` and the per-venue capability fields as the source of truth rather than hardcoding a venue list.
