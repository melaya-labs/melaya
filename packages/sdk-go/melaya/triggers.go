// Triggers API: read, diagnose, and dry-run event triggers.
//
// Maps to /api/v1/private/triggers/*. An event trigger starts work on a
// pipeline when something happens elsewhere: a signed webhook, a WebSocket
// or SSE stream, an exchange event, a poll of a connected app, or a push
// from a connected app. This module is for inspecting triggers and finding
// out why one did or did not fire: list and read triggers, read delivery
// receipts, verdict stats and the live event log, list the approvals a
// trigger is waiting on, and run dry tests.
//
// Creating, editing, deleting and rotating the secret of a trigger are done
// in the Melaya app; they are not on the REST surface. Secrets are never
// returned by any method here.
//
// Test and PollTest are dry runs: nothing is published and the action never
// executes. PollNow queues a real poll.
//
// Example:
//
//	triggers, _ := m.Triggers.List(ctx, &melaya.TriggerListOptions{Project: "acme"})
//	for _, t := range triggers {
//	    fmt.Println(t.Name, t.Kind, t.Enabled)
//	}
//
//	// Why did nothing run? Read the newest events, including those that
//	// wrote no receipt (filtered, shed, ingress rejections).
//	ev, _ := m.Triggers.Events(ctx, &melaya.TriggerEventsOptions{TriggerID: id, Limit: 20})
//	for _, e := range ev.Events {
//	    detail := ""
//	    if e.Detail != nil {
//	        detail = *e.Detail
//	    }
//	    fmt.Println(e.Verdict, detail)
//	}
package melaya

import (
	"context"
	"encoding/json"
	"net/http"
	"net/url"
	"strconv"
	"strings"
)

// triggersBase is the base path for the event triggers REST surface.
const triggersBase = "/api/v1/private/triggers"

// TriggerVerdicts lists every delivery verdict the server reports, in
// pipeline order. Use these values in TriggerEventsOptions.Verdicts.
var TriggerVerdicts = []string{
	"accepted", "duplicate", "rejected", "rate_limited", "filtered",
	"decided", "dispatched", "skipped", "failed",
}

// TriggersAPI wraps the event trigger read, diagnose, and dry-run endpoints.
// See the package doc comment above for what is and is not covered.
type TriggersAPI struct {
	h *httpClient
}

// TriggerRecord is one event trigger. Secrets are never included.
type TriggerRecord struct {
	ID       string `json:"id"`
	PublicID string `json:"publicId"`
	Name     string `json:"name"`
	// Kind is "webhook", "wss" (a WebSocket or SSE stream), "engine", "poll"
	// or "push".
	Kind         string `json:"kind"`
	Project      string `json:"project"`
	PipelineName string `json:"pipelineName"`
	Enabled      bool   `json:"enabled"`
	// PausedReason is set when the platform paused the trigger (for example
	// "project_access_lost"), nil otherwise.
	PausedReason *string `json:"pausedReason"`
	// SigningScheme is "melaya", "stripe", "github" or "slack" (webhooks).
	SigningScheme string  `json:"signingScheme"`
	SourceID      *string `json:"sourceId"`
	// Config is the trigger config as saved (prefilter, decide, routes,
	// action, poll, push, ...). Kept as generic JSON.
	Config              map[string]interface{} `json:"config"`
	MaxEventsPerMin     int                    `json:"maxEventsPerMin"`
	MaxRunsPerDay       int                    `json:"maxRunsPerDay"`
	MaxConcurrentRuns   int                    `json:"maxConcurrentRuns"`
	ConsecutiveFailures int                    `json:"consecutiveFailures"`
	// LastEventAt, CreatedAt and UpdatedAt are ISO 8601 timestamps.
	LastEventAt *string `json:"lastEventAt"`
	CreatedAt   string  `json:"createdAt"`
	UpdatedAt   string  `json:"updatedAt"`
	// WebhookURL is the public ingress URL of a webhook trigger, nil for
	// other kinds.
	WebhookURL *string `json:"webhookUrl"`
	// ProjectAccess is false when the caller is no longer a member of the
	// trigger's project (the trigger can then only be disabled or deleted).
	ProjectAccess bool `json:"projectAccess"`
}

// TriggerListOptions filters TriggersAPI.List. Empty fields are not sent.
type TriggerListOptions struct {
	Project      string
	PipelineName string
}

// TriggerDelivery is one delivery receipt of a trigger.
type TriggerDelivery struct {
	ID         string `json:"id"`
	TriggerID  string `json:"triggerId"`
	EventID    string `json:"eventId"`
	Source     string `json:"source"`
	ReceivedAt string `json:"receivedAt"`
	// Verdict is one of TriggerVerdicts.
	Verdict string `json:"verdict"`
	// Decision holds the decision answers, when the trigger decides.
	// Kept as generic JSON.
	Decision  interface{} `json:"decision"`
	Action    *string     `json:"action"`
	RunID     *string     `json:"runId"`
	Detail    *string     `json:"detail"`
	LatencyMs *float64    `json:"latencyMs"`
	// Timings holds per-stage milliseconds: ingress, pickup, receipt,
	// prefilter, decide, act, total.
	Timings map[string]float64 `json:"timings"`
	// ResultExcerpt is the redacted output of a read-only tool call, up to 2 KB.
	ResultExcerpt *string `json:"resultExcerpt"`
	// Redelivered is the stream delivery count (more than 1 means the event
	// was redelivered after a crash or reclaim).
	Redelivered *int `json:"redelivered"`
}

// TriggerVerdictStats is the count and latency percentiles of one verdict.
type TriggerVerdictStats struct {
	N   int      `json:"n"`
	P50 *float64 `json:"p50"`
	P95 *float64 `json:"p95"`
}

// TriggerStats is the result of TriggersAPI.Stats.
type TriggerStats struct {
	Hours     int                            `json:"hours"`
	ByVerdict map[string]TriggerVerdictStats `json:"byVerdict"`
	// Filtered counts events dropped by the prefilter (they write no
	// receipt). Nil when the counter is unavailable.
	Filtered *int `json:"filtered"`
	// Sampled is true when the window held more receipts than the stats
	// sample (the latest 5,000).
	Sampled bool `json:"sampled"`
}

// TriggerPendingApproval is one tool call of a trigger waiting on a human
// decision. CreatedAt and ExpiresAt are epoch milliseconds.
type TriggerPendingApproval struct {
	RequestID   string `json:"requestId"`
	TriggerID   string `json:"triggerId"`
	DeliveryID  string `json:"deliveryId"`
	EventID     string `json:"eventId"`
	Source      string `json:"source"`
	Service     string `json:"service"`
	Tool        string `json:"tool"`
	ArgsPreview string `json:"argsPreview"`
	CreatedAt   int64  `json:"createdAt"`
	ExpiresAt   int64  `json:"expiresAt"`
}

// TriggerTestResult is the result of TriggersAPI.Test.
type TriggerTestResult struct {
	Accepted bool   `json:"accepted"`
	EventID  string `json:"eventId"`
	// Reason is set when Accepted is false: "disabled",
	// "project_access_lost", "rate_limited", "unavailable", ...
	Reason string `json:"reason,omitempty"`
}

// TriggerEventsOptions filters TriggersAPI.Events. Zero values are not sent.
type TriggerEventsOptions struct {
	// TriggerID narrows the log to one trigger.
	TriggerID string
	// Since returns only events newer than this epoch millisecond time. Pass
	// the newest At already seen to poll for new events.
	Since int64
	// Verdicts keeps only these verdicts (see TriggerVerdicts).
	Verdicts []string
	// Limit caps the number of events, 1-200. Zero uses the server default (50).
	Limit int
}

// TriggerLiveEvent is one entry of the live trigger event log.
type TriggerLiveEvent struct {
	TriggerID string `json:"triggerId"`
	// DeliveryID is nil for outcomes that wrote no receipt.
	DeliveryID *string `json:"deliveryId"`
	EventID    string  `json:"eventId"`
	// Source is "webhook", "wss", "engine", "poll", "push" or "test".
	Source string `json:"source"`
	// Verdict is one of TriggerVerdicts.
	Verdict       string   `json:"verdict"`
	Action        *string  `json:"action,omitempty"`
	RunID         *string  `json:"runId,omitempty"`
	Detail        *string  `json:"detail,omitempty"`
	LatencyMs     *float64 `json:"latencyMs,omitempty"`
	ResultExcerpt *string  `json:"resultExcerpt,omitempty"`
	// At is the event time in epoch milliseconds.
	At int64 `json:"at"`
}

// TriggerEventsRetention describes how much of the live log the server keeps.
type TriggerEventsRetention struct {
	MaxEvents int `json:"maxEvents"`
	TTLSec    int `json:"ttlSec"`
}

// TriggerEvents is the result of TriggersAPI.Events, newest first.
type TriggerEvents struct {
	Events    []TriggerLiveEvent     `json:"events"`
	Scanned   int                    `json:"scanned"`
	Retention TriggerEventsRetention `json:"retention"`
}

// TriggerPollStatus is the runtime state of a poll trigger.
type TriggerPollStatus struct {
	// Synced is false when the trigger has no runtime row yet (see PollSync).
	Synced          bool    `json:"synced"`
	Status          string  `json:"status"`
	LastError       *string `json:"lastError"`
	LastPolledAt    *string `json:"lastPolledAt"`
	NextPollAt      *string `json:"nextPollAt"`
	Armed           bool    `json:"armed"`
	BaselinePending bool    `json:"baselinePending"`
	SeenCount       int     `json:"seenCount"`
	ItemsPublished  int     `json:"itemsPublished"`
	// ConsecutiveErrors counts failed polls in a row.
	ConsecutiveErrors    int `json:"consecutiveErrors"`
	RequestedIntervalSec int `json:"requestedIntervalSec"`
	EffectiveIntervalSec int `json:"effectiveIntervalSec"`
	TierFloorSec         int `json:"tierFloorSec"`
}

// TriggerPollItem is one item a dry poll found.
type TriggerPollItem struct {
	ID      string `json:"id"`
	Preview string `json:"preview"`
}

// TriggerPollTestResult is the result of TriggersAPI.PollTest. When OK is
// false the poll's tool call failed: Error holds the poll error code and the
// other fields are zero.
type TriggerPollTestResult struct {
	Dry   bool   `json:"dry"`
	OK    bool   `json:"ok"`
	Error string `json:"error,omitempty"`
	Found int    `json:"found"`
	// Baseline is true on the first poll after arming: those items are
	// recorded and nothing is published.
	Baseline     bool              `json:"baseline"`
	WouldPublish int               `json:"wouldPublish"`
	Items        []TriggerPollItem `json:"items"`
	// SamplePayload is the first item as an event payload (generic JSON).
	SamplePayload interface{} `json:"samplePayload"`
}

// TriggerPollNowResult is the result of TriggersAPI.PollNow.
type TriggerPollNowResult struct {
	Dry    bool `json:"dry"`
	Queued bool `json:"queued"`
}

// TriggerPollSyncResult is the result of TriggersAPI.PollSync.
type TriggerPollSyncResult struct {
	Result string `json:"result"`
}

// TriggerBeta is the beta gate state for triggers.
type TriggerBeta struct {
	Allowed bool   `json:"allowed"`
	MinTier string `json:"minTier"`
}

// TriggerPresets is the result of TriggersAPI.Presets.
type TriggerPresets struct {
	Tier         string `json:"tier"`
	TierFloorSec int    `json:"tierFloorSec"`
	// Presets are the trigger presets available to the caller. Kept as
	// generic JSON.
	Presets []map[string]interface{} `json:"presets"`
	Beta    TriggerBeta              `json:"beta"`
}

// TriggerUsage is a used / cap pair.
type TriggerUsage struct {
	Used int `json:"used"`
	Cap  int `json:"cap"`
}

// TriggerEventsPerMin is the events-per-minute budget.
type TriggerEventsPerMin struct {
	PerUser           int `json:"perUser"`
	PerTriggerMax     int `json:"perTriggerMax"`
	PerTriggerDefault int `json:"perTriggerDefault"`
}

// TriggerApprovalTTL is the allowed approval wait, in seconds.
type TriggerApprovalTTL struct {
	Min     int `json:"min"`
	Max     int `json:"max"`
	Default int `json:"default"`
}

// TriggerLimits is the result of TriggersAPI.Limits.
type TriggerLimits struct {
	TierClass            string              `json:"tierClass"`
	Beta                 TriggerBeta         `json:"beta"`
	Triggers             TriggerUsage        `json:"triggers"`
	Sources              TriggerUsage        `json:"sources"`
	EventsPerMin         TriggerEventsPerMin `json:"eventsPerMin"`
	PollIntervalFloorSec int                 `json:"pollIntervalFloorSec"`
	ApprovalTTLSec       TriggerApprovalTTL  `json:"approvalTtlSec"`
}

// TriggerStreamSource is one WebSocket or SSE stream source. The auth value
// is never returned; HasAuth tells whether one is set.
type TriggerStreamSource struct {
	ID              string      `json:"id"`
	Name            string      `json:"name"`
	URL             string      `json:"url"`
	AuthHeaderName  *string     `json:"authHeaderName"`
	HasAuth         bool        `json:"hasAuth"`
	SubscribeFrame  interface{} `json:"subscribeFrame"`
	EventIDPath     *string     `json:"eventIdPath"`
	Enabled         bool        `json:"enabled"`
	Status          string      `json:"status"`
	LastError       *string     `json:"lastError"`
	LastConnectedAt *string     `json:"lastConnectedAt"`
	DroppedFrames   int         `json:"droppedFrames"`
	CreatedAt       string      `json:"createdAt"`
	// ConnectorService is set for a connector-bound source, nil otherwise.
	ConnectorService *string `json:"connectorService"`
	// Transport is "ws" (WebSocket) or "sse" (Server-Sent Events).
	Transport string `json:"transport"`
}

func triggerPath(id string, suffix string) string {
	return triggersBase + "/" + url.PathEscape(id) + suffix
}

// List returns the caller's event triggers. opts may be nil.
//
// GET /api/v1/private/triggers?project=&pipelineName=
func (t *TriggersAPI) List(ctx context.Context, opts *TriggerListOptions) ([]TriggerRecord, error) {
	q := map[string]string{}
	if opts != nil {
		if opts.Project != "" {
			q["project"] = opts.Project
		}
		if opts.PipelineName != "" {
			q["pipelineName"] = opts.PipelineName
		}
	}
	data, err := t.h.get(ctx, triggersBase, q)
	if err != nil {
		return nil, err
	}
	var v []TriggerRecord
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Get returns one trigger with its config. Secrets are never included.
//
// GET /api/v1/private/triggers/{id}
func (t *TriggersAPI) Get(ctx context.Context, id string) (*TriggerRecord, error) {
	data, err := t.h.get(ctx, triggerPath(id, ""), nil)
	if err != nil {
		return nil, err
	}
	var v TriggerRecord
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Deliveries returns the most recent delivery receipts of a trigger, newest
// first: verdict, decision answers, action, run id and timings. limit is
// 1-200; zero uses the server default (50).
//
// GET /api/v1/private/triggers/{id}/deliveries?limit=
func (t *TriggersAPI) Deliveries(ctx context.Context, id string, limit int) ([]TriggerDelivery, error) {
	q := map[string]string{}
	if limit > 0 {
		q["limit"] = strconv.Itoa(limit)
	}
	data, err := t.h.get(ctx, triggerPath(id, "/deliveries"), q)
	if err != nil {
		return nil, err
	}
	var v []TriggerDelivery
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Stats returns delivery counts and latency percentiles by verdict over the
// last hours (1-168); zero uses the server default (24).
//
// GET /api/v1/private/triggers/{id}/stats?hours=
func (t *TriggersAPI) Stats(ctx context.Context, id string, hours int) (*TriggerStats, error) {
	q := map[string]string{}
	if hours > 0 {
		q["hours"] = strconv.Itoa(hours)
	}
	data, err := t.h.get(ctx, triggerPath(id, "/stats"), q)
	if err != nil {
		return nil, err
	}
	var v TriggerStats
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// PendingApprovals returns the tool calls of this trigger still waiting on a
// human decision, oldest first. Approve or reject them in the Melaya app.
//
// GET /api/v1/private/triggers/{id}/approvals
func (t *TriggersAPI) PendingApprovals(ctx context.Context, id string) ([]TriggerPendingApproval, error) {
	data, err := t.h.get(ctx, triggerPath(id, "/approvals"), nil)
	if err != nil {
		return nil, err
	}
	var v []TriggerPendingApproval
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}

// Test sends one synthetic event through the trigger as a dry run: the
// prefilter, decision and routing run for real, the action never executes.
// Follow the result with Events or Deliveries using the returned EventID.
// payload may be nil (an empty object is sent). Test fires count against
// the trigger's rate limit.
//
// POST /api/v1/private/triggers/{id}/test
func (t *TriggersAPI) Test(ctx context.Context, id string, payload interface{}) (*TriggerTestResult, error) {
	if payload == nil {
		payload = map[string]interface{}{}
	}
	body := map[string]interface{}{"payload": payload}
	data, err := t.h.post(ctx, triggerPath(id, "/test"), body)
	if err != nil {
		return nil, err
	}
	var v TriggerTestResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Events returns recent live trigger events, newest first. It includes
// outcomes that wrote no receipt: filtered, shed, ingress rejections
// (signature, rate limit, duplicate, size), feed refusals and push lifecycle
// notes. opts may be nil.
//
// GET /api/v1/private/triggers/events?triggerId=&since=&verdicts=&limit=
func (t *TriggersAPI) Events(ctx context.Context, opts *TriggerEventsOptions) (*TriggerEvents, error) {
	q := map[string]string{}
	if opts != nil {
		if opts.TriggerID != "" {
			q["triggerId"] = opts.TriggerID
		}
		if opts.Since > 0 {
			q["since"] = strconv.FormatInt(opts.Since, 10)
		}
		if len(opts.Verdicts) > 0 {
			q["verdicts"] = strings.Join(opts.Verdicts, ",")
		}
		if opts.Limit > 0 {
			q["limit"] = strconv.Itoa(opts.Limit)
		}
	}
	data, err := t.h.get(ctx, triggersBase+"/events", q)
	if err != nil {
		return nil, err
	}
	var v TriggerEvents
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// PollStatus returns the runtime state of a poll trigger: status, last
// error, last and next poll, baseline, counters and intervals.
//
// GET /api/v1/private/triggers/{id}/poll
func (t *TriggersAPI) PollStatus(ctx context.Context, id string) (*TriggerPollStatus, error) {
	data, err := t.h.get(ctx, triggerPath(id, "/poll"), nil)
	if err != nil {
		return nil, err
	}
	var v TriggerPollStatus
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// PollTest runs the poll once as a dry run: it calls the tool and returns
// what it found and would publish. Nothing is published and no state
// changes. The server allows one dry poll every 10 seconds per user.
//
// A poll whose tool call fails is returned as a normal result with OK false
// and Error set to the poll error code, not as an error. HTTP errors (for
// example 429 when dry polls are throttled) still return a *MelayaError.
//
// POST /api/v1/private/triggers/{id}/poll/test  body {dry: true}
func (t *TriggersAPI) PollTest(ctx context.Context, id string) (*TriggerPollTestResult, error) {
	data, status, err := t.h.doWithRetry(ctx, http.MethodPost, triggerPath(id, "/poll/test"), nil, map[string]interface{}{"dry": true})
	if err != nil {
		return nil, err
	}
	// A 2xx dry-run body {dry: true, ok: false, error} is a result here, so
	// the shared ok:false check is skipped for it. Everything else (HTTP
	// errors included) goes through parseEnvelope as usual.
	var probe struct {
		Dry bool `json:"dry"`
	}
	if status >= 400 || json.Unmarshal(data, &probe) != nil || !probe.Dry {
		if data, err = parseEnvelope(data, status); err != nil {
			return nil, err
		}
	}
	var v TriggerPollTestResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// PollNow makes an enabled poll trigger due now: a real poll is queued and
// new items are published as events.
//
// POST /api/v1/private/triggers/{id}/poll/test  body {dry: false}
func (t *TriggersAPI) PollNow(ctx context.Context, id string) (*TriggerPollNowResult, error) {
	data, err := t.h.post(ctx, triggerPath(id, "/poll/test"), map[string]interface{}{"dry": false})
	if err != nil {
		return nil, err
	}
	var v TriggerPollNowResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// PollSync re-creates a poll trigger's runtime row from its saved config.
// Use it to re-arm a trigger whose poller never started (PollStatus reports
// Synced false).
//
// POST /api/v1/private/triggers/{id}/poll/sync
func (t *TriggersAPI) PollSync(ctx context.Context, id string) (*TriggerPollSyncResult, error) {
	data, err := t.h.post(ctx, triggerPath(id, "/poll/sync"), map[string]interface{}{})
	if err != nil {
		return nil, err
	}
	var v TriggerPollSyncResult
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Presets returns the trigger presets available to the caller, with the
// caller's tier and poll interval floor.
//
// GET /api/v1/private/triggers/presets
func (t *TriggersAPI) Presets(ctx context.Context) (*TriggerPresets, error) {
	data, err := t.h.get(ctx, triggersBase+"/presets", nil)
	if err != nil {
		return nil, err
	}
	var v TriggerPresets
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Limits returns plan caps and current usage for triggers and sources, the
// events-per-minute budget, the poll interval floor and approval wait bounds.
//
// GET /api/v1/private/triggers/limits
func (t *TriggersAPI) Limits(ctx context.Context) (*TriggerLimits, error) {
	data, err := t.h.get(ctx, triggersBase+"/limits", nil)
	if err != nil {
		return nil, err
	}
	var v TriggerLimits
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// Sources returns the caller's WebSocket and SSE stream sources.
//
// GET /api/v1/private/triggers/sources
func (t *TriggersAPI) Sources(ctx context.Context) ([]TriggerStreamSource, error) {
	data, err := t.h.get(ctx, triggersBase+"/sources", nil)
	if err != nil {
		return nil, err
	}
	var v []TriggerStreamSource
	if err := unmarshal(data, &v); err != nil {
		return nil, err
	}
	return v, nil
}
