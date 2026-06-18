/**
 * Melaya TypeScript SDK — quickstart / smoke test.
 *
 *   npm install @melaya/sdk
 *   MELAYA_API_KEY=mk_... npx tsx examples/typescript.ts
 */
import { Melaya } from "@melaya/sdk";

const apiKey = process.env.MELAYA_API_KEY;
if (!apiKey) throw new Error("Set MELAYA_API_KEY=mk_...");

const melaya = new Melaya({ apiKey });

async function main() {
  // 1. How many venues are live?
  const exchanges = await melaya.market.listExchanges();
  console.log(`exchanges: ${exchanges.length}`);

  // 2. Normalized REST ticker
  const t = await melaya.market.ticker({ exchange: "binance", symbol: "BTC/USDT", market: "spot" });
  console.log(`BTC/USDT  last=${t.last}  bid=${t.bid}  ask=${t.ask}`);

  // 3. Order book
  const book = await melaya.market.orderbook({ exchange: "bybit", symbol: "BTC/USDT", market: "spot", limit: 5 });
  console.log("top bid:", book.bids[0], "top ask:", book.asks[0]);

  // 4. Live stream — print 3 ticker frames then stop
  let n = 0;
  const stream = melaya.stream.ticker({ exchange: "binance", symbol: "BTC/USDT", market: "spot" });
  for await (const frame of stream) {
    console.log("stream:", frame.last);
    if (++n >= 3) break;
  }

  // 5. Account — connected keys + tier usage
  const keys = await melaya.account.keys();
  console.log(`connected keys: ${keys.map((k) => k.apiKeyId).join(", ") || "(none)"}`);
  const usage = await melaya.account.usage();
  console.log(`tier: ${usage.tier}`);

  // 6. Paper trading — launch a paper strategy (no exchange key needed) and
  //    round-trip a synthetic order through the sim broker. Nothing hits a venue.
  const { strategyId } = await melaya.strategies.create({
    name: "SDK example (paper)",
    strategyType: "custom", // SDK-launchable strategies are custom Rhai definitions
    exchange: "binanceusdm",
    symbol: "BTC/USDT:USDT",
    market: "FUTURES",
    dryRun: true, // paper. dryRun:false + apiKeyId would place REAL orders.
    params: {
      language: "rhai",
      definition: `fn evaluate() { emit_long(param("qty")); }`,
      qty: 0.001,
    },
  });
  console.log(`launched paper strategy ${strategyId}`);
  const fill = await melaya.sim.createOrder({
    strategyId, exchange: "binanceusdm", symbol: "BTC/USDT:USDT",
    side: "buy", type: "market", amount: 0.001, market: "FUTURES",
  });
  console.log(`paper fill @ ${fill.fill_price}  (order ${fill.order_id})`);
  console.log("paper balance:", await melaya.sim.balance({ strategyId }));
  await melaya.strategies.stop(strategyId);

  // 7. Backtest on the Rust engine
  const start = await melaya.backtest.start({
    strategyType: "custom", exchange: "binance", symbol: "BTC/USDT", timeframe: "1h",
    since_ms: Date.now() - 90 * 864e5, until_ms: Date.now(),
    language: "rhai", definition: `fn evaluate() { emit_long(param("qty")); }`,
    params: { qty: 0.001 },
  });
  console.log(`backtest job ${start.job_id} started`);

  console.log("done");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
