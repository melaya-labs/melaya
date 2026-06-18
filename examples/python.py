"""Melaya Python SDK — quickstart / smoke test.

    pip install "melaya[stream]"
    MELAYA_API_KEY=mk_... python examples/python.py
"""
import asyncio
import os

from melaya import Melaya


def main() -> None:
    api_key = os.environ.get("MELAYA_API_KEY")
    if not api_key:
        raise SystemExit("Set MELAYA_API_KEY=mk_...")

    m = Melaya(api_key=api_key)

    # 1. How many venues are live?
    exchanges = m.market.list_exchanges()
    print(f"exchanges: {len(exchanges)}")

    # 2. Normalized REST ticker
    t = m.market.ticker(exchange="binance", symbol="BTC/USDT", market="spot")
    print(f"BTC/USDT  last={t.get('last')}  bid={t.get('bid')}  ask={t.get('ask')}")

    # 3. Order book
    book = m.market.orderbook(exchange="bybit", symbol="BTC/USDT", market="spot", limit=5)
    print("top bid:", book["bids"][0], "top ask:", book["asks"][0])

    # 4. Live stream — print 3 ticker frames then stop
    asyncio.run(_stream(m))

    # 5. Account — connected keys + tier usage
    keys = m.account.keys()
    print("connected keys:", ", ".join(k["apiKeyId"] for k in keys) or "(none)")
    print("tier:", m.account.usage().get("tier"))

    # 6. Paper trading — launch a paper strategy (no exchange key needed) and
    #    round-trip a synthetic order through the sim broker. Nothing hits a venue.
    res = m.strategies.create(
        name="SDK example (paper)", strategy_type="custom",  # custom Rhai definition
        exchange="binanceusdm", symbol="BTC/USDT:USDT", market="FUTURES",
        dry_run=True,  # dry_run=False + api_key_id places REAL orders
        params={"language": "rhai",
                "definition": 'fn evaluate() { emit_long(param("qty")); }',
                "qty": 0.001},
    )
    sid = res["strategyId"]
    print(f"launched paper strategy {sid}")
    fill = m.sim.create_order(strategy_id=sid, exchange="binanceusdm",
                              symbol="BTC/USDT:USDT", side="buy", type="market",
                              amount=0.001, market="FUTURES")
    print(f"paper fill @ {fill.get('fill_price')}  (order {fill.get('order_id')})")
    print("paper balance:", m.sim.balance(strategy_id=sid))
    m.strategies.stop(sid)

    # 7. Backtest on the Rust engine
    start = m.backtest.start({
        "strategyType": "custom", "exchange": "binance", "symbol": "BTC/USDT", "timeframe": "1h",
        "language": "rhai", "definition": 'fn evaluate() { emit_long(param("qty")); }',
        "params": {"qty": 0.001},
    })
    print(f"backtest job {start['job_id']} started")

    m.close()
    print("done")


async def _stream(m: Melaya) -> None:
    n = 0
    async for frame in m.stream.ticker(exchange="binance", symbol="BTC/USDT", market="spot"):
        print("stream:", frame.get("last"))
        n += 1
        if n >= 3:
            break


if __name__ == "__main__":
    main()
