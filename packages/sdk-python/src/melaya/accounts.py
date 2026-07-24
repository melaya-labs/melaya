"""Accounts API — user profile, credits, and CEX key management.

Maps to ``/api/v1/private/accounts/*`` and ``/api/v1/private/keys/*``.

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> credits = m.accounts.credits()
>>> m.accounts.update_profile(display_name="Antoine")
"""
from __future__ import annotations

from typing import Any, Dict, Optional

from .platform_types import JsonDict


def _snake_to_camel(key: str) -> str:
    """Convert ``snake_case`` to ``camelCase``; camelCase keys pass through."""
    head, *rest = key.split("_")
    return head + "".join(part[:1].upper() + part[1:] for part in rest)


class AccountsAPI:
    """User-account management: profile, credits, and CEX key lifecycle."""

    def __init__(self, request: Any) -> None:
        self._request = request

    def export_my_data(self) -> JsonDict:
        """GDPR Art 15/20 data export; returns JSON blob of all user data."""
        return self._request("POST", "/api/v1/private/accounts/export")

    def remove_key(self, key_id: str) -> JsonDict:
        """Remove a stored CEX API key by its ID."""
        return self._request("DELETE", f"/api/v1/private/keys/{key_id}")

    def update_profile(self, **kwargs: Any) -> JsonDict:
        """Update user display name, avatar, or settings.

        Keyword arguments are forwarded as JSON body fields. snake_case keys
        are converted to camelCase on the wire, e.g. ``display_name="..."``
        becomes ``{"displayName": "..."}``. Keys that are already camelCase
        (no underscores) are passed through unchanged.
        """
        body = {_snake_to_camel(k): v for k, v in kwargs.items()}
        return self._request("PATCH", "/api/v1/private/accounts/profile", json=body)

    def credits(self) -> JsonDict:
        """Return current credit balance and transaction history."""
        return self._request("GET", "/api/v1/private/accounts/credits")

    def ai_credits(self) -> JsonDict:
        """Return AI/LLM credit balance."""
        return self._request("GET", "/api/v1/private/accounts/credits/ai")

    def portfolio_ideas_credits(self) -> JsonDict:
        """Return portfolio-ideas feature credit balance."""
        return self._request("GET", "/api/v1/private/accounts/credits/portfolio-ideas")

    def risk_monitoring_credits(self) -> JsonDict:
        """Return risk-monitoring feature credit balance."""
        return self._request("GET", "/api/v1/private/accounts/credits/risk-monitoring")
