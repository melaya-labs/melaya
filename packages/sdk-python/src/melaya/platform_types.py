"""Shared types for the Melaya Platform + Agents API surface.

These complement the trading-plane types and cover the agentic/pipeline plane:
projects, pipeline runs, HITL approvals, credentials/connectors, AI models,
assistant profile, billing, team management, evals, and bugs.
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional, Union

# ── Common ───────────────────────────────────────────────────────────────────

RunStatus = str  # "pending" | "queued" | "running" | "done" | "error" | "killed" | "cancelled"
HitlDecision = str  # "approved" | "rejected"
TeamRole = str  # "owner" | "editor" | "viewer"
TemplateVisibility = str  # "private" | "team" | "community" | "assigned"

# Generic JSON dict type used for typed responses
JsonDict = Dict[str, Any]

__all__ = [
    "RunStatus",
    "HitlDecision",
    "TeamRole",
    "TemplateVisibility",
    "JsonDict",
]

