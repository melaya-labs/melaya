// Connectors API — project-scoped connector credentials.
//
// Maps to /api/v1/private/projects/:project/connectors/*.
// Each project can hold independent credentials for the same service.
package melaya

import (
	"context"
	"net/url"
)

// ConnectorsAPI wraps project-scoped connector credential endpoints.
type ConnectorsAPI struct {
	h *httpClient
}

// ConnectedServices lists connected services for a project.
//
// GET /api/v1/private/projects/:project/connectors/services
func (c *ConnectorsAPI) ConnectedServices(ctx context.Context, project string) ([]ConnectedService, error) {
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/connectors/services"
	data, err := c.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v []ConnectedService
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Set stores a connector credential at project scope.
//
// PUT /api/v1/private/projects/:project/connectors/:service
func (c *ConnectorsAPI) Set(ctx context.Context, project, service string, body ProjectConnectorSetBody) (bool, error) {
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/connectors/" + url.PathEscape(service)
	data, err := c.h.put(ctx, path, body)
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}

// Delete deletes a project-scoped connector credential.
//
// DELETE /api/v1/private/projects/:project/connectors/:service
func (c *ConnectorsAPI) Delete(ctx context.Context, project, service string) (bool, error) {
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/connectors/" + url.PathEscape(service)
	data, err := c.h.del(ctx, path, nil)
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}

// EnvHandle returns a short-lived env-handle token for project-scoped credentials.
// The runner uses this token to decrypt credentials without a full session.
//
// POST /api/v1/private/projects/:project/connectors/env-handle
func (c *ConnectorsAPI) EnvHandle(ctx context.Context, project string) (map[string]interface{}, error) {
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/connectors/env-handle"
	data, err := c.h.post(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// GoogleOAuthStart starts a Google OAuth flow for a project-scoped connector.
//
// POST /api/v1/private/projects/:project/connectors/google/oauth
func (c *ConnectorsAPI) GoogleOAuthStart(ctx context.Context, project string, body map[string]interface{}) (map[string]interface{}, error) {
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/connectors/google/oauth"
	data, err := c.h.post(ctx, path, body)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ApplyPersonal shares the caller's OWN personal connector credential into the
// project pool (requires editor or owner role on the project). Pass
// googleCapabilities to scope which granted Google capabilities are copied
// when service is "google"; nil/empty copies the default set.
//
// POST /api/v1/private/projects/:project/connectors/:service/apply-personal
func (c *ConnectorsAPI) ApplyPersonal(ctx context.Context, project, service string, googleCapabilities []string) (map[string]interface{}, error) {
	body := map[string]interface{}{}
	if len(googleCapabilities) > 0 {
		body["googleCapabilities"] = googleCapabilities
	}
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/connectors/" + url.PathEscape(service) + "/apply-personal"
	data, err := c.h.post(ctx, path, body)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// SharedBy returns which member shared each connected project connector
// (usernames only — never credential values).
//
// GET /api/v1/private/projects/:project/connectors/shared-by
func (c *ConnectorsAPI) SharedBy(ctx context.Context, project string) (map[string]interface{}, error) {
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/connectors/shared-by"
	data, err := c.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ── Several accounts per project connector ──────────────────────────────────
// Same as the personal-scope account methods on CredentialsAPI, for a
// project's connectors. Agents use the DEFAULT account unless a tool call
// names another one. Only labels and ids ever come back, never credential values.

func projectAccountsPath(project, service string) string {
	return "/api/v1/private/projects/" + url.PathEscape(project) + "/connectors/" + url.PathEscape(service) + "/accounts"
}

// Accounts lists the accounts connected to one project connector. An account
// with ID "current" is a single connection made before accounts existed.
//
// GET /api/v1/private/projects/{project}/connectors/{service}/accounts
func (c *ConnectorsAPI) Accounts(ctx context.Context, project, service string) ([]ConnectorAccount, error) {
	return decodeConnectorAccounts(c.h.get(ctx, projectAccountsPath(project, service), nil))
}

// AddAccount adds another account to a project's field-based connector.
// in.Fields are the connector's credential fields; the connection is tested
// first. in.CurrentLabel names the existing single connection when it is
// adopted as the first account. Returns the updated list.
//
// POST /api/v1/private/projects/{project}/connectors/{service}/accounts
func (c *ConnectorsAPI) AddAccount(ctx context.Context, project, service string, in AddConnectorAccountInput) ([]ConnectorAccount, error) {
	if in.Fields == nil {
		in.Fields = map[string]string{}
	}
	return decodeConnectorAccounts(c.h.post(ctx, projectAccountsPath(project, service), in))
}

// SetDefaultAccount chooses which account the project connector (and so every
// agent of the project) uses. Returns the updated list.
//
// PUT /api/v1/private/projects/{project}/connectors/{service}/accounts/default
func (c *ConnectorsAPI) SetDefaultAccount(ctx context.Context, project, service, accountID string) ([]ConnectorAccount, error) {
	return decodeConnectorAccounts(c.h.put(ctx, projectAccountsPath(project, service)+"/default", map[string]string{"accountId": accountID}))
}

// RenameAccount renames one account of a project connector (max 80 chars).
// Returns the updated list.
//
// PUT /api/v1/private/projects/{project}/connectors/{service}/accounts/{accountId}
func (c *ConnectorsAPI) RenameAccount(ctx context.Context, project, service, accountID, label string) ([]ConnectorAccount, error) {
	path := projectAccountsPath(project, service) + "/" + url.PathEscape(accountID)
	return decodeConnectorAccounts(c.h.put(ctx, path, map[string]string{"label": label}))
}

// RemoveAccount removes one account from a project connector. Returns the
// remaining list.
//
// DELETE /api/v1/private/projects/{project}/connectors/{service}/accounts/{accountId}
func (c *ConnectorsAPI) RemoveAccount(ctx context.Context, project, service, accountID string) ([]ConnectorAccount, error) {
	path := projectAccountsPath(project, service) + "/" + url.PathEscape(accountID)
	return decodeConnectorAccounts(c.h.del(ctx, path, nil))
}

// GoogleStatus lists the Google OAuth capabilities actually granted to a project.
//
// GET /api/v1/private/projects/:project/connectors/google/status
func (c *ConnectorsAPI) GoogleStatus(ctx context.Context, project string) (map[string]interface{}, error) {
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/connectors/google/status"
	data, err := c.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// GoogleSetDefault selects which connected Google account a project uses for
// one capability. accountId is a 24-hex-char id. capability is one of: gmail,
// calendar, drive, sheets, docs, search_console, youtube, google_ads,
// analytics, meet, slides.
//
// PUT /api/v1/private/projects/:project/connectors/google/default
func (c *ConnectorsAPI) GoogleSetDefault(ctx context.Context, project, capability, accountID string) (bool, error) {
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/connectors/google/default"
	data, err := c.h.put(ctx, path, map[string]string{"capability": capability, "accountId": accountID})
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
// connected Google account from a project. Pass an empty capability to
// disconnect the whole account.
//
// DELETE /api/v1/private/projects/:project/connectors/google/access
// (JSON body: {accountId, capability?})
func (c *ConnectorsAPI) GoogleDisconnect(ctx context.Context, project, accountID, capability string) (bool, error) {
	body := map[string]string{"accountId": accountID}
	if capability != "" {
		body["capability"] = capability
	}
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/connectors/google/access"
	data, err := c.h.delWithBody(ctx, path, nil, body)
	if err != nil {
		return false, err
	}
	var v successResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Success, nil
}

// DBTestStart probes a database connector from the project's own runner
// (reaches IP-allow-listed / VPC hosts the cloud can't). Pass credentials to
// test freshly-typed values before saving; omit to test the stored project
// credential. Poll the result with DBTestStatus.
//
// POST /api/v1/private/projects/:project/connectors/db-test
func (c *ConnectorsAPI) DBTestStart(ctx context.Context, project, service string, credentials map[string]string) (*DBTestStartResult, error) {
	body := map[string]interface{}{"service": service}
	if len(credentials) > 0 {
		body["credentials"] = credentials
	}
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/connectors/db-test"
	data, err := c.h.post(ctx, path, body)
	if err != nil {
		return nil, err
	}
	var v DBTestStartResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// DBTestStatus polls a project database connector runner-test result started
// by DBTestStart.
//
// GET /api/v1/private/projects/:project/connectors/db-test/:sessionId
func (c *ConnectorsAPI) DBTestStatus(ctx context.Context, project, sessionID string) (*DBTestStatusResult, error) {
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/connectors/db-test/" + url.PathEscape(sessionID)
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
