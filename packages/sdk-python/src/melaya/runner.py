"""Runner API — mint, list, and revoke runner tokens.

Maps to ``/api/v1/private/runner/tokens``. Runner tokens (``mel_run_`` prefix)
authenticate the Melaya runner CLI process that executes agent pipelines on
your infrastructure.

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> result = m.runner.create_token(label="prod-server-1")
>>> token = result["token"]  # Store securely — shown only once.
>>> tokens = m.runner.list_tokens()
>>> m.runner.revoke_token(tokens[0]["id"])
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional

from .platform_types import JsonDict


class RunnerAPI:
    def __init__(self, request: Any) -> None:
        self._request = request

    def create_token(self, *, label: Optional[str] = None) -> JsonDict:
        """Mint a new runner token (``mel_run_`` prefix). Plaintext shown only once — store securely."""
        body: Dict[str, Any] = {}
        if label is not None:
            body["label"] = label
        return self._request("POST", "/api/v1/private/runner/tokens", json=body if body else None)

    def list_tokens(self) -> List[JsonDict]:
        """List all runner tokens for the caller (masked, with last_seen timestamp)."""
        return self._request("GET", "/api/v1/private/runner/tokens")

    def revoke_token(self, token_id: str) -> JsonDict:
        """Revoke a runner token by ID."""
        return self._request("DELETE", f"/api/v1/private/runner/tokens/{token_id}")
