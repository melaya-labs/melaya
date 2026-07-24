//! Platform real-time events via Socket.IO / Engine.IO v4 at `/api/v1/events`.
//!
//! The [`MelayaEvents`] struct implements an Engine.IO v4 client:
//! 1. HTTP long-poll handshake to obtain a session ID (`sid`).
//! 2. POST the Socket.IO connect packet with Bearer auth.
//! 3. Upgrade to WebSocket (tungstenite) and pump frames to a tokio channel.
//!
//! Room semantics (matching the server):
//! - `run:<runId>` — events for a specific pipeline run.
//! - `project:<project>` — all events in a project.
//! - `hitl:user:<userId>` — HITL approval events (joined automatically on connect).
//!
//! The connection is opened lazily on the first `subscribe_*` / `join_room`
//! call — constructing [`Melaya`](crate::Melaya) performs no I/O and works
//! outside a Tokio runtime. The background task exits once every client
//! handle has been dropped.
//!
//! # Example
//! ```no_run
//! use melaya::Melaya;
//!
//! #[tokio::main]
//! async fn main() {
//!     let m = Melaya::new(&std::env::var("MK").unwrap()).unwrap();
//!     let mut evts = m.events;
//!     // Subscribe to a run
//!     let mut rx = evts.subscribe_run("run-abc123");
//!     while let Some(ev) = rx.recv().await {
//!         println!("{ev}");
//!     }
//! }
//! ```

use std::sync::{Arc, Mutex};

use futures_util::{SinkExt, StreamExt};
use serde_json::Value;
use tokio::sync::mpsc::{self, UnboundedReceiver, UnboundedSender};
use tokio_tungstenite::{connect_async_tls_with_config, tungstenite::Message, Connector};
use url::Url;

use crate::error::MelayaError;

/// Engine.IO v4 packet prefix characters.
const EIO_PING: char = '2';
const EIO_PONG: char = '3';
const EIO_MESSAGE: char = '4';

/// Socket.IO v4 packet types (first character after `4`).
const SIO_CONNECT: u8 = 0;
const SIO_EVENT: u8 = 2;

/// A received platform event (raw JSON from the Socket.IO server).
pub type EventFrame = Value;

/// A subscription handle. Drop it to stop receiving events. The internal
/// sender is automatically removed when the receiver is dropped.
pub struct EventSubscription {
    rx: UnboundedReceiver<EventFrame>,
    /// Kept so `recv` (async — a runtime is guaranteed) can spawn the
    /// background task if the subscription was created outside a runtime.
    starter: Arc<Starter>,
}

impl EventSubscription {
    /// Receive the next event, returning `None` if the connection closed.
    pub async fn recv(&mut self) -> Option<EventFrame> {
        self.starter.ensure_started();
        self.rx.recv().await
    }
}

// ── Shared state (Arc<Mutex<...>>) ──────────────────────────────────────────

struct Inner {
    /// All active subscribers keyed by the Socket.IO event name they care about.
    /// Each entry is a `(room_filter, sender)` pair — `None` room means match all.
    subs: Vec<(Option<String>, String, UnboundedSender<EventFrame>)>,
    /// Rooms currently joined.
    rooms: Vec<String>,
}

impl Inner {
    fn new() -> Self {
        Self {
            subs: Vec::new(),
            rooms: Vec::new(),
        }
    }

    fn dispatch(&mut self, event_name: &str, payload: Value) {
        self.subs.retain(|(room, ev, tx)| {
            if ev != event_name {
                return true;
            }
            if !room_matches(room.as_deref(), &payload) {
                // Not this subscription's room — keep it, skip the event.
                return true;
            }
            tx.send(payload.clone()).is_ok()
        });
    }
}

/// Return `true` if a payload belongs to a subscription's room filter.
///
/// `None` matches everything. When the payload carries a room discriminator
/// (`runId` for `run:` rooms, `projectId`/`project` for `project:` rooms) it
/// must match, so multi-room subscribers never receive cross-room events.
/// Payloads without a discriminator are delivered to avoid dropping events.
fn room_matches(room: Option<&str>, payload: &Value) -> bool {
    let Some(room) = room else {
        return true;
    };
    if let Some(id) = room.strip_prefix("run:") {
        if let Some(run_id) = payload.get("runId").and_then(Value::as_str) {
            return run_id == id;
        }
    } else if let Some(name) = room.strip_prefix("project:") {
        if let Some(project) = payload
            .get("projectId")
            .or_else(|| payload.get("project"))
            .and_then(Value::as_str)
        {
            return project == name;
        }
    }
    true
}

// ── MelayaEvents ────────────────────────────────────────────────────────────

/// Deferred-start state for the background Engine.IO task.
///
/// Constructing [`MelayaEvents`] performs no I/O and never spawns: the task
/// is started lazily on the first `subscribe_*` / `join_room` call (or, as a
/// fallback, on the first `recv`). This keeps `Melaya::new` usable outside a
/// Tokio runtime.
struct Starter {
    api_key: String,
    base_url: String,
    inner: Arc<Mutex<Inner>>,
    /// The command receiver, waiting to be moved into the background task on
    /// first use. `None` once the task has been spawned.
    pending_rx: Mutex<Option<UnboundedReceiver<Cmd>>>,
}

impl Starter {
    /// Spawn the background task if it has not started yet and a Tokio
    /// runtime is available. Never panics outside a runtime — the spawn is
    /// simply retried on the next subscribe/join/recv call.
    fn ensure_started(&self) {
        let Ok(handle) = tokio::runtime::Handle::try_current() else {
            return;
        };
        let mut pending = self.pending_rx.lock().unwrap();
        if let Some(cmd_rx) = pending.take() {
            handle.spawn(run_client(
                self.api_key.clone(),
                self.base_url.clone(),
                Arc::clone(&self.inner),
                cmd_rx,
            ));
        }
    }
}

/// Platform real-time event client (Engine.IO v4 + Socket.IO v4).
#[derive(Clone)]
pub struct MelayaEvents {
    inner: Arc<Mutex<Inner>>,
    /// Channel to send join/leave room commands to the background task.
    /// When every clone of this sender is dropped the task exits.
    cmd_tx: UnboundedSender<Cmd>,
    starter: Arc<Starter>,
}

#[derive(Debug)]
enum Cmd {
    Join(String),
    Leave(String),
}

impl MelayaEvents {
    pub(crate) fn new(api_key: String, base_url: String) -> Self {
        let inner = Arc::new(Mutex::new(Inner::new()));
        let (cmd_tx, cmd_rx) = mpsc::unbounded_channel::<Cmd>();

        // The background task is NOT spawned here: connect lazily on the
        // first subscription/join so construction works outside a runtime.
        let starter = Arc::new(Starter {
            api_key,
            base_url,
            inner: Arc::clone(&inner),
            pending_rx: Mutex::new(Some(cmd_rx)),
        });

        Self {
            inner,
            cmd_tx,
            starter,
        }
    }

    // ── Subscribe helpers ────────────────────────────────────────────────────

    fn subscribe(&self, event_name: &str, room: Option<&str>) -> EventSubscription {
        self.starter.ensure_started();
        let (tx, rx) = mpsc::unbounded_channel::<EventFrame>();
        self.inner
            .lock()
            .unwrap()
            .subs
            .push((room.map(str::to_owned), event_name.to_owned(), tx));
        EventSubscription {
            rx,
            starter: Arc::clone(&self.starter),
        }
    }

    /// Subscribe to run events for a specific pipeline run.
    /// Automatically joins the `run:<runId>` room.
    pub fn subscribe_run(&self, run_id: &str) -> EventSubscription {
        let room = format!("run:{run_id}");
        self.join_room(&room);
        self.subscribe("pushEvent", Some(&room))
    }

    /// Subscribe to init-phase events for a run.
    pub fn subscribe_init_phase(&self, run_id: &str) -> EventSubscription {
        let room = format!("run:{run_id}");
        self.join_room(&room);
        self.subscribe("pushInitPhase", Some(&room))
    }

    /// Subscribe to all events in a project.
    /// Automatically joins the `project:<project>` room.
    pub fn subscribe_project(&self, project: &str) -> EventSubscription {
        let room = format!("project:{project}");
        self.join_room(&room);
        self.subscribe("pushEvent", Some(&room))
    }

    /// Subscribe to HITL approval events.
    /// The server auto-joins authenticated sockets to the user's HITL room.
    pub fn subscribe_hitl(&self) -> EventSubscription {
        self.subscribe("pushHitlApprovals", None)
    }

    /// Subscribe to pipeline CRUD events in a project.
    pub fn subscribe_pipeline_events(&self, project: &str) -> EventSubscription {
        let room = format!("project:{project}");
        self.join_room(&room);
        self.subscribe("pipelineCreated", Some(&room))
    }

    // ── Room management ──────────────────────────────────────────────────────

    /// Join a Socket.IO room.
    pub fn join_room(&self, room: &str) -> &Self {
        self.starter.ensure_started();
        {
            let mut g = self.inner.lock().unwrap();
            if g.rooms.contains(&room.to_owned()) {
                return self;
            }
            g.rooms.push(room.to_owned());
        }
        let _ = self.cmd_tx.send(Cmd::Join(room.to_owned()));
        self
    }

    /// Remove a room from the rejoin-on-reconnect list and drop the
    /// subscriptions scoped to it (their receivers observe channel close).
    fn forget_room(&self, room: &str) {
        let mut g = self.inner.lock().unwrap();
        g.rooms.retain(|r| r != room);
        g.subs.retain(|(r, _, _)| r.as_deref() != Some(room));
    }

    /// Leave a Socket.IO room.
    pub fn leave_run(&self, run_id: &str) -> &Self {
        let room = format!("run:{run_id}");
        self.forget_room(&room);
        let _ = self.cmd_tx.send(Cmd::Leave(room));
        self
    }

    /// Leave a project room.
    pub fn leave_project(&self, project: &str) -> &Self {
        let room = format!("project:{project}");
        self.forget_room(&room);
        let _ = self.cmd_tx.send(Cmd::Leave(room));
        self
    }
}

// ── Background Engine.IO client task ────────────────────────────────────────

/// Build the Engine.IO polling URL.
fn polling_url(base_url: &str, sid: Option<&str>) -> String {
    let base = base_url.trim_end_matches('/');
    let mut url = Url::parse(&format!("{base}/api/v1/events/")).unwrap();
    url.query_pairs_mut()
        .append_pair("EIO", "4")
        .append_pair("transport", "polling");
    if let Some(s) = sid {
        url.query_pairs_mut().append_pair("sid", s);
    }
    url.to_string()
}

/// Build the Engine.IO WebSocket upgrade URL.
fn ws_url(base_url: &str, sid: &str) -> String {
    let base = base_url.trim_end_matches('/');
    // Convert http(s) to ws(s)
    let ws_base = if base.starts_with("https://") {
        base.replacen("https://", "wss://", 1)
    } else if base.starts_with("http://") {
        base.replacen("http://", "ws://", 1)
    } else {
        base.to_owned()
    };
    let mut url = Url::parse(&format!("{ws_base}/api/v1/events/")).unwrap();
    url.query_pairs_mut()
        .append_pair("EIO", "4")
        .append_pair("transport", "websocket")
        .append_pair("sid", sid);
    url.to_string()
}

/// Parse a Socket.IO event packet and dispatch to subscribers.
fn handle_packet(raw: &str, inner: &Arc<Mutex<Inner>>) {
    let Some(first) = raw.chars().next() else {
        return;
    };
    if first == EIO_PING {
        // Pong is sent by the WebSocket task directly.
        return;
    }
    if first != EIO_MESSAGE {
        return;
    }
    let sio_raw = &raw[1..];
    let Some(sio_type_ch) = sio_raw.chars().next() else {
        return;
    };
    let sio_type = sio_type_ch as u8 - b'0';
    if sio_type != SIO_EVENT {
        return;
    }
    // Parse `[event_name, payload]`
    let json_part = &sio_raw[1..];
    if let Ok(Value::Array(arr)) = serde_json::from_str::<Value>(json_part) {
        if arr.len() >= 2 {
            if let Value::String(event_name) = &arr[0] {
                let payload = arr[1].clone();
                inner.lock().unwrap().dispatch(event_name, payload);
            }
        }
    }
}

/// Build a rustls connector with the public web PKI roots.
fn make_tls_connector() -> Connector {
    // Ensure the ring provider is installed.
    let _ = rustls::crypto::ring::default_provider().install_default();
    let mut root_store = rustls::RootCertStore::empty();
    root_store.extend(webpki_roots::TLS_SERVER_ROOTS.iter().cloned());
    let config = rustls::ClientConfig::builder()
        .with_root_certificates(root_store)
        .with_no_client_auth();
    Connector::Rustls(Arc::new(config))
}

/// Build the correct Socket.IO join packet for a room string.
///
/// Server contract (both primary path and reconnect/fallback path):
/// - `run:<id>`       → emit `joinRunRoom` with bare `<id>`
/// - `project:<name>` → emit `joinProjectRoom` with bare `<name>`
///
/// The server does NOT handle a generic `"join"` event; unknown room
/// prefixes are treated as run rooms as a best-effort fallback.
fn make_join_packet(room: &str) -> String {
    let (event, arg) = if let Some(id) = room.strip_prefix("run:") {
        ("joinRunRoom", id)
    } else if let Some(name) = room.strip_prefix("project:") {
        ("joinProjectRoom", name)
    } else {
        // Unknown prefix: treat as a run room so the server can handle it.
        ("joinRunRoom", room)
    };
    format!(
        "{EIO_MESSAGE}{SIO_EVENT}{}",
        serde_json::json!([event, arg])
    )
}

/// The main background task: handshake → upgrade → pump.
async fn run_client(
    api_key: String,
    base_url: String,
    inner: Arc<Mutex<Inner>>,
    mut cmd_rx: UnboundedReceiver<Cmd>,
) {
    // HTTP client for polling handshake.
    let http = reqwest::Client::new();

    // Retry loop with bounded exponential back-off (cap: 30 s). The back-off
    // resets to 1 s after every successful connection (inside `connect_once`).
    let mut backoff_ms = 1000u64;

    loop {
        // Exit as soon as every client handle (command sender) is dropped —
        // even while disconnected. Drained commands are safe to discard: the
        // room list lives in `Inner` and is replayed on (re)connect, and
        // `leave_*` already pruned `Inner` before queueing its command.
        loop {
            match cmd_rx.try_recv() {
                Ok(_) => continue,
                Err(mpsc::error::TryRecvError::Empty) => break,
                Err(mpsc::error::TryRecvError::Disconnected) => return,
            }
        }

        match connect_once(
            &http,
            &api_key,
            &base_url,
            &inner,
            &mut cmd_rx,
            &mut backoff_ms,
        )
        .await
        {
            Ok(()) => {
                // All client handles dropped — stop.
                return;
            }
            Err(_e) => {
                // Add ±20 % jitter to avoid thundering-herd on reconnect.
                let jitter = backoff_ms / 5;
                let delay = backoff_ms.saturating_add(jitter);
                // Sleep, but wake immediately if the last handle is dropped.
                let sleep = tokio::time::sleep(tokio::time::Duration::from_millis(delay));
                tokio::pin!(sleep);
                loop {
                    tokio::select! {
                        _ = &mut sleep => break,
                        cmd = cmd_rx.recv() => match cmd {
                            // Rooms are tracked in `Inner`; joins are replayed
                            // on reconnect, so queued commands can be dropped.
                            Some(_) => {}
                            None => return,
                        },
                    }
                }
                // Double the base, cap at 30 s.
                backoff_ms = (backoff_ms * 2).min(30_000);
            }
        }
    }
}

async fn connect_once(
    http: &reqwest::Client,
    api_key: &str,
    base_url: &str,
    inner: &Arc<Mutex<Inner>>,
    cmd_rx: &mut UnboundedReceiver<Cmd>,
    backoff_ms: &mut u64,
) -> Result<(), MelayaError> {
    // ── Step 1: EIO handshake via HTTP long-poll ──────────────────────────
    let poll_url_no_sid = polling_url(base_url, None);
    let res = http
        .get(&poll_url_no_sid)
        .header("Authorization", format!("Bearer {api_key}"))
        .send()
        .await?;
    let text = res.text().await?;

    // Extract JSON (skip optional length prefix).
    let json_start = text.find('{').ok_or_else(|| {
        MelayaError::Config("MelayaEvents: unexpected EIO handshake response".into())
    })?;
    let open_data: serde_json::Value = serde_json::from_str(&text[json_start..])?;
    let sid = open_data["sid"]
        .as_str()
        .ok_or_else(|| MelayaError::Config("MelayaEvents: no sid in EIO open packet".into()))?;

    // ── Step 2: POST Socket.IO connect packet ────────────────────────────
    let connect_packet = format!(
        "{}{}{}",
        EIO_MESSAGE,
        SIO_CONNECT,
        serde_json::json!({ "token": api_key })
    );
    let poll_url_sid = polling_url(base_url, Some(sid));
    http.post(&poll_url_sid)
        .header("Authorization", format!("Bearer {api_key}"))
        .header("Content-Type", "text/plain;charset=UTF-8")
        .body(connect_packet)
        .send()
        .await?;

    // ── Step 3: WebSocket upgrade ────────────────────────────────────────
    let ws_target = ws_url(base_url, sid);
    let connector = make_tls_connector();
    let (ws_stream, _) =
        connect_async_tls_with_config(&ws_target, None, false, Some(connector)).await?;
    let (mut ws_write, mut ws_read) = ws_stream.split();

    // Engine.IO upgrade probe.
    ws_write.send(Message::Text("2probe".into())).await.ok();
    ws_write.send(Message::Text("5".into())).await.ok();

    // Connected — reset the reconnect back-off to its initial value.
    *backoff_ms = 1000;

    // Replay queued room joins using the correct server event names.
    let rooms: Vec<String> = inner.lock().unwrap().rooms.clone();
    for room in rooms {
        let packet = make_join_packet(&room);
        ws_write.send(Message::Text(packet.into())).await.ok();
    }

    // ── Step 4: pump frames + handle commands ────────────────────────────
    loop {
        tokio::select! {
            msg = ws_read.next() => {
                match msg {
                    Some(Ok(Message::Text(txt))) => {
                        let raw = txt.as_str();
                        // Respond to EIO pings
                        if raw.starts_with(EIO_PING) {
                            let pong = EIO_PONG.to_string();
                            ws_write.send(Message::Text(pong.into())).await.ok();
                        } else {
                            handle_packet(raw, inner);
                        }
                    }
                    Some(Ok(Message::Close(_))) | None => {
                        return Err(MelayaError::Config("WebSocket closed".into()));
                    }
                    Some(Err(e)) => return Err(e.into()),
                    _ => {}
                }
            }
            cmd = cmd_rx.recv() => {
                match cmd {
                    Some(Cmd::Join(room)) => {
                        // Map room prefix to the correct server event name:
                        // "run:<id>"      -> emit joinRunRoom(<id>)
                        // "project:<name>" -> emit joinProjectRoom(<name>)
                        let packet = make_join_packet(&room);
                        ws_write.send(Message::Text(packet.into())).await.ok();
                    }
                    Some(Cmd::Leave(room)) => {
                        // Server expects leaveRoom with the FULL room string.
                        let sio = format!(
                            "{EIO_MESSAGE}{SIO_EVENT}{}",
                            serde_json::json!(["leaveRoom", room])
                        );
                        ws_write.send(Message::Text(sio.into())).await.ok();
                    }
                    None => break,
                }
            }
        }
    }

    Ok(())
}
