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
