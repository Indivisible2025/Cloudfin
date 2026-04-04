//! Cloudfin Core Server

use std::sync::Arc;

use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        Path, State,
    },
    routing,
    Json, Router,
};
use chrono::{DateTime, Utc};
use futures::{sink::SinkExt, stream::StreamExt};
use serde::{Deserialize, Serialize};
use serde_json::json;
use tokio::sync::{broadcast, RwLock};
use parking_lot::Mutex;
use tower_http::trace::TraceLayer;
use uuid::Uuid;

use cloudfin_core::{Module, PluginManager};

// === Shared Types ===

#[derive(Clone)]
struct AppState {
    version: String,
    started_at: DateTime<Utc>,
    config: Arc<RwLock<Config>>,
    plugin_manager: Arc<Mutex<PluginManager>>,
}

type SharedState = Arc<AppState>;

#[derive(Debug, Serialize)]
pub struct ApiResponse<T> {
    pub ok: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub data: Option<T>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

impl<T: Serialize> ApiResponse<T> {
    pub fn ok(data: T) -> Self {
        Self { ok: true, data: Some(data), error: None }
    }
    pub fn err(msg: impl ToString) -> Self {
        Self { ok: false, data: None, error: Some(msg.to_string()) }
    }
}

#[derive(Clone)]
struct AppCtx {
    inner: SharedState,
    broadcaster: broadcast::Sender<WsMessage>,
}

#[derive(Clone, Debug)]
enum WsMessage {
    Response { request_id: String, data: serde_json::Value },
    StatusUpdate(serde_json::Value),
    ModuleChanged { id: String, status: String },
}

// === WebSocket ===

async fn ws_handler(
    ws: WebSocketUpgrade,
    State(ctx): State<AppCtx>,
) -> axum::response::Response {
    ws.on_upgrade(|socket| ws_socket_handler(socket, ctx))
}

async fn ws_socket_handler(socket: WebSocket, ctx: AppCtx) {
    let (mut sender, mut receiver) = socket.split();
    let _client_id = Uuid::new_v4().to_string();
    let mut broadcast_rx = ctx.broadcaster.subscribe();

    let status = build_status_json(&ctx.inner).await;
    let init_msg = serde_json::to_string(&json!({
        "type": "status_update",
        "data": status
    })).unwrap();
    let _ = sender.send(Message::Text(init_msg)).await;

    loop {
        tokio::select! {
            msg = broadcast_rx.recv() => {
                match msg {
                    Ok(WsMessage::Response { request_id, data }) => {
                        let text = serde_json::to_string(&json!({
                            "type": "response",
                            "request_id": request_id,
                            "data": data
                        })).unwrap();
                        let _ = sender.send(Message::Text(text)).await;
                    }
                    Ok(WsMessage::StatusUpdate(data)) => {
                        let text = serde_json::to_string(&json!({
                            "type": "status_update",
                            "data": data
                        })).unwrap();
                        let _ = sender.send(Message::Text(text)).await;
                    }
                    Ok(WsMessage::ModuleChanged { id, status }) => {
                        let text = serde_json::to_string(&json!({
                            "type": "module_status_changed",
                            "module_id": id,
                            "status": status
                        })).unwrap();
                        let _ = sender.send(Message::Text(text)).await;
                    }
                    Err(broadcast::error::RecvError::Lagged(_)) => {}
                    Err(broadcast::error::RecvError::Closed) => break,
                }
            }
            msg = receiver.next() => {
                match msg {
                    Some(Ok(Message::Text(text))) => {
                        if let Err(e) = handle_ws_request(&text, &ctx).await {
                            let text = serde_json::to_string(&json!({
                                "type": "error",
                                "error": e
                            })).unwrap();
                            let _ = sender.send(Message::Text(text)).await;
                        }
                    }
                    Some(Ok(Message::Close(_))) | None => break,
                    _ => {}
                }
            }
        }
    }
}

async fn handle_ws_request(text: &str, ctx: &AppCtx) -> Result<(), String> {
    let req: serde_json::Value =
        serde_json::from_str(text).map_err(|e| format!("Invalid JSON: {}", e))?;

    let action = req["action"]
        .as_str()
        .ok_or("Missing 'action' field")?;
    let request_id = req["request_id"].as_str().unwrap_or("");
    let data = req["data"].clone();

    match action {
        "get_core_status" => {
            let status = build_status_json(&ctx.inner).await;
            let _ = ctx.broadcaster.send(WsMessage::Response {
                request_id: request_id.to_string(),
                data: status,
            });
        }
        "get_modules" => {
            let pm = Arc::clone(&ctx.inner.plugin_manager);
            let _guard = pm.lock();
            let modules = _guard.list();
            drop(_guard);
            let _ = ctx.broadcaster.send(WsMessage::Response {
                request_id: request_id.to_string(),
                data: json!({ "modules": modules }),
            });
        }
        "get_config" => {
            let config = ctx.inner.config.read().await.clone();
            let _ = ctx.broadcaster.send(WsMessage::Response {
                request_id: request_id.to_string(),
                data: serde_json::to_value(&config).unwrap_or_default(),
            });
        }
        "load_module" => {
            let path = data["path"].as_str().ok_or("Missing 'path'")?;
            let pm = Arc::clone(&ctx.inner.plugin_manager);
            let mut _guard = pm.lock();
            match _guard.load_plugin(&std::path::PathBuf::from(path)) {
                Ok(id) => {
                    let _ = ctx.broadcaster.send(WsMessage::ModuleChanged {
                        id: path.to_string(),
                        status: "loaded".to_string(),
                    });
                    let _ = ctx.broadcaster.send(WsMessage::Response {
                        request_id: request_id.to_string(),
                        data: json!({ "ok": true, "module_id": id }),
                    });
                }
                Err(e) => {
                    let data: serde_json::Value = json!({ "ok": false, "error": e.to_string() });
                    let _ = ctx.broadcaster.send(WsMessage::Response {
                        request_id: request_id.to_string(),
                        data,
                    });
                }
            }
        }
        "get_p2p_state" => {
            let _ = ctx.broadcaster.send(WsMessage::Response {
                request_id: request_id.to_string(),
                data: json!({ "connected_peers": 0, "listening_addr": "" }),
            });
        }
        "dial_peer" => {
            let _addr = data["addr"].as_str().ok_or("Missing 'addr'")?;
            tracing::info!("WS: dial_peer request");
            let _ = ctx.broadcaster.send(WsMessage::Response {
                request_id: request_id.to_string(),
                data: json!({ "ok": true }),
            });
        }
        _ => return Err(format!("Unknown action: {}", action)),
    }

    Ok(())
}

async fn build_status_json(state: &SharedState) -> serde_json::Value {
    let uptime = Utc::now()
        .signed_duration_since(state.started_at)
        .num_seconds();
    let pm = Arc::clone(&state.plugin_manager);
    let _guard = pm.lock();
    let modules = _guard.list();
    drop(_guard);

    serde_json::json!({
        "version": state.version,
        "uptime_secs": uptime,
        "modules": modules,
    })
}

// === HTTP Handlers ===

async fn get_status(State(ctx): State<AppCtx>) -> Json<ApiResponse<serde_json::Value>> {
    let data = build_status_json(&ctx.inner).await;
    Json(ApiResponse::ok(data))
}

async fn get_config(State(ctx): State<AppCtx>) -> Json<ApiResponse<Config>> {
    let config = ctx.inner.config.read().await.clone();
    Json(ApiResponse::ok(config))
}

async fn list_modules(State(ctx): State<AppCtx>) -> Json<ApiResponse<Vec<String>>> {
    let pm = Arc::clone(&ctx.inner.plugin_manager);
    let _guard = pm.lock();
    let modules = _guard.list();
    drop(_guard);
    Json(ApiResponse::ok(modules))
}

async fn load_plugin(
    State(ctx): State<AppCtx>,
    Json(payload): Json<serde_json::Value>,
) -> Json<ApiResponse<String>> {
    let path = payload
        .get("path")
        .and_then(|v| v.as_str())
        .ok_or("missing 'path' field");

    match path {
        Ok(p) => {
            let pm = Arc::clone(&ctx.inner.plugin_manager);
            let mut _guard = pm.lock();
            match _guard.load_plugin(&std::path::PathBuf::from(p)) {
                Ok(id) => Json(ApiResponse::ok(id)),
                Err(e) => Json(ApiResponse::err(e)),
            }
        }
        Err(e) => Json(ApiResponse::err(e)),
    }
}

// === Logging Init ===

fn init_logging() {
    use tracing_subscriber::{fmt, prelude::*, EnvFilter};
    let filter = EnvFilter::try_from_default_env()
        .unwrap_or_else(|_| EnvFilter::new("info"));
    tracing_subscriber::registry()
        .with(fmt::layer())
        .with(filter)
        .init();
}

// === Config (local override) ===

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Config {
    pub version: String,
    pub modules_dir: String,
    pub host: String,
    pub port: u16,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            version: "0.1.0".into(),
            modules_dir: "./modules".into(),
            host: "127.0.0.1".into(),
            port: 19001,
        }
    }
}

// === Main ===

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    init_logging();
    tracing::info!("Starting Cloudfin Core...");

    let config = Config::default();
    let plugins_dir = std::path::PathBuf::from(&config.modules_dir);
    let plugins_dir_clone = plugins_dir.clone();

    let (broadcaster, _) = broadcast::channel::<WsMessage>(100);

    let mut plugin_manager = PluginManager::new(plugins_dir);
    plugin_manager.load_all()?;

    let state: SharedState = Arc::new(AppState {
        version: env!("CARGO_PKG_VERSION").to_string(),
        started_at: Utc::now(),
        config: Arc::new(RwLock::new(config)),
        plugin_manager: Arc::new(Mutex::new(plugin_manager)),
    });

    let ctx = AppCtx {
        inner: state,
        broadcaster,
    };

    let host = ctx.inner.config.read().await.host.clone();
    let port = ctx.inner.config.read().await.port;

    let app = Router::new()
        .route("/api/core/status", routing::get(get_status))
        .route("/api/config", routing::get(get_config))
        .route("/api/modules", routing::get(list_modules))
        .route("/api/plugins/load", routing::post(load_plugin))
        .route("/ws", routing::get(ws_handler))
        .route("/ping", routing::get(|| async { "pong" }))
        .layer(TraceLayer::new_for_http())
        .with_state(ctx);

    let addr = format!("{}:{}", host, port);
    tracing::info!("Listening on {}", addr);
    tracing::info!("WebSocket available at ws://{}:{}/ws", host, port);
    tracing::info!("Modules dir: {}", plugins_dir_clone.display());

    let listener = tokio::net::TcpListener::bind(&addr).await?;
    axum::serve(listener, app).await?;

    Ok(())
}
