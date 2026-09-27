//! Integration tests for the connector-tools SDK surface
//! (`melaya::ConnectorToolsAPI`, `melaya.agents.connector_tools` /
//! `melaya.connector_tools`).
//!
//! Same mock-transport approach as `tests/sdk_0_3.rs`: a tiny one-shot (or
//! sequenced) local TCP server stands in for the Melaya API — no HTTP-mocking
//! crate is added as a dependency. Each test spins up its own server, points
//! a `Melaya` client at it via `MelayaOptions::base_url`, and asserts on both
//! the parsed response and the raw request bytes the server captured.

use melaya::{Melaya, MelayaError, MelayaOptions};
use serde_json::{json, Value};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};

// ── mock server ──────────────────────────────────────────────────────────────

/// Start a one-shot mock HTTP server on `127.0.0.1`: accepts exactly one
/// connection, captures the raw request bytes, replies with the given
/// status/body, then closes. Returns the base URL to point a client at, and
/// a join handle that resolves to the captured request bytes.
async fn mock_once(status: u16, body: &'static str) -> (String, tokio::task::JoinHandle<Vec<u8>>) {
    let (base_url, handle) = mock_sequence(vec![(status, body.to_owned())]).await;
    let handle = tokio::spawn(async move { handle.await.expect("mock server task").remove(0) });
    (base_url, handle)
}

/// Start a mock HTTP server that answers a fixed, ordered sequence of
/// `(status, body)` responses, one per accepted connection (each response
/// sends `Connection: close`, so a client using a fresh request per call gets
/// a fresh connection). Returns the base URL and a join handle resolving to
/// the list of captured raw requests, in order.
async fn mock_sequence(
    responses: Vec<(u16, String)>,
) -> (String, tokio::task::JoinHandle<Vec<Vec<u8>>>) {
    let listener = TcpListener::bind("127.0.0.1:0")
        .await
        .expect("bind mock listener");
    let addr = listener.local_addr().expect("local_addr");
    let handle = tokio::spawn(async move {
        let mut captured = Vec::with_capacity(responses.len());
        for (status, body) in responses {
            let (mut socket, _) = listener.accept().await.expect("accept mock connection");
            let request = read_full_request(&mut socket).await;
            captured.push(request);
            let response = format!(
                "HTTP/1.1 {status} {}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
                reason_phrase(status),
                body.len(),
            );
            let _ = socket.write_all(response.as_bytes()).await;
            let _ = socket.write_all(body.as_bytes()).await;
            let _ = socket.shutdown().await;
        }
        captured
    });
    (format!("http://{addr}"), handle)
}

fn reason_phrase(status: u16) -> &'static str {
    match status {
        200 => "OK",
        202 => "Accepted",
        403 => "Forbidden",
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

// ── 1. search() encodes q + limit as query params ───────────────────────────

#[tokio::test]
async fn search_encodes_query_params() {
    let (base_url, handle) = mock_once(
        200,
        r#"{"query":"invoice","services":["stripe"],"tools":[]}"#,
    )
    .await;
    let m = client_at(&base_url);

    let out = m
        .connector_tools
        .search("invoice", Some(5))
        .await
        .expect("search should succeed");
    assert_eq!(out["query"], json!("invoice"));

    let raw = handle.await.expect("mock server task");
    let (request_line, _headers, _body) = split_request(&raw);
    let path_and_query = request_line
        .split_whitespace()
        .nth(1)
        .expect("request path present");
    assert!(
        path_and_query.starts_with("/api/v1/private/connector-tools/search?"),
        "unexpected path: {path_and_query}"
    );
    let url = url::Url::parse(&format!("http://x{path_and_query}")).expect("parse request url");
    let pairs: std::collections::HashMap<String, String> = url.query_pairs().into_owned().collect();
    assert_eq!(pairs.get("q").unwrap(), "invoice");
    assert_eq!(pairs.get("limit").unwrap(), "5");
}

// ── 2. call() on a read tool runs immediately (200) ─────────────────────────

#[tokio::test]
async fn call_read_tool_runs_immediately() {
    let (base_url, handle) = mock_once(
        200,
        r#"{"status":"done","tool":"gmail_list_messages","readOnly":true,"result":"3 unread"}"#,
    )
    .await;
    let m = client_at(&base_url);

    let out = m
        .connector_tools
        .call("gmail_list_messages", None, None)
        .await
        .expect("read call should succeed");
    assert_eq!(out["status"], json!("done"));
    assert_eq!(out["result"], json!("3 unread"));

    let raw = handle.await.expect("mock server task");
    let (request_line, _headers, body) = split_request(&raw);
    assert!(
        request_line.starts_with("POST /api/v1/private/connector-tools/call"),
        "unexpected request line: {request_line}"
    );
    let parsed: Value = serde_json::from_slice(&body).expect("request body should be JSON");
    assert_eq!(parsed["tool"], json!("gmail_list_messages"));
    assert_eq!(parsed["args"], json!({}));
    assert_eq!(parsed["approval"], json!("required"));
}

// ── 3. call() on a staged write returns the 202 body, not an error ─────────

#[tokio::test]
async fn call_write_staged_returns_202_body() {
    let (base_url, _handle) = mock_once(
        202,
        r#"{"status":"pending_approval","tool":"gmail_send","requestId":"req_1","message":"Approve it in the Melaya app."}"#,
    )
    .await;
    let m = client_at(&base_url);

    let out = m
        .connector_tools
        .call(
            "gmail_send",
            Some(&json!({"to": "a@b.c"})),
            None, // default approval: "required"
        )
        .await
        .expect("a staged 202 write must be returned as Ok, not raised as an error");
    assert_eq!(out["status"], json!("pending_approval"));
    assert_eq!(out["requestId"], json!("req_1"));
}

// ── 4. callStatus() done ─────────────────────────────────────────────────────

#[tokio::test]
async fn call_status_done() {
    let (base_url, handle) = mock_once(
        200,
        r#"{"requestId":"req_1","tool":"gmail_send","status":"done","ok":true,"result":"sent"}"#,
    )
    .await;
    let m = client_at(&base_url);

    let out = m
        .connector_tools
        .call_status("req_1")
        .await
        .expect("call_status should succeed");
    assert_eq!(out["status"], json!("done"));
    assert_eq!(out["ok"], json!(true));
    assert_eq!(out["result"], json!("sent"));

    let raw = handle.await.expect("mock server task");
    let (request_line, _headers, _body) = split_request(&raw);
    assert!(
        request_line.starts_with("GET /api/v1/private/connector-tools/calls/req_1"),
        "unexpected request line: {request_line}"
    );
}

// ── 5. money-moving 403 raises the SDK's error ──────────────────────────────

#[tokio::test]
async fn money_moving_write_raises_sdk_error() {
    let (base_url, _handle) = mock_once(
        403,
        r#"{"error":"money_moving_requires_app_approval","message":"Tools that move money or trade run only from the Melaya app."}"#,
    )
    .await;
    let m = client_at(&base_url);

    let err = m
        .connector_tools
        .call("stripe_create_refund", None, Some("none"))
        .await
        .expect_err("a money-moving write must raise, not return Ok");

    match err {
        MelayaError::Api { status, code, .. } => {
            assert_eq!(status, 403);
            assert_eq!(code.as_deref(), Some("money_moving_requires_app_approval"));
        }
        other => panic!("expected MelayaError::Api, got: {other:?}"),
    }
}

// ── 6. callAndWait polls to done with a tiny interval ───────────────────────

#[tokio::test]
async fn call_and_wait_polls_to_done() {
    let (base_url, handle) = mock_sequence(vec![
        (
            202,
            json!({
                "status": "pending_approval",
                "tool": "gmail_send",
                "requestId": "req_9",
                "message": "Approve it in the Melaya app."
            })
            .to_string(),
        ),
        (
            200,
            json!({ "requestId": "req_9", "tool": "gmail_send", "status": "pending" }).to_string(),
        ),
        (
            200,
            json!({
                "requestId": "req_9",
                "tool": "gmail_send",
                "status": "done",
                "ok": true,
                "result": "sent!"
            })
            .to_string(),
        ),
    ])
    .await;
    let m = client_at(&base_url);

    let outcome = m
        .connector_tools
        .call_and_wait(
            "gmail_send",
            Some(&json!({"to": "a@b.c"})),
            None,
            Some(10), // tiny poll interval
            Some(5_000),
        )
        .await
        .expect("call_and_wait should settle to done");

    assert_eq!(outcome["status"], json!("done"));
    assert_eq!(outcome["ok"], json!(true));
    assert_eq!(outcome["result"], json!("sent!"));

    let requests = handle.await.expect("mock server task");
    assert_eq!(
        requests.len(),
        3,
        "expected one call() + two call_status() polls"
    );
    let (first_line, _, _) = split_request(&requests[0]);
    assert!(first_line.starts_with("POST /api/v1/private/connector-tools/call"));
    for raw in &requests[1..] {
        let (line, _, _) = split_request(raw);
        assert!(
            line.starts_with("GET /api/v1/private/connector-tools/calls/req_9"),
            "unexpected poll request line: {line}"
        );
    }
}
