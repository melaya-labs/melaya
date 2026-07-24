// Runner API — mint, list, and revoke runner tokens (mel_run_ prefix).
//
// Maps to /api/v1/private/runner/tokens. Runner tokens authenticate the
// Melaya runner process that executes agent pipelines on your infrastructure.
//
// Example:
//
//	result, _ := m.Runner.CreateToken(ctx, &melaya.RunnerTokenCreate{Label: "prod-1"})
//	// store result.Token securely — it is shown only once
//	tokens, _ := m.Runner.ListTokens(ctx)
//	m.Runner.RevokeToken(ctx, tokens[0].ID)
package melaya

import (
	"context"
	"net/url"
)

// RunnerAPI wraps runner token endpoints.
type RunnerAPI struct {
	h *httpClient
}

// CreateToken mints a new runner token.
// The plaintext token is returned only in this response — store it securely.
//
// POST /api/v1/private/runner/tokens
func (r *RunnerAPI) CreateToken(ctx context.Context, body *RunnerTokenCreate) (*RunnerTokenCreateResult, error) {
	data, err := r.h.post(ctx, "/api/v1/private/runner/tokens", body)
	if err != nil {
		return nil, err
	}
	var v RunnerTokenCreateResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// ListTokens lists all runner tokens for the caller (masked, with last_seen).
//
// GET /api/v1/private/runner/tokens
func (r *RunnerAPI) ListTokens(ctx context.Context) ([]RunnerToken, error) {
	data, err := r.h.get(ctx, "/api/v1/private/runner/tokens", nil)
	if err != nil {
		return nil, err
	}
	var v []RunnerToken
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// RevokeToken revokes a runner token by ID.
//
// DELETE /api/v1/private/runner/tokens/:tokenId
func (r *RunnerAPI) RevokeToken(ctx context.Context, tokenID string) (bool, error) {
	path := "/api/v1/private/runner/tokens/" + url.PathEscape(tokenID)
	data, err := r.h.del(ctx, path, nil)
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}
