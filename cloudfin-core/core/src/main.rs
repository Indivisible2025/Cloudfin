//! Cloudfin Core Server

use std::sync::Arc;

use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        State,
    },
    routing,
    Router,
};
use chrono::{DateTime, Utc};
use futures::{sink::SinkExt, stream::StreamExt};
use serde::{Deserialize, Serialize};
use serde_json::json;
use tokio::sync::{broadcast, RwLock};
use parking_lot::Mutex;
use tower_http::trace::TraceLayer;
use uuid::Uuid;

use cloudfin_core::PluginManager;

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
    Response { id: serde_json::Value, result: serde_json::Value },
    Notification { method: String, params: serde_json::Value },
    ModuleAdded(serde_json::Value),
    ModuleRemoved(String),
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
        "jsonrpc": "2.0",
        "method": "status_update",
        "params": status
    })).unwrap();
    let _ = sender.send(Message::Text(init_msg)).await;

    loop {
        tokio::select! {
            msg = broadcast_rx.recv() => {
                match msg {
                    Ok(WsMessage::Response { id, result }) => {
                        let text = serde_json::to_string(&json!({
                            "jsonrpc": "2.0",
                            "id": id,
                            "result": result
                        })).unwrap();
                        let _ = sender.send(Message::Text(text)).await;
                    }
                    Ok(WsMessage::Notification { method, params }) => {
                        let text = serde_json::to_string(&json!({
                            "jsonrpc": "2.0",
                            "method": method,
                            "params": params
                        })).unwrap();
                        let _ = sender.send(Message::Text(text)).await;
                    }
                    Ok(WsMessage::ModuleAdded(data)) => {
                        let text = serde_json::to_string(&json!({
                            "jsonrpc": "2.0",
                            "method": "module_added",
                            "params": data
                        })).unwrap();
                        let _ = sender.send(Message::Text(text)).await;
                    }
                    Ok(WsMessage::ModuleRemoved(id)) => {
                        let text = serde_json::to_string(&json!({
                            "jsonrpc": "2.0",
                            "method": "module_removed",
                            "params": { "id": id }
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
                                "jsonrpc": "2.0",
                                "error": { "code": -32600, "message": e }
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

    // Validate JSON-RPC 2.0
    if req["jsonrpc"].as_str() != Some("2.0") {
        return Err("Invalid JSON-RPC version, expected '2.0'".to_string());
    }

    let method = req["method"]
        .as_str()
        .ok_or("Missing 'method' field")?;

    // id can be number or string or null; use json value to preserve type
    let id = req["id"].clone();

    // params from request, or fall back to "data" for backward compat
    let params = if req["params"].is_null() {
        req["data"].clone()
    } else {
        req["params"].clone()
    };

    match method {
        "get_core_status" => {
            let status = build_status_json(&ctx.inner).await;
            let _ = ctx.broadcaster.send(WsMessage::Response {
                id,
                result: status,
            });
        }
        "get_modules" => {
            let pm = Arc::clone(&ctx.inner.plugin_manager);
            let _guard = pm.lock();
            let modules = _guard.list_full();
            drop(_guard);
            let _ = ctx.broadcaster.send(WsMessage::Response {
                id,
                result: json!({"modules": modules}),
            });
        }
        "get_config" => {
            let config = ctx.inner.config.read().await.clone();
            let _ = ctx.broadcaster.send(WsMessage::Response {
                id,
                result: serde_json::to_value(&config).unwrap_or_default(),
            });
        }
        "load_module" => {
            let path = params["path"].as_str().ok_or("Missing 'path' in params")?;
            let pm = Arc::clone(&ctx.inner.plugin_manager);
            let mut guard = pm.lock();
            match guard.load_plugin(&std::path::PathBuf::from(path)) {
                Ok((module_id, meta)) => {
                    drop(guard);
                    let _ = ctx.broadcaster.send(WsMessage::ModuleAdded(json!({
                        "id": module_id,
                        "name": meta.name,
                        "version": meta.version,
                    })));
                    let _ = ctx.broadcaster.send(WsMessage::Response {
                        id: id.clone(),
                        result: json!({"ok": true, "module_id": module_id}),
                    });
                }
                Err(e) => {
                    drop(guard);
                    let result: serde_json::Value = json!({"ok": false, "error": e.to_string()});
                    let _ = ctx.broadcaster.send(WsMessage::Response {
                        id,
                        result,
                    });
                }
            }
        }
        "modules.list" => {
            let pm = Arc::clone(&ctx.inner.plugin_manager);
            let guard = pm.lock();
            let modules = guard.list_full();
            drop(guard);
            let _ = ctx.broadcaster.send(WsMessage::Response {
                id,
                result: json!({"modules": modules}),
            });
        }
        "modules.rescan" => {
            let pm = Arc::clone(&ctx.inner.plugin_manager);
            let mut guard = pm.lock();
            let (loaded, failed) = guard.rescan();
            drop(guard);
            let _ = ctx.broadcaster.send(WsMessage::Response {
                id,
                result: json!({"ok": true, "loaded": loaded, "failed": failed}),
            });
        }
        "modules.unload" => {
            let mid = params["id"].as_str().ok_or("missing id")?;
            let pm = Arc::clone(&ctx.inner.plugin_manager);
            let mut guard = pm.lock();
            match guard.unload_plugin(mid) {
                Ok(_) => {
                    drop(guard);
                    let _ = ctx.broadcaster.send(WsMessage::ModuleRemoved(mid.to_string()));
                    let _ = ctx.broadcaster.send(WsMessage::Response {
                        id,
                        result: json!({"ok": true}),
                    });
                }
                Err(e) => {
                    drop(guard);
                    let _ = ctx.broadcaster.send(WsMessage::Response {
                        id,
                        result: json!({"ok": false, "error": e.to_string()}),
                    });
                }
            }
        }
        "get_p2p_state" => {
            let _ = ctx.broadcaster.send(WsMessage::Response {
                id,
                result: json!({"connected_peers": 0, "listening_addr": ""}),
            });
        }
        "dial_peer" => {
            let _addr = params["addr"].as_str().ok_or("Missing 'addr' in params")?;
            tracing::info!("WS: dial_peer request");
            let _ = ctx.broadcaster.send(WsMessage::Response {
                id,
                result: json!({"ok": true}),
            });
        }
        _ => return Err(format!("Unknown method: {}", method)),
    }

    Ok(())
}

async fn build_status_json(state: &SharedState) -> serde_json::Value {
    let uptime = Utc::now()
        .signed_duration_since(state.started_at)
        .num_seconds();
    let pm = Arc::clone(&state.plugin_manager);
    let _guard = pm.lock();
    let modules = _guard.list_full();
    drop(_guard);

    serde_json::json!({
        "version": state.version,
        "uptime_secs": uptime,
        "modules": modules,
    })
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

    let mut plugin_manager = PluginManager::new(plugins_dir.clone());
    plugin_manager.load_all()?;

    // Start file-system watcher for new .so modules
    let module_dir = plugins_dir.clone();
    tokio::spawn(async move {
        use notify::{RecursiveMode, Watcher};
        let (tx, rx) = std::sync::mpsc::channel();
        let mut watcher = notify::recommended_watcher(tx).unwrap();
        watcher.watch(module_dir.as_path(), RecursiveMode::NonRecursive).unwrap();
        loop {
            match rx.recv() {
                Ok(Ok(event)) => {
                    if let notify::EventKind::Create(_) = event.kind {
                        for path in event.paths {
                            if path.extension().and_then(|s| s.to_str()) == Some("so") {
                                tracing::info!("New .so detected: {:?}", path);
                            }
                        }
                    }
                }
                Ok(Err(_)) | Err(_) => break,
            }
        }
    });

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
