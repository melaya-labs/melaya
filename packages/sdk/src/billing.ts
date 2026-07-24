/**
 * Billing API — subscription status, Stripe checkout/portal sessions, and
 * public pricing plans.
 *
 * Maps to `/api/v1/private/billing/*` (authenticated) and `/api/v1/billing/plans`
 * (public). Read-only for most consumers; checkout and portal return redirect URLs.
 *
 * @example
 * ```ts
 * const sub = await melaya.billing.subscription();
 * console.log(sub.tier, sub.status);
 *
 * const { url } = await melaya.billing.createCheckout({ priceId: "price_..." });
 * // redirect user to url
 * ```
 */
import type { HttpClient } from "./client.js";
import type {
  BillingCheckoutResult,
  BillingPlan,
  BillingPortalResult,
  BillingSubscription,
} from "./platform-types.js";

export class BillingAPI {
  constructor(private readonly http: HttpClient) {}

  /** Get the caller's current Stripe subscription status and tier. */
  async subscription(): Promise<BillingSubscription> {
    return this.http.get<BillingSubscription>("/api/v1/private/billing/subscription");
  }

  /**
   * Create a Stripe Checkout session for a tier upgrade.
   * Returns a URL to redirect the user to.
   */
  async createCheckout(body: { priceId?: string; tier?: string }): Promise<BillingCheckoutResult> {
    return this.http.post<BillingCheckoutResult>("/api/v1/private/billing/checkout", body);
  }

  /**
   * Create a Stripe Customer Portal session for managing the subscription.
   * Returns a URL to redirect the user to.
   */
  async createPortal(): Promise<BillingPortalResult> {
    return this.http.post<BillingPortalResult>("/api/v1/private/billing/portal");
  }

  /** Return public pricing plan details (price IDs for forge/bastion/citadel tiers). */
  async plans(): Promise<BillingPlan[]> {
    return this.http.get<BillingPlan[]>("/api/v1/billing/plans");
  }
}
