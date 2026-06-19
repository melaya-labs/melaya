// Melaya Java SDK -- quickstart / smoke test.
//
//   (add org.melaya:melaya-sdk to your build)
//   MELAYA_API_KEY=mk_... java examples/Java.java
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.melaya.*;

public class Java {
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("MELAYA_API_KEY");
        if (apiKey == null) throw new IllegalStateException("Set MELAYA_API_KEY=mk_...");
        Melaya m = new Melaya(apiKey);

        // 1. How many venues are live?
        System.out.println("exchanges: " + m.market().listExchanges().size());

        // 2. Normalized REST ticker
        JsonNode t = m.market().ticker("binance", "BTC/USDT", "spot");
        System.out.printf("BTC/USDT last=%s bid=%s ask=%s%n", t.get("last"), t.get("bid"), t.get("ask"));

        // 3. Order book
        JsonNode book = m.market().orderbook("bybit", "BTC/USDT", "spot", 5);
        System.out.println("top bid: " + book.get("bids").get(0) + "  top ask: " + book.get("asks").get(0));

        // 4. Live stream -- print up to 3 ticker frames then stop
        try (MelayaStream s = m.stream().ticker("binance", "BTC/USDT", "spot")) {
            for (int i = 0; i < 3; i++) {
                JsonNode frame = s.nextFrame(10, TimeUnit.SECONDS);
                if (frame == null) break;
                System.out.println("stream: " + frame.get("last"));
            }
        }

        // 5. Account -- connected keys + tier usage
        System.out.println("connected keys: " + m.account().keys().size());
        System.out.println("tier: " + m.account().usage().get("tier"));

        // 6. Paper trading -- launch a paper strategy (no exchange key needed) and
        //    round-trip a synthetic order through the sim broker. Nothing hits a venue.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "SDK example (paper)");
        body.put("strategyType", "custom");      // custom Rhai definition
        body.put("exchange", "binanceusdm");
        body.put("symbol", "BTC/USDT:USDT");
        body.put("market", "FUTURES");
        body.put("dryRun", true);                // dryRun:false + apiKeyId => REAL orders
        body.put("params", Map.of("language", "rhai", "definition", "fn evaluate() { emit_long(param("qty")); }", "qty", 0.001));
        String sid = m.strategies().create(body).get("strategyId").asText();
        System.out.println("launched paper strategy " + sid);
        JsonNode fill = m.sim().createOrder(sid, "binanceusdm", "BTC/USDT:USDT",
                "buy", 0.001, "market", null, "FUTURES");
        System.out.println("paper fill @ " + fill.get("fill_price"));
        System.out.println("paper balance: " + m.sim().balance(sid, null));
        m.strategies().stop(sid);

        // 7. Backtest on the Rust engine
        Map<String, Object> bt = new LinkedHashMap<>();
        bt.put("strategyType", "custom");
        bt.put("exchange", "binance");
        bt.put("symbol", "BTC/USDT");
        bt.put("timeframe", "1h");
        bt.put("language", "rhai");
        bt.put("definition", "fn evaluate() { emit_long(param("qty")); }");
        bt.put("params", Map.of("qty", 0.001));
        System.out.println("backtest job " + m.backtest().start(bt).get("job_id") + " started");
        System.out.println("done");
    }
}
