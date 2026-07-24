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
