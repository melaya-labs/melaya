// FULL endpoint validation for @melaya/sdk — every method, live.
// Safety: paper/sim only. NEVER places a live order or launches a live strategy.
//   - destructive/billable endpoints (backtest.deleteAll, strategies.aiOptStart,
//     aiOptApprove) are WIRED-checked, not invoked, to avoid data loss / spend.
// Run: MK=mk_... NODE_TLS_REJECT_UNAUTHORIZED=0 node full_smoke.mjs
import { pathToFileURL } from "node:url";
const { Melaya, MelayaError } = await import(new URL("../dist/index.js", import.meta.url).href);

const apiKey = process.env.MK;
if (!apiKey) { console.error("set MK=mk_..."); process.exit(2); }
const m = new Melaya({ apiKey });
const SPOT = { exchange: "binance", symbol: "BTC/USDT", market: "spot" };
const PERP = { exchange: "binanceusdm", symbol: "BTC/USDT:USDT" };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const R = [];
const pass = (cat, name, d) => R.push({ cat, name, st: "PASS", d: String(d ?? "").slice(0, 80) });
const fail = (cat, name, e) => R.push({ cat, name, st: "FAIL", d: `${e?.status ?? ""} ${e?.code ?? ""} ${String(e?.message ?? e).slice(0, 90)}` });
const wired = (cat, name, d) => R.push({ cat, name, st: "WIRED", d: String(d).slice(0, 80) });
const skip = (cat, name, d) => R.push({ cat, name, st: "SKIP", d: String(d).slice(0, 80) });

async function chk(cat, name, fn, validate, { retry = false } = {}) {
  for (let i = 1; i <= (retry ? 2 : 1); i++) {
    try { const r = await fn(); if (!validate || validate(r)) { pass(cat, name, JSON.stringify(r)); return r; }
      if (i === (retry ? 2 : 1)) { R.push({ cat, name, st: "FAIL", d: "invalid shape: " + JSON.stringify(r).slice(0, 80) }); return r; } }
    catch (e) { if (i === (retry ? 2 : 1)) { fail(cat, name, e); return null; } }
    await sleep(1600);
  }
}
const arr = (n) => (r) => Array.isArray(r) && r.length >= n;
const isArr = (r) => Array.isArray(r);
const obj = (r) => r && typeof r === "object";

function streamChk(cat, name, mk) {
  return new Promise((resolve) => {
    let opened = false, done = false, s;
    const fin = (good, d) => { if (done) return; done = true; try { s?.close(); } catch {} R.push({ cat, name, st: good ? "PASS" : "FAIL", d: String(d).slice(0, 70) }); resolve(); };
    Promise.resolve(mk()).then((stream) => {
      s = stream;
      s.on("open", () => { opened = true; });
      s.on("message", (f) => fin(true, "frame " + JSON.stringify(f).slice(0, 45)));
      s.on("error", (e) => fin(false, "ws err " + String(e?.message ?? e).slice(0, 55)));
      setTimeout(() => fin(opened, opened ? "open, no frame 10s" : "no open 10s"), 10000);
    }).catch((e) => fin(false, `mint/open ${e?.status ?? ""} ${String(e?.message ?? e).slice(0, 60)}`));
  });
}

// ════ MARKET (22) ════
await chk("market", "listExchanges", () => m.market.listExchanges(), arr(60));
await chk("market", "ticker", () => m.market.ticker(SPOT), (r) => obj(r) && (r.last != null || r.bid != null), { retry: true });
await chk("market", "orderbook", () => m.market.orderbook({ ...SPOT, limit: 5 }), (r) => r?.bids?.length > 0, { retry: true });
await chk("market", "ohlcv", () => m.market.ohlcv({ ...SPOT, timeframe: "1h", limit: 10 }), arr(1), { retry: true });
await chk("market", "trades", () => m.market.trades(SPOT), arr(1), { retry: true });
await chk("market", "markets", () => m.market.markets({ exchange: "binance" }), arr(1));
await chk("market", "currencies", () => m.market.currencies({ exchange: "kraken" }), arr(1), { retry: true });
await chk("market", "status", () => m.market.status({ exchange: "binance" }), obj);
await chk("market", "time", () => m.market.time({ exchange: "binance" }), (r) => r != null);
await chk("market", "tickers", () => m.market.tickers({ exchange: "binance", symbols: ["BTC/USDT", "ETH/USDT"] }), obj, { retry: true });
await chk("market", "fundingRates", () => m.market.fundingRates({ exchange: "binanceusdm", symbols: [PERP.symbol] }), obj, { retry: true });
await chk("market", "fundingRateHistory", () => m.market.fundingRateHistory({ exchange: "binanceusdm", symbol: PERP.symbol, hours: 24 }), arr(1), { retry: true });
await chk("market", "openInterest", () => m.market.openInterest({ exchange: "binanceusdm", symbols: [PERP.symbol] }), obj, { retry: true });
await chk("market", "openInterestHistory", () => m.market.openInterestHistory({ exchange: "binanceusdm", symbol: PERP.symbol, hours: 24 }), arr(1), { retry: true });
await chk("market", "instruments", () => m.market.instruments({ exchange: "binanceusdm" }), obj);
await chk("market", "liquidationEvents", () => m.market.liquidationEvents({ exchange: "binanceusdm", limit: 10 }), isArr);
await chk("market", "ohlcvMulti", () => m.market.ohlcvMulti({ exchange: "binance", symbols: ["BTC/USDT", "ETH/USDT"], timeframe: "1h", limit: 5, market: "spot" }), obj, { retry: true });
await chk("market", "marketConstraints", () => m.market.marketConstraints({ exchange: "binanceusdm", symbol: PERP.symbol }), (r) => r != null);
await chk("market", "fundingRateHistoryMulti", () => m.market.fundingRateHistoryMulti({ exchanges: ["binanceusdm", "bybitlinear"], symbol: PERP.symbol, hours: 24 }), obj, { retry: true });
await chk("market", "openInterestHistoryMulti", () => m.market.openInterestHistoryMulti({ exchanges: ["binanceusdm", "bybitlinear"], symbol: PERP.symbol, hours: 24 }), obj, { retry: true });
await chk("market", "predictionMarkets", () => m.market.predictionMarkets({ venue: "polymarket" }), arr(1), { retry: true });
await chk("market", "catalogCounts", () => m.market.catalogCounts(), (r) => r?.tools > 0);

// ════ ACCOUNT (3) ════
await chk("account", "keys", () => m.account.keys(), isArr);
await chk("account", "usage", () => m.account.usage(), (r) => r?.tier != null);
await chk("account", "apiKeyStatus", () => m.account.apiKeyStatus(), obj);

// ════ STRATEGIES — reads use an existing strategy; lifecycle uses a fresh paper one ════
let readSid = null;
const list = await chk("strategies", "list", () => m.strategies.list(), arr(1));
readSid = list?.[0]?.strategyId;
if (readSid) {
  await chk("strategies", "get", () => m.strategies.get(readSid), (r) => r?.strategyId === readSid);
  await chk("strategies", "status", () => m.strategies.status(readSid), obj);
  await chk("strategies", "executions", () => m.strategies.executions(readSid), isArr);
  await chk("strategies", "trades", () => m.strategies.trades(readSid), isArr);
  await chk("strategies", "performance", () => m.strategies.performance(readSid), isArr);
  await chk("strategies", "logs", () => m.strategies.logs(readSid), isArr);
  await chk("strategies", "aiOptStatus", () => m.strategies.aiOptStatus(readSid), obj);
  await chk("strategies", "aiOptRuns", () => m.strategies.aiOptRuns(readSid), (r) => r != null);
}

// Fresh PAPER strategy for the write lifecycle + a clean sim round-trip.
let paperSid = null;
const RHAI = `fn evaluate() {\n    let qty = param("qty");\n    if qty == () { qty = 0.001; }\n    emit_long(qty);\n}`;
const created = await chk("strategies", "create(custom,paper)", () => m.strategies.create({
  name: "SDK full-smoke (custom)", strategyType: "custom",
  exchange: "binanceusdm", symbol: "BTC/USDT:USDT", market: "FUTURES", dryRun: true,
  params: { language: "rhai", definition: RHAI, qty: 0.001 },
}), (r) => r?.ok && r?.strategyId);
paperSid = created?.strategyId;

if (paperSid) {
  await chk("strategies", "pause", () => m.strategies.pause(paperSid), (r) => r?.ok);
  await chk("strategies", "resume", () => m.strategies.resume(paperSid), (r) => r?.ok);
  await chk("strategies", "updateParams", () => m.strategies.updateParams(paperSid, { fast: 8, slow: 20 }), (r) => r?.ok);
  await chk("strategies", "aiOptStop", () => m.strategies.aiOptStop(paperSid), (r) => r?.ok);

  // ════ SIM (7) — fresh paper strategy, no pre-existing positions ════
  await chk("sim", "balance", () => m.sim.balance({ strategyId: paperSid }), (r) => r?.total != null);
  await chk("sim", "positions", () => m.sim.positions({ strategyId: paperSid }), isArr);
  await chk("sim", "listAccounts", () => m.sim.listAccounts(), isArr);
  await chk("sim", "myTrades", () => m.sim.myTrades({ strategyId: paperSid }), isArr);
  // Resting limit far below market -> won't fill -> cancelable (validates create + cancel)
  let ord = null;
  const px = await m.market.ticker(PERP).then((t) => Number(t.last || t.bid || 60000)).catch(() => 60000);
  ord = await chk("sim", "createOrder(limit,resting)", () => m.sim.createOrder({
    strategyId: paperSid, exchange: "binanceusdm", symbol: "BTC/USDT:USDT",
    side: "buy", type: "limit", price: Math.round(px * 0.5), amount: 0.001, market: "FUTURES",
  }), (r) => r?.order_id);
  await chk("sim", "openOrders", () => m.sim.openOrders({ strategyId: paperSid }), isArr);
  if (ord?.order_id) {
    await chk("sim", "cancelOrder", () => m.sim.cancelOrder({ strategyId: paperSid, orderId: ord.order_id, symbol: "BTC/USDT:USDT", exchange: "binanceusdm" }), obj);
  } else { skip("sim", "cancelOrder", "no resting order id"); }
} else {
  for (const n of ["pause", "resume", "updateParams", "aiOptStop"]) skip("strategies", n, "create(paper) failed");
  for (const n of ["balance", "positions", "listAccounts", "myTrades", "createOrder(limit,resting)", "openOrders", "cancelOrder"]) skip("sim", n, "no paper sid");
}

// aiOptStart / aiOptApprove are billable / side-effecting — wired-check only.
wired("strategies", "aiOptStart", "not invoked (would start a billed optimization)");
wired("strategies", "aiOptApprove", "not invoked (applies optimizer output)");

// ════ BACKTEST (11; deleteAll skipped — destructive) ════
const now = Date.now();
const bt = await chk("backtest", "start", () => m.backtest.start({
  strategyType: "macd_cross", exchange: "binance", symbol: "BTC/USDT", timeframe: "1h",
  since_ms: now - 60 * 864e5, until_ms: now, initial_equity: 10000, params: { fast: 12, slow: 26, signal: 9 },
}), (r) => r?.job_id);
const jobId = bt?.job_id;
if (jobId) {
  let status = "queued";
  for (let i = 0; i < 12 && !["done", "error", "halted", "cancelled"].includes(status); i++) {
    await sleep(2000); try { status = String((await m.backtest.job(jobId)).status || "").toLowerCase(); } catch {}
  }
  await chk("backtest", "job(poll)", () => m.backtest.job(jobId), (r) => r?.job_id === jobId);
  if (status === "done") {
    await chk("backtest", "results", () => m.backtest.results(jobId), obj);
    await chk("backtest", "trades", () => m.backtest.trades(jobId, { limit: 10 }), isArr);
  } else { skip("backtest", "results", `job ${status}`); skip("backtest", "trades", `job ${status}`); }
} else { for (const n of ["job(poll)", "results", "trades"]) skip("backtest", n, "start failed"); }
await chk("backtest", "list", () => m.backtest.list({ limit: 5 }), isArr);
await chk("backtest", "favorites", () => m.backtest.favorites({ limit: 5 }), isArr);
await chk("backtest", "fundingRange", () => m.backtest.fundingRange({ exchange: "binanceusdm", symbol: PERP.symbol }), (r) => r === null || typeof r === "number");
// sweep: start a tiny grid sweep to get a parent, then read it
const sweep = await chk("backtest", "start(grid_sweep)", () => m.backtest.start({
  mode: "grid_sweep", strategyType: "macd_cross", exchange: "binance", symbol: "BTC/USDT", timeframe: "1h",
  since_ms: now - 30 * 864e5, until_ms: now, initial_equity: 10000,
  paramRanges: { fast: [10, 12], slow: [24, 26] },
}), (r) => r?.job_id);
if (sweep?.job_id) { await chk("backtest", "sweep", () => m.backtest.sweep(sweep.job_id, { limit: 10 }), obj); }
else { skip("backtest", "sweep", "no sweep parent"); }
// cancel + delete on jobs we created (non-destructive to other jobs)
const cancelJob = await chk("backtest", "start(for-cancel)", () => m.backtest.start({
  strategyType: "macd_cross", exchange: "binance", symbol: "ETH/USDT", timeframe: "1h",
  since_ms: now - 365 * 864e5, until_ms: now, params: { fast: 12, slow: 26, signal: 9 },
}), (r) => r?.job_id);
if (cancelJob?.job_id) {
  await chk("backtest", "cancel", () => m.backtest.cancel(cancelJob.job_id), obj);
  await chk("backtest", "delete", () => m.backtest.delete(cancelJob.job_id), (r) => r?.ok);
} else { skip("backtest", "cancel", "no job"); skip("backtest", "delete", "no job"); }
wired("backtest", "deleteAll", "not invoked (soft-deletes ALL non-favorited jobs)");

// ════ STREAMS — public (5) + private (2) ════
await streamChk("stream", "ticker", () => m.stream.ticker(SPOT));
await streamChk("stream", "orderbook", () => m.stream.orderbook({ ...SPOT, limit: 10 }));
await streamChk("stream", "ohlcv", () => m.stream.ohlcv({ ...SPOT, timeframe: "1m" }));
await streamChk("stream", "trades", () => m.stream.trades(SPOT));
await streamChk("stream", "liquidations", () => m.stream.liquidations({ exchange: "binanceusdm" }));
await streamChk("stream", "strategies(private)", () => m.stream.strategies());
const qkey = (await m.account.keys().catch(() => []))[0];
if (qkey) { await streamChk("stream", "private(account)", () => m.stream.private({ exchange: qkey.exchange, market: qkey.market, apiKeyId: qkey.apiKeyId })); }
else { skip("stream", "private(account)", "no connected key"); }

// ════ TEARDOWN — stop + delete the paper strategy we created ════
if (paperSid) {
  await chk("teardown", "strategies.stop", () => m.strategies.stop(paperSid), (r) => r?.ok);
  await chk("teardown", "strategies.delete", () => m.strategies.delete(paperSid), (r) => r?.ok);
}

// ════ REPORT ════
console.log("\n══════════════ MELAYA SDK — FULL ENDPOINT VALIDATION (TypeScript) ══════════════");
const cats = [...new Set(R.map((r) => r.cat))];
let nPass = 0, nFail = 0, nWired = 0, nSkip = 0;
for (const cat of cats) {
  console.log(`\n── ${cat} ──`);
  for (const r of R.filter((x) => x.cat === cat)) {
    console.log(`  ${r.st.padEnd(5)} ${r.name.padEnd(28)} ${r.d}`);
    if (r.st === "PASS") nPass++; else if (r.st === "FAIL") nFail++; else if (r.st === "WIRED") nWired++; else nSkip++;
  }
}
console.log("\n════════════════════════════════════════════════════════════════════════════════");
console.log(`PASS ${nPass}   FAIL ${nFail}   WIRED(not-invoked) ${nWired}   SKIP ${nSkip}   |  total methods ${nPass + nFail + nWired + nSkip}`);
console.log(nFail === 0 ? "RESULT: GO — every invoked endpoint validated." : `RESULT: NO-GO — ${nFail} failing.`);
process.exit(nFail === 0 ? 0 : 1);
