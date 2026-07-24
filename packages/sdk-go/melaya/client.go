// Package melaya is the official Go SDK for the Melaya unified market-data,
// trading, and agent-platform API. It covers the full REST surface (trading +
// agents + platform) and real-time Socket.IO events.
//
// # Quick-start
//
//	m, err := melaya.New("mk_...")
//	if err != nil { log.Fatal(err) }
//
// # Domain namespaces (primary API)
//
// Three top-level namespace objects expose the full structure at a glance:
//
//	// Trading — market data, exchange connectivity, strategies
//	t, err := m.Trading.Market.Ticker(ctx, melaya.SymbolQuery{Exchange: "binance", Symbol: "BTC/USDT"})
//	bal, _ := m.Trading.Sim.Balance(ctx)
//
//	// Agents — pipelines, HITL, evals, phone, assistant
//	runs, _    := m.Agents.Pipelines.List(ctx, nil)
//	pending, _ := m.Agents.Hitl.Pending(ctx)
//
//	// Platform — projects, credentials, billing, auth, events…
//	projects, _ := m.Platform.Projects.List(ctx)
//	me, _        := m.Platform.Auth.Me(ctx)
//	unsub := m.Platform.Events.OnRunUpdate("run-123", func(e melaya.RunPushEvent) { fmt.Println(e.EventType) })
//	defer unsub()
//
// # Flat accessors (secondary, fully equivalent)
//
// The flat fields on Client (m.Market, m.Pipelines, m.Projects, …) are the
// same pointers and remain valid — existing code compiles unchanged:
//
//	t, err        := m.Market.Ticker(ctx, melaya.SymbolQuery{Exchange: "binance", Symbol: "BTC/USDT"})
//	projects, err := m.Projects.List(ctx)
//	pending, err  := m.Hitl.Pending(ctx)
package melaya

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"math/rand"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
)

const (
	// DefaultBaseURL is the Melaya REST API base URL.
	DefaultBaseURL = "https://api.melaya.org"
	// DefaultWsURL is the Melaya WebSocket base URL.
	DefaultWsURL = "wss://wss.melaya.org"

	// defaultTimeout is the default per-request HTTP timeout.
	defaultTimeout = 30 * time.Second
	// maxRetries is the maximum number of retries for idempotent (GET) requests.
	// Only GETs are retried; POST/PUT/PATCH/DELETE are never retried.
	maxRetries = 2
)

// Options configures the Melaya client.
type Options struct {
	// APIKey is your mk_-prefixed Melaya platform API key. Required unless
	// BearerToken is set.
	APIKey string
	// BearerToken is a session JWT (obtained from /api/v1/private/auth/login).
	// Takes precedence over APIKey for Authorization headers.
	BearerToken string
	// BaseURL overrides the REST base URL (default: https://api.melaya.org).
	BaseURL string
	// WsURL overrides the WebSocket base URL (default: wss://wss.melaya.org).
	WsURL string
	// Timeout overrides the default HTTP request timeout (default: 30s).
	Timeout time.Duration
	// HTTPClient overrides the underlying http.Client.
	HTTPClient *http.Client
}

// MelayaError is returned for non-2xx HTTP responses or envelope ok==false.
// Body contains the raw decoded response — never log it directly if it may
// contain sensitive data.
type MelayaError struct {
	// Message is a human-readable description, safe to log.
	Message string
	// Status is the HTTP status code.
	Status int
	// Code is the API error code from the response envelope (e.g. "tier_insufficient").
	Code string
	// Body is the raw decoded error response (may be nil).
	Body interface{}
}

func (e *MelayaError) Error() string { return e.Message }

// IsTierInsufficient reports whether the error is a 403 tier gate.
func (e *MelayaError) IsTierInsufficient() bool { return e.Code == "tier_insufficient" }

// IsRateLimited reports whether the error is a 429 rate limit response.
func (e *MelayaError) IsRateLimited() bool { return e.Status == 429 }

// Client is the Melaya SDK entry point. Construct with New().
//
// The client is safe for concurrent use from multiple goroutines.
//
// # Domain namespaces (primary API)
//
// Modules are grouped into three top-level namespaces that make the structure
// immediately visible:
//
//	m.Trading  — Market, Account, Sim, Strategies, Trade, Backtest, Stream
//	m.Agents   — Pipelines, Hitl, Assistant, Phone, Evals
//	m.Platform — Projects, Credentials, Connectors, Billing, Team, Templates,
//	             Overview, Runner, Auth, Accounts, Bugs, Events
//
// Example:
//
//	t, _  := m.Trading.Market.Ticker(ctx, melaya.SymbolQuery{Exchange: "binance", Symbol: "BTC/USDT"})
//	runs, _ := m.Agents.Pipelines.List(ctx, nil)
//	projs, _ := m.Platform.Projects.List(ctx)
//
// # Flat accessors (secondary, fully equivalent)
//
// The flat fields below (m.Market, m.Pipelines, …) are identical pointers and
// remain valid — existing code continues to compile unchanged.
type Client struct {
	http *httpClient

	// ── Domain namespaces (primary / documented API) ───────────────────────
	// Trading groups market-data, exchange connectivity, and trading modules.
	Trading *TradingNamespace
	// Agents groups pipeline execution, human oversight, and AI tooling modules.
	Agents *AgentsNamespace
	// Platform groups identity, projects, billing, infrastructure, and
	// observability modules.
	Platform *PlatformNamespace

	// ── Trading plane (flat accessors — equivalent to Trading.*) ──────────
	// Market provides normalized market-data endpoints (public and private).
	Market *MarketAPI
	// Account provides authenticated account reads.
	Account *AccountAPI
	// Auth provides authentication and MFA endpoints.
	Auth *AuthAPI
	// Sim provides the paper-trading (sim broker) API.
	Sim *SimAPI
	// Strategies provides launch/control/inspection of trading strategies.
	Strategies *StrategiesAPI
	// Backtest provides historical backtests and parameter sweeps.
	Backtest *BacktestAPI
	// Trade provides live credentialed trading (real funds).
	Trade *TradeAPI
	// Stream provides WebSocket streaming (public + private).
	Stream *StreamAPI

	// ── Platform / agents plane ────────────────────────────────────────────
	// Projects provides creation and listing of agent projects.
	Projects *ProjectsAPI
	// Pipelines provides pipeline run overview, traces, and schedules.
	Pipelines *PipelinesAPI
	// Hitl provides the Human-in-the-Loop approval queue.
	Hitl *HitlAPI
	// Credentials provides user-scoped credential storage.
	Credentials *CredentialsAPI
	// Connectors provides project-scoped connector credentials.
	Connectors *ConnectorsAPI
	// Billing provides subscription and Stripe checkout/portal.
	Billing *BillingAPI
	// Phone provides phone device control (pair, screen-tree, apps).
	Phone *PhoneAPI
	// Team provides project team management.
	Team *TeamAPI
	// Templates provides pipeline template management.
	Templates *TemplatesAPI
	// Assistant provides the assistant onboarding profile.
	Assistant *AssistantAPI
	// Runner provides runner token management (mel_run_ tokens).
	Runner *RunnerAPI
	// Overview provides dashboard overview and model pricing.
	Overview *OverviewAPI
	// Evals provides eval run listing and comparison.
	Evals *EvalsAPI
	// Bugs provides the user bug-report feedback surface.
	Bugs *BugsAPI
	// Events is a Socket.IO client for real-time platform events.
	Events *EventsClient
}

// New creates a new Melaya client authenticated with an mk_ platform API key.
//
//	m, err := melaya.New("mk_your_key")
//	m, err := melaya.New("mk_your_key", melaya.Options{Timeout: 60*time.Second})
func New(apiKey string, opts ...Options) (*Client, error) {
	var o Options
	if len(opts) > 0 {
		o = opts[0]
	}
	if o.APIKey == "" {
		o.APIKey = apiKey
	}
	if o.BearerToken == "" && o.APIKey == "" {
		return nil, fmt.Errorf("melaya: apiKey is required (create one at melaya.org → Settings → API Keys)")
	}
	if o.APIKey != "" && !strings.HasPrefix(o.APIKey, "mk_") {
		return nil, fmt.Errorf("melaya: platform API keys must be prefixed mk_")
	}
	if o.BaseURL == "" {
		o.BaseURL = DefaultBaseURL
	}
	if o.WsURL == "" {
		o.WsURL = DefaultWsURL
	}
	if o.Timeout == 0 {
		o.Timeout = defaultTimeout
	}

	hc := o.HTTPClient
	if hc == nil {
		hc = &http.Client{Transport: http.DefaultTransport, Timeout: o.Timeout}
	}

	// Determine the credential to send as Bearer.
	credential := o.APIKey
	if o.BearerToken != "" {
		credential = o.BearerToken
	}

	h := &httpClient{
		credential: credential,
		apiKey:     o.APIKey, // kept separately for WS / Socket.IO auth
		baseURL:    strings.TrimRight(o.BaseURL, "/"),
		hc:         hc,
	}

	c := &Client{http: h}

	// Trading plane
	c.Market = &MarketAPI{h: h}
	c.Account = &AccountAPI{h: h}
	c.Auth = &AuthAPI{h: h}
	c.Sim = &SimAPI{h: h}
	c.Strategies = &StrategiesAPI{h: h}
	c.Backtest = &BacktestAPI{h: h}
	c.Trade = &TradeAPI{h: h}
	c.Stream = &StreamAPI{
		apiKey: o.APIKey,
		wsURL:  strings.TrimRight(o.WsURL, "/"),
		h:      h,
	}

	// Platform / agents plane
	c.Projects = &ProjectsAPI{h: h}
	c.Pipelines = &PipelinesAPI{h: h}
	c.Hitl = &HitlAPI{h: h}
	c.Credentials = &CredentialsAPI{h: h}
	c.Connectors = &ConnectorsAPI{h: h}
	c.Billing = &BillingAPI{h: h}
	c.Phone = &PhoneAPI{h: h}
	c.Team = &TeamAPI{h: h}
	c.Templates = &TemplatesAPI{h: h}
	c.Assistant = &AssistantAPI{h: h}
	c.Runner = &RunnerAPI{h: h}
	c.Overview = &OverviewAPI{h: h}
	c.Evals = &EvalsAPI{h: h}
	c.Bugs = &BugsAPI{h: h}
	c.Events = newEventsClient(h, strings.TrimRight(o.BaseURL, "/"))

	// ── Domain namespaces — wire existing pointers under their group ───────
	c.Trading = &TradingNamespace{
		Market:     c.Market,
		Account:    c.Account,
		Sim:        c.Sim,
		Strategies: c.Strategies,
		Trade:      c.Trade,
		Backtest:   c.Backtest,
		Stream:     c.Stream,
	}
	c.Agents = &AgentsNamespace{
		Pipelines: c.Pipelines,
		Hitl:      c.Hitl,
		Assistant: c.Assistant,
		Phone:     c.Phone,
		Evals:     c.Evals,
	}
	c.Platform = &PlatformNamespace{
		Projects:    c.Projects,
		Credentials: c.Credentials,
		Connectors:  c.Connectors,
		Billing:     c.Billing,
		Team:        c.Team,
		Templates:   c.Templates,
		Overview:    c.Overview,
		Runner:      c.Runner,
		Auth:        c.Auth,
		Accounts:    c.Auth, // alias — same surface, discoverable name
		Bugs:        c.Bugs,
		Events:      c.Events,
	}

	return c, nil
}

// ---------------------------------------------------------------------------
// internal HTTP client
// ---------------------------------------------------------------------------

type httpClient struct {
	// credential is what goes in Authorization: Bearer <credential>.
	// Usually the mk_ API key, but may be a session JWT.
	credential string
	// apiKey is always the mk_ key (for Socket.IO auth params).
	apiKey  string
	baseURL string
	hc      *http.Client
}

func (c *httpClient) buildURL(path string, query map[string]string) string {
	u, _ := url.Parse(c.baseURL + path)
	q := u.Query()
	// SECURITY: the credential is sent ONLY via `Authorization: Bearer` (see
	// do() below). It must NEVER appear in the REST URL query string, where it
	// would leak into access logs and proxies. Public WebSocket streams are
	// the one place a key rides the URL: stream.go injects ?apiKey= on the
	// wss:// URL (server protocol), and private streams use a short-lived
	// ?wsTicket=. The Socket.IO events client (events.go) authenticates via
	// the Authorization header and the Socket.IO auth payload, never the URL.
	for k, v := range query {
		if v != "" {
			q.Set(k, v)
		}
	}
	u.RawQuery = q.Encode()
	return u.String()
}

func (c *httpClient) do(ctx context.Context, method, path string, query map[string]string, body interface{}) ([]byte, int, http.Header, error) {
	var bodyReader io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			return nil, 0, nil, fmt.Errorf("melaya: marshal request body: %w", err)
		}
		bodyReader = bytes.NewReader(b)
	}

	// Per-request timeout: wrap the caller's context with an absolute deadline
	// so a single hung request never blocks the caller indefinitely. The
	// http.Client.Timeout already covers the transport layer; this context
	// deadline is additive and cancels the request body read as well.
	timeout := defaultTimeout
	if c.hc.Timeout > 0 {
		timeout = c.hc.Timeout
	}
	reqCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	req, err := http.NewRequestWithContext(reqCtx, method, c.buildURL(path, query), bodyReader)
	if err != nil {
		return nil, 0, nil, err
	}
	// Never expose the credential in error messages — only set it in the header.
	req.Header.Set("Authorization", "Bearer "+c.credential)
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}

	resp, err := c.hc.Do(req)
	if err != nil {
		return nil, 0, nil, err
	}
	defer resp.Body.Close()
	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, resp.StatusCode, resp.Header, err
	}
	return data, resp.StatusCode, resp.Header, nil
}

// retryAfterDelay parses a Retry-After header value (RFC 7231: either an
// integer number of seconds, or an HTTP-date) into a duration. Returns
// (0, false) when the header is absent or unparseable.
func retryAfterDelay(h http.Header) (time.Duration, bool) {
	if h == nil {
		return 0, false
	}
	v := strings.TrimSpace(h.Get("Retry-After"))
	if v == "" {
		return 0, false
	}
	if secs, err := strconv.Atoi(v); err == nil {
		if secs < 0 {
			secs = 0
		}
		return time.Duration(secs) * time.Second, true
	}
	if t, err := http.ParseTime(v); err == nil {
		d := time.Until(t)
		if d < 0 {
			d = 0
		}
		return d, true
	}
	return 0, false
}

// doWithRetry wraps do with bounded exponential backoff (with jitter) on
// network errors, 429, and 5xx — but ONLY for idempotent GET requests.
// POST, PUT, PATCH, and DELETE are executed exactly once (no retry).
func (c *httpClient) doWithRetry(ctx context.Context, method, path string, query map[string]string, body interface{}) ([]byte, int, error) {
	// Non-idempotent methods are never retried.
	if method != http.MethodGet {
		data, status, _, err := c.do(ctx, method, path, query, body)
		if err != nil {
			return nil, 0, err
		}
		return data, status, nil
	}

	var (
		data   []byte
		status int
		hdr    http.Header
		err    error
	)
	for attempt := 0; attempt <= maxRetries; attempt++ {
		if attempt > 0 {
			// Exponential backoff with ±25 % jitter.
			// On a 429 the server's Retry-After header takes precedence.
			base := time.Duration(math.Pow(2, float64(attempt-1))*500) * time.Millisecond
			jitter := time.Duration(rand.Int63n(int64(base) / 4)) //nolint:gosec
			wait := base + jitter
			if status == 429 {
				if ra, ok := retryAfterDelay(hdr); ok {
					// Honor the server's Retry-After, capped at 60s so a
					// hostile or misconfigured header can't stall the caller.
					if ra > 60*time.Second {
						ra = 60 * time.Second
					}
					wait = ra
				}
			}
			select {
			case <-ctx.Done():
				return nil, 0, ctx.Err()
			case <-time.After(wait):
			}
		}
		data, status, hdr, err = c.do(ctx, method, path, query, body)
		if err != nil {
			// Network-level error — retry up to maxRetries times.
			continue
		}
		if status != 429 && (status < 500 || status >= 600) {
			break
		}
	}
	if err != nil {
		return nil, 0, err
	}
	return data, status, nil
}

// parseEnvelope maps HTTP errors and the Melaya error envelope to MelayaError.
// Two envelope shapes are supported:
//
//	{error: "tier_insufficient", tier: "bastion"}  (403)
//	{error: "...", message: "...", code: "..."}     (4xx/5xx)
func parseEnvelope(data []byte, status int) ([]byte, error) {
	if status >= 400 {
		var env map[string]interface{}
		code := ""
		if json.Unmarshal(data, &env) == nil {
			if c, ok := env["error"].(string); ok {
				code = c
			}
		}
		msg := "Melaya API " + strconv.Itoa(status)
		if code != "" {
			msg += " (" + code + ")"
		}
		return nil, &MelayaError{Message: msg, Status: status, Code: code, Body: env}
	}

	// Check for envelope-level ok==false (non-fatal HTTP but logical failure).
	var env map[string]interface{}
	if json.Unmarshal(data, &env) == nil {
		if ok, exists := env["ok"]; exists {
			if b, isBool := ok.(bool); isBool && !b {
				code := ""
				if c, ok2 := env["error"].(string); ok2 {
					code = c
				}
				msg := "Melaya API request failed"
				if code != "" {
					msg += ": " + code
				}
				return nil, &MelayaError{Message: msg, Status: status, Code: code, Body: env}
			}
		}
	}
	return data, nil
}

func (c *httpClient) get(ctx context.Context, path string, query map[string]string) ([]byte, error) {
	data, status, err := c.doWithRetry(ctx, http.MethodGet, path, query, nil)
	if err != nil {
		return nil, err
	}
	return parseEnvelope(data, status)
}

func (c *httpClient) post(ctx context.Context, path string, body interface{}) ([]byte, error) {
	data, status, err := c.doWithRetry(ctx, http.MethodPost, path, nil, body)
	if err != nil {
		return nil, err
	}
	return parseEnvelope(data, status)
}

func (c *httpClient) put(ctx context.Context, path string, body interface{}) ([]byte, error) {
	data, status, err := c.doWithRetry(ctx, http.MethodPut, path, nil, body)
	if err != nil {
		return nil, err
	}
	return parseEnvelope(data, status)
}

func (c *httpClient) patch(ctx context.Context, path string, body interface{}) ([]byte, error) {
	data, status, err := c.doWithRetry(ctx, http.MethodPatch, path, nil, body)
	if err != nil {
		return nil, err
	}
	return parseEnvelope(data, status)
}

func (c *httpClient) del(ctx context.Context, path string, query map[string]string) ([]byte, error) {
	data, status, err := c.doWithRetry(ctx, http.MethodDelete, path, query, nil)
	if err != nil {
		return nil, err
	}
	return parseEnvelope(data, status)
}

// unmarshal decodes JSON bytes into v.
func unmarshal(data []byte, v interface{}) error {
	return json.Unmarshal(data, v)
}

// okResult is a common single-field response.
type okResult struct {
	Ok bool `json:"ok"`
}
