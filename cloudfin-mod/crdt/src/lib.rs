//! Cloudfin CRDT Module
//! 基于 yrs (Yjs Rust) 的 CRDT 同步模块

pub mod document;
pub mod sync;
pub mod state;

use serde::{Deserialize, Serialize};
use tokio::sync::RwLock;
use cloudfin_plugin_common::{Module, ModuleHealth};

/// CRDT 配置
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CrdtConfig {
    pub doc_name: String,
    pub sync_addr: Option<String>,
    pub persistence_path: Option<String>,
    pub max_docs: usize,
}

impl Default for CrdtConfig {
    fn default() -> Self {
        Self {
            doc_name: "default".into(),
            sync_addr: None,
            persistence_path: None,
            max_docs: 100,
        }
    }
}

/// CRDT 状态
#[derive(Debug, Clone)]
pub struct CrdtState {
    pub active_docs: usize,
    pub pending_updates: usize,
    pub connected_peers: usize,
}

/// Cloudfin CRDT 模块
pub struct CrdtModule {
    config: CrdtConfig,
    state: RwLock<CrdtState>,
}

impl CrdtModule {
    pub fn new() -> Self {
        Self {
            config: CrdtConfig::default(),
            state: RwLock::new(CrdtState {
                active_docs: 0,
                pending_updates: 0,
                connected_peers: 0,
            }),
        }
    }
}

impl Default for CrdtModule {
    fn default() -> Self {
        Self::new()
    }
}

impl Module for CrdtModule {
    fn id(&self) -> &str {
        "cloudfin.crdt"
    }

    fn name(&self) -> &str {
        "CRDT Sync"
    }

    fn version(&self) -> &str {
        "0.1.0"
    }

    fn init(&mut self, config: serde_json::Value) -> anyhow::Result<()> {
        if let Some(obj) = config.as_object() {
            if let Some(name) = obj.get("doc_name").and_then(|v| v.as_str()) {
                self.config.doc_name = name.to_string();
            }
            if let Some(addr) = obj.get("sync_addr").and_then(|v| v.as_str()) {
                self.config.sync_addr = Some(addr.to_string());
            }
            if let Some(path) = obj.get("persistence_path").and_then(|v| v.as_str()) {
                self.config.persistence_path = Some(path.to_string());
            }
        }
        tracing::info!("CRDT module initialized");
        Ok(())
    }

    fn start(&mut self) -> anyhow::Result<()> {
        tracing::info!("CRDT module started");
        Ok(())
    }

    fn stop(&mut self) -> anyhow::Result<()> {
        tracing::info!("CRDT module stopped");
        Ok(())
    }

    fn status(&self) -> ModuleHealth {
        ModuleHealth {
            running: true,
            message: "CRDT module running".into(),
        }
    }
}

/// Factory function — must be #[no_mangle] for libloading
#[no_mangle]
pub extern "C" fn create_module() -> *mut Box<dyn Module> {
    Box::into_raw(Box::new(Box::new(CrdtModule::new()) as Box<dyn Module>))
}
