// Projects API — create and list agent projects.
//
// Maps to /api/v1/private/projects/*.
package melaya

import "context"

// ProjectsAPI wraps agent project endpoints.
type ProjectsAPI struct {
	h *httpClient
}

// List returns all projects the authenticated user can access
// (owned + projects they are a member of).
//
// GET /api/v1/private/projects
func (p *ProjectsAPI) List(ctx context.Context) ([]Project, error) {
	data, err := p.h.get(ctx, "/api/v1/private/projects", nil)
	if err != nil {
		return nil, err
	}
	var v []Project
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Create creates a new agent project.
//
// POST /api/v1/private/projects
func (p *ProjectsAPI) Create(ctx context.Context, body ProjectCreate) (*Project, error) {
	data, err := p.h.post(ctx, "/api/v1/private/projects", body)
	if err != nil {
		return nil, err
	}
	var v Project
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Rename renames a project.
//
// PATCH /api/v1/private/projects/rename
func (p *ProjectsAPI) Rename(ctx context.Context, oldName, newName string) (bool, error) {
	data, err := p.h.patch(ctx, "/api/v1/private/projects/rename", map[string]string{
		"oldName": oldName,
		"newName": newName,
	})
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}

// RunnerProjects returns the projects list (runner-facing endpoint).
//
// GET /api/v1/private/projects/runner
func (p *ProjectsAPI) RunnerProjects(ctx context.Context) ([]Project, error) {
	data, err := p.h.get(ctx, "/api/v1/private/projects/runner", nil)
	if err != nil {
		return nil, err
	}
	var v []Project
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}
