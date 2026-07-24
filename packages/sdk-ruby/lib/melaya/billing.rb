# frozen_string_literal: true

module Melaya
  # Billing API — subscription status, Stripe checkout/portal sessions,
  # public pricing plans, and credit balances.
  #
  # Maps to /api/v1/private/billing/* (authenticated) and
  # /api/v1/billing/plans (public), and /api/v1/private/accounts/* for credits.
  class BillingAPI
    def initialize(http)
      @http = http
    end

    # GET /api/v1/private/billing/subscription
    # Get caller's current Stripe subscription status and tier.
    def subscription
      @http.get("/api/v1/private/billing/subscription")
    end

    # POST /api/v1/private/billing/checkout
    # Create Stripe Checkout session for tier upgrade.
    # Returns a URL to redirect the user to.
    # @param price_id [String, nil] Stripe price ID
    # @param tier [String, nil]
    def create_checkout(price_id: nil, tier: nil)
      body = compact("priceId" => price_id, "tier" => tier)
      @http.post("/api/v1/private/billing/checkout", body)
    end

    # POST /api/v1/private/billing/portal
    # Create Stripe Customer Portal session for subscription management.
    # Returns a URL to redirect the user to.
    def create_portal
      @http.post("/api/v1/private/billing/portal")
    end

    # GET /api/v1/billing/plans (public)
    # Return public pricing plan details (price IDs for forge/bastion/citadel tiers).
    def plans
      @http.get("/api/v1/billing/plans")
    end

    # ── Credits ───────────────────────────────────────────────────────────────

    # GET /api/v1/private/accounts/credits
    # Return current credit balance and transaction history.
    def credits
      @http.get("/api/v1/private/accounts/credits")
    end

    # GET /api/v1/private/accounts/credits/ai
    # Return AI/LLM credit balance.
    def ai_credits
      @http.get("/api/v1/private/accounts/credits/ai")
    end

    # GET /api/v1/private/accounts/credits/portfolio-ideas
    # Return portfolio-ideas feature credit balance.
    def portfolio_ideas_credits
      @http.get("/api/v1/private/accounts/credits/portfolio-ideas")
    end

    # GET /api/v1/private/accounts/credits/risk-monitoring
    # Return risk-monitoring feature credit balance.
    def risk_monitoring_credits
      @http.get("/api/v1/private/accounts/credits/risk-monitoring")
    end

    private

    def compact(hash)
      hash.reject { |_, v| v.nil? }
    end
  end
end
