"""Credentials API — store, retrieve, test, and delete secrets and service connections.

Maps to ``/api/v1/private/credentials/*``. Credentials are envelope-encrypted at rest.
Use ``melaya.connectors`` for project-scoped connector credentials.

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> m.credentials.set("openai", value="sk-...", label="OpenAI prod key")
>>> result = m.credentials.test("openai")
>>> models = m.credentials.list_models(provider="openai")
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional
from urllib.parse import quote

from .platform_types import JsonDict


class CredentialsAPI:
    def __init__(self, request: Any) -> None:
        self._request = request

    def list(self) -> List[JsonDict]:
        """List all stored credentials (services, OAuth connections, env handles)."""
        return self._request("GET", "/api/v1/private/credentials")

    def connected_services(self) -> List[JsonDict]:
        """List connected third-party services for the caller."""
        return self._request("GET", "/api/v1/private/credentials/services")

    def get_operator_profile(self) -> JsonDict:
        """Get operator profile (persona config for agent context)."""
        return self._request("GET", "/api/v1/private/credentials/operator-profile")

    def get_luma_schema(self, *, event_id: Optional[str] = None) -> JsonDict:
        """Get the Luma event registration form schema."""
        params: Dict[str, Any] = {}
        if event_id is not None:
            params["eventId"] = event_id
        return self._request("GET", "/api/v1/private/credentials/luma/schema", params=params)

    def set_operator_profile(self, profile: Dict[str, Any]) -> JsonDict:
        """Save operator profile."""
        return self._request("PUT", "/api/v1/private/credentials/operator-profile", json=profile)

    def get(self, service: str, *, key: Optional[str] = None) -> JsonDict:
        """Get a stored credential value by service; pass key to retrieve a specific named key."""
        params: Dict[str, Any] = {}
        if key is not None:
            params["key"] = key
        return self._request("GET", f"/api/v1/private/credentials/{service}", params=params if params else None)

    def set(self, service: str, *, value: str, key: Optional[str] = None, label: Optional[str] = None) -> JsonDict:
        """Store or update a credential (envelope-encrypted at rest)."""
        body: Dict[str, Any] = {"value": value}
        if key is not None:
            body["key"] = key
        if label is not None:
            body["label"] = label
        return self._request("PUT", f"/api/v1/private/credentials/{service}", json=body)

    def delete(self, service: str) -> JsonDict:
        """Delete a stored credential by service name."""
        return self._request("DELETE", f"/api/v1/private/credentials/{service}")

    def rag_ingest_start(self, **kwargs: Any) -> JsonDict:
        """Start a RAG document ingestion job."""
        return self._request("POST", "/api/v1/private/rag/ingest", json=kwargs)

    def rag_ingest_status(self, session_id: str) -> JsonDict:
        """Poll RAG ingestion job status."""
        return self._request("GET", f"/api/v1/private/rag/ingest/{session_id}")

    def rag_retrieve_start(self, **kwargs: Any) -> JsonDict:
        """Start a RAG retrieval query."""
        return self._request("POST", "/api/v1/private/rag/retrieve", json=kwargs)

    def rag_retrieve_status(self, session_id: str) -> JsonDict:
        """Poll RAG retrieval result."""
        return self._request("GET", f"/api/v1/private/rag/retrieve/{session_id}")

    def pick_folder_start(self) -> JsonDict:
        """Initiate native folder picker for file ingestion."""
        return self._request("POST", "/api/v1/private/rag/pick-folder")

    def pick_folder_status(self, session_id: str) -> JsonDict:
        """Poll folder picker result."""
        return self._request("GET", f"/api/v1/private/rag/pick-folder/{session_id}")

    def linkedin_connect_start(self) -> JsonDict:
        """Start LinkedIn OAuth flow."""
        return self._request("POST", "/api/v1/private/credentials/linkedin/connect")

    def linkedin_connect_cancel(self) -> JsonDict:
        """Cancel an in-progress LinkedIn OAuth flow."""
        return self._request("DELETE", "/api/v1/private/credentials/linkedin/connect")

    def linkedin_connect_status(self) -> JsonDict:
        """Poll LinkedIn OAuth connection status."""
        return self._request("GET", "/api/v1/private/credentials/linkedin/connect/status")

    def luma_connect_start(self) -> JsonDict:
        """Start Luma OAuth flow."""
        return self._request("POST", "/api/v1/private/credentials/luma/connect")

    def luma_connect_status(self) -> JsonDict:
        """Poll Luma OAuth connection status."""
        return self._request("GET", "/api/v1/private/credentials/luma/connect/status")

    def luma_connect_cancel(self) -> JsonDict:
        """Cancel an in-progress Luma OAuth flow."""
        return self._request("DELETE", "/api/v1/private/credentials/luma/connect")

    def test(self, service: str) -> JsonDict:
        """Test a stored credential (e.g. validate API key against the exchange)."""
        return self._request("POST", f"/api/v1/private/credentials/{service}/test")

    def melaya_accounts(self) -> List[JsonDict]:
        """List Melaya sub-accounts available to the caller."""
        return self._request("GET", "/api/v1/private/credentials/melaya-accounts")

    def google_oauth_start(self) -> JsonDict:
        """Start Google OAuth flow for credential storage."""
        return self._request("POST", "/api/v1/private/credentials/google/oauth")

    def cli_auth_start(self) -> JsonDict:
        """Start CLI authentication flow (device-code style)."""
        return self._request("POST", "/api/v1/private/credentials/cli-auth")

    def notebooklm_login(self, *, username: Optional[str] = None, password: Optional[str] = None, **kwargs: Any) -> JsonDict:
        """Store NotebookLM credentials."""
        body: Dict[str, Any] = {**kwargs}
        if username is not None:
            body["username"] = username
        if password is not None:
            body["password"] = password
        return self._request("POST", "/api/v1/private/credentials/notebooklm/login", json=body)

    def notebooklm_status(self) -> JsonDict:
        """Check NotebookLM connection status."""
        return self._request("GET", "/api/v1/private/credentials/notebooklm/status")

    def telegram_auth_start(self, *, phone: str) -> JsonDict:
        """Start Telegram user auth (phone number step)."""
        return self._request("POST", "/api/v1/private/credentials/telegram/auth", json={"phone": phone})

    def telegram_auth_code(self, *, code: str) -> JsonDict:
        """Submit Telegram SMS verification code."""
        return self._request("POST", "/api/v1/private/credentials/telegram/auth/code", json={"code": code})

    def telegram_auth_2fa(self, *, password: str) -> JsonDict:
        """Submit Telegram 2FA password."""
        return self._request("POST", "/api/v1/private/credentials/telegram/auth/2fa", json={"password": password})

    def list_models(self, *, provider: Optional[str] = None, capability: Optional[str] = None) -> List[JsonDict]:
        """List available AI models across all configured providers.

        Collapses 19+ provider fan-out into a parameterized query.
        """
        params: Dict[str, Any] = {}
        if provider is not None:
            params["provider"] = provider
        if capability is not None:
            params["capability"] = capability
        return self._request("GET", "/api/v1/private/credentials/models", params=params if params else None)

    # ── Several accounts per connector (personal scope) ─────────────────────────
    # Field-based connectors can hold several accounts (two mailboxes, two shops).
    # Agents use the DEFAULT account unless a tool call names another one. Only
    # labels and ids ever come back, never credential values.

    def accounts(self, service: str) -> List[JsonDict]:
        """List the accounts connected to one connector.

        Each item is ``{"id", "label", "isDefault", "createdAt"}`` (``createdAt``
        may be ``None``). ``id == "current"`` is a single connection made before
        accounts existed.
        """
        return self._request("GET", f"/api/v1/private/credentials/{quote(service, safe='')}/accounts")

    def add_account(
        self,
        service: str,
        *,
        fields: Dict[str, str],
        label: Optional[str] = None,
        current_label: Optional[str] = None,
        make_default: Optional[bool] = None,
    ) -> List[JsonDict]:
        """Add another account to a field-based connector.

        ``fields`` are the connector's credential fields (same keys as ``set``);
        the connection is tested first. ``current_label`` names the existing
        single connection when it is adopted as the first account. Returns the
        updated account list.
        """
        body: Dict[str, Any] = {"fields": fields}
        if label is not None:
            body["label"] = label
        if current_label is not None:
            body["currentLabel"] = current_label
        if make_default is not None:
            body["makeDefault"] = make_default
        return self._request(
            "POST", f"/api/v1/private/credentials/{quote(service, safe='')}/accounts", json=body
        )

    def set_default_account(self, service: str, account_id: str) -> List[JsonDict]:
        """Choose which account the connector (and so every agent) uses. Returns the updated list."""
        return self._request(
            "PUT",
            f"/api/v1/private/credentials/{quote(service, safe='')}/accounts/default",
            json={"accountId": account_id},
        )

    def identify_account(self, service: str, account_id: str) -> List[JsonDict]:
        """Name an account after the identity its connector reports (runs the connection test on it)."""
        return self._request(
            "POST",
            f"/api/v1/private/credentials/{quote(service, safe='')}/accounts/{quote(account_id, safe='')}/identify",
            json={},
        )

    def rename_account(self, service: str, account_id: str, label: str) -> List[JsonDict]:
        """Rename one account (max 80 chars). Returns the updated list."""
        return self._request(
            "PUT",
            f"/api/v1/private/credentials/{quote(service, safe='')}/accounts/{quote(account_id, safe='')}",
            json={"label": label},
        )

    def remove_account(self, service: str, account_id: str) -> List[JsonDict]:
        """Remove one account from a connector. Returns the remaining list."""
        return self._request(
            "DELETE",
            f"/api/v1/private/credentials/{quote(service, safe='')}/accounts/{quote(account_id, safe='')}",
        )

    def google_status(self) -> JsonDict:
        """Get Google account connection status (personal, user-scoped)."""
        return self._request("GET", "/api/v1/private/credentials/google/status")

    def google_set_default(self, capability: str, account_id: str) -> JsonDict:
        """Set the default connected Google account for one capability (personal, user-scoped).

        ``capability`` is one of: gmail, calendar, drive, sheets, docs,
        search_console, youtube, google_ads, analytics, meet, slides.
        ``account_id`` is a 24-hex-char id.
        """
        return self._request(
            "PUT",
            "/api/v1/private/credentials/google/default",
            json={"capability": capability, "accountId": account_id},
        )

    def google_disconnect(self, account_id: str, *, capability: Optional[str] = None) -> JsonDict:
        """Disconnect a Google account (or one capability of it), user-scoped.

        Omit ``capability`` to disconnect the account entirely.
        """
        body: Dict[str, Any] = {"accountId": account_id}
        if capability is not None:
            body["capability"] = capability
        return self._request("DELETE", "/api/v1/private/credentials/google/access", json=body)

    def db_test_start(self, service: str, *, credentials: Optional[Dict[str, str]] = None) -> JsonDict:
        """Start a database connectivity probe from the user's runner (personal, user-scoped).

        Returns ``{"sessionId": ...}``; poll with ``db_test_status()``.
        """
        body: Dict[str, Any] = {"service": service}
        if credentials is not None:
            body["credentials"] = credentials
        return self._request("POST", "/api/v1/private/credentials/db-test", json=body)

    def db_test_status(self, session_id: str) -> JsonDict:
        """Poll a personal database connectivity probe session."""
        return self._request("GET", f"/api/v1/private/credentials/db-test/{session_id}")

    def telegram_qr_start(self, api_id: int, api_hash: str) -> JsonDict:
        """Start Telegram QR-code login. Returns ``{"handle": ...}`` to poll with ``telegram_qr_poll()``."""
        return self._request(
            "POST",
            "/api/v1/private/credentials/telegram/auth/qr/start",
            json={"api_id": api_id, "api_hash": api_hash},
        )

    def telegram_qr_poll(self, handle: str) -> JsonDict:
        """Poll a Telegram QR-code login started with ``telegram_qr_start()``.

        ``handle`` starts with ``"tgauth_"``.
        """
        return self._request("POST", "/api/v1/private/credentials/telegram/auth/qr/poll", json={"handle": handle})

    def whatsapp_signup_config(self) -> JsonDict:
        """Get the WhatsApp Embedded Signup config (App ID, config ID, etc.) needed to start the flow."""
        return self._request("GET", "/api/v1/private/credentials/whatsapp/embedded-signup/config")

    def whatsapp_signup_exchange(
        self, code: str, phone_number_id: str, waba_id: str, *, project: Optional[str] = None
    ) -> JsonDict:
        """Complete WhatsApp Embedded Signup by exchanging the flow's auth code."""
        body: Dict[str, Any] = {"code": code, "phoneNumberId": phone_number_id, "wabaId": waba_id}
        if project is not None:
            body["project"] = project
        return self._request("POST", "/api/v1/private/credentials/whatsapp/embedded-signup/exchange", json=body)

    def tiktok_creator_info(self) -> JsonDict:
        """Get the connected TikTok account's creator info (posting permissions, privacy options)."""
        return self._request("GET", "/api/v1/private/credentials/tiktok/creator-info")

    def substack_email_link_send(self, email: str) -> JsonDict:
        """Send a Substack sign-in link to ``email`` (start of the Substack connect flow)."""
        return self._request("POST", "/api/v1/private/credentials/substack/email-link", json={"email": email})

    def substack_email_link_redeem(self, link: str, *, email: Optional[str] = None) -> JsonDict:
        """Redeem a Substack sign-in link sent via ``substack_email_link_send()``."""
        body: Dict[str, Any] = {"link": link}
        if email is not None:
            body["email"] = email
        return self._request("POST", "/api/v1/private/credentials/substack/email-link/redeem", json=body)
