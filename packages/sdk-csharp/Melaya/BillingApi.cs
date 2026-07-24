namespace Melaya;

/// <summary>
/// Billing API — subscription status, Stripe checkout/portal sessions, and public pricing plans.
/// Maps to <c>/api/v1/private/billing/*</c> (authenticated) and <c>/api/v1/billing/plans</c> (public).
/// </summary>
/// <example>
/// <code>
/// var sub = await m.Billing.SubscriptionAsync();
/// Console.WriteLine($"{sub.Tier}: {sub.Status}");
/// var checkout = await m.Billing.CreateCheckoutAsync(new { priceId = "price_..." });
/// // redirect user to checkout.Url
/// </code>
/// </example>
public sealed class BillingApi
{
    private readonly MelayaHttpClient _http;

    internal BillingApi(MelayaHttpClient http) => _http = http;

    /// <summary>Get the caller's current Stripe subscription status and tier.</summary>
    public async Task<BillingSubscription> SubscriptionAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<BillingSubscription>("/api/v1/private/billing/subscription", ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Create a Stripe Checkout session for a tier upgrade.
    /// Returns a URL to redirect the user to.
    /// </summary>
    public async Task<BillingCheckoutResult> CreateCheckoutAsync(object body, CancellationToken ct = default)
    {
        return await _http.PostAsync<BillingCheckoutResult>("/api/v1/private/billing/checkout", body, ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Create a Stripe Customer Portal session for managing the subscription.
    /// Returns a URL to redirect the user to.
    /// </summary>
    public async Task<BillingPortalResult> CreatePortalAsync(CancellationToken ct = default)
    {
        return await _http.PostAsync<BillingPortalResult>("/api/v1/private/billing/portal", null, ct).ConfigureAwait(false);
    }

    /// <summary>Return public pricing plan details (price IDs for forge/bastion/citadel tiers). Public endpoint — no auth required.</summary>
    public async Task<List<BillingPlan>> PlansAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<List<BillingPlan>>("/api/v1/billing/plans", ct: ct).ConfigureAwait(false);
    }
}
