"""Unit tests (mock transport) for the Melaya SDK connector tools module
(``melaya.connector_tools`` / ``m.agents.connector_tools`` / ``m.connector_tools``).

Mirrors ``routes/connectorToolsApi.ts`` and ``connectorToolsApi.test.ts`` in
melaya-platform: reads run immediately, writes default to a staged approval
card (202, not an error), ``approval="none"`` runs immediately, money-moving
tools are refused under both modes, and ``call_and_wait`` polls a staged
write to a terminal status.

No live network calls are made: every test swaps the client's httpx transport
for ``httpx.MockTransport`` so requests are inspected in-process.
"""
from __future__ import annotations

import json
from urllib.parse import parse_qs, urlparse

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


# ── services / describe / connect: smoke ────────────────────────────────────


def test_services_lists_connected_services_and_tool_counts():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path.endswith("/api/v1/private/connector-tools/services")
        return httpx.Response(
            200,
            json={"services": ["gmail"], "builtIn": "melaya_core", "toolCounts": {"gmail": {"readTools": 1, "writeTools": 1}}},
        )

    m = make_client(handler)
    try:
        out = m.agents.connector_tools.services()
    finally:
        m.close()

    assert out["services"] == ["gmail"]
    assert out["toolCounts"]["gmail"] == {"readTools": 1, "writeTools": 1}


def test_describe_unknown_tool_raises_404():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(404, json={"error": "not_found", "message": "Unknown tool."})

    m = make_client(handler)
    try:
        with pytest.raises(MelayaError) as exc_info:
            m.connector_tools.describe("nope_tool")
    finally:
        m.close()

    assert exc_info.value.status == 404
    assert exc_info.value.code == "not_found"


def test_connect_returns_oauth_authorization_url():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.method == "POST"
        assert json.loads(request.content) == {"service": "gmail"}
        return httpx.Response(200, json={"service": "gmail", "kind": "oauth", "authorizationUrl": "https://accounts.google.com/o/oauth2/...", "message": "Open this URL to connect."})

    m = make_client(handler)
    try:
        out = m.connector_tools.connect("gmail")
    finally:
        m.close()

    assert out["kind"] == "oauth"
    assert "authorizationUrl" in out


def test_test_credential_ok():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path.endswith("/api/v1/private/connector-tools/test")
        assert json.loads(request.content) == {"service": "gmail"}
        return httpx.Response(200, json={"service": "gmail", "success": True, "message": "gmail ok"})

    m = make_client(handler)
    try:
        out = m.connector_tools.test("gmail")
    finally:
        m.close()

    assert out == {"service": "gmail", "success": True, "message": "gmail ok"}


def test_test_credential_timeout_raises_504():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(504, json={"error": "timeout", "service": "gmail", "message": "The service did not answer within 30 seconds."})

    m = make_client(handler)
    try:
        with pytest.raises(MelayaError) as exc_info:
            m.connector_tools.test("gmail")
    finally:
        m.close()

    assert exc_info.value.status == 504
    assert exc_info.value.code == "timeout"


# ── search: query + limit encoding ──────────────────────────────────────────


def test_search_encodes_query_and_limit():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        return httpx.Response(
            200,
            json={"query": "unread email", "services": ["gmail", "melaya_core"], "tools": []},
        )

    m = make_client(handler)
    try:
        out = m.connector_tools.search("unread email", limit=5)
    finally:
        m.close()

    parsed = urlparse(captured["url"])
    assert parsed.path.endswith("/api/v1/private/connector-tools/search")
    qs = parse_qs(parsed.query)
    assert qs["q"] == ["unread email"]
    assert qs["limit"] == ["5"]
    assert out["query"] == "unread email"


def test_search_omits_limit_when_not_given():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        return httpx.Response(200, json={"query": "refund", "services": [], "tools": []})

    m = make_client(handler)
    try:
        m.connector_tools.search("refund")
    finally:
        m.close()

    qs = parse_qs(urlparse(captured["url"]).query)
    assert qs["q"] == ["refund"]
    assert "limit" not in qs


# ── call: read runs immediately (200) ───────────────────────────────────────


def test_call_read_tool_runs_immediately():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={"status": "done", "tool": "gmail_list_messages", "readOnly": True, "result": "3 unread"})

    m = make_client(handler)
    try:
        out = m.connector_tools.call("gmail_list_messages", {"max_results": 5})
    finally:
        m.close()

    assert captured["body"] == {"tool": "gmail_list_messages", "args": {"max_results": 5}, "approval": "required"}
    assert out == {"status": "done", "tool": "gmail_list_messages", "readOnly": True, "result": "3 unread"}


def test_call_defaults_args_to_empty_dict():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={"status": "done", "tool": "gmail_list_messages", "readOnly": True, "result": ""})

    m = make_client(handler)
    try:
        m.connector_tools.call("gmail_list_messages")
    finally:
        m.close()

    assert captured["body"]["args"] == {}


def test_call_write_none_approval_runs_immediately():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={"status": "done", "tool": "gmail_send", "readOnly": False, "result": "sent"})

    m = make_client(handler)
    try:
        out = m.connector_tools.call("gmail_send", {"to": "a@b.c"}, approval="none")
    finally:
        m.close()

    assert captured["body"]["approval"] == "none"
    assert out["status"] == "done"


# ── call: write staged (202) is a return value, not an exception ───────────


def test_call_write_staged_returns_202_body_not_raised():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            202,
            json={"status": "pending_approval", "tool": "gmail_send", "requestId": "11111111-1111-1111-1111-111111111111", "message": "Approve or reject it in the Melaya app, then poll GET /calls/:requestId."},
        )

    m = make_client(handler)
    try:
        out = m.connector_tools.call("gmail_send", {"to": "a@b.c"})
    finally:
        m.close()

    assert out["status"] == "pending_approval"
    assert out["requestId"] == "11111111-1111-1111-1111-111111111111"


# ── call: money-moving tools are refused under both approval modes ─────────


def test_call_money_moving_write_raises_403():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            403,
            json={"error": "money_moving_requires_app_approval", "tool": "stripe_create_refund", "message": "Tools that move money or trade run only from the Melaya app."},
        )

    m = make_client(handler)
    try:
        with pytest.raises(MelayaError) as exc_info:
            m.connector_tools.call("stripe_create_refund", approval="none")
    finally:
        m.close()

    assert exc_info.value.status == 403
    assert exc_info.value.code == "money_moving_requires_app_approval"


# ── call_status: outcomes ───────────────────────────────────────────────────


def test_call_status_done():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path.endswith("/api/v1/private/connector-tools/calls/req-1")
        return httpx.Response(200, json={"requestId": "req-1", "tool": "gmail_send", "status": "done", "ok": True, "result": "sent"})

    m = make_client(handler)
    try:
        out = m.connector_tools.call_status("req-1")
    finally:
        m.close()

    assert out == {"requestId": "req-1", "tool": "gmail_send", "status": "done", "ok": True, "result": "sent"}


def test_call_status_unknown_raises_404():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(404, json={"error": "not_found", "message": "Unknown or expired request."})

    m = make_client(handler)
    try:
        with pytest.raises(MelayaError) as exc_info:
            m.connector_tools.call_status("does-not-exist")
    finally:
        m.close()

    assert exc_info.value.status == 404


# ── call_and_wait: polls a staged write to done with a tiny interval ───────


def test_call_and_wait_polls_pending_then_done():
    calls = {"n": 0}

    def handler(request: httpx.Request) -> httpx.Response:
        if request.method == "POST" and request.url.path.endswith("/connector-tools/call"):
            return httpx.Response(
                202,
                json={"status": "pending_approval", "tool": "gmail_send", "requestId": "req-42", "message": "pending"},
            )
        # GET /calls/req-42 — pending on first poll, done on the second.
        calls["n"] += 1
        if calls["n"] == 1:
            return httpx.Response(200, json={"requestId": "req-42", "tool": "gmail_send", "status": "pending"})
        return httpx.Response(200, json={"requestId": "req-42", "tool": "gmail_send", "status": "done", "ok": True, "result": "sent"})

    m = make_client(handler)
    try:
        out = m.connector_tools.call_and_wait(
            "gmail_send", {"to": "a@b.c"}, poll_interval=0.01, timeout=5,
        )
    finally:
        m.close()

    assert calls["n"] == 2
    assert out == {"requestId": "req-42", "tool": "gmail_send", "status": "done", "ok": True, "result": "sent"}


def test_call_and_wait_returns_immediately_for_a_read():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"status": "done", "tool": "gmail_list_messages", "readOnly": True, "result": "3 unread"})

    m = make_client(handler)
    try:
        out = m.connector_tools.call_and_wait("gmail_list_messages", {"max_results": 5})
    finally:
        m.close()

    assert out["status"] == "done"


def test_call_and_wait_stops_on_rejected():
    calls = {"n": 0}

    def handler(request: httpx.Request) -> httpx.Response:
        if request.method == "POST":
            return httpx.Response(202, json={"status": "pending_approval", "tool": "gmail_send", "requestId": "req-9", "message": "pending"})
        calls["n"] += 1
        return httpx.Response(200, json={"requestId": "req-9", "tool": "gmail_send", "status": "rejected", "reason": "not now"})

    m = make_client(handler)
    try:
        out = m.connector_tools.call_and_wait("gmail_send", {"to": "a@b.c"}, poll_interval=0.01, timeout=5)
    finally:
        m.close()

    assert calls["n"] == 1
    assert out == {"requestId": "req-9", "tool": "gmail_send", "status": "rejected", "reason": "not now"}


def test_call_and_wait_gives_up_after_timeout_without_raising():
    def handler(request: httpx.Request) -> httpx.Response:
        if request.method == "POST":
            return httpx.Response(202, json={"status": "pending_approval", "tool": "gmail_send", "requestId": "req-stuck", "message": "pending"})
        return httpx.Response(200, json={"requestId": "req-stuck", "tool": "gmail_send", "status": "pending"})

    m = make_client(handler)
    try:
        out = m.connector_tools.call_and_wait(
            "gmail_send", {"to": "a@b.c"}, poll_interval=0.01, timeout=0.03,
        )
    finally:
        m.close()

    assert out["status"] == "pending"


# ── namespace + flat alias wire to the same instance ────────────────────────


def test_connector_tools_namespace_and_flat_alias_share_one_instance():
    m = Melaya(api_key="mk_test_1234567890")
    try:
        assert m.connector_tools is m.agents.connector_tools
    finally:
        m.close()
