use std::collections::HashMap;

use reqwest::{
    header::{HeaderMap, HeaderValue, AUTHORIZATION, CONTENT_TYPE},
    ClientBuilder,
};
use serde_json::Value;
use url::Url;

use crate::error::{MelayaError, Result};

pub const DEFAULT_BASE_URL: &str = "https://api.melaya.org";
pub const DEFAULT_WS_URL: &str = "wss://wss.melaya.org";

/// HTTP client that injects the API key on every call and unwraps the
/// `{ ok, <data> }` envelope.
#[derive(Clone)]
pub struct HttpClient {
    inner: reqwest::Client,
    api_key: String,
    base_url: String,
}

impl HttpClient {
    pub fn new(api_key: String, base_url: String, insecure_tls: bool) -> Result<Self> {
        let mut headers = HeaderMap::new();
        let bearer = format!("Bearer {api_key}");
        headers.insert(
            AUTHORIZATION,
            HeaderValue::from_str(&bearer)
                .map_err(|e| MelayaError::Config(e.to_string()))?,
        );

        let mut builder = ClientBuilder::new().default_headers(headers);
        if insecure_tls {
            builder = builder.danger_accept_invalid_certs(true);
        }
        let inner = builder.build()?;
        Ok(Self { inner, api_key, base_url })
    }

    /// Build a URL from a path + optional query params; always injects `apiKey`.
    fn build_url(
        &self,
        path: &str,
        query: Option<&[(&str, &str)]>,
    ) -> Result<Url> {
        let base = if self.base_url.ends_with('/') {
            self.base_url.clone()
        } else {
            format!("{}/", self.base_url)
        };
        let stripped = path.trim_start_matches('/');
        let mut url = Url::parse(&format!("{base}{stripped}"))?;
        url.query_pairs_mut().append_pair("apiKey", &self.api_key);
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
    pub async fn get(
        &self,
        path: &str,
        query: &HashMap<&str, Option<String>>,
    ) -> Result<Value> {
        let pairs: Vec<(&str, String)> = query
            .iter()
            .filter_map(|(k, v)| v.as_ref().map(|s| (*k, s.clone())))
            .collect();
        let str_pairs: Vec<(&str, &str)> = pairs.iter().map(|(k, v)| (*k, v.as_str())).collect();
        let url = self.build_url(path, Some(&str_pairs))?;
        let resp = self.inner.get(url).send().await?;
        self.parse(resp).await
    }

    /// POST with a JSON body.
    pub async fn post(&self, path: &str, body: &Value) -> Result<Value> {
        let url = self.build_url(path, None)?;
        let resp = self
            .inner
            .post(url)
            .header(CONTENT_TYPE, "application/json")
            .json(body)
            .send()
            .await?;
        self.parse(resp).await
    }

    /// DELETE (no body).
    pub async fn delete(&self, path: &str, query: &HashMap<&str, Option<String>>) -> Result<Value> {
        let pairs: Vec<(&str, String)> = query
            .iter()
            .filter_map(|(k, v)| v.as_ref().map(|s| (*k, s.clone())))
            .collect();
        let str_pairs: Vec<(&str, &str)> = pairs.iter().map(|(k, v)| (*k, v.as_str())).collect();
        let url = self.build_url(path, Some(&str_pairs))?;
        let resp = self.inner.delete(url).send().await?;
        self.parse(resp).await
    }

    pub fn api_key(&self) -> &str {
        &self.api_key
    }
}
