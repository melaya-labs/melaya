# Exchanges & the unified API

Melaya exposes **one normalized REST + WebSocket API over 70+ venues**, backed by an in-house Rust engine. You write your integration once against the Melaya schema; the engine handles each venue's symbol formats, rate limits, settlement suffixes, funding intervals, and connection lifecycles.

This page is the **venue catalog, the normalized schema, and authentication**. The endpoint reference is split across two companion pages:

- **[Market data & streaming](./market-data.md)** — REST reads (ticker, order book, OHLCV, trades, funding, open interest, liquidations) and the public WebSocket streams.
- **[Trading & strategies](./trading.md)** — account, paper (sim) trading, live trading, backtesting, launching `custom` strategies and `agent_crew` [trading crews](./agentic-trading.md), and the private streams.

Official SDKs wrap the whole API in **9 languages** — TypeScript/JavaScript, Python, Go, Rust, Java, Kotlin, C#/.NET, Ruby, and PHP (see [`packages/`](../packages)). One `mk_` key unlocks the whole surface.

## Supported venues

The 70+ venues break down into two families that **share one API surface**:

- **Centralized exchanges & perpetuals** — **60 spot exchanges** plus **5 perpetual-futures venues** (binanceusdm, bingxfutures, bitgetfutures, bybitlinear, okxswap).
- **Prediction markets & DEX** — **6 venues** (azuro, drift_pm, kalshi, overtime, polymarket, sxbet).

**One API, both families.** CEX, perpetuals, and prediction markets are all reached through the **same normalized REST + WebSocket schema, the same `mk_` key, and the same SDK methods** — you select the venue with the `exchange` (CEX/perp) or `venue` (prediction-market) parameter. There's no separate client and no separate auth: a ticker is a ticker, an order is an order, a stream is a stream, whether it's Binance spot, a Bybit perp, or a Polymarket event contract. Prediction-market listings come from `POST /api/v1/market/pm-markets` and the PM trading surface follows the identical call pattern (see [Market data](./market-data.md) and [Trading](./trading.md)). A machine-readable dataset — id, display name, market type, auth requirements (passphrase / application-id), and native ticker-stream support — is published here: [`data/exchanges.json`](../data/exchanges.json) · [`data/exchanges.csv`](../data/exchanges.csv).

### Centralized exchanges & perpetuals

<p align="center">
  <img src="../assets/exchanges/ASCENDEX.png" height="30" alt="AscendEX" title="AscendEX"/>
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
  <img src="../assets/exchanges/EXMO.png" height="30" alt="Exmo" title="Exmo"/>
  <img src="../assets/exchanges/FOXBIT.png" height="30" alt="Foxbit" title="Foxbit"/>
  <img src="../assets/exchanges/GEMINI.png" height="30" alt="Gemini" title="Gemini"/>
  <img src="../assets/exchanges/HASHKEY.png" height="30" alt="HashKey" title="HashKey"/>
  <img src="../assets/exchanges/HITBTC.png" height="30" alt="HitBTC" title="HitBTC"/>
  <img src="../assets/exchanges/HYPERLIQUID.png" height="30" alt="Hyperliquid Spot" title="Hyperliquid Spot"/>
  <img src="../assets/exchanges/indodax.png" height="30" alt="Indodax" title="Indodax"/>
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
  <img src="../assets/exchanges/P2B.png" height="30" alt="P2B" title="P2B"/>
  <img src="../assets/exchanges/PAYMIUM.png" height="30" alt="Paymium" title="Paymium"/>
  <img src="../assets/exchanges/PHEMEX.png" height="30" alt="Phemex" title="Phemex"/>
  <img src="../assets/exchanges/POLONIEX.png" height="30" alt="Poloniex" title="Poloniex"/>
  <img src="../assets/exchanges/TOOBIT.png" height="30" alt="Toobit" title="Toobit"/>
  <img src="../assets/exchanges/UPBIT.png" height="30" alt="Upbit" title="Upbit"/>
  <img src="../assets/exchanges/WEEX.png" height="30" alt="WEEX" title="WEEX"/>
  <img src="../assets/exchanges/WHITEBIT.png" height="30" alt="WhiteBIT" title="WhiteBIT"/>
  <img src="../assets/exchanges/WOOX.png" height="30" alt="WOO X" title="WOO X"/>
  <img src="../assets/exchanges/XT.png" height="30" alt="XT.com" title="XT.com"/>
  <img src="../assets/exchanges/ZEBPAY.png" height="30" alt="ZebPay" title="ZebPay"/>
  <img src="../assets/exchanges/ZONDA.png" height="30" alt="Zonda" title="Zonda"/>
</p>

### Prediction markets & DEX

Same schema, same `mk_` key, same streams — addressed by `venue` (e.g. `polymarket`, `kalshi`). Listings via `POST /api/v1/market/pm-markets`.

<p align="center">
  <img src="../assets/exchanges/AZURO.png" height="30" alt="Azuro" title="Azuro"/>
  <img src="../assets/exchanges/DRIFT.png" height="30" alt="Drift PM" title="Drift PM"/>
  <img src="../assets/exchanges/KALSHI.png" height="30" alt="Kalshi" title="Kalshi"/>
  <img src="../assets/exchanges/OVERTIME.png" height="30" alt="Overtime Markets" title="Overtime Markets"/>
  <img src="../assets/exchanges/POLYMARKET.png" height="30" alt="Polymarket" title="Polymarket"/>
  <img src="../assets/exchanges/SXBET.png" height="30" alt="SX Bet" title="SX Bet"/>
</p>

### Enabled on demand

The venues above are the **validated, live** set — the ones currently activated for trading and reflected in `list-exchanges`. The engine carries **integrated adapters for additional venues** that aren't switched on yet, including major derivatives and perp-DEX venues such as **Deribit, BitMEX, Gate, HTX (Huobi), dYdX, Apex, Paradex, Delta, and Derive**, plus inverse / COIN-M and other perpetual markets on venues already listed. These are **pluggable but enabled on demand**: each is activated once it clears validation testing, which we prioritize when a customer wants to trade there. If your strategy needs a venue you don't see in the list, ask us to enable it — the adapter usually already exists.

The live, always-current list of **activated** venues is available programmatically (the dataset above is a snapshot; this endpoint is the source of truth):

```
GET https://api.melaya.org/api/v1/market/list-exchanges
```

## Bases & authentication

- **REST base:** `https://api.melaya.org`
- **WebSocket base:** `wss://wss.melaya.org`
- **Auth:** API keys prefixed `mk_`, passed as `?apiKey=mk_...` (query) or `Authorization: Bearer mk_...` (header).

Market data, account/strategy reads, paper trading, and backtesting need only the `mk_` key. **Live** order placement and live strategy/crew launches additionally require a connected exchange key (referenced by `apiKeyId` — connect one in the dashboard → **Settings → Connectors**). Full read/write breakdown on the [Trading & strategies](./trading.md) page.

## Normalized schema

Regardless of venue, market data comes back in one shape — e.g. a ticker always exposes `bid`, `ask`, `last`, `high`, `low`, `baseVolume`, `quoteVolume`, and `timestamp`. This is the whole point: your code does not branch per exchange.

> Coverage and capabilities evolve. Always treat `GET /api/v1/market/list-exchanges` and the per-venue capability fields as the source of truth rather than hardcoding a venue list.

## Where next

- **[Market data & streaming →](./market-data.md)** — every REST read + the public WebSocket streams.
- **[Trading & strategies →](./trading.md)** — account, paper, live, backtesting, and launching `custom` + `agent_crew` strategies.
- **[AI agentic trading →](./agentic-trading.md)** — the conceptual guide to trading crews.
