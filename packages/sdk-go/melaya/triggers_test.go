// Unit tests for the event triggers API (mock HTTP transport via
// httptest.Server, no live network calls). Each test checks the method,
// path, query and body the SDK sends, and that the response decodes.
package melaya

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"testing"
)

const trigID = "3f2b8c1e-7a4d-4e5f-9b0a-1c2d3e4f5a6b"

// trigCapture records one request seen by the mock server.
type trigCapture struct {
	method string
	path   string
	query  map[string][]string
	body   map[string]interface{}
}

// trigServer returns a client whose server records the request and answers
// status + resp.
func trigServer(t *testing.T, status int, resp string) (*Client, *trigCapture) {
	t.Helper()
	got := &trigCapture{}
	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		got.method = r.Method
		got.path = r.URL.Path
		got.query = map[string][]string(r.URL.Query())
		if raw, _ := io.ReadAll(r.Body); len(raw) > 0 {
			if err := json.Unmarshal(raw, &got.body); err != nil {
				t.Errorf("request body is not JSON: %v", err)
			}
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(status)
		_, _ = w.Write([]byte(resp))
	})
	return c, got
}

func (g *trigCapture) expect(t *testing.T, method, path string) {
	t.Helper()
	if g.method != method {
		t.Errorf("expected %s, got %s", method, g.method)
	}
	if g.path != path {
		t.Errorf("expected path %s, got %s", path, g.path)
	}
}

func (g *trigCapture) expectQuery(t *testing.T, want map[string]string) {
	t.Helper()
	if len(g.query) != len(want) {
		t.Errorf("expected query %v, got %v", want, g.query)
	}
	for k, v := range want {
		if got := g.query[k]; len(got) != 1 || got[0] != v {
			t.Errorf("expected %s=%q, got %v", k, v, got)
		}
	}
}

func TestTriggersList_FiltersAndDecode(t *testing.T) {
	c, got := trigServer(t, 200, `[{"id":"`+trigID+`","publicId":"pub_1","name":"Stripe refunds","kind":"webhook","project":"acme","pipelineName":"refunds","enabled":true,"pausedReason":null,"signingScheme":"stripe","sourceId":null,"config":{"action":{"type":"notify"}},"maxEventsPerMin":60,"maxRunsPerDay":100,"maxConcurrentRuns":2,"consecutiveFailures":0,"lastEventAt":null,"createdAt":"2026-09-01T00:00:00.000Z","updatedAt":"2026-09-02T00:00:00.000Z","webhookUrl":"https://api.melaya.org/api/v1/hooks/pub_1","projectAccess":true}]`)

	res, err := c.Triggers.List(context.Background(), &TriggerListOptions{Project: "acme", PipelineName: "refunds"})
	if err != nil {
		t.Fatalf("List: %v", err)
	}
	got.expect(t, http.MethodGet, "/api/v1/private/triggers")
	got.expectQuery(t, map[string]string{"project": "acme", "pipelineName": "refunds"})
	if len(res) != 1 {
		t.Fatalf("expected 1 trigger, got %d", len(res))
	}
	tr := res[0]
	if tr.ID != trigID || tr.Kind != "webhook" || !tr.Enabled || !tr.ProjectAccess || tr.SigningScheme != "stripe" {
		t.Errorf("unexpected record: %+v", tr)
	}
	if tr.WebhookURL == nil || *tr.WebhookURL != "https://api.melaya.org/api/v1/hooks/pub_1" {
		t.Errorf("unexpected webhookUrl: %v", tr.WebhookURL)
	}
	if tr.PausedReason != nil || tr.LastEventAt != nil {
		t.Errorf("expected nil pausedReason/lastEventAt")
	}
	action, _ := tr.Config["action"].(map[string]interface{})
	if action["type"] != "notify" {
		t.Errorf("config not decoded as generic JSON: %+v", tr.Config)
	}
}

func TestTriggersList_NilOptionsSendsNoQuery(t *testing.T) {
	c, got := trigServer(t, 200, `[]`)
	res, err := c.Triggers.List(context.Background(), nil)
	if err != nil {
		t.Fatalf("List: %v", err)
	}
	got.expectQuery(t, map[string]string{})
	if len(res) != 0 {
		t.Errorf("expected empty list, got %+v", res)
	}
}

func TestTriggersGet(t *testing.T) {
	c, got := trigServer(t, 200, `{"id":"`+trigID+`","name":"Poll inbox","kind":"poll","project":"acme","pipelineName":"triage","enabled":false,"pausedReason":"project_access_lost","config":{},"webhookUrl":null,"projectAccess":false}`)
	res, err := c.Triggers.Get(context.Background(), trigID)
	if err != nil {
		t.Fatalf("Get: %v", err)
	}
	got.expect(t, http.MethodGet, "/api/v1/private/triggers/"+trigID)
	if res.Kind != "poll" || res.ProjectAccess || res.WebhookURL != nil {
		t.Errorf("unexpected record: %+v", res)
	}
	if res.PausedReason == nil || *res.PausedReason != "project_access_lost" {
		t.Errorf("unexpected pausedReason: %v", res.PausedReason)
	}
}

func TestTriggersDeliveries(t *testing.T) {
	c, got := trigServer(t, 200, `[{"id":"d1","triggerId":"`+trigID+`","eventId":"evt_1","source":"webhook","receivedAt":"2026-09-28T10:00:00.000Z","verdict":"dispatched","decision":{"urgent":{"choice":"yes","answer_confidence":0.91}},"action":"pipeline_run","runId":"run_1","detail":null,"latencyMs":412,"timings":{"ingress":3,"decide":180,"total":412},"resultExcerpt":null,"redelivered":1}]`)
	res, err := c.Triggers.Deliveries(context.Background(), trigID, 20)
	if err != nil {
		t.Fatalf("Deliveries: %v", err)
	}
	got.expect(t, http.MethodGet, "/api/v1/private/triggers/"+trigID+"/deliveries")
	got.expectQuery(t, map[string]string{"limit": "20"})
	if len(res) != 1 {
		t.Fatalf("expected 1 delivery, got %d", len(res))
	}
	d := res[0]
	if d.Verdict != "dispatched" || d.RunID == nil || *d.RunID != "run_1" || d.LatencyMs == nil || *d.LatencyMs != 412 {
		t.Errorf("unexpected delivery: %+v", d)
	}
	if d.Timings["decide"] != 180 || d.Redelivered == nil || *d.Redelivered != 1 {
		t.Errorf("unexpected timings/redelivered: %+v", d)
	}
	dec, _ := d.Decision.(map[string]interface{})
	if _, ok := dec["urgent"]; !ok {
		t.Errorf("decision not decoded as generic JSON: %+v", d.Decision)
	}
}

func TestTriggersDeliveries_ZeroLimitOmitted(t *testing.T) {
	c, got := trigServer(t, 200, `[]`)
	if _, err := c.Triggers.Deliveries(context.Background(), trigID, 0); err != nil {
		t.Fatalf("Deliveries: %v", err)
	}
	got.expectQuery(t, map[string]string{})
}

func TestTriggersStats(t *testing.T) {
	c, got := trigServer(t, 200, `{"hours":48,"byVerdict":{"dispatched":{"n":12,"p50":300,"p95":900},"failed":{"n":1,"p50":null,"p95":null}},"filtered":7,"sampled":false}`)
	res, err := c.Triggers.Stats(context.Background(), trigID, 48)
	if err != nil {
		t.Fatalf("Stats: %v", err)
	}
	got.expect(t, http.MethodGet, "/api/v1/private/triggers/"+trigID+"/stats")
	got.expectQuery(t, map[string]string{"hours": "48"})
	if res.Hours != 48 || res.Filtered == nil || *res.Filtered != 7 || res.Sampled {
		t.Errorf("unexpected stats: %+v", res)
	}
	if v := res.ByVerdict["dispatched"]; v.N != 12 || v.P95 == nil || *v.P95 != 900 {
		t.Errorf("unexpected dispatched stats: %+v", v)
	}
	if v := res.ByVerdict["failed"]; v.N != 1 || v.P50 != nil {
		t.Errorf("unexpected failed stats: %+v", v)
	}
}

func TestTriggersPendingApprovals(t *testing.T) {
	c, got := trigServer(t, 200, `[{"requestId":"req_1","triggerId":"`+trigID+`","deliveryId":"d1","eventId":"evt_1","source":"webhook","service":"gmail","tool":"gmail_send","argsPreview":"{\"to\":\"a@b.c\"}","createdAt":1790000000000,"expiresAt":1790003600000}]`)
	res, err := c.Triggers.PendingApprovals(context.Background(), trigID)
	if err != nil {
		t.Fatalf("PendingApprovals: %v", err)
	}
	got.expect(t, http.MethodGet, "/api/v1/private/triggers/"+trigID+"/approvals")
	if len(res) != 1 || res[0].RequestID != "req_1" || res[0].Tool != "gmail_send" || res[0].ExpiresAt != 1790003600000 {
		t.Errorf("unexpected approvals: %+v", res)
	}
}

func TestTriggersTest_SendsPayload(t *testing.T) {
	c, got := trigServer(t, 200, `{"accepted":true,"eventId":"test-abc"}`)
	res, err := c.Triggers.Test(context.Background(), trigID, map[string]interface{}{"amount": 42})
	if err != nil {
		t.Fatalf("Test: %v", err)
	}
	got.expect(t, http.MethodPost, "/api/v1/private/triggers/"+trigID+"/test")
	payload, ok := got.body["payload"].(map[string]interface{})
	if !ok || payload["amount"] != float64(42) {
		t.Errorf("unexpected request body: %+v", got.body)
	}
	if !res.Accepted || res.EventID != "test-abc" || res.Reason != "" {
		t.Errorf("unexpected result: %+v", res)
	}
}

func TestTriggersTest_NilPayloadAndRefusal(t *testing.T) {
	c, got := trigServer(t, 200, `{"accepted":false,"eventId":"test-def","reason":"disabled"}`)
	res, err := c.Triggers.Test(context.Background(), trigID, nil)
	if err != nil {
		t.Fatalf("Test: %v", err)
	}
	payload, ok := got.body["payload"].(map[string]interface{})
	if !ok || len(payload) != 0 {
		t.Errorf("expected empty payload object, got %+v", got.body)
	}
	if res.Accepted || res.Reason != "disabled" {
		t.Errorf("unexpected result: %+v", res)
	}
}

func TestTriggersEvents_QueryEncoding(t *testing.T) {
	c, got := trigServer(t, 200, `{"events":[{"triggerId":"`+trigID+`","deliveryId":null,"eventId":"evt_9","source":"webhook","verdict":"filtered","detail":"prefilter false","at":1790000000500}],"scanned":37,"retention":{"maxEvents":500,"ttlSec":86400}}`)
	res, err := c.Triggers.Events(context.Background(), &TriggerEventsOptions{
		TriggerID: trigID,
		Since:     1790000000000,
		Verdicts:  []string{"filtered", "rejected"},
		Limit:     20,
	})
	if err != nil {
		t.Fatalf("Events: %v", err)
	}
	got.expect(t, http.MethodGet, "/api/v1/private/triggers/events")
	got.expectQuery(t, map[string]string{
		"triggerId": trigID,
		"since":     "1790000000000",
		"verdicts":  "filtered,rejected",
		"limit":     "20",
	})
	if res.Scanned != 37 || res.Retention.MaxEvents != 500 || res.Retention.TTLSec != 86400 {
		t.Errorf("unexpected envelope: %+v", res)
	}
	if len(res.Events) != 1 {
		t.Fatalf("expected 1 event, got %d", len(res.Events))
	}
	e := res.Events[0]
	if e.Verdict != "filtered" || e.DeliveryID != nil || e.At != 1790000000500 || e.Detail == nil || *e.Detail != "prefilter false" {
		t.Errorf("unexpected event: %+v", e)
	}
}

func TestTriggersEvents_NilOptions(t *testing.T) {
	c, got := trigServer(t, 200, `{"events":[],"scanned":0,"retention":{"maxEvents":500,"ttlSec":86400}}`)
	if _, err := c.Triggers.Events(context.Background(), nil); err != nil {
		t.Fatalf("Events: %v", err)
	}
	got.expectQuery(t, map[string]string{})
}

func TestTriggersPollStatus(t *testing.T) {
	c, got := trigServer(t, 200, `{"synced":true,"status":"ok","lastError":null,"lastPolledAt":"2026-09-28T10:00:00.000Z","nextPollAt":"2026-09-28T10:05:00.000Z","armed":true,"baselinePending":false,"seenCount":14,"itemsPublished":3,"consecutiveErrors":0,"requestedIntervalSec":60,"effectiveIntervalSec":300,"tierFloorSec":300}`)
	res, err := c.Triggers.PollStatus(context.Background(), trigID)
	if err != nil {
		t.Fatalf("PollStatus: %v", err)
	}
	got.expect(t, http.MethodGet, "/api/v1/private/triggers/"+trigID+"/poll")
	if !res.Synced || !res.Armed || res.SeenCount != 14 || res.EffectiveIntervalSec != 300 || res.RequestedIntervalSec != 60 {
		t.Errorf("unexpected poll status: %+v", res)
	}
	if res.NextPollAt == nil || res.LastError != nil {
		t.Errorf("unexpected nullable fields: %+v", res)
	}
}

func TestTriggersPollTest_DryTrue(t *testing.T) {
	c, got := trigServer(t, 200, `{"dry":true,"ok":true,"found":2,"baseline":false,"wouldPublish":1,"items":[{"id":"m1","preview":"{\"subject\":\"Invoice\"}"}],"samplePayload":{"subject":"Invoice"}}`)
	res, err := c.Triggers.PollTest(context.Background(), trigID)
	if err != nil {
		t.Fatalf("PollTest: %v", err)
	}
	got.expect(t, http.MethodPost, "/api/v1/private/triggers/"+trigID+"/poll/test")
	if got.body["dry"] != true {
		t.Errorf("expected dry:true in body, got %+v", got.body)
	}
	if !res.Dry || !res.OK || res.Found != 2 || res.WouldPublish != 1 || len(res.Items) != 1 || res.Items[0].ID != "m1" {
		t.Errorf("unexpected dry poll: %+v", res)
	}
	sp, _ := res.SamplePayload.(map[string]interface{})
	if sp["subject"] != "Invoice" {
		t.Errorf("unexpected samplePayload: %+v", res.SamplePayload)
	}
}

func TestTriggersPollTest_FailedPollIsResult(t *testing.T) {
	c, got := trigServer(t, 200, `{"dry":true,"ok":false,"error":"tool_failed"}`)
	res, err := c.Triggers.PollTest(context.Background(), trigID)
	if err != nil {
		t.Fatalf("a failed dry poll must be a result, got error: %v", err)
	}
	if got.body["dry"] != true {
		t.Errorf("expected dry:true in body, got %+v", got.body)
	}
	if !res.Dry || res.OK || res.Error != "tool_failed" {
		t.Errorf("unexpected result: %+v", res)
	}
}

func TestTriggersPollTest_HTTPErrorStillErrors(t *testing.T) {
	c, _ := trigServer(t, 429, `{"error":"poll_dry_run_throttled","message":"[poll_dry_run_throttled] Wait a few seconds before testing the poll again."}`)
	_, err := c.Triggers.PollTest(context.Background(), trigID)
	var melErr *MelayaError
	if !errors.As(err, &melErr) {
		t.Fatalf("expected *MelayaError, got %v", err)
	}
	if melErr.Status != 429 || melErr.Code != "poll_dry_run_throttled" {
		t.Errorf("unexpected error: status=%d code=%q", melErr.Status, melErr.Code)
	}
}

func TestTriggersPollNow_OkFalseStillErrors(t *testing.T) {
	c, _ := trigServer(t, 200, `{"dry":false,"ok":false,"error":"trigger_disabled"}`)
	_, err := c.Triggers.PollNow(context.Background(), trigID)
	var melErr *MelayaError
	if !errors.As(err, &melErr) || melErr.Code != "trigger_disabled" {
		t.Fatalf("expected *MelayaError with code trigger_disabled, got %v", err)
	}
}

func TestTriggersPollNow_DryFalse(t *testing.T) {
	c, got := trigServer(t, 200, `{"dry":false,"queued":true}`)
	res, err := c.Triggers.PollNow(context.Background(), trigID)
	if err != nil {
		t.Fatalf("PollNow: %v", err)
	}
	got.expect(t, http.MethodPost, "/api/v1/private/triggers/"+trigID+"/poll/test")
	if v, ok := got.body["dry"]; !ok || v != false {
		t.Errorf("expected dry:false in body, got %+v", got.body)
	}
	if res.Dry || !res.Queued {
		t.Errorf("unexpected result: %+v", res)
	}
}

func TestTriggersPollSync(t *testing.T) {
	c, got := trigServer(t, 200, `{"result":"synced"}`)
	res, err := c.Triggers.PollSync(context.Background(), trigID)
	if err != nil {
		t.Fatalf("PollSync: %v", err)
	}
	got.expect(t, http.MethodPost, "/api/v1/private/triggers/"+trigID+"/poll/sync")
	if res.Result != "synced" {
		t.Errorf("unexpected result: %+v", res)
	}
}

func TestTriggersPresets(t *testing.T) {
	c, got := trigServer(t, 200, `{"tier":"forge","tierFloorSec":60,"presets":[{"id":"gmail_new_email","category":"email","available":true}],"beta":{"allowed":true,"minTier":"forge"}}`)
	res, err := c.Triggers.Presets(context.Background())
	if err != nil {
		t.Fatalf("Presets: %v", err)
	}
	got.expect(t, http.MethodGet, "/api/v1/private/triggers/presets")
	if res.Tier != "forge" || res.TierFloorSec != 60 || !res.Beta.Allowed || res.Beta.MinTier != "forge" {
		t.Errorf("unexpected presets envelope: %+v", res)
	}
	if len(res.Presets) != 1 || res.Presets[0]["id"] != "gmail_new_email" {
		t.Errorf("unexpected presets: %+v", res.Presets)
	}
}

func TestTriggersLimits(t *testing.T) {
	c, got := trigServer(t, 200, `{"tierClass":"forge","beta":{"allowed":true,"minTier":"forge"},"triggers":{"used":2,"cap":10},"sources":{"used":0,"cap":2},"eventsPerMin":{"perUser":600,"perTriggerMax":300,"perTriggerDefault":60},"pollIntervalFloorSec":60,"approvalTtlSec":{"min":60,"max":86400,"default":3600}}`)
	res, err := c.Triggers.Limits(context.Background())
	if err != nil {
		t.Fatalf("Limits: %v", err)
	}
	got.expect(t, http.MethodGet, "/api/v1/private/triggers/limits")
	if res.TierClass != "forge" || res.Triggers.Used != 2 || res.Triggers.Cap != 10 || res.Sources.Cap != 2 {
		t.Errorf("unexpected limits: %+v", res)
	}
	if res.EventsPerMin.PerUser != 600 || res.PollIntervalFloorSec != 60 || res.ApprovalTTLSec.Max != 86400 {
		t.Errorf("unexpected limits detail: %+v", res)
	}
}

func TestTriggersSources(t *testing.T) {
	c, got := trigServer(t, 200, `[{"id":"s1","name":"Kalshi feed","url":"wss://api.kalshi.com/ws","authHeaderName":null,"hasAuth":false,"subscribeFrame":{"cmd":"subscribe"},"eventIdPath":"msg.id","enabled":true,"status":"connected","lastError":null,"lastConnectedAt":"2026-09-28T09:00:00.000Z","droppedFrames":0,"createdAt":"2026-09-01T00:00:00.000Z","connectorService":"kalshi","transport":"ws"}]`)
	res, err := c.Triggers.Sources(context.Background())
	if err != nil {
		t.Fatalf("Sources: %v", err)
	}
	got.expect(t, http.MethodGet, "/api/v1/private/triggers/sources")
	if len(res) != 1 {
		t.Fatalf("expected 1 source, got %d", len(res))
	}
	s := res[0]
	if s.Transport != "ws" || s.ConnectorService == nil || *s.ConnectorService != "kalshi" || s.Status != "connected" {
		t.Errorf("unexpected source: %+v", s)
	}
}

func TestTriggers_ErrorStatusIsMelayaError(t *testing.T) {
	c, _ := trigServer(t, 404, `{"error":"trigger_not_found","message":"[trigger_not_found] Trigger not found."}`)
	_, err := c.Triggers.Get(context.Background(), trigID)
	var melErr *MelayaError
	if !errors.As(err, &melErr) || melErr.Status != 404 {
		t.Fatalf("expected 404 *MelayaError, got %v", err)
	}
}

func TestTriggers_ExposedOnAgentsNamespace(t *testing.T) {
	c, _ := trigServer(t, 200, `[]`)
	if c.Agents.Triggers == nil || c.Agents.Triggers != c.Triggers {
		t.Fatalf("expected m.Agents.Triggers to be the same pointer as m.Triggers")
	}
}
