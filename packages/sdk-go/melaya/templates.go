// Templates API — create, manage, share, and assign pipeline templates.
//
// Maps to /api/v1/private/user-templates/* and /api/v1/private/templates/*.
package melaya

import (
	"context"
	"net/url"
)

// TemplatesAPI wraps pipeline template management endpoints.
type TemplatesAPI struct {
	h *httpClient
}

// List returns all templates visible to the caller (own + team + community + assigned).
//
// GET /api/v1/private/user-templates
func (t *TemplatesAPI) List(ctx context.Context) ([]UserTemplate, error) {
	data, err := t.h.get(ctx, "/api/v1/private/user-templates", nil)
	if err != nil {
		return nil, err
	}
	var v []UserTemplate
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ListGlobal lists all community-visibility (global) templates.
//
// GET /api/v1/private/templates/global
func (t *TemplatesAPI) ListGlobal(ctx context.Context) ([]UserTemplate, error) {
	data, err := t.h.get(ctx, "/api/v1/private/templates/global", nil)
	if err != nil {
		return nil, err
	}
	var v []UserTemplate
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ListValidated lists the IDs of all validated (platform-approved) templates.
//
// GET /api/v1/private/templates/validated
func (t *TemplatesAPI) ListValidated(ctx context.Context) ([]string, error) {
	data, err := t.h.get(ctx, "/api/v1/private/templates/validated", nil)
	if err != nil {
		return nil, err
	}
	var v []string
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Save creates a new private user template.
//
// POST /api/v1/private/user-templates
func (t *TemplatesAPI) Save(ctx context.Context, body TemplateSaveBody) (*UserTemplate, error) {
	data, err := t.h.post(ctx, "/api/v1/private/user-templates", body)
	if err != nil {
		return nil, err
	}
	var v UserTemplate
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Update updates name/description/category/payload of a private user template.
//
// PATCH /api/v1/private/user-templates/:id
func (t *TemplatesAPI) Update(ctx context.Context, templateID string, body TemplateUpdateBody) (*UserTemplate, error) {
	path := "/api/v1/private/user-templates/" + url.PathEscape(templateID)
	data, err := t.h.patch(ctx, path, body)
	if err != nil {
		return nil, err
	}
	var v UserTemplate
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Duplicate duplicates a readable template into the caller's private library.
//
// POST /api/v1/private/user-templates/:sourceId/duplicate
func (t *TemplatesAPI) Duplicate(ctx context.Context, sourceID string, newName string) (*UserTemplate, error) {
	path := "/api/v1/private/user-templates/" + url.PathEscape(sourceID) + "/duplicate"
	var body interface{}
	if newName != "" {
		body = map[string]string{"newName": newName}
	}
	data, err := t.h.post(ctx, path, body)
	if err != nil {
		return nil, err
	}
	var v UserTemplate
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Delete deletes (or soft-demotes if shared) a template.
//
// DELETE /api/v1/private/user-templates/:id
func (t *TemplatesAPI) Delete(ctx context.Context, templateID string) (bool, error) {
	path := "/api/v1/private/user-templates/" + url.PathEscape(templateID)
	data, err := t.h.del(ctx, path, nil)
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}

// Share changes the visibility of a template.
//
// PUT /api/v1/private/user-templates/:id/visibility
func (t *TemplatesAPI) Share(ctx context.Context, templateID string, visibility TemplateVisibility) (bool, error) {
	path := "/api/v1/private/user-templates/" + url.PathEscape(templateID) + "/visibility"
	data, err := t.h.put(ctx, path, map[string]string{"visibility": visibility})
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}

// ListAssignments lists all assignments for a given template.
//
// GET /api/v1/private/user-templates/:templateId/assignments
func (t *TemplatesAPI) ListAssignments(ctx context.Context, templateID string) ([]TemplateAssignment, error) {
	path := "/api/v1/private/user-templates/" + url.PathEscape(templateID) + "/assignments"
	data, err := t.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v []TemplateAssignment
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Assign assigns a template to a user or project. Set exactly one of
// body.UserID or body.ProjectID (both are UUIDs); the server rejects any
// other combination. The target is sent in the JSON body as {userId} or
// {projectId}.
//
// POST /api/v1/private/user-templates/:templateId/assignments
func (t *TemplatesAPI) Assign(ctx context.Context, templateID string, body TemplateAssignBody) (bool, error) {
	path := "/api/v1/private/user-templates/" + url.PathEscape(templateID) + "/assignments"
	data, err := t.h.post(ctx, path, body)
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}

// Unassign removes an assignment from a template. Set exactly one of
// body.UserID or body.ProjectID (both are UUIDs). The target is sent as
// query params (userId or projectId) — the REST bridge ignores DELETE
// request bodies.
//
// DELETE /api/v1/private/user-templates/:templateId/assignments?userId=|projectId=
func (t *TemplatesAPI) Unassign(ctx context.Context, templateID string, body TemplateAssignBody) (bool, error) {
	path := "/api/v1/private/user-templates/" + url.PathEscape(templateID) + "/assignments"
	query := map[string]string{
		"userId":    body.UserID,
		"projectId": body.ProjectID,
	}
	data, err := t.h.del(ctx, path, query)
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}

// ShareTargets lists projects the caller is a member of (for the share target picker).
//
// GET /api/v1/private/user-templates/share-targets
func (t *TemplatesAPI) ShareTargets(ctx context.Context) ([]map[string]interface{}, error) {
	data, err := t.h.get(ctx, "/api/v1/private/user-templates/share-targets", nil)
	if err != nil {
		return nil, err
	}
	var v []map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}
