// EventsClient — real-time Socket.IO v4 client for /api/v1/events.
//
// Implements a minimal Engine.IO v4 / Socket.IO v4 client that:
//   - Completes the EIO handshake via HTTP long-polling (mandatory first step)
//   - Upgrades to WebSocket (via gorilla/websocket) when the upgrade succeeds
//   - Falls back to long-polling when WebSocket is unavailable
//   - Reconnects automatically with back-off on disconnect
//   - Dispatches typed event callbacks for run, project, and HITL rooms
//
// The credential is passed via Authorization headers and Socket.IO auth data.
//
// Example:
//
//	unsub := m.Events.OnRunUpdate("run-abc", func(e melaya.RunPushEvent) {
//	    fmt.Println(e.EventType, e.Status)
//	})
//	defer unsub()
//	defer m.Events.Close()
package melaya

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

// Engine.IO v4 packet type prefixes.
const (
	eioOpen    = '0'
	eioPing    = '2'
	eioPong    = '3'
	eioMessage = '4' // followed by a Socket.IO packet
)

// Socket.IO v4 packet types (first char after EIO prefix).
const (
	sioConnect    = '0'
	sioDisconnect = '1'
	sioEvent      = '2'
)

type anyListener = func(payload interface{})

// listenerEntry locates one registered listener (for room-scoped removal).
type listenerEntry struct {
	event string
	idx   int
}

// EventsClient is a Socket.IO v4 client for the Melaya platform events namespace.
// It is safe for concurrent use.
type EventsClient struct {
	h       *httpClient
	baseURL string

	mu          sync.Mutex
	sid         string
	ws          *websocket.Conn
	closed      bool
	joinedRooms map[string]struct{}
	listeners   map[string][]func(interface{})
	// roomListeners tracks which listeners were registered for which room so
	// LeaveRun/LeaveProject can remove them along with the room itself.
	roomListeners map[string][]listenerEntry

	// startOnce guards lazy start of the reconnect loop: the background
	// goroutine (and the network connection it opens) is only spun up on the
	// first subscription, not at client construction. A Melaya client that
	// never touches Events therefore never opens an events socket.
	startOnce sync.Once
}

func newEventsClient(h *httpClient, baseURL string) *EventsClient {
	// Deliberately does NOT start the reconnect loop here — see ensureStarted.
	return &EventsClient{
		h:             h,
		baseURL:       baseURL,
		joinedRooms:   make(map[string]struct{}),
		listeners:     make(map[string][]func(interface{})),
		roomListeners: make(map[string][]listenerEntry),
	}
}

// ensureStarted lazily launches the reconnect loop on first use. Safe to call
// from every subscribe path; only the first call spawns the goroutine.
func (c *EventsClient) ensureStarted() {
	c.mu.Lock()
	closed := c.closed
	c.mu.Unlock()
	if closed {
		return
	}
	c.startOnce.Do(func() { go c.loop() })
}

// ── Public subscription API ───────────────────────────────────────────────────

// OnRunUpdate subscribes to run events for a specific pipeline run.
// Automatically joins the run:<runId> Socket.IO room. Events carrying a
// different runId are filtered out, so subscribing to several runs at once
// never cross-delivers.
// Returns an unsubscribe function.
func (c *EventsClient) OnRunUpdate(runID string, cb func(RunPushEvent)) func() {
	room := "run:" + runID
	c.joinRoom(room)
	return c.onRoom(room, "pushEvent", func(p interface{}) {
		if e, ok := tryDecode[RunPushEvent](p); ok {
			if e.RunID != "" && e.RunID != runID {
				return
			}
			cb(e)
		}
	})
}

// OnInitPhase subscribes to init-phase progress events for a run. Events
// carrying a different runId are filtered out.
// Returns an unsubscribe function.
func (c *EventsClient) OnInitPhase(runID string, cb func(RunInitPhaseEvent)) func() {
	room := "run:" + runID
	c.joinRoom(room)
	return c.onRoom(room, "pushInitPhase", func(p interface{}) {
		if e, ok := tryDecode[RunInitPhaseEvent](p); ok {
			if e.RunID != "" && e.RunID != runID {
				return
			}
			cb(e)
		}
	})
}

// OnProjectEvent subscribes to all events in a project room. Events carrying
// a different project are filtered out.
// Returns an unsubscribe function.
func (c *EventsClient) OnProjectEvent(project string, cb func(RunPushEvent)) func() {
	room := "project:" + project
	c.joinRoom(room)
	return c.onRoom(room, "pushEvent", func(p interface{}) {
		if e, ok := tryDecode[RunPushEvent](p); ok {
			if e.Project != "" && e.Project != project {
				return
			}
			cb(e)
		}
	})
}

// OnHitlApproval subscribes to HITL approval invalidation events.
// The server automatically joins the authenticated socket to the user's HITL room.
// Returns an unsubscribe function.
func (c *EventsClient) OnHitlApproval(cb func(HitlApprovalEvent)) func() {
	return c.on("pushHitlApprovals", func(p interface{}) {
		if e, ok := tryDecode[HitlApprovalEvent](p); ok {
			cb(e)
		}
	})
}

// OnPipelineCreated subscribes to pipeline creation events in a project room.
// Events carrying a different project are filtered out.
func (c *EventsClient) OnPipelineCreated(project string, cb func(PipelineCrudEvent)) func() {
	return c.onPipelineCrud(project, "pipelineCreated", cb)
}

// OnPipelineUpdated subscribes to pipeline update events in a project room.
// Events carrying a different project are filtered out.
func (c *EventsClient) OnPipelineUpdated(project string, cb func(PipelineCrudEvent)) func() {
	return c.onPipelineCrud(project, "pipelineUpdated", cb)
}

// OnPipelineDeleted subscribes to pipeline deletion events in a project room.
// Events carrying a different project are filtered out.
func (c *EventsClient) OnPipelineDeleted(project string, cb func(PipelineCrudEvent)) func() {
	return c.onPipelineCrud(project, "pipelineDeleted", cb)
}

// onPipelineCrud is the shared project-room-scoped pipeline CRUD subscription.
func (c *EventsClient) onPipelineCrud(project, event string, cb func(PipelineCrudEvent)) func() {
	room := "project:" + project
	c.joinRoom(room)
	return c.onRoom(room, event, func(p interface{}) {
		if e, ok := tryDecode[PipelineCrudEvent](p); ok {
			if e.Project != "" && e.Project != project {
				return
			}
			cb(e)
		}
	})
}

// LeaveRun leaves the run:<runId> room, removes the listeners registered for
// it, and drops it from the rejoin-on-reconnect list.
func (c *EventsClient) LeaveRun(runID string) { c.leaveRoom("run:" + runID) }

// LeaveProject leaves the project:<project> room, removes the listeners
// registered for it, and drops it from the rejoin-on-reconnect list.
func (c *EventsClient) LeaveProject(project string) { c.leaveRoom("project:" + project) }

// Close disconnects and releases all listeners. After Close the client cannot reconnect.
func (c *EventsClient) Close() {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.closed = true
	if c.ws != nil {
		_ = c.ws.Close()
		c.ws = nil
	}
	c.listeners = make(map[string][]func(interface{}))
	c.joinedRooms = make(map[string]struct{})
	c.roomListeners = make(map[string][]listenerEntry)
}

// ── Internal helpers ──────────────────────────────────────────────────────────

func (c *EventsClient) on(event string, cb func(interface{})) func() {
	return c.onRoom("", event, cb)
}

// onRoom registers a listener, optionally bound to a room so that leaving the
// room (LeaveRun/LeaveProject) also removes the listener.
func (c *EventsClient) onRoom(room, event string, cb func(interface{})) func() {
	c.ensureStarted()
	c.mu.Lock()
	defer c.mu.Unlock()
	c.listeners[event] = append(c.listeners[event], cb)
	idx := len(c.listeners[event]) - 1
	if room != "" {
		c.roomListeners[room] = append(c.roomListeners[room], listenerEntry{event: event, idx: idx})
	}
	return func() {
		c.mu.Lock()
		defer c.mu.Unlock()
		ls := c.listeners[event]
		if idx < len(ls) {
			ls[idx] = nil
		}
	}
}

func (c *EventsClient) emit(event string, payload interface{}) {
	c.mu.Lock()
	ls := c.listeners[event]
	c.mu.Unlock()
	for _, cb := range ls {
		if cb != nil {
			func() {
				defer func() { recover() }() //nolint:errcheck
				cb(payload)
			}()
		}
	}
}

func (c *EventsClient) joinRoom(room string) {
	c.ensureStarted()
	c.mu.Lock()
	_, already := c.joinedRooms[room]
	c.joinedRooms[room] = struct{}{}
	sid := c.sid
	c.mu.Unlock()
	if !already && sid != "" {
		c.emitJoin(room)
	}
}

func (c *EventsClient) leaveRoom(room string) {
	c.mu.Lock()
	// Drop the room from the rejoin-on-reconnect list and remove every
	// listener that was registered for it.
	delete(c.joinedRooms, room)
	for _, le := range c.roomListeners[room] {
		if ls := c.listeners[le.event]; le.idx < len(ls) {
			ls[le.idx] = nil
		}
	}
	delete(c.roomListeners, room)
	sid := c.sid
	c.mu.Unlock()
	if sid != "" {
		// leaveRoom always sends the FULL room string (e.g. "run:<id>", "project:<name>").
		c.sioEmit("leaveRoom", room)
	}
}

// emitJoin maps the logical room string to the server's actual Socket.IO event:
//   - "run:<id>"        → emit joinRunRoom("<id>")
//   - "project:<name>"  → emit joinProjectRoom("<name>")
//   - anything else     → emit joinRunRoom(room) as a safe fallback
func (c *EventsClient) emitJoin(room string) {
	switch {
	case strings.HasPrefix(room, "run:"):
		c.sioEmit("joinRunRoom", room[4:])
	case strings.HasPrefix(room, "project:"):
		c.sioEmit("joinProjectRoom", room[8:])
	default:
		c.sioEmit("joinRunRoom", room)
	}
}

func (c *EventsClient) sioEmit(event string, args ...interface{}) {
	all := append([]interface{}{event}, args...)
	b, err := json.Marshal(all)
	if err != nil {
		return
	}
	c.sendPacket(string([]byte{eioMessage, sioEvent}) + string(b))
}

func (c *EventsClient) replayJoins() {
	c.mu.Lock()
	rooms := make([]string, 0, len(c.joinedRooms))
	for r := range c.joinedRooms {
		rooms = append(rooms, r)
	}
	c.mu.Unlock()
	for _, r := range rooms {
		c.emitJoin(r)
	}
}

// handlePacket decodes a raw Engine.IO packet string and dispatches events.
func (c *EventsClient) handlePacket(raw string) {
	if len(raw) == 0 {
		return
	}
	switch raw[0] {
	case eioPing:
		// Answer the server ping promptly on whatever transport is active.
		c.sendPacket(string([]byte{eioPong}))
	case eioMessage:
		if len(raw) < 2 {
			return
		}
		switch raw[1] {
		case sioEvent:
			// Socket.IO event: 42[eventName, payload]
			var args []interface{}
			if err := json.Unmarshal([]byte(raw[2:]), &args); err != nil {
				return
			}
			if len(args) < 2 {
				return
			}
			eventName, _ := args[0].(string)
			c.emit(eventName, args[1])
		}
	}
}

// sendPacket writes an Engine.IO packet on whichever transport is active:
// the WebSocket when upgraded, otherwise a POST to the long-polling endpoint
// (so joins and pongs are never silently dropped on the polling fallback).
func (c *EventsClient) sendPacket(s string) {
	c.mu.Lock()
	ws := c.ws
	sid := c.sid
	c.mu.Unlock()
	if ws != nil {
		_ = ws.WriteMessage(websocket.TextMessage, []byte(s))
		return
	}
	if sid == "" {
		return
	}
	c.postPacket(s)
}

// postPacket POSTs an outbound packet to the Engine.IO polling URL (with sid).
func (c *EventsClient) postPacket(s string) {
	hcToUse := c.h.hc
	if hcToUse == nil {
		hcToUse = http.DefaultClient
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.pollURL(), strings.NewReader(s))
	if err != nil {
		return
	}
	req.Header.Set("Authorization", "Bearer "+c.h.credential)
	req.Header.Set("Content-Type", "text/plain;charset=UTF-8")
	resp, err := hcToUse.Do(req)
	if err != nil {
		return
	}
	_, _ = io.Copy(io.Discard, resp.Body)
	resp.Body.Close()
}

// ── Connection lifecycle ──────────────────────────────────────────────────────

// pollURL builds the Engine.IO polling URL (sid appended once handshaken).
// The credential goes in the Authorization header, never in this URL.
func (c *EventsClient) pollURL() string {
	c.mu.Lock()
	sid := c.sid
	c.mu.Unlock()
	base := strings.TrimRight(c.baseURL, "/")
	u, _ := url.Parse(base + "/api/v1/events/")
	q := u.Query()
	q.Set("EIO", "4")
	q.Set("transport", "polling")
	if sid != "" {
		q.Set("sid", sid)
	}
	u.RawQuery = q.Encode()
	return u.String()
}

func (c *EventsClient) wsURL() string {
	base := strings.TrimRight(c.baseURL, "/")
	u, _ := url.Parse(base + "/api/v1/events/")
	if u.Scheme == "https" {
		u.Scheme = "wss"
	} else {
		u.Scheme = "ws"
	}
	q := u.Query()
	q.Set("EIO", "4")
	q.Set("transport", "websocket")
	if c.sid != "" {
		q.Set("sid", c.sid)
	}
	u.RawQuery = q.Encode()
	return u.String()
}

// loop is the reconnect loop. Runs in its own goroutine.
// Reconnect backoff is bounded: starts at 1s, doubles each failure, caps at
// 30s, and resets after a healthy session (one that stayed connected for a
// while) so the next reconnect is fast again.
func (c *EventsClient) loop() {
	const (
		maxBackoff = 30 * time.Second
		// healthyAfter: a session that stayed up this long counts as a
		// successful connection for backoff purposes.
		healthyAfter = 30 * time.Second
	)
	backoff := 1 * time.Second
	for {
		c.mu.Lock()
		if c.closed {
			c.mu.Unlock()
			return
		}
		c.mu.Unlock()

		start := time.Now()
		err := c.connect()
		if err == nil || time.Since(start) > healthyAfter {
			backoff = 1 * time.Second
		}

		c.mu.Lock()
		closed := c.closed
		c.mu.Unlock()
		if closed {
			return
		}

		time.Sleep(backoff)
		backoff *= 2
		if backoff > maxBackoff {
			backoff = maxBackoff
		}
	}
}

// connect performs the Engine.IO handshake, then upgrades to WebSocket.
func (c *EventsClient) connect() error {
	c.mu.Lock()
	if c.closed {
		c.mu.Unlock()
		return fmt.Errorf("closed")
	}
	// A fresh handshake must never carry a stale sid from a dead session.
	c.sid = ""
	c.mu.Unlock()

	// Step 1: HTTP GET polling handshake
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	hcToUse := c.h.hc
	if hcToUse == nil {
		hcToUse = http.DefaultClient
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.pollURL(), nil)
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+c.h.credential)

	resp, err := hcToUse.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return err
	}

	// Parse Engine.IO open packet: "0{...json...}" (may be length-prefixed)
	text := string(body)
	jsonStart := strings.Index(text, "{")
	if jsonStart == -1 {
		return fmt.Errorf("melaya/events: unexpected EIO handshake: %q", text[:min(len(text), 80)])
	}
	var openData struct {
		SID          string `json:"sid"`
		PingInterval int    `json:"pingInterval"`
		PingTimeout  int    `json:"pingTimeout"`
	}
	if err := json.Unmarshal([]byte(text[jsonStart:]), &openData); err != nil {
		return err
	}
	c.mu.Lock()
	c.sid = openData.SID
	c.mu.Unlock()

	// Step 2: POST Socket.IO connect packet with auth
	sioConnectPacket := string([]byte{eioMessage, sioConnect}) + `{"token":"` + c.h.apiKey + `"}`
	postReq, err := http.NewRequestWithContext(ctx, http.MethodPost, c.pollURL(),
		strings.NewReader(sioConnectPacket))
	if err != nil {
		return err
	}
	postReq.Header.Set("Authorization", "Bearer "+c.h.credential)
	postReq.Header.Set("Content-Type", "text/plain;charset=UTF-8")
	postResp, err := hcToUse.Do(postReq)
	if err != nil {
		return err
	}
	postResp.Body.Close()

	// Step 3: Upgrade to WebSocket
	dialer := websocket.Dialer{
		HandshakeTimeout: 10 * time.Second,
	}
	if c.h.hc != nil {
		if transport, ok := c.h.hc.Transport.(*http.Transport); ok && transport.TLSClientConfig != nil {
			dialer.TLSClientConfig = transport.TLSClientConfig
		}
	}
	if dialer.TLSClientConfig == nil {
		dialer.TLSClientConfig = &tls.Config{} //nolint:gosec
	}

	wsConn, _, err := dialer.Dial(c.wsURL(), http.Header{
		"Authorization": []string{"Bearer " + c.h.credential},
	})
	if err != nil {
		// WebSocket upgrade failed — stay on long-polling
		return c.runPolling(openData.PingInterval, openData.PingTimeout)
	}

	// Send Engine.IO upgrade probe
	_ = wsConn.WriteMessage(websocket.TextMessage, []byte("2probe"))
	_ = wsConn.WriteMessage(websocket.TextMessage, []byte("5"))

	c.mu.Lock()
	c.ws = wsConn
	c.mu.Unlock()

	c.replayJoins()

	// Read loop
	for {
		_, msg, err := wsConn.ReadMessage()
		if err != nil {
			c.mu.Lock()
			c.ws = nil
			c.sid = ""
			c.mu.Unlock()
			return err
		}
		c.handlePacket(string(msg))
	}
}

// runPolling implements the long-poll fallback (used when WebSocket upgrade
// fails). The GET blocks server-side until packets are available (bounded by
// pingInterval+pingTimeout), so the loop polls back-to-back with no
// client-side sleep between polls. Any HTTP error status — notably 400
// "Session ID unknown" — means the session is dead: the sid is cleared and an
// error is returned so the reconnect loop performs a full re-handshake.
func (c *EventsClient) runPolling(pingIntervalMs, pingTimeoutMs int) error {
	if pingIntervalMs <= 0 {
		pingIntervalMs = 25000
	}
	if pingTimeoutMs <= 0 {
		pingTimeoutMs = 20000
	}
	maxWait := time.Duration(pingIntervalMs+pingTimeoutMs)*time.Millisecond + 5*time.Second
	c.replayJoins()
	hcToUse := c.h.hc
	if hcToUse == nil {
		hcToUse = http.DefaultClient
	}
	for {
		c.mu.Lock()
		if c.closed {
			c.mu.Unlock()
			return fmt.Errorf("closed")
		}
		c.mu.Unlock()

		ctx, cancel := context.WithTimeout(context.Background(), maxWait)
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.pollURL(), nil)
		if err != nil {
			cancel()
			c.clearSession()
			return err
		}
		req.Header.Set("Authorization", "Bearer "+c.h.credential)
		resp, err := hcToUse.Do(req)
		if err != nil {
			cancel()
			c.clearSession()
			return err
		}
		body, _ := io.ReadAll(resp.Body)
		resp.Body.Close()
		cancel()

		if resp.StatusCode >= 400 {
			// 400 = "Session ID unknown" → dead session; re-handshake.
			c.clearSession()
			return fmt.Errorf("melaya/events: poll HTTP %d — session expired", resp.StatusCode)
		}
		c.parsePollResponse(string(body))
	}
}

// clearSession drops the Engine.IO sid so the next connect re-handshakes.
func (c *EventsClient) clearSession() {
	c.mu.Lock()
	c.sid = ""
	c.mu.Unlock()
}

// parsePollResponse splits a batched Engine.IO v4 poll body into packets.
// Packets are separated by the 0x1e record-separator byte.
func (c *EventsClient) parsePollResponse(text string) {
	for _, packet := range strings.Split(text, "\x1e") {
		if packet != "" {
			c.handlePacket(packet)
		}
	}
}

// ── Utility ───────────────────────────────────────────────────────────────────

// tryDecode round-trips v through JSON to produce a T.
func tryDecode[T any](v interface{}) (T, bool) {
	var zero T
	b, err := json.Marshal(v)
	if err != nil {
		return zero, false
	}
	var result T
	if err := json.Unmarshal(b, &result); err != nil {
		return zero, false
	}
	return result, true
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
