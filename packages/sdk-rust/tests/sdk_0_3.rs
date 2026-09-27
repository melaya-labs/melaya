//! Integration tests for the SDK 0.3 surface additions.
//!
//! Mock transport: a tiny one-shot local TCP server stands in for the
//! Melaya API (no HTTP-mocking crate is added as a dependency — the SDK
//! itself adds none, per its no-new-dependencies constraint, and neither
//! does its test suite). Each test spins up its own server, points a
//! `Melaya` client at it via `MelayaOptions::base_url`, makes one call, and
//! asserts on both the parsed response and the raw request bytes the
//! server captured.

use std::collections::HashMap as StdHashMap;

use melaya::{Melaya, MelayaOptions, PipelineRunOptions, RunInputs};
use serde_json::{json, Value};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};

// ── mock server ──────────────────────────────────────────────────────────────

/// Start a one-shot mock HTTP server on `127.0.0.1`: accepts exactly one
/// connection, captures the raw request bytes, replies with the given
/// status/body, then closes. Returns the base URL to point a client at, and
/// a join handle that resolves to the captured request bytes.
async fn mock_once(
    status: u16,
    content_type: &'static str,
    body: &'static str,
) -> (String, tokio::task::JoinHandle<Vec<u8>>) {
    mock_once_bytes(status, content_type, body.as_bytes().to_vec()).await
}

/// Same as [`mock_once`] but the response body is raw bytes (for the
/// `run_input_file` raw-bytes-download test).
async fn mock_once_bytes(
    status: u16,
    content_type: &'static str,
    body: Vec<u8>,
) -> (String, tokio::task::JoinHandle<Vec<u8>>) {
    let listener = TcpListener::bind("127.0.0.1:0")
        .await
        .expect("bind mock listener");
    let addr = listener.local_addr().expect("local_addr");
    let handle = tokio::spawn(async move {
        let (mut socket, _) = listener.accept().await.expect("accept mock connection");
        let request = read_full_request(&mut socket).await;
        let response = format!(
            "HTTP/1.1 {status} {}\r\nContent-Type: {content_type}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
            reason_phrase(status),
            body.len(),
        );
        let _ = socket.write_all(response.as_bytes()).await;
        let _ = socket.write_all(&body).await;
        let _ = socket.shutdown().await;
        request
    });
    (format!("http://{addr}"), handle)
}

fn reason_phrase(status: u16) -> &'static str {
    match status {
        200 => "OK",
        404 => "Not Found",
        _ => "OK",
    }
}

/// Read one full HTTP/1.1 request (headers + `Content-Length` body, if any)
/// off the socket.
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
                    let lower = l.to_ascii_lowercase();
                    lower
                        .strip_prefix("content-length:")
                        .map(|v| v.trim().to_owned())
                })
                .and_then(|v| v.parse().ok())
                .unwrap_or(0);
            let body_start = header_end + 4;
            if buf.len() - body_start >= content_length {
                break;
            }
        }
    }
    buf
}

fn find_subslice(haystack: &[u8], needle: &[u8]) -> Option<usize> {
    haystack.windows(needle.len()).position(|w| w == needle)
}

/// Split raw request bytes into (request line, lower-cased headers block,
/// body bytes).
fn split_request(raw: &[u8]) -> (String, String, Vec<u8>) {
    let header_end = find_subslice(raw, b"\r\n\r\n").expect("request header terminator");
    let head = String::from_utf8_lossy(&raw[..header_end]).to_string();
    let body = raw[header_end + 4..].to_vec();
    let request_line = head.split("\r\n").next().unwrap_or("").to_owned();
    (request_line, head.to_ascii_lowercase(), body)
}

fn client_at(base_url: &str) -> Melaya {
    let opts = MelayaOptions {
        api_key: "mk_test_0000000000000000000000".to_owned(),
        base_url: Some(base_url.to_owned()),
        ws_url: None,
    };
    Melaya::with_options(opts).expect("construct test client")
}

// ── 1. run() sends a run_inputs body ────────────────────────────────────────

#[tokio::test]
async fn run_sends_run_inputs_body_and_parses_accepted() {
    let (base_url, handle) = mock_once(
        200,
        "application/json",
        r#"{"ok":true,"run_id":"abc123","queued":true}"#,
    )
    .await;
    let m = client_at(&base_url);

    let mut values = StdHashMap::new();
    values.insert("report".to_owned(), json!({ "file_id": "f_123" }));
    let opts = PipelineRunOptions {
        project: Some("acme".into()),
        run_inputs: Some(RunInputs {
            brief: Some("Summarize the attached report".into()),
            values: Some(values),
        }),
        ..Default::default()
    };

    let accepted = m
        .pipelines
        .run("demo", Some(opts))
        .await
        .expect("run() should succeed");
    assert_eq!(accepted.run_id, "abc123");
    assert!(accepted.queued);

    let raw = handle.await.expect("mock server task");
    let (request_line, _headers, body) = split_request(&raw);
    assert!(
        request_line.starts_with("POST /api/v1/private/pipelines/demo/run"),
        "unexpected request line: {request_line}"
    );
    let parsed: Value = serde_json::from_slice(&body).expect("request body should be JSON");
    assert_eq!(
        parsed["run_inputs"]["brief"],
        json!("Summarize the attached report")
    );
    assert_eq!(
        parsed["run_inputs"]["values"]["report"]["file_id"],
        json!("f_123")
    );
    assert_eq!(parsed["project"], json!("acme"));
}

// ── 2. multipart upload ──────────────────────────────────────────────────────

#[tokio::test]
async fn upload_doc_sends_multipart_with_boundary_and_filename() {
    let (base_url, handle) = mock_once(200, "application/json", r#"{"ok":true}"#).await;
    let m = client_at(&base_url);

    m.pipelines
        .upload_doc(
            "demo",
            b"hello world",
            Some("notes.md"),
            Some("text/markdown"),
        )
        .await
        .expect("upload_doc should succeed");

    let raw = handle.await.expect("mock server task");
    let (request_line, headers, body) = split_request(&raw);
    assert!(
        request_line.starts_with("POST /api/v1/private/pipelines/demo/docs"),
        "unexpected request line: {request_line}"
    );

    let content_type_line = headers
        .lines()
        .find(|l| l.starts_with("content-type:"))
        .expect("content-type header present");
    assert!(
        content_type_line.contains("multipart/form-data")
            && content_type_line.contains("boundary="),
        "Content-Type should be multipart/form-data with a boundary: {content_type_line}"
    );
    let boundary = content_type_line
        .split("boundary=")
        .nth(1)
        .expect("boundary value present")
        .trim()
        .to_owned();

    let body_str = String::from_utf8_lossy(&body);
    assert!(
        body_str.contains(&format!("--{boundary}")),
        "body should contain the boundary marker"
    );
    assert!(
        body_str.contains(r#"name="file"; filename="notes.md""#),
        "body should contain the file part with the given filename"
    );
    assert!(body_str.contains("Content-Type: text/markdown"));
    assert!(
        body_str.contains("hello world"),
        "body should contain the file content"
    );
}

// ── 3. run_input_file returns raw bytes ─────────────────────────────────────

#[tokio::test]
async fn run_input_file_returns_raw_bytes() {
    // Bytes that would break naive JSON parsing (invalid UTF-8 + a bare
    // unterminated brace), to prove the client never tries to parse them.
    let payload: Vec<u8> = vec![0, 1, 2, 3, 255, 254, b'{', b'"', b'n', b'o'];
    let (base_url, handle) =
        mock_once_bytes(200, "application/octet-stream", payload.clone()).await;
    let m = client_at(&base_url);

    let bytes = m
        .pipelines
        .run_input_file("demo", "0123456789abcdef", 0)
        .await
        .expect("run_input_file should succeed");
    assert_eq!(bytes, payload);

    let raw = handle.await.expect("mock server task");
    let (request_line, _headers, _body) = split_request(&raw);
    assert!(
        request_line
            .starts_with("GET /api/v1/private/pipelines/demo/runs/0123456789abcdef/inputs/files/0"),
        "unexpected request line: {request_line}"
    );
}

// ── 4. projectToolCalls query encoding ──────────────────────────────────────

#[tokio::test]
async fn project_tool_calls_encodes_query_params() {
    let (base_url, handle) = mock_once(
        200,
        "application/json",
        r#"{"ok":true,"items":[],"nextCursor":null,"capped":false}"#,
    )
    .await;
    let m = client_at(&base_url);

    m.pipelines
        .project_tool_calls(
            "acme",
            Some("2026-01-01T00:00:00.000Z"),
            Some("span_9"),
            Some(10),
            Some("web_search"),
            None,
            None,
            Some("ok"),
            None,
            Some("project"),
            Some("auto"),
            None,
            Some("recent"),
        )
        .await
        .expect("project_tool_calls should succeed");

    let raw = handle.await.expect("mock server task");
    let (request_line, _headers, _body) = split_request(&raw);
    let path_and_query = request_line
        .split_whitespace()
        .nth(1)
        .expect("request path present");
    assert!(
        path_and_query.starts_with("/api/v1/private/projects/acme/tool-calls?"),
        "unexpected path: {path_and_query}"
    );

    let url = url::Url::parse(&format!("http://x{path_and_query}")).expect("parse request url");
    let pairs: StdHashMap<String, String> = url.query_pairs().into_owned().collect();
    assert_eq!(
        pairs.get("beforeCreatedAt").unwrap(),
        "2026-01-01T00:00:00.000Z"
    );
    assert_eq!(pairs.get("beforeId").unwrap(), "span_9");
    assert_eq!(pairs.get("limit").unwrap(), "10");
    assert_eq!(pairs.get("tool").unwrap(), "web_search");
    assert_eq!(pairs.get("status").unwrap(), "ok");
    assert_eq!(pairs.get("connectorSource").unwrap(), "project");
    assert_eq!(pairs.get("approval").unwrap(), "auto");
    assert_eq!(pairs.get("sort").unwrap(), "recent");
    assert!(
        !pairs.contains_key("agent"),
        "unset params should be omitted"
    );
    assert!(
        !pairs.contains_key("runId"),
        "unset params should be omitted"
    );
    assert!(
        !pairs.contains_key("search"),
        "unset params should be omitted"
    );
    assert!(
        !pairs.contains_key("provider"),
        "unset params should be omitted"
    );
}

// ── 5. bridged POST: path param + body (applyPersonal) ──────────────────────

#[tokio::test]
async fn apply_personal_sends_path_param_and_json_body() {
    let (base_url, handle) = mock_once(200, "application/json", r#"{"ok":true}"#).await;
    let m = client_at(&base_url);

    m.connectors
        .apply_personal("acme", "google", Some(&["gmail", "calendar"]))
        .await
        .expect("apply_personal should succeed");

    let raw = handle.await.expect("mock server task");
    let (request_line, _headers, body) = split_request(&raw);
    assert!(
        request_line
            .starts_with("POST /api/v1/private/projects/acme/connectors/google/apply-personal"),
        "unexpected request line: {request_line}"
    );
    let parsed: Value = serde_json::from_slice(&body).expect("request body should be JSON");
    assert_eq!(parsed["googleCapabilities"], json!(["gmail", "calendar"]));
}

// ── 6. DELETE with JSON body (googleDisconnect) ─────────────────────────────

#[tokio::test]
async fn google_disconnect_sends_delete_with_json_body() {
    let (base_url, handle) = mock_once(200, "application/json", r#"{"ok":true}"#).await;
    let m = client_at(&base_url);

    m.credentials
        .google_disconnect("507f1f77bcf86cd799439011", Some("gmail"))
        .await
        .expect("google_disconnect should succeed");

    let raw = handle.await.expect("mock server task");
    let (request_line, _headers, body) = split_request(&raw);
    assert!(
        request_line.starts_with("DELETE /api/v1/private/credentials/google/access"),
        "unexpected request line: {request_line}"
    );
    let parsed: Value = serde_json::from_slice(&body).expect("request body should be JSON");
    assert_eq!(parsed["accountId"], json!("507f1f77bcf86cd799439011"));
    assert_eq!(parsed["capability"], json!("gmail"));
}
