//! Module Trait Definition

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// Module trait that all Cloudfin plugins must implement
pub trait Module: Send + Sync {
    /// Unique module identifier
    fn id(&self) -> &str;
    
    /// Human-readable module name
    fn name(&self) -> &str;
    
    /// Module version
    fn version(&self) -> &str;
    
    /// Initialize the module with configuration
    fn init(&mut self, config: serde_json::Value) -> anyhow::Result<()>;
    
    /// Start the module
    fn start(&mut self) -> anyhow::Result<()>;
    
    /// Stop the module
    fn stop(&mut self) -> anyhow::Result<()>;
    
    /// Get module health status
    fn status(&self) -> ModuleHealth;
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModuleHealth {
    pub running: bool,
    pub message: String,
}

/// Module state
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum ModuleState {
    Uninitialized = 0,
    Initialized = 1,
    Running = 2,
    Stopped = 3,
    Error = 4,
}
