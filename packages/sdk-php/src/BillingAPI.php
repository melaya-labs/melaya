<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Billing API — subscription status, Stripe checkout/portal sessions, and
 * public pricing plans.
 *
 * Maps to /api/v1/private/billing/* (authenticated) and /api/v1/billing/plans (public).
 *
 * @example
 * ```php
 * $sdk = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * $sub = $sdk->billing->subscription();
 * echo $sub['tier'];  // 'forge' | 'bastion' | 'citadel'
 *
 * $checkout = $sdk->billing->createCheckout(['tier' => 'bastion']);
 * // Redirect user to $checkout['url']
 * ```
 */
class BillingAPI
{
    public function __construct(private readonly HttpClient $http) {}

    /** Get the caller's current Stripe subscription status and tier. */
    public function subscription(): array
    {
        return $this->http->get('/api/v1/private/billing/subscription');
    }

    /**
     * Create a Stripe Checkout session for a tier upgrade.
     * Returns ['url' => '...'] — redirect the user to this URL.
     *
     * @param array $body ['priceId' => '...'] or ['tier' => 'bastion']
     */
    public function createCheckout(array $body = []): array
    {
        return $this->http->post('/api/v1/private/billing/checkout', $body);
    }

    /**
     * Create a Stripe Customer Portal session for managing the subscription.
     * Returns ['url' => '...'] — redirect the user to this URL.
     */
    public function createPortal(): array
    {
        return $this->http->post('/api/v1/private/billing/portal');
    }

    /** Return public pricing plan details (price IDs for forge/bastion/citadel). */
    public function plans(): array
    {
        return $this->http->get('/api/v1/billing/plans');
    }

    /** The ambassador discount the caller is entitled to, or null. */
    public function ambassadorPerk(): array
    {
        return $this->http->get('/api/v1/private/billing/ambassador-perk');
    }

    /**
     * Redeem a single-use promo code to the caller's account. The discount
     * applies on the next checkout.
     */
    public function redeemCode(string $code): array
    {
        return $this->http->post('/api/v1/private/billing/redeem-code', ['code' => $code]);
    }

    /** The caller's active reserved promo (for the subscription modal), or null. */
    public function reservedPromo(): array
    {
        return $this->http->get('/api/v1/private/billing/reserved-promo');
    }
}
