"""Tracing-overhead shim.

Models the cost of emitting one OpenTelemetry-style span around an
operation: open span, stamp attributes, close span, hand to an in-process
exporter. This is the per-span "observability tax" the runner adds when
tracing is on. No network, no real exporter.
"""
from __future__ import annotations
import time
from typing import Any


class _SpanExporter:
    def __init__(self) -> None:
        self.spans: list[dict[str, Any]] = []

    def export(self, span: dict[str, Any]) -> None:
        self.spans.append(span)


class Tracer:
    def __init__(self, exporter: _SpanExporter | None = None) -> None:
        self.exporter = exporter or _SpanExporter()

    def span(self, name: str, attrs: dict[str, Any]) -> dict[str, Any]:
        s: dict[str, Any] = {"name": name, "start_ns": time.perf_counter_ns(),
                             "attrs": dict(attrs)}
        s["end_ns"] = time.perf_counter_ns()
        self.exporter.export(s)
        return s
