// Melaya C# / .NET SDK -- quickstart / smoke test.
//
//   dotnet add package Melaya
//   MELAYA_API_KEY=mk_... dotnet run
using Melaya;

var apiKey = Environment.GetEnvironmentVariable("MELAYA_API_KEY")
    ?? throw new InvalidOperationException("Set MELAYA_API_KEY=mk_...");
using var client = new MelayaClient(new MelayaOptions { ApiKey = apiKey });

// 1. How many venues are live?
Console.WriteLine($"exchanges: {(await client.Market.ListExchangesAsync()).Count}");

// 2. Normalized REST ticker
var t = await client.Market.TickerAsync("binance", "BTC/USDT", "spot");
Console.WriteLine($"BTC/USDT last={t.GetProperty("last")}");

// 3. Order book
var book = await client.Market.OrderbookAsync("bybit", "BTC/USDT", "spot", 5);
Console.WriteLine($"top bid: {book.GetProperty("bids")[0]}  top ask: {book.GetProperty("asks")[0]}");

// 4. Live stream -- print 3 ticker frames then stop
var n = 0;
await foreach (var frame in client.Stream.TickerAsync("binance", "BTC/USDT", "spot"))
{
    Console.WriteLine($"stream: {frame.GetProperty("last")}");
    if (++n >= 3) break;
}

// 5. Account -- connected keys + tier usage
Console.WriteLine($"connected keys: {(await client.Account.KeysAsync()).Count}");
Console.WriteLine($"tier: {(await client.Account.UsageAsync()).GetProperty("tier")}");

// 6. Paper trading -- launch a paper strategy (no exchange key needed) and
//    round-trip a synthetic order through the sim broker. Nothing hits a venue.
var created = await client.Strategies.CreateAsync(new
{
    name = "SDK example (paper)",
    strategyType = "custom",                 // custom Rhai definition
    exchange = "binanceusdm", symbol = "BTC/USDT:USDT", market = "FUTURES",
    dryRun = true,                            // dryRun:false + apiKeyId => REAL orders
    @params = new { language = "rhai", definition = "fn evaluate() { emit_long(param("qty")); }", qty = 0.001 },
});
var sid = created.StrategyId;
Console.WriteLine($"launched paper strategy {sid}");
var fill = await client.Sim.CreateOrderAsync(sid, "binanceusdm", "BTC/USDT:USDT",
    "buy", 0.001, "market", market: "FUTURES");
Console.WriteLine($"paper fill @ {fill.FillPrice}");
Console.WriteLine($"paper balance: {await client.Sim.BalanceAsync(sid)}");
await client.Strategies.StopAsync(sid);

// 7. Backtest on the Rust engine
var bt = await client.Backtest.StartAsync(new
{
    strategyType = "custom", exchange = "binance", symbol = "BTC/USDT", timeframe = "1h",
    language = "rhai", definition = "fn evaluate() { emit_long(param("qty")); }", @params = new { qty = 0.001 },
});
Console.WriteLine($"backtest job {bt.GetProperty("job_id")} started");
Console.WriteLine("done");
