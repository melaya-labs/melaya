"""Cross-run session-memory shim.

Models persisting a crew's working memory between runs and restoring it on
the next run: serialize -> store, then load -> deserialize. The store is an
in-process dict (no real DB/disk) so the bench is self-contained; the cost
modelled is the serialize/deserialize + bookkeeping, which scales with the
memory size a long-running crew accumulates.
"""
from __future__ import annotations
import json
from typing import Any


class _MemStore:
    def __init__(self) -> None:
        self._d: dict[str, str] = {}

    def put(self, k: str, v: str) -> None:
        self._d[k] = v

    def get(self, k: str) -> str | None:
        return self._d.get(k)


class SessionMemory:
    def __init__(self, store: _MemStore | None = None) -> None:
        self.store = store or _MemStore()
        self.entries: list[dict[str, Any]] = []

    def append(self, entry: dict[str, Any]) -> None:
        self.entries.append(entry)

    def save(self, session_id: str) -> int:
        blob = json.dumps(self.entries)
        self.store.put(session_id, blob)
        return len(blob)

    def load(self, session_id: str) -> list[dict[str, Any]]:
        blob = self.store.get(session_id)
        return json.loads(blob) if blob else []
