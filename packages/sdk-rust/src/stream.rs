use std::collections::HashMap;
use std::sync::Arc;

use futures_util::StreamExt;
use serde_json::Value;
use tokio::sync::mpsc;
use tokio_tungstenite::{
    connect_async_tls_with_config,
    tungstenite::Message,
    Connector,
};
use url::Url;

use crate::client::HttpClient;
use crate::error::{MelayaError, Result};

/// A live stream of JSON frames from a Melaya WebSocket.
///
/// Receive frames by calling [`MelayaStream::recv`] or iterating the channel
/// receiver directly.
pub struct MelayaStream {
    rx: mpsc::UnboundedReceiver<Value>,
}

impl MelayaStream {
    /// Receive the next frame, returning `None` if the socket closed.
    pub async fn recv(&mut self) -> Option<Value> {
        self.rx.recv().await
    }
}

/// Ensure a process-wide rustls CryptoProvider is installed. Both `ring` and
/// `aws-lc-rs` end up compiled in (via reqwest), so rustls can't auto-select —
/// pin `ring` explicitly. Idempotent; the `.ok()` swallows "already installed".
fn ensure_crypto_provider() {
    let _ = rustls::crypto::ring::default_provider().install_default();
}

/// Build a rustls `ClientConfig` that skips all certificate verification.
fn insecure_rustls_config() -> Arc<rustls::ClientConfig> {
    ensure_crypto_provider();
    let config = rustls::ClientConfig::builder()
        .dangerous()
        .with_custom_certificate_verifier(Arc::new(NoVerifier))
        .with_no_client_auth();
    Arc::new(config)
}

/// Build a normal rustls `ClientConfig` using webpki roots.
fn secure_rustls_config() -> Arc<rustls::ClientConfig> {
    ensure_crypto_provider();
    let mut root_store = rustls::RootCertStore::empty();
    root_store.extend(webpki_roots::TLS_SERVER_ROOTS.iter().cloned());
    let config = rustls::ClientConfig::builder()
        .with_root_certificates(root_store)
        .with_no_client_auth();
    Arc::new(config)
}

/// Open a WebSocket, optionally skipping TLS verification, and pump JSON frames
/// into an unbounded channel.
async fn open_ws(url: &str, insecure_tls: bool) -> Result<MelayaStream> {
    let parsed = Url::parse(url)?;

    let connector = if insecure_tls {
        Connector::Rustls(insecure_rustls_config())
    } else {
        Connector::Rustls(secure_rustls_config())
    };

    let (ws_stream, _resp) = connect_async_tls_with_config(
        parsed.as_str(),
        None,
        false,
        Some(connector),
    )
    .await?;

    let (tx, rx) = mpsc::unbounded_channel::<Value>();

    tokio::spawn(async move {
        let (_, mut read) = ws_stream.split();
        while let Some(msg) = read.next().await {
            match msg {
                Ok(Message::Text(text)) => {
                    // tungstenite 0.24: Text holds either String or Utf8Bytes (AsRef<str>)
                    if let Ok(v) = serde_json::from_str::<Value>(text.as_ref()) {
                        if tx.send(v).is_err() {
                            break;
                        }
                    }
                }
                Ok(Message::Binary(bytes)) => {
                    if let Ok(s) = std::str::from_utf8(&bytes) {
                        if let Ok(v) = serde_json::from_str::<Value>(s) {
                            if tx.send(v).is_err() {
                                break;
                            }
                        }
                    }
                }
                Ok(Message::Close(_)) => break,
                Err(_) => break,
                _ => {}
            }
        }
    });

    Ok(MelayaStream { rx })
}

// ── custom rustls verifier that accepts any certificate ─────────────────────

#[derive(Debug)]
struct NoVerifier;

impl rustls::client::danger::ServerCertVerifier for NoVerifier {
    fn verify_server_cert(
        &self,
        _end_entity: &rustls::pki_types::CertificateDer<'_>,
        _intermediates: &[rustls::pki_types::CertificateDer<'_>],
        _server_name: &rustls::pki_types::ServerName<'_>,
        _ocsp_response: &[u8],
        _now: rustls::pki_types::UnixTime,
    ) -> std::result::Result<rustls::client::danger::ServerCertVerified, rustls::Error> {
        Ok(rustls::client::danger::ServerCertVerified::assertion())
    }

    fn verify_tls12_signature(
        &self,
        _message: &[u8],
        _cert: &rustls::pki_types::CertificateDer<'_>,
        _dss: &rustls::DigitallySignedStruct,
    ) -> std::result::Result<rustls::client::danger::HandshakeSignatureValid, rustls::Error> {
        Ok(rustls::client::danger::HandshakeSignatureValid::assertion())
    }

    fn verify_tls13_signature(
        &self,
        _message: &[u8],
        _cert: &rustls::pki_types::CertificateDer<'_>,
        _dss: &rustls::DigitallySignedStruct,
    ) -> std::result::Result<rustls::client::danger::HandshakeSignatureValid, rustls::Error> {
        Ok(rustls::client::danger::HandshakeSignatureValid::assertion())
    }

    fn supported_verify_schemes(&self) -> Vec<rustls::SignatureScheme> {
        vec![
            rustls::SignatureScheme::RSA_PKCS1_SHA1,
            rustls::SignatureScheme::ECDSA_SHA1_Legacy,
            rustls::SignatureScheme::RSA_PKCS1_SHA256,
            rustls::SignatureScheme::ECDSA_NISTP256_SHA256,
            rustls::SignatureScheme::RSA_PKCS1_SHA384,
            rustls::SignatureScheme::ECDSA_NISTP384_SHA384,
            rustls::SignatureScheme::RSA_PKCS1_SHA512,
            rustls::SignatureScheme::ECDSA_NISTP521_SHA512,
            rustls::SignatureScheme::RSA_PSS_SHA256,
            rustls::SignatureScheme::RSA_PSS_SHA384,
            rustls::SignatureScheme::RSA_PSS_SHA512,
            rustls::SignatureScheme::ED25519,
            rustls::SignatureScheme::ED448,
        ]
    }
}

// ── StreamAPI ────────────────────────────────────────────────────────────────

/// WebSocket streaming API.
pub struct StreamAPI {
    api_key: String,
    ws_url: String,
    insecure_tls: bool,
    http: HttpClient,
}

impl StreamAPI {
    pub(crate) fn new(
        api_key: String,
        ws_url: String,
        insecure_tls: bool,
        http: HttpClient,
    ) -> Self {
        Self {
            api_key,
            ws_url,
            insecure_tls,
            http,
        }
    }

    fn ws_base(&self) -> &str {
        self.ws_url.trim_end_matches('/')
    }

    fn build_public_url(
        &self,
        path: &str,
        params: &HashMap<&str, Option<String>>,
    ) -> Result<String> {
        let base = format!("{}{}", self.ws_base(), path);
        let mut url = Url::parse(&base)?;
        url.query_pairs_mut().append_pair("apiKey", &self.api_key);
        for (k, v) in params {
            if let Some(val) = v {
                url.query_pairs_mut().append_pair(k, val);
            }
        }
        Ok(url.to_string())
    }

    async fn open_public(
        &self,
        path: &str,
        params: HashMap<&str, Option<String>>,
    ) -> Result<MelayaStream> {
        let url = self.build_public_url(path, &params)?;
        open_ws(&url, self.insecure_tls).await
    }

    async fn open_private(
        &self,
        ws_path: &str,
        stream: &str,
        extra: HashMap<&str, Option<String>>,
    ) -> Result<MelayaStream> {
        let mut body = serde_json::json!({ "stream": stream });
        for (k, v) in &extra {
            if let Some(val) = v {
                body[*k] = Value::String(val.clone());
            }
        }
        let ticket_resp = self
            .http
            .post("/api/v1/private/private-ticket", &body)
            .await?;
        let ticket = ticket_resp["wsTicket"]
            .as_str()
            .ok_or_else(|| {
                MelayaError::Config("private-ticket response missing wsTicket".into())
            })?
            .to_owned();

        let base = format!("{}{}", self.ws_base(), ws_path);
        let mut url = Url::parse(&base)?;
        url.query_pairs_mut().append_pair("wsTicket", &ticket);
        open_ws(url.as_str(), self.insecure_tls).await
    }

    // ── Public streams ───────────────────────────────────────────────────────

    /// Live ticker frames.
    pub async fn ticker(
        &self,
        exchange: &str,
        symbol: &str,
        market: Option<&str>,
    ) -> Result<MelayaStream> {
        let mut p: HashMap<&str, Option<String>> = HashMap::new();
        p.insert("exchange", Some(exchange.to_owned()));
        p.insert("symbol", Some(symbol.to_owned()));
        p.insert("market", market.map(str::to_owned));
        self.open_public("/ws/ticker", p).await
    }

    /// Live order-book frames.
    pub async fn orderbook(
        &self,
        exchange: &str,
        symbol: &str,
        market: Option<&str>,
        limit: Option<u32>,
    ) -> Result<MelayaStream> {
        let mut p: HashMap<&str, Option<String>> = HashMap::new();
        p.insert("exchange", Some(exchange.to_owned()));
        p.insert("symbol", Some(symbol.to_owned()));
        p.insert("market", market.map(str::to_owned));
        p.insert("limit", limit.map(|v| v.to_string()));
        self.open_public("/ws/orderbook", p).await
    }

    /// Live OHLCV candle frames.
    pub async fn ohlcv(
        &self,
        exchange: &str,
        symbol: &str,
        timeframe: &str,
        market: Option<&str>,
    ) -> Result<MelayaStream> {
        let mut p: HashMap<&str, Option<String>> = HashMap::new();
        p.insert("exchange", Some(exchange.to_owned()));
        p.insert("symbol", Some(symbol.to_owned()));
        p.insert("timeframe", Some(timeframe.to_owned()));
        p.insert("market", market.map(str::to_owned));
        self.open_public("/ws/ohlcv", p).await
    }

    /// Live public-trade frames.
    pub async fn trades(
        &self,
        exchange: &str,
        symbol: &str,
        market: Option<&str>,
    ) -> Result<MelayaStream> {
        let mut p: HashMap<&str, Option<String>> = HashMap::new();
        p.insert("exchange", Some(exchange.to_owned()));
        p.insert("symbol", Some(symbol.to_owned()));
        p.insert("market", market.map(str::to_owned));
        self.open_public("/ws/public-trades", p).await
    }

    /// Cross-exchange liquidation firehose. Omit `exchange` for all venues.
    pub async fn liquidations(&self, exchange: Option<&str>) -> Result<MelayaStream> {
        let mut p: HashMap<&str, Option<String>> = HashMap::new();
        p.insert("exchange", exchange.map(str::to_owned));
        self.open_public("/ws/liquidations", p).await
    }

    // ── Private streams (ticket-minted) ──────────────────────────────────────

    /// Live strategy events for your account.
    /// Mints a short-lived ticket, then opens `/ws/strategies`.
    pub async fn strategies(&self) -> Result<MelayaStream> {
        self.open_private("/ws/strategies", "strategies", HashMap::new())
            .await
    }

    /// Live private account feed for one connected exchange key.
    /// Pass `api_key_id` from `account.keys()`.
    pub async fn private(
        &self,
        exchange: &str,
        market: Option<&str>,
        api_key_id: Option<&str>,
        key_id: Option<&str>,
        symbol: Option<&str>,
    ) -> Result<MelayaStream> {
        let mut extra: HashMap<&str, Option<String>> = HashMap::new();
        extra.insert("exchange", Some(exchange.to_owned()));
        extra.insert("market", market.map(str::to_owned));
        extra.insert("apiKeyId", api_key_id.map(str::to_owned));
        extra.insert("keyId", key_id.map(str::to_owned));
        extra.insert("symbol", symbol.map(str::to_owned));
        self.open_private("/ws/private", "private", extra).await
    }
}
