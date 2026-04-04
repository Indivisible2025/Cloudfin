//! Cloudfin P2P Module
//! 基于 libp2p 的 P2P 网络模块

pub mod behaviour;
pub mod discovery;

use anyhow::Result;
use serde::{Deserialize, Serialize};
use tokio::sync::RwLock;
use cloudfin_plugin_common::{Module, ModuleHealth};

/// P2P 模块配置
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct P2pConfig {
    pub listen_addr: String,
    pub bootstrap_nodes: Vec<String>,
    pub enable_mdns: bool,
    pub kbucket_size: usize,
}

impl Default for P2pConfig {
    fn default() -> Self {
        Self {
            listen_addr: "/ip4/0.0.0.0/tcp/0".into(),
            bootstrap_nodes: vec![],
            enable_mdns: true,
            kbucket_size: 20,
        }
    }
}

/// P2P 模块状态
#[derive(Debug, Clone)]
pub struct P2pState {
    pub connected_peers: usize,
    pub discovered_peers: usize,
    pub listening_addr: String,
}

/// Cloudfin P2P 模块主结构
pub struct P2pModule {
    config: P2pConfig,
    state: RwLock<P2pState>,
}

impl P2pModule {
    pub fn new() -> Self {
        Self {
            config: P2pConfig::default(),
            state: RwLock::new(P2pState {
                connected_peers: 0,
                discovered_peers: 0,
                listening_addr: String::new(),
            }),
        }
    }
}

impl Default for P2pModule {
    fn default() -> Self {
        Self::new()
    }
}

impl Module for P2pModule {
    fn id(&self) -> &str {
        "cloudfin.p2p"
    }

    fn name(&self) -> &str {
        "P2P Networking"
    }

    fn version(&self) -> &str {
        "0.1.0"
    }

    fn init(&mut self, config: serde_json::Value) -> anyhow::Result<()> {
        if let Some(obj) = config.as_object() {
            if let Some(addr) = obj.get("listen_addr").and_then(|v| v.as_str()) {
                self.config.listen_addr = addr.to_string();
            }
            if let Some(nodes) = obj.get("bootstrap_nodes").and_then(|v| v.as_array()) {
                self.config.bootstrap_nodes = nodes.iter().filter_map(|v| v.as_str().map(String::from)).collect();
            }
            if let Some(enable) = obj.get("enable_mdns").and_then(|v| v.as_bool()) {
                self.config.enable_mdns = enable;
            }
        }
        tracing::info!("P2P module initialized");
        Ok(())
    }

    fn start(&mut self) -> anyhow::Result<()> {
        tracing::info!("P2P module started on {}", self.config.listen_addr);
        Ok(())
    }

    fn stop(&mut self) -> anyhow::Result<()> {
        tracing::info!("P2P module stopped");
        Ok(())
    }

    fn status(&self) -> ModuleHealth {
        ModuleHealth {
            running: true,
            message: "P2P module running".into(),
        }
    }
}

/// Factory function — must be #[no_mangle] for libloading
#[no_mangle]
pub extern "C" fn create_module() -> *mut Box<dyn Module> {
    Box::into_raw(Box::new(Box::new(P2pModule::new()) as Box<dyn Module>))
}
