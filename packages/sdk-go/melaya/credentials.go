// Credentials API — store, retrieve, test, and delete user-scoped secrets.
//
// Maps to /api/v1/private/credentials/*. Credentials are envelope-encrypted
// at rest. Use Connectors for project-scoped credentials.
package melaya

import (
	"context"
	"net/url"
)

// CredentialsAPI wraps user-scoped credential endpoints.
type CredentialsAPI struct {
	h *httpClient
}

// List returns all stored credentials for the caller.
//
// GET /api/v1/private/credentials
func (c *CredentialsAPI) List(ctx context.Context) ([]Credential, error) {
	data, err := c.h.get(ctx, "/api/v1/private/credentials", nil)
	if err != nil {
		return nil, err
	}
	var v []Credential
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ConnectedServices lists connected third-party services.
//
// GET /api/v1/private/credentials/services
func (c *CredentialsAPI) ConnectedServices(ctx context.Context) ([]ConnectedService, error) {
	data, err := c.h.get(ctx, "/api/v1/private/credentials/services", nil)
	if err != nil {
		return nil, err
	}
	var v []ConnectedService
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Get returns a stored credential by service name. Pass key to retrieve a specific
// named key within the service.
//
// GET /api/v1/private/credentials/:service
func (c *CredentialsAPI) Get(ctx context.Context, service, key string) (*Credential, error) {
	q := map[string]string{}
	if key != "" {
		q["key"] = key
	}
	data, err := c.h.get(ctx, "/api/v1/private/credentials/"+url.PathEscape(service), q)
	if err != nil {
		return nil, err
	}
	var v Credential
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Set stores or updates a credential (envelope-encrypted at rest).
//
// PUT /api/v1/private/credentials/:service
func (c *CredentialsAPI) Set(ctx context.Context, service string, body CredentialSetBody) (bool, error) {
	data, err := c.h.put(ctx, "/api/v1/private/credentials/"+url.PathEscape(service), body)
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}

// Delete deletes a stored credential by service name.
//
// DELETE /api/v1/private/credentials/:service
func (c *CredentialsAPI) Delete(ctx context.Context, service string) (bool, error) {
	data, err := c.h.del(ctx, "/api/v1/private/credentials/"+url.PathEscape(service), nil)
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}

// Test tests a stored credential against its target service.
//
// POST /api/v1/private/credentials/:service/test
func (c *CredentialsAPI) Test(ctx context.Context, service string) (*CredentialTestResult, error) {
	data, err := c.h.post(ctx, "/api/v1/private/credentials/"+url.PathEscape(service)+"/test", nil)
	if err != nil {
		return nil, err
	}
	var v CredentialTestResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// ── Operator profile ──────────────────────────────────────────────────────────

// GetOperatorProfile returns the operator profile (persona config for agents).
//
// GET /api/v1/private/credentials/operator-profile
func (c *CredentialsAPI) GetOperatorProfile(ctx context.Context) (*OperatorProfile, error) {
	data, err := c.h.get(ctx, "/api/v1/private/credentials/operator-profile", nil)
	if err != nil {
		return nil, err
	}
	var v OperatorProfile
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// SetOperatorProfile saves the operator profile.
//
// PUT /api/v1/private/credentials/operator-profile
func (c *CredentialsAPI) SetOperatorProfile(ctx context.Context, profile OperatorProfile) (bool, error) {
	data, err := c.h.put(ctx, "/api/v1/private/credentials/operator-profile", profile)
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}

// ── AI models ─────────────────────────────────────────────────────────────────

// ListModels lists available AI models across all configured providers.
//
// GET /api/v1/private/credentials/models
func (c *CredentialsAPI) ListModels(ctx context.Context, provider, capability string) ([]AIModel, error) {
	q := map[string]string{}
	if provider != "" {
		q["provider"] = provider
	}
	if capability != "" {
		q["capability"] = capability
	}
	data, err := c.h.get(ctx, "/api/v1/private/credentials/models", q)
	if err != nil {
		return nil, err
	}
	var v []AIModel
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ── Melaya accounts ───────────────────────────────────────────────────────────

// MelayaAccounts lists Melaya sub-accounts available to the caller.
//
// GET /api/v1/private/credentials/melaya-accounts
func (c *CredentialsAPI) MelayaAccounts(ctx context.Context) ([]map[string]interface{}, error) {
	data, err := c.h.get(ctx, "/api/v1/private/credentials/melaya-accounts", nil)
	if err != nil {
		return nil, err
	}
	var v []map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ── RAG ───────────────────────────────────────────────────────────────────────

// RagIngestStart starts a RAG document ingestion job.
//
// POST /api/v1/private/rag/ingest
func (c *CredentialsAPI) RagIngestStart(ctx context.Context, body RagIngestStartBody) (*RagJobStatus, error) {
	data, err := c.h.post(ctx, "/api/v1/private/rag/ingest", body)
	if err != nil {
		return nil, err
	}
	var v RagJobStatus
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// RagIngestStatus polls RAG ingestion job status.
//
// GET /api/v1/private/rag/ingest/:sessionId
func (c *CredentialsAPI) RagIngestStatus(ctx context.Context, sessionID string) (*RagJobStatus, error) {
	path := "/api/v1/private/rag/ingest/" + url.PathEscape(sessionID)
	data, err := c.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v RagJobStatus
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// RagRetrieveStart starts a RAG retrieval query.
//
// POST /api/v1/private/rag/retrieve
func (c *CredentialsAPI) RagRetrieveStart(ctx context.Context, body RagRetrieveBody) (*RagJobStatus, error) {
	data, err := c.h.post(ctx, "/api/v1/private/rag/retrieve", body)
	if err != nil {
		return nil, err
	}
	var v RagJobStatus
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// RagRetrieveStatus polls a RAG retrieval result.
//
// GET /api/v1/private/rag/retrieve/:sessionId
func (c *CredentialsAPI) RagRetrieveStatus(ctx context.Context, sessionID string) (*RagJobStatus, error) {
	path := "/api/v1/private/rag/retrieve/" + url.PathEscape(sessionID)
	data, err := c.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v RagJobStatus
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// ── Folder picker ─────────────────────────────────────────────────────────────

// PickFolderStart initiates a native folder picker for file ingestion.
//
// POST /api/v1/private/rag/pick-folder
func (c *CredentialsAPI) PickFolderStart(ctx context.Context) (*PickFolderStatus, error) {
	data, err := c.h.post(ctx, "/api/v1/private/rag/pick-folder", nil)
	if err != nil {
		return nil, err
	}
	var v PickFolderStatus
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// PickFolderStatus polls a folder picker result.
//
// GET /api/v1/private/rag/pick-folder/:sessionId
func (c *CredentialsAPI) PickFolderStatus(ctx context.Context, sessionID string) (*PickFolderStatus, error) {
	path := "/api/v1/private/rag/pick-folder/" + url.PathEscape(sessionID)
	data, err := c.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v PickFolderStatus
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// ── LinkedIn OAuth ────────────────────────────────────────────────────────────

// LinkedInConnectStart starts a LinkedIn OAuth flow.
//
// POST /api/v1/private/credentials/linkedin/connect
func (c *CredentialsAPI) LinkedInConnectStart(ctx context.Context) (map[string]interface{}, error) {
	data, err := c.h.post(ctx, "/api/v1/private/credentials/linkedin/connect", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// LinkedInConnectCancel cancels an in-progress LinkedIn OAuth flow.
//
// DELETE /api/v1/private/credentials/linkedin/connect
func (c *CredentialsAPI) LinkedInConnectCancel(ctx context.Context) (map[string]interface{}, error) {
	data, err := c.h.del(ctx, "/api/v1/private/credentials/linkedin/connect", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// LinkedInConnectStatus polls the LinkedIn OAuth connection status.
//
// GET /api/v1/private/credentials/linkedin/connect/status
func (c *CredentialsAPI) LinkedInConnectStatus(ctx context.Context) (map[string]interface{}, error) {
	data, err := c.h.get(ctx, "/api/v1/private/credentials/linkedin/connect/status", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ── Luma OAuth ───────────────────────────────────────────────────────────────

// LumaConnectStart starts a Luma OAuth flow.
//
// POST /api/v1/private/credentials/luma/connect
func (c *CredentialsAPI) LumaConnectStart(ctx context.Context) (map[string]interface{}, error) {
	data, err := c.h.post(ctx, "/api/v1/private/credentials/luma/connect", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// LumaConnectStatus polls the Luma OAuth connection status.
//
// GET /api/v1/private/credentials/luma/connect/status
func (c *CredentialsAPI) LumaConnectStatus(ctx context.Context) (map[string]interface{}, error) {
	data, err := c.h.get(ctx, "/api/v1/private/credentials/luma/connect/status", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// LumaConnectCancel cancels an in-progress Luma OAuth flow.
//
// DELETE /api/v1/private/credentials/luma/connect
func (c *CredentialsAPI) LumaConnectCancel(ctx context.Context) (map[string]interface{}, error) {
	data, err := c.h.del(ctx, "/api/v1/private/credentials/luma/connect", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// GetLumaRegistrationSchema returns the Luma event registration form schema.
//
// GET /api/v1/private/credentials/luma/schema
func (c *CredentialsAPI) GetLumaRegistrationSchema(ctx context.Context) (map[string]interface{}, error) {
	data, err := c.h.get(ctx, "/api/v1/private/credentials/luma/schema", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ── Google OAuth ──────────────────────────────────────────────────────────────

// GoogleOAuthStart starts a Google OAuth flow for credential storage.
//
// POST /api/v1/private/credentials/google/oauth
func (c *CredentialsAPI) GoogleOAuthStart(ctx context.Context, body map[string]interface{}) (map[string]interface{}, error) {
	data, err := c.h.post(ctx, "/api/v1/private/credentials/google/oauth", body)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// GoogleStatus lists the Google OAuth capabilities actually granted to the caller.
//
// GET /api/v1/private/credentials/google/status
func (c *CredentialsAPI) GoogleStatus(ctx context.Context) (map[string]interface{}, error) {
	data, err := c.h.get(ctx, "/api/v1/private/credentials/google/status", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// GoogleSetDefault selects which connected Google account is used for one
// capability. accountId is a 24-hex-char id. capability is one of: gmail,
// calendar, drive, sheets, docs, search_console, youtube, google_ads,
// analytics, meet, slides.
//
// PUT /api/v1/private/credentials/google/default
func (c *CredentialsAPI) GoogleSetDefault(ctx context.Context, capability, accountID string) (bool, error) {
	data, err := c.h.put(ctx, "/api/v1/private/credentials/google/default", map[string]string{
		"capability": capability, "accountId": accountID,
	})
	if err != nil {
		return false, err
	}
	var v successResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Success, nil
}

// GoogleDisconnect disconnects one Google product (capability) or an entire
// connected Google account. Pass an empty capability to disconnect the whole
// account.
//
// DELETE /api/v1/private/credentials/google/access
// (JSON body: {accountId, capability?})
func (c *CredentialsAPI) GoogleDisconnect(ctx context.Context, accountID, capability string) (bool, error) {
	body := map[string]string{"accountId": accountID}
	if capability != "" {
		body["capability"] = capability
	}
	data, err := c.h.delWithBody(ctx, "/api/v1/private/credentials/google/access", nil, body)
	if err != nil {
		return false, err
	}
	var v successResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Success, nil
}

// ── Database connector test ──────────────────────────────────────────────────

// DBTestStart probes a database connector from the caller's own runner
// (reaches IP-allow-listed / VPC hosts the cloud can't). Pass credentials to
// test freshly-typed values before saving; omit to test the stored personal
// credential. Poll the result with DBTestStatus.
//
// POST /api/v1/private/credentials/db-test
func (c *CredentialsAPI) DBTestStart(ctx context.Context, service string, credentials map[string]string) (*DBTestStartResult, error) {
	body := map[string]interface{}{"service": service}
	if len(credentials) > 0 {
		body["credentials"] = credentials
	}
	data, err := c.h.post(ctx, "/api/v1/private/credentials/db-test", body)
	if err != nil {
		return nil, err
	}
	var v DBTestStartResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// DBTestStatus polls a database connector runner-test result started by DBTestStart.
//
// GET /api/v1/private/credentials/db-test/:sessionId
func (c *CredentialsAPI) DBTestStatus(ctx context.Context, sessionID string) (*DBTestStatusResult, error) {
	path := "/api/v1/private/credentials/db-test/" + url.PathEscape(sessionID)
	data, err := c.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v DBTestStatusResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// ── Telegram QR login ─────────────────────────────────────────────────────────

// TelegramQrStart starts Telegram user QR-code login (an alternative to the
// SMS-code flow started by TelegramAuthStart). Returns a handle (prefixed
// "tgauth_") plus a qr_url to render as a QR code — poll with TelegramQrPoll.
//
// POST /api/v1/private/credentials/telegram/auth/qr/start
func (c *CredentialsAPI) TelegramQrStart(ctx context.Context, apiID int, apiHash string) (map[string]interface{}, error) {
	data, err := c.h.post(ctx, "/api/v1/private/credentials/telegram/auth/qr/start", map[string]interface{}{
		"api_id": apiID, "api_hash": apiHash,
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

// TelegramQrPoll polls a Telegram QR login started by TelegramQrStart. handle
// starts with "tgauth_".
//
// POST /api/v1/private/credentials/telegram/auth/qr/poll
func (c *CredentialsAPI) TelegramQrPoll(ctx context.Context, handle string) (map[string]interface{}, error) {
	data, err := c.h.post(ctx, "/api/v1/private/credentials/telegram/auth/qr/poll", map[string]string{"handle": handle})
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ── WhatsApp Embedded Signup ──────────────────────────────────────────────────

// WhatsappSignupConfig returns the WhatsApp Embedded Signup config (appId,
// configId) needed to launch Meta's signup flow client-side.
//
// GET /api/v1/private/credentials/whatsapp/embedded-signup/config
func (c *CredentialsAPI) WhatsappSignupConfig(ctx context.Context) (map[string]interface{}, error) {
	data, err := c.h.get(ctx, "/api/v1/private/credentials/whatsapp/embedded-signup/config", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// WhatsappSignupExchange exchanges a WhatsApp Embedded Signup authorization
// code for a business-scoped access token. Set body.Project to store the
// credential at project scope (editor/owner required); omit for personal scope.
//
// POST /api/v1/private/credentials/whatsapp/embedded-signup/exchange
func (c *CredentialsAPI) WhatsappSignupExchange(ctx context.Context, body WhatsappSignupExchangeBody) (map[string]interface{}, error) {
	data, err := c.h.post(ctx, "/api/v1/private/credentials/whatsapp/embedded-signup/exchange", body)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ── TikTok ────────────────────────────────────────────────────────────────────

// TiktokCreatorInfo returns the connected TikTok account's creator info
// (nickname, allowed privacy levels, interaction availability) for a
// compliant post-to-TikTok approval UI.
//
// GET /api/v1/private/credentials/tiktok/creator-info
func (c *CredentialsAPI) TiktokCreatorInfo(ctx context.Context) (map[string]interface{}, error) {
	data, err := c.h.get(ctx, "/api/v1/private/credentials/tiktok/creator-info", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ── Substack ──────────────────────────────────────────────────────────────────

// SubstackEmailLinkSend asks Substack to email a sign-in link to email.
//
// POST /api/v1/private/credentials/substack/email-link
func (c *CredentialsAPI) SubstackEmailLinkSend(ctx context.Context, email string) (map[string]interface{}, error) {
	data, err := c.h.post(ctx, "/api/v1/private/credentials/substack/email-link", map[string]string{"email": email})
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// SubstackEmailLinkRedeem finishes Substack sign-in using the link emailed by
// SubstackEmailLinkSend. email is optional.
//
// POST /api/v1/private/credentials/substack/email-link/redeem
func (c *CredentialsAPI) SubstackEmailLinkRedeem(ctx context.Context, link, email string) (map[string]interface{}, error) {
	body := map[string]string{"link": link}
	if email != "" {
		body["email"] = email
	}
	data, err := c.h.post(ctx, "/api/v1/private/credentials/substack/email-link/redeem", body)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ── CLI auth ──────────────────────────────────────────────────────────────────

// CliAuthStart starts a CLI authentication flow (device-code style).
//
// POST /api/v1/private/credentials/cli-auth
func (c *CredentialsAPI) CliAuthStart(ctx context.Context) (map[string]interface{}, error) {
	data, err := c.h.post(ctx, "/api/v1/private/credentials/cli-auth", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ── NotebookLM ────────────────────────────────────────────────────────────────

// NotebookLMLogin stores NotebookLM credentials.
//
// POST /api/v1/private/credentials/notebooklm/login
func (c *CredentialsAPI) NotebookLMLogin(ctx context.Context, body map[string]interface{}) (map[string]interface{}, error) {
	data, err := c.h.post(ctx, "/api/v1/private/credentials/notebooklm/login", body)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// NotebookLMStatus checks NotebookLM connection status.
//
// GET /api/v1/private/credentials/notebooklm/status
func (c *CredentialsAPI) NotebookLMStatus(ctx context.Context) (map[string]interface{}, error) {
	data, err := c.h.get(ctx, "/api/v1/private/credentials/notebooklm/status", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ── Telegram auth ─────────────────────────────────────────────────────────────

// TelegramAuthStart starts Telegram user auth (phone number step).
//
// POST /api/v1/private/credentials/telegram/auth
func (c *CredentialsAPI) TelegramAuthStart(ctx context.Context, body map[string]interface{}) (map[string]interface{}, error) {
	data, err := c.h.post(ctx, "/api/v1/private/credentials/telegram/auth", body)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// TelegramAuthCode submits the Telegram SMS verification code.
//
// POST /api/v1/private/credentials/telegram/auth/code
func (c *CredentialsAPI) TelegramAuthCode(ctx context.Context, body map[string]interface{}) (map[string]interface{}, error) {
	data, err := c.h.post(ctx, "/api/v1/private/credentials/telegram/auth/code", body)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// TelegramAuth2FA submits the Telegram 2FA password.
//
// POST /api/v1/private/credentials/telegram/auth/2fa
func (c *CredentialsAPI) TelegramAuth2FA(ctx context.Context, body map[string]interface{}) (map[string]interface{}, error) {
	data, err := c.h.post(ctx, "/api/v1/private/credentials/telegram/auth/2fa", body)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}
