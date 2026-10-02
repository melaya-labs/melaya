// Unit tests for connector accounts (personal + project), platform API key
// management, pipeline docs previews / test retrieval, SetInputs and
// RunMessages (mock HTTP transport via httptest.Server, no live network
// calls). Each test checks the method, the exact escaped path on the wire,
// the query and the JSON body the SDK sends, and that the response decodes.
package melaya

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"reflect"
	"strings"
	"testing"
)

// wireCapture records one request exactly as it went over the wire.
type wireCapture struct {
	method  string
	rawPath string // escaped path as sent (from RequestURI, query stripped)
	query   map[string][]string
	body    interface{}
	hasBody bool
}

func wireServer(t *testing.T, resp string) (*Client, *wireCapture) {
	t.Helper()
	got := &wireCapture{}
	c := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		got.method = r.Method
		got.rawPath = r.RequestURI
		if i := strings.IndexByte(got.rawPath, '?'); i >= 0 {
			got.rawPath = got.rawPath[:i]
		}
		got.query = map[string][]string(r.URL.Query())
		if raw, _ := io.ReadAll(r.Body); len(raw) > 0 {
			got.hasBody = true
			if err := json.Unmarshal(raw, &got.body); err != nil {
				t.Errorf("request body is not JSON: %v (%q)", err, raw)
			}
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(resp))
	})
	return c, got
}

func (g *wireCapture) expect(t *testing.T, method, rawPath string) {
	t.Helper()
	if g.method != method {
		t.Errorf("expected %s, got %s", method, g.method)
	}
	if g.rawPath != rawPath {
		t.Errorf("expected path %s, got %s", rawPath, g.rawPath)
	}
}

func (g *wireCapture) expectBody(t *testing.T, want string) {
	t.Helper()
	var w interface{}
	if err := json.Unmarshal([]byte(want), &w); err != nil {
		t.Fatalf("bad want JSON: %v", err)
	}
	if !reflect.DeepEqual(g.body, w) {
		t.Errorf("expected body %s, got %#v", want, g.body)
	}
}

func (g *wireCapture) expectNoBody(t *testing.T) {
	t.Helper()
	if g.hasBody {
		t.Errorf("expected no body, got %#v", g.body)
	}
}

func (g *wireCapture) expectQuery(t *testing.T, want map[string]string) {
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

const accountsResp = `[{"id":"current","label":"Main shop","isDefault":true,"createdAt":null},{"id":"a1","label":"EU shop","isDefault":false,"createdAt":"2026-09-30T10:00:00.000Z"}]`

func checkAccounts(t *testing.T, res []ConnectorAccount, err error) {
	t.Helper()
	if err != nil {
		t.Fatalf("call: %v", err)
	}
	if len(res) != 2 {
		t.Fatalf("expected 2 accounts, got %d", len(res))
	}
	if res[0].ID != "current" || !res[0].IsDefault || res[0].CreatedAt != nil || res[0].Label != "Main shop" {
		t.Errorf("bad first account: %+v", res[0])
	}
	if res[1].CreatedAt == nil || *res[1].CreatedAt != "2026-09-30T10:00:00.000Z" || res[1].IsDefault {
		t.Errorf("bad second account: %+v", res[1])
	}
}

// ── Personal connector accounts ─────────────────────────────────────────────

func TestCredentialsAccounts(t *testing.T) {
	c, got := wireServer(t, accountsResp)
	res, err := c.Credentials.Accounts(context.Background(), "my shop/eu")
	checkAccounts(t, res, err)
	got.expect(t, http.MethodGet, "/api/v1/private/credentials/my%20shop%2Feu/accounts")
	got.expectNoBody(t)
}

func TestCredentialsAddAccount(t *testing.T) {
	c, got := wireServer(t, accountsResp)
	yes := true
	res, err := c.Credentials.AddAccount(context.Background(), "my shop", AddConnectorAccountInput{
		Label:        "EU shop",
		Fields:       map[string]string{"API_KEY": "k"},
		CurrentLabel: "Main shop",
		MakeDefault:  &yes,
	})
	checkAccounts(t, res, err)
	got.expect(t, http.MethodPost, "/api/v1/private/credentials/my%20shop/accounts")
	got.expectBody(t, `{"label":"EU shop","fields":{"API_KEY":"k"},"currentLabel":"Main shop","makeDefault":true}`)
}

func TestCredentialsAddAccount_OmitsAbsentOptionals(t *testing.T) {
	c, got := wireServer(t, accountsResp)
	_, err := c.Credentials.AddAccount(context.Background(), "shopify", AddConnectorAccountInput{
		Fields: map[string]string{"API_KEY": "k"},
	})
	if err != nil {
		t.Fatal(err)
	}
	got.expectBody(t, `{"fields":{"API_KEY":"k"}}`)
}

func TestCredentialsSetDefaultAccount(t *testing.T) {
	c, got := wireServer(t, accountsResp)
	res, err := c.Credentials.SetDefaultAccount(context.Background(), "my shop", "a/1")
	checkAccounts(t, res, err)
	got.expect(t, http.MethodPut, "/api/v1/private/credentials/my%20shop/accounts/default")
	got.expectBody(t, `{"accountId":"a/1"}`)
}

func TestCredentialsIdentifyAccount(t *testing.T) {
	c, got := wireServer(t, accountsResp)
	res, err := c.Credentials.IdentifyAccount(context.Background(), "my shop", "a 1/x")
	checkAccounts(t, res, err)
	got.expect(t, http.MethodPost, "/api/v1/private/credentials/my%20shop/accounts/a%201%2Fx/identify")
	got.expectBody(t, `{}`)
}

func TestCredentialsRenameAccount(t *testing.T) {
	c, got := wireServer(t, accountsResp)
	res, err := c.Credentials.RenameAccount(context.Background(), "my shop", "a/1", "EU shop")
	checkAccounts(t, res, err)
	got.expect(t, http.MethodPut, "/api/v1/private/credentials/my%20shop/accounts/a%2F1")
	got.expectBody(t, `{"label":"EU shop"}`)
}

func TestCredentialsRemoveAccount(t *testing.T) {
	c, got := wireServer(t, accountsResp)
	res, err := c.Credentials.RemoveAccount(context.Background(), "my shop", "a 1")
	checkAccounts(t, res, err)
	got.expect(t, http.MethodDelete, "/api/v1/private/credentials/my%20shop/accounts/a%201")
	got.expectNoBody(t)
}

// ── Project connector accounts ──────────────────────────────────────────────

func TestConnectorsAccounts(t *testing.T) {
	c, got := wireServer(t, accountsResp)
	res, err := c.Connectors.Accounts(context.Background(), "acme corp", "my/shop")
	checkAccounts(t, res, err)
	got.expect(t, http.MethodGet, "/api/v1/private/projects/acme%20corp/connectors/my%2Fshop/accounts")
	got.expectNoBody(t)
}

func TestConnectorsAddAccount(t *testing.T) {
	c, got := wireServer(t, accountsResp)
	no := false
	res, err := c.Connectors.AddAccount(context.Background(), "acme corp", "shopify", AddConnectorAccountInput{
		Label:       "EU shop",
		Fields:      map[string]string{"API_KEY": "k", "SHOP": "eu"},
		MakeDefault: &no,
	})
	checkAccounts(t, res, err)
	got.expect(t, http.MethodPost, "/api/v1/private/projects/acme%20corp/connectors/shopify/accounts")
	got.expectBody(t, `{"label":"EU shop","fields":{"API_KEY":"k","SHOP":"eu"},"makeDefault":false}`)
}

func TestConnectorsSetDefaultAccount(t *testing.T) {
	c, got := wireServer(t, accountsResp)
	res, err := c.Connectors.SetDefaultAccount(context.Background(), "acme corp", "shopify", "a1")
	checkAccounts(t, res, err)
	got.expect(t, http.MethodPut, "/api/v1/private/projects/acme%20corp/connectors/shopify/accounts/default")
	got.expectBody(t, `{"accountId":"a1"}`)
}

func TestConnectorsRenameAccount(t *testing.T) {
	c, got := wireServer(t, accountsResp)
	res, err := c.Connectors.RenameAccount(context.Background(), "acme corp", "shopify", "a/1", "EU")
	checkAccounts(t, res, err)
	got.expect(t, http.MethodPut, "/api/v1/private/projects/acme%20corp/connectors/shopify/accounts/a%2F1")
	got.expectBody(t, `{"label":"EU"}`)
}

func TestConnectorsRemoveAccount(t *testing.T) {
	c, got := wireServer(t, accountsResp)
	res, err := c.Connectors.RemoveAccount(context.Background(), "acme/corp", "shopify", "a 1")
	checkAccounts(t, res, err)
	got.expect(t, http.MethodDelete, "/api/v1/private/projects/acme%2Fcorp/connectors/shopify/accounts/a%201")
	got.expectNoBody(t)
}

// ── Platform API key ────────────────────────────────────────────────────────

func TestAccountRotateAPIKey(t *testing.T) {
	c, got := wireServer(t, `{"apiKey":"mk_new_key"}`)
	res, err := c.Account.RotateAPIKey(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	got.expect(t, http.MethodPost, "/api/v1/private/api-key")
	got.expectBody(t, `{}`)
	if res.APIKey != "mk_new_key" {
		t.Errorf("expected mk_new_key, got %q", res.APIKey)
	}
}

func TestAccountRevokeAPIKey(t *testing.T) {
	c, got := wireServer(t, `{"ok":true}`)
	ok, err := c.Account.RevokeAPIKey(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	got.expect(t, http.MethodDelete, "/api/v1/private/api-key")
	got.expectNoBody(t)
	if !ok {
		t.Error("expected ok=true")
	}
}

func TestAccountAPIKeyUsage(t *testing.T) {
	c, got := wireServer(t, `{"requests":42}`)
	res, err := c.Account.APIKeyUsage(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	got.expect(t, http.MethodGet, "/api/v1/private/api-key/usage")
	got.expectQuery(t, map[string]string{})
	if res["requests"] != float64(42) {
		t.Errorf("bad usage: %v", res)
	}
}

// ── Pipeline docs previews / test retrieval ─────────────────────────────────

func TestPipelinesDocsPreview(t *testing.T) {
	c, got := wireServer(t, `{"docs":[]}`)
	_, err := c.Pipelines.DocsPreview(context.Background(), "my pipe/v2", &DocsPreviewOptions{ModelName: "qwen3.7-plus", ModelProvider: "qwen"})
	if err != nil {
		t.Fatal(err)
	}
	got.expect(t, http.MethodGet, "/api/v1/private/pipelines/my%20pipe%2Fv2/docs/preview")
	got.expectQuery(t, map[string]string{"model_name": "qwen3.7-plus", "model_provider": "qwen"})
}

func TestPipelinesDocsPreview_NoOptions(t *testing.T) {
	c, got := wireServer(t, `{"docs":[]}`)
	if _, err := c.Pipelines.DocsPreview(context.Background(), "my pipe", nil); err != nil {
		t.Fatal(err)
	}
	got.expect(t, http.MethodGet, "/api/v1/private/pipelines/my%20pipe/docs/preview")
	got.expectQuery(t, map[string]string{})
}

func TestPipelinesRetrievalPreview(t *testing.T) {
	c, got := wireServer(t, `{"documents":3,"chunks":40}`)
	res, err := c.Pipelines.RetrievalPreview(context.Background(), "my pipe/v2")
	if err != nil {
		t.Fatal(err)
	}
	got.expect(t, http.MethodGet, "/api/v1/private/pipelines/my%20pipe%2Fv2/docs/retrieval/preview")
	got.expectNoBody(t)
	if m, ok := res.(map[string]interface{}); !ok || m["chunks"] != float64(40) {
		t.Errorf("bad preview: %v", res)
	}
}

func TestPipelinesTestRetrieve(t *testing.T) {
	c, got := wireServer(t, `{"passages":[]}`)
	if _, err := c.Pipelines.TestRetrieve(context.Background(), "my pipe", "refund policy", 7); err != nil {
		t.Fatal(err)
	}
	got.expect(t, http.MethodPost, "/api/v1/private/pipelines/my%20pipe/docs/retrieval/test_retrieve")
	got.expectBody(t, `{"query":"refund policy","limit":7}`)
}

func TestPipelinesTestRetrieve_OmitsLimit(t *testing.T) {
	c, got := wireServer(t, `{"passages":[]}`)
	if _, err := c.Pipelines.TestRetrieve(context.Background(), "a/b", "q", 0); err != nil {
		t.Fatal(err)
	}
	got.expect(t, http.MethodPost, "/api/v1/private/pipelines/a%2Fb/docs/retrieval/test_retrieve")
	got.expectBody(t, `{"query":"q"}`)
}

// ── SetInputs / RunMessages ─────────────────────────────────────────────────

func TestPipelinesSetInputs(t *testing.T) {
	c, got := wireServer(t, `{"name":"my pipe/v2","inputs":[{"key":"brief","label":"Brief","type":"text","required":true}]}`)
	res, err := c.Pipelines.SetInputs(context.Background(), "my pipe/v2", "acme", []PipelineInputDeclaration{
		{Key: "brief", Label: "Brief", Type: "text", Required: true},
	})
	if err != nil {
		t.Fatal(err)
	}
	got.expect(t, http.MethodPut, "/api/v1/private/pipelines/my%20pipe%2Fv2/inputs")
	got.expectBody(t, `{"inputs":[{"key":"brief","label":"Brief","type":"text","required":true}],"project":"acme"}`)
	if res.Name != "my pipe/v2" || len(res.Inputs) != 1 || res.Inputs[0].Key != "brief" {
		t.Errorf("bad result: %+v", res)
	}
}

func TestPipelinesSetInputs_NilClearsAll(t *testing.T) {
	c, got := wireServer(t, `{"name":"p","inputs":[]}`)
	if _, err := c.Pipelines.SetInputs(context.Background(), "p", "acme", nil); err != nil {
		t.Fatal(err)
	}
	got.expectBody(t, `{"inputs":[],"project":"acme"}`)
}

func TestHitlRunMessages(t *testing.T) {
	c, got := wireServer(t, `[{"id":"m1","runId":"r 1","role":"assistant","content":"hi"}]`)
	limit := 20
	res, err := c.Hitl.RunMessages(context.Background(), "r 1/x", &limit, "cur1")
	if err != nil {
		t.Fatal(err)
	}
	got.expect(t, http.MethodGet, "/api/v1/private/runs/r%201%2Fx/messages")
	got.expectQuery(t, map[string]string{"limit": "20", "cursor": "cur1"})
	if len(res) != 1 || res[0].Content != "hi" {
		t.Errorf("bad messages: %+v", res)
	}
}
