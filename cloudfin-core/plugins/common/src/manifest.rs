//! Module Manifest

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModuleManifest {
    pub id: String,
    pub name: String,
    pub version: String,
    pub description: String,
    pub author: String,
    pub rust_version: String,
    pub dependencies: HashMap<String, String>,
    pub permissions: Vec<Permission>,
    pub api_version: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum Permission {
    Network,
    Storage,
    Crypto,
    #[serde(rename = "unsafe-all")]
    UnsafeAll,
}

impl ModuleManifest {
    pub fn from_json(json: &str) -> anyhow::Result<Self> {
        Ok(serde_json::from_str(json)?)
    }
    
    pub fn to_json(&self) -> anyhow::Result<String> {
        Ok(serde_json::to_string_pretty(self)?)
    }
}
