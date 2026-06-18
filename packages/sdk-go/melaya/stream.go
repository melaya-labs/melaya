// Stream API — WebSocket streaming (public market data + private feeds).
package melaya

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"sync"

	"github.com/gorilla/websocket"
)

// Frame is a generic JSON frame from a WebSocket stream.
type Frame map[string]interface{}

// Stream is a live channel of JSON frames from a Melaya WebSocket.
// Read from the Ch channel; close with Close().
type Stream struct {
	Ch     <-chan Frame
	Errors <-chan error
	close  func()
}

// Close shuts down the underlying WebSocket connection.
func (s *Stream) Close() {
	if s.close != nil {
		s.close()
	}
}

// StreamAPI manages WebSocket connections to Melaya.
type StreamAPI struct {
	apiKey      string
	wsURL       string
	h           *httpClient
	insecureTLS bool
}

func (s *StreamAPI) dialer() *websocket.Dialer {
	d := *websocket.DefaultDialer
	if s.insecureTLS {
		d.TLSClientConfig = &tls.Config{InsecureSkipVerify: true} //nolint:gosec
	}
	return &d
}

func (s *StreamAPI) open(path string, params map[string]string) (*Stream, error) {
	u, err := url.Parse(s.wsURL + path)
	if err != nil {
		return nil, err
	}
	q := u.Query()
	q.Set("apiKey", s.apiKey)
	for k, v := range params {
		if v != "" {
			q.Set(k, v)
		}
	}
	u.RawQuery = q.Encode()

	conn, _, err := s.dialer().Dial(u.String(), http.Header{"Authorization": []string{"Bearer " + s.apiKey}})
	if err != nil {
		return nil, fmt.Errorf("melaya stream: dial %s: %w", path, err)
	}
	return newStream(conn), nil
}

func (s *StreamAPI) openWithTicket(path, stream string, extra map[string]interface{}) (*Stream, error) {
	body := map[string]interface{}{"stream": stream}
	for k, v := range extra {
		if v == nil {
			continue
		}
		if str, ok := v.(string); ok && str == "" {
			continue
		}
		body[k] = v
	}
	ctx := context.Background()
	data, err := s.h.post(ctx, "/api/v1/private/private-ticket", body)
	if err != nil {
		return nil, fmt.Errorf("melaya stream: mint ticket for %s: %w", stream, err)
	}
	var env struct {
		WsTicket string `json:"wsTicket"`
	}
	if err := json.Unmarshal(data, &env); err != nil || env.WsTicket == "" {
		return nil, fmt.Errorf("melaya stream: no wsTicket in response")
	}

	u, err := url.Parse(s.wsURL + path)
	if err != nil {
		return nil, err
	}
	q := u.Query()
	q.Set("wsTicket", env.WsTicket)
	u.RawQuery = q.Encode()

	conn, _, err := s.dialer().Dial(u.String(), nil)
	if err != nil {
		return nil, fmt.Errorf("melaya stream: dial %s: %w", path, err)
	}
	return newStream(conn), nil
}

func newStream(conn *websocket.Conn) *Stream {
	ch := make(chan Frame, 64)
	errCh := make(chan error, 1)
	done := make(chan struct{})

	go func() {
		defer close(ch)
		defer close(errCh)
		for {
			select {
			case <-done:
				return
			default:
			}
			_, msg, err := conn.ReadMessage()
			if err != nil {
				select {
				case errCh <- err:
				default:
				}
				return
			}
			var f Frame
			if err := json.Unmarshal(msg, &f); err != nil {
				continue // ignore non-JSON keep-alive frames
			}
			select {
			case ch <- f:
			case <-done:
				return
			}
		}
	}()

	var once sync.Once
	return &Stream{
		Ch:     ch,
		Errors: errCh,
		close: func() {
			once.Do(func() {
				close(done)
				conn.Close()
			})
		},
	}
}

// ── Public streams ─────────────────────────────────────────────────────────────

// Ticker opens a live ticker stream for one symbol.
func (s *StreamAPI) Ticker(exchange, symbol, market string) (*Stream, error) {
	return s.open("/ws/ticker", map[string]string{
		"exchange": exchange,
		"symbol":   symbol,
		"market":   market,
	})
}

// Orderbook opens a live order-book stream.
func (s *StreamAPI) Orderbook(exchange, symbol, market string, limit int) (*Stream, error) {
	params := map[string]string{"exchange": exchange, "symbol": symbol, "market": market}
	if limit > 0 {
		params["limit"] = fmt.Sprintf("%d", limit)
	}
	return s.open("/ws/orderbook", params)
}

// Ohlcv opens a live OHLCV candle stream.
func (s *StreamAPI) Ohlcv(exchange, symbol, timeframe, market string) (*Stream, error) {
	return s.open("/ws/ohlcv", map[string]string{
		"exchange":  exchange,
		"symbol":    symbol,
		"timeframe": timeframe,
		"market":    market,
	})
}

// Trades opens a live public-trades stream.
func (s *StreamAPI) Trades(exchange, symbol, market string) (*Stream, error) {
	return s.open("/ws/public-trades", map[string]string{
		"exchange": exchange,
		"symbol":   symbol,
		"market":   market,
	})
}

// Liquidations opens the cross-exchange liquidation firehose.
func (s *StreamAPI) Liquidations(exchange string) (*Stream, error) {
	return s.open("/ws/liquidations", map[string]string{"exchange": exchange})
}

// ── Private streams (ticket-minted) ──────────────────────────────────────────

// Strategies opens the live strategy-events stream for your account.
func (s *StreamAPI) Strategies() (*Stream, error) {
	return s.openWithTicket("/ws/strategies", "strategies", nil)
}

// Private opens the live private account feed for one connected exchange key.
func (s *StreamAPI) Private(exchange, market, apiKeyID, keyID, symbol string) (*Stream, error) {
	extra := map[string]interface{}{
		"exchange": exchange,
		"market":   market,
		"apiKeyId": apiKeyID,
		"keyId":    keyID,
		"symbol":   symbol,
	}
	return s.openWithTicket("/ws/private", "private", extra)
}
