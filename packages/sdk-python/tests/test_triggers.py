"""Unit tests (mock transport) for the event-triggers diagnostics module.

Each call is checked for HTTP method, path, query and JSON body, and the
response is checked to pass through unchanged. No live network calls: the
client's httpx transport is swapped for ``httpx.MockTransport``.
"""
from __future__ import annotations

import json
from typing import Any, Callable, Dict, Optional

import httpx
import pytest

from melaya import Melaya, MelayaError

TID = "3f2b8c1e-5d4a-4e6f-9a7b-1c2d3e4f5a6b"
BASE = "/api/v1/private/triggers"


def make_client(response: Any, status: int = 200):
    """Client wired to a mock transport that records requests and answers ``response``."""
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        return httpx.Response(status, json=response)

    m = Melaya(api_key="mk_test_1234567890")
    m._http = httpx.Client(
        base_url=m._base_url,
        timeout=m._request_timeout,
        headers=m._http.headers,
        transport=httpx.MockTransport(handler),
    )
    return m, calls


def _body(request: httpx.Request) -> Optional[Dict[str, Any]]:
    return json.loads(request.content) if request.content else None


CASES = [
    # (name, call, method, path, query, body, response)
    ("list", lambda t: t.list(project="acme", pipeline_name="digest"),
     "GET", BASE, {"project": "acme", "pipelineName": "digest"}, None,
     [{"id": TID, "name": "Stripe refunds"}]),
    ("list_no_filters", lambda t: t.list(), "GET", BASE, {}, None, []),
    ("get", lambda t: t.get(TID), "GET", f"{BASE}/{TID}", {}, None,
     {"id": TID, "kind": "webhook", "webhookUrl": "https://api.melaya.org/api/v1/hooks/abc"}),
    ("deliveries", lambda t: t.deliveries(TID, limit=20), "GET", f"{BASE}/{TID}/deliveries", {"limit": "20"}, None,
     [{"id": "d1", "verdict": "dispatched", "runId": "run_1", "timings": {"total": 812}}]),
    ("stats", lambda t: t.stats(TID, hours=48), "GET", f"{BASE}/{TID}/stats", {"hours": "48"}, None,
     {"hours": 48, "byVerdict": {"dispatched": {"n": 3, "p50": 400, "p95": 900}}, "filtered": 2, "sampled": False}),
    ("pending_approvals", lambda t: t.pending_approvals(TID), "GET", f"{BASE}/{TID}/approvals", {}, None,
     [{"requestId": "r1", "tool": "gmail_send", "expiresAt": 1}]),
    ("test", lambda t: t.test(TID, {"amount": 120, "currency": "usd"}), "POST", f"{BASE}/{TID}/test", {},
     {"payload": {"amount": 120, "currency": "usd"}}, {"accepted": True, "eventId": "test-1"}),
    ("test_no_payload", lambda t: t.test(TID), "POST", f"{BASE}/{TID}/test", {}, {},
     {"accepted": False, "eventId": "test-2", "reason": "disabled"}),
    ("events", lambda t: t.events(trigger_id=TID, since=1727500000000, verdicts=["filtered", "rejected"], limit=100),
     "GET", f"{BASE}/events",
     {"triggerId": TID, "since": "1727500000000", "verdicts": "filtered,rejected", "limit": "100"}, None,
     {"events": [{"triggerId": TID, "verdict": "filtered", "at": 1}], "scanned": 12,
      "retention": {"maxEvents": 500, "ttlSec": 86400}}),
    ("events_no_params", lambda t: t.events(), "GET", f"{BASE}/events", {}, None,
     {"events": [], "scanned": 0, "retention": {"maxEvents": 500, "ttlSec": 86400}}),
    ("poll_status", lambda t: t.poll_status(TID), "GET", f"{BASE}/{TID}/poll", {}, None,
     {"synced": True, "status": "idle", "armed": True, "effectiveIntervalSec": 300, "tierFloorSec": 300}),
    ("poll_test", lambda t: t.poll_test(TID), "POST", f"{BASE}/{TID}/poll/test", {}, {"dry": True},
     {"dry": True, "ok": True, "found": 2, "baseline": False, "wouldPublish": 1,
      "items": [{"id": "a", "preview": "{}"}], "samplePayload": {}}),
    ("poll_now", lambda t: t.poll_now(TID), "POST", f"{BASE}/{TID}/poll/test", {}, {"dry": False},
     {"dry": False, "queued": True}),
    ("poll_sync", lambda t: t.poll_sync(TID), "POST", f"{BASE}/{TID}/poll/sync", {}, None, {"result": "synced"}),
    ("presets", lambda t: t.presets(), "GET", f"{BASE}/presets", {}, None,
     {"tier": "forge", "tierFloorSec": 120, "presets": [], "beta": {"allowed": True, "minTier": "forge"}}),
    ("limits", lambda t: t.limits(), "GET", f"{BASE}/limits", {}, None,
     {"tierClass": "forge", "triggers": {"used": 1, "cap": 10}, "sources": {"used": 0, "cap": 2}}),
    ("sources", lambda t: t.sources(), "GET", f"{BASE}/sources", {}, None,
     [{"id": "s1", "transport": "sse", "hasAuth": True}]),
]


@pytest.mark.parametrize("name,call,method,path,query,body,response", CASES, ids=[c[0] for c in CASES])
def test_trigger_calls(
    name: str,
    call: Callable[[Any], Any],
    method: str,
    path: str,
    query: Dict[str, str],
    body: Optional[Dict[str, Any]],
    response: Any,
) -> None:
    m, calls = make_client(response)
    try:
        result = call(m.agents.triggers)
    finally:
        m.close()

    assert len(calls) == 1
    req = calls[0]
    assert req.method == method
    assert req.url.path == path
    assert dict(req.url.params) == query
    assert _body(req) == body
    assert req.headers["authorization"] == "Bearer mk_test_1234567890"
    assert result == response


def test_flat_alias_is_same_instance() -> None:
    m, _ = make_client({})
    try:
        assert m.triggers is m.agents.triggers
    finally:
        m.close()


def test_poll_test_returns_failed_dry_poll() -> None:
    failed = {"dry": True, "ok": False, "error": "tool_error"}
    m, _ = make_client(failed)
    try:
        assert m.agents.triggers.poll_test(TID) == failed
    finally:
        m.close()


def test_poll_now_ok_false_still_raises() -> None:
    m, _ = make_client({"ok": False, "error": "boom"})
    try:
        with pytest.raises(MelayaError):
            m.agents.triggers.poll_now(TID)
    finally:
        m.close()


def test_get_404_raises() -> None:
    m, _ = make_client({"error": "NOT_FOUND", "message": "[trigger_not_found] Trigger not found."}, status=404)
    try:
        with pytest.raises(MelayaError) as info:
            m.agents.triggers.get(TID)
    finally:
        m.close()
    assert info.value.status == 404


def test_get_quotes_the_id() -> None:
    m, calls = make_client({})
    try:
        m.agents.triggers.get("a/b")
    finally:
        m.close()
    assert calls[0].url.raw_path.decode() == f"{BASE}/a%2Fb"
