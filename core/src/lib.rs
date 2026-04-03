//! Cloudfin Core - Dynamic Module Runtime

pub mod plugins;
pub mod config;
pub mod logging;

pub use plugins::PluginManager;
pub use logging::initLogging;

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use tokio::sync::RwLock;

/// API response wrapper
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ApiResponse<T> {
    pub ok: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub data: Option<T>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

impl<T> ApiResponse<T> {
    pub fn ok(data: T) -> Self {
        Self { ok: true, data: Some(data), error: None }
    }
    pub fn err(error: impl Into<String>) -> Self {
        Self { ok: false, data: None, error: Some(error.into()) }
    }
}

/// Module status
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum ModuleStatus {
    Loading,
    Loaded,
    Running,
    Unloading,
    Failed,
    Unloaded,
}

/// A loaded module
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Module {
    pub id: String,
    pub name: String,
    pub path: String,
    pub status: ModuleStatus,
    #[serde(with = "chrono::serde::ts_milliseconds")]
    pub registered_at: DateTime<Utc>,
}

/// Core configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Config {
    pub host: String,
    pub port: u16,
    pub log_level: String,
    pub modules_dir: String,
    #[serde(default)]
    pub features: serde_json::Value,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            host: "127.0.0.1".into(),
            port: 19001,
            log_level: "info".into(),
            modules_dir: "./modules".into(),
            features: serde_json::json!({}),
        }
    }
}

/// Application shared state
#[derive(Debug, Clone)]
pub struct AppState {
    pub version: String,
    pub started_at: DateTime<Utc>,
    pub config: Arc<RwLock<Config>>,
    pub plugin_manager: Arc<PluginManager>,
}

pub type SharedState = Arc<AppState>;
