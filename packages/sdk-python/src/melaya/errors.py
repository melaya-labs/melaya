"""Exceptions raised by the Melaya SDK."""
from __future__ import annotations

from typing import Any, Optional


class MelayaError(Exception):
    """Raised for non-2xx API responses (and missing optional deps)."""

    def __init__(
        self,
        message: str,
        status: Optional[int] = None,
        code: Optional[str] = None,
        body: Any = None,
    ) -> None:
        super().__init__(message)
        self.status = status
        self.code = code
        self.body = body
