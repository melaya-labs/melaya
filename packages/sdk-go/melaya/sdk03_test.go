// Unit tests for the SDK 0.3 surface additions (mock HTTP transport via
// httptest.Server — no live network calls). Covers, per the quality bar:
//   - Run() with a run_inputs body
//   - one multipart upload (Content-Type boundary + file part with filename)
//   - RunInputFile() returning raw bytes
//   - ProjectToolCalls() query encoding
//   - one bridged POST with a path param + body (ApplyPersonal)
//   - one DELETE with a JSON body (GoogleDisconnect)
package melaya

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"mime"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

// newTestClient spins up an httptest.Server driven by handler and returns a
// Client pointed at it.
func newTestClient(t *testing.T, handler http.HandlerFunc) *Client {
	t.Helper()
	srv := httptest.NewServer(handler)
	t.Cleanup(srv.Close)
	c, err := New("mk_test_key", Options{BaseURL: srv.URL})
	if err != nil {
		t.Fatalf("New: %v", err)
	}
	return c
}

func TestPipelinesRun_WithRunInputsBody(t *testing.T) {
	var gotBody map[string]interface{}
	var gotPath, gotAuth string

	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		gotAuth = r.Header.Get("Authorization")
		if r.Method != http.MethodPost {
			t.Errorf("expected POST, got %s", r.Method)
		}
		body, _ := io.ReadAll(r.Body)
		if err := json.Unmarshal(body, &gotBody); err != nil {
			t.Fatalf("unmarshal request body: %v", err)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"run_id":"run_abc123","queued":true,"run_inputs":{"brief":"resolved brief"}}`))
	})

	opts := &PipelineRunOptions{
		Project: "acme",
		RunInputs: &RunInputs{
			Brief: "Summarize the Q3 report",
			Values: map[string]interface{}{
				"doc": map[string]interface{}{"file_id": "file_123"},
			},
		},
	}
	res, err := c.Pipelines.Run(context.Background(), "daily-digest", opts)
	if err != nil {
		t.Fatalf("Run: %v", err)
	}
	if gotPath != "/api/v1/private/pipelines/daily-digest/run" {
		t.Errorf("unexpected path: %s", gotPath)
	}
	if gotAuth != "Bearer mk_test_key" {
		t.Errorf("unexpected Authorization header: %s", gotAuth)
	}
	if res.RunID != "run_abc123" || !res.Queued {
		t.Fatalf("unexpected result: %+v", res)
	}
	if res.RunInputs == nil || res.RunInputs.Brief != "resolved brief" {
		t.Fatalf("expected run_inputs echo in response, got %+v", res.RunInputs)
	}

	riRaw, ok := gotBody["run_inputs"].(map[string]interface{})
	if !ok {
		t.Fatalf("expected run_inputs in request body, got %+v", gotBody)
	}
	if riRaw["brief"] != "Summarize the Q3 report" {
		t.Errorf("unexpected run_inputs.brief in request body: %+v", riRaw)
	}
	values, ok := riRaw["values"].(map[string]interface{})
	if !ok {
		t.Fatalf("expected run_inputs.values in request body, got %+v", riRaw)
	}
	doc, ok := values["doc"].(map[string]interface{})
	if !ok || doc["file_id"] != "file_123" {
		t.Errorf("unexpected run_inputs.values.doc: %+v", values["doc"])
	}
	if gotBody["project"] != "acme" {
		t.Errorf("expected project in request body, got %+v", gotBody)
	}
}

func TestUploadRunFile_MultipartBoundaryAndFilename(t *testing.T) {
	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Errorf("expected POST, got %s", r.Method)
		}
		if r.URL.Path != "/api/v1/private/pipelines/daily-digest/run-files" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		if got := r.URL.Query().Get("key"); got != "doc" {
			t.Errorf("expected key=doc query param, got %q", got)
		}
		if got := r.URL.Query().Get("project"); got != "acme" {
			t.Errorf("expected project=acme query param, got %q", got)
		}

		contentType := r.Header.Get("Content-Type")
		mediaType, params, err := mime.ParseMediaType(contentType)
		if err != nil {
			t.Fatalf("parse Content-Type %q: %v", contentType, err)
		}
		if !strings.HasPrefix(mediaType, "multipart/") {
			t.Fatalf("expected a multipart Content-Type, got %q", mediaType)
		}
		if params["boundary"] == "" {
			t.Fatalf("expected a boundary in the multipart Content-Type, got %q", contentType)
		}

		mr := multipart.NewReader(r.Body, params["boundary"])
		part, err := mr.NextPart()
		if err != nil {
			t.Fatalf("read multipart part: %v", err)
		}
		if part.FormName() != "file" {
			t.Errorf("expected form field name 'file', got %q", part.FormName())
		}
		if part.FileName() != "report.pdf" {
			t.Errorf("expected filename 'report.pdf', got %q", part.FileName())
		}
		data, err := io.ReadAll(part)
		if err != nil {
			t.Fatalf("read part body: %v", err)
		}
		if string(data) != "hello world" {
			t.Errorf("unexpected file contents: %q", data)
		}

		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"file_id":"file_abc"}`))
	})

	res, err := c.Pipelines.UploadRunFile(
		context.Background(), "daily-digest", "doc",
		strings.NewReader("hello world"), "report.pdf",
		&UploadRunFileOptions{Project: "acme"},
	)
	if err != nil {
		t.Fatalf("UploadRunFile: %v", err)
	}
	if res.FileID != "file_abc" {
		t.Errorf("unexpected result: %+v", res)
	}
}

func TestRunInputFile_ReturnsRawBytes(t *testing.T) {
	want := []byte{0x25, 0x50, 0x44, 0x46, 0x00, 0x01, 0x02, 0xff}

	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		wantPath := "/api/v1/private/pipelines/daily-digest/runs/0123456789abcdef/inputs/files/0"
		if r.URL.Path != wantPath {
			t.Errorf("unexpected path: %s (want %s)", r.URL.Path, wantPath)
		}
		w.Header().Set("Content-Type", "application/octet-stream")
		_, _ = w.Write(want)
	})

	got, err := c.Pipelines.RunInputFile(context.Background(), "daily-digest", "0123456789abcdef", 0)
	if err != nil {
		t.Fatalf("RunInputFile: %v", err)
	}
	if !bytes.Equal(got, want) {
		t.Fatalf("unexpected bytes: got %v, want %v", got, want)
	}
}

func TestProjectToolCalls_QueryEncoding(t *testing.T) {
	var gotQuery map[string][]string

	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/private/projects/acme/tool-calls" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		gotQuery = map[string][]string(r.URL.Query())
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"items":[{"id":"span1","traceId":"t1","runId":"r1","toolName":"gmail_send","pipeline":"p","project":"acme","provider":"anthropic","approval":{"mode":"auto","approvedBy":null,"approvedAt":null},"status":"error","timestamp":"2026-01-01T00:00:00Z","inputPreview":"","inputTruncated":false,"outputPreview":"","outputTruncated":false}],"nextCursor":{"beforeCreatedAt":"2026-01-01T00:00:00Z","beforeId":"span1"},"capped":false}`))
	})

	res, err := c.Pipelines.ProjectToolCalls(context.Background(), "acme", &ProjectToolCallsParams{
		Limit:           50,
		Tool:            "gmail_send",
		Agent:           "researcher",
		Status:          "error",
		ConnectorSource: "project",
		Approval:        "by:alice",
		Provider:        "anthropic",
		Sort:            "slowest",
		Search:          "invoice",
	})
	if err != nil {
		t.Fatalf("ProjectToolCalls: %v", err)
	}

	expect := map[string]string{
		"limit":           "50",
		"tool":            "gmail_send",
		"agent":           "researcher",
		"status":          "error",
		"connectorSource": "project",
		"approval":        "by:alice",
		"provider":        "anthropic",
		"sort":            "slowest",
		"search":          "invoice",
	}
	for k, want := range expect {
		got := ""
		if vs := gotQuery[k]; len(vs) > 0 {
			got = vs[0]
		}
		if got != want {
			t.Errorf("query param %q: got %q, want %q", k, got, want)
		}
	}

	if len(res.Items) != 1 || res.Items[0].ToolName != "gmail_send" {
		t.Fatalf("unexpected items: %+v", res.Items)
	}
	if res.NextCursor == nil || res.NextCursor.BeforeID != "span1" {
		t.Fatalf("unexpected nextCursor: %+v", res.NextCursor)
	}
}

func TestApplyPersonal_BridgedPostWithPathParamAndBody(t *testing.T) {
	var gotBody map[string]interface{}

	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Errorf("expected POST, got %s", r.Method)
		}
		wantPath := "/api/v1/private/projects/acme/connectors/google/apply-personal"
		if r.URL.Path != wantPath {
			t.Errorf("unexpected path: %s (want %s)", r.URL.Path, wantPath)
		}
		body, _ := io.ReadAll(r.Body)
		if err := json.Unmarshal(body, &gotBody); err != nil {
			t.Fatalf("unmarshal request body: %v", err)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"success":true,"service":"google","applied":["access_token","refresh_token"]}`))
	})

	res, err := c.Connectors.ApplyPersonal(context.Background(), "acme", "google", []string{"gmail", "calendar"})
	if err != nil {
		t.Fatalf("ApplyPersonal: %v", err)
	}
	if res["service"] != "google" {
		t.Errorf("unexpected result: %+v", res)
	}

	// project and service ride the URL path — they must NOT be duplicated in the body.
	if _, ok := gotBody["project"]; ok {
		t.Errorf("project should not appear in the request body (path param), got %+v", gotBody)
	}
	if _, ok := gotBody["service"]; ok {
		t.Errorf("service should not appear in the request body (path param), got %+v", gotBody)
	}
	caps, ok := gotBody["googleCapabilities"].([]interface{})
	if !ok || len(caps) != 2 || caps[0] != "gmail" || caps[1] != "calendar" {
		t.Fatalf("expected googleCapabilities in body, got %+v", gotBody)
	}
}

func TestGoogleDisconnect_DeleteWithJSONBody(t *testing.T) {
	var gotBody map[string]interface{}
	var gotMethod string

	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		gotMethod = r.Method
		if r.URL.Path != "/api/v1/private/credentials/google/access" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		body, err := io.ReadAll(r.Body)
		if err != nil {
			t.Fatalf("read body: %v", err)
		}
		if len(body) == 0 {
			t.Fatalf("expected a non-empty JSON body on the DELETE request")
		}
		if err := json.Unmarshal(body, &gotBody); err != nil {
			t.Fatalf("unmarshal request body: %v", err)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"success":true,"removedAccount":true}`))
	})

	ok, err := c.Credentials.GoogleDisconnect(context.Background(), "507f1f77bcf86cd799439011", "gmail")
	if err != nil {
		t.Fatalf("GoogleDisconnect: %v", err)
	}
	if gotMethod != http.MethodDelete {
		t.Errorf("expected DELETE, got %s", gotMethod)
	}
	if !ok {
		t.Errorf("expected ok=true")
	}
	if gotBody["accountId"] != "507f1f77bcf86cd799439011" {
		t.Errorf("unexpected accountId in body: %+v", gotBody)
	}
	if gotBody["capability"] != "gmail" {
		t.Errorf("unexpected capability in body: %+v", gotBody)
	}
}
