// Unit tests (mock transport) for the 0.3 surface additions.
// No test framework dependency: uses Node's built-in `node:test` / `node:assert`
// (Node 18+, matches the package's own `engines` requirement) against the
// built `dist/index.js`, exactly like `e2e/smoke.mjs` does for the live API.
// Run: npm run build && node --test tests/
import { test } from "node:test";
import assert from "node:assert/strict";
import { Melaya } from "../dist/index.js";

/** JSON `Response` matching the server's `{ ok, ...data }` envelope. */
function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify({ ok: true, ...data }), {
    status,
    headers: { "content-type": "application/json" },
  });
}

/** A Melaya client whose `fetch` is a recording mock. `handler` returns the
 *  `Response` for each call; `calls` accumulates `{ url, init }` for assertions. */
function mockClient(handler) {
  const calls = [];
  const fetchMock = async (url, init) => {
    calls.push({ url: String(url), init });
    return handler(String(url), init);
  };
  const melaya = new Melaya({ apiKey: "mk_test_key", fetch: fetchMock });
  return { melaya, calls };
}

// ── A.1 run() with run_inputs ───────────────────────────────────────────────

test("pipelines.run() sends run_inputs in the body and echoes them back", async () => {
  const { melaya, calls } = mockClient((_url, init) => {
    const body = JSON.parse(init.body);
    return jsonResponse({ run_id: "run_abc123", queued: true, run_inputs: body.run_inputs });
  });

  const runInputs = { brief: "Focus on Q3 numbers", values: { sourceDoc: { file_id: "file_xyz" } } };
  const result = await melaya.agents.pipelines.run("daily-digest", { project: "acme", run_inputs: runInputs });

  assert.equal(calls.length, 1);
  assert.equal(calls[0].init.method, "POST");
  assert.match(new URL(calls[0].url).pathname, /\/api\/v1\/private\/pipelines\/daily-digest\/run$/);

  const sentBody = JSON.parse(calls[0].init.body);
  assert.deepEqual(sentBody.run_inputs, runInputs);
  assert.equal(sentBody.project, "acme");

  assert.equal(result.run_id, "run_abc123");
  assert.equal(result.queued, true);
  assert.deepEqual(result.run_inputs, runInputs);
});

// ── A.2 uploadRunFile() — multipart ─────────────────────────────────────────

test("pipelines.uploadRunFile() sends a proper multipart/form-data body with the file part", async () => {
  let captured;
  const { melaya, calls } = mockClient(async (url, init) => {
    // Constructing a Request from the intercepted (url, init) makes the
    // fetch implementation compute the real multipart Content-Type/boundary
    // and serialize the FormData body, without any network I/O.
    const req = new Request(url, init);
    captured = {
      contentType: req.headers.get("content-type"),
      raw: await req.text(),
    };
    return jsonResponse({ file_id: "file_upload_1" });
  });

  const bytes = new TextEncoder().encode("hello world, this is the file body");
  const result = await melaya.agents.pipelines.uploadRunFile("daily-digest", "sourceDoc", bytes, {
    project: "acme",
    filename: "notes.txt",
    contentType: "text/plain",
  });

  assert.equal(result.file_id, "file_upload_1");
  assert.equal(calls.length, 1);
  assert.equal(calls[0].init.method, "POST");

  const url = new URL(calls[0].url);
  assert.match(url.pathname, /\/pipelines\/daily-digest\/run-files$/);
  assert.equal(url.searchParams.get("key"), "sourceDoc");
  assert.equal(url.searchParams.get("project"), "acme");

  assert.ok(captured.contentType, "Content-Type header must be set");
  assert.match(captured.contentType, /^multipart\/form-data;\s*boundary=/);
  assert.match(captured.raw, /name="file"; filename="notes\.txt"/);
  assert.match(captured.raw, /hello world, this is the file body/);
});

// ── A.4 runInputFile() — raw bytes, not JSON ────────────────────────────────

test("pipelines.runInputFile() returns raw bytes without JSON-parsing the body", async () => {
  const payload = new Uint8Array([0x25, 0x50, 0x44, 0x46, 0x00, 0xff, 0x10]); // arbitrary binary, incl. a NUL and 0xFF
  const { melaya, calls } = mockClient(() =>
    new Response(payload, { status: 200, headers: { "content-type": "application/octet-stream" } }),
  );

  const bytes = await melaya.agents.pipelines.runInputFile("daily-digest", "0123456789abcdef", 0);

  assert.equal(calls.length, 1);
  assert.equal(calls[0].init.method, "GET");
  assert.match(
    new URL(calls[0].url).pathname,
    /\/pipelines\/daily-digest\/runs\/0123456789abcdef\/inputs\/files\/0$/,
  );
  assert.ok(bytes instanceof Uint8Array);
  assert.deepEqual([...bytes], [...payload]);
});

// ── B. projectToolCalls() — query encoding ──────────────────────────────────

test("pipelines.projectToolCalls() encodes all filter/sort params in the query string", async () => {
  const { melaya, calls } = mockClient(() => jsonResponse({ items: [], nextCursor: null, capped: false }));

  await melaya.agents.pipelines.projectToolCalls("acme", {
    limit: 50,
    tool: "web_search",
    agent: "researcher",
    status: "error",
    connectorSource: "project",
    approval: "by:alice",
    provider: "anthropic",
    sort: "slowest",
    beforeCreatedAt: "2026-09-01T00:00:00.000Z",
    beforeId: "span_1",
  });

  assert.equal(calls.length, 1);
  assert.equal(calls[0].init.method, "GET");
  const url = new URL(calls[0].url);
  assert.equal(url.pathname, "/api/v1/private/projects/acme/tool-calls");
  assert.equal(url.searchParams.get("limit"), "50");
  assert.equal(url.searchParams.get("tool"), "web_search");
  assert.equal(url.searchParams.get("agent"), "researcher");
  assert.equal(url.searchParams.get("status"), "error");
  assert.equal(url.searchParams.get("connectorSource"), "project");
  assert.equal(url.searchParams.get("approval"), "by:alice");
  assert.equal(url.searchParams.get("provider"), "anthropic");
  assert.equal(url.searchParams.get("sort"), "slowest");
  assert.equal(url.searchParams.get("beforeCreatedAt"), "2026-09-01T00:00:00.000Z");
  assert.equal(url.searchParams.get("beforeId"), "span_1");
});

// ── B. applyPersonal() — bridged POST with path param + body ────────────────

test("connectors.applyPersonal() puts project+service in the path and googleCapabilities in the body", async () => {
  const { melaya, calls } = mockClient(() => jsonResponse({}));

  await melaya.platform.connectors.applyPersonal("acme", "google", ["gmail", "calendar"]);

  assert.equal(calls.length, 1);
  assert.equal(calls[0].init.method, "POST");
  const url = new URL(calls[0].url);
  assert.equal(url.pathname, "/api/v1/private/projects/acme/connectors/google/apply-personal");
  const body = JSON.parse(calls[0].init.body);
  assert.deepEqual(body.googleCapabilities, ["gmail", "calendar"]);
});

// ── B. googleDisconnect() — DELETE with a JSON body ─────────────────────────

test("connectors.googleDisconnect() issues a DELETE with a JSON body", async () => {
  const { melaya, calls } = mockClient(() => jsonResponse({}));

  await melaya.platform.connectors.googleDisconnect("acme", "507f1f77bcf86cd799439011", "gmail");

  assert.equal(calls.length, 1);
  assert.equal(calls[0].init.method, "DELETE");
  assert.equal(calls[0].init.headers["Content-Type"], "application/json");
  const url = new URL(calls[0].url);
  assert.equal(url.pathname, "/api/v1/private/projects/acme/connectors/google/access");
  const body = JSON.parse(calls[0].init.body);
  assert.deepEqual(body, { accountId: "507f1f77bcf86cd799439011", capability: "gmail" });
});

// ── Personal credentials.googleDisconnect() — same DELETE+body contract ────

test("credentials.googleDisconnect() (personal scope) issues a DELETE with a JSON body", async () => {
  const { melaya, calls } = mockClient(() => jsonResponse({}));

  await melaya.platform.credentials.googleDisconnect("507f1f77bcf86cd799439011");

  assert.equal(calls[0].init.method, "DELETE");
  assert.equal(calls[0].init.headers["Content-Type"], "application/json");
  const body = JSON.parse(calls[0].init.body);
  assert.equal(body.accountId, "507f1f77bcf86cd799439011");
});

// ── ingestRetrieval() uses a long per-call timeout ──────────────────────────

test("pipelines.ingestRetrieval() posts to the ingest endpoint with a default body", async () => {
  const { melaya, calls } = mockClient(() => jsonResponse({ started: true }));

  await melaya.agents.pipelines.ingestRetrieval("daily-digest");

  assert.equal(calls.length, 1);
  assert.equal(calls[0].init.method, "POST");
  assert.match(new URL(calls[0].url).pathname, /\/docs\/retrieval\/ingest$/);
  assert.deepEqual(JSON.parse(calls[0].init.body), {});
});

// ── get()/update() envelope round trip (A.8) ────────────────────────────────

test("pipelines.get() returns an envelope whose .config round-trips through update()", async () => {
  const storedConfig = {
    name: "daily-digest",
    project: "acme",
    steps: [{ kind: "agent", agent: { name: "researcher", instruction: "...", model: { provider: "anthropic", name: "claude-sonnet-4-6" } } }],
  };
  const { melaya, calls } = mockClient((url, init) => {
    if (init.method === "GET") {
      return jsonResponse({ name: "daily-digest", client: "ts-sdk", config: storedConfig, code: "// generated", docs: null });
    }
    // PUT (update)
    const body = JSON.parse(init.body);
    return jsonResponse({ ...body.config });
  });

  const envelope = await melaya.agents.pipelines.get("daily-digest", "acme");
  assert.ok(envelope.config, "get() must return an envelope with a .config field");
  assert.equal(envelope.config.steps[0].agent.name, "researcher");

  envelope.config.steps[0].agent.model = { provider: "anthropic", name: "claude-opus-4-8" };
  await melaya.agents.pipelines.update("daily-digest", envelope.config, "acme");

  const putCall = calls.find((c) => c.init.method === "PUT");
  assert.ok(putCall, "update() must issue a PUT");
  const putBody = JSON.parse(putCall.init.body);
  assert.equal(putBody.config.steps[0].agent.model.name, "claude-opus-4-8");
  assert.equal(putBody.project, "acme");
});
