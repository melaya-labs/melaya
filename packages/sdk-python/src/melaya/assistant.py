"""Assistant API — get and save the caller's onboarding/persona profile.

Maps to ``/api/v1/private/assistant/profile``. The profile is stored
envelope-encrypted and used to personalise the in-app assistant experience.

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> profile = m.assistant.get_profile()
>>> m.assistant.set_profile({"name": "Antoine", "goals": ["grow my trading edge"]})
"""
from __future__ import annotations

from typing import Any, Dict

from .platform_types import JsonDict


class AssistantAPI:
    def __init__(self, request: Any) -> None:
        self._request = request

    def get_profile(self) -> JsonDict:
        """Get the caller's assistant onboarding profile (decrypted from service='assistant_profile')."""
        return self._request("GET", "/api/v1/private/assistant/profile")

    def set_profile(self, profile: Dict[str, Any]) -> JsonDict:
        """Save the caller's assistant onboarding profile."""
        return self._request("PUT", "/api/v1/private/assistant/profile", json=profile)
