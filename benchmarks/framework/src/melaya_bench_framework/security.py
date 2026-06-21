"""Prompt-injection guard shim.

Faithful reproduction of the production input-safety guard: a weighted pattern scan over untrusted
model-bound content (RAG-retrieved docs, tool outputs) returning
allow / flag / block. Generic patterns, same shape as production.
"""
from __future__ import annotations
import re
from typing import Any

_PATTERNS = [
    (r"ignore\s+(?:all\s+|the\s+|your\s+|any\s+)?(?:previous|prior|above)\s+instructions", "ignore-previous", 2),
    (r"disregard\s+.{0,30}(?:instructions|prompt|rules|guardrails|system)", "disregard", 2),
    (r"(?:reveal|print|repeat|show|output)\s+.{0,30}(?:system\s*prompt|your\s+instructions)", "reveal-prompt", 3),
    (r"(?:reveal|print|exfiltrat\w*|send|leak|dump)\s+.{0,30}(?:api[\s_-]?key|secret|password|token|credential)", "exfiltrate", 3),
    (r"you\s+are\s+now\s+(?:a|an|in|dan|developer\s+mode|unrestricted)", "role-override", 2),
    (r"\bdo\s+anything\s+now\b", "dan", 2),
    (r"\bjailbreak\b", "jailbreak", 1),
    (r"<\|im_start\|>|<\|im_end\|>|\[/?INST\]|<<SYS>>", "chat-template", 3),
    (r"(?m)^\s*#{2,3}\s*(?:system|instruction|admin|developer)\b", "fake-header", 1),
    (r"\bnew\s+instructions?\s*:", "new-instructions", 1),
    (r"\bbase64\b\s*[:,(]|atob\s*\(|fromCharCode", "encoded", 1),
]
_BLOCK_SCORE = 3


class InjectionGuard:
    def __init__(self) -> None:
        self._rx = [(re.compile(p, re.I), r, w) for p, r, w in _PATTERNS]

    def scan(self, text: str) -> dict[str, Any]:
        score = 0
        reasons = []
        for rx, reason, weight in self._rx:
            if rx.search(text):
                score += weight
                reasons.append(reason)
        verdict = "block" if score >= _BLOCK_SCORE else "flag" if score > 0 else "allow"
        return {"verdict": verdict, "score": score, "reasons": reasons}
