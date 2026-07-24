// Bugs API — user bug-report feedback surface.
//
// Maps to /api/v1/private/bugs/*.
package melaya

import (
	"context"
	"net/url"
)

// BugsAPI wraps the user-facing bug report endpoints.
type BugsAPI struct {
	h *httpClient
}

// Create submits a bug report.
//
// POST /api/v1/private/bugs
func (b *BugsAPI) Create(ctx context.Context, body BugCreateBody) (*BugReport, error) {
	data, err := b.h.post(ctx, "/api/v1/private/bugs", body)
	if err != nil {
		return nil, err
	}
	var v BugReport
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// ListMine lists bug reports submitted by the caller.
//
// GET /api/v1/private/bugs/mine
func (b *BugsAPI) ListMine(ctx context.Context) ([]BugReport, error) {
	data, err := b.h.get(ctx, "/api/v1/private/bugs/mine", nil)
	if err != nil {
		return nil, err
	}
	var v []BugReport
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Get returns a single bug report by ID.
//
// GET /api/v1/private/bugs/:bugId
func (b *BugsAPI) Get(ctx context.Context, bugID string) (*BugReport, error) {
	path := "/api/v1/private/bugs/" + url.PathEscape(bugID)
	data, err := b.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v BugReport
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// AddComment adds a comment to a bug report.
//
// POST /api/v1/private/bugs/:bugId/comments
func (b *BugsAPI) AddComment(ctx context.Context, bugID, comment string) (map[string]interface{}, error) {
	path := "/api/v1/private/bugs/" + url.PathEscape(bugID) + "/comments"
	data, err := b.h.post(ctx, path, map[string]string{"comment": comment})
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ListNotifications lists unread bug-related notifications for the caller.
//
// GET /api/v1/private/bugs/notifications
func (b *BugsAPI) ListNotifications(ctx context.Context) ([]BugNotification, error) {
	data, err := b.h.get(ctx, "/api/v1/private/bugs/notifications", nil)
	if err != nil {
		return nil, err
	}
	var v []BugNotification
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// MarkNotificationsRead marks bug notifications as read.
//
// POST /api/v1/private/bugs/notifications/read
func (b *BugsAPI) MarkNotificationsRead(ctx context.Context) (bool, error) {
	data, err := b.h.post(ctx, "/api/v1/private/bugs/notifications/read", nil)
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}
