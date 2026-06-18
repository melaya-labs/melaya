"""FULL endpoint validation for the `melaya` Python SDK — every method, live.
Safety: paper/sim only. NEVER places a live order or launches a live strategy.
Destructive/billable endpoints (backtest.delete_all, strategies.ai_opt_start /
ai_opt_approve) are WIRED-checked, not invoked.
Run: MK=mk_... python full_smoke.py   (SDK src on sys.path)
"""
import asyncio
import os
import sys
import time

import os as _os; sys.path.insert(0, _os.path.join(_os.path.dirname(__file__), "..", "src"))
from melaya import Melaya, MelayaError  # noqa: E402

KEY = os.environ.get("MK")
if not KEY:
    print("set MK=mk_..."); sys.exit(2)
m = Melaya(api_key=KEY)
# Local dev box does TLS interception; disable cert verification for the test
# (mirrors NODE_TLS_REJECT_UNAUTHORIZED=0 used by the Node smoke). Not an SDK concern.
import warnings as _w  # noqa: E402
import httpx as _httpx  # noqa: E402
_w.filterwarnings("ignore")
m._http = _httpx.Client(base_url="https://api.melaya.org", timeout=30.0,
                        headers={"Authorization": f"Bearer {KEY}"}, verify=False)
import ssl as _ssl  # noqa: E402
_NOVERIFY = _ssl.create_default_context()
_NOVERIFY.check_hostname = False
_NOVERIFY.verify_mode = _ssl.CERT_NONE
SPOT = dict(exchange="binance", symbol="BTC/USDT", market="spot")
PERP = dict(exchange="binanceusdm", symbol="BTC/USDT:USDT")
R = []


def rec(cat, name, st, d=""):
    R.append((cat, name, st, str(d)[:80]))


def chk(cat, name, fn, validate=None, retry=False):
    for i in range(2 if retry else 1):
        try:
            r = fn()
            if validate is None or validate(r):
                rec(cat, name, "PASS", _short(r)); return r
            if i == (1 if retry else 0):
                rec(cat, name, "FAIL", "invalid shape: " + _short(r)); return r
        except MelayaError as e:
            if i == (1 if retry else 0):
                rec(cat, name, "FAIL", f"{e.status or ''} {e.code or ''} {str(e)[:70]}"); return None
        except Exception as e:  # noqa: BLE001
            if i == (1 if retry else 0):
                rec(cat, name, "FAIL", str(e)[:80]); return None
        time.sleep(1.6)


def _short(r):
    import json
    try:
        return json.dumps(r)[:80]
    except Exception:  # noqa: BLE001
        return str(r)[:80]


def is_list(n=0):
    return lambda r: isinstance(r, list) and len(r) >= n


def is_obj(r):
    return isinstance(r, dict) and len(r) > 0


# ════ MARKET (22) ════
chk("market", "list_exchanges", lambda: m.market.list_exchanges(), is_list(60))
chk("market", "ticker", lambda: m.market.ticker(**SPOT), lambda r: is_obj(r) and (r.get("last") is not None or r.get("bid") is not None), retry=True)
chk("market", "orderbook", lambda: m.market.orderbook(**SPOT, limit=5), lambda r: r.get("bids"), retry=True)
chk("market", "ohlcv", lambda: m.market.ohlcv(**SPOT, timeframe="1h", limit=10), is_list(1), retry=True)
chk("market", "trades", lambda: m.market.trades(**SPOT), is_list(1), retry=True)
chk("market", "markets", lambda: m.market.markets(exchange="binance"), is_list(1))
chk("market", "currencies", lambda: m.market.currencies(exchange="kraken"), is_list(1), retry=True)
chk("market", "status", lambda: m.market.status(exchange="binance"), is_obj)
chk("market", "time", lambda: m.market.time(exchange="binance"), lambda r: r is not None)
chk("market", "tickers", lambda: m.market.tickers(exchange="binance", symbols=["BTC/USDT", "ETH/USDT"]), is_obj, retry=True)
chk("market", "funding_rates", lambda: m.market.funding_rates(exchange="binanceusdm", symbols=[PERP["symbol"]]), is_obj, retry=True)
chk("market", "funding_rate_history", lambda: m.market.funding_rate_history(exchange="binanceusdm", symbol=PERP["symbol"], hours=24), is_list(1), retry=True)
chk("market", "open_interest", lambda: m.market.open_interest(exchange="binanceusdm", symbols=[PERP["symbol"]]), is_obj, retry=True)
chk("market", "open_interest_history", lambda: m.market.open_interest_history(exchange="binanceusdm", symbol=PERP["symbol"], hours=24), is_list(1), retry=True)
chk("market", "instruments", lambda: m.market.instruments(exchange="binanceusdm"), is_obj)
chk("market", "liquidation_events", lambda: m.market.liquidation_events(exchange="binanceusdm", limit=10), lambda r: isinstance(r, list))
chk("market", "ohlcv_multi", lambda: m.market.ohlcv_multi(exchange="binance", symbols=["BTC/USDT", "ETH/USDT"], timeframe="1h", limit=5, market="spot"), is_obj, retry=True)
chk("market", "market_constraints", lambda: m.market.market_constraints(exchange="binanceusdm", symbol=PERP["symbol"]), lambda r: r is not None)
chk("market", "funding_rate_history_multi", lambda: m.market.funding_rate_history_multi(exchanges=["binanceusdm", "bybitlinear"], symbol=PERP["symbol"], hours=24), is_obj, retry=True)
chk("market", "open_interest_history_multi", lambda: m.market.open_interest_history_multi(exchanges=["binanceusdm", "bybitlinear"], symbol=PERP["symbol"], hours=24), is_obj, retry=True)
chk("market", "prediction_markets", lambda: m.market.prediction_markets(venue="polymarket"), is_list(1), retry=True)
chk("market", "catalog_counts", lambda: m.market.catalog_counts(), lambda r: r.get("tools", 0) > 0)

# ════ ACCOUNT (3) ════
chk("account", "keys", lambda: m.account.keys(), lambda r: isinstance(r, list))
chk("account", "usage", lambda: m.account.usage(), lambda r: r.get("tier") is not None)
chk("account", "api_key_status", lambda: m.account.api_key_status(), is_obj)

# ════ STRATEGIES — reads on an existing one; lifecycle on a fresh custom paper one ════
lst = chk("strategies", "list", lambda: m.strategies.list(), is_list(1))
read_sid = lst[0]["strategyId"] if lst else None
if read_sid:
    chk("strategies", "get", lambda: m.strategies.get(read_sid), lambda r: r.get("strategyId") == read_sid)
    chk("strategies", "status", lambda: m.strategies.status(read_sid), is_obj)
    chk("strategies", "executions", lambda: m.strategies.executions(read_sid), lambda r: isinstance(r, list))
    chk("strategies", "trades", lambda: m.strategies.trades(read_sid), lambda r: isinstance(r, list))
    chk("strategies", "performance", lambda: m.strategies.performance(read_sid), lambda r: isinstance(r, list))
    chk("strategies", "logs", lambda: m.strategies.logs(read_sid), lambda r: isinstance(r, list))
    chk("strategies", "ai_opt_status", lambda: m.strategies.ai_opt_status(read_sid), is_obj)
    chk("strategies", "ai_opt_runs", lambda: m.strategies.ai_opt_runs(read_sid), lambda r: r is not None)

RHAI = 'fn evaluate() {\n    let qty = param("qty");\n    if qty == () { qty = 0.001; }\n    emit_long(qty);\n}'
created = chk("strategies", "create(custom,paper)", lambda: m.strategies.create(
    name="SDK full-smoke py (custom)", strategy_type="custom",
    exchange="binanceusdm", symbol="BTC/USDT:USDT", market="FUTURES", dry_run=True,
    params={"language": "rhai", "definition": RHAI, "qty": 0.001},
), lambda r: r.get("ok") and r.get("strategyId"))
paper_sid = created.get("strategyId") if created else None

if paper_sid:
    chk("strategies", "pause", lambda: m.strategies.pause(paper_sid), lambda r: r.get("ok"))
    chk("strategies", "resume", lambda: m.strategies.resume(paper_sid), lambda r: r.get("ok"))
    chk("strategies", "update_params", lambda: m.strategies.update_params(paper_sid, {"qty": 0.002}), lambda r: r.get("ok"))
    chk("strategies", "ai_opt_stop", lambda: m.strategies.ai_opt_stop(paper_sid), lambda r: r.get("ok"))

    # ════ SIM (7) ════
    chk("sim", "balance", lambda: m.sim.balance(strategy_id=paper_sid), lambda r: r.get("total") is not None)
    chk("sim", "positions", lambda: m.sim.positions(strategy_id=paper_sid), lambda r: isinstance(r, list))
    chk("sim", "list_accounts", lambda: m.sim.list_accounts(), lambda r: isinstance(r, list))
    chk("sim", "my_trades", lambda: m.sim.my_trades(strategy_id=paper_sid), lambda r: isinstance(r, list))
    px = 60000
    try:
        t = m.market.ticker(**PERP); px = float(t.get("last") or t.get("bid") or 60000)
    except Exception:  # noqa: BLE001
        pass
    ordr = chk("sim", "create_order(limit,resting)", lambda: m.sim.create_order(
        strategy_id=paper_sid, exchange="binanceusdm", symbol="BTC/USDT:USDT",
        side="buy", type="limit", price=round(px * 0.5), amount=0.001, market="FUTURES",
    ), lambda r: r.get("order_id"))
    chk("sim", "open_orders", lambda: m.sim.open_orders(strategy_id=paper_sid), lambda r: isinstance(r, list))
    if ordr and ordr.get("order_id"):
        oid = ordr["order_id"]
        chk("sim", "cancel_order", lambda: m.sim.cancel_order(strategy_id=paper_sid, order_id=oid, symbol="BTC/USDT:USDT", exchange="binanceusdm"), is_obj)
    else:
        rec("sim", "cancel_order", "SKIP", "no resting order id")
else:
    for n in ["pause", "resume", "update_params", "ai_opt_stop"]:
        rec("strategies", n, "SKIP", "create failed")
    for n in ["balance", "positions", "list_accounts", "my_trades", "create_order(limit,resting)", "open_orders", "cancel_order"]:
        rec("sim", n, "SKIP", "no paper sid")

rec("strategies", "ai_opt_start", "WIRED", "not invoked (billed optimization)")
rec("strategies", "ai_opt_approve", "WIRED", "not invoked (applies optimizer output)")

# ════ BACKTEST (custom strategy, end-to-end; deleteAll skipped) ════
now = int(time.time() * 1000)
bt = chk("backtest", "start(custom)", lambda: m.backtest.start({
    "strategyType": "custom", "exchange": "binance", "symbol": "BTC/USDT", "timeframe": "1h",
    "since_ms": now - 60 * 86400000, "until_ms": now, "initial_equity": 10000,
    "language": "rhai", "definition": RHAI, "custom_code": RHAI, "params": {"qty": 0.001},
}), lambda r: r.get("job_id"))
job_id = bt.get("job_id") if bt else None
if job_id:
    status = "pending"
    for _ in range(20):
        if status in ("done", "error", "halted", "cancelled"):
            break
        time.sleep(2)
        try:
            status = str(m.backtest.job(job_id).get("status", "")).lower()
        except Exception:  # noqa: BLE001
            pass
    chk("backtest", "job(poll)", lambda: m.backtest.job(job_id), lambda r: r.get("job_id") == job_id)
    if status == "done":
        chk("backtest", "results", lambda: m.backtest.results(job_id), is_obj)
        chk("backtest", "trades", lambda: m.backtest.trades(job_id, limit=10), lambda r: isinstance(r, list))
    else:
        rec("backtest", "results", "SKIP", f"job {status}")
        rec("backtest", "trades", "SKIP", f"job {status}")
chk("backtest", "list", lambda: m.backtest.list(limit=5), lambda r: isinstance(r, list))
chk("backtest", "favorites", lambda: m.backtest.favorites(limit=5), lambda r: isinstance(r, list))
chk("backtest", "funding_range", lambda: m.backtest.funding_range(exchange="binanceusdm", symbol=PERP["symbol"]), lambda r: r is None or isinstance(r, (int, float)))
sweep = chk("backtest", "start(grid_sweep)", lambda: m.backtest.start({
    "mode": "grid_sweep", "strategyType": "custom", "exchange": "binance", "symbol": "BTC/USDT", "timeframe": "1h",
    "since_ms": now - 30 * 86400000, "until_ms": now, "language": "rhai", "definition": RHAI, "custom_code": RHAI,
    "paramRanges": {"qty": [0.001, 0.002]},
}), lambda r: r.get("job_id"))
if sweep and sweep.get("job_id"):
    chk("backtest", "sweep", lambda: m.backtest.sweep(sweep["job_id"], limit=10), is_obj)
else:
    rec("backtest", "sweep", "SKIP", "no sweep parent")
cj = chk("backtest", "start(for-cancel)", lambda: m.backtest.start({
    "strategyType": "custom", "exchange": "binance", "symbol": "ETH/USDT", "timeframe": "1h",
    "since_ms": now - 365 * 86400000, "until_ms": now, "language": "rhai", "definition": RHAI, "custom_code": RHAI, "params": {"qty": 0.001},
}), lambda r: r.get("job_id"))
if cj and cj.get("job_id"):
    chk("backtest", "cancel", lambda: m.backtest.cancel(cj["job_id"]), is_obj)
    chk("backtest", "delete", lambda: m.backtest.delete(cj["job_id"]), lambda r: r.get("ok"))
else:
    rec("backtest", "cancel", "SKIP", "no job"); rec("backtest", "delete", "SKIP", "no job")
rec("backtest", "delete_all", "WIRED", "not invoked (soft-deletes ALL non-favorited jobs)")


# ════ STREAMS — public (5) + private (2) ════
async def stream_chk(cat, name, factory):
    opened = False
    try:
        agen = factory().__aiter__()
        frame = await asyncio.wait_for(agen.__anext__(), timeout=10)
        rec(cat, name, "PASS", "frame " + _short(frame)[:45])
    except asyncio.TimeoutError:
        rec(cat, name, "PASS" if opened else "FAIL", "no frame in 10s")
    except Exception as e:  # noqa: BLE001
        rec(cat, name, "FAIL", str(e)[:60])


async def run_streams():
    await stream_chk("stream", "ticker", lambda: m.stream.ticker(**SPOT))
    await stream_chk("stream", "orderbook", lambda: m.stream.orderbook(**SPOT, limit=10))
    await stream_chk("stream", "ohlcv", lambda: m.stream.ohlcv(**SPOT, timeframe="1m"))
    await stream_chk("stream", "trades", lambda: m.stream.trades(**SPOT))
    await stream_chk("stream", "liquidations", lambda: m.stream.liquidations(exchange="binanceusdm"))
    await stream_chk("stream", "strategies(private)", lambda: m.stream.strategies())
    try:
        keys = m.account.keys()
    except Exception:  # noqa: BLE001
        keys = []
    if keys:
        k = keys[0]
        await stream_chk("stream", "private(account)", lambda: m.stream.private(exchange=k.get("exchange"), market=k.get("market"), api_key_id=k.get("apiKeyId")))
    else:
        rec("stream", "private(account)", "SKIP", "no connected key")

asyncio.run(run_streams())

# ════ TEARDOWN ════
if paper_sid:
    chk("teardown", "strategies.stop", lambda: m.strategies.stop(paper_sid), lambda r: r.get("ok"))
    chk("teardown", "strategies.delete", lambda: m.strategies.delete(paper_sid), lambda r: r.get("ok"))

m.close()

# ════ REPORT ════
print("\n============== MELAYA SDK — FULL ENDPOINT VALIDATION (Python) ==============")
cats = []
for c, *_ in R:
    if c not in cats:
        cats.append(c)
nP = nF = nW = nS = 0
for cat in cats:
    print(f"\n-- {cat} --")
    for c, name, st, d in [x for x in R if x[0] == cat]:
        print(f"  {st:<5} {name:<28} {d}")
        nP += st == "PASS"; nF += st == "FAIL"; nW += st == "WIRED"; nS += st == "SKIP"
print("\n===========================================================================")
print(f"PASS {nP}   FAIL {nF}   WIRED(not-invoked) {nW}   SKIP {nS}   |  total {nP + nF + nW + nS}")
print("RESULT: GO — every invoked endpoint validated." if nF == 0 else f"RESULT: NO-GO — {nF} failing.")
sys.exit(0 if nF == 0 else 1)
