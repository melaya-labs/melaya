<!--
SEO note: canonical reference for Melaya's market-data API — normalized REST reads
and public WebSocket streams across 70+ venues (CEX, perps, prediction markets).
Keywords: unified crypto market data API, normalized ticker, order book, OHLCV,
funding rate API, open interest, liquidations stream, WebSocket crypto data.
-->

# Market data & streaming

Normalized **REST reads** and **public WebSocket streams** across all [70+ venues](./exchanges.md): CEX, perpetuals, and prediction markets, one schema. For account, trading, backtesting, and strategy/crew launch, see [Trading & strategies](./trading.md).

- **REST base:** `https://api.melaya.org`
- **WebSocket base:** `wss://wss.melaya.org`
- **Auth:** API key prefixed `mk_`, passed as `?apiKey=mk_...` (query) or `Authorization: Bearer mk_...` (header). Market data needs only the key.

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
| `GET /api/v1/market/list-exchanges` | The live list of supported venues (source of truth) |

Batch & derivatives endpoints (POST):

| Endpoint | Returns |
|---|---|
| `POST /api/v1/market/tickers` | Tickers for many symbols on one venue |
| `POST /api/v1/market/ohlcv-multi` | Multi-symbol OHLCV in one call |
| `POST /api/v1/market/funding-rates` · `/funding-rate-history` · `/funding-rate-history-multi` | Perp funding (latest, history, cross-venue) |
| `POST /api/v1/market/open-interest` · `/open-interest-history` · `/open-interest-history-multi` | Open interest (latest, history, cross-venue) |
| `POST /api/v1/market/instruments` · `/market-constraints` | Instrument list + tick size / min-notional / qty step |
| `POST /api/v1/market/liquidation-events` | Cross-exchange liquidation events |
| `POST /api/v1/market/pm-markets` | Prediction-market listings (polymarket, kalshi, …) |
| `GET  /api/v1/public/catalog-counts` | Live platform catalog counts |

**Prediction markets share this surface** — `pm-markets` returns event/market listings addressed by `venue`, and the same ticker / order book / OHLCV reads apply to PM instruments. See [Exchanges](./exchanges.md#prediction-markets--dex).

## Streaming (WebSocket)

Subscribe with your API key on the query string:

| Stream | Path |
|---|---|
| Ticker | `wss://wss.melaya.org/ws/ticker?apiKey=mk_...&exchange=binance&symbol=BTC/USDT&market=spot` |
| Order book | `wss://wss.melaya.org/ws/orderbook?apiKey=mk_...&exchange=bybit&symbol=BTC/USDT&limit=20&market=spot` |
| OHLCV | `wss://wss.melaya.org/ws/ohlcv?apiKey=mk_...&exchange=binance&symbol=BTC/USDT&timeframe=1m&market=spot` |
| Public trades | `wss://wss.melaya.org/ws/public-trades?apiKey=mk_...&exchange=binance&symbol=BTC/USDT&market=spot` |
| Liquidations | `wss://wss.melaya.org/ws/liquidations?apiKey=mk_...&exchange=binance` |

The ticker stream fires only when the normalized ticker advances (no duplicate frames). Liquidations can be filtered to one venue or consumed as a cross-exchange firehose. **Private** account & strategy streams are documented under [Trading & strategies](./trading.md#private-websocket-feeds).

## Using the SDK

```ts
import { Melaya } from "@melaya/sdk";
const m = new Melaya({ apiKey: "mk_..." });

// REST — normalized ticker from any of 70+ venues
const t = await m.market.ticker({ exchange: "binance", symbol: "BTC/USDT", market: "spot" });
console.log(t.last, t.bid, t.ask);

// Order book
const book = await m.market.orderbook({ exchange: "bybit", symbol: "BTC/USDT", market: "spot", limit: 20 });

// Derivatives — cross-venue funding in one call
const funding = await m.market.fundingRateHistoryMulti({
  exchanges: ["binanceusdm", "bybit", "okx"], symbols: ["BTCUSDT"], hours: 72,
});

// WebSocket — live order book stream
for await (const frame of m.stream.orderbook({ exchange: "bybit", symbol: "BTC/USDT", market: "spot", limit: 20 })) {
  console.log(frame.bids[0], frame.asks[0]);
}
```

The same surface exists in all 9 SDKs (idiomatic per language). See runnable quickstarts under [`examples/`](../examples).

## Normalized schema

Regardless of venue, market data comes back in one shape: a ticker always exposes `bid`, `ask`, `last`, `high`, `low`, `baseVolume`, `quoteVolume`, and `timestamp`. Your code does not branch per exchange. Full detail on the [Exchanges](./exchanges.md#normalized-schema) page.

## Where next

- **[Trading & strategies →](./trading.md)** — account, paper, live, backtesting, and launching `custom` + `agent_crew` strategies.
- **[Exchanges →](./exchanges.md)** — the venue catalog and the normalized schema.
