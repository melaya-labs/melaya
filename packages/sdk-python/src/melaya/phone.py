"""Phone API — pair and control Android devices connected to the Melaya runner.

Maps to ``/api/v1/private/phone/*``. Agents use these endpoints to drive a
paired phone (tap, type, read screen state, launch apps) via the Melaya APK.

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> result = m.phone.pair()
>>> print(result["code"])  # Enter on the phone APK to pair
>>> devices = m.phone.list_devices()
>>> tree = m.phone.screen_tree()
"""
from __future__ import annotations

from typing import Any, List
from urllib.parse import quote

from .platform_types import JsonDict


class PhoneAPI:
    def __init__(self, request: Any) -> None:
        self._request = request

    def pair(self) -> JsonDict:
        """Start phone device pairing — generates a pairing code to enter on the Melaya APK."""
        return self._request("POST", "/api/v1/private/phone/pair")

    def list_devices(self) -> List[JsonDict]:
        """List paired phone devices for the caller."""
        response = self._request("GET", "/api/v1/private/phone/devices")
        return response.get("devices", [])

    def revoke_device(self, device_id: str) -> JsonDict:
        """Revoke a paired phone device by ID."""
        return self._request(
            "DELETE", f"/api/v1/private/phone/devices/{quote(device_id, safe='')}"
        )

    def screen_tree(self) -> JsonDict:
        """Get current accessibility tree from the paired phone's screen."""
        return self._request("GET", "/api/v1/private/phone/screen-tree")

    def list_apps(self) -> List[JsonDict]:
        """List installed apps on the paired phone."""
        response = self._request("GET", "/api/v1/private/phone/apps")
        return response.get("result", {}).get("apps", [])

    def set_allowed_apps(self, package_names: List[str]) -> JsonDict:
        """Set the allowlist of apps that agents are permitted to interact with."""
        return self._request("PUT", "/api/v1/private/phone/apps/allowed",
                             json={"apps": [{"package": name} for name in package_names]})

    def register_active_run(self, run_id: str) -> JsonDict:
        """Register the currently active pipeline run on the phone (used by agents)."""
        return self._request("POST", "/api/v1/private/phone/active-run", json={"runId": run_id})
