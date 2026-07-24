"""MFA API — TOTP setup and enrollment status.

Maps to ``/api/v1/private/mfa/*``.

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> status = m.mfa.status()
>>> setup = m.mfa.setup()
>>> # Show setup["qrCode"] to the user, then:
>>> m.mfa.confirm(code="123456")
"""
from __future__ import annotations

from typing import Any

from .platform_types import JsonDict


class MfaAPI:
    def __init__(self, request: Any) -> None:
        self._request = request

    def status(self) -> JsonDict:
        """Return MFA enrollment status for the caller."""
        return self._request("GET", "/api/v1/private/mfa/status")

    def setup(self) -> JsonDict:
        """Initiate TOTP setup; returns QR code URL and shared secret."""
        return self._request("POST", "/api/v1/private/mfa/setup")

    def confirm(self, *, code: str) -> JsonDict:
        """Confirm TOTP setup with first valid code; activates MFA."""
        return self._request("POST", "/api/v1/private/mfa/confirm", json={"code": code})
