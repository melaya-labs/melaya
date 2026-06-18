"""Account API — authenticated reads about your Melaya account.

Connected-exchange key references (masked), tier limits, and live usage
counters. Requires an ``mk_`` key on the private plane.
"""
from __future__ import annotations

from typing import Any, Callable, Dict, List

_Request = Callable[..., Any]


class AccountAPI:
    def __init__(self, request: _Request) -> None:
        self._request = request

    def keys(self) -> List[Dict[str, Any]]:
        """Connected exchange keys. ``apiKey`` is masked; use ``apiKeyId`` as the reference."""
        return self._request("GET", "/api/v1/private/keys")["keys"]

    def usage(self) -> Dict[str, Any]:
        """Tier, plan limits, and live usage counters."""
        return self._request("GET", "/api/v1/private/usage")

    def api_key_status(self) -> Dict[str, Any]:
        """Status of your platform API key (tier, max concurrent connections)."""
        return self._request("GET", "/api/v1/private/api-key")
