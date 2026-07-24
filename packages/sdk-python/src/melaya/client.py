"""Core Melaya client — HTTP transport + entry point for all API modules.

Authentication: pass an ``mk_`` platform API key. For REST calls the SDK sends
it ONLY as an ``Authorization: Bearer mk_...`` header — never in the URL query
string, so the key cannot leak into REST access logs or proxies. WebSocket
streams follow the server protocol instead: PUBLIC market-data streams carry
the key as an ``?apiKey=`` query parameter in the ``wss://`` URL, and PRIVATE
streams carry a short-lived, one-shot ``?wsTicket=`` minted per connection (the
``mk_`` key itself never appears in private stream URLs). JWT session tokens
(from ``auth.login()``) can be passed via ``session_jwt`` for user-session
authenticated calls.

Retry: 429 and 5xx responses are retried with exponential back-off (up to
``max_retries`` attempts, default 3). ``Retry-After`` headers are respected.

Security:
    - Tokens are never logged.
    - TLS is enforced (base URL must use https:// in production).
    - The Authorization header is redacted in error messages.

Namespaces
----------
Three grouped namespaces are available as the *primary* documented API:

  melaya.trading   — market, account, sim, strategies, backtest, trade, stream
                     (preview — not for real funds)
  melaya.agents    — pipelines, hitl, assistant, phone, evals
  melaya.platform  — projects, credentials, connectors, billing, team,
                     templates, runner, auth, mfa, accounts, bugs, events

Flat top-level accessors (``m.market``, ``m.pipelines``, …) remain available
as aliases for backwards compatibility.

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> # Namespaced (primary API)
>>> t = m.trading.market.ticker(exchange="binance", symbol="BTC/USDT", market="spot")
>>> runs = m.agents.pipelines.list(project="my-project")
>>> projects = m.platform.projects.list()
>>> # Flat aliases (backwards compatible)
>>> t = m.market.ticker(exchange="binance", symbol="BTC/USDT", market="spot")
"""
from __future__ import annotations

import logging
import random
import time
from typing import Any, Dict, Optional

import httpx

from .errors import MelayaError

# Trading plane
from .market import MarketAPI
from .account import AccountAPI
from .sim import SimAPI
from .strategies import StrategiesAPI
from .backtest import BacktestAPI
from .trade import TradeAPI
from .stream import StreamAPI

# Platform / agents plane
from .auth import AuthAPI
from .mfa import MfaAPI
from .accounts import AccountsAPI
from .billing import BillingAPI
from .runner import RunnerAPI
from .projects import ProjectsAPI
from .pipelines import PipelinesAPI
from .hitl import HitlAPI
from .credentials import CredentialsAPI
from .connectors import ConnectorsAPI
from .phone import PhoneAPI
from .team import TeamAPI
from .templates import TemplatesAPI
from .assistant import AssistantAPI
from .bugs import BugsAPI
from .evals import EvalsAPI
from .events import MelayaEvents

DEFAULT_BASE_URL = "https://api.melaya.org"
DEFAULT_WS_URL = "wss://wss.melaya.org"

logger = logging.getLogger(__name__)

_RETRY_STATUSES = {429, 500, 502, 503, 504}
_DEFAULT_MAX_RETRIES = 3
_DEFAULT_BACKOFF_BASE = 1.0  # seconds
_DEFAULT_TIMEOUT_MS = 30_000  # milliseconds — 30s per request
_GET_MAX_RETRIES = 2  # bounded retries for idempotent GETs only


# ── Namespace objects ─────────────────────────────────────────────────────────
# These are plain attribute-holder classes — zero logic, zero overhead.
# They are the *primary* documented API surface; the flat top-level accessors
# on Melaya (m.market, m.pipelines, …) remain as aliases.


class TradingNamespace:
    """``melaya.trading`` — market data, execution, simulation, and streaming.

    Preview — Melaya Trading is not generally available; do not use with real
    funds.

    Attributes
    ----------
    market:
        REST market-data + reference endpoints (public plane).
    account:
        Authenticated account reads: connected keys, tier limits, usage.
    sim:
        Paper trading (sim broker): virtual balance, positions, and orders.
    strategies:
        Launch, control, and inspect trading strategies (paper + live).
    backtest:
        Historical backtests + parameter sweeps on the Rust engine.
    trade:
        Live credentialed trading on a connected exchange (real funds).
    stream:
        WebSocket streaming endpoints (public market data + private feeds).

    Example
    -------
    >>> m.trading.market.ticker(exchange="binance", symbol="BTC/USDT", market="spot")
    >>> m.trading.strategies.create(name="bot", strategy_type="custom", ...)
    >>> async for t in m.trading.stream.ticker(exchange="binance", symbol="BTC/USDT", market="spot"):
    ...     print(t["last"])
    """

    def __init__(
        self,
        market: MarketAPI,
        account: AccountAPI,
        sim: SimAPI,
        strategies: StrategiesAPI,
        backtest: BacktestAPI,
        trade: TradeAPI,
        stream: StreamAPI,
    ) -> None:
        self.market = market
        self.account = account
        self.sim = sim
        self.strategies = strategies
        self.backtest = backtest
        self.trade = trade
        self.stream = stream


class AgentsNamespace:
    """``melaya.agents`` — AI agent pipelines, HITL, assistant, phone, and evals.

    Attributes
    ----------
    pipelines:
        Pipeline run overview, traces, and cron schedules.
    hitl:
        Human-in-the-loop approval queue: list pending, approve, reject.
    assistant:
        Assistant onboarding profile (get + set).
    phone:
        Phone device control: pair, list, screen-tree, apps.
    evals:
        Eval runs, summaries, memory graphs, and benchmarks.

    Example
    -------
    >>> pending = m.agents.hitl.pending()
    >>> m.agents.hitl.approve(pending[0]["requestId"])
    >>> runs = m.agents.pipelines.list(project="my-project")
    >>> m.agents.assistant.set_profile({"name": "Antoine"})
    """

    def __init__(
        self,
        pipelines: PipelinesAPI,
        hitl: HitlAPI,
        assistant: AssistantAPI,
        phone: PhoneAPI,
        evals: EvalsAPI,
    ) -> None:
        self.pipelines = pipelines
        self.hitl = hitl
        self.assistant = assistant
        self.phone = phone
        self.evals = evals


class PlatformNamespace:
    """``melaya.platform`` — account, projects, billing, team, and infrastructure.

    Attributes
    ----------
    projects:
        Create and list agent projects.
    credentials:
        User-scoped credential storage (services, OAuth, env handles).
    connectors:
        Project-scoped connector credentials (per-project service keys).
    billing:
        Billing: subscription status, Stripe checkout/portal, pricing plans.
    team:
        Project team management: members, roles, and invite links.
    templates:
        Pipeline templates: create, share, assign, and manage visibility.
    runner:
        Runner token management: mint, list, revoke ``mel_run_`` tokens.
    auth:
        Authentication: login, register, MFA, password management, session.
    mfa:
        TOTP MFA setup and enrollment status.
    accounts:
        User profile, credits, and CEX key management.
    bugs:
        Bug reports: submit, list, comment, and notifications.
    events:
        Platform real-time events over Socket.IO at ``/api/v1/events``.

    Example
    -------
    >>> projects = m.platform.projects.list()
    >>> m.platform.credentials.set("openai", value="sk-...")
    >>> sub = m.platform.billing.subscription()
    >>> members = m.platform.team.list_members("my-project")
    """

    def __init__(
        self,
        projects: ProjectsAPI,
        credentials: CredentialsAPI,
        connectors: ConnectorsAPI,
        billing: BillingAPI,
        team: TeamAPI,
        templates: TemplatesAPI,
        runner: RunnerAPI,
        auth: AuthAPI,
        mfa: MfaAPI,
        accounts: AccountsAPI,
        bugs: BugsAPI,
        events: MelayaEvents,
    ) -> None:
        self.projects = projects
        self.credentials = credentials
        self.connectors = connectors
        self.billing = billing
        self.team = team
        self.templates = templates
        self.runner = runner
        self.auth = auth
        self.mfa = mfa
        self.accounts = accounts
        self.bugs = bugs
        self.events = events


class Melaya:
    """Unified client for the Melaya platform API (trading + agents + platform).

    Parameters
    ----------
    api_key:
        Your Melaya platform API key, prefixed ``mk_``.
        Create one at melaya.org → Settings → API Keys.
    base_url:
        Override the REST base URL. Defaults to ``https://api.melaya.org``.
    ws_url:
        Override the WebSocket base URL. Defaults to ``wss://wss.melaya.org``.
    timeout:
        HTTP request timeout in seconds (default 30). Applied per request via
        ``AbortController``-equivalent (httpx ``Timeout``).
    timeout_ms:
        HTTP request timeout in milliseconds. When set, overrides ``timeout``.
    max_retries:
        Maximum number of retry attempts for GET requests on 429/5xx
        responses (default 3, hard-capped at 2). Non-GET requests are never
        retried (non-idempotent).
    session_jwt:
        Optional session JWT returned by ``auth.login()``. When provided it
        overrides the ``mk_`` key in the ``Authorization`` header.

    Example
    -------
    >>> from melaya import Melaya
    >>> m = Melaya(api_key="mk_...")
    >>> # Namespaced (primary API)
    >>> t = m.trading.market.ticker(exchange="binance", symbol="BTC/USDT", market="spot")
    >>> runs = m.agents.pipelines.list(project="my-project")
    >>> pending = m.agents.hitl.pending()
    >>> projects = m.platform.projects.list()
    >>> # Flat aliases (backwards compatible)
    >>> t = m.market.ticker(exchange="binance", symbol="BTC/USDT", market="spot")
    >>> pending = m.hitl.pending()
    """

    # ── Namespaced API (primary) ───────────────────────────────────────────────
    trading: TradingNamespace
    """Grouped trading namespace: market, account, sim, strategies, backtest, trade, stream
    (preview — not for real funds)."""
    agents: AgentsNamespace
    """Grouped agents namespace: pipelines, hitl, assistant, phone, evals."""
    platform: PlatformNamespace
    """Grouped platform namespace: projects, credentials, connectors, billing, team,
    templates, runner, auth, mfa, accounts, bugs, events."""

    # ── Trading plane (flat aliases) ───────────────────────────────────────────
    market: MarketAPI
    """REST market-data + reference endpoints (public plane). Alias: ``m.trading.market``."""
    account: AccountAPI
    """Authenticated account reads: connected keys, tier limits, usage. Alias: ``m.trading.account``."""
    sim: SimAPI
    """Paper trading (sim broker): virtual balance, positions, and orders. Alias: ``m.trading.sim``."""
    strategies: StrategiesAPI
    """Launch, control, and inspect trading strategies (paper + live). Alias: ``m.trading.strategies``."""
    backtest: BacktestAPI
    """Historical backtests + parameter sweeps on the Rust engine. Alias: ``m.trading.backtest``."""
    trade: TradeAPI
    """Live credentialed trading on a connected exchange (real funds). Alias: ``m.trading.trade``."""
    stream: StreamAPI
    """WebSocket streaming endpoints (public market data + private feeds). Alias: ``m.trading.stream``."""

    # ── Agents plane (flat aliases) ────────────────────────────────────────────
    pipelines: PipelinesAPI
    """Pipeline run overview, traces, and cron schedules. Alias: ``m.agents.pipelines``."""
    hitl: HitlAPI
    """Human-in-the-loop approval queue: list pending, approve, reject. Alias: ``m.agents.hitl``."""
    assistant: AssistantAPI
    """Assistant onboarding profile (get + set). Alias: ``m.agents.assistant``."""
    phone: PhoneAPI
    """Phone device control: pair, list, screen-tree, apps. Alias: ``m.agents.phone``."""
    evals: EvalsAPI
    """Eval runs, summaries, memory graphs, and benchmarks. Alias: ``m.agents.evals``."""

    # ── Platform plane (flat aliases) ──────────────────────────────────────────
    auth: AuthAPI
    """Authentication: login, register, MFA, password management, session. Alias: ``m.platform.auth``."""
    mfa: MfaAPI
    """TOTP MFA setup and enrollment status. Alias: ``m.platform.mfa``."""
    accounts: AccountsAPI
    """User profile, credits, and CEX key management. Alias: ``m.platform.accounts``."""
    billing: BillingAPI
    """Billing: subscription status, Stripe checkout/portal, pricing plans. Alias: ``m.platform.billing``."""
    runner: RunnerAPI
    """Runner token management: mint, list, revoke ``mel_run_`` tokens. Alias: ``m.platform.runner``."""
    projects: ProjectsAPI
    """Create and list agent projects. Alias: ``m.platform.projects``."""
    credentials: CredentialsAPI
    """User-scoped credential storage (services, OAuth, env handles). Alias: ``m.platform.credentials``."""
    connectors: ConnectorsAPI
    """Project-scoped connector credentials (per-project service keys). Alias: ``m.platform.connectors``."""
    team: TeamAPI
    """Project team management: members, roles, and invite links. Alias: ``m.platform.team``."""
    templates: TemplatesAPI
    """Pipeline templates: create, share, assign, and manage visibility. Alias: ``m.platform.templates``."""
    bugs: BugsAPI
    """Bug reports: submit, list, comment, and notifications. Alias: ``m.platform.bugs``."""
    events: MelayaEvents
    """Platform real-time events over Socket.IO at ``/api/v1/events``. Alias: ``m.platform.events``."""

    def __init__(
        self,
        api_key: str,
        *,
        base_url: str = DEFAULT_BASE_URL,
        ws_url: str = DEFAULT_WS_URL,
        timeout: float = 30.0,
        timeout_ms: Optional[int] = None,
        max_retries: int = _DEFAULT_MAX_RETRIES,
        session_jwt: Optional[str] = None,
    ) -> None:
        if not api_key:
            raise ValueError(
                "Melaya: api_key is required (create one at melaya.org -> Settings -> API Keys)."
            )
        if not api_key.startswith("mk_"):
            raise ValueError("Melaya: API keys must be prefixed 'mk_'.")

        self._api_key = api_key
        self._session_jwt = session_jwt
        self._base_url = base_url.rstrip("/")
        self._max_retries = max_retries

        # Resolve per-request timeout: timeout_ms takes priority over timeout (seconds)
        effective_timeout = (timeout_ms / 1000.0) if timeout_ms is not None else timeout
        self._request_timeout = effective_timeout

        # Build httpx client. The Authorization header uses the JWT when
        # provided, falling back to the mk_ key. The key is sent ONLY in this
        # header — never in the query string.
        bearer = session_jwt if session_jwt else api_key
        self._http = httpx.Client(
            base_url=base_url,
            timeout=effective_timeout,
            headers={"Authorization": f"Bearer {bearer}"},
        )

        # ── Trading plane ──────────────────────────────────────────────────────
        self.market = MarketAPI(self._request)
        self.account = AccountAPI(self._request)
        self.sim = SimAPI(self._request)
        self.strategies = StrategiesAPI(self._request)
        self.backtest = BacktestAPI(self._request)
        self.trade = TradeAPI(self._request)
        self.stream = StreamAPI(api_key, ws_url, self._request)

        # ── Platform / agents plane ────────────────────────────────────────────
        self.auth = AuthAPI(self._request)
        self.mfa = MfaAPI(self._request)
        self.accounts = AccountsAPI(self._request)
        self.billing = BillingAPI(self._request)
        self.runner = RunnerAPI(self._request)
        self.projects = ProjectsAPI(self._request)
        self.pipelines = PipelinesAPI(self._request)
        self.hitl = HitlAPI(self._request)
        self.credentials = CredentialsAPI(self._request)
        self.connectors = ConnectorsAPI(self._request)
        self.phone = PhoneAPI(self._request)
        self.team = TeamAPI(self._request)
        self.templates = TemplatesAPI(self._request)
        self.assistant = AssistantAPI(self._request)
        self.bugs = BugsAPI(self._request)
        self.evals = EvalsAPI(self._request)
        self.events = MelayaEvents(base_url=base_url, api_key=api_key)

        # ── Namespace groupings (primary documented API) ───────────────────────
        # Each namespace holds references to the same module instances as the
        # flat aliases above — no duplication of state, no extra HTTP clients.
        self.trading = TradingNamespace(
            market=self.market,
            account=self.account,
            sim=self.sim,
            strategies=self.strategies,
            backtest=self.backtest,
            trade=self.trade,
            stream=self.stream,
        )
        self.agents = AgentsNamespace(
            pipelines=self.pipelines,
            hitl=self.hitl,
            assistant=self.assistant,
            phone=self.phone,
            evals=self.evals,
        )
        self.platform = PlatformNamespace(
            projects=self.projects,
            credentials=self.credentials,
            connectors=self.connectors,
            billing=self.billing,
            team=self.team,
            templates=self.templates,
            runner=self.runner,
            auth=self.auth,
            mfa=self.mfa,
            accounts=self.accounts,
            bugs=self.bugs,
            events=self.events,
        )

    # ── HTTP transport ─────────────────────────────────────────────────────────

    def _request(
        self,
        method: str,
        path: str,
        *,
        params: Optional[Dict[str, Any]] = None,
        json: Any = None,
    ) -> Any:
        """Execute an HTTP request with per-request timeout and bounded retry.

        Retry policy:
          - GET requests only: at most min(max_retries, 2) retries on network
            error / 429 / 5xx.
          - Non-GET (POST/PUT/PATCH/DELETE): no retry (non-idempotent).
          - On 429 with a Retry-After header: sleep for that duration instead of
            the exponential backoff (not in addition to it).
          - On other retryable errors: exponential backoff with full jitter.
          - Per-request timeout enforced via httpx Timeout parameter.

        For REST requests the API key is carried in the ``Authorization``
        header only, never in the query string. Tokens are never logged.
        """
        # SECURITY (REST): the API key travels ONLY in the Authorization header
        # (set on the httpx.Client in __init__). It must NEVER be added to a
        # REST query string, where it would leak into access logs, proxies, and
        # error reports. WebSocket streams follow the server protocol instead:
        # public streams carry ?apiKey= in the wss URL; private streams carry a
        # short-lived one-shot ?wsTicket= — see stream.py.
        query: Dict[str, Any] = {k: v for k, v in (params or {}).items() if v is not None}

        is_get = method.upper() == "GET"
        # Non-idempotent methods get 0 retries; GET gets at most _GET_MAX_RETRIES,
        # further capped by the caller-supplied max_retries.
        get_retries = min(self._max_retries, _GET_MAX_RETRIES)
        max_attempts = (1 + get_retries) if is_get else 1

        last_exc: Optional[Exception] = None
        skip_backoff = False
        for attempt in range(max_attempts):
            if attempt > 0 and not skip_backoff:
                # Exponential backoff with full jitter: sleep in [0, base * 2^(attempt-1)]
                cap = _DEFAULT_BACKOFF_BASE * (2 ** (attempt - 1))
                backoff = random.uniform(0, cap)
                logger.debug("Melaya: retrying %s %s in %.2fs (attempt %d)", method, path, backoff, attempt)
                time.sleep(backoff)
            skip_backoff = False

            try:
                resp = self._http.request(
                    method,
                    path,
                    params=query,
                    json=json,
                    timeout=self._request_timeout,
                )
            except httpx.TimeoutException as exc:
                last_exc = exc
                if is_get:
                    continue
                raise MelayaError(
                    f"Melaya: request timed out after {self._request_timeout}s",
                    status=None,
                ) from exc
            except httpx.TransportError as exc:
                last_exc = exc
                if is_get:
                    continue
                raise MelayaError(
                    f"Melaya: network error: {exc}",
                    status=None,
                ) from exc

            # Retry on transient server errors (GET only — non-GET falls through to error below)
            if resp.status_code in _RETRY_STATUSES and attempt < max_attempts - 1 and is_get:
                # Honour Retry-After if present; use it INSTEAD of the generic backoff.
                retry_after = resp.headers.get("Retry-After")
                if retry_after:
                    try:
                        time.sleep(float(retry_after))
                        skip_backoff = True  # already slept — skip backoff at top of next loop
                    except ValueError:
                        pass
                last_exc = MelayaError(
                    f"Melaya API {resp.status_code} (retryable)",
                    status=resp.status_code,
                )
                continue

            # Parse response body
            try:
                data = resp.json() if resp.content else None
            except ValueError:
                data = resp.text

            # Map HTTP errors to MelayaError
            if resp.status_code >= 400:
                code: Optional[str] = None
                message: Optional[str] = None
                if isinstance(data, dict):
                    # Two error envelope shapes:
                    # 1) {error: "tier_insufficient", tier: "..."}
                    # 2) {error: "...", message: "...", code: "..."}
                    code = data.get("error") or data.get("code")
                    message = data.get("message")
                raise MelayaError(
                    f"Melaya API {resp.status_code}"
                    + (f" ({code})" if code else "")
                    + (f": {message}" if message else ""),
                    status=resp.status_code,
                    code=code,
                    body=data,
                )

            # Surface ok:false request-level failures
            if isinstance(data, dict) and data.get("ok") is False:
                code = data.get("error")
                raise MelayaError(
                    "Melaya API request failed" + (f": {code}" if code else ""),
                    status=resp.status_code,
                    code=code,
                    body=data,
                )

            return data

        # All retries exhausted
        if last_exc is not None:
            if isinstance(last_exc, MelayaError):
                raise last_exc
            raise MelayaError(
                f"Melaya: request failed after {_GET_MAX_RETRIES} retries: {last_exc}",
                status=None,
            ) from last_exc

        return None  # unreachable in practice

    # ── Session JWT hot-swap ──────────────────────────────────────────────────

    def set_session_jwt(self, jwt: str) -> None:
        """Replace the Authorization header with a user session JWT.

        Call this after ``m.auth.login()`` to use the session token for
        subsequent requests without recreating the client.
        """
        self._session_jwt = jwt
        self._http.headers.update({"Authorization": f"Bearer {jwt}"})

    def clear_session_jwt(self) -> None:
        """Revert Authorization header back to the ``mk_`` API key."""
        self._session_jwt = None
        self._http.headers.update({"Authorization": f"Bearer {self._api_key}"})

    # ── Version ────────────────────────────────────────────────────────────────

    def version(self) -> Any:
        """Get the current server version string. Public endpoint."""
        return self._request("GET", "/api/v1/version")

    # ── Lifecycle ──────────────────────────────────────────────────────────────

    def close(self) -> None:
        """Close the underlying HTTP connection pool."""
        self._http.close()
        self.events.close()

    def __enter__(self) -> "Melaya":
        return self

    def __exit__(self, *exc: Any) -> None:
        self.close()
