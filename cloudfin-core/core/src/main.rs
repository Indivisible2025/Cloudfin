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
use chrono::Utc;
use futures::{sink::SinkExt, stream::StreamExt};
use serde_json::json;
use tokio::sync::{broadcast, RwLock};
use tower_http::trace::TraceLayer;
use uuid::Uuid;

use cloudfin_core::{ApiResponse, AppState, Config, Module, PluginManager, SharedState};

/// Shared state for all HTTP/WS handlers
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

    // Send initial status
    let status = build_status_json(&ctx.inner).await;
    let init_msg = serde_json::to_string(&json!({
        "type": "status_update",
        "data": status
    })).unwrap();
    let _ = sender.send(Message::Text(init_msg)).await;

    loop {
        tokio::select! {
            // Broadcast to this client
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

            // Message from client
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
            let modules = ctx.inner.plugin_manager.list_modules();
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
            let result = ctx.inner.plugin_manager.load_module(&std::path::PathBuf::from(path));
            let ok = result.ok;
            let _ = ctx.broadcaster.send(WsMessage::ModuleChanged {
                id: path.to_string(),
                status: if ok { "loaded".to_string() } else { "error".to_string() },
            });
            let resp_data = if ok {
                json!({ "ok": true })
            } else {
                json!({ "ok": false, "error": result.error })
            };
            let _ = ctx.broadcaster.send(WsMessage::Response {
                request_id: request_id.to_string(),
                data: resp_data,
            });
        }
        "unload_module" => {
            let module_id = data["module_id"].as_str().ok_or("Missing 'module_id'")?;
            let result = ctx.inner.plugin_manager.unload_module(module_id);
            let ok = result.ok;
            let _ = ctx.broadcaster.send(WsMessage::ModuleChanged {
                id: module_id.to_string(),
                status: if ok { "unloaded".to_string() } else { "error".to_string() },
            });
            let resp_data = if ok {
                json!({ "ok": true })
            } else {
                json!({ "ok": false, "error": result.error })
            };
            let _ = ctx.broadcaster.send(WsMessage::Response {
                request_id: request_id.to_string(),
                data: resp_data,
            });
        }
        "update_config" => {
            let mut config = ctx.inner.config.write().await;
            if let Some(host) = data["listen_host"].as_str() {
                config.host = host.to_string();
            }
            if let Some(port) = data["listen_port"].as_i64() {
                config.port = port as u16;
            }
            let _ = ctx.broadcaster.send(WsMessage::Response {
                request_id: request_id.to_string(),
                data: json!({ "ok": true }),
            });
        }
        "get_p2p_state" => {
            let _ = ctx.broadcaster.send(WsMessage::Response {
                request_id: request_id.to_string(),
                data: json!({ "connected_peers": 0, "listening_addr": "" }),
            });
        }
        "dial_peer" => {
            let addr = data["addr"].as_str().ok_or("Missing 'addr'")?;
            tracing::info!("WS: dial_peer request to {}", addr);
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
    let modules = state.plugin_manager.list_modules();

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
    let modules = ctx.inner.plugin_manager.list_modules();
    Json(ApiResponse::ok(modules))
}

async fn load_plugin(
    State(ctx): State<AppCtx>,
    Json(payload): Json<serde_json::Value>,
) -> Json<ApiResponse<Module>> {
    let path = payload
        .get("path")
        .and_then(|v| v.as_str())
        .ok_or("missing 'path' field");

    match path {
        Ok(p) => {
            let resp = ctx.inner.plugin_manager.load_module(&std::path::PathBuf::from(p));
            Json(resp)
        }
        Err(e) => Json(ApiResponse::err(e)),
    }
}

async fn unload_plugin(
    State(ctx): State<AppCtx>,
    Path(id): Path<String>,
) -> Json<ApiResponse<()>> {
    let resp = ctx.inner.plugin_manager.unload_module(&id);
    Json(resp)
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    cloudfin_core::initLogging();
    tracing::info!("Starting Cloudfin Core...");

    let config = Config::default();
    let plugins_dir = std::path::PathBuf::from(&config.modules_dir);

    let (broadcaster, _) = broadcast::channel::<WsMessage>(100);

    let state: SharedState = Arc::new(AppState {
        version: env!("CARGO_PKG_VERSION").to_string(),
        started_at: Utc::now(),
        config: Arc::new(RwLock::new(config)),
        plugin_manager: Arc::new(PluginManager::new(plugins_dir)),
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
        .route("/api/plugins/unload/:id", routing::delete(unload_plugin))
        .route("/ws", routing::get(ws_handler))
        .layer(TraceLayer::new_for_http())
        .with_state(ctx);

    let addr = format!("{}:{}", host, port);

    tracing::info!("Listening on {}", addr);
    tracing::info!("WebSocket available at ws://{}:{}/ws", host, port);

    let listener = tokio::net::TcpListener::bind(&addr).await?;
    axum::serve(listener, app).await?;

    Ok(())
}
