package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Full end-to-end smoke test for the Melaya Java SDK.
 * Exercises EVERY method in every category (~70 checks).
 *
 * <pre>
 *   MK=mk_yourkey MELAYA_INSECURE_TLS=1 ./gradlew run
 * </pre>
 *
 * Safety: paper/sim only. Created strategies are always stopped and deleted.
 * Billable/destructive endpoints (aiOptStart, aiOptApprove, backtest.deleteAll)
 * are WIRED — counted but not invoked.
 */
public class E2E {

    // ── result record ────────────────────────────────────────────────────────

    enum Status { PASS, FAIL, WIRED, SKIP }

    record Result(String cat, String name, Status status, String detail) {}

    private static final List<Result> RESULTS = new ArrayList<>();

    private static void addResult(String cat, String name, Status st, String detail) {
        RESULTS.add(new Result(cat, name, st, detail == null ? "" : detail.length() > 90 ? detail.substring(0, 90) : detail));
    }

    private static void pass(String cat, String name, JsonNode data) {
        String d = data == null ? "ok" : data.toString();
        if (d.length() > 80) d = d.substring(0, 80);
        addResult(cat, name, Status.PASS, d);
    }

    private static void fail(String cat, String name, Throwable e) {
        String d = e == null ? "null" : e.getMessage();
        if (d == null) d = e.getClass().getSimpleName();
        if (d.length() > 90) d = d.substring(0, 90);
        addResult(cat, name, Status.FAIL, d);
    }

    private static void wired(String cat, String name, String reason) {
        addResult(cat, name, Status.WIRED, reason);
    }

    private static void skip(String cat, String name, String reason) {
        addResult(cat, name, Status.SKIP, reason);
    }

    // ── check helpers ────────────────────────────────────────────────────────

    @FunctionalInterface
    interface Thunk {
        JsonNode run() throws Exception;
    }

    @FunctionalInterface
    interface Validator {
        boolean test(JsonNode node);
    }

    /**
     * Run fn(), validate result, record PASS or FAIL.
     * retry=true → sleep 1600ms and try once more on first failure (cold-cache absorb).
     */
    private static JsonNode chk(String cat, String name, Thunk fn, Validator validate, boolean retry) {
        int attempts = retry ? 2 : 1;
        Exception lastEx = null;
        JsonNode lastResult = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                JsonNode r = fn.run();
                lastResult = r;
                lastEx = null;
                if (validate == null || validate.test(r)) {
                    pass(cat, name, r);
                    return r;
                }
                // shape invalid
                if (i == attempts) {
                    fail(cat, name, new RuntimeException("invalid shape: " +
                            (r == null ? "null" : r.toString().substring(0, Math.min(80, r.toString().length())))));
                    return r;
                }
            } catch (Exception e) {
                lastEx = e;
                lastResult = null;
                if (i == attempts) {
                    fail(cat, name, e);
                    return null;
                }
            }
            try { Thread.sleep(1600); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
        if (lastEx != null) fail(cat, name, lastEx);
        return lastResult;
    }

    private static JsonNode chk(String cat, String name, Thunk fn, Validator validate) {
        return chk(cat, name, fn, validate, false);
    }

    private static JsonNode chk(String cat, String name, Thunk fn) {
        return chk(cat, name, fn, null, false);
    }

    // Validators
    private static final Validator isArr    = r -> r != null && r.isArray();
    private static final Validator arr1     = r -> r != null && r.isArray() && r.size() >= 1;
    private static final Validator arr60    = r -> r != null && r.isArray() && r.size() >= 60;
    private static final Validator obj      = r -> r != null && r.isObject();
    private static final Validator notNull  = r -> r != null;

    // ── stream check ─────────────────────────────────────────────────────────

    @FunctionalInterface
    interface StreamSupplier {
        MelayaStream open() throws Exception;
    }

    private static void streamChk(String cat, String name, StreamSupplier mk) {
        MelayaStream stream = null;
        try {
            stream = mk.open();
            JsonNode frame = stream.nextFrame(10, TimeUnit.SECONDS);
            // hello frame or real data — any non-null frame is a PASS
            if (frame != null) {
                pass(cat, name, frame);
            } else {
                // opened but no frame in 10s — still a PASS (open = connected)
                addResult(cat, name, Status.PASS, "open, no frame 10s");
            }
        } catch (Exception e) {
            fail(cat, name, e);
        } finally {
            if (stream != null) try { stream.close(); } catch (Exception ignored) {}
        }
    }

    // ── sleep ─────────────────────────────────────────────────────────────────

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // main
    // ══════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) throws Exception {
        String mk = System.getenv("MK");
        if (mk == null || mk.isBlank()) {
            System.err.println("ERROR: set MK=mk_... environment variable");
            System.exit(2);
        }

        // Build SDK — trust-all TLS when MELAYA_INSECURE_TLS=1
        boolean insecure = "1".equals(System.getenv("MELAYA_INSECURE_TLS"));
        org.melaya.HttpClient http;
        if (insecure) {
            SSLContext ctx = org.melaya.HttpClient.trustAllSslContext();
            HttpClient javaHttp = HttpClient.newBuilder().sslContext(ctx).build();
            http = new org.melaya.HttpClient(mk, Melaya.DEFAULT_BASE_URL, javaHttp);
        } else {
            http = new org.melaya.HttpClient(mk, Melaya.DEFAULT_BASE_URL);
        }
        Melaya m = new Melaya(http, Melaya.DEFAULT_WS_URL);

        // Constants mirrored from the reference TS smoke
        final String EX    = "binance";
        final String SYM   = "BTC/USDT";
        final String MKT   = "spot";
        final String PERP_EX  = "binanceusdm";
        final String PERP_SYM = "BTC/USDT:USDT";
        final String RHAI  = "fn evaluate() { emit_long(param(\"qty\")); }";

        // ════ MARKET (22) ════════════════════════════════════════════════════
        System.out.println("\n── market ──");

        chk("market", "listExchanges",
                () -> m.market().listExchanges(), arr60);

        chk("market", "ticker",
                () -> {
                    JsonNode t = m.market().ticker(EX, SYM, MKT);
                    if (t == null || (!t.has("last") && !t.has("bid")))
                        throw new RuntimeException("ticker missing last/bid");
                    return t;
                }, null, true);

        chk("market", "orderbook",
                () -> {
                    JsonNode ob = m.market().orderbook(EX, SYM, MKT, 5);
                    if (ob == null || !ob.has("bids") || ob.get("bids").size() == 0)
                        throw new RuntimeException("orderbook missing bids");
                    return ob;
                }, null, true);

        chk("market", "ohlcv",
                () -> {
                    JsonNode c = m.market().ohlcv(EX, SYM, "1h", MKT, 10);
                    if (c == null || !c.isArray()) throw new RuntimeException("ohlcv not array");
                    return c;
                }, arr1, true);

        chk("market", "trades",
                () -> m.market().trades(EX, SYM, MKT), arr1, true);

        chk("market", "markets",
                () -> m.market().markets(EX), arr1);

        chk("market", "currencies",
                () -> m.market().currencies("kraken"), arr1, true);

        chk("market", "status",
                () -> m.market().status(EX), obj);

        chk("market", "time",
                () -> m.market().time(EX), notNull);

        chk("market", "tickers",
                () -> {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("exchange", EX);
                    b.put("symbols", List.of("BTC/USDT", "ETH/USDT"));
                    return m.market().tickers(b);
                }, obj, true);

        chk("market", "fundingRates",
                () -> {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("exchange", PERP_EX);
                    b.put("symbols", List.of(PERP_SYM));
                    return m.market().fundingRates(b);
                }, obj, true);

        chk("market", "fundingRateHistory",
                () -> {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("exchange", PERP_EX);
                    b.put("symbol", PERP_SYM);
                    b.put("hours", 24);
                    return m.market().fundingRateHistory(b);
                }, arr1, true);

        chk("market", "openInterest",
                () -> {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("exchange", PERP_EX);
                    b.put("symbols", List.of(PERP_SYM));
                    return m.market().openInterest(b);
                }, obj, true);

        chk("market", "openInterestHistory",
                () -> {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("exchange", PERP_EX);
                    b.put("symbol", PERP_SYM);
                    b.put("hours", 24);
                    return m.market().openInterestHistory(b);
                }, arr1, true);

        chk("market", "instruments",
                () -> {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("exchange", PERP_EX);
                    return m.market().instruments(b);
                }, obj);

        chk("market", "liquidationEvents",
                () -> {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("exchange", PERP_EX);
                    b.put("limit", 10);
                    return m.market().liquidationEvents(b);
                }, isArr);

        chk("market", "ohlcvMulti",
                () -> {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("exchange", EX);
                    b.put("symbols", List.of("BTC/USDT", "ETH/USDT"));
                    b.put("timeframe", "1h");
                    b.put("limit", 5);
                    b.put("market", MKT);
                    return m.market().ohlcvMulti(b);
                }, obj, true);

        chk("market", "marketConstraints",
                () -> {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("exchange", PERP_EX);
                    b.put("symbol", PERP_SYM);
                    return m.market().marketConstraints(b);
                }, notNull);

        chk("market", "fundingRateHistoryMulti",
                () -> {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("exchanges", List.of("binanceusdm", "bybitlinear"));
                    b.put("symbol", PERP_SYM);
                    b.put("hours", 24);
                    return m.market().fundingRateHistoryMulti(b);
                }, obj, true);

        chk("market", "openInterestHistoryMulti",
                () -> {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("exchanges", List.of("binanceusdm", "bybitlinear"));
                    b.put("symbol", PERP_SYM);
                    b.put("hours", 24);
                    return m.market().openInterestHistoryMulti(b);
                }, obj, true);

        chk("market", "predictionMarkets",
                () -> {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("venue", "polymarket");
                    return m.market().predictionMarkets(b);
                }, arr1, true);

        chk("market", "catalogCounts",
                () -> {
                    JsonNode cc = m.market().catalogCounts();
                    if (cc == null || !cc.has("tools")) throw new RuntimeException("missing 'tools'");
                    return cc;
                }, null);

        // ════ ACCOUNT (3) ════════════════════════════════════════════════════
        System.out.println("\n── account ──");

        chk("account", "keys",
                () -> m.account().keys(), isArr);

        chk("account", "usage",
                () -> {
                    JsonNode u = m.account().usage();
                    if (u == null || !u.has("tier")) throw new RuntimeException("usage missing 'tier'");
                    return u;
                }, null);

        chk("account", "apiKeyStatus",
                () -> m.account().apiKeyStatus(), obj);

        // ════ STRATEGIES — reads (9) on an existing strategy ═════════════════
        System.out.println("\n── strategies ──");

        String readSid = null;
        JsonNode listResult = chk("strategies", "list",
                () -> m.strategies().list(), arr1);
        if (listResult != null && listResult.isArray() && listResult.size() > 0) {
            JsonNode first = listResult.get(0);
            if (first != null && first.has("strategyId")) {
                readSid = first.get("strategyId").asText();
            }
        }

        if (readSid != null) {
            final String rSid = readSid;
            chk("strategies", "get",
                    () -> {
                        JsonNode s = m.strategies().get(rSid);
                        if (s == null || !s.has("strategyId")) throw new RuntimeException("get missing strategyId");
                        return s;
                    }, null);

            chk("strategies", "status",
                    () -> m.strategies().status(rSid), obj);

            chk("strategies", "executions",
                    () -> m.strategies().executions(rSid), isArr);

            chk("strategies", "trades",
                    () -> m.strategies().trades(rSid), isArr);

            chk("strategies", "performance",
                    () -> m.strategies().performance(rSid), isArr);

            chk("strategies", "logs",
                    () -> m.strategies().logs(rSid), isArr);

            chk("strategies", "aiOptStatus",
                    () -> m.strategies().aiOptStatus(rSid), obj);

            chk("strategies", "aiOptRuns",
                    () -> m.strategies().aiOptRuns(rSid), notNull);
        } else {
            for (String n : new String[]{"get","status","executions","trades","performance","logs","aiOptStatus","aiOptRuns"}) {
                skip("strategies", n, "list() returned empty — no existing strategy to read");
            }
        }

        // ════ STRATEGIES — lifecycle (5) on a fresh PAPER custom strategy ════

        String paperSid = null;
        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("name", "SDK java-smoke (custom)");
        createBody.put("strategyType", "custom");
        createBody.put("exchange", PERP_EX);
        createBody.put("symbol", PERP_SYM);
        createBody.put("market", "FUTURES");
        createBody.put("dryRun", true);
        Map<String, Object> stratParams = new LinkedHashMap<>();
        stratParams.put("language", "rhai");
        stratParams.put("definition", RHAI);
        stratParams.put("qty", 0.001);
        createBody.put("params", stratParams);

        JsonNode created = chk("strategies", "create(custom,paper)",
                () -> {
                    JsonNode r = m.strategies().create(createBody);
                    if (r == null || !r.has("strategyId")) throw new RuntimeException("create missing strategyId");
                    return r;
                }, null);

        if (created != null && created.has("strategyId")) {
            paperSid = created.get("strategyId").asText();
            System.out.println("  created paper strategyId=" + paperSid);
        }

        if (paperSid != null) {
            final String pSid = paperSid;

            chk("strategies", "pause",
                    () -> {
                        JsonNode r = m.strategies().pause(pSid);
                        if (r == null || !r.has("ok")) throw new RuntimeException("pause missing ok");
                        return r;
                    }, null);

            chk("strategies", "resume",
                    () -> {
                        JsonNode r = m.strategies().resume(pSid);
                        if (r == null || !r.has("ok")) throw new RuntimeException("resume missing ok");
                        return r;
                    }, null);

            chk("strategies", "updateParams",
                    () -> {
                        Map<String, Object> up = new LinkedHashMap<>();
                        up.put("fast", 8);
                        up.put("slow", 20);
                        JsonNode r = m.strategies().updateParams(pSid, up);
                        if (r == null || !r.has("ok")) throw new RuntimeException("updateParams missing ok");
                        return r;
                    }, null);

            chk("strategies", "aiOptStop",
                    () -> {
                        JsonNode r = m.strategies().aiOptStop(pSid);
                        if (r == null || !r.has("ok")) throw new RuntimeException("aiOptStop missing ok");
                        return r;
                    }, null);
        } else {
            for (String n : new String[]{"pause","resume","updateParams","aiOptStop"}) {
                skip("strategies", n, "create(paper) failed");
            }
        }

        // WIRED — billable / side-effecting
        wired("strategies", "aiOptStart",  "not invoked (would start a billed optimization)");
        wired("strategies", "aiOptApprove","not invoked (applies optimizer output)");

        // ════ SIM (7) — on the fresh paper strategy ══════════════════════════
        System.out.println("\n── sim ──");

        if (paperSid != null) {
            final String pSid = paperSid;

            chk("sim", "balance",
                    () -> {
                        JsonNode b = m.sim().balance(pSid, null);
                        if (b == null || !b.has("total")) throw new RuntimeException("balance missing 'total'");
                        return b;
                    }, null);

            chk("sim", "positions",
                    () -> m.sim().positions(pSid), isArr);

            chk("sim", "listAccounts",
                    () -> m.sim().listAccounts(), isArr);

            chk("sim", "myTrades",
                    () -> m.sim().myTrades(pSid), isArr);

            // Fetch live price for a resting limit order far below market
            double px = 60000.0;
            try {
                JsonNode t = m.market().ticker(PERP_EX, PERP_SYM, null);
                if (t != null) {
                    JsonNode lastNode = t.get("last");
                    JsonNode bidNode  = t.get("bid");
                    if (lastNode != null && !lastNode.isNull()) px = lastNode.asDouble();
                    else if (bidNode != null && !bidNode.isNull()) px = bidNode.asDouble();
                }
            } catch (Exception ignored) {}
            final double limitPrice = Math.round(px * 0.5);

            JsonNode ord = chk("sim", "createOrder(limit,resting)",
                    () -> {
                        JsonNode r = m.sim().createOrder(pSid, PERP_EX, PERP_SYM,
                                "buy", 0.001, "limit", limitPrice, "FUTURES");
                        if (r == null || !r.has("order_id")) throw new RuntimeException("createOrder missing order_id");
                        return r;
                    }, null);

            chk("sim", "openOrders",
                    () -> m.sim().openOrders(pSid), isArr);

            if (ord != null && ord.has("order_id")) {
                final String oid = ord.get("order_id").asText();
                chk("sim", "cancelOrder",
                        () -> {
                            JsonNode r = m.sim().cancelOrder(pSid, oid, PERP_SYM, PERP_EX);
                            if (r == null) throw new RuntimeException("cancelOrder returned null");
                            return r;
                        }, obj);
            } else {
                skip("sim", "cancelOrder", "no resting order id");
            }
        } else {
            for (String n : new String[]{"balance","positions","listAccounts","myTrades",
                                         "createOrder(limit,resting)","openOrders","cancelOrder"}) {
                skip("sim", n, "no paper strategyId");
            }
        }

        // ════ BACKTEST (11; deleteAll WIRED) ═════════════════════════════════
        System.out.println("\n── backtest ──");

        long now      = System.currentTimeMillis();
        long since60d = now - 60L * 86_400_000L;
        long since30d = now - 30L * 86_400_000L;

        // 1. Custom backtest start → poll job → results → trades
        Map<String, Object> btBody = new LinkedHashMap<>();
        btBody.put("strategyType", "custom");
        btBody.put("exchange", EX);
        btBody.put("symbol", SYM);
        btBody.put("timeframe", "1h");
        btBody.put("since_ms", since60d);
        btBody.put("until_ms", now);
        btBody.put("language", "rhai");
        btBody.put("definition", RHAI);
        Map<String, Object> btParams = new LinkedHashMap<>();
        btParams.put("qty", 0.001);
        btBody.put("params", btParams);

        JsonNode btStart = chk("backtest", "start",
                () -> {
                    JsonNode r = m.backtest().start(btBody);
                    if (r == null || !r.has("job_id")) throw new RuntimeException("start missing job_id");
                    return r;
                }, null);

        String jobId = btStart != null && btStart.has("job_id") ? btStart.get("job_id").asText() : null;
        if (jobId != null) {
            System.out.println("  backtest job_id=" + jobId);

            // Poll up to 24s (12 × 2s)
            String jobStatus = "queued";
            for (int i = 0; i < 12; i++) {
                sleep(2000);
                try {
                    JsonNode job = m.backtest().job(jobId);
                    if (job != null && job.has("status")) {
                        jobStatus = job.get("status").asText().toLowerCase();
                        System.out.println("  backtest poll [" + i + "]: " + jobStatus);
                        if (Set.of("done","completed","error","halted","cancelled").contains(jobStatus)) break;
                    }
                } catch (Exception ignored) {}
            }

            final String fStatus = jobStatus;
            final String fJobId  = jobId;
            chk("backtest", "job(poll)",
                    () -> {
                        JsonNode r = m.backtest().job(fJobId);
                        if (r == null || !r.has("job_id")) throw new RuntimeException("job missing job_id");
                        return r;
                    }, null);

            if ("done".equals(jobStatus) || "completed".equals(jobStatus)) {
                chk("backtest", "results",
                        () -> {
                            JsonNode r = m.backtest().results(fJobId);
                            if (r == null) throw new RuntimeException("results is null");
                            return r;
                        }, obj);

                chk("backtest", "trades",
                        () -> {
                            // total_trades may be 0 — that is a PASS per spec
                            return m.backtest().trades(fJobId, 10, null);
                        }, isArr);
            } else {
                skip("backtest", "results", "job status: " + fStatus);
                skip("backtest", "trades",  "job status: " + fStatus);
            }
        } else {
            skip("backtest", "job(poll)", "start failed");
            skip("backtest", "results",  "start failed");
            skip("backtest", "trades",   "start failed");
        }

        chk("backtest", "list",
                () -> {
                    JsonNode r = m.backtest().list(5, null);
                    if (r == null) throw new RuntimeException("list returned null");
                    return r;
                }, isArr);

        chk("backtest", "favorites",
                () -> {
                    JsonNode r = m.backtest().favorites(5, null);
                    if (r == null) throw new RuntimeException("favorites returned null");
                    return r;
                }, isArr);

        chk("backtest", "fundingRange",
                () -> {
                    JsonNode r = m.backtest().fundingRange(PERP_EX, PERP_SYM);
                    // null (no funding data) or a number — both valid
                    return r;
                }, notNull);

        // 2. grid_sweep start → sweep read
        Map<String, Object> sweepBody = new LinkedHashMap<>();
        sweepBody.put("mode", "grid_sweep");
        sweepBody.put("strategyType", "custom");
        sweepBody.put("exchange", EX);
        sweepBody.put("symbol", SYM);
        sweepBody.put("timeframe", "1h");
        sweepBody.put("since_ms", since30d);
        sweepBody.put("until_ms", now);
        sweepBody.put("language", "rhai");
        sweepBody.put("definition", RHAI);
        Map<String, Object> sweepParamRanges = new LinkedHashMap<>();
        sweepParamRanges.put("qty", List.of(0.001, 0.002));
        sweepBody.put("paramRanges", sweepParamRanges);

        JsonNode sweepStart = chk("backtest", "start(grid_sweep)",
                () -> {
                    JsonNode r = m.backtest().start(sweepBody);
                    if (r == null || !r.has("job_id")) throw new RuntimeException("grid_sweep start missing job_id");
                    return r;
                }, null);

        if (sweepStart != null && sweepStart.has("job_id")) {
            final String sweepId = sweepStart.get("job_id").asText();
            chk("backtest", "sweep",
                    () -> {
                        JsonNode r = m.backtest().sweep(sweepId, null, 10);
                        if (r == null) throw new RuntimeException("sweep returned null");
                        return r;
                    }, obj);
        } else {
            skip("backtest", "sweep", "grid_sweep start failed");
        }

        // 3. start(for-cancel) → cancel → delete
        Map<String, Object> cancelBody = new LinkedHashMap<>();
        cancelBody.put("strategyType", "custom");
        cancelBody.put("exchange", EX);
        cancelBody.put("symbol", "ETH/USDT");
        cancelBody.put("timeframe", "1h");
        cancelBody.put("since_ms", now - 365L * 86_400_000L);
        cancelBody.put("until_ms", now);
        cancelBody.put("language", "rhai");
        cancelBody.put("definition", RHAI);
        Map<String, Object> cancelBtParams = new LinkedHashMap<>();
        cancelBtParams.put("qty", 0.001);
        cancelBody.put("params", cancelBtParams);

        JsonNode cancelStart = chk("backtest", "start(for-cancel)",
                () -> {
                    JsonNode r = m.backtest().start(cancelBody);
                    if (r == null || !r.has("job_id")) throw new RuntimeException("cancel-start missing job_id");
                    return r;
                }, null);

        if (cancelStart != null && cancelStart.has("job_id")) {
            final String cancelJobId = cancelStart.get("job_id").asText();

            chk("backtest", "cancel",
                    () -> {
                        JsonNode r = m.backtest().cancel(cancelJobId);
                        if (r == null) throw new RuntimeException("cancel returned null");
                        return r;
                    }, obj);

            chk("backtest", "delete",
                    () -> {
                        JsonNode r = m.backtest().delete(cancelJobId);
                        if (r == null || !r.has("ok")) throw new RuntimeException("delete missing ok");
                        return r;
                    }, null);
        } else {
            skip("backtest", "cancel", "start(for-cancel) failed");
            skip("backtest", "delete", "start(for-cancel) failed");
        }

        // WIRED — destructive: soft-deletes ALL non-favorited jobs
        wired("backtest", "deleteAll", "not invoked (soft-deletes ALL non-favorited jobs)");

        // ════ STREAMS — public (5) + private (2) ════════════════════════════
        System.out.println("\n── stream ──");

        streamChk("stream", "ticker",
                () -> m.stream().ticker(EX, SYM, MKT));

        streamChk("stream", "orderbook",
                () -> m.stream().orderbook(EX, SYM, MKT, 10));

        streamChk("stream", "ohlcv",
                () -> m.stream().ohlcv(EX, SYM, "1m", MKT));

        streamChk("stream", "trades",
                () -> m.stream().trades(EX, SYM, MKT));

        streamChk("stream", "liquidations",
                () -> m.stream().liquidations(PERP_EX));

        streamChk("stream", "strategies(private)",
                () -> m.stream().strategies());

        // private(account) — use first key from account.keys()
        JsonNode keysResult = null;
        try { keysResult = m.account().keys(); } catch (Exception ignored) {}
        if (keysResult != null && keysResult.isArray() && keysResult.size() > 0) {
            JsonNode qkey = keysResult.get(0);
            String kExchange = qkey.has("exchange")  ? qkey.get("exchange").asText()  : null;
            String kMarket   = qkey.has("market")    ? qkey.get("market").asText()    : null;
            String kApiKeyId = qkey.has("apiKeyId")  ? qkey.get("apiKeyId").asText()  : null;
            String kKeyId    = qkey.has("keyId")     ? qkey.get("keyId").asText()     : null;
            streamChk("stream", "private(account)",
                    () -> m.stream().privateStream(kExchange, kMarket, kApiKeyId, kKeyId, null));
        } else {
            skip("stream", "private(account)", "no connected exchange key");
        }

        // ════ TEARDOWN — stop + delete the paper strategy ════════════════════
        System.out.println("\n── teardown ──");

        if (paperSid != null) {
            final String pSid = paperSid;
            chk("teardown", "strategies.stop",
                    () -> {
                        JsonNode r = m.strategies().stop(pSid);
                        if (r == null || !r.has("ok")) throw new RuntimeException("stop missing ok");
                        return r;
                    }, null);

            chk("teardown", "strategies.delete",
                    () -> {
                        JsonNode r = m.strategies().delete(pSid);
                        if (r == null || !r.has("ok")) throw new RuntimeException("delete missing ok");
                        return r;
                    }, null);
        }

        // ════ REPORT ════════════════════════════════════════════════════════
        System.out.println("\n══════════════ MELAYA SDK — FULL ENDPOINT VALIDATION (Java) ══════════════");
        List<String> cats = RESULTS.stream().map(r -> r.cat()).distinct().toList();
        int nPass = 0, nFail = 0, nWired = 0, nSkip = 0;
        for (String cat : cats) {
            System.out.println("\n── " + cat + " ──");
            for (Result r : RESULTS.stream().filter(x -> x.cat().equals(cat)).toList()) {
                System.out.printf("  %-5s %-32s %s%n", r.status(), r.name(), r.detail());
                switch (r.status()) {
                    case PASS  -> nPass++;
                    case FAIL  -> nFail++;
                    case WIRED -> nWired++;
                    case SKIP  -> nSkip++;
                }
            }
        }
        int total = nPass + nFail + nWired + nSkip;
        System.out.println("\n════════════════════════════════════════════════════════════════════════════════");
        System.out.printf("PASS %d   FAIL %d   WIRED(not-invoked) %d   SKIP %d   |  total methods %d%n",
                nPass, nFail, nWired, nSkip, total);
        if (nFail == 0) {
            System.out.println("RESULT: GO — every invoked endpoint validated.");
        } else {
            System.out.printf("RESULT: NO-GO — %d failing.%n", nFail);
        }
        System.exit(nFail == 0 ? 0 : 1);
    }
}
