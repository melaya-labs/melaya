package org.melaya

import org.json.JSONArray
import org.json.JSONObject

/**
 * Billing API — subscription status, Stripe checkout/portal sessions, and pricing plans.
 *
 * Paths:
 *   - `GET  /api/v1/private/billing/subscription` — current Stripe subscription status
 *   - `POST /api/v1/private/billing/checkout`     — create Stripe Checkout session
 *   - `POST /api/v1/private/billing/portal`       — create Stripe Customer Portal session
 *   - `GET  /api/v1/billing/plans`                — public pricing plan details (no auth)
 */
class BillingAPI internal constructor(private val http: HttpClient) {

    /** Get the caller's current Stripe subscription status and tier. */
    fun subscription(): JSONObject {
        return http.get("/api/v1/private/billing/subscription").asObject()
    }

    /**
     * Create a Stripe Checkout session for a tier upgrade.
     * Optionally pass [priceId] or [tier] to target a specific plan.
     * Returns an object containing a `url` to redirect the user to.
     */
    fun createCheckout(priceId: String? = null, tier: String? = null): JSONObject {
        val body = buildMap<String, Any?> {
            if (priceId != null) put("priceId", priceId)
            if (tier != null) put("tier", tier)
        }
        return http.post("/api/v1/private/billing/checkout", body).asObject()
    }

    /**
     * Create a Stripe Customer Portal session for managing the subscription.
     * Returns an object containing a `url` to redirect the user to.
     */
    fun createPortal(): JSONObject {
        return http.post("/api/v1/private/billing/portal").asObject()
    }

    /**
     * Return public pricing plan details (price IDs for forge/bastion/citadel tiers).
     * Does not require authentication.
     */
    fun plans(): JSONArray {
        val r = http.get("/api/v1/billing/plans")
        return when (r) {
            is JSONArray -> r
            is JSONObject -> r.optJSONArray("plans") ?: JSONArray()
            else -> JSONArray()
        }
    }
}
