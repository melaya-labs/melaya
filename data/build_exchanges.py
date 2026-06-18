"""Generate the public Melaya exchange dataset.

Source of truth: server/src/services/ExchangeCatalog.ts — the ALLOWED_EXCHANGES
allowlist (what GET /api/v1/market/list-exchanges actually serves) plus the
display-name / market / auth metadata maps. 71 venues.

Run: python data/build_exchanges.py   (writes exchanges.json + exchanges.csv here)
"""
import csv
import json
import os

DISPLAY = {
    # perpetuals (5)
    "binanceusdm": "Binance Perpetuals (USD-M)", "bingxfutures": "BingX Perpetuals",
    "bitgetfutures": "Bitget Perpetuals (USD-M)", "bybitlinear": "Bybit Perpetuals (linear)",
    "okxswap": "OKX Perpetuals",
    # spot (60)
    "ascendex": "AscendEX", "backpack": "Backpack", "bequant": "Bequant", "bigone": "BigONE",
    "binance": "Binance", "bingx": "BingX", "bitfinex": "Bitfinex", "bitget": "Bitget",
    "bithumb": "Bithumb", "bitmart": "BitMart", "bitopro": "BitoPro", "bitrue": "Bitrue",
    "bitso": "Bitso", "bitstamp": "Bitstamp", "bitvavo": "Bitvavo", "btcmarkets": "BTC Markets",
    "btcturk": "BTCTurk", "btse": "BTSE", "bullish": "Bullish", "bybit": "Bybit", "cexio": "CEX.IO",
    "coinbase": "Coinbase", "coincheck": "Coincheck", "coinex": "CoinEx", "coinmate": "CoinMate",
    "coinmetro": "CoinMetro", "coinone": "CoinOne", "coinstore": "Coinstore", "coinw": "CoinW",
    "cryptocom": "Crypto.com", "deepcoin": "Deepcoin", "digifinex": "Digifinex", "exmo": "Exmo",
    "foxbit": "Foxbit", "gemini": "Gemini", "hashkey": "HashKey", "hitbtc": "HitBTC",
    "hyperliquid": "Hyperliquid Spot", "indodax": "Indodax", "kraken": "Kraken", "kucoin": "KuCoin",
    "latoken": "LATOKEN", "lbank": "LBank", "luno": "Luno", "mexc": "MEXC", "ndax": "NDAX",
    "okx": "OKX Spot", "onetrading": "One Trading", "p2b": "P2B", "paymium": "Paymium",
    "phemex": "Phemex", "poloniex": "Poloniex", "toobit": "Toobit", "upbit": "Upbit", "weex": "WEEX",
    "whitebit": "WhiteBIT", "woox": "WOO X", "xt": "XT.com", "zebpay": "ZebPay", "zonda": "Zonda",
    # prediction-market / DEX (6)
    "azuro": "Azuro", "drift_pm": "Drift PM", "kalshi": "Kalshi", "overtime": "Overtime Markets",
    "polymarket": "Polymarket", "sxbet": "SX Bet",
}

PERPS = {"binanceusdm", "bingxfutures", "bitgetfutures", "bybitlinear", "okxswap"}
PREDICTION = {"azuro", "drift_pm", "kalshi", "overtime", "polymarket", "sxbet"}
REQUIRES_PASSPHRASE = {"okx", "okxswap", "bitget", "bitgetfutures", "kucoin", "coinbase", "cryptocom"}
REQUIRES_APPLICATION_ID = {"woox"}
# Venues with no native ticker WS (route rejects subscribe; use REST/orderbook).
NO_TICKER_WS = {"p2b", "coincheck", "foxbit"} | PREDICTION

rows = []
for vid, name in DISPLAY.items():
    if vid in PERPS:
        market, subtype = "perpetuals", "linear"
    elif vid in PREDICTION:
        market, subtype = "prediction-market", ""
    else:
        market, subtype = "spot", ""
    rows.append({
        "id": vid,
        "name": name,
        "market": market,
        "subtype": subtype,
        "requiresPassphrase": vid in REQUIRES_PASSPHRASE,
        "requiresApplicationId": vid in REQUIRES_APPLICATION_ID,
        "tickerStream": vid not in NO_TICKER_WS,
    })

rows.sort(key=lambda r: r["id"])
assert len(rows) == 71, f"expected 71 venues, got {len(rows)}"

here = os.path.dirname(__file__)
with open(os.path.join(here, "exchanges.json"), "w", encoding="utf-8") as f:
    json.dump(rows, f, indent=2)
    f.write("\n")
with open(os.path.join(here, "exchanges.csv"), "w", encoding="utf-8", newline="") as f:
    w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
    w.writeheader()
    w.writerows(rows)

n_spot = sum(1 for r in rows if r["market"] == "spot")
n_perp = sum(1 for r in rows if r["market"] == "perpetuals")
n_pred = sum(1 for r in rows if r["market"] == "prediction-market")
print(f"wrote {len(rows)} venues -> exchanges.json + exchanges.csv")
print(f"  spot={n_spot}  perpetuals={n_perp}  prediction/DEX={n_pred}")
