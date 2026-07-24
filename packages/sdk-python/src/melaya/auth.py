"""Auth API — login, registration, MFA, password management, and session control.

Maps to ``/api/v1/private/auth/*`` (authenticated) and
``/api/v1/private/auth/*`` (public endpoints).

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> profile = m.auth.me()
>>> m.auth.change_password(current_password="old", new_password="new")
"""
from __future__ import annotations

from typing import Any, Dict, Optional

from .platform_types import JsonDict


class AuthAPI:
    def __init__(self, request: Any) -> None:
        self._request = request

    # ── Public endpoints (no auth needed) ──────────────────────────────────────

    def login(self, *, username: str, password: str) -> JsonDict:
        """Password + username login; returns session JWT + optional MFA challenge token."""
        return self._request("POST", "/api/v1/private/auth/login",
                             json={"username": username, "password": password})

    def verify_mfa(self, *, challenge_token: str, code: str) -> JsonDict:
        """Resolve MFA challenge — exchange challenge token + TOTP code for full session JWT."""
        return self._request("POST", "/api/v1/private/auth/mfa/verify",
                             json={"challengeToken": challenge_token, "code": code})

    def register(self, *, username: str, email: str, password: str, **kwargs: Any) -> JsonDict:
        """Create a new user account; sends email verification."""
        body = {"username": username, "email": email, "password": password, **kwargs}
        return self._request("POST", "/api/v1/private/auth/register", json=body)

    def verify_signup(self, *, token: str) -> JsonDict:
        """Confirm email address from signup link token."""
        return self._request("POST", "/api/v1/private/auth/verify-signup", json={"token": token})

    def resend_verification(self, *, email: str) -> JsonDict:
        """Re-send email verification message."""
        return self._request("POST", "/api/v1/private/auth/resend-verification", json={"email": email})

    def forgot_password(self, *, email: str) -> JsonDict:
        """Initiate password reset flow; sends email with reset token."""
        return self._request("POST", "/api/v1/private/auth/forgot-password", json={"email": email})

    def reset_password(self, *, token: str, new_password: str) -> JsonDict:
        """Complete password reset using token from email."""
        return self._request("POST", "/api/v1/private/auth/reset-password",
                             json={"token": token, "newPassword": new_password})

    # ── Authenticated endpoints ─────────────────────────────────────────────────

    def me(self) -> JsonDict:
        """Return current authenticated user profile (id, username, email, tier, role, capabilities)."""
        return self._request("GET", "/api/v1/private/auth/me")

    def check(self) -> JsonDict:
        """Lightweight session validity check; returns ok: true."""
        return self._request("GET", "/api/v1/private/auth/check")

    def change_password(self, *, current_password: str, new_password: str) -> JsonDict:
        """Change password (current + new; re-hashes, invalidates other sessions)."""
        return self._request("POST", "/api/v1/private/auth/change-password",
                             json={"currentPassword": current_password, "newPassword": new_password})

    def create_mobile_handoff(self) -> JsonDict:
        """Create a short-lived handoff token for mobile app deep-link auth."""
        return self._request("POST", "/api/v1/private/auth/mobile-handoff")

    def my_permissions(self) -> JsonDict:
        """Return caller's permission flags (capabilities, tier, feature gates)."""
        return self._request("GET", "/api/v1/private/auth/permissions")

    def refresh(self) -> JsonDict:
        """Rotate session JWT (sliding expiry); returns new token."""
        return self._request("POST", "/api/v1/private/auth/refresh")
