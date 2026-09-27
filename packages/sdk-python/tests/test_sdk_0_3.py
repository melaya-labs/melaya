"""Unit tests (mock transport) for the Melaya SDK 0.3 surface additions.

Covers: pipelines.run(run_inputs=...), the hand-built multipart upload helper,
raw-bytes downloads, tool-call audit query encoding, a bridged POST with a
path param + JSON body, a DELETE with a JSON body, the new memory module, and
the extended per-call timeout used by ingest_retrieval().

No live network calls are made: every test swaps the client's httpx transport
for ``httpx.MockTransport`` so requests are inspected in-process.
"""
from __future__ import annotations

import json

import httpx
import pytest

from melaya import Melaya, MelayaError


def make_client(handler) -> Melaya:
    """Build a Melaya client whose HTTP client is wired to a mock transport.

    Preserves the real Authorization header and base_url set up by the normal
    constructor so requests still look exactly like production traffic.
    """
    m = Melaya(api_key="mk_test_1234567890")
    m._http = httpx.Client(
        base_url=m._base_url,
        timeout=m._request_timeout,
        headers=m._http.headers,
        transport=httpx.MockTransport(handler),
    )
    return m


# ── A.1 run() with run_inputs ───────────────────────────────────────────────


def test_run_with_run_inputs_body():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["method"] = request.method
        captured["url"] = str(request.url)
        captured["body"] = json.loads(request.content)
        return httpx.Response(
            200,
            json={"run_id": "run_abc123", "queued": True, "run_inputs": {"brief": "hi"}},
        )

    m = make_client(handler)
    try:
        result = m.pipelines.run(
            "my-pipe",
            project="acme",
            run_inputs={"brief": "Summarize the news", "values": {"doc": {"file_id": "f_123"}}},
        )
    finally:
        m.close()

    assert captured["method"] == "POST"
    assert "/api/v1/private/pipelines/my-pipe/run" in captured["url"]
    assert captured["body"]["project"] == "acme"
    assert captured["body"]["run_inputs"] == {
        "brief": "Summarize the news",
        "values": {"doc": {"file_id": "f_123"}},
    }
    assert result["run_id"] == "run_abc123"
    assert result["run_inputs"] == {"brief": "hi"}


def test_run_without_run_inputs_omits_key():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["body"] = json.loads(request.content) if request.content else {}
        return httpx.Response(200, json={"run_id": "run_x", "queued": True})

    m = make_client(handler)
    try:
        m.pipelines.run("my-pipe")
    finally:
        m.close()

    assert "run_inputs" not in captured["body"]


# ── A.2 multipart upload (hand-built body) ──────────────────────────────────


def test_upload_run_file_multipart_body_and_headers():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["content_type"] = request.headers.get("content-type", "")
        captured["auth"] = request.headers.get("authorization", "")
        captured["body"] = request.content
        captured["url"] = str(request.url)
        return httpx.Response(200, json={"file_id": "file_xyz", "ok": True})

    m = make_client(handler)
    try:
        result = m.pipelines.upload_run_file(
            "my-pipe",
            "brief-doc",
            b"hello world",
            project="acme",
            filename="notes.txt",
            content_type="text/plain",
        )
    finally:
        m.close()

    assert result == {"file_id": "file_xyz", "ok": True}
    assert captured["auth"] == "Bearer mk_test_1234567890"
    assert "multipart/form-data" in captured["content_type"]
    assert "boundary=" in captured["content_type"]
    boundary = captured["content_type"].split("boundary=", 1)[1]
    assert boundary.encode() in captured["body"]
    assert b'Content-Disposition: form-data; name="file"; filename="notes.txt"' in captured["body"]
    assert b"Content-Type: text/plain" in captured["body"]
    assert b"hello world" in captured["body"]
    assert "/api/v1/private/pipelines/my-pipe/run-files" in captured["url"]
    assert "key=brief-doc" in captured["url"]
    assert "project=acme" in captured["url"]


def test_upload_doc_accepts_file_like_object():
    import io

    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["body"] = request.content
        return httpx.Response(200, json={"ok": True})

    m = make_client(handler)
    try:
        m.pipelines.upload_doc("my-pipe", io.BytesIO(b"# Notes\n"), filename="notes.md")
    finally:
        m.close()

    assert b'filename="notes.md"' in captured["body"]
    assert b"# Notes" in captured["body"]


def test_multipart_upload_is_never_retried_on_5xx():
    attempts = {"n": 0}

    def handler(request: httpx.Request) -> httpx.Response:
        attempts["n"] += 1
        return httpx.Response(503, json={"error": "unavailable"})

    m = make_client(handler)
    try:
        with pytest.raises(MelayaError) as exc_info:
            m.pipelines.upload_run_file("my-pipe", "k", b"data", filename="f.bin")
    finally:
        m.close()

    assert attempts["n"] == 1
    assert exc_info.value.status == 503


# ── A.4 runInputFile — raw bytes, not JSON ──────────────────────────────────


def test_run_input_file_returns_raw_bytes():
    payload = b"\x89PNG\r\n\x1a\n\x00\x01\x02\xff"

    def handler(request: httpx.Request) -> httpx.Response:
        assert "/inputs/files/0" in str(request.url)
        return httpx.Response(200, content=payload, headers={"content-type": "application/octet-stream"})

    m = make_client(handler)
    try:
        data = m.pipelines.run_input_file("my-pipe", "0123456789abcdef", 0)
    finally:
        m.close()

    assert isinstance(data, (bytes, bytearray))
    assert data == payload


def test_get_bytes_retries_get_on_5xx_then_succeeds():
    attempts = {"n": 0}
    payload = b"binary-content"

    def handler(request: httpx.Request) -> httpx.Response:
        attempts["n"] += 1
        if attempts["n"] == 1:
            return httpx.Response(503, json={"error": "unavailable"})
        return httpx.Response(200, content=payload)

    m = make_client(handler)
    try:
        data = m.pipelines.run_input_file("my-pipe", "0123456789abcdef", 1)
    finally:
        m.close()

    assert attempts["n"] == 2
    assert data == payload


# ── B: project_tool_calls query encoding ────────────────────────────────────


def test_project_tool_calls_query_encoding():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        return httpx.Response(200, json={"items": [], "nextCursor": None, "capped": False})

    m = make_client(handler)
    try:
        m.pipelines.project_tool_calls(
            "acme",
            limit=50,
            tool="phone_tap",
            agent="mobile-operator",
            run_id="run_1",
            status="ok",
            search="tap",
            connector_source="project",
            approval="auto",
            provider="anthropic",
            sort="recent",
            before_created_at="2026-01-01T00:00:00Z",
            before_id="abc123",
        )
    finally:
        m.close()

    url = captured["url"]
    assert "/api/v1/private/projects/acme/tool-calls" in url
    assert "limit=50" in url
    assert "tool=phone_tap" in url
    assert "agent=mobile-operator" in url
    assert "runId=run_1" in url
    assert "status=ok" in url
    assert "search=tap" in url
    assert "connectorSource=project" in url
    assert "approval=auto" in url
    assert "provider=anthropic" in url
    assert "sort=recent" in url
    assert "beforeId=abc123" in url
    assert "beforeCreatedAt=" in url


def test_project_tool_call_facets_and_tool_call_detail():
    seen = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(str(request.url))
        return httpx.Response(200, json={"tools": [], "agents": []})

    m = make_client(handler)
    try:
        m.pipelines.project_tool_call_facets("acme")
        m.pipelines.tool_call_detail("run_1", "span_1")
    finally:
        m.close()

    assert any("/api/v1/private/projects/acme/tool-calls/facets" in u for u in seen)
    assert any("/api/v1/private/runs/run_1/tool-calls/span_1" in u for u in seen)


# ── B: bridged POST with path param + JSON body (applyPersonal) ────────────


def test_apply_personal_bridged_post():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["method"] = request.method
        captured["url"] = str(request.url)
        captured["body"] = json.loads(request.content) if request.content else {}
        return httpx.Response(200, json={"ok": True})

    m = make_client(handler)
    try:
        m.connectors.apply_personal("acme", "gmail", google_capabilities=["gmail", "calendar"])
    finally:
        m.close()

    assert captured["method"] == "POST"
    assert "/api/v1/private/projects/acme/connectors/gmail/apply-personal" in captured["url"]
    assert captured["body"] == {"googleCapabilities": ["gmail", "calendar"]}


# ── B: DELETE with JSON body (googleDisconnect) ─────────────────────────────


def test_google_disconnect_delete_with_json_body():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["method"] = request.method
        captured["url"] = str(request.url)
        captured["body"] = json.loads(request.content) if request.content else {}
        return httpx.Response(200, json={"ok": True})

    m = make_client(handler)
    try:
        m.credentials.google_disconnect("507f1f77bcf86cd799439011", capability="gmail")
    finally:
        m.close()

    assert captured["method"] == "DELETE"
    assert "/api/v1/private/credentials/google/access" in captured["url"]
    assert captured["body"] == {"accountId": "507f1f77bcf86cd799439011", "capability": "gmail"}


def test_project_google_disconnect_delete_with_json_body():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["method"] = request.method
        captured["body"] = json.loads(request.content) if request.content else {}
        return httpx.Response(200, json={"ok": True})

    m = make_client(handler)
    try:
        m.connectors.google_disconnect("acme", "507f1f77bcf86cd799439011")
    finally:
        m.close()

    assert captured["method"] == "DELETE"
    assert captured["body"] == {"accountId": "507f1f77bcf86cd799439011"}


# ── memory module ────────────────────────────────────────────────────────────


def test_memory_edit_entry_and_delete_entry():
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append((str(request.url), json.loads(request.content)))
        return httpx.Response(200, json={"ok": True})

    m = make_client(handler)
    try:
        m.memory.edit_entry("my-crew", "entry_1", project="acme", topic="new topic", tags=["a", "b"])
        m.memory.delete_entry("my-crew", "entry_1", project="acme")
    finally:
        m.close()

    edit_url, edit_body = calls[0]
    delete_url, delete_body = calls[1]
    assert edit_url.endswith("/api/v1/private/memory/crew/edit")
    assert edit_body == {
        "pipeline": "my-crew",
        "entryId": "entry_1",
        "patch": {"topic": "new topic", "tags": ["a", "b"]},
        "project": "acme",
    }
    assert delete_url.endswith("/api/v1/private/memory/crew/delete")
    assert delete_body == {"pipeline": "my-crew", "entryId": "entry_1", "project": "acme"}


# ── ingest_retrieval: extended per-call timeout ─────────────────────────────


def test_ingest_retrieval_uses_extended_default_timeout():
    calls = []

    def fake_request(method, path, **kwargs):
        calls.append((method, path, kwargs))
        return {"ok": True}

    m = Melaya(api_key="mk_test_1234567890")
    m.pipelines._request = fake_request
    try:
        m.pipelines.ingest_retrieval("my-pipe")
        m.pipelines.ingest_retrieval("my-pipe", {"force": True}, timeout=600)
    finally:
        m.close()

    method0, path0, kwargs0 = calls[0]
    assert method0 == "POST"
    assert path0.endswith("/api/v1/private/pipelines/my-pipe/docs/retrieval/ingest")
    assert kwargs0["json"] == {}
    assert kwargs0["timeout"] == 300.0

    _, _, kwargs1 = calls[1]
    assert kwargs1["json"] == {"force": True}
    assert kwargs1["timeout"] == 600


def test_execute_timeout_is_forwarded_to_httpx():
    seen_timeout = {}
    real_request = httpx.Client.request

    def spy_request(self, method, url, *args, **kwargs):
        seen_timeout["value"] = kwargs.get("timeout")
        return real_request(self, method, url, *args, **kwargs)

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"ok": True})

    m = make_client(handler)
    m._http.request = spy_request.__get__(m._http, httpx.Client)
    try:
        m.pipelines.ingest_retrieval("my-pipe", timeout=123.0)
    finally:
        m.close()

    assert seen_timeout["value"] == 123.0


# ── other new B-section methods (one smoke test per module) ────────────────


def test_team_transfer_ownership():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={"ok": True})

    m = make_client(handler)
    try:
        m.team.transfer_ownership("acme", "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    finally:
        m.close()

    assert "/api/v1/private/projects/acme/transfer-ownership" in captured["url"]
    assert captured["body"] == {"newOwnerUserId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"}


def test_billing_redeem_code_and_reads():
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append((request.method, str(request.url)))
        return httpx.Response(200, json={"ok": True})

    m = make_client(handler)
    try:
        m.billing.redeem_code("WELCOME10")
        m.billing.ambassador_perk()
        m.billing.reserved_promo()
    finally:
        m.close()

    assert calls[0][0] == "POST"
    assert "redeem-code" in calls[0][1]
    assert any("ambassador-perk" in u for _, u in calls)
    assert any("reserved-promo" in u for _, u in calls)


def test_accounts_email_verification():
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content) if request.content else {}
        calls.append((request.method, str(request.url), body))
        return httpx.Response(200, json={"ok": True})

    m = make_client(handler)
    try:
        m.accounts.resend_email_verification()
        m.accounts.verify_email("a" * 64)
    finally:
        m.close()

    assert calls[0][1].endswith("/api/v1/private/accounts/resend-email-verification")
    assert calls[1][2] == {"token": "a" * 64}


def test_phone_grant_app_and_request_cast():
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content) if request.content else {}
        calls.append((str(request.url), body))
        return httpx.Response(200, json={"ok": True})

    m = make_client(handler)
    try:
        m.phone.grant_app("com.android.chrome", "Chrome")
        m.phone.request_cast()
    finally:
        m.close()

    assert calls[0][1] == {"package": "com.android.chrome", "label": "Chrome"}
    assert calls[1][1] == {}


def test_credentials_google_and_dbtest_and_telegram_qr():
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content) if request.content else {}
        calls.append((request.method, str(request.url), body))
        return httpx.Response(200, json={"ok": True, "sessionId": "s1", "handle": "tgauth_abc"})

    m = make_client(handler)
    try:
        m.credentials.google_set_default("gmail", "507f1f77bcf86cd799439011")
        m.credentials.db_test_start("postgres", credentials={"host": "db.internal"})
        m.credentials.telegram_qr_start(12345, "abcdef")
    finally:
        m.close()

    assert calls[0][0] == "PUT"
    assert calls[0][2] == {"capability": "gmail", "accountId": "507f1f77bcf86cd799439011"}
    assert calls[1][2] == {"service": "postgres", "credentials": {"host": "db.internal"}}
    assert calls[2][2] == {"api_id": 12345, "api_hash": "abcdef"}


def test_pipeline_static_and_retrieval_docs():
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append((request.method, str(request.url), request.content))
        return httpx.Response(200, json={"ok": True, "active": True})

    m = make_client(handler)
    try:
        m.pipelines.list_docs("my-pipe")
        m.pipelines.delete_doc("my-pipe", "notes.md")
        m.pipelines.delete_retrieval_doc("my-pipe", "kb.pdf")
        m.pipelines.run_active("my-pipe", "0123456789abcdef")
        m.pipelines.run_inputs("my-pipe", "0123456789abcdef")
    finally:
        m.close()

    urls = [u for _, u, _ in calls]
    assert any(u.endswith("/api/v1/private/pipelines/my-pipe/docs") for u in urls)
    assert any(u.endswith("/api/v1/private/pipelines/my-pipe/docs/notes.md") for u in urls)
    assert any(u.endswith("/api/v1/private/pipelines/my-pipe/docs/retrieval/kb.pdf") for u in urls)
    assert any(u.endswith("/api/v1/private/pipelines/my-pipe/runs/0123456789abcdef/active") for u in urls)
    assert any(u.endswith("/api/v1/private/pipelines/my-pipe/runs/0123456789abcdef/inputs") for u in urls)
