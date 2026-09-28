//! Integration tests for the event triggers SDK surface
//! (`melaya::TriggersAPI`, `melaya.agents.triggers` / `melaya.triggers`).
//!
//! Same mock-transport approach as `tests/connector_tools.rs`: a tiny
//! one-shot local TCP server stands in for the Melaya API, with no
//! HTTP-mocking crate added as a dependency. Each test checks the method,
//! path, query and body the SDK sends, and the decoded response.

use melaya::{Melaya, MelayaError, MelayaOptions, TriggerEventsQuery, TRIGGER_VERDICTS};
use serde_json::{json, Value};
use std::collections::HashMap;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};

const TRIG_ID: &str = "3f2b8c1e-7a4d-4e5f-9b0a-1c2d3e4f5a6b";

// ── mock server ──────────────────────────────────────────────────────────────

/// Start a one-shot mock HTTP server on `127.0.0.1`: accepts one connection,
/// captures the raw request bytes, replies with the given status/body, then
/// closes. Returns the base URL and a join handle resolving to the request.
async fn mock_once(status: u16, body: &str) -> (String, tokio::task::JoinHandle<Vec<u8>>) {
    let listener = TcpListener::bind("127.0.0.1:0")
        .await
        .expect("bind mock listener");
    let addr = listener.local_addr().expect("local_addr");
    let body = body.to_owned();
    let handle = tokio::spawn(async move {
        let (mut socket, _) = listener.accept().await.expect("accept mock connection");
        let request = read_full_request(&mut socket).await;
        let reason = match status {
            200 => "OK",
            404 => "Not Found",
            429 => "Too Many Requests",
            _ => "OK",
        };
        let response = format!(
            "HTTP/1.1 {status} {reason}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
            body.len(),
        );
        let _ = socket.write_all(response.as_bytes()).await;
        let _ = socket.write_all(body.as_bytes()).await;
        let _ = socket.shutdown().await;
        request
    });
    (format!("http://{addr}"), handle)
}

/// Read one full HTTP/1.1 request (headers + `Content-Length` body) off the socket.
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

/// A captured request: method, path (no query), query pairs, JSON body.
struct Captured {
    method: String,
    path: String,
    query: HashMap<String, String>,
    body: Value,
}

async fn captured(handle: tokio::task::JoinHandle<Vec<u8>>) -> Captured {
    let raw = handle.await.expect("mock server task");
    let header_end = find_subslice(&raw, b"\r\n\r\n").expect("request header terminator");
    let head = String::from_utf8_lossy(&raw[..header_end]).to_string();
    let body_bytes = &raw[header_end + 4..];
    let request_line = head.split("\r\n").next().unwrap_or("").to_owned();
    let mut parts = request_line.split_whitespace();
    let method = parts.next().unwrap_or("").to_owned();
    let target = parts.next().unwrap_or("").to_owned();
    let url = url::Url::parse(&format!("http://x{target}")).expect("parse request url");
    let body = if body_bytes.is_empty() {
        Value::Null
    } else {
        serde_json::from_slice(body_bytes).expect("request body should be JSON")
    };
    Captured {
        method,
        path: url.path().to_owned(),
        query: url.query_pairs().into_owned().collect(),
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

fn path(suffix: &str) -> String {
    format!("/api/v1/private/triggers{suffix}")
}

// ── list / get ───────────────────────────────────────────────────────────────

#[tokio::test]
async fn list_sends_filters_and_decodes() {
    let (base, handle) = mock_once(
        200,
        &json!([{
            "id": TRIG_ID, "publicId": "pub_1", "name": "Stripe refunds", "kind": "webhook",
            "project": "acme", "pipelineName": "refunds", "enabled": true, "pausedReason": null,
            "signingScheme": "stripe", "config": {"action": {"type": "notify"}},
            "webhookUrl": "https://api.melaya.org/api/v1/hooks/pub_1", "projectAccess": true
        }])
        .to_string(),
    )
    .await;
    let m = client_at(&base);
    let out = m
        .triggers
        .list(Some("acme"), Some("refunds"))
        .await
        .expect("list");
    assert_eq!(out[0]["id"], json!(TRIG_ID));
    assert_eq!(out[0]["config"]["action"]["type"], json!("notify"));

    let req = captured(handle).await;
    assert_eq!(req.method, "GET");
    assert_eq!(req.path, path(""));
    assert_eq!(req.query.get("project").map(String::as_str), Some("acme"));
    assert_eq!(
        req.query.get("pipelineName").map(String::as_str),
        Some("refunds")
    );
    assert_eq!(req.query.len(), 2);
}

#[tokio::test]
async fn list_without_filters_sends_no_query() {
    let (base, handle) = mock_once(200, "[]").await;
    let m = client_at(&base);
    let out = m.triggers.list(None, None).await.expect("list");
    assert_eq!(out, json!([]));
    let req = captured(handle).await;
    assert!(req.query.is_empty(), "unexpected query: {:?}", req.query);
}

#[tokio::test]
async fn get_hits_id_path() {
    let (base, handle) = mock_once(
        200,
        &json!({"id": TRIG_ID, "kind": "poll", "projectAccess": false, "webhookUrl": null})
            .to_string(),
    )
    .await;
    let m = client_at(&base);
    let out = m.triggers.get(TRIG_ID).await.expect("get");
    assert_eq!(out["kind"], json!("poll"));
    assert_eq!(out["projectAccess"], json!(false));
    let req = captured(handle).await;
    assert_eq!(req.method, "GET");
    assert_eq!(req.path, path(&format!("/{TRIG_ID}")));
}

#[tokio::test]
async fn get_not_found_is_api_error() {
    let (base, _handle) = mock_once(
        404,
        r#"{"error":"trigger_not_found","message":"[trigger_not_found] Trigger not found."}"#,
    )
    .await;
    let m = client_at(&base);
    match m
        .triggers
        .get(TRIG_ID)
        .await
        .expect_err("404 must be an error")
    {
        MelayaError::Api { status, code, .. } => {
            assert_eq!(status, 404);
            assert_eq!(code.as_deref(), Some("trigger_not_found"));
        }
        other => panic!("expected MelayaError::Api, got: {other:?}"),
    }
}

// ── deliveries / stats / approvals ───────────────────────────────────────────

#[tokio::test]
async fn deliveries_sends_limit() {
    let (base, handle) = mock_once(
        200,
        &json!([{
            "id": "d1", "triggerId": TRIG_ID, "eventId": "evt_1", "verdict": "dispatched",
            "decision": {"urgent": {"choice": "yes"}}, "runId": "run_1", "latencyMs": 412,
            "timings": {"decide": 180, "total": 412}
        }])
        .to_string(),
    )
    .await;
    let m = client_at(&base);
    let out = m
        .triggers
        .deliveries(TRIG_ID, Some(20))
        .await
        .expect("deliveries");
    assert_eq!(out[0]["verdict"], json!("dispatched"));
    assert_eq!(out[0]["timings"]["decide"], json!(180));
    let req = captured(handle).await;
    assert_eq!(req.method, "GET");
    assert_eq!(req.path, path(&format!("/{TRIG_ID}/deliveries")));
    assert_eq!(req.query.get("limit").map(String::as_str), Some("20"));
}

#[tokio::test]
async fn deliveries_without_limit_sends_no_query() {
    let (base, handle) = mock_once(200, "[]").await;
    let m = client_at(&base);
    m.triggers
        .deliveries(TRIG_ID, None)
        .await
        .expect("deliveries");
    let req = captured(handle).await;
    assert!(req.query.is_empty(), "unexpected query: {:?}", req.query);
}

#[tokio::test]
async fn stats_sends_hours() {
    let (base, handle) = mock_once(
        200,
        &json!({
            "hours": 48,
            "byVerdict": {"dispatched": {"n": 12, "p50": 300, "p95": 900}},
            "filtered": 7, "sampled": false
        })
        .to_string(),
    )
    .await;
    let m = client_at(&base);
    let out = m.triggers.stats(TRIG_ID, Some(48)).await.expect("stats");
    assert_eq!(out["byVerdict"]["dispatched"]["n"], json!(12));
    assert_eq!(out["filtered"], json!(7));
    let req = captured(handle).await;
    assert_eq!(req.method, "GET");
    assert_eq!(req.path, path(&format!("/{TRIG_ID}/stats")));
    assert_eq!(req.query.get("hours").map(String::as_str), Some("48"));
}

#[tokio::test]
async fn pending_approvals_hits_approvals_path() {
    let (base, handle) = mock_once(
        200,
        &json!([{
            "requestId": "req_1", "triggerId": TRIG_ID, "tool": "gmail_send",
            "argsPreview": "{\"to\":\"a@b.c\"}", "createdAt": 1790000000000u64, "expiresAt": 1790003600000u64
        }])
        .to_string(),
    )
    .await;
    let m = client_at(&base);
    let out = m
        .triggers
        .pending_approvals(TRIG_ID)
        .await
        .expect("pending_approvals");
    assert_eq!(out[0]["requestId"], json!("req_1"));
    let req = captured(handle).await;
    assert_eq!(req.method, "GET");
    assert_eq!(req.path, path(&format!("/{TRIG_ID}/approvals")));
}

// ── test (dry run) ───────────────────────────────────────────────────────────

#[tokio::test]
async fn test_posts_payload() {
    let (base, handle) = mock_once(200, r#"{"accepted":true,"eventId":"test-abc"}"#).await;
    let m = client_at(&base);
    let out = m
        .triggers
        .test(TRIG_ID, Some(&json!({"amount": 42})))
        .await
        .expect("test");
    assert_eq!(out["accepted"], json!(true));
    assert_eq!(out["eventId"], json!("test-abc"));
    let req = captured(handle).await;
    assert_eq!(req.method, "POST");
    assert_eq!(req.path, path(&format!("/{TRIG_ID}/test")));
    assert_eq!(req.body, json!({"payload": {"amount": 42}}));
}

#[tokio::test]
async fn test_without_payload_sends_empty_object() {
    let (base, handle) = mock_once(
        200,
        r#"{"accepted":false,"eventId":"test-def","reason":"disabled"}"#,
    )
    .await;
    let m = client_at(&base);
    let out = m.triggers.test(TRIG_ID, None).await.expect("test");
    assert_eq!(out["reason"], json!("disabled"));
    let req = captured(handle).await;
    assert_eq!(req.body, json!({"payload": {}}));
}

// ── events ───────────────────────────────────────────────────────────────────

#[tokio::test]
async fn events_encodes_every_filter() {
    let (base, handle) = mock_once(
        200,
        &json!({
            "events": [{
                "triggerId": TRIG_ID, "deliveryId": null, "eventId": "evt_9", "source": "webhook",
                "verdict": "filtered", "detail": "prefilter false", "at": 1790000000500u64
            }],
            "scanned": 37,
            "retention": {"maxEvents": 500, "ttlSec": 86400}
        })
        .to_string(),
    )
    .await;
    let m = client_at(&base);
    let out = m
        .triggers
        .events(&TriggerEventsQuery {
            trigger_id: Some(TRIG_ID.to_owned()),
            since: Some(1_790_000_000_000),
            verdicts: vec!["filtered".to_owned(), "rejected".to_owned()],
            limit: Some(20),
        })
        .await
        .expect("events");
    assert_eq!(out["events"][0]["verdict"], json!("filtered"));
    assert_eq!(out["retention"]["ttlSec"], json!(86400));

    let req = captured(handle).await;
    assert_eq!(req.method, "GET");
    assert_eq!(req.path, path("/events"));
    assert_eq!(
        req.query.get("triggerId").map(String::as_str),
        Some(TRIG_ID)
    );
    assert_eq!(
        req.query.get("since").map(String::as_str),
        Some("1790000000000")
    );
    assert_eq!(
        req.query.get("verdicts").map(String::as_str),
        Some("filtered,rejected")
    );
    assert_eq!(req.query.get("limit").map(String::as_str), Some("20"));
}

#[tokio::test]
async fn events_default_query_sends_nothing() {
    let (base, handle) = mock_once(
        200,
        r#"{"events":[],"scanned":0,"retention":{"maxEvents":500,"ttlSec":86400}}"#,
    )
    .await;
    let m = client_at(&base);
    m.triggers
        .events(&TriggerEventsQuery::default())
        .await
        .expect("events");
    let req = captured(handle).await;
    assert!(req.query.is_empty(), "unexpected query: {:?}", req.query);
}

#[test]
fn verdicts_constant_matches_server() {
    assert_eq!(TRIGGER_VERDICTS.len(), 9);
    assert_eq!(TRIGGER_VERDICTS[0], "accepted");
    assert_eq!(TRIGGER_VERDICTS[8], "failed");
}

// ── poll ─────────────────────────────────────────────────────────────────────

#[tokio::test]
async fn poll_status_hits_poll_path() {
    let (base, handle) = mock_once(
        200,
        &json!({
            "synced": true, "status": "ok", "lastError": null, "armed": true,
            "baselinePending": false, "seenCount": 14, "itemsPublished": 3, "consecutiveErrors": 0,
            "requestedIntervalSec": 60, "effectiveIntervalSec": 300, "tierFloorSec": 300
        })
        .to_string(),
    )
    .await;
    let m = client_at(&base);
    let out = m.triggers.poll_status(TRIG_ID).await.expect("poll_status");
    assert_eq!(out["effectiveIntervalSec"], json!(300));
    let req = captured(handle).await;
    assert_eq!(req.method, "GET");
    assert_eq!(req.path, path(&format!("/{TRIG_ID}/poll")));
}

#[tokio::test]
async fn poll_test_sends_dry_true() {
    let (base, handle) = mock_once(
        200,
        &json!({
            "dry": true, "ok": true, "found": 2, "baseline": false, "wouldPublish": 1,
            "items": [{"id": "m1", "preview": "{\"subject\":\"Invoice\"}"}],
            "samplePayload": {"subject": "Invoice"}
        })
        .to_string(),
    )
    .await;
    let m = client_at(&base);
    let out = m.triggers.poll_test(TRIG_ID).await.expect("poll_test");
    assert_eq!(out["wouldPublish"], json!(1));
    assert_eq!(out["samplePayload"]["subject"], json!("Invoice"));
    let req = captured(handle).await;
    assert_eq!(req.method, "POST");
    assert_eq!(req.path, path(&format!("/{TRIG_ID}/poll/test")));
    assert_eq!(req.body, json!({"dry": true}));
}

#[tokio::test]
async fn poll_test_failed_dry_poll_is_returned_as_result() {
    let (base, handle) = mock_once(200, r#"{"dry":true,"ok":false,"error":"tool_failed"}"#).await;
    let m = client_at(&base);
    let out = m
        .triggers
        .poll_test(TRIG_ID)
        .await
        .expect("a failed dry poll must be returned as a result, not an error");
    assert_eq!(
        out,
        json!({"dry": true, "ok": false, "error": "tool_failed"})
    );
    let req = captured(handle).await;
    assert_eq!(req.body, json!({"dry": true}));
}

#[tokio::test]
async fn poll_test_http_error_still_errors() {
    let (base, _handle) = mock_once(
        429,
        r#"{"error":"poll_dry_run_throttled","message":"[poll_dry_run_throttled] Wait a few seconds before testing the poll again."}"#,
    )
    .await;
    let m = client_at(&base);
    match m
        .triggers
        .poll_test(TRIG_ID)
        .await
        .expect_err("a 429 must still be an error")
    {
        MelayaError::Api { status, code, .. } => {
            assert_eq!(status, 429);
            assert_eq!(code.as_deref(), Some("poll_dry_run_throttled"));
        }
        other => panic!("expected MelayaError::Api, got: {other:?}"),
    }
}

#[tokio::test]
async fn poll_now_ok_false_still_errors() {
    let (base, _handle) = mock_once(
        200,
        r#"{"dry":false,"ok":false,"error":"trigger_disabled"}"#,
    )
    .await;
    let m = client_at(&base);
    match m
        .triggers
        .poll_now(TRIG_ID)
        .await
        .expect_err("poll_now keeps the shared ok:false check")
    {
        MelayaError::Api { code, .. } => assert_eq!(code.as_deref(), Some("trigger_disabled")),
        other => panic!("expected MelayaError::Api, got: {other:?}"),
    }
}

#[tokio::test]
async fn poll_now_sends_dry_false() {
    let (base, handle) = mock_once(200, r#"{"dry":false,"queued":true}"#).await;
    let m = client_at(&base);
    let out = m.triggers.poll_now(TRIG_ID).await.expect("poll_now");
    assert_eq!(out["queued"], json!(true));
    let req = captured(handle).await;
    assert_eq!(req.method, "POST");
    assert_eq!(req.path, path(&format!("/{TRIG_ID}/poll/test")));
    assert_eq!(req.body, json!({"dry": false}));
}

#[tokio::test]
async fn poll_sync_posts_to_sync_path() {
    let (base, handle) = mock_once(200, r#"{"result":"synced"}"#).await;
    let m = client_at(&base);
    let out = m.triggers.poll_sync(TRIG_ID).await.expect("poll_sync");
    assert_eq!(out["result"], json!("synced"));
    let req = captured(handle).await;
    assert_eq!(req.method, "POST");
    assert_eq!(req.path, path(&format!("/{TRIG_ID}/poll/sync")));
}

// ── presets / limits / sources ───────────────────────────────────────────────

#[tokio::test]
async fn presets_limits_sources_paths() {
    let (base, handle) = mock_once(
        200,
        r#"{"tier":"forge","tierFloorSec":60,"presets":[{"id":"gmail_new_email"}],"beta":{"allowed":true,"minTier":"forge"}}"#,
    )
    .await;
    let out = client_at(&base).triggers.presets().await.expect("presets");
    assert_eq!(out["presets"][0]["id"], json!("gmail_new_email"));
    let req = captured(handle).await;
    assert_eq!((req.method.as_str(), req.path), ("GET", path("/presets")));

    let (base, handle) = mock_once(
        200,
        r#"{"tierClass":"forge","triggers":{"used":2,"cap":10},"sources":{"used":0,"cap":2},"pollIntervalFloorSec":60}"#,
    )
    .await;
    let out = client_at(&base).triggers.limits().await.expect("limits");
    assert_eq!(out["triggers"]["cap"], json!(10));
    let req = captured(handle).await;
    assert_eq!((req.method.as_str(), req.path), ("GET", path("/limits")));

    let (base, handle) = mock_once(
        200,
        r#"[{"id":"s1","name":"Kalshi feed","url":"wss://api.kalshi.com/ws","hasAuth":false,"transport":"ws"}]"#,
    )
    .await;
    let out = client_at(&base).triggers.sources().await.expect("sources");
    assert_eq!(out[0]["transport"], json!("ws"));
    let req = captured(handle).await;
    assert_eq!((req.method.as_str(), req.path), ("GET", path("/sources")));
}

// ── namespace wiring ─────────────────────────────────────────────────────────

#[tokio::test]
async fn agents_namespace_exposes_triggers() {
    let (base, handle) = mock_once(200, r#"{"tierClass":"forge"}"#).await;
    let m = client_at(&base);
    let out = m.agents.triggers.limits().await.expect("limits via agents");
    assert_eq!(out["tierClass"], json!("forge"));
    let req = captured(handle).await;
    assert_eq!(req.path, path("/limits"));
}
