//! Cloudfin Core Server

use std::sync::Arc;
use std::path::PathBuf;

use axum::{
    extract::{Path, State},
    routing,
    Json, Router,
};
use chrono::Utc;
use tokio::sync::RwLock;
use tower_http::trace::TraceLayer;

use cloudfin_core::{
    ApiResponse, AppState, Config, Module, ModuleStatus, PluginManager, SharedState,
};

type ApiResponse<T> = cloudfin_core::ApiResponse<T>;

#[derive(Clone)]
struct AppStateExt {
    inner: SharedState,
}

async fn get_status(State(state): State<SharedState>) -> Json<ApiResponse<serde_json::Value>> {
    let uptime = Utc::now()
        .signed_duration_since(state.started_at)
        .num_seconds();

    let data = serde_json::json!({
        "version": state.version,
        "uptime_secs": uptime,
        "modules": state.plugin_manager.list_modules(),
    });

    Json(ApiResponse::ok(data))
}

async fn get_config(State(state): State<SharedState>) -> Json<ApiResponse<Config>> {
    let config = state.config.read().await.clone();
    Json(ApiResponse::ok(config))
}

async fn list_modules(State(state): State<SharedState>) -> Json<ApiResponse<Vec<String>>> {
    let modules = state.plugin_manager.list_modules();
    Json(ApiResponse::ok(modules))
}

async fn load_plugin(
    State(state): State<SharedState>,
    Json(payload): Json<serde_json::Value>,
) -> Json<ApiResponse<Module>> {
    let path = match payload.get("path").and_then(|v| v.as_str()) {
        Some(p) => PathBuf::from(p),
        None => return Json(ApiResponse::err("missing 'path' field")),
    };

    let result = state.plugin_manager.load_module(&path);
    Json(result)
}

async fn unload_plugin(
    State(state): State<SharedState>,
    Path(id): Path<String>,
) -> Json<ApiResponse<()>> {
    let result = state.plugin_manager.unload_module(&id);
    Json(result)
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Initialize logging
    cloudfin_core::initLogging();

    tracing::info!("Starting Cloudfin Core...");

    // Load config
    let config = Config::default();
    let plugins_dir = PathBuf::from(&config.modules_dir);

    // Create shared state
    let state: SharedState = Arc::new(AppState {
        version: env!("CARGO_PKG_VERSION").to_string(),
        started_at: Utc::now(),
        config: Arc::new(RwLock::new(config)),
        plugin_manager: Arc::new(PluginManager::new(plugins_dir)),
    });

    let app = Router::new()
        .route("/api/core/status", routing::get(get_status))
        .route("/api/config", routing::get(get_config))
        .route("/api/modules", routing::get(list_modules))
        .route("/api/plugins/load", routing::post(load_plugin))
        .route("/api/plugins/unload/:id", routing::delete(unload_plugin))
        .layer(TraceLayer::new_for_http())
        .with_state(state.clone());

    let host = state.config.read().await.host.clone();
    let port = state.config.read().await.port;

    let addr = format!("{}:{}", host, port);
    tracing::info!("Listening on {}", addr);

    let listener = tokio::net::TcpListener::bind(&addr).await?;
    axum::serve(listener, app).await?;

    Ok(())
}
