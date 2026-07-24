// Assistant API — get and save the caller's onboarding / persona profile.
//
// Maps to /api/v1/private/assistant/profile.
package melaya

import "context"

// AssistantAPI wraps the assistant onboarding profile endpoints.
type AssistantAPI struct {
	h *httpClient
}

// GetProfile returns the caller's assistant onboarding profile.
//
// GET /api/v1/private/assistant/profile
func (a *AssistantAPI) GetProfile(ctx context.Context) (*AssistantProfile, error) {
	data, err := a.h.get(ctx, "/api/v1/private/assistant/profile", nil)
	if err != nil {
		return nil, err
	}
	var v AssistantProfile
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// SetProfile saves the caller's assistant onboarding profile.
//
// PUT /api/v1/private/assistant/profile
func (a *AssistantAPI) SetProfile(ctx context.Context, profile AssistantProfile) (bool, error) {
	data, err := a.h.put(ctx, "/api/v1/private/assistant/profile", profile)
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}
