use std::collections::HashMap;
use std::time::Duration;

use reqwest::{
    header::{HeaderMap, HeaderValue, AUTHORIZATION, CONTENT_TYPE},
    ClientBuilder,
};
use serde_json::Value;
use tokio::time::sleep;
use url::Url;

use crate::error::{MelayaError, Result};

pub const DEFAULT_BASE_URL: &str = "https://api.melaya.org";
pub const DEFAULT_WS_URL: &str = "wss://wss.melaya.org";

/// Default per-request timeout (30 s).
const DEFAULT_TIMEOUT_MS: u64 = 30_000;
/// Maximum GET retries on network error / 429 / 5xx.
const MAX_GET_RETRIES: u32 = 2;

/// HTTP client that injects the API key on every call and unwraps the
/// `{ ok, <data> }` envelope.
///
/// `Clone` is cheap: the underlying `reqwest::Client` is `Arc`-backed.
#[derive(Clone)]
pub struct HttpClient {
    inner: reqwest::Client,
    api_key: String,
    base_url: String,
    /// Per-request timeout in milliseconds.
    timeout_ms: u64,
}

impl HttpClient {
    pub fn new(api_key: String, base_url: String) -> Result<Self> {
        Self::new_with_timeout(api_key, base_url, DEFAULT_TIMEOUT_MS)
    }

    pub fn new_with_timeout(api_key: String, base_url: String, timeout_ms: u64) -> Result<Self> {
        let mut headers = HeaderMap::new();
        let bearer = format!("Bearer {api_key}");
        headers.insert(
            AUTHORIZATION,
            HeaderValue::from_str(&bearer).map_err(|e| MelayaError::Config(e.to_string()))?,
        );

        let builder = ClientBuilder::new().default_headers(headers);
        let inner = builder.build()?;
        Ok(Self {
            inner,
            api_key,
            base_url,
            timeout_ms,
        })
    }

    /// Build a URL from a path + optional query params.
    ///
    /// SECURITY: the API key is NOT injected here. For REST it travels only
    /// in the `Authorization: Bearer` header (set on the reqwest client), so
    /// it cannot leak into access logs, proxies, or referrer headers. The
    /// only query-string auth in the SDK is on WebSocket streams: public
    /// streams use `?apiKey=` (server protocol) and private streams use a
    /// short-lived `?wsTicket=` instead of the API key (see `stream.rs`).
    fn build_url(&self, path: &str, query: Option<&[(&str, &str)]>) -> Result<Url> {
        let base = if self.base_url.ends_with('/') {
            self.base_url.clone()
        } else {
            format!("{}/", self.base_url)
        };
        let stripped = path.trim_start_matches('/');
        let mut url = Url::parse(&format!("{base}{stripped}"))?;
        if let Some(pairs) = query {
            for (k, v) in pairs {
                url.query_pairs_mut().append_pair(k, v);
            }
        }
        Ok(url)
    }

    /// Parse a response: check HTTP status, then check `ok` field.
    async fn parse(&self, resp: reqwest::Response) -> Result<Value> {
        let status = resp.status().as_u16();
        let text = resp.text().await?;
        let data: Value = if text.is_empty() {
            Value::Null
        } else {
            serde_json::from_str(&text).unwrap_or(Value::String(text))
        };

        if status >= 400 {
            let code = data
                .get("error")
                .and_then(|v| v.as_str())
                .map(str::to_owned);
            return Err(MelayaError::Api {
                status,
                code,
                body: Some(data),
            });
        }

        if let Some(ok) = data.get("ok") {
            if ok == &Value::Bool(false) {
                let code = data
                    .get("error")
                    .and_then(|v| v.as_str())
                    .map(str::to_owned);
                return Err(MelayaError::Api {
                    status,
                    code,
                    body: Some(data),
                });
            }
        }

        Ok(data)
    }

    /// GET with optional query params (as owned strings, filtered for None).
    ///
    /// Retries up to `MAX_GET_RETRIES` times on network error, 429, or 5xx,
    /// with exponential back-off. Honors `Retry-After` on 429.
    pub async fn get(&self, path: &str, query: &HashMap<&str, Option<String>>) -> Result<Value> {
        let pairs: Vec<(&str, String)> = query
            .iter()
            .filter_map(|(k, v)| v.as_ref().map(|s| (*k, s.clone())))
            .collect();
        let str_pairs: Vec<(&str, &str)> = pairs.iter().map(|(k, v)| (*k, v.as_str())).collect();
        let url = self.build_url(path, Some(&str_pairs))?;
        let timeout = Duration::from_millis(self.timeout_ms);

        let mut last_err: Option<MelayaError> = None;
        // Set when Retry-After was honored on a 429; causes the next attempt
        // to skip the regular exponential back-off (they are mutually exclusive).
        let mut retry_after_honored = false;
        for attempt in 0..=MAX_GET_RETRIES {
            if attempt > 0 && !retry_after_honored {
                // Exponential back-off: 500 ms, 1000 ms (±20 % jitter).
                let base_ms = 500u64 * (1u64 << (attempt - 1));
                let jitter = base_ms / 5;
                sleep(Duration::from_millis(base_ms + jitter)).await;
            }
            retry_after_honored = false;

            let req = self.inner.get(url.clone()).timeout(timeout);

            match req.send().await {
                Err(e) => {
                    // Network / connect error — retry.
                    last_err = Some(e.into());
                    continue;
                }
                Ok(resp) => {
                    let status = resp.status().as_u16();
                    // On 429 honor Retry-After header if present; skip the
                    // normal per-attempt backoff on the next iteration.
                    if status == 429 {
                        if attempt < MAX_GET_RETRIES {
                            let retry_after_ms = resp
                                .headers()
                                .get("retry-after")
                                .and_then(|v| v.to_str().ok())
                                .and_then(|s| s.parse::<u64>().ok())
                                .map(|secs| secs * 1_000)
                                .unwrap_or(1_000);
                            sleep(Duration::from_millis(retry_after_ms)).await;
                            retry_after_honored = true;
                            last_err = Some(MelayaError::Api {
                                status,
                                code: Some("RATE_LIMITED".into()),
                                body: None,
                            });
                            continue;
                        }
                    }
                    // Retry 5xx.
                    if status >= 500 && attempt < MAX_GET_RETRIES {
                        last_err = Some(MelayaError::Api {
                            status,
                            code: None,
                            body: None,
                        });
                        continue;
                    }
                    return self.parse(resp).await;
                }
            }
        }
        Err(last_err.unwrap_or_else(|| MelayaError::Config("GET failed after retries".into())))
    }

    /// POST with a JSON body (no retry — non-idempotent).
    pub async fn post(&self, path: &str, body: &Value) -> Result<Value> {
        let url = self.build_url(path, None)?;
        let timeout = Duration::from_millis(self.timeout_ms);
        let resp = self
            .inner
            .post(url)
            .header(CONTENT_TYPE, "application/json")
            .json(body)
            .timeout(timeout)
            .send()
            .await?;
        self.parse(resp).await
    }

    /// PUT with a JSON body (no retry — non-idempotent).
    pub async fn put(&self, path: &str, body: &Value) -> Result<Value> {
        let url = self.build_url(path, None)?;
        let timeout = Duration::from_millis(self.timeout_ms);
        let resp = self
            .inner
            .put(url)
            .header(CONTENT_TYPE, "application/json")
            .json(body)
            .timeout(timeout)
            .send()
            .await?;
        self.parse(resp).await
    }

    /// PATCH with a JSON body (no retry — non-idempotent).
    pub async fn patch(&self, path: &str, body: &Value) -> Result<Value> {
        let url = self.build_url(path, None)?;
        let timeout = Duration::from_millis(self.timeout_ms);
        let resp = self
            .inner
            .patch(url)
            .header(CONTENT_TYPE, "application/json")
            .json(body)
            .timeout(timeout)
            .send()
            .await?;
        self.parse(resp).await
    }

    /// DELETE (no body, no retry — non-idempotent).
    pub async fn delete(&self, path: &str, query: &HashMap<&str, Option<String>>) -> Result<Value> {
        let pairs: Vec<(&str, String)> = query
            .iter()
            .filter_map(|(k, v)| v.as_ref().map(|s| (*k, s.clone())))
            .collect();
        let str_pairs: Vec<(&str, &str)> = pairs.iter().map(|(k, v)| (*k, v.as_str())).collect();
        let url = self.build_url(path, Some(&str_pairs))?;
        let timeout = Duration::from_millis(self.timeout_ms);
        let resp = self.inner.delete(url).timeout(timeout).send().await?;
        self.parse(resp).await
    }

    #[allow(dead_code)]
    pub fn api_key(&self) -> &str {
        &self.api_key
    }
}
