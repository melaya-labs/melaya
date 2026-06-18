// Account API — authenticated reads about your Melaya account.
package melaya

import "context"

// AccountAPI wraps /api/v1/private/ account-read endpoints.
type AccountAPI struct {
	h *httpClient
}

// Keys returns the exchange API keys connected to the account.
// The APIKey field is masked (display only); use APIKeyID when launching strategies.
func (a *AccountAPI) Keys(ctx context.Context) ([]ConnectedKey, error) {
	data, err := a.h.get(ctx, "/api/v1/private/keys", nil)
	if err != nil {
		return nil, err
	}
	var env struct {
		Keys []ConnectedKey `json:"keys"`
	}
	if err := unmarshal(data, &env); err != nil {
		return nil, err
	}
	return env.Keys, nil
}

// Usage returns the tier, plan limits, and live usage counters.
func (a *AccountAPI) Usage(ctx context.Context) (*UsageSummary, error) {
	data, err := a.h.get(ctx, "/api/v1/private/usage", nil)
	if err != nil {
		return nil, err
	}
	var v UsageSummary
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// APIKeyStatus returns the status of the platform API key (tier, max connections).
func (a *AccountAPI) APIKeyStatus(ctx context.Context) (map[string]interface{}, error) {
	data, err := a.h.get(ctx, "/api/v1/private/api-key", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}
