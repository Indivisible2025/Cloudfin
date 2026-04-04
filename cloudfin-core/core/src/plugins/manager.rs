//! Plugin Manager - Module loading and lifecycle management
//! Spec: SPEC.md Section 5.1

use crate::Module;
use anyhow::{Context, Result};
use libloading::{Library, Symbol};
use serde::Deserialize;
use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};
use tracing::{error, info, warn};

/// Module metadata loaded from .json config
#[derive(Debug, Deserialize)]
pub struct ModuleMeta {
    pub id: String,
    pub name: String,
    pub version: String,
    pub description: Option<String>,
    pub config: Option<serde_json::Value>,
}

/// A loaded plugin instance
pub struct LoadedPlugin {
    pub meta: ModuleMeta,
    lib: Library,
    module: Box<dyn Module>,
}

/// Discovers and manages dynamic modules
pub struct PluginManager {
    plugins: HashMap<String, LoadedPlugin>,
    module_dir: PathBuf,
}

impl PluginManager {
    pub fn new(module_dir: PathBuf) -> Self {
        Self {
            plugins: HashMap::new(),
            module_dir,
        }
    }

    /// Scan the module directory and load all discovered modules
    pub fn load_all(&mut self) -> Result<()> {
        if !self.module_dir.exists() {
            fs::create_dir_all(&self.module_dir)
                .context("failed to create module directory")?;
            info!("Created module directory: {}", self.module_dir.display());
            return Ok(());
        }

        let entries =
            fs::read_dir(&self.module_dir).context("failed to read module directory")?;

        for entry in entries.flatten() {
            let path = entry.path();
            if path.extension().and_then(|s| s.to_str()) == Some("so") {
                match self.load_plugin(&path) {
                    Ok(id) => info!("Loaded plugin: {}", id),
                    Err(e) => warn!("Failed to load plugin {}: {}", path.display(), e),
                }
            }
        }

        info!(
            "PluginManager loaded {} module(s) from {}",
            self.plugins.len(),
            self.module_dir.display()
        );
        Ok(())
    }

    pub fn load_plugin(&mut self, so_path: &Path) -> Result<String> {
        let module_name = so_path
            .file_stem()
            .and_then(|s| s.to_str())
            .context("invalid module filename")?;

        let lib = unsafe {
            Library::new(so_path)
                .with_context(|| format!("failed to load .so: {}", so_path.display()))?
        };

        // Get the create_module symbol (C-compatible FFI)
        let create_sym: Symbol<unsafe extern "C" fn() -> *mut Box<dyn Module>> = unsafe {
            lib.get(b"create_module")
                .with_context(|| format!("{}: missing `create_module` symbol", module_name))?
        };

        let mut module = unsafe {
            let ptr = create_sym();
            *Box::from_raw(ptr)
        };

        // Load {module_name}.json config if present
        let json_path = so_path.with_file_name(format!("{}.json", module_name));
        let meta = if json_path.exists() {
            let content =
                fs::read_to_string(&json_path).context("failed to read module config")?;
            serde_json::from_str::<ModuleMeta>(&content)
                .with_context(|| format!("failed to parse module config: {}", json_path.display()))?
        } else {
            ModuleMeta {
                id: module.id().to_string(),
                name: module.name().to_string(),
                version: module.version().to_string(),
                description: None,
                config: None,
            }
        };

        let config = meta.config.clone().unwrap_or(serde_json::json!({}));
        module.init(config.clone())?;
        module.start()?;

        info!(
            "Module loaded: {} v{} ({})",
            meta.name,
            meta.version,
            meta.id
        );

        let id = meta.id.clone();
        self.plugins.insert(id.clone(), LoadedPlugin { meta, lib, module });
        Ok(id)
    }

    pub fn list(&self) -> Vec<String> {
        self.plugins.keys().cloned().collect()
    }

    pub fn len(&self) -> usize {
        self.plugins.len()
    }

    pub fn is_empty(&self) -> bool {
        self.plugins.is_empty()
    }

    pub fn stop_all(&mut self) {
        for (_, plugin) in self.plugins.iter_mut() {
            if let Err(e) = plugin.module.stop() {
                error!("Error stopping module: {}", e);
            }
        }
        info!("All modules stopped");
    }
}
