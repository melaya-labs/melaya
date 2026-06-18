// Package melaya is the official Go SDK for the Melaya unified market-data
// and trading API. It covers REST (market, account, sim, strategies, backtest)
// and WebSocket streaming (public and private feeds).
//
// Usage:
//
//	m, err := melaya.New("mk_...")
//	if err != nil { log.Fatal(err) }
//	t, err := m.Market.Ticker(ctx, melaya.SymbolQuery{Exchange: "binance", Symbol: "BTC/USDT", Market: "spot"})
package melaya

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
)

const (
	DefaultBaseURL = "https://api.melaya.org"
	DefaultWsURL   = "wss://wss.melaya.org"
)

// Options configures the Melaya client.
type Options struct {
	// APIKey is your mk_-prefixed Melaya API key. Required.
	APIKey string
	// BaseURL overrides the REST base URL (default: https://api.melaya.org).
	BaseURL string
	// WsURL overrides the WebSocket base URL (default: wss://wss.melaya.org).
	WsURL string
	// HTTPClient overrides the HTTP client used for REST calls.
	// If nil, a default client is built (with TLS skip enabled when
	// MELAYA_INSECURE_TLS=1).
	HTTPClient *http.Client
}

// MelayaError is returned for non-2xx HTTP responses or envelope ok==false.
type MelayaError struct {
	Message string
	Status  int
	Code    string
	Body    interface{}
}

func (e *MelayaError) Error() string { return e.Message }

// Client is the Melaya SDK entry point.
type Client struct {
	http     *httpClient
	// Market provides access to normalized market-data endpoints.
	Market   *MarketAPI
	// Account provides authenticated account reads.
	Account  *AccountAPI
	// Sim provides the paper-trading (sim broker) API.
	Sim      *SimAPI
	// Strategies provides launch/control/inspection of trading strategies.
	Strategies *StrategiesAPI
	// Backtest provides historical backtesting on the Rust engine.
	Backtest *BacktestAPI
	// Trade provides live credentialed trading on a connected exchange (real funds).
	Trade    *TradeAPI
	// Stream provides WebSocket streaming (public + private).
	Stream   *StreamAPI
}

// New creates a new Melaya client. The apiKey must be prefixed with "mk_".
func New(apiKey string, opts ...Options) (*Client, error) {
	var o Options
	if len(opts) > 0 {
		o = opts[0]
	}
	if o.APIKey == "" {
		o.APIKey = apiKey
	}
	if o.APIKey == "" {
		return nil, fmt.Errorf("melaya: apiKey is required (create one at melaya.org → Settings → API Keys)")
	}
	if !strings.HasPrefix(o.APIKey, "mk_") {
		return nil, fmt.Errorf("melaya: API keys must be prefixed mk_")
	}
	if o.BaseURL == "" {
		o.BaseURL = DefaultBaseURL
	}
	if o.WsURL == "" {
		o.WsURL = DefaultWsURL
	}

	hc := o.HTTPClient
	if hc == nil {
		transport := http.DefaultTransport
		if os.Getenv("MELAYA_INSECURE_TLS") == "1" {
			transport = &http.Transport{
				TLSClientConfig: &tls.Config{InsecureSkipVerify: true}, //nolint:gosec
			}
		}
		hc = &http.Client{Transport: transport}
	}

	h := &httpClient{apiKey: o.APIKey, baseURL: strings.TrimRight(o.BaseURL, "/"), hc: hc}

	c := &Client{http: h}
	c.Market = &MarketAPI{h: h}
	c.Trade = &TradeAPI{h: h}
	c.Account = &AccountAPI{h: h}
	c.Sim = &SimAPI{h: h}
	c.Strategies = &StrategiesAPI{h: h}
	c.Backtest = &BacktestAPI{h: h}
	c.Stream = &StreamAPI{
		apiKey: o.APIKey,
		wsURL:  strings.TrimRight(o.WsURL, "/"),
		h:      h,
		insecureTLS: os.Getenv("MELAYA_INSECURE_TLS") == "1",
	}
	return c, nil
}

// ---------------------------------------------------------------------------
// internal HTTP client
// ---------------------------------------------------------------------------

type httpClient struct {
	apiKey  string
	baseURL string
	hc      *http.Client
}

func (c *httpClient) buildURL(path string, query map[string]string) string {
	u, _ := url.Parse(c.baseURL + path)
	q := u.Query()
	q.Set("apiKey", c.apiKey)
	for k, v := range query {
		if v != "" {
			q.Set(k, v)
		}
	}
	u.RawQuery = q.Encode()
	return u.String()
}

func (c *httpClient) do(ctx context.Context, method, path string, query map[string]string, body interface{}) ([]byte, int, error) {
	var bodyReader io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			return nil, 0, fmt.Errorf("melaya: marshal request body: %w", err)
		}
		bodyReader = bytes.NewReader(b)
	}

	req, err := http.NewRequestWithContext(ctx, method, c.buildURL(path, query), bodyReader)
	if err != nil {
		return nil, 0, err
	}
	req.Header.Set("Authorization", "Bearer "+c.apiKey)
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}

	resp, err := c.hc.Do(req)
	if err != nil {
		return nil, 0, err
	}
	defer resp.Body.Close()
	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, resp.StatusCode, err
	}
	return data, resp.StatusCode, nil
}

// parseEnvelope parses the response bytes. It returns a MelayaError for
// HTTP >=400 or envelope ok==false, otherwise returns the raw bytes.
func parseEnvelope(data []byte, status int) ([]byte, error) {
	if status >= 400 {
		var env map[string]interface{}
		code := ""
		if json.Unmarshal(data, &env) == nil {
			if c, ok := env["error"].(string); ok {
				code = c
			}
		}
		msg := fmt.Sprintf("Melaya API %d", status)
		if code != "" {
			msg += " (" + code + ")"
		}
		return nil, &MelayaError{Message: msg, Status: status, Code: code, Body: env}
	}

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

// get performs a GET request, returns parsed bytes.
func (c *httpClient) get(ctx context.Context, path string, query map[string]string) ([]byte, error) {
	data, status, err := c.do(ctx, http.MethodGet, path, query, nil)
	if err != nil {
		return nil, err
	}
	return parseEnvelope(data, status)
}

// post performs a POST request, returns parsed bytes.
func (c *httpClient) post(ctx context.Context, path string, body interface{}) ([]byte, error) {
	data, status, err := c.do(ctx, http.MethodPost, path, nil, body)
	if err != nil {
		return nil, err
	}
	return parseEnvelope(data, status)
}

// del performs a DELETE request, returns parsed bytes.
func (c *httpClient) del(ctx context.Context, path string, query map[string]string) ([]byte, error) {
	data, status, err := c.do(ctx, http.MethodDelete, path, query, nil)
	if err != nil {
		return nil, err
	}
	return parseEnvelope(data, status)
}

// unmarshal is a helper to decode JSON bytes into v.
func unmarshal(data []byte, v interface{}) error {
	return json.Unmarshal(data, v)
}
