// Team API — manage project team membership, roles, and invitations.
//
// Maps to /api/v1/private/projects/:project/members/* and /api/v1/private/team/*.
package melaya

import (
	"context"
	"net/url"
)

// TeamAPI wraps project team management endpoints.
type TeamAPI struct {
	h *httpClient
}

// ListMembers lists all members of a project team.
//
// GET /api/v1/private/projects/:project/members
func (t *TeamAPI) ListMembers(ctx context.Context, project string) ([]TeamMember, error) {
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/members"
	data, err := t.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v []TeamMember
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Invite invites a user to a project team by username.
//
// POST /api/v1/private/projects/:project/members/invite
func (t *TeamAPI) Invite(ctx context.Context, project, username string) (bool, error) {
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/members/invite"
	data, err := t.h.post(ctx, path, map[string]string{"username": username})
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}

// CreateInviteLink creates a shareable invite link for a project.
//
// POST /api/v1/private/projects/:project/invite-link
func (t *TeamAPI) CreateInviteLink(ctx context.Context, project string) (*TeamInviteResult, error) {
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/invite-link"
	data, err := t.h.post(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v TeamInviteResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// AcceptInvite accepts a project invite using the token from an invite link.
//
// POST /api/v1/private/team/invite/accept
func (t *TeamAPI) AcceptInvite(ctx context.Context, token string) (bool, error) {
	data, err := t.h.post(ctx, "/api/v1/private/team/invite/accept", map[string]string{"token": token})
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}

// UpdateMemberRole updates a team member's role in a project.
//
// PATCH /api/v1/private/projects/:project/members/:userId
func (t *TeamAPI) UpdateMemberRole(ctx context.Context, project, userID string, role TeamRole) (bool, error) {
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/members/" + url.PathEscape(userID)
	data, err := t.h.patch(ctx, path, map[string]string{"role": role})
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}

// RemoveMember removes a member from a project team.
//
// DELETE /api/v1/private/projects/:project/members/:userId
func (t *TeamAPI) RemoveMember(ctx context.Context, project, userID string) (bool, error) {
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/members/" + url.PathEscape(userID)
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

// GetPipelineVisibility returns visibility settings for a pipeline within a project.
//
// GET /api/v1/private/projects/:project/pipelines/:pipeline/visibility
func (t *TeamAPI) GetPipelineVisibility(ctx context.Context, project, pipeline string) (map[string]interface{}, error) {
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/pipelines/" + url.PathEscape(pipeline) + "/visibility"
	data, err := t.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// SetPipelineVisibility sets pipeline visibility within a project.
//
// PUT /api/v1/private/projects/:project/pipelines/:pipeline/visibility
func (t *TeamAPI) SetPipelineVisibility(ctx context.Context, project, pipeline string, body map[string]interface{}) (bool, error) {
	path := "/api/v1/private/projects/" + url.PathEscape(project) + "/pipelines/" + url.PathEscape(pipeline) + "/visibility"
	data, err := t.h.put(ctx, path, body)
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}
