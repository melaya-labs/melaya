// Unit tests for the connector tools API (mock HTTP transport via
// httptest.Server — no live network calls). Covers, per the quality bar:
//   - Search query encoding (q + limit)
//   - Call of a read tool (200, immediate result)
//   - Call of a write staged for approval (202 body returned, not an error)
//   - CallStatus reporting "done"
//   - A money-moving refusal (403) surfacing as a *MelayaError
//   - CallAndWait polling to "done" with a tiny interval
package melaya

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"testing"
	"time"
)

func TestConnectorToolsSearch_QueryEncoding(t *testing.T) {
	var gotPath string
	var gotQuery map[string][]string

	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		gotQuery = map[string][]string(r.URL.Query())
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"query":"unread email","services":["gmail","melaya_core"],"tools":[{"name":"gmail_list_messages","service":"gmail","description":"List messages","readOnly":true,"movesMoney":false,"params":{}}]}`))
	})

	res, err := c.ConnectorTools.Search(context.Background(), "unread email", &ConnectorToolSearchOptions{Limit: 5})
	if err != nil {
		t.Fatalf("Search: %v", err)
	}
	if gotPath != connectorToolsBase+"/search" {
		t.Errorf("unexpected path: %s", gotPath)
	}
	if got := gotQuery["q"]; len(got) != 1 || got[0] != "unread email" {
		t.Errorf("expected q=%q, got %v", "unread email", got)
	}
	if got := gotQuery["limit"]; len(got) != 1 || got[0] != "5" {
		t.Errorf("expected limit=5, got %v", got)
	}
	if res.Query != "unread email" {
		t.Errorf("unexpected query echo: %q", res.Query)
	}
	if len(res.Tools) != 1 || res.Tools[0].Name != "gmail_list_messages" {
		t.Fatalf("unexpected tools: %+v", res.Tools)
	}
}

func TestConnectorToolsSearch_NoLimitOmitsQueryParam(t *testing.T) {
	var gotQuery map[string][]string

	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		gotQuery = map[string][]string(r.URL.Query())
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"query":"refund","services":[],"tools":[]}`))
	})

	if _, err := c.ConnectorTools.Search(context.Background(), "refund", nil); err != nil {
		t.Fatalf("Search: %v", err)
	}
	if _, ok := gotQuery["limit"]; ok {
		t.Errorf("expected no limit query param, got %v", gotQuery["limit"])
	}
}

func TestConnectorToolsCall_ReadRunsImmediately(t *testing.T) {
	var gotPath, gotMethod string
	var gotBody map[string]interface{}

	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		gotMethod = r.Method
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &gotBody)
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"status":"done","tool":"gmail_list_messages","readOnly":true,"result":"3 unread"}`))
	})

	res, err := c.ConnectorTools.Call(context.Background(), "gmail_list_messages", nil, nil)
	if err != nil {
		t.Fatalf("Call: %v", err)
	}
	if gotMethod != http.MethodPost {
		t.Errorf("expected POST, got %s", gotMethod)
	}
	if gotPath != connectorToolsBase+"/call" {
		t.Errorf("unexpected path: %s", gotPath)
	}
	if gotBody["approval"] != "required" {
		t.Errorf("expected default approval \"required\" in body, got %+v", gotBody)
	}
	if _, ok := gotBody["args"]; ok {
		t.Errorf("expected no args key for a nil args map, got %+v", gotBody)
	}
	if res.Status != "done" || res.Result != "3 unread" {
		t.Fatalf("unexpected result: %+v", res)
	}
}

func TestConnectorToolsCall_WriteStagedFor202IsNotAnError(t *testing.T) {
	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		var body map[string]interface{}
		raw, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(raw, &body)
		if body["approval"] != "required" {
			t.Errorf("expected approval \"required\", got %+v", body)
		}
		args, ok := body["args"].(map[string]interface{})
		if !ok || args["to"] != "a@b.com" {
			t.Errorf("expected args.to == a@b.com, got %+v", body["args"])
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusAccepted)
		_, _ = w.Write([]byte(`{"status":"pending_approval","tool":"gmail_send","requestId":"req-1","message":"Approve or reject it in the Melaya app."}`))
	})

	res, err := c.ConnectorTools.Call(context.Background(), "gmail_send", map[string]interface{}{"to": "a@b.com"}, nil)
	if err != nil {
		t.Fatalf("Call returned an error for a 202 response, want a parsed result: %v", err)
	}
	if res.Status != "pending_approval" || res.RequestID != "req-1" {
		t.Fatalf("unexpected result: %+v", res)
	}
}

func TestConnectorToolsCall_ApprovalNone(t *testing.T) {
	var gotBody map[string]interface{}

	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		raw, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(raw, &gotBody)
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"status":"done","tool":"gmail_send","readOnly":false,"result":"sent"}`))
	})

	res, err := c.ConnectorTools.Call(context.Background(), "gmail_send", map[string]interface{}{"to": "a@b.com"}, &ConnectorToolCallOptions{Approval: "none"})
	if err != nil {
		t.Fatalf("Call: %v", err)
	}
	if gotBody["approval"] != "none" {
		t.Errorf("expected approval \"none\" in body, got %+v", gotBody)
	}
	if res.Status != "done" || res.Result != "sent" {
		t.Fatalf("unexpected result: %+v", res)
	}
}

func TestConnectorToolsCall_MoneyMovingRefusalRaisesMelayaError(t *testing.T) {
	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusForbidden)
		_, _ = w.Write([]byte(`{"error":"money_moving_requires_app_approval","tool":"stripe_create_refund","message":"Tools that move money or trade run only from the Melaya app."}`))
	})

	_, err := c.ConnectorTools.Call(context.Background(), "stripe_create_refund", nil, &ConnectorToolCallOptions{Approval: "none"})
	if err == nil {
		t.Fatal("expected an error for a money-moving refusal, got nil")
	}
	melErr, ok := err.(*MelayaError)
	if !ok {
		t.Fatalf("expected a *MelayaError, got %T: %v", err, err)
	}
	if melErr.Status != http.StatusForbidden {
		t.Errorf("expected status 403, got %d", melErr.Status)
	}
	if melErr.Code != "money_moving_requires_app_approval" {
		t.Errorf("expected code money_moving_requires_app_approval, got %q", melErr.Code)
	}
	if !melErr.IsMoneyMovingRefused() {
		t.Error("expected IsMoneyMovingRefused() to be true")
	}
}

func TestConnectorToolsCallStatus_Done(t *testing.T) {
	var gotPath string

	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"requestId":"req-1","tool":"gmail_send","status":"done","ok":true,"result":"sent"}`))
	})

	res, err := c.ConnectorTools.CallStatus(context.Background(), "req-1")
	if err != nil {
		t.Fatalf("CallStatus: %v", err)
	}
	if gotPath != connectorToolsBase+"/calls/req-1" {
		t.Errorf("unexpected path: %s", gotPath)
	}
	if res.Status != "done" || !res.Ok || res.Result != "sent" {
		t.Fatalf("unexpected result: %+v", res)
	}
}

func TestConnectorToolsCallStatus_NotFound(t *testing.T) {
	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusNotFound)
		_, _ = w.Write([]byte(`{"error":"not_found","message":"Unknown or expired request."}`))
	})

	_, err := c.ConnectorTools.CallStatus(context.Background(), "gone")
	if err == nil {
		t.Fatal("expected an error for an unknown requestId")
	}
	melErr, ok := err.(*MelayaError)
	if !ok || melErr.Status != http.StatusNotFound {
		t.Fatalf("expected a 404 *MelayaError, got %T: %v", err, err)
	}
}

// TestConnectorToolsCallAndWait_PollsToDone stages a write, returns "pending"
// on the first two status polls, then "done" — CallAndWait must poll through
// the pending states (with a tiny interval) and return the final outcome.
func TestConnectorToolsCallAndWait_PollsToDone(t *testing.T) {
	var callCount, statusPolls int

	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch {
		case r.Method == http.MethodPost && r.URL.Path == connectorToolsBase+"/call":
			callCount++
			w.WriteHeader(http.StatusAccepted)
			_, _ = w.Write([]byte(`{"status":"pending_approval","tool":"gmail_send","requestId":"req-42","message":"pending"}`))
		case r.Method == http.MethodGet && r.URL.Path == connectorToolsBase+"/calls/req-42":
			statusPolls++
			if statusPolls < 3 {
				_, _ = w.Write([]byte(`{"requestId":"req-42","tool":"gmail_send","status":"pending"}`))
				return
			}
			_, _ = w.Write([]byte(`{"requestId":"req-42","tool":"gmail_send","status":"done","ok":true,"result":"sent"}`))
		default:
			t.Errorf("unexpected request: %s %s", r.Method, r.URL.Path)
			w.WriteHeader(http.StatusNotFound)
		}
	})

	res, err := c.ConnectorTools.CallAndWait(context.Background(), "gmail_send", map[string]interface{}{"to": "a@b.com"}, &ConnectorToolCallAndWaitOptions{
		PollInterval: time.Millisecond,
		Timeout:      time.Second,
	})
	if err != nil {
		t.Fatalf("CallAndWait: %v", err)
	}
	if callCount != 1 {
		t.Errorf("expected exactly one Call, got %d", callCount)
	}
	if statusPolls < 3 {
		t.Errorf("expected CallAndWait to poll through the pending states, got %d polls", statusPolls)
	}
	if res.Status != "done" || !res.Ok || res.Result != "sent" {
		t.Fatalf("unexpected final outcome: %+v", res)
	}
}

// TestConnectorToolsCallAndWait_ReadSkipsPolling ensures a read (Status
// "done" from Call itself) never touches CallStatus.
func TestConnectorToolsCallAndWait_ReadSkipsPolling(t *testing.T) {
	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == connectorToolsBase+"/calls/anything" {
			t.Fatal("CallAndWait should not poll for an immediate result")
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"status":"done","tool":"gmail_list_messages","readOnly":true,"result":"3 unread"}`))
	})

	res, err := c.ConnectorTools.CallAndWait(context.Background(), "gmail_list_messages", nil, nil)
	if err != nil {
		t.Fatalf("CallAndWait: %v", err)
	}
	if res.Status != "done" || res.Result != "3 unread" {
		t.Fatalf("unexpected result: %+v", res)
	}
}

func TestConnectorToolsServices(t *testing.T) {
	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != connectorToolsBase+"/services" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"services":["gmail"],"builtIn":"melaya_core","toolCounts":{"gmail":{"readTools":1,"writeTools":1}}}`))
	})

	res, err := c.ConnectorTools.Services(context.Background())
	if err != nil {
		t.Fatalf("Services: %v", err)
	}
	if res.BuiltIn != "melaya_core" {
		t.Errorf("unexpected builtIn: %q", res.BuiltIn)
	}
	if res.ToolCounts["gmail"].ReadTools != 1 || res.ToolCounts["gmail"].WriteTools != 1 {
		t.Fatalf("unexpected tool counts: %+v", res.ToolCounts)
	}
}

func TestConnectorToolsConnect(t *testing.T) {
	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		var body map[string]interface{}
		raw, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(raw, &body)
		if body["service"] != "gmail" {
			t.Errorf("unexpected body: %+v", body)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"service":"gmail","kind":"oauth","authorizationUrl":"https://accounts.google.com/o/oauth2/…","message":"Open this URL to connect."}`))
	})

	res, err := c.ConnectorTools.Connect(context.Background(), "gmail")
	if err != nil {
		t.Fatalf("Connect: %v", err)
	}
	if res.Kind != "oauth" || res.AuthorizationURL == "" {
		t.Fatalf("unexpected result: %+v", res)
	}
}

func TestConnectorToolsDescribe_NotFound(t *testing.T) {
	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != connectorToolsBase+"/tools/nope_tool" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusNotFound)
		_, _ = w.Write([]byte(`{"error":"not_found","message":"Unknown tool, or not unlocked by your connected services."}`))
	})

	_, err := c.ConnectorTools.Describe(context.Background(), "nope_tool")
	if err == nil {
		t.Fatal("expected an error for an unknown tool")
	}
	melErr, ok := err.(*MelayaError)
	if !ok || melErr.Status != http.StatusNotFound {
		t.Fatalf("expected a 404 *MelayaError, got %T: %v", err, err)
	}
}

func TestConnectorToolsTest_Timeout(t *testing.T) {
	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusGatewayTimeout)
		_, _ = w.Write([]byte(`{"error":"timeout","service":"gmail","message":"The service did not answer within 30 seconds."}`))
	})

	_, err := c.ConnectorTools.Test(context.Background(), "gmail")
	if err == nil {
		t.Fatal("expected a timeout error")
	}
	melErr, ok := err.(*MelayaError)
	if !ok || melErr.Status != http.StatusGatewayTimeout || melErr.Code != "timeout" {
		t.Fatalf("expected a 504 timeout *MelayaError, got %T: %v", err, err)
	}
}
