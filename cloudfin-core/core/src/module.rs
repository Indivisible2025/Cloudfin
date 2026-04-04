//! Core module trait - defines the interface for dynamic modules
//! Spec: SPEC.md Section 5

use serde::{Deserialize, Serialize};
use serde_json::Value;

/// Health status for a module
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModuleHealth {
    pub running: bool,
    pub message: String,
}

/// Core trait that all dynamic modules must implement.
/// Modules are loaded via libloading as .so/.dylib/.dll files.
pub trait Module: Send + Sync {
    /// Unique identifier, e.g. "cloudfin.p2p"
    fn id(&self) -> &str;

    /// Human-readable name, e.g. "P2P Networking"
    fn name(&self) -> &str;

    /// Semantic version, e.g. "0.1.0"
    fn version(&self) -> &str;

    /// Initialize the module with JSON configuration
    fn init(&mut self, config: Value) -> anyhow::Result<()>;

    /// Start the module (called after init)
    fn start(&mut self) -> anyhow::Result<()>;

    /// Stop the module (called on shutdown)
    fn stop(&mut self) -> anyhow::Result<()>;

    /// Current health status
    fn status(&self) -> ModuleHealth;

    /// Cards data for UI display (info, actions, settings)
    /// Default returns empty cards structure.
    fn cards(&self) -> Value {
        serde_json::json!({
            "info": [],
            "actions": [],
            "settings": []
        })
    }
}
