package org.melaya;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Billing API — subscription status, Stripe checkout/portal sessions, and
 * public pricing plans.
 *
 * <p>Maps to {@code /api/v1/private/billing/*} (authenticated) and
 * {@code /api/v1/billing/plans} (public).
 *
 * @example
 * <pre>{@code
 * JsonNode sub = melaya.billing().subscription();
 * System.out.println(sub.get("tier").asText());
 *
 * JsonNode checkout = melaya.billing().createCheckout(Map.of("priceId", "price_xxx"));
 * String url = checkout.get("url").asText(); // redirect the user here
 * }</pre>
 */
public class BillingAPI {

    private final HttpClient http;

    BillingAPI(HttpClient http) {
        this.http = http;
    }

    /** Get the caller's current Stripe subscription status and tier. */
    public JsonNode subscription() {
        return http.get("/api/v1/private/billing/subscription", null);
    }

    /**
     * Create a Stripe Checkout session for a tier upgrade.
     * Returns a {@code url} to redirect the user to.
     *
     * @param body map containing optional {@code priceId} and/or {@code tier}
     */
    public JsonNode createCheckout(Map<String, Object> body) {
        return http.post("/api/v1/private/billing/checkout", body);
    }

    /**
     * Create a Stripe Customer Portal session for subscription management.
     * Returns a {@code url} to redirect the user to.
     */
    public JsonNode createPortal() {
        return http.post("/api/v1/private/billing/portal", null);
    }

    /**
     * Return public pricing plan details (price IDs for forge/bastion/citadel tiers).
     * This endpoint is public and requires no authentication.
     */
    public JsonNode plans() {
        return http.get("/api/v1/billing/plans", null);
    }

    /**
     * The ambassador discount the caller is entitled to, or {@code null}.
     *
     * <p>Maps to {@code GET /api/v1/private/billing/ambassador-perk}.
     */
    public JsonNode ambassadorPerk() {
        return http.get("/api/v1/private/billing/ambassador-perk", null);
    }

    /**
     * Redeem a single-use promo code to the caller's account (the discount
     * applies on the next checkout).
     *
     * <p>Maps to {@code POST /api/v1/private/billing/redeem-code}.
     *
     * @param code the promo code
     */
    public JsonNode redeemCode(String code) {
        return http.post("/api/v1/private/billing/redeem-code", Map.of("code", code));
    }

    /**
     * The caller's active reserved promo (for the subscription modal to
     * reflect), or {@code null}.
     *
     * <p>Maps to {@code GET /api/v1/private/billing/reserved-promo}.
     */
    public JsonNode reservedPromo() {
        return http.get("/api/v1/private/billing/reserved-promo", null);
    }
}
