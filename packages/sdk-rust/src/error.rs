use thiserror::Error;

/// Errors returned by the Melaya SDK.
#[derive(Debug, Error)]
pub enum MelayaError {
    /// The API returned HTTP >= 400 or `{ ok: false }`.
    #[error("Melaya API {status}{}", format_code(.code))]
    Api {
        status: u16,
        code: Option<String>,
        body: Option<serde_json::Value>,
    },

    /// A network or TLS error from reqwest.
    #[error("HTTP error: {0}")]
    Http(#[from] reqwest::Error),

    /// A WebSocket error from tungstenite.
    #[error("WebSocket error: {0}")]
    Ws(#[from] tokio_tungstenite::tungstenite::Error),

    /// JSON serialisation / deserialisation error.
    #[error("JSON error: {0}")]
    Json(#[from] serde_json::Error),

    /// URL parse error.
    #[error("URL error: {0}")]
    Url(#[from] url::ParseError),

    /// Configuration error (bad key prefix, missing env var, etc.).
    #[error("Config error: {0}")]
    Config(String),
}

fn format_code(code: &Option<String>) -> String {
    match code {
        Some(c) => format!(" ({c})"),
        None => String::new(),
    }
}

pub type Result<T> = std::result::Result<T, MelayaError>;
