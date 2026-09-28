// Unit tests (mock transport) for the event-triggers diagnostics module.
// Uses Node's built-in `node:test` / `node:assert` against the built
// `dist/index.js`, like the other files in this folder.
// Run: npm run build && node --test tests/
import { test } from "node:test";
import assert from "node:assert/strict";
import { Melaya, MelayaError } from "../dist/index.js";

const TID = "3f2b8c1e-5d4a-4e6f-9a7b-1c2d3e4f5a6b";
const BASE = "/api/v1/private/triggers";

/** Raw JSON `Response` (these routes return the bare result, no envelope). */
function rawJson(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json" },
  });
}

/** A client whose `fetch` records every call and answers with `data`. */
function mockClient(data, status = 200) {
  const calls = [];
  const fetchMock = async (url, init) => {
    calls.push({ url: new URL(String(url)), init });
    return rawJson(data, status);
  };
  const melaya = new Melaya({ apiKey: "mk_test_key", fetch: fetchMock });
  return { melaya, calls };
}

function body(call) {
  return call.init.body === undefined ? undefined : JSON.parse(call.init.body);
}

function query(call) {
  return Object.fromEntries(call.url.searchParams.entries());
}

// Each case: method call, expected HTTP method, path, query, body.
const cases = [
  {
    name: "list() with filters",
    run: (t) => t.list({ project: "acme", pipelineName: "digest" }),
    method: "GET", path: BASE, query: { project: "acme", pipelineName: "digest" },
    response: [{ id: TID, name: "Stripe refunds" }],
  },
  {
    name: "list() without filters",
    run: (t) => t.list(),
    method: "GET", path: BASE, query: {},
    response: [],
  },
  {
    name: "get()",
    run: (t) => t.get(TID),
    method: "GET", path: `${BASE}/${TID}`, query: {},
    response: { id: TID, kind: "webhook", webhookUrl: "https://api.melaya.org/api/v1/hooks/abc" },
  },
  {
    name: "deliveries()",
    run: (t) => t.deliveries(TID, { limit: 20 }),
    method: "GET", path: `${BASE}/${TID}/deliveries`, query: { limit: "20" },
    response: [{ id: "d1", verdict: "dispatched", runId: "run_1", timings: { total: 812 } }],
  },
  {
    name: "stats()",
    run: (t) => t.stats(TID, { hours: 48 }),
    method: "GET", path: `${BASE}/${TID}/stats`, query: { hours: "48" },
    response: { hours: 48, byVerdict: { dispatched: { n: 3, p50: 400, p95: 900 } }, filtered: 2, sampled: false },
  },
  {
    name: "pendingApprovals()",
    run: (t) => t.pendingApprovals(TID),
    method: "GET", path: `${BASE}/${TID}/approvals`, query: {},
    response: [{ requestId: "r1", tool: "gmail_send", expiresAt: 1 }],
  },
  {
    name: "test()",
    run: (t) => t.test(TID, { amount: 120, currency: "usd" }),
    method: "POST", path: `${BASE}/${TID}/test`, query: {},
    body: { payload: { amount: 120, currency: "usd" } },
    response: { accepted: true, eventId: "test-1" },
  },
  {
    name: "events() joins verdicts into a comma list",
    run: (t) => t.events({ triggerId: TID, since: 1727500000000, verdicts: ["filtered", "rejected"], limit: 100 }),
    method: "GET", path: `${BASE}/events`,
    query: { triggerId: TID, since: "1727500000000", verdicts: "filtered,rejected", limit: "100" },
    response: { events: [{ triggerId: TID, verdict: "filtered", at: 1 }], scanned: 12, retention: { maxEvents: 500, ttlSec: 86400 } },
  },
  {
    name: "events() without params",
    run: (t) => t.events(),
    method: "GET", path: `${BASE}/events`, query: {},
    response: { events: [], scanned: 0, retention: { maxEvents: 500, ttlSec: 86400 } },
  },
  {
    name: "pollStatus()",
    run: (t) => t.pollStatus(TID),
    method: "GET", path: `${BASE}/${TID}/poll`, query: {},
    response: { synced: true, status: "idle", armed: true, effectiveIntervalSec: 300, tierFloorSec: 300 },
  },
  {
    name: "pollTest() sends dry: true",
    run: (t) => t.pollTest(TID),
    method: "POST", path: `${BASE}/${TID}/poll/test`, query: {},
    body: { dry: true },
    response: { dry: true, ok: true, found: 2, baseline: false, wouldPublish: 1, items: [{ id: "a", preview: "{}" }], samplePayload: {} },
  },
  {
    name: "pollNow() sends dry: false",
    run: (t) => t.pollNow(TID),
    method: "POST", path: `${BASE}/${TID}/poll/test`, query: {},
    body: { dry: false },
    response: { dry: false, queued: true },
  },
  {
    name: "pollSync()",
    run: (t) => t.pollSync(TID),
    method: "POST", path: `${BASE}/${TID}/poll/sync`, query: {},
    body: undefined,
    response: { result: "synced" },
  },
  {
    name: "presets()",
    run: (t) => t.presets(),
    method: "GET", path: `${BASE}/presets`, query: {},
    response: { tier: "forge", tierFloorSec: 120, presets: [], beta: { allowed: true, minTier: "forge" } },
  },
  {
    name: "limits()",
    run: (t) => t.limits(),
    method: "GET", path: `${BASE}/limits`, query: {},
    response: { tierClass: "forge", triggers: { used: 1, cap: 10 }, sources: { used: 0, cap: 2 } },
  },
  {
    name: "sources()",
    run: (t) => t.sources(),
    method: "GET", path: `${BASE}/sources`, query: {},
    response: [{ id: "s1", transport: "sse", hasAuth: true }],
  },
];

for (const c of cases) {
  test(`triggers.${c.name}: method, path, query, body, passthrough`, async () => {
    const { melaya, calls } = mockClient(c.response);
    const result = await c.run(melaya.agents.triggers);

    assert.equal(calls.length, 1);
    const call = calls[0];
    assert.equal(call.init.method, c.method);
    assert.equal(call.url.pathname, c.path);
    assert.deepEqual(query(call), c.query);
    if (c.method === "POST") assert.deepEqual(body(call), c.body);
    assert.equal(call.init.headers.Authorization, "Bearer mk_test_key");
    assert.deepEqual(result, c.response);
  });
}

test("triggers flat alias is the same instance as agents.triggers", () => {
  const { melaya } = mockClient({});
  assert.equal(melaya.triggers, melaya.agents.triggers);
});

test("triggers.test() without a payload sends an empty body object", async () => {
  const { melaya, calls } = mockClient({ accepted: false, eventId: "test-2", reason: "disabled" });
  const result = await melaya.agents.triggers.test(TID);
  assert.deepEqual(body(calls[0]), {});
  assert.equal(result.reason, "disabled");
});

test("triggers.pollTest() returns a failed dry poll instead of throwing", async () => {
  const failed = { dry: true, ok: false, error: "tool_error" };
  const { melaya } = mockClient(failed);
  const result = await melaya.agents.triggers.pollTest(TID);
  assert.deepEqual(result, failed);
});

test("triggers.get() surfaces a 404 as MelayaError", async () => {
  const { melaya } = mockClient({ error: "NOT_FOUND", message: "[trigger_not_found] Trigger not found." }, 404);
  await assert.rejects(melaya.agents.triggers.get(TID), (err) => {
    assert.ok(err instanceof MelayaError);
    assert.equal(err.status, 404);
    return true;
  });
});

test("triggers.get() encodes the id in the path", async () => {
  const { melaya, calls } = mockClient({});
  await melaya.agents.triggers.get("a/b");
  assert.equal(calls[0].url.pathname, `${BASE}/a%2Fb`);
});
