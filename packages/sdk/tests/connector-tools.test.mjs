// Unit tests (mock transport) for `agents.connectorTools` / `melaya.connectorTools`
// (src/connector-tools.ts), against the server contract in
// melaya-platform server/src/routes/connectorToolsApi.ts +
// server/test/connectorToolsApi.test.ts.
//
// No test framework dependency: node:test / node:assert against the built
// dist/index.js, exactly like the other tests/*.test.mjs files.
// Run: npm run build && node --test tests/
import { test } from "node:test";
import assert from "node:assert/strict";
import { Melaya, MelayaError } from "../dist/index.js";

/** A raw JSON `Response` — the connector-tools routes do NOT use the
 *  `{ ok, ...data }` envelope other private routes use, so this does not add
 *  one. */
function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json" },
  });
}

/** A Melaya client whose `fetch` is a recording mock. `handler(url, init, n)`
 *  (n = 1-based call index) returns the `Response` for each call; `calls`
 *  accumulates `{ url, init }` for assertions. */
function mockClient(handler) {
  const calls = [];
  const fetchMock = async (url, init) => {
    calls.push({ url: String(url), init });
    return handler(String(url), init, calls.length);
  };
  const melaya = new Melaya({ apiKey: "mk_test_key", fetch: fetchMock });
  return { melaya, calls };
}

// ── services() ───────────────────────────────────────────────────────────────

test("services() lists connected services and per-service tool counts", async () => {
  const { melaya, calls } = mockClient(() =>
    jsonResponse({
      services: ["gmail"],
      builtIn: "melaya_core",
      toolCounts: { gmail: { readTools: 1, writeTools: 1 } },
    }),
  );

  const r = await melaya.agents.connectorTools.services();
  assert.equal(calls[0].init.method, "GET");
  assert.equal(new URL(calls[0].url).pathname, "/api/v1/private/connector-tools/services");
  assert.deepEqual(r.services, ["gmail"]);
  assert.equal(r.builtIn, "melaya_core");
  assert.deepEqual(r.toolCounts.gmail, { readTools: 1, writeTools: 1 });
});

// ── search() — query encoding ───────────────────────────────────────────────

test("search() encodes q and limit in the query string", async () => {
  const { melaya, calls } = mockClient(() =>
    jsonResponse({ query: "unread email", services: ["gmail", "melaya_core"], tools: [] }),
  );

  await melaya.agents.connectorTools.search("unread email", { limit: 5 });

  assert.equal(calls.length, 1);
  assert.equal(calls[0].init.method, "GET");
  const url = new URL(calls[0].url);
  assert.equal(url.pathname, "/api/v1/private/connector-tools/search");
  assert.equal(url.searchParams.get("q"), "unread email");
  assert.equal(url.searchParams.get("limit"), "5");
});

test("search() omits limit from the query string when not given", async () => {
  const { melaya, calls } = mockClient(() => jsonResponse({ query: "refund", services: [], tools: [] }));

  await melaya.agents.connectorTools.search("refund");

  const url = new URL(calls[0].url);
  assert.equal(url.searchParams.get("q"), "refund");
  assert.equal(url.searchParams.has("limit"), false);
});

// ── describe() ───────────────────────────────────────────────────────────────

test("describe() reads one tool by name", async () => {
  const tool = {
    name: "gmail_send",
    service: "gmail",
    description: "Send an email",
    readOnly: false,
    movesMoney: false,
    params: { to: { type: "string", required: true } },
  };
  const { melaya, calls } = mockClient(() => jsonResponse(tool));

  const r = await melaya.agents.connectorTools.describe("gmail_send");
  assert.equal(new URL(calls[0].url).pathname, "/api/v1/private/connector-tools/tools/gmail_send");
  assert.deepEqual(r, tool);
});

test("describe() throws a MelayaError (404) for an unknown tool", async () => {
  const { melaya } = mockClient(() =>
    jsonResponse({ error: "not_found", message: "Unknown tool, or not unlocked by your connected services." }, 404),
  );

  await assert.rejects(
    () => melaya.agents.connectorTools.describe("nope_tool"),
    (e) => e instanceof MelayaError && e.status === 404 && e.code === "not_found",
  );
});

// ── call() — a read tool runs immediately (200) ─────────────────────────────

test("call() runs a read tool immediately and returns its result", async () => {
  const { melaya, calls } = mockClient(() =>
    jsonResponse({ status: "done", tool: "gmail_list_messages", readOnly: true, result: "3 unread" }),
  );

  const r = await melaya.agents.connectorTools.call("gmail_list_messages", {});

  assert.equal(calls[0].init.method, "POST");
  assert.equal(new URL(calls[0].url).pathname, "/api/v1/private/connector-tools/call");
  const body = JSON.parse(calls[0].init.body);
  assert.deepEqual(body, { tool: "gmail_list_messages", args: {}, approval: "required" });
  assert.equal(r.status, "done");
  assert.equal(r.readOnly, true);
  assert.equal(r.result, "3 unread");
});

test("call() sends approval: none through to the body", async () => {
  const { melaya, calls } = mockClient(() =>
    jsonResponse({ status: "done", tool: "gmail_send", readOnly: false, result: "sent" }),
  );

  await melaya.agents.connectorTools.call("gmail_send", { to: "a@b.c" }, { approval: "none" });

  const body = JSON.parse(calls[0].init.body);
  assert.deepEqual(body, { tool: "gmail_send", args: { to: "a@b.c" }, approval: "none" });
});

// ── call() — a staged write returns its 202 body, not a thrown error ───────

test("call() returns the 202 staged-write body without throwing", async () => {
  const { melaya } = mockClient(() =>
    jsonResponse(
      { status: "pending_approval", tool: "gmail_send", requestId: "req-1", message: "Approve it in the Melaya app." },
      202,
    ),
  );

  const r = await melaya.agents.connectorTools.call("gmail_send", { to: "x@y.z" });
  assert.equal(r.status, "pending_approval");
  assert.equal(r.requestId, "req-1");
});

// ── call() — money-moving tools are refused under both approval modes ──────

test("call() raises a MelayaError for a money-moving tool (403)", async () => {
  const { melaya } = mockClient(() =>
    jsonResponse(
      { error: "money_moving_requires_app_approval", tool: "stripe_create_refund", message: "Tools that move money or trade run only from the Melaya app." },
      403,
    ),
  );

  await assert.rejects(
    () => melaya.agents.connectorTools.call("stripe_create_refund", {}, { approval: "none" }),
    (e) => e instanceof MelayaError && e.status === 403 && e.code === "money_moving_requires_app_approval",
  );
});

// ── callStatus() ─────────────────────────────────────────────────────────────

test("callStatus() reports a done outcome", async () => {
  const { melaya, calls } = mockClient(() =>
    jsonResponse({ requestId: "req-2", tool: "gmail_send", status: "done", ok: true, result: "sent" }),
  );

  const r = await melaya.agents.connectorTools.callStatus("req-2");
  assert.equal(new URL(calls[0].url).pathname, "/api/v1/private/connector-tools/calls/req-2");
  assert.equal(r.status, "done");
  assert.equal(r.ok, true);
  assert.equal(r.result, "sent");
});

test("callStatus() throws a MelayaError (404) for an unknown request id", async () => {
  const { melaya } = mockClient(() => jsonResponse({ error: "not_found" }, 404));

  await assert.rejects(
    () => melaya.agents.connectorTools.callStatus("00000000-0000-0000-0000-000000000000"),
    (e) => e instanceof MelayaError && e.status === 404,
  );
});

// ── connect() — oauth_unavailable (503) is a normal result, not a throw ────

test("connect() returns oauth_unavailable instead of throwing on its 503", async () => {
  const { melaya } = mockClient(() =>
    jsonResponse({ service: "acme_crm", kind: "oauth_unavailable", message: "OAuth is not configured for acme_crm." }, 503),
  );

  const r = await melaya.agents.connectorTools.connect("acme_crm");
  assert.equal(r.kind, "oauth_unavailable");
  assert.equal(r.service, "acme_crm");
});

test("connect() still throws for a genuine error", async () => {
  const { melaya } = mockClient(() => jsonResponse({ error: "bad_request", message: "service is required." }, 400));

  await assert.rejects(
    () => melaya.agents.connectorTools.connect(""),
    (e) => e instanceof MelayaError && e.status === 400,
  );
});

// ── callAndWait() — polls to done with a tiny interval ──────────────────────

test("callAndWait() polls callStatus() until done, then stops", async () => {
  const { melaya, calls } = mockClient((url, init, n) => {
    if (n === 1) {
      assert.equal(init.method, "POST");
      return jsonResponse(
        { status: "pending_approval", tool: "gmail_send", requestId: "req-3", message: "Approve it." },
        202,
      );
    }
    // Polls 2 and 3 are still pending; poll 4 is done.
    if (n < 4) return jsonResponse({ requestId: "req-3", tool: "gmail_send", status: "pending" });
    return jsonResponse({ requestId: "req-3", tool: "gmail_send", status: "done", ok: true, result: "sent!" });
  });

  const outcome = await melaya.agents.connectorTools.callAndWait(
    "gmail_send",
    { to: "a@b.c" },
    { pollIntervalMs: 1 },
  );

  assert.equal(calls.length, 4); // 1 call + 3 callStatus polls
  assert.equal(outcome.status, "done");
  assert.equal(outcome.ok, true);
  assert.equal(outcome.result, "sent!");
  assert.equal(outcome.requestId, "req-3");
});

test("callAndWait() returns the immediate result without polling when the tool runs right away", async () => {
  const { melaya, calls } = mockClient(() =>
    jsonResponse({ status: "done", tool: "gmail_list_messages", readOnly: true, result: "3 unread" }),
  );

  const outcome = await melaya.agents.connectorTools.callAndWait("gmail_list_messages");

  assert.equal(calls.length, 1);
  assert.equal(outcome.status, "done");
  assert.equal(outcome.ok, true);
  assert.equal(outcome.result, "3 unread");
  assert.equal(outcome.requestId, undefined);
});

test("callAndWait() surfaces a rejected decision without throwing", async () => {
  const { melaya } = mockClient((url, init, n) => {
    if (n === 1) return jsonResponse({ status: "pending_approval", tool: "gmail_send", requestId: "req-4", message: "Approve it." }, 202);
    return jsonResponse({ requestId: "req-4", tool: "gmail_send", status: "rejected", reason: "not now" });
  });

  const outcome = await melaya.agents.connectorTools.callAndWait("gmail_send", {}, { pollIntervalMs: 1 });
  assert.equal(outcome.status, "rejected");
  assert.equal(outcome.reason, "not now");
});

// ── Flat alias wiring ────────────────────────────────────────────────────────

test("melaya.connectorTools is the same instance as melaya.agents.connectorTools", () => {
  const { melaya } = mockClient(() => jsonResponse({}));
  assert.equal(melaya.connectorTools, melaya.agents.connectorTools);
});
