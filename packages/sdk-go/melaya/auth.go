// Auth API — login, MFA, registration, and session management.
//
// Maps to /api/v1/private/auth/* and /api/v1/private/mfa/*.
package melaya

import (
	"context"
	"net/url"
)

// AuthAPI wraps authentication and MFA endpoints.
type AuthAPI struct {
	h *httpClient
}

// Login authenticates with username + password. On MFA-enrolled accounts the
// response includes MFARequired=true and a ChallengeToken — call VerifyMFA
// to exchange it for a full session JWT.
//
// POST /api/v1/private/auth/login (public)
func (a *AuthAPI) Login(ctx context.Context, body AuthLoginBody) (*AuthLoginResult, error) {
	data, err := a.h.post(ctx, "/api/v1/private/auth/login", body)
	if err != nil {
		return nil, err
	}
	var v AuthLoginResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// VerifyMFA resolves an MFA challenge after login; exchanges challengeToken + TOTP
// code for a full session JWT.
//
// POST /api/v1/private/auth/mfa/verify (public)
func (a *AuthAPI) VerifyMFA(ctx context.Context, body map[string]interface{}) (*AuthLoginResult, error) {
	data, err := a.h.post(ctx, "/api/v1/private/auth/mfa/verify", body)
	if err != nil {
		return nil, err
	}
	var v AuthLoginResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Register creates a new user account and sends email verification.
//
// POST /api/v1/private/auth/register (public)
func (a *AuthAPI) Register(ctx context.Context, body AuthRegisterBody) (map[string]interface{}, error) {
	data, err := a.h.post(ctx, "/api/v1/private/auth/register", body)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// VerifySignup confirms an email address from a signup link token.
//
// POST /api/v1/private/auth/verify-signup (public)
func (a *AuthAPI) VerifySignup(ctx context.Context, token string) (map[string]interface{}, error) {
	data, err := a.h.post(ctx, "/api/v1/private/auth/verify-signup", map[string]string{"token": token})
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ResendVerification re-sends the email verification message.
//
// POST /api/v1/private/auth/resend-verification (public)
func (a *AuthAPI) ResendVerification(ctx context.Context, email string) (map[string]interface{}, error) {
	data, err := a.h.post(ctx, "/api/v1/private/auth/resend-verification", map[string]string{"email": email})
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Me returns the current authenticated user profile.
//
// GET /api/v1/private/auth/me
func (a *AuthAPI) Me(ctx context.Context) (*UserProfile, error) {
	data, err := a.h.get(ctx, "/api/v1/private/auth/me", nil)
	if err != nil {
		return nil, err
	}
	var v UserProfile
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Check performs a lightweight session validity check; returns ok:true.
//
// GET /api/v1/private/auth/check
func (a *AuthAPI) Check(ctx context.Context) (bool, error) {
	data, err := a.h.get(ctx, "/api/v1/private/auth/check", nil)
	if err != nil {
		return false, err
	}
	var v struct {
		Ok bool `json:"ok"`
	}
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}

// ChangePassword changes the current user's password.
//
// POST /api/v1/private/auth/change-password
func (a *AuthAPI) ChangePassword(ctx context.Context, currentPassword, newPassword string) (map[string]interface{}, error) {
	data, err := a.h.post(ctx, "/api/v1/private/auth/change-password", map[string]string{
		"currentPassword": currentPassword,
		"newPassword":     newPassword,
	})
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ForgotPassword initiates a password reset flow and sends an email.
//
// POST /api/v1/private/auth/forgot-password (public)
func (a *AuthAPI) ForgotPassword(ctx context.Context, email string) (map[string]interface{}, error) {
	data, err := a.h.post(ctx, "/api/v1/private/auth/forgot-password", map[string]string{"email": email})
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ResetPassword completes a password reset using the token from the email link.
//
// POST /api/v1/private/auth/reset-password (public)
func (a *AuthAPI) ResetPassword(ctx context.Context, token, newPassword string) (map[string]interface{}, error) {
	data, err := a.h.post(ctx, "/api/v1/private/auth/reset-password", map[string]string{
		"token":       token,
		"newPassword": newPassword,
	})
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// CreateMobileHandoff creates a short-lived handoff token for mobile deep-link auth.
//
// POST /api/v1/private/auth/mobile-handoff
func (a *AuthAPI) CreateMobileHandoff(ctx context.Context) (map[string]interface{}, error) {
	data, err := a.h.post(ctx, "/api/v1/private/auth/mobile-handoff", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// MyPermissions returns the caller's permission flags.
//
// GET /api/v1/private/auth/permissions
func (a *AuthAPI) MyPermissions(ctx context.Context) (map[string]interface{}, error) {
	data, err := a.h.get(ctx, "/api/v1/private/auth/permissions", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Refresh rotates the session JWT (sliding expiry) and returns a new token.
//
// POST /api/v1/private/auth/refresh
func (a *AuthAPI) Refresh(ctx context.Context) (*AuthLoginResult, error) {
	data, err := a.h.post(ctx, "/api/v1/private/auth/refresh", nil)
	if err != nil {
		return nil, err
	}
	var v AuthLoginResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// ── MFA ─────────────────────────────────────────────────────────────────────

// MFAStatus returns the MFA enrollment status for the caller.
//
// GET /api/v1/private/mfa/status
func (a *AuthAPI) MFAStatus(ctx context.Context) (*MFAStatus, error) {
	data, err := a.h.get(ctx, "/api/v1/private/mfa/status", nil)
	if err != nil {
		return nil, err
	}
	var v MFAStatus
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// MFASetup initiates TOTP setup and returns a QR code URL and secret.
//
// POST /api/v1/private/mfa/setup
func (a *AuthAPI) MFASetup(ctx context.Context) (*MFASetupResult, error) {
	data, err := a.h.post(ctx, "/api/v1/private/mfa/setup", nil)
	if err != nil {
		return nil, err
	}
	var v MFASetupResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// MFAConfirm confirms TOTP setup with the first valid TOTP code, activating MFA.
//
// POST /api/v1/private/mfa/confirm
func (a *AuthAPI) MFAConfirm(ctx context.Context, code string) (map[string]interface{}, error) {
	data, err := a.h.post(ctx, "/api/v1/private/mfa/confirm", map[string]string{"code": code})
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ── Account helpers ───────────────────────────────────────────────────────────

// ExportMyData requests a GDPR Art 15/20 data export for the caller.
//
// POST /api/v1/private/accounts/export
func (a *AuthAPI) ExportMyData(ctx context.Context) (map[string]interface{}, error) {
	data, err := a.h.post(ctx, "/api/v1/private/accounts/export", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// RemoveKey removes a stored CEX API key.
//
// DELETE /api/v1/private/keys/:keyId
func (a *AuthAPI) RemoveKey(ctx context.Context, keyID string) (map[string]interface{}, error) {
	data, err := a.h.del(ctx, "/api/v1/private/keys/"+url.PathEscape(keyID), nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// UpdateProfile updates the user's display name, avatar, or settings.
//
// PATCH /api/v1/private/accounts/profile
func (a *AuthAPI) UpdateProfile(ctx context.Context, body map[string]interface{}) (map[string]interface{}, error) {
	data, err := a.h.patch(ctx, "/api/v1/private/accounts/profile", body)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Credits returns the current credit balance and transaction history.
//
// GET /api/v1/private/accounts/credits
func (a *AuthAPI) Credits(ctx context.Context) (map[string]interface{}, error) {
	data, err := a.h.get(ctx, "/api/v1/private/accounts/credits", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// AICredits returns the AI/LLM credit balance.
//
// GET /api/v1/private/accounts/credits/ai
func (a *AuthAPI) AICredits(ctx context.Context) (map[string]interface{}, error) {
	data, err := a.h.get(ctx, "/api/v1/private/accounts/credits/ai", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// PortfolioIdeasCredits returns the portfolio-ideas feature credit balance.
//
// GET /api/v1/private/accounts/credits/portfolio-ideas
func (a *AuthAPI) PortfolioIdeasCredits(ctx context.Context) (map[string]interface{}, error) {
	data, err := a.h.get(ctx, "/api/v1/private/accounts/credits/portfolio-ideas", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// RiskMonitoringCredits returns the risk-monitoring feature credit balance.
//
// GET /api/v1/private/accounts/credits/risk-monitoring
func (a *AuthAPI) RiskMonitoringCredits(ctx context.Context) (map[string]interface{}, error) {
	data, err := a.h.get(ctx, "/api/v1/private/accounts/credits/risk-monitoring", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Version returns the current server version string.
//
// GET /api/v1/version (public)
func (a *AuthAPI) Version(ctx context.Context) (string, error) {
	data, err := a.h.get(ctx, "/api/v1/version", nil)
	if err != nil {
		return "", err
	}
	var v struct {
		Version string `json:"version"`
	}
	if err := unmarshal(data, &v); err != nil {
		return "", err
	}
	return v.Version, nil
}
