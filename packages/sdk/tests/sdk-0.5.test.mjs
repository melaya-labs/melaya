// Unit tests (mock transport) for the 0.5 surface additions: connector
// accounts (personal + project), platform API key rotation, retrieval and
// docs previews, declared run inputs (setInputs), and the run-messages path.
// Same harness as sdk-0.3.test.mjs. Run: npm run build && node --test tests/
import { test } from "node:test";
import assert from "node:assert/strict";
import { Melaya } from "../dist/index.js";

/** A Melaya client whose `fetch` is a recording mock returning `payload` as JSON. */
function mockClient(payload = { ok: true }) {
  const calls = [];
  const fetchMock = async (url, init) => {
    calls.push({ url: String(url), init });
    return new Response(JSON.stringify(payload), { status: 200, headers: { "content-type": "application/json" } });
  };
  return { melaya: new Melaya({ apiKey: "mk_test_key", fetch: fetchMock }), calls };
}

const pathOf = (c) => new URL(c.url).pathname;
const bodyOf = (c) => (c.init.body === undefined ? undefined : JSON.parse(c.init.body));
const ACCOUNTS = [{ id: "a1b2c3", label: "Sales inbox", isDefault: true, createdAt: "2026-10-01T00:00:00.000Z" }];

// ── Personal connector accounts ─────────────────────────────────────────────

test("credentials.accounts() lists the accounts of a connector (service URL-encoded)", async () => {
  const { melaya, calls } = mockClient(ACCOUNTS);
  const out = await melaya.platform.credentials.accounts("zoho mail");
  assert.equal(calls[0].init.method, "GET");
  assert.equal(pathOf(calls[0]), "/api/v1/private/credentials/zoho%20mail/accounts");
  assert.deepEqual(out, ACCOUNTS);
});

test("credentials.addAccount() posts label, fields and options", async () => {
  const { melaya, calls } = mockClient(ACCOUNTS);
  await melaya.platform.credentials.addAccount("zoho_mail", { label: "Support", fields: { refresh_token: "x" }, makeDefault: true });
  assert.equal(calls[0].init.method, "POST");
  assert.equal(pathOf(calls[0]), "/api/v1/private/credentials/zoho_mail/accounts");
  assert.deepEqual(bodyOf(calls[0]), { label: "Support", fields: { refresh_token: "x" }, makeDefault: true });
});

test("credentials.setDefaultAccount() puts the account id", async () => {
  const { melaya, calls } = mockClient(ACCOUNTS);
  await melaya.platform.credentials.setDefaultAccount("zoho_mail", "a1b2c3");
  assert.equal(calls[0].init.method, "PUT");
  assert.equal(pathOf(calls[0]), "/api/v1/private/credentials/zoho_mail/accounts/default");
  assert.deepEqual(bodyOf(calls[0]), { accountId: "a1b2c3" });
});

test("credentials.identifyAccount() posts to /identify", async () => {
  const { melaya, calls } = mockClient(ACCOUNTS);
  await melaya.platform.credentials.identifyAccount("zoho_mail", "a1 b2");
  assert.equal(calls[0].init.method, "POST");
  assert.equal(pathOf(calls[0]), "/api/v1/private/credentials/zoho_mail/accounts/a1%20b2/identify");
});

test("credentials.renameAccount() puts the label", async () => {
  const { melaya, calls } = mockClient(ACCOUNTS);
  await melaya.platform.credentials.renameAccount("zoho_mail", "a1b2c3", "Billing");
  assert.equal(calls[0].init.method, "PUT");
  assert.equal(pathOf(calls[0]), "/api/v1/private/credentials/zoho_mail/accounts/a1b2c3");
  assert.deepEqual(bodyOf(calls[0]), { label: "Billing" });
});

test("credentials.removeAccount() deletes one account", async () => {
  const { melaya, calls } = mockClient([]);
  await melaya.platform.credentials.removeAccount("zoho_mail", "a1b2c3");
  assert.equal(calls[0].init.method, "DELETE");
  assert.equal(pathOf(calls[0]), "/api/v1/private/credentials/zoho_mail/accounts/a1b2c3");
});

// ── Project connector accounts ──────────────────────────────────────────────

test("connectors.accounts() lists a project connector's accounts", async () => {
  const { melaya, calls } = mockClient(ACCOUNTS);
  await melaya.platform.connectors.accounts("acme corp", "zoho_mail");
  assert.equal(calls[0].init.method, "GET");
  assert.equal(pathOf(calls[0]), "/api/v1/private/projects/acme%20corp/connectors/zoho_mail/accounts");
});

test("connectors.addAccount() posts to the project connector", async () => {
  const { melaya, calls } = mockClient(ACCOUNTS);
  await melaya.platform.connectors.addAccount("acme", "zoho_mail", { fields: { refresh_token: "x" } });
  assert.equal(calls[0].init.method, "POST");
  assert.equal(pathOf(calls[0]), "/api/v1/private/projects/acme/connectors/zoho_mail/accounts");
  assert.deepEqual(bodyOf(calls[0]), { fields: { refresh_token: "x" } });
});

test("connectors.setDefaultAccount() / renameAccount() / removeAccount()", async () => {
  const { melaya, calls } = mockClient(ACCOUNTS);
  await melaya.platform.connectors.setDefaultAccount("acme", "zoho_mail", "a1");
  await melaya.platform.connectors.renameAccount("acme", "zoho_mail", "a1", "Ops");
  await melaya.platform.connectors.removeAccount("acme", "zoho_mail", "a1");
  assert.deepEqual(calls.map((c) => [c.init.method, pathOf(c)]), [
    ["PUT", "/api/v1/private/projects/acme/connectors/zoho_mail/accounts/default"],
    ["PUT", "/api/v1/private/projects/acme/connectors/zoho_mail/accounts/a1"],
    ["DELETE", "/api/v1/private/projects/acme/connectors/zoho_mail/accounts/a1"],
  ]);
  assert.deepEqual(bodyOf(calls[0]), { accountId: "a1" });
  assert.deepEqual(bodyOf(calls[1]), { label: "Ops" });
});

// ── Platform API key ────────────────────────────────────────────────────────

test("account.rotateApiKey() / revokeApiKey() / apiKeyUsage()", async () => {
  const { melaya, calls } = mockClient({ apiKey: "mk_new" });
  const rotated = await melaya.account.rotateApiKey();
  await melaya.account.revokeApiKey();
  await melaya.account.apiKeyUsage();
  assert.equal(rotated.apiKey, "mk_new");
  assert.deepEqual(calls.map((c) => [c.init.method, pathOf(c)]), [
    ["POST", "/api/v1/private/api-key"],
    ["DELETE", "/api/v1/private/api-key"],
    ["GET", "/api/v1/private/api-key/usage"],
  ]);
});

// ── Pipeline documents and retrieval ────────────────────────────────────────

test("pipelines.docsPreview() passes the model as query parameters", async () => {
  const { melaya, calls } = mockClient({ files: [] });
  await melaya.agents.pipelines.docsPreview("deal flow", { modelName: "qwen3.7-plus", modelProvider: "qwen" });
  const u = new URL(calls[0].url);
  assert.equal(calls[0].init.method, "GET");
  assert.equal(u.pathname, "/api/v1/private/pipelines/deal%20flow/docs/preview");
  assert.equal(u.searchParams.get("model_name"), "qwen3.7-plus");
  assert.equal(u.searchParams.get("model_provider"), "qwen");
});

test("pipelines.retrievalPreview() and testRetrieve()", async () => {
  const { melaya, calls } = mockClient({ results: [] });
  await melaya.agents.pipelines.retrievalPreview("kb");
  await melaya.agents.pipelines.testRetrieve("kb", "refund policy", 3);
  assert.equal(pathOf(calls[0]), "/api/v1/private/pipelines/kb/docs/retrieval/preview");
  assert.equal(calls[1].init.method, "POST");
  assert.equal(pathOf(calls[1]), "/api/v1/private/pipelines/kb/docs/retrieval/test_retrieve");
  assert.deepEqual(bodyOf(calls[1]), { query: "refund policy", limit: 3 });
});

// ── Declared run inputs + run messages ──────────────────────────────────────

test("pipelines.setInputs() replaces only the declared inputs", async () => {
  const { melaya, calls } = mockClient({ name: "dd", inputs: [] });
  const inputs = [{ key: "company", label: "Company", type: "text", required: true }];
  await melaya.agents.pipelines.setInputs("due diligence", "acme", inputs);
  assert.equal(calls[0].init.method, "PUT");
  assert.equal(pathOf(calls[0]), "/api/v1/private/pipelines/due%20diligence/inputs");
  assert.deepEqual(bodyOf(calls[0]), { inputs, project: "acme" });
});

test("hitl.runMessages() calls /runs/:runId/messages (not /hitl/runs)", async () => {
  const { melaya, calls } = mockClient([]);
  await melaya.agents.hitl.runMessages("0123456789abcdef", { limit: 10 });
  assert.equal(pathOf(calls[0]), "/api/v1/private/runs/0123456789abcdef/messages");
});
