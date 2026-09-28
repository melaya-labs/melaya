"""Triggers API — read, diagnose and dry-run event triggers.

Maps to ``/api/v1/private/triggers/*``. Event triggers start your pipelines
from outside events (webhooks, streams, polls, push, engine). This module
lists and inspects them, reads their delivery receipts, verdict stats,
pending approvals and the live event log, dry-runs an event, and checks or
tests a poll trigger. Every read is scoped to your own triggers server-side.

Create, update, delete and secret rotation are deliberately not on REST:
push consent and autonomy grants are enforced in the Agent Builder and the
MCP server, so manage triggers there.

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> triggers = m.agents.triggers.list(project="acme")
>>> tid = triggers[0]["id"]
>>> m.agents.triggers.deliveries(tid, limit=20)
>>> m.agents.triggers.test(tid, {"amount": 120})
>>> m.agents.triggers.events(trigger_id=tid, verdicts=["filtered", "rejected"])
>>> m.agents.triggers.poll_test(tid)
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional, TypedDict
from urllib.parse import quote

from .errors import MelayaError
from .platform_types import JsonDict

_BASE = "/api/v1/private/triggers"


def _path(trigger_id: str, suffix: str = "") -> str:
    return f"{_BASE}/{quote(trigger_id, safe='')}{suffix}"


class TriggerRecord(TypedDict, total=False):
    """One event trigger. Secrets are never included."""
    id: str
    publicId: str
    name: str
    kind: str  # "webhook" | "wss" | "engine" | "poll" | "push"
    project: str
    pipelineName: str
    enabled: bool
    pausedReason: Optional[str]
    signingScheme: str
    sourceId: Optional[str]
    config: JsonDict
    maxEventsPerMin: int
    maxRunsPerDay: int
    maxConcurrentRuns: int
    consecutiveFailures: int
    lastEventAt: Optional[str]
    createdAt: str
    updatedAt: str
    webhookUrl: Optional[str]
    projectAccess: bool


class TriggerDelivery(TypedDict, total=False):
    """One delivery receipt: what happened to one event."""
    id: str
    triggerId: str
    eventId: str
    source: str
    receivedAt: str
    verdict: str
    decision: Any
    action: Optional[str]
    runId: Optional[str]
    detail: Optional[str]
    latencyMs: Optional[int]
    timings: Optional[Dict[str, float]]
    resultExcerpt: Optional[str]
    redelivered: Optional[int]


class TriggerLiveEvent(TypedDict, total=False):
    """One entry of the live event log (``at`` is epoch ms)."""
    triggerId: str
    deliveryId: Optional[str]
    eventId: str
    source: str
    verdict: str
    action: Optional[str]
    runId: Optional[str]
    detail: Optional[str]
    latencyMs: Optional[int]
    resultExcerpt: Optional[str]
    at: int


class TriggerEventsResult(TypedDict):
    """Return shape of ``events()``."""
    events: List[TriggerLiveEvent]
    scanned: int
    retention: JsonDict


class TriggerPollStatus(TypedDict, total=False):
    """Runtime state of a poll trigger."""
    synced: bool
    status: str
    lastError: Optional[str]
    lastPolledAt: Optional[str]
    nextPollAt: Optional[str]
    armed: bool
    baselinePending: bool
    seenCount: int
    itemsPublished: int
    consecutiveErrors: int
    requestedIntervalSec: int
    effectiveIntervalSec: int
    tierFloorSec: int


class TriggersAPI:
    def __init__(self, request: Any) -> None:
        self._request = request

    def list(self, *, project: Optional[str] = None, pipeline_name: Optional[str] = None) -> List[TriggerRecord]:
        """List your event triggers, newest first.

        Parameters
        ----------
        project:
            Only triggers of this project.
        pipeline_name:
            Only triggers that start this pipeline.
        """
        return self._request("GET", _BASE, params={"project": project, "pipelineName": pipeline_name})

    def get(self, trigger_id: str) -> TriggerRecord:
        """One trigger with its config. An unknown id raises ``MelayaError`` (404)."""
        return self._request("GET", _path(trigger_id))

    def deliveries(self, trigger_id: str, *, limit: Optional[int] = None) -> List[TriggerDelivery]:
        """Recent delivery receipts of a trigger, newest first.

        Each receipt carries the verdict, decision answers, action, run id,
        detail, latency, per-stage timings and a result excerpt. ``limit`` is
        1-200, default 50 server-side.
        """
        return self._request("GET", _path(trigger_id, "/deliveries"), params={"limit": limit})

    def stats(self, trigger_id: str, *, hours: Optional[int] = None) -> JsonDict:
        """Delivery counts by verdict over a window.

        ``hours`` is 1-168, default 24 server-side. Returns ``{"hours",
        "byVerdict": {verdict: {"n", "p50", "p95"}}, "filtered", "sampled"}``.
        """
        return self._request("GET", _path(trigger_id, "/stats"), params={"hours": hours})

    def pending_approvals(self, trigger_id: str) -> List[JsonDict]:
        """Approvals still waiting on this trigger's runs and tool calls.

        Each item is ``{"requestId", "triggerId", "deliveryId", "eventId",
        "source", "service", "tool", "argsPreview", "createdAt", "expiresAt"}``
        (times in epoch ms). Decide them in the Melaya app.
        """
        return self._request("GET", _path(trigger_id, "/approvals"))

    def test(self, trigger_id: str, payload: Any = None) -> JsonDict:
        """Dry-run one event through the trigger.

        Prefilter, decide and routing run for real; the action never executes
        (no run, no write). Rate limits still apply. Returns ``{"accepted",
        "eventId", "reason"?}``; follow the event with ``events()``.
        """
        body: Dict[str, Any] = {}
        if payload is not None:
            body["payload"] = payload
        return self._request("POST", _path(trigger_id, "/test"), json=body)

    def events(
        self,
        *,
        trigger_id: Optional[str] = None,
        since: Optional[int] = None,
        verdicts: Optional[List[str]] = None,
        limit: Optional[int] = None,
    ) -> TriggerEventsResult:
        """Recent live trigger events, newest first (last 500 events / 24 h).

        Includes outcomes that write no receipt: filtered, shed and ingress
        rejections.

        Parameters
        ----------
        trigger_id:
            Only events of this trigger.
        since:
            Only events after this time (epoch ms). Poll with the newest
            ``at`` you have seen.
        verdicts:
            Only these verdicts, e.g. ``["filtered", "rejected"]``.
        limit:
            1-200, default 50 server-side.
        """
        params: Dict[str, Any] = {
            "triggerId": trigger_id,
            "since": since,
            "verdicts": ",".join(verdicts) if verdicts else None,
            "limit": limit,
        }
        return self._request("GET", f"{_BASE}/events", params=params)

    def poll_status(self, trigger_id: str) -> TriggerPollStatus:
        """Runtime state of a poll trigger: status, last error, next poll, intervals."""
        return self._request("GET", _path(trigger_id, "/poll"))

    def poll_test(self, trigger_id: str) -> JsonDict:
        """Dry poll: call the tool now and show what it found and would publish.

        Nothing is published and no state changes. Returns ``{"dry": True,
        "ok": True, "found", "baseline", "wouldPublish", "items": [{"id",
        "preview"}], "samplePayload"}``. A tool failure is returned as
        ``{"dry": True, "ok": False, "error"}``, not raised.
        """
        try:
            return self._request("POST", _path(trigger_id, "/poll/test"), json={"dry": True})
        except MelayaError as exc:
            # The transport raises on any ``{"ok": false}`` body; a failed dry
            # poll is a normal 200 result here, so hand it back as-is.
            body = exc.body
            if exc.status is not None and exc.status < 300 and isinstance(body, dict) and body.get("dry") is True:
                return body
            raise

    def poll_now(self, trigger_id: str) -> JsonDict:
        """Make a real poll due now. The trigger must be enabled.

        Returns ``{"dry": False, "queued": bool}``.
        """
        return self._request("POST", _path(trigger_id, "/poll/test"), json={"dry": False})

    def poll_sync(self, trigger_id: str) -> JsonDict:
        """Re-create a poll trigger's runtime row from its saved config.

        Re-arms a poller that never started. Returns ``{"result": str}``.
        """
        return self._request("POST", _path(trigger_id, "/poll/sync"))

    def presets(self) -> JsonDict:
        """Trigger presets for your connected services and plan.

        Returns ``{"tier", "tierFloorSec", "presets": [...], "beta": {"allowed",
        "minTier"}}``.
        """
        return self._request("GET", f"{_BASE}/presets")

    def limits(self) -> JsonDict:
        """Plan caps and usage: triggers, sources, events per minute, poll floor, approval TTL."""
        return self._request("GET", f"{_BASE}/limits")

    def sources(self) -> List[JsonDict]:
        """Your WebSocket / Server-Sent Events stream sources (credential values never returned)."""
        return self._request("GET", f"{_BASE}/sources")
