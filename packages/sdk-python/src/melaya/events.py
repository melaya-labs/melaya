"""Platform real-time events via Socket.IO at ``/api/v1/events``.

The ``MelayaEvents`` class opens a Socket.IO v4 connection (Engine.IO v4
underneath) and exposes typed subscription helpers that mirror the TypeScript
SDK's ``MelayaEvents``.

Transport strategy: HTTP long-poll for the Engine.IO handshake (spec mandates
it), then upgrade to WebSocket if ``websockets`` is available. Falls back to
long-poll-only in environments without a WebSocket library.

Room semantics:
    - ``run:<runId>``        — events for a specific pipeline run
    - ``project:<project>``  — all events in a project
    - ``hitl:user:<userId>`` — HITL approval events (joined automatically by
                               the server on connect)

Requires the optional ``websockets`` extra::

    pip install "melaya[stream]"

Example
-------
>>> import asyncio
>>> from melaya import Melaya
>>> async def main():
...     m = Melaya(api_key="mk_...")
...     await m.events.connect()
...     m.events.on_run_update("run-123", lambda e: print(e["event_type"]))
...     await m.events.wait_closed()
>>> asyncio.run(main())
"""
from __future__ import annotations

import asyncio
import json
import logging
import time
from typing import Any, Callable, Dict, Optional, Set
from urllib.parse import urlencode, urlparse

from .errors import MelayaError

try:
    import websockets  # type: ignore
    _HAS_WEBSOCKETS = True
except ImportError:  # pragma: no cover
    _HAS_WEBSOCKETS = False

try:
    import httpx as _httpx  # type: ignore
    _HAS_HTTPX = True
except ImportError:  # pragma: no cover
    _HAS_HTTPX = False

logger = logging.getLogger(__name__)

_Listener = Callable[[Any], None]

# Engine.IO v4 packet types (string prefix)
_EIO_OPEN = "0"
_EIO_PING = "2"
_EIO_PONG = "3"
_EIO_MESSAGE = "4"

# Socket.IO v4 packet types (int, embedded after EIO message prefix)
_SIO_CONNECT = 0
_SIO_EVENT = 2


class MelayaEvents:
    """Async Socket.IO v4 client for Melaya platform real-time events.

    Instantiate once and call the ``on_*`` helpers to subscribe.
    The connection is established lazily on the first call to :meth:`connect`.

    Most users should drive this via ``melaya.events``::

        m = Melaya(api_key="mk_...")
        await m.events.connect()
        m.events.on_run_update("run-123", lambda e: print(e))
        # ... do work ...
        m.events.close()
    """

    def __init__(self, *, base_url: str, api_key: str) -> None:
        self._base_url = base_url.rstrip("/")
        # Derive the events URL (http/https base, path /api/v1/events)
        parsed = urlparse(self._base_url)
        # Keep http/https scheme for long-poll; ws upgrade uses wss/ws
        self._poll_base = f"{parsed.scheme}://{parsed.netloc}"
        self._api_key = api_key

        self._sid: Optional[str] = None
        self._closed = False
        self._connected = False
        self._ws: Optional[Any] = None
        self._ws_reconnect_delay = 1.0

        self._event_listeners: Dict[str, Set[_Listener]] = {}
        self._joined_rooms: Set[str] = set()
        # room → {(event, listener)} so leave_run/leave_project can drop the
        # listeners that were registered for that room.
        self._room_listeners: Dict[str, Set[Any]] = {}

        # Background tasks
        self._connect_task: Optional[asyncio.Task] = None  # type: ignore[type-arg]
        self._poll_task: Optional[asyncio.Task] = None  # type: ignore[type-arg]
        self._ws_task: Optional[asyncio.Task] = None  # type: ignore[type-arg]

    # ── Public subscription API ────────────────────────────────────────────────

    def on_run_update(self, run_id: str, cb: _Listener) -> Callable[[], None]:
        """Subscribe to run events for ``run:<run_id>``. Returns an unsubscribe fn.

        Payloads carrying a ``runId`` field are delivered only when it matches
        ``run_id``, so subscribing to multiple runs never cross-delivers events.
        """
        room = f"run:{run_id}"
        self._join_room(room)
        return self._on_scoped(room, "pushEvent", cb, key="runId", expected=run_id)

    def on_init_phase(self, run_id: str, cb: _Listener) -> Callable[[], None]:
        """Subscribe to init-phase progress events for a run. Returns unsubscribe fn."""
        room = f"run:{run_id}"
        self._join_room(room)
        return self._on_scoped(room, "pushInitPhase", cb, key="runId", expected=run_id)

    def on_project_event(self, project: str, cb: _Listener) -> Callable[[], None]:
        """Subscribe to all events in a project room. Returns unsubscribe fn.

        Payloads carrying a ``projectId`` field are delivered only when it
        matches ``project``, so multi-project subscribers stay isolated.
        """
        room = f"project:{project}"
        self._join_room(room)
        return self._on_scoped(room, "pushEvent", cb, key="projectId", expected=project)

    def on_hitl_approval(self, cb: _Listener) -> Callable[[], None]:
        """Subscribe to HITL approval events. The server auto-joins the hitl room on connect."""
        return self._on("pushHitlApprovals", cb)

    def on_pipeline_created(self, project: str, cb: _Listener) -> Callable[[], None]:
        """Subscribe to pipeline CRUD created events within a project room."""
        room = f"project:{project}"
        self._join_room(room)
        return self._on_scoped(room, "pipelineCreated", cb, key="projectId", expected=project)

    def on_pipeline_updated(self, project: str, cb: _Listener) -> Callable[[], None]:
        """Subscribe to pipeline CRUD updated events within a project room."""
        room = f"project:{project}"
        self._join_room(room)
        return self._on_scoped(room, "pipelineUpdated", cb, key="projectId", expected=project)

    def on_pipeline_deleted(self, project: str, cb: _Listener) -> Callable[[], None]:
        """Subscribe to pipeline CRUD deleted events within a project room."""
        room = f"project:{project}"
        self._join_room(room)
        return self._on_scoped(room, "pipelineDeleted", cb, key="projectId", expected=project)

    def leave_run(self, run_id: str) -> None:
        """Stop listening to a specific run room and drop its listeners."""
        self._leave_room(f"run:{run_id}")

    def leave_project(self, project: str) -> None:
        """Leave a project room and drop its listeners."""
        self._leave_room(f"project:{project}")

    def close(self) -> None:
        """Close the Socket.IO connection and release all listeners."""
        self._closed = True
        self._connected = False
        self._event_listeners.clear()
        self._joined_rooms.clear()
        self._room_listeners.clear()
        for task in (self._connect_task, self._poll_task, self._ws_task):
            if task is not None and not task.done():
                task.cancel()

    # ── Async connect lifecycle ─────────────────────────────────────────────────

    async def connect(self) -> None:
        """Initiate the Engine.IO handshake and background tasks.

        Call once after creating the instance. Safe to call multiple times.
        """
        if self._closed or self._connected:
            return
        self._connect_task = asyncio.ensure_future(self._connect_loop())

    async def wait_closed(self) -> None:
        """Await until :meth:`close` is called. Useful in long-running scripts."""
        while not self._closed:
            await asyncio.sleep(1)

    # ── Internal helpers ───────────────────────────────────────────────────────

    def _on(self, event: str, cb: _Listener) -> Callable[[], None]:
        if event not in self._event_listeners:
            self._event_listeners[event] = set()
        self._event_listeners[event].add(cb)

        def _unsub() -> None:
            self._event_listeners.get(event, set()).discard(cb)

        return _unsub

    def _on_scoped(
        self,
        room: str,
        event: str,
        cb: _Listener,
        *,
        key: str,
        expected: str,
    ) -> Callable[[], None]:
        """Register a room-scoped listener.

        The callback fires only when the payload's ``key`` field (e.g.
        ``runId``/``projectId``) matches ``expected`` — payloads without that
        field are delivered as-is. The (event, listener) pair is tracked per
        room so :meth:`leave_run`/:meth:`leave_project` can remove it.
        """

        def _scoped(payload: Any) -> None:
            if isinstance(payload, dict):
                scope = payload.get(key)
                if scope is not None and str(scope) != expected:
                    return  # event belongs to a different room — filter out
            cb(payload)

        unsub = self._on(event, _scoped)
        self._room_listeners.setdefault(room, set()).add((event, _scoped))

        def _unsub() -> None:
            unsub()
            self._room_listeners.get(room, set()).discard((event, _scoped))

        return _unsub

    def _emit_event(self, event: str, payload: Any) -> None:
        for cb in list(self._event_listeners.get(event, set())):
            try:
                cb(payload)
            except Exception:  # noqa: BLE001 — listener errors must not crash the client
                logger.debug("MelayaEvents: listener error", exc_info=True)

    def _join_room(self, room: str) -> None:
        if room in self._joined_rooms:
            return
        self._joined_rooms.add(room)
        # If already connected, send join immediately; otherwise replayed on connect
        if self._connected:
            asyncio.ensure_future(self._send(self._room_join_packet(room)))

    def _leave_room(self, room: str) -> None:
        # Drop from the rejoin-on-reconnect list AND remove the listeners that
        # were registered for this room.
        self._joined_rooms.discard(room)
        for event, listener in self._room_listeners.pop(room, set()):
            self._event_listeners.get(event, set()).discard(listener)
        # Emit leaveRoom with the FULL room string (e.g. "run:<id>", "project:<name>")
        if self._connected:
            asyncio.ensure_future(self._send(self._sio_event_packet("leaveRoom", room)))

    def _handle_sio_event(self, args: Any) -> None:
        if not isinstance(args, list) or len(args) < 2:
            return
        event, payload = args[0], args[1]
        self._emit_event(str(event), payload)

    def _handle_packet(self, raw: str) -> None:
        if not raw:
            return
        eio_type = raw[0]
        if eio_type == _EIO_PING:
            # Heartbeat — answer promptly on whatever transport is active.
            # On long-poll this POSTs "3" back to the polling URL; without it
            # the server drops the session after pingInterval + pingTimeout.
            asyncio.ensure_future(self._send(_EIO_PONG))
            return
        if eio_type != _EIO_MESSAGE:
            return
        sio_raw = raw[1:]
        if not sio_raw:
            return
        try:
            sio_type = int(sio_raw[0])
        except (ValueError, IndexError):
            return
        if sio_type != _SIO_EVENT:
            return
        try:
            args = json.loads(sio_raw[1:])
            self._handle_sio_event(args)
        except (json.JSONDecodeError, Exception):  # noqa: BLE001
            pass

    def _parse_poll_response(self, text: str) -> None:
        """Parse an Engine.IO v4 poll body: packets separated by 0x1e (record separator)."""
        for packet in text.split("\x1e"):
            if packet:
                self._handle_packet(packet)

    def _poll_url(self, extra: Optional[Dict[str, str]] = None) -> str:
        params: Dict[str, str] = {
            "EIO": "4",
            "transport": "polling",
        }
        if self._sid:
            params["sid"] = self._sid
        if extra:
            params.update(extra)
        return f"{self._poll_base}/api/v1/events/?{urlencode(params)}"

    def _ws_url(self) -> str:
        parsed = urlparse(self._poll_base)
        ws_scheme = "wss" if parsed.scheme == "https" else "ws"
        params: Dict[str, str] = {
            "EIO": "4",
            "transport": "websocket",
        }
        if self._sid:
            params["sid"] = self._sid
        return f"{ws_scheme}://{parsed.netloc}/api/v1/events/?{urlencode(params)}"

    def _sio_connect_packet(self) -> str:
        """Engine.IO message (4) + Socket.IO connect (0) + auth JSON."""
        auth = json.dumps({"token": self._api_key})
        return f"{_EIO_MESSAGE}{_SIO_CONNECT}{auth}"

    def _sio_event_packet(self, event: str, *args: Any) -> str:
        return f"{_EIO_MESSAGE}{_SIO_EVENT}{json.dumps([event, *args])}"

    async def _replay_joins(self) -> None:
        for room in list(self._joined_rooms):
            await self._send(self._room_join_packet(room))

    def _room_join_packet(self, room: str) -> str:
        """Return the correct Socket.IO event packet for joining a room."""
        if room.startswith("run:"):
            return self._sio_event_packet("joinRunRoom", room[4:])
        if room.startswith("project:"):
            return self._sio_event_packet("joinProjectRoom", room[8:])
        # Safe fallback for unknown room prefixes
        return self._sio_event_packet("joinRunRoom", room)

    async def _send(self, data: str) -> None:
        """Send a packet on the active transport: WebSocket if upgraded, else
        POST it to the polling URL (Engine.IO v4 polling requires outbound
        packets — pongs, joinRoom emits — to be POSTed with the sid)."""
        ws = self._ws
        if ws is not None:
            try:
                await ws.send(data)
            except Exception:  # noqa: BLE001
                logger.debug("MelayaEvents: WS send failed", exc_info=True)
            return
        await self._poll_post(data)

    async def _poll_post(self, data: str) -> None:
        """POST an outbound Engine.IO packet to the polling URL (needs a sid)."""
        if not _HAS_HTTPX or not self._sid:
            return
        headers = {
            "Authorization": f"Bearer {self._api_key}",
            "Content-Type": "text/plain;charset=UTF-8",
        }
        try:
            async with _httpx.AsyncClient() as client:
                await client.post(self._poll_url(), content=data, headers=headers)
        except Exception:  # noqa: BLE001
            logger.debug("MelayaEvents: poll POST failed", exc_info=True)

    # ── Connection loops ────────────────────────────────────────────────────────

    async def _connect_loop(self) -> None:
        """Engine.IO handshake → SIO connect → upgrade or poll."""
        retry_delay = 1.0
        while not self._closed:
            try:
                await self._do_connect()
                retry_delay = 1.0
                return
            except Exception as exc:  # noqa: BLE001
                logger.debug("MelayaEvents: connect failed (%s), retrying in %.1fs", exc, retry_delay)
                await asyncio.sleep(retry_delay)
                retry_delay = min(retry_delay * 2, 30.0)

    async def _do_connect(self) -> None:
        if not _HAS_HTTPX:
            raise MelayaError(
                "MelayaEvents: httpx is required for the events client. "
                "It is a core dependency and should always be present."
            )
        headers = {"Authorization": f"Bearer {self._api_key}"}
        async with _httpx.AsyncClient() as client:
            # Step 1: Engine.IO handshake (GET)
            resp = await client.get(self._poll_url(), headers=headers)
            resp.raise_for_status()
            text = resp.text
            json_start = text.find("{")
            if json_start == -1:
                raise MelayaError("MelayaEvents: unexpected EIO handshake response")
            open_data = json.loads(text[json_start:])
            self._sid = open_data["sid"]
            ping_interval = open_data.get("pingInterval", 25000) / 1000

            # Step 2: Socket.IO connect packet (POST)
            await client.post(
                self._poll_url(),
                content=self._sio_connect_packet(),
                headers={**headers, "Content-Type": "text/plain;charset=UTF-8"},
            )

        self._connected = True

        # Step 3: Upgrade to WebSocket if available, otherwise long-poll
        if _HAS_WEBSOCKETS:
            self._ws_task = asyncio.ensure_future(self._ws_loop())
        else:
            self._poll_task = asyncio.ensure_future(self._poll_loop(ping_interval))

        # Replay queued room joins
        await asyncio.sleep(0.1)  # give the WS a moment to open
        await self._replay_joins()

    async def _ws_connect(self, ws_url: str, headers: Dict[str, str]) -> Any:
        """Open the WebSocket with header-kwarg compat across websockets versions.

        ``websockets`` >= 13 renamed ``extra_headers`` to ``additional_headers``
        (passing the old name raises ``TypeError``). Try the modern name first
        and fall back on ``TypeError`` — never silently: the fallback is logged
        at WARNING so version-compat problems stay visible.
        """
        try:
            return await websockets.connect(ws_url, additional_headers=headers)  # type: ignore[attr-defined]
        except TypeError as exc:
            logger.warning(
                "MelayaEvents: installed 'websockets' rejected additional_headers (%s); "
                "retrying with extra_headers for websockets < 13",
                exc,
            )
            return await websockets.connect(ws_url, extra_headers=headers)  # type: ignore[attr-defined]

    async def _ws_loop(self) -> None:
        """Maintain the WebSocket upgrade connection."""
        self._ws = None
        ws_url = self._ws_url()
        headers = {"Authorization": f"Bearer {self._api_key}"}
        try:
            ws = await self._ws_connect(ws_url, headers)
            try:
                self._ws = ws
                # Engine.IO upgrade probe
                await ws.send("2probe")
                await ws.send("5")  # upgrade packet
                # Socket actually opened — reset the reconnect backoff.
                self._ws_reconnect_delay = 1.0
                async for raw in ws:
                    if self._closed:
                        break
                    if isinstance(raw, bytes):
                        raw = raw.decode()
                    # Respond to server heartbeat pings
                    if raw == _EIO_PING:
                        await ws.send(_EIO_PONG)
                        continue
                    self._handle_packet(raw)
            finally:
                await ws.close()
        except asyncio.CancelledError:
            pass
        except Exception as exc:  # noqa: BLE001
            logger.debug("MelayaEvents: WS disconnected (%s), reconnecting", exc)
        finally:
            self._ws = None
            self._connected = False
            self._sid = None
        if not self._closed:
            # Bounded reconnect backoff: start at 1s, cap at 30s
            delay = self._ws_reconnect_delay
            await asyncio.sleep(delay)
            self._ws_reconnect_delay = min(delay * 2, 30.0)
            self._connect_task = asyncio.ensure_future(self._connect_loop())

    async def _poll_loop(self, interval: float) -> None:
        """HTTP long-poll fallback loop (Engine.IO v4 polling transport).

        The GET blocks server-side until packets are available, so we re-poll
        immediately after each response. Server pings ("2") arrive in the poll
        body and are answered by POSTing pong ("3") via :meth:`_send`. An HTTP
        400 means the sid is dead ("Session ID unknown") — the only recovery
        is a full re-handshake.
        """
        headers = {"Authorization": f"Bearer {self._api_key}"}
        dead_session = False
        try:
            # Generous read timeout: a long-poll GET can idle up to pingInterval.
            async with _httpx.AsyncClient(timeout=interval + 30.0) as client:
                while not self._closed:
                    try:
                        resp = await client.get(self._poll_url(), headers=headers)
                    except Exception:  # noqa: BLE001 — transient transport error
                        await asyncio.sleep(1.0)
                        continue
                    if resp.status_code == 200:
                        self._parse_poll_response(resp.text)
                        continue
                    if resp.status_code == 400:
                        # Dead session ("Session ID unknown") → full re-handshake
                        logger.warning(
                            "MelayaEvents: polling session died (HTTP 400), re-handshaking"
                        )
                        dead_session = True
                        break
                    await asyncio.sleep(1.0)  # unexpected status — brief pause
        except asyncio.CancelledError:
            return
        if dead_session and not self._closed:
            self._connected = False
            self._sid = None
            self._connect_task = asyncio.ensure_future(self._connect_loop())
