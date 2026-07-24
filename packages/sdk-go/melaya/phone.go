// Phone API — pair and control Android devices connected to the Melaya runner.
//
// Maps to /api/v1/private/phone/*.
package melaya

import (
	"context"
	"net/url"
)

// PhoneAPI wraps phone device control endpoints.
type PhoneAPI struct {
	h *httpClient
}

// Pair starts phone device pairing and generates a pairing code.
// Show the code to the user; they enter it in the Melaya APK to pair.
//
// POST /api/v1/private/phone/pair
func (p *PhoneAPI) Pair(ctx context.Context) (*PhonePairResult, error) {
	data, err := p.h.post(ctx, "/api/v1/private/phone/pair", nil)
	if err != nil {
		return nil, err
	}
	var v PhonePairResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// ListDevices lists all paired phone devices for the authenticated user.
//
// GET /api/v1/private/phone/devices
func (p *PhoneAPI) ListDevices(ctx context.Context) ([]PhoneDevice, error) {
	data, err := p.h.get(ctx, "/api/v1/private/phone/devices", nil)
	if err != nil {
		return nil, err
	}
	var v struct {
		Devices []PhoneDevice `json:"devices"`
	}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v.Devices, nil
}

// RevokeDevice revokes a paired phone device by ID.
//
// DELETE /api/v1/private/phone/devices/:deviceId
func (p *PhoneAPI) RevokeDevice(ctx context.Context, deviceID string) (bool, error) {
	path := "/api/v1/private/phone/devices/" + url.PathEscape(deviceID)
	data, err := p.h.del(ctx, path, nil)
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}

// ScreenTree returns the current accessibility tree from the paired phone's screen.
//
// GET /api/v1/private/phone/screen-tree
func (p *PhoneAPI) ScreenTree(ctx context.Context) (map[string]interface{}, error) {
	data, err := p.h.get(ctx, "/api/v1/private/phone/screen-tree", nil)
	if err != nil {
		return nil, err
	}
	var v map[string]interface{}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// ListApps lists installed apps on the paired phone.
//
// GET /api/v1/private/phone/apps
func (p *PhoneAPI) ListApps(ctx context.Context) ([]PhoneApp, error) {
	data, err := p.h.get(ctx, "/api/v1/private/phone/apps", nil)
	if err != nil {
		return nil, err
	}
	var v struct {
		Result struct {
			Apps []PhoneApp `json:"apps"`
		} `json:"result"`
	}
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v.Result.Apps, nil
}

// SetAllowedApps sets the allowlist of apps that agents are permitted to interact with.
// Pass a slice of package names.
//
// PUT /api/v1/private/phone/apps/allowed
func (p *PhoneAPI) SetAllowedApps(ctx context.Context, packageNames []string) (bool, error) {
	apps := make([]map[string]string, len(packageNames))
	for i, name := range packageNames {
		apps[i] = map[string]string{"package": name}
	}
	data, err := p.h.put(ctx, "/api/v1/private/phone/apps/allowed", map[string]interface{}{
		"apps": apps,
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

// RegisterActiveRun registers the currently active pipeline run on the phone.
//
// POST /api/v1/private/phone/active-run
func (p *PhoneAPI) RegisterActiveRun(ctx context.Context, runID string) (bool, error) {
	data, err := p.h.post(ctx, "/api/v1/private/phone/active-run", map[string]string{"runId": runID})
	if err != nil {
		return false, err
	}
	var v okResult
	if err := unmarshal(data, &v); err != nil {
		return false, err
	}
	return v.Ok, nil
}
