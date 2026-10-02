//! Integration tests for connector accounts (personal + project), platform
//! API key rotation/revocation/usage, docs/retrieval previews, `set_inputs`,
//! `run_messages` and `usage_summary`.
//!
//! Same mock transport as `tests/sdk_0_3.rs`: a one-shot local TCP server
//! stands in for the Melaya API (no HTTP-mocking crate). Each test points a
//! `Melaya` client at it, makes one call, and asserts on the method, path
//! (including URL-encoding of a value with a space), query and JSON body the
//! server captured.

use melaya::{Melaya, MelayaOptions};
use serde_json::{json, Value};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};

// ── mock server ──────────────────────────────────────────────────────────────

/// Start a one-shot mock HTTP server replying 200 with `body` (JSON).
/// Returns the base URL and a handle resolving to the raw request bytes.
async fn mock_once(body: &'static str) -> (String, tokio::task::JoinHandle<Vec<u8>>) {
    let listener = TcpListener::bind("127.0.0.1:0")
        .await
        .expect("bind mock listener");
    let addr = listener.local_addr().expect("local_addr");
    let handle = tokio::spawn(async move {
        let (mut socket, _) = listener.accept().await.expect("accept mock connection");
        let request = read_full_request(&mut socket).await;
        let response = format!(
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
            body.len(),
        );
        let _ = socket.write_all(response.as_bytes()).await;
        let _ = socket.shutdown().await;
        request
    });
    (format!("http://{addr}"), handle)
}

/// Read one full HTTP/1.1 request (headers + `Content-Length` body).
async fn read_full_request(socket: &mut TcpStream) -> Vec<u8> {
    let mut buf = Vec::new();
    let mut chunk = [0u8; 8192];
    loop {
        let n = socket.read(&mut chunk).await.expect("read mock request");
        if n == 0 {
            break;
        }
        buf.extend_from_slice(&chunk[..n]);
        if let Some(header_end) = find_subslice(&buf, b"\r\n\r\n") {
            let headers = String::from_utf8_lossy(&buf[..header_end]);
            let content_length: usize = headers
                .lines()
                .find_map(|l| {
                    l.to_ascii_lowercase()
                        .strip_prefix("content-length:")
                        .map(|v| v.trim().to_owned())
                })
                .and_then(|v| v.parse().ok())
                .unwrap_or(0);
            if buf.len() - (header_end + 4) >= content_length {
                break;
            }
        }
    }
    buf
}

fn find_subslice(haystack: &[u8], needle: &[u8]) -> Option<usize> {
    haystack.windows(needle.len()).position(|w| w == needle)
}

/// A captured request: method, path (without query), query string, JSON body.
struct Captured {
    method: String,
    path: String,
    query: String,
    body: Option<Value>,
}

async fn captured(handle: tokio::task::JoinHandle<Vec<u8>>) -> Captured {
    let raw = handle.await.expect("mock server task");
    let header_end = find_subslice(&raw, b"\r\n\r\n").expect("request header terminator");
    let head = String::from_utf8_lossy(&raw[..header_end]).to_string();
    let body_bytes = &raw[header_end + 4..];
    let request_line = head.split("\r\n").next().unwrap_or("").to_owned();
    let mut parts = request_line.split(' ');
    let method = parts.next().unwrap_or("").to_owned();
    let target = parts.next().unwrap_or("").to_owned();
    let (path, query) = match target.split_once('?') {
        Some((p, q)) => (p.to_owned(), q.to_owned()),
        None => (target, String::new()),
    };
    let body = if body_bytes.is_empty() {
        None
    } else {
        Some(serde_json::from_slice(body_bytes).expect("request body should be JSON"))
    };
    Captured {
        method,
        path,
        query,
        body,
    }
}

fn client_at(base_url: &str) -> Melaya {
    let opts = MelayaOptions {
        api_key: "mk_test_0000000000000000000000".to_owned(),
        base_url: Some(base_url.to_owned()),
        ws_url: None,
    };
    Melaya::with_options(opts).expect("construct test client")
}

const ACCOUNTS: &str = r#"[{"id":"a1","label":"Work","isDefault":true,"createdAt":"2026-10-01T00:00:00Z"},{"id":"current","label":"Old","isDefault":false,"createdAt":null}]"#;

// ── Personal connector accounts ──────────────────────────────────────────────

#[tokio::test]
async fn credentials_accounts_gets_list_with_encoded_service() {
    let (url, h) = mock_once(ACCOUNTS).await;
    let r = client_at(&url)
        .credentials
        .accounts("my service")
        .await
        .expect("accounts");
    assert_eq!(r[0]["isDefault"], json!(true));
    assert_eq!(r[1]["createdAt"], Value::Null);
    let c = captured(h).await;
    assert_eq!(c.method, "GET");
    assert_eq!(c.path, "/api/v1/private/credentials/my%20service/accounts");
    assert!(c.body.is_none());
}

#[tokio::test]
async fn credentials_add_account_posts_full_body() {
    let (url, h) = mock_once(ACCOUNTS).await;
    client_at(&url)
        .credentials
        .add_account(
            "zoho_mail",
            &json!({ "email": "a@b.c", "password": "pw" }),
            Some("Work"),
            Some("Old"),
            Some(true),
        )
        .await
        .expect("add_account");
    let c = captured(h).await;
    assert_eq!(c.method, "POST");
    assert_eq!(c.path, "/api/v1/private/credentials/zoho_mail/accounts");
    assert_eq!(
        c.body.unwrap(),
        json!({
            "label": "Work",
            "fields": { "email": "a@b.c", "password": "pw" },
            "currentLabel": "Old",
            "makeDefault": true
        })
    );
}

#[tokio::test]
async fn credentials_add_account_omits_none_fields() {
    let (url, h) = mock_once(ACCOUNTS).await;
    client_at(&url)
        .credentials
        .add_account("shop x", &json!({ "token": "t" }), None, None, None)
        .await
        .expect("add_account");
    let c = captured(h).await;
    assert_eq!(c.method, "POST");
    assert_eq!(c.path, "/api/v1/private/credentials/shop%20x/accounts");
    assert_eq!(c.body.unwrap(), json!({ "fields": { "token": "t" } }));
}

#[tokio::test]
async fn credentials_set_default_account_puts_account_id() {
    let (url, h) = mock_once(ACCOUNTS).await;
    client_at(&url)
        .credentials
        .set_default_account("zoho_mail", "a1")
        .await
        .expect("set_default_account");
    let c = captured(h).await;
    assert_eq!(c.method, "PUT");
    assert_eq!(
        c.path,
        "/api/v1/private/credentials/zoho_mail/accounts/default"
    );
    assert_eq!(c.body.unwrap(), json!({ "accountId": "a1" }));
}

#[tokio::test]
async fn credentials_identify_account_posts_empty_body() {
    let (url, h) = mock_once(ACCOUNTS).await;
    client_at(&url)
        .credentials
        .identify_account("zoho_mail", "acc 1")
        .await
        .expect("identify_account");
    let c = captured(h).await;
    assert_eq!(c.method, "POST");
    assert_eq!(
        c.path,
        "/api/v1/private/credentials/zoho_mail/accounts/acc%201/identify"
    );
    assert_eq!(c.body.unwrap(), json!({}));
}

#[tokio::test]
async fn credentials_rename_account_puts_label() {
    let (url, h) = mock_once(ACCOUNTS).await;
    client_at(&url)
        .credentials
        .rename_account("zoho_mail", "a1", "Personal box")
        .await
        .expect("rename_account");
    let c = captured(h).await;
    assert_eq!(c.method, "PUT");
    assert_eq!(c.path, "/api/v1/private/credentials/zoho_mail/accounts/a1");
    assert_eq!(c.body.unwrap(), json!({ "label": "Personal box" }));
}

#[tokio::test]
async fn credentials_remove_account_deletes() {
    let (url, h) = mock_once("[]").await;
    client_at(&url)
        .credentials
        .remove_account("zoho mail", "a 1")
        .await
        .expect("remove_account");
    let c = captured(h).await;
    assert_eq!(c.method, "DELETE");
    assert_eq!(
        c.path,
        "/api/v1/private/credentials/zoho%20mail/accounts/a%201"
    );
    assert!(c.body.is_none());
}

// ── Project connector accounts ───────────────────────────────────────────────

#[tokio::test]
async fn connectors_accounts_gets_list_with_encoded_segments() {
    let (url, h) = mock_once(ACCOUNTS).await;
    let r = client_at(&url)
        .connectors
        .accounts("Acme Corp", "zoho_mail")
        .await
        .expect("accounts");
    assert_eq!(r[0]["id"], json!("a1"));
    let c = captured(h).await;
    assert_eq!(c.method, "GET");
    assert_eq!(
        c.path,
        "/api/v1/private/projects/Acme%20Corp/connectors/zoho_mail/accounts"
    );
}

#[tokio::test]
async fn connectors_add_account_posts_body() {
    let (url, h) = mock_once(ACCOUNTS).await;
    client_at(&url)
        .connectors
        .add_account(
            "acme",
            "shopify",
            &json!({ "shop": "x", "token": "t" }),
            Some("Shop 2"),
            None,
            Some(false),
        )
        .await
        .expect("add_account");
    let c = captured(h).await;
    assert_eq!(c.method, "POST");
    assert_eq!(
        c.path,
        "/api/v1/private/projects/acme/connectors/shopify/accounts"
    );
    assert_eq!(
        c.body.unwrap(),
        json!({
            "label": "Shop 2",
            "fields": { "shop": "x", "token": "t" },
            "makeDefault": false
        })
    );
}

#[tokio::test]
async fn connectors_set_default_account_puts_account_id() {
    let (url, h) = mock_once(ACCOUNTS).await;
    client_at(&url)
        .connectors
        .set_default_account("acme", "shopify", "a2")
        .await
        .expect("set_default_account");
    let c = captured(h).await;
    assert_eq!(c.method, "PUT");
    assert_eq!(
        c.path,
        "/api/v1/private/projects/acme/connectors/shopify/accounts/default"
    );
    assert_eq!(c.body.unwrap(), json!({ "accountId": "a2" }));
}

#[tokio::test]
async fn connectors_rename_account_puts_label() {
    let (url, h) = mock_once(ACCOUNTS).await;
    client_at(&url)
        .connectors
        .rename_account("acme", "shopify", "a 2", "EU shop")
        .await
        .expect("rename_account");
    let c = captured(h).await;
    assert_eq!(c.method, "PUT");
    assert_eq!(
        c.path,
        "/api/v1/private/projects/acme/connectors/shopify/accounts/a%202"
    );
    assert_eq!(c.body.unwrap(), json!({ "label": "EU shop" }));
}

#[tokio::test]
async fn connectors_remove_account_deletes() {
    let (url, h) = mock_once("[]").await;
    client_at(&url)
        .connectors
        .remove_account("acme", "shopify", "a2")
        .await
        .expect("remove_account");
    let c = captured(h).await;
    assert_eq!(c.method, "DELETE");
    assert_eq!(
        c.path,
        "/api/v1/private/projects/acme/connectors/shopify/accounts/a2"
    );
    assert!(c.body.is_none());
}

// ── Platform API key ─────────────────────────────────────────────────────────

#[tokio::test]
async fn account_rotate_api_key_posts_empty_body() {
    let (url, h) = mock_once(r#"{"apiKey":"mk_new"}"#).await;
    let r = client_at(&url)
        .account
        .rotate_api_key()
        .await
        .expect("rotate_api_key");
    assert_eq!(r["apiKey"], json!("mk_new"));
    let c = captured(h).await;
    assert_eq!(c.method, "POST");
    assert_eq!(c.path, "/api/v1/private/api-key");
    assert_eq!(c.body.unwrap(), json!({}));
}

#[tokio::test]
async fn account_revoke_api_key_deletes() {
    let (url, h) = mock_once(r#"{"ok":true}"#).await;
    let r = client_at(&url)
        .account
        .revoke_api_key()
        .await
        .expect("revoke_api_key");
    assert_eq!(r["ok"], json!(true));
    let c = captured(h).await;
    assert_eq!(c.method, "DELETE");
    assert_eq!(c.path, "/api/v1/private/api-key");
}

#[tokio::test]
async fn account_api_key_usage_gets() {
    let (url, h) = mock_once(r#"{"requests":42}"#).await;
    let r = client_at(&url)
        .account
        .api_key_usage()
        .await
        .expect("api_key_usage");
    assert_eq!(r["requests"], json!(42));
    let c = captured(h).await;
    assert_eq!(c.method, "GET");
    assert_eq!(c.path, "/api/v1/private/api-key/usage");
}

// ── Pipelines: docs / retrieval previews ─────────────────────────────────────

#[tokio::test]
async fn pipelines_docs_preview_sends_model_query() {
    let (url, h) = mock_once(r#"{"files":[]}"#).await;
    client_at(&url)
        .pipelines
        .docs_preview("my pipe", Some("qwen3.7-plus"), Some("qwen"))
        .await
        .expect("docs_preview");
    let c = captured(h).await;
    assert_eq!(c.method, "GET");
    assert_eq!(c.path, "/api/v1/private/pipelines/my%20pipe/docs/preview");
    let mut pairs: Vec<&str> = c.query.split('&').collect();
    pairs.sort_unstable();
    assert_eq!(
        pairs,
        vec!["model_name=qwen3.7-plus", "model_provider=qwen"]
    );
}

#[tokio::test]
async fn pipelines_docs_preview_omits_none_query() {
    let (url, h) = mock_once(r#"{"files":[]}"#).await;
    client_at(&url)
        .pipelines
        .docs_preview("demo", None, None)
        .await
        .expect("docs_preview");
    let c = captured(h).await;
    assert_eq!(c.path, "/api/v1/private/pipelines/demo/docs/preview");
    assert_eq!(c.query, "");
}

#[tokio::test]
async fn pipelines_retrieval_preview_gets() {
    let (url, h) = mock_once(r#"{"chunks":3}"#).await;
    client_at(&url)
        .pipelines
        .retrieval_preview("my pipe")
        .await
        .expect("retrieval_preview");
    let c = captured(h).await;
    assert_eq!(c.method, "GET");
    assert_eq!(
        c.path,
        "/api/v1/private/pipelines/my%20pipe/docs/retrieval/preview"
    );
}

#[tokio::test]
async fn pipelines_test_retrieve_posts_query_and_limit() {
    let (url, h) = mock_once(r#"{"passages":[]}"#).await;
    client_at(&url)
        .pipelines
        .test_retrieve("my pipe", "refund policy", Some(3))
        .await
        .expect("test_retrieve");
    let c = captured(h).await;
    assert_eq!(c.method, "POST");
    assert_eq!(
        c.path,
        "/api/v1/private/pipelines/my%20pipe/docs/retrieval/test_retrieve"
    );
    assert_eq!(
        c.body.unwrap(),
        json!({ "query": "refund policy", "limit": 3 })
    );
}

#[tokio::test]
async fn pipelines_test_retrieve_omits_limit() {
    let (url, h) = mock_once(r#"{"passages":[]}"#).await;
    client_at(&url)
        .pipelines
        .test_retrieve("demo", "q", None)
        .await
        .expect("test_retrieve");
    let c = captured(h).await;
    assert_eq!(c.body.unwrap(), json!({ "query": "q" }));
}

// ── Already-ported surface: set_inputs, run_messages, usage_summary ──────────

#[tokio::test]
async fn pipelines_set_inputs_puts_inputs_and_project() {
    let (url, h) = mock_once(r#"{"name":"my pipe","inputs":[]}"#).await;
    let inputs = json!([{ "key": "topic", "label": "Topic", "type": "text", "required": true }]);
    client_at(&url)
        .pipelines
        .set_inputs("my pipe", "acme", &inputs)
        .await
        .expect("set_inputs");
    let c = captured(h).await;
    assert_eq!(c.method, "PUT");
    assert_eq!(c.path, "/api/v1/private/pipelines/my%20pipe/inputs");
    assert_eq!(
        c.body.unwrap(),
        json!({ "inputs": inputs, "project": "acme" })
    );
}

#[tokio::test]
async fn hitl_run_messages_uses_runs_path_encoded() {
    let (url, h) = mock_once(r#"{"messages":[]}"#).await;
    client_at(&url)
        .hitl
        .run_messages("run 1", Some(50), None)
        .await
        .expect("run_messages");
    let c = captured(h).await;
    assert_eq!(c.method, "GET");
    assert_eq!(c.path, "/api/v1/private/runs/run%201/messages");
    assert_eq!(c.query, "limit=50");
}

#[tokio::test]
async fn pipelines_usage_summary_gets_overview_usage() {
    let (url, h) = mock_once(r#"{"pipelines":4}"#).await;
    let r = client_at(&url)
        .pipelines
        .usage_summary()
        .await
        .expect("usage_summary");
    assert_eq!(r["pipelines"], json!(4));
    let c = captured(h).await;
    assert_eq!(c.method, "GET");
    assert_eq!(c.path, "/api/v1/private/overview/usage");
}
