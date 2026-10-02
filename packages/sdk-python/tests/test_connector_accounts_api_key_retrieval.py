"""Unit tests (mock transport) for connector accounts, API key rotation,
pipeline docs/retrieval previews, set_inputs and run_messages.

No live network calls are made: every test swaps the client's httpx transport
for ``httpx.MockTransport`` and asserts method + exact (encoded) path + body.
"""
from __future__ import annotations

import json
from typing import Any, Dict, List

import httpx

from melaya import Melaya

ACCOUNTS = [{"id": "acc/1", "label": "Sales", "isDefault": True, "createdAt": None}]


class Recorder:
    """Records every request and answers with a fixed JSON payload."""

    def __init__(self, payload: Any = None) -> None:
        self.payload = ACCOUNTS if payload is None else payload
        self.calls: List[Dict[str, Any]] = []

    def __call__(self, request: httpx.Request) -> httpx.Response:
        self.calls.append(
            {
                "method": request.method,
                "path": request.url.raw_path.decode("ascii").split("?", 1)[0],
                "params": dict(request.url.params),
                "body": json.loads(request.content) if request.content else None,
            }
        )
        return httpx.Response(200, json=self.payload)

    @property
    def last(self) -> Dict[str, Any]:
        return self.calls[-1]


def make_client(handler) -> Melaya:
    m = Melaya(api_key="mk_test_1234567890")
    m._http = httpx.Client(
        base_url=m._base_url,
        timeout=m._request_timeout,
        headers=m._http.headers,
        transport=httpx.MockTransport(handler),
    )
    return m


def call(fn_name: str, *args: Any, payload: Any = None, **kwargs: Any) -> Dict[str, Any]:
    """Run ``m.<fn_name>(*args, **kwargs)`` against a recorder; return result + the recorded call."""
    rec = Recorder(payload)
    m = make_client(rec)
    try:
        target: Any = m
        for part in fn_name.split("."):
            target = getattr(target, part)
        result = target(*args, **kwargs)
    finally:
        m.close()
    assert len(rec.calls) == 1
    return {"result": result, **rec.last}


CRED = "/api/v1/private/credentials/zoho%20mail/accounts"
PROJ = "/api/v1/private/projects/my%20proj/connectors/zoho%20mail/accounts"


# ── Personal connector accounts ─────────────────────────────────────────────


def test_credentials_accounts():
    c = call("credentials.accounts", "zoho mail")
    assert c["method"] == "GET"
    assert c["path"] == CRED
    assert c["body"] is None
    assert c["result"] == ACCOUNTS


def test_credentials_add_account_full_body():
    c = call(
        "credentials.add_account",
        "zoho mail",
        fields={"api_key": "k1"},
        label="Support",
        current_label="Sales",
        make_default=True,
    )
    assert c["method"] == "POST"
    assert c["path"] == CRED
    assert c["body"] == {
        "fields": {"api_key": "k1"},
        "label": "Support",
        "currentLabel": "Sales",
        "makeDefault": True,
    }
    assert c["result"] == ACCOUNTS


def test_credentials_add_account_omits_absent_optionals():
    c = call("credentials.add_account", "zoho mail", fields={"api_key": "k1"})
    assert c["body"] == {"fields": {"api_key": "k1"}}


def test_credentials_set_default_account():
    c = call("credentials.set_default_account", "zoho mail", "acc/1")
    assert c["method"] == "PUT"
    assert c["path"] == CRED + "/default"
    assert c["body"] == {"accountId": "acc/1"}


def test_credentials_identify_account():
    c = call("credentials.identify_account", "zoho mail", "acc/1")
    assert c["method"] == "POST"
    assert c["path"] == CRED + "/acc%2F1/identify"
    assert c["body"] == {}


def test_credentials_rename_account():
    c = call("credentials.rename_account", "zoho mail", "acc/1", "Billing")
    assert c["method"] == "PUT"
    assert c["path"] == CRED + "/acc%2F1"
    assert c["body"] == {"label": "Billing"}


def test_credentials_remove_account():
    c = call("credentials.remove_account", "zoho mail", "acc/1", payload=[])
    assert c["method"] == "DELETE"
    assert c["path"] == CRED + "/acc%2F1"
    assert c["body"] is None
    assert c["result"] == []


# ── Project connector accounts ──────────────────────────────────────────────


def test_connectors_accounts():
    c = call("connectors.accounts", "my proj", "zoho mail")
    assert c["method"] == "GET"
    assert c["path"] == PROJ
    assert c["result"] == ACCOUNTS


def test_connectors_add_account():
    c = call(
        "connectors.add_account",
        "my proj",
        "zoho mail",
        fields={"api_key": "k2"},
        label="Ops",
        make_default=False,
    )
    assert c["method"] == "POST"
    assert c["path"] == PROJ
    assert c["body"] == {"fields": {"api_key": "k2"}, "label": "Ops", "makeDefault": False}


def test_connectors_set_default_account():
    c = call("connectors.set_default_account", "my proj", "zoho mail", "acc/1")
    assert c["method"] == "PUT"
    assert c["path"] == PROJ + "/default"
    assert c["body"] == {"accountId": "acc/1"}


def test_connectors_rename_account():
    c = call("connectors.rename_account", "my proj", "zoho mail", "acc/1", "Ops EU")
    assert c["method"] == "PUT"
    assert c["path"] == PROJ + "/acc%2F1"
    assert c["body"] == {"label": "Ops EU"}


def test_connectors_remove_account():
    c = call("connectors.remove_account", "my proj", "zoho mail", "acc/1")
    assert c["method"] == "DELETE"
    assert c["path"] == PROJ + "/acc%2F1"
    assert c["body"] is None


# ── Platform API key ────────────────────────────────────────────────────────


def test_rotate_api_key():
    c = call("account.rotate_api_key", payload={"apiKey": "mk_new"})
    assert c["method"] == "POST"
    assert c["path"] == "/api/v1/private/api-key"
    assert c["body"] == {}
    assert c["result"] == {"apiKey": "mk_new"}


def test_revoke_api_key():
    c = call("account.revoke_api_key", payload={"ok": True})
    assert c["method"] == "DELETE"
    assert c["path"] == "/api/v1/private/api-key"
    assert c["body"] is None
    assert c["result"] == {"ok": True}


def test_api_key_usage():
    c = call("account.api_key_usage", payload={"requests": 3})
    assert c["method"] == "GET"
    assert c["path"] == "/api/v1/private/api-key/usage"
    assert c["result"] == {"requests": 3}


def test_usage_summary():
    c = call("account.usage_summary", payload={"pipelines": 2})
    assert c["method"] == "GET"
    assert c["path"] == "/api/v1/private/overview/usage"


# ── Pipelines: docs / retrieval previews, inputs, run messages ──────────────


def test_docs_preview_with_model_query():
    c = call(
        "pipelines.docs_preview",
        "my pipe",
        model_name="qwen/qwen3.7-plus",
        model_provider="openrouter",
        payload={"docs": []},
    )
    assert c["method"] == "GET"
    assert c["path"] == "/api/v1/private/pipelines/my%20pipe/docs/preview"
    assert c["params"] == {"model_name": "qwen/qwen3.7-plus", "model_provider": "openrouter"}


def test_docs_preview_omits_absent_query():
    c = call("pipelines.docs_preview", "my pipe", payload={"docs": []})
    assert c["path"] == "/api/v1/private/pipelines/my%20pipe/docs/preview"
    assert c["params"] == {}


def test_retrieval_preview():
    c = call("pipelines.retrieval_preview", "my pipe", payload={"chunks": 4})
    assert c["method"] == "GET"
    assert c["path"] == "/api/v1/private/pipelines/my%20pipe/docs/retrieval/preview"


def test_test_retrieve_with_limit():
    c = call("pipelines.test_retrieve", "my pipe", "pricing tiers", limit=3, payload={"passages": []})
    assert c["method"] == "POST"
    assert c["path"] == "/api/v1/private/pipelines/my%20pipe/docs/retrieval/test_retrieve"
    assert c["body"] == {"query": "pricing tiers", "limit": 3}


def test_test_retrieve_omits_limit():
    c = call("pipelines.test_retrieve", "my pipe", "pricing tiers", payload={"passages": []})
    assert c["body"] == {"query": "pricing tiers"}


def test_set_inputs():
    inputs = [{"key": "company", "label": "Company", "type": "text", "required": True}]
    c = call(
        "pipelines.set_inputs",
        "my pipe",
        project="acme",
        inputs=inputs,
        payload={"name": "my pipe", "inputs": inputs},
    )
    assert c["method"] == "PUT"
    assert c["path"] == "/api/v1/private/pipelines/my%20pipe/inputs"
    assert c["body"] == {"inputs": inputs, "project": "acme"}


def test_run_messages_path():
    c = call("hitl.run_messages", "run_abc123", limit=10, payload={"messages": []})
    assert c["method"] == "GET"
    assert c["path"] == "/api/v1/private/runs/run_abc123/messages"
    assert c["params"] == {"limit": "10"}


def test_namespaced_accessors_share_new_methods():
    rec = Recorder()
    m = make_client(rec)
    try:
        m.platform.credentials.accounts("gmail")
        m.platform.connectors.accounts("p", "gmail")
    finally:
        m.close()
    assert [c["path"] for c in rec.calls] == [
        "/api/v1/private/credentials/gmail/accounts",
        "/api/v1/private/projects/p/connectors/gmail/accounts",
    ]

