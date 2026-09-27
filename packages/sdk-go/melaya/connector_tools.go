// Connector tools API — the same list/discover/test/connect/call surface the
// Melaya MCP server exposes to an assistant, callable directly over REST.
//
// Maps to /api/v1/private/connector-tools/*. Do not confuse this with the
// ConnectorsAPI (connectors.go): that module stores project-scoped connector
// CREDENTIALS; this module CALLS the tools those credentials unlock.
//
// Reads run immediately. Writes default to an approval card raised in the
// Melaya app (Approval: "required" — the same card the Assistant raises);
// pass Approval: "none" to run a write immediately instead, which is still
// audit-logged. Tools that move money or trade are always refused, under
// both approval modes. No method here ever accepts or returns a credential
// value.
//
// Example:
//
//	tools, _ := m.ConnectorTools.Search(ctx, "unread email", nil)
//	res, _ := m.ConnectorTools.Call(ctx, "gmail_list_messages", nil, nil)
//	fmt.Println(res.Result)
//
//	// A write, approved in the Melaya app, waited on synchronously:
//	out, _ := m.ConnectorTools.CallAndWait(ctx, "gmail_send",
//		map[string]interface{}{"to": "a@b.com", "subject": "hi", "body": "…"}, nil)
package melaya

import (
	"context"
	"net/url"
	"strconv"
	"time"
)

// connectorToolsBase is the base path for the connector tools REST surface.
const connectorToolsBase = "/api/v1/private/connector-tools"

// ConnectorToolsAPI wraps the connector tool call surface. See the package
// doc comment above for the write/approval policy.
type ConnectorToolsAPI struct {
	h *httpClient
}

// ConnectorToolParamInfo describes one parameter a connector tool accepts.
type ConnectorToolParamInfo struct {
	Type        string      `json:"type,omitempty"`
	Required    bool        `json:"required,omitempty"`
	Default     interface{} `json:"default,omitempty"`
	Description string      `json:"description,omitempty"`
}

// ConnectorToolInfo describes one connector tool.
type ConnectorToolInfo struct {
	Name        string                            `json:"name"`
	Service     string                            `json:"service"`
	Description string                            `json:"description,omitempty"`
	ReadOnly    bool                              `json:"readOnly"`
	MovesMoney  bool                              `json:"movesMoney"`
	Params      map[string]ConnectorToolParamInfo `json:"params,omitempty"`
}

// ConnectorToolServiceCounts is the read/write tool counts for one connected service.
type ConnectorToolServiceCounts struct {
	ReadTools  int `json:"readTools"`
	WriteTools int `json:"writeTools"`
}

// ConnectorToolServices is the result of ConnectorToolsAPI.Services.
type ConnectorToolServices struct {
	Services []string `json:"services"`
	// BuiltIn is always "melaya_core" — the always-available built-in tool set.
	BuiltIn    string                                `json:"builtIn"`
	ToolCounts map[string]ConnectorToolServiceCounts `json:"toolCounts"`
}

// ConnectorToolSearchOptions configures ConnectorToolsAPI.Search.
type ConnectorToolSearchOptions struct {
	// Limit caps the number of results, 1-50. Zero uses the server default (15).
	Limit int
}

// ConnectorToolSearchResult is the result of ConnectorToolsAPI.Search.
type ConnectorToolSearchResult struct {
	Query    string              `json:"query"`
	Services []string            `json:"services"`
	Tools    []ConnectorToolInfo `json:"tools"`
}

// ConnectorToolTestResult is the result of ConnectorToolsAPI.Test.
type ConnectorToolTestResult struct {
	Service string `json:"service"`
	Success bool   `json:"success"`
	Message string `json:"message,omitempty"`
}

// ConnectorToolConnectResult is the result of ConnectorToolsAPI.Connect. Kind
// is one of "oauth", "oauth_unavailable", "interactive_login", or "api_key".
// AuthorizationURL is set only for "oauth" (open it in a browser);
// ConnectURL is set for "interactive_login" and "api_key" (the Melaya
// Connectors page — no method here ever accepts a secret).
type ConnectorToolConnectResult struct {
	Service          string `json:"service"`
	Kind             string `json:"kind"`
	AuthorizationURL string `json:"authorizationUrl,omitempty"`
	ConnectURL       string `json:"connectUrl,omitempty"`
	Message          string `json:"message,omitempty"`
}

// ConnectorToolCallOptions configures ConnectorToolsAPI.Call.
type ConnectorToolCallOptions struct {
	// Approval is "required" (default — stages a write as an approval card in
	// the Melaya app) or "none" (runs a write immediately; still
	// audit-logged). Ignored for read tools, which always run immediately.
	// Money-moving/trading tools are refused under both.
	Approval string
}

// ConnectorToolCallResult is the result of ConnectorToolsAPI.Call. Status is
// "done" for a read, or a write run immediately with Approval: "none" —
// Result is then set. Status is "pending_approval" for a write staged under
// the default Approval: "required" — RequestID is then set; fetch the
// eventual outcome with CallStatus or CallAndWait.
//
// A 202 HTTP response ("pending_approval") is a success, not an error — it
// decodes into this struct like any other response.
type ConnectorToolCallResult struct {
	Status    string `json:"status"`
	Tool      string `json:"tool"`
	ReadOnly  bool   `json:"readOnly,omitempty"`
	Result    string `json:"result,omitempty"`
	RequestID string `json:"requestId,omitempty"`
	Message   string `json:"message,omitempty"`
}

// ConnectorToolCallStatus is the outcome of a staged write, returned by
// CallStatus and CallAndWait. Status is one of "pending", "running",
// "expired", "done", or "rejected". Ok/Result/Error are only meaningful once
// Status is "done"; Reason is only meaningful when Status is "rejected".
type ConnectorToolCallStatus struct {
	RequestID string `json:"requestId"`
	Tool      string `json:"tool,omitempty"`
	Status    string `json:"status"`
	Ok        bool   `json:"ok,omitempty"`
	Result    string `json:"result,omitempty"`
	Error     string `json:"error,omitempty"`
	Reason    string `json:"reason,omitempty"`
}

// ConnectorToolCallAndWaitOptions configures ConnectorToolsAPI.CallAndWait.
type ConnectorToolCallAndWaitOptions struct {
	// Approval is passed through to the underlying Call — see
	// ConnectorToolCallOptions.Approval.
	Approval string
	// PollInterval is the delay between CallStatus polls. Default 3s.
	PollInterval time.Duration
	// Timeout bounds the total time spent polling. Default 10 minutes. When
	// it elapses before a terminal status, CallAndWait returns the last
	// polled (still-pending) status rather than an error.
	Timeout time.Duration
}

// Services lists connected services and their read/write tool counts.
//
// GET /api/v1/private/connector-tools/services
func (c *ConnectorToolsAPI) Services(ctx context.Context) (*ConnectorToolServices, error) {
	data, err := c.h.get(ctx, connectorToolsBase+"/services", nil)
	if err != nil {
		return nil, err
	}
	var v ConnectorToolServices
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Search discovers tools by plain business keywords (e.g. "unread email").
// q is required.
//
// GET /api/v1/private/connector-tools/search?q=&limit=
func (c *ConnectorToolsAPI) Search(ctx context.Context, q string, opts *ConnectorToolSearchOptions) (*ConnectorToolSearchResult, error) {
	query := map[string]string{"q": q}
	if opts != nil && opts.Limit > 0 {
		query["limit"] = strconv.Itoa(opts.Limit)
	}
	data, err := c.h.get(ctx, connectorToolsBase+"/search", query)
	if err != nil {
		return nil, err
	}
	var v ConnectorToolSearchResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Describe returns one tool's full description and parameters. Returns a
// *MelayaError with Status 404 when tool is unknown, or not unlocked by the
// caller's connected services.
//
// GET /api/v1/private/connector-tools/tools/:tool
func (c *ConnectorToolsAPI) Describe(ctx context.Context, tool string) (*ConnectorToolInfo, error) {
	path := connectorToolsBase + "/tools/" + url.PathEscape(tool)
	data, err := c.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v ConnectorToolInfo
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Test tests the STORED credential for a connected service. Returns a
// *MelayaError with Status 504 (Code "timeout") if the service does not
// answer within 30 seconds.
//
// POST /api/v1/private/connector-tools/test
func (c *ConnectorToolsAPI) Test(ctx context.Context, service string) (*ConnectorToolTestResult, error) {
	data, err := c.h.post(ctx, connectorToolsBase+"/test", map[string]string{"service": service})
	if err != nil {
		return nil, err
	}
	var v ConnectorToolTestResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Connect starts connecting a service. It never takes a secret: an "oauth"
// result carries an AuthorizationURL for the user to open; every other kind
// carries a ConnectURL to the Melaya Connectors page instead.
//
// POST /api/v1/private/connector-tools/connect
func (c *ConnectorToolsAPI) Connect(ctx context.Context, service string) (*ConnectorToolConnectResult, error) {
	data, err := c.h.post(ctx, connectorToolsBase+"/connect", map[string]string{"service": service})
	if err != nil {
		return nil, err
	}
	var v ConnectorToolConnectResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Call invokes a connector tool. A read tool, or a write called with
// Approval: "none", runs immediately and returns Status "done" with Result
// set. A write called under the default Approval: "required" is staged as
// the same approval card the Assistant raises and returns Status
// "pending_approval" with RequestID set; poll CallStatus (or use
// CallAndWait) once the user has decided in the Melaya app.
//
// Tools that move money or trade are refused under both approval modes —
// the call then returns a *MelayaError with Code
// "money_moving_requires_app_approval" (HTTP 403); check it with
// err.(*MelayaError).IsMoneyMovingRefused().
//
// opts may be nil, which defaults Approval to "required".
//
// POST /api/v1/private/connector-tools/call
func (c *ConnectorToolsAPI) Call(ctx context.Context, tool string, args map[string]interface{}, opts *ConnectorToolCallOptions) (*ConnectorToolCallResult, error) {
	body := map[string]interface{}{"tool": tool}
	if len(args) > 0 {
		body["args"] = args
	}
	approval := "required"
	if opts != nil && opts.Approval != "" {
		approval = opts.Approval
	}
	body["approval"] = approval

	data, err := c.h.post(ctx, connectorToolsBase+"/call", body)
	if err != nil {
		return nil, err
	}
	var v ConnectorToolCallResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// CallStatus returns the outcome of a staged write started by Call. Returns
// a *MelayaError with Status 404 when requestID is unknown or expired.
//
// GET /api/v1/private/connector-tools/calls/:requestId
func (c *ConnectorToolsAPI) CallStatus(ctx context.Context, requestID string) (*ConnectorToolCallStatus, error) {
	path := connectorToolsBase + "/calls/" + url.PathEscape(requestID)
	data, err := c.h.get(ctx, path, nil)
	if err != nil {
		return nil, err
	}
	var v ConnectorToolCallStatus
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// CallAndWait calls a tool and, when the write is staged for approval,
// blocks polling CallStatus (every opts.PollInterval, default 3s) until the
// outcome is "done", "rejected", or "expired", or until opts.Timeout elapses
// (default 10 minutes — see ConnectorToolCallAndWaitOptions.Timeout). A read,
// or a write called with Approval: "none", returns immediately without
// polling. opts may be nil to use every default.
func (c *ConnectorToolsAPI) CallAndWait(ctx context.Context, tool string, args map[string]interface{}, opts *ConnectorToolCallAndWaitOptions) (*ConnectorToolCallStatus, error) {
	var callOpts *ConnectorToolCallOptions
	pollInterval := 3 * time.Second
	timeout := 10 * time.Minute
	if opts != nil {
		if opts.Approval != "" {
			callOpts = &ConnectorToolCallOptions{Approval: opts.Approval}
		}
		if opts.PollInterval > 0 {
			pollInterval = opts.PollInterval
		}
		if opts.Timeout > 0 {
			timeout = opts.Timeout
		}
	}

	res, err := c.Call(ctx, tool, args, callOpts)
	if err != nil {
		return nil, err
	}
	if res.Status != "pending_approval" {
		// Read, or a write run immediately under approval "none".
		return &ConnectorToolCallStatus{RequestID: res.RequestID, Tool: res.Tool, Status: "done", Ok: true, Result: res.Result}, nil
	}

	deadline := time.Now().Add(timeout)
	for {
		status, err := c.CallStatus(ctx, res.RequestID)
		if err != nil {
			return nil, err
		}
		switch status.Status {
		case "done", "rejected", "expired":
			return status, nil
		}
		if !time.Now().Before(deadline) {
			return status, nil
		}
		select {
		case <-ctx.Done():
			return nil, ctx.Err()
		case <-time.After(pollInterval):
		}
	}
}
