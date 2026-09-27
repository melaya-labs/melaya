// Billing API — subscription status, Stripe checkout/portal sessions, and
// public pricing plans.
//
// Maps to /api/v1/private/billing/* (auth) and /api/v1/billing/plans (public).
package melaya

import "context"

// BillingAPI wraps Stripe billing endpoints.
type BillingAPI struct {
	h *httpClient
}

// Subscription returns the caller's current Stripe subscription status and tier.
//
// GET /api/v1/private/billing/subscription
func (b *BillingAPI) Subscription(ctx context.Context) (*BillingSubscription, error) {
	data, err := b.h.get(ctx, "/api/v1/private/billing/subscription", nil)
	if err != nil {
		return nil, err
	}
	var v BillingSubscription
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// CreateCheckout creates a Stripe Checkout session for a tier upgrade.
// Returns a URL to redirect the user to.
//
// POST /api/v1/private/billing/checkout
func (b *BillingAPI) CreateCheckout(ctx context.Context, body map[string]interface{}) (*BillingCheckoutResult, error) {
	data, err := b.h.post(ctx, "/api/v1/private/billing/checkout", body)
	if err != nil {
		return nil, err
	}
	var v BillingCheckoutResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// CreatePortal creates a Stripe Customer Portal session for managing the subscription.
// Returns a URL to redirect the user to.
//
// POST /api/v1/private/billing/portal
func (b *BillingAPI) CreatePortal(ctx context.Context) (*BillingPortalResult, error) {
	data, err := b.h.post(ctx, "/api/v1/private/billing/portal", nil)
	if err != nil {
		return nil, err
	}
	var v BillingPortalResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Plans returns public pricing plan details (price IDs for forge/bastion/citadel tiers).
//
// GET /api/v1/billing/plans (public)
func (b *BillingAPI) Plans(ctx context.Context) ([]BillingPlan, error) {
	data, err := b.h.get(ctx, "/api/v1/billing/plans", nil)
	if err != nil {
		return nil, err
	}
	var v []BillingPlan
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// AmbassadorPerk returns the ambassador discount the caller is entitled to
// (nil if none) — the checkout applies it automatically, but it's surfaced so
// the discount is visible before that.
//
// GET /api/v1/private/billing/ambassador-perk
func (b *BillingAPI) AmbassadorPerk(ctx context.Context) (map[string]interface{}, error) {
	data, err := b.h.get(ctx, "/api/v1/private/billing/ambassador-perk", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// RedeemCode redeems a single-use promo code to the caller's account; the
// discount applies on the next checkout.
//
// POST /api/v1/private/billing/redeem-code
func (b *BillingAPI) RedeemCode(ctx context.Context, code string) (map[string]interface{}, error) {
	data, err := b.h.post(ctx, "/api/v1/private/billing/redeem-code", map[string]string{"code": code})
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ReservedPromo returns the caller's active reserved promo (nil if none), so
// the subscription modal can reflect a discount without the user re-entering
// a code.
//
// GET /api/v1/private/billing/reserved-promo
func (b *BillingAPI) ReservedPromo(ctx context.Context) (map[string]interface{}, error) {
	data, err := b.h.get(ctx, "/api/v1/private/billing/reserved-promo", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}
