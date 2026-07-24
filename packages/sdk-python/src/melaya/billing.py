"""Billing API — subscription status, Stripe checkout/portal, and pricing plans.

Maps to ``/api/v1/private/billing/*`` (authenticated) and
``/api/v1/billing/plans`` (public).

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> sub = m.billing.subscription()
>>> print(sub["tier"], sub["status"])
>>> checkout = m.billing.create_checkout(tier="bastion")
>>> # Redirect user to checkout["url"]
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional

from .platform_types import JsonDict


class BillingAPI:
    def __init__(self, request: Any) -> None:
        self._request = request

    def subscription(self) -> JsonDict:
        """Get the caller's current Stripe subscription status and tier."""
        return self._request("GET", "/api/v1/private/billing/subscription")

    def create_checkout(self, *, price_id: Optional[str] = None, tier: Optional[str] = None) -> JsonDict:
        """Create a Stripe Checkout session for a tier upgrade. Returns a redirect URL."""
        body: Dict[str, Any] = {}
        if price_id is not None:
            body["priceId"] = price_id
        if tier is not None:
            body["tier"] = tier
        return self._request("POST", "/api/v1/private/billing/checkout", json=body)

    def create_portal(self) -> JsonDict:
        """Create a Stripe Customer Portal session for subscription management. Returns a redirect URL."""
        return self._request("POST", "/api/v1/private/billing/portal")

    def plans(self) -> List[JsonDict]:
        """Return public pricing plan details (price IDs for forge/bastion/citadel tiers). Public endpoint."""
        return self._request("GET", "/api/v1/billing/plans")
