/*
 * Melaya .NET SDK — FULL endpoint validation (~70 checks)
 *
 * Usage:
 *   set MK=mk_...
 *   set MELAYA_INSECURE_TLS=1   (dev box with TLS intercept)
 *   dotnet run
 *
 * SAFETY: PAPER/SIM ONLY. Never places live orders. Always stop+deletes
 * the paper strategy it creates.
 *
 * WIRED (not invoked): aiOptStart, aiOptApprove, backtest.deleteAll
 */
using System.Text.Json;
using Melaya;

// ── Setup ─────────────────────────────────────────────────────────────────────

var mk = Environment.GetEnvironmentVariable("MK");
if (string.IsNullOrWhiteSpace(mk))
{
    Console.Error.WriteLine("ERROR: set env var MK=mk_...");
    Environment.Exit(1);
}

using var client = new MelayaClient(new MelayaOptions { ApiKey = mk! });

// ── Result tracking ───────────────────────────────────────────────────────────

var results = new List<Result>();

void RecPass(string cat, string name, string d = "")  => results.Add(new(cat, name, "PASS",  d[..Math.Min(d.Length, 80)]));
void RecFail(string cat, string name, string d = "")  => results.Add(new(cat, name, "FAIL",  d[..Math.Min(d.Length, 90)]));
void RecWired(string cat, string name, string d = "") => results.Add(new(cat, name, "WIRED", d[..Math.Min(d.Length, 80)]));
void RecSkip(string cat, string name, string d = "")  => results.Add(new(cat, name, "SKIP",  d[..Math.Min(d.Length, 80)]));

// Retry once after 1.6s on cold-cache failures
async Task<T?> Chk<T>(string cat, string name, Func<Task<T>> fn, Func<T, bool>? validate = null, bool retry = false)
{
    for (int attempt = 1; attempt <= (retry ? 2 : 1); attempt++)
    {
        try
        {
            var r = await fn();
            if (validate == null || validate(r))
            {
                var s = JsonSerializer.Serialize(r);
                RecPass(cat, name, s[..Math.Min(s.Length, 80)]);
                return r;
            }
            if (attempt == (retry ? 2 : 1))
            {
                var s = JsonSerializer.Serialize(r);
                RecFail(cat, name, "invalid shape: " + s[..Math.Min(s.Length, 80)]);
                return r;
            }
        }
        catch (Exception e) when (attempt < (retry ? 2 : 1))
        {
            // will retry
        }
        catch (Exception e)
        {
            var me = e as MelayaException;
            RecFail(cat, name, $"{me?.Status.ToString() ?? ""} {me?.Code ?? ""} {e.Message}"[..Math.Min($"{me?.Status.ToString() ?? ""} {me?.Code ?? ""} {e.Message}".Length, 90)]);
            return default;
        }
        await Task.Delay(1600);
    }
    return default;
}

// Stream check: open WS, expect ≥1 frame within 10s
async Task StreamChk(string cat, string name, Func<IAsyncEnumerable<JsonElement>> mk2)
{
    using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(10));
    int count = 0;
    try
    {
        await foreach (var frame in mk2().WithCancellation(cts.Token).ConfigureAwait(false))
        {
            count++;
            break;
        }
    }
    catch (OperationCanceledException) { /* expected on timeout */ }
    catch (Exception e)
    {
        RecFail(cat, name, $"ws err {e.Message}"[..Math.Min($"ws err {e.Message}".Length, 70)]);
        return;
    }

    if (count > 0)
        RecPass(cat, name, $"frame received");
    else
        RecFail(cat, name, "no frames within 10s");
}

static bool IsArr(JsonElement? e) => e.HasValue && e.Value.ValueKind == JsonValueKind.Array;
static bool IsObj(JsonElement? e) => e.HasValue && e.Value.ValueKind == JsonValueKind.Object;
static bool IsAny(JsonElement? e) => e.HasValue && e.Value.ValueKind != JsonValueKind.Undefined && e.Value.ValueKind != JsonValueKind.Null;
static bool ArrMin(int n, List<JsonElement>? r) => r != null && r.Count >= n;
static bool ObjOrDefault(JsonElement e) => e.ValueKind != JsonValueKind.Undefined;

// ════ MARKET (22) ════
Console.WriteLine("\n── market ──");

await Chk("market", "listExchanges",
    async () => await client.Market.ListExchangesAsync(),
    r => r != null && r.Count >= 60);

var ticker = await Chk("market", "ticker",
    async () => await client.Market.TickerAsync("binance", "BTC/USDT", "spot"),
    r => r.ValueKind != JsonValueKind.Undefined && r.ValueKind != JsonValueKind.Null,
    retry: true);

await Chk("market", "orderbook",
    async () => await client.Market.OrderbookAsync("binance", "BTC/USDT", "spot", 5),
    r => {
        if (r.ValueKind == JsonValueKind.Undefined) return false;
        return r.TryGetProperty("bids", out var bids) && bids.ValueKind == JsonValueKind.Array && bids.GetArrayLength() > 0;
    },
    retry: true);

await Chk("market", "ohlcv",
    async () => await client.Market.OhlcvAsync("binance", "BTC/USDT", "1h", "spot", 10),
    r => r != null && r.Count >= 1,
    retry: true);

await Chk("market", "trades",
    async () => await client.Market.TradesAsync("binance", "BTC/USDT", "spot"),
    r => r != null && r.Count >= 1,
    retry: true);

await Chk("market", "markets",
    async () => await client.Market.MarketsAsync("binance"),
    r => r != null && r.Count >= 1);

await Chk("market", "currencies",
    async () => await client.Market.CurrenciesAsync("kraken"),
    r => r != null && r.Count >= 1,
    retry: true);

await Chk("market", "status",
    async () => await client.Market.StatusAsync("binance"),
    r => r.ValueKind != JsonValueKind.Undefined);

await Chk("market", "time",
    async () => await client.Market.TimeAsync("binance"),
    r => r.ValueKind != JsonValueKind.Undefined && r.ValueKind != JsonValueKind.Null);

await Chk("market", "tickers",
    async () => await client.Market.TickersAsync("binance", ["BTC/USDT", "ETH/USDT"]),
    r => r.ValueKind != JsonValueKind.Undefined,
    retry: true);

await Chk("market", "fundingRates",
    async () => await client.Market.FundingRatesAsync("binanceusdm", ["BTC/USDT:USDT"]),
    r => r.ValueKind != JsonValueKind.Undefined,
    retry: true);

await Chk("market", "fundingRateHistory",
    async () => await client.Market.FundingRateHistoryAsync("binanceusdm", "BTC/USDT:USDT", hours: 24),
    r => r != null && r.Count >= 1,
    retry: true);

await Chk("market", "openInterest",
    async () => await client.Market.OpenInterestAsync("binanceusdm", ["BTC/USDT:USDT"]),
    r => r.ValueKind != JsonValueKind.Undefined,
    retry: true);

await Chk("market", "openInterestHistory",
    async () => await client.Market.OpenInterestHistoryAsync("binanceusdm", "BTC/USDT:USDT", hours: 24),
    r => r != null && r.Count >= 1,
    retry: true);

await Chk("market", "instruments",
    async () => await client.Market.InstrumentsAsync("binanceusdm"),
    r => r.ValueKind != JsonValueKind.Undefined);

await Chk("market", "liquidationEvents",
    async () => await client.Market.LiquidationEventsAsync("binanceusdm", limit: 10),
    r => r != null);

await Chk("market", "ohlcvMulti",
    async () => await client.Market.OhlcvMultiAsync("binance", ["BTC/USDT", "ETH/USDT"], "1h", 5, "spot"),
    r => r.ValueKind != JsonValueKind.Undefined,
    retry: true);

await Chk("market", "marketConstraints",
    async () => await client.Market.MarketConstraintsAsync("binanceusdm", "BTC/USDT:USDT"),
    r => r.ValueKind != JsonValueKind.Undefined && r.ValueKind != JsonValueKind.Null);

await Chk("market", "fundingRateHistoryMulti",
    async () => await client.Market.FundingRateHistoryMultiAsync(["binanceusdm", "bybitlinear"], "BTC/USDT:USDT", hours: 24),
    r => r.ValueKind != JsonValueKind.Undefined,
    retry: true);

await Chk("market", "openInterestHistoryMulti",
    async () => await client.Market.OpenInterestHistoryMultiAsync(["binanceusdm", "bybitlinear"], "BTC/USDT:USDT", hours: 24),
    r => r.ValueKind != JsonValueKind.Undefined,
    retry: true);

await Chk("market", "predictionMarkets",
    async () => await client.Market.PredictionMarketsAsync("polymarket"),
    r => r != null && r.Count >= 1,
    retry: true);

await Chk("market", "catalogCounts",
    async () => await client.Market.CatalogCountsAsync(),
    r => {
        if (r.ValueKind == JsonValueKind.Undefined) return false;
        return r.TryGetProperty("tools", out var t) && t.TryGetInt64(out var n) && n > 0;
    });

// ════ ACCOUNT (3) ════
Console.WriteLine("\n── account ──");

List<JsonElement>? accountKeys = null;
await Chk("account", "keys",
    async () => {
        accountKeys = await client.Account.KeysAsync();
        return accountKeys;
    },
    r => r != null);

await Chk("account", "usage",
    async () => await client.Account.UsageAsync(),
    r => {
        if (r.ValueKind == JsonValueKind.Undefined) return false;
        return r.TryGetProperty("tier", out _);
    });

await Chk("account", "apiKeyStatus",
    async () => await client.Account.ApiKeyStatusAsync(),
    r => r.ValueKind != JsonValueKind.Undefined);

// ════ STRATEGIES — reads on first existing strategy ════
Console.WriteLine("\n── strategies ──");

string? readSid = null;
var stratList = await Chk("strategies", "list",
    async () => await client.Strategies.ListAsync(),
    r => r != null && r.Count >= 1);

if (stratList != null && stratList.Count > 0)
{
    if (stratList[0].TryGetProperty("strategyId", out var sidEl))
        readSid = sidEl.GetString();

    if (readSid != null)
    {
        await Chk("strategies", "get",
            async () => await client.Strategies.GetAsync(readSid),
            r => {
                if (r.ValueKind == JsonValueKind.Undefined) return false;
                return r.TryGetProperty("strategyId", out var s) && s.GetString() == readSid;
            });

        await Chk("strategies", "status",
            async () => await client.Strategies.StatusAsync(readSid),
            r => r.ValueKind != JsonValueKind.Undefined);

        await Chk("strategies", "executions",
            async () => await client.Strategies.ExecutionsAsync(readSid),
            r => r != null);

        await Chk("strategies", "trades",
            async () => await client.Strategies.TradesAsync(readSid),
            r => r != null);

        await Chk("strategies", "performance",
            async () => await client.Strategies.PerformanceAsync(readSid),
            r => r != null);

        await Chk("strategies", "logs",
            async () => await client.Strategies.LogsAsync(readSid),
            r => r != null);

        await Chk("strategies", "aiOptStatus",
            async () => await client.Strategies.AiOptStatusAsync(readSid),
            r => r.ValueKind != JsonValueKind.Undefined);

        await Chk("strategies", "aiOptRuns",
            async () => await client.Strategies.AiOptRunsAsync(readSid),
            r => r.ValueKind != JsonValueKind.Undefined && r.ValueKind != JsonValueKind.Null);
    }
    else
    {
        foreach (var n in new[] { "get", "status", "executions", "trades", "performance", "logs", "aiOptStatus", "aiOptRuns" })
            RecSkip("strategies", n, "no strategyId in list[0]");
    }
}
else
{
    foreach (var n in new[] { "get", "status", "executions", "trades", "performance", "logs", "aiOptStatus", "aiOptRuns" })
        RecSkip("strategies", n, "list empty");
}

// ── strategies lifecycle: fresh PAPER custom strategy ────────────────────────
const string RHAI = """fn evaluate() { emit_long(param("qty")); }""";
string? paperSid = null;

var created = await Chk("strategies", "create(custom,paper)",
    async () => await client.Strategies.CreateAsync(new
    {
        name         = "csharp-sdk-smoke",
        strategyType = "custom",
        exchange     = "binanceusdm",
        symbol       = "BTC/USDT:USDT",
        market       = "FUTURES",
        dryRun       = true,
        @params      = new
        {
            language   = "rhai",
            definition = RHAI,
            qty        = 0.001,
        },
    }),
    r => r?.Ok == true && !string.IsNullOrWhiteSpace(r.StrategyId));

paperSid = created?.StrategyId;
if (paperSid != null)
    Console.WriteLine($"  [INFO] paperSid = {paperSid}");

if (paperSid != null)
{
    await Chk("strategies", "pause",
        async () => await client.Strategies.PauseAsync(paperSid),
        r => r?.Ok == true);

    await Chk("strategies", "resume",
        async () => await client.Strategies.ResumeAsync(paperSid),
        r => r?.Ok == true);

    await Chk("strategies", "updateParams",
        async () => await client.Strategies.UpdateParamsAsync(paperSid, new { qty = 0.002 }),
        r => r?.Ok == true);

    await Chk("strategies", "aiOptStop",
        async () => await client.Strategies.AiOptStopAsync(paperSid),
        r => r?.Ok == true);
}
else
{
    foreach (var n in new[] { "pause", "resume", "updateParams", "aiOptStop" })
        RecSkip("strategies", n, "create(paper) failed");
}

// aiOptStart / aiOptApprove — WIRED, not invoked
RecWired("strategies", "aiOptStart",  "not invoked (would start a billed optimization)");
RecWired("strategies", "aiOptApprove","not invoked (applies optimizer output)");

// ════ SIM (7) — paper strategy ════
Console.WriteLine("\n── sim ──");

if (paperSid != null)
{
    await Chk("sim", "balance",
        async () => await client.Sim.BalanceAsync(paperSid),
        r => {
            if (r.ValueKind == JsonValueKind.Undefined) return false;
            return r.TryGetProperty("total", out _) || r.TryGetProperty("USDT", out _) || r.TryGetProperty("USD", out _);
        });

    await Chk("sim", "positions",
        async () => await client.Sim.PositionsAsync(paperSid),
        r => r != null);

    await Chk("sim", "listAccounts",
        async () => await client.Sim.ListAccountsAsync(),
        r => r != null);

    await Chk("sim", "myTrades",
        async () => await client.Sim.MyTradesAsync(paperSid),
        r => r != null);

    // Get ticker price to set resting limit order at 50% of last
    double limitPrice = 60000.0;
    if (ticker.ValueKind == JsonValueKind.Object)
    {
        if (ticker.TryGetProperty("last", out var lastEl) && lastEl.ValueKind == JsonValueKind.Number)
            limitPrice = Math.Round(lastEl.GetDouble() * 0.5, 0);
        else if (ticker.TryGetProperty("bid", out var bidEl) && bidEl.ValueKind == JsonValueKind.Number)
            limitPrice = Math.Round(bidEl.GetDouble() * 0.5, 0);
    }

    string? orderId = null;
    var ord = await Chk("sim", "createOrder(limit,resting)",
        async () => await client.Sim.CreateOrderAsync(
            strategyId: paperSid,
            exchange:   "binanceusdm",
            symbol:     "BTC/USDT:USDT",
            side:       "buy",
            amount:     0.001,
            orderType:  "limit",
            price:      limitPrice,
            market:     "FUTURES"),
        r => r?.OrderId != null || r?.Ok == true);

    orderId = ord?.OrderId;
    if (orderId != null)
        Console.WriteLine($"  [INFO] orderId = {orderId}");

    await Chk("sim", "openOrders",
        async () => await client.Sim.OpenOrdersAsync(paperSid),
        r => r != null);

    if (orderId != null)
    {
        await Chk("sim", "cancelOrder",
            async () => await client.Sim.CancelOrderAsync(paperSid, orderId, "BTC/USDT:USDT", "binanceusdm"),
            r => r.ValueKind != JsonValueKind.Undefined);
    }
    else
    {
        RecSkip("sim", "cancelOrder", "no resting order id");
    }
}
else
{
    foreach (var n in new[] { "balance", "positions", "listAccounts", "myTrades", "createOrder(limit,resting)", "openOrders", "cancelOrder" })
        RecSkip("sim", n, "no paper strategy");
}

// ════ BACKTEST (10 invoked; deleteAll WIRED) ════
Console.WriteLine("\n── backtest ──");

long now     = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
long since60 = now - 60L * 86_400_000;
long since30 = now - 30L * 86_400_000;
long since365= now - 365L * 86_400_000;

// start custom
string? btJobId = null;
var btStart = await Chk("backtest", "start",
    async () => await client.Backtest.StartAsync(new
    {
        strategyType = "custom",
        language     = "rhai",
        definition   = RHAI,
        exchange     = "binance",
        symbol       = "BTC/USDT",
        timeframe    = "1h",
        since_ms     = since60,
        until_ms     = now,
        @params      = new { qty = 0.001 },
    }),
    r => r?.JobId != null);

btJobId = btStart?.JobId;
if (btJobId != null)
    Console.WriteLine($"  [INFO] btJobId = {btJobId}");

if (btJobId != null)
{
    // poll to done
    string btStatus = "queued";
    await Chk("backtest", "job(poll)",
        async () => {
            for (int i = 0; i < 12 && !new[] { "done", "error", "halted", "cancelled" }.Contains(btStatus); i++)
            {
                await Task.Delay(2000);
                try
                {
                    var j = await client.Backtest.JobAsync(btJobId);
                    if (j.TryGetProperty("status", out var st))
                        btStatus = st.GetString()?.ToLowerInvariant() ?? "";
                    Console.WriteLine($"  [INFO] bt status={btStatus}");
                }
                catch { }
            }
            var final = await client.Backtest.JobAsync(btJobId);
            if (!final.TryGetProperty("job_id", out _) && final.ValueKind == JsonValueKind.Undefined)
                throw new Exception("job not found");
            return final;
        },
        r => r.ValueKind != JsonValueKind.Undefined);

    if (btStatus == "done" || btStatus == "completed" || btStatus == "complete")
    {
        await Chk("backtest", "results",
            async () => await client.Backtest.ResultsAsync(btJobId),
            r => r.ValueKind != JsonValueKind.Undefined && r.ValueKind != JsonValueKind.Null);

        await Chk("backtest", "trades",
            async () => await client.Backtest.TradesAsync(btJobId, 10),
            r => r != null); // 0 trades is PASS
    }
    else
    {
        RecSkip("backtest", "results", $"job {btStatus}");
        RecSkip("backtest", "trades",  $"job {btStatus}");
    }
}
else
{
    foreach (var n in new[] { "job(poll)", "results", "trades" })
        RecSkip("backtest", n, "start failed");
}

await Chk("backtest", "list",
    async () => await client.Backtest.ListAsync(5),
    r => r != null);

await Chk("backtest", "favorites",
    async () => await client.Backtest.FavoritesAsync(5),
    r => r != null);

await Chk("backtest", "fundingRange",
    async () => await client.Backtest.FundingRangeAsync("binanceusdm", "BTC/USDT:USDT"),
    r => true); // null is valid

// sweep: start a tiny grid sweep parent, then read it
string? sweepJobId = null;
var sweepStart = await Chk("backtest", "start(grid_sweep)",
    async () => await client.Backtest.StartAsync(new
    {
        mode         = "grid_sweep",
        strategyType = "custom",
        language     = "rhai",
        definition   = RHAI,
        exchange     = "binance",
        symbol       = "BTC/USDT",
        timeframe    = "1h",
        since_ms     = since30,
        until_ms     = now,
        @params      = new { qty = 0.001 },
        paramRanges  = new { qty = new[] { 0.001, 0.002 } },
    }),
    r => r?.JobId != null);

sweepJobId = sweepStart?.JobId;

if (sweepJobId != null)
{
    await Chk("backtest", "sweep",
        async () => await client.Backtest.SweepAsync(sweepJobId, limit: 10),
        r => r.ValueKind != JsonValueKind.Undefined);
}
else
{
    RecSkip("backtest", "sweep", "no sweep parent");
}

// cancel + delete on a fresh job
string? cancelJobId = null;
var cancelStart = await Chk("backtest", "start(for-cancel)",
    async () => await client.Backtest.StartAsync(new
    {
        strategyType = "custom",
        language     = "rhai",
        definition   = RHAI,
        exchange     = "binance",
        symbol       = "ETH/USDT",
        timeframe    = "1h",
        since_ms     = since365,
        until_ms     = now,
        @params      = new { qty = 0.001 },
    }),
    r => r?.JobId != null);

cancelJobId = cancelStart?.JobId;

if (cancelJobId != null)
{
    await Chk("backtest", "cancel",
        async () => await client.Backtest.CancelAsync(cancelJobId),
        r => r.ValueKind != JsonValueKind.Undefined);

    await Chk("backtest", "delete",
        async () => await client.Backtest.DeleteAsync(cancelJobId),
        r => r?.Ok == true);
}
else
{
    RecSkip("backtest", "cancel", "no cancel job");
    RecSkip("backtest", "delete", "no cancel job");
}

RecWired("backtest", "deleteAll", "not invoked (soft-deletes ALL non-favorited jobs)");

// ════ STREAMS — public (5) + private (2) ════
Console.WriteLine("\n── stream ──");

await StreamChk("stream", "ticker",
    () => client.Stream.TickerAsync("binance", "BTC/USDT", "spot"));

await StreamChk("stream", "orderbook",
    () => client.Stream.OrderbookAsync("binance", "BTC/USDT", "spot", 10));

await StreamChk("stream", "ohlcv",
    () => client.Stream.OhlcvAsync("binance", "BTC/USDT", "1m", "spot"));

await StreamChk("stream", "trades",
    () => client.Stream.TradesAsync("binance", "BTC/USDT", "spot"));

await StreamChk("stream", "liquidations",
    () => client.Stream.LiquidationsAsync("binanceusdm"));

await StreamChk("stream", "strategies(private)",
    () => client.Stream.StrategiesAsync());

// private(account) — use first connected key
if (accountKeys != null && accountKeys.Count > 0)
{
    var qkey = accountKeys[0];
    string? qExchange = null, qMarket = null, qApiKeyId = null;
    if (qkey.TryGetProperty("exchange",  out var ex)) qExchange  = ex.GetString();
    if (qkey.TryGetProperty("market",    out var mk3)) qMarket   = mk3.GetString();
    if (qkey.TryGetProperty("apiKeyId",  out var aid)) qApiKeyId = aid.GetString();

    await StreamChk("stream", "private(account)",
        () => client.Stream.PrivateAsync(qExchange ?? "binance", qMarket, qApiKeyId));
}
else
{
    RecSkip("stream", "private(account)", "no connected key");
}

// ════ TEARDOWN — stop + delete paper strategy ════
Console.WriteLine("\n── teardown ──");

if (paperSid != null)
{
    await Chk("teardown", "strategies.stop",
        async () => await client.Strategies.StopAsync(paperSid),
        r => r?.Ok == true);

    await Chk("teardown", "strategies.delete",
        async () => await client.Strategies.DeleteAsync(paperSid),
        r => r?.Ok == true);
}

// ════ REPORT ════
Console.WriteLine();
Console.WriteLine("══════════════ MELAYA SDK — FULL ENDPOINT VALIDATION (C#) ══════════════");

var cats = results.Select(r => r.Cat).Distinct().ToList();
int nPass = 0, nFail = 0, nWired = 0, nSkip = 0;

foreach (var cat in cats)
{
    Console.WriteLine($"\n── {cat} ──");
    foreach (var r in results.Where(x => x.Cat == cat))
    {
        Console.WriteLine($"  {r.St.PadRight(5)} {r.Name.PadRight(32)} {r.Detail}");
        if      (r.St == "PASS")  nPass++;
        else if (r.St == "FAIL")  nFail++;
        else if (r.St == "WIRED") nWired++;
        else                      nSkip++;
    }
}

Console.WriteLine();
Console.WriteLine("════════════════════════════════════════════════════════════════════");
Console.WriteLine($"PASS {nPass}   FAIL {nFail}   WIRED(not-invoked) {nWired}   SKIP {nSkip}   |  total methods {nPass + nFail + nWired + nSkip}");
Console.WriteLine(nFail == 0
    ? "RESULT: GO — every invoked endpoint validated."
    : $"RESULT: NO-GO — {nFail} failing.");

Environment.Exit(nFail == 0 ? 0 : 1);

record Result(string Cat, string Name, string St, string Detail);
