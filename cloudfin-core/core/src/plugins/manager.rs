//! Plugin Manager - Module loading and lifecycle management
//! Spec: SPEC.md Section 5.1

use crate::Module;
use anyhow::{Context, Result};
use libloading::{Library, Symbol};
use serde::Deserialize;
use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::mpsc as std_mpsc;
use tracing::{error, info, warn};

/// Module metadata loaded from .so.json config
/// Spec: SPEC.md Section 3.4 & 3.5
#[derive(Debug, Clone, Deserialize)]
pub struct ModuleMeta {
    pub id: String,
    pub name: String,
    pub version: String,
    pub description: Option<String>,
    /// Arbitrary module-specific configuration
    pub config: Option<serde_json::Value>,
    /// UI card declarations: { info: [...], actions: [...], settings: [...] }
    /// Spec: SPEC.md Section 3.5
    pub cards: Option<serde_json::Value>,
}

/// A loaded plugin instance
pub struct LoadedPlugin {
    pub meta: ModuleMeta,
    lib: Library,
    pub module: Box<dyn Module>,
}

/// Discovers and manages dynamic modules
pub struct PluginManager {
    plugins: HashMap<String, LoadedPlugin>,
    module_dir: PathBuf,
    #[allow(dead_code)]
    watcher_tx: std_mpsc::Sender<notify::Result<notify::Event>>,
}

impl PluginManager {
    pub fn new(module_dir: PathBuf) -> Self {
        let (tx, _rx) = std_mpsc::channel();
        Self {
            plugins: HashMap::new(),
            module_dir,
            watcher_tx: tx,
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
                    Ok((id, _meta)) => info!("Loaded plugin: {}", id),
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

    pub fn load_plugin(&mut self, so_path: &Path) -> Result<(String, ModuleMeta)> {
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
        // Config file is {name}.so.json per SPEC Section 3.4
        let json_path = so_path.with_file_name(format!("{}.so.json", module_name));
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
                cards: None,
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

        let meta_out = meta.clone();
        let id = meta.id.clone();
        self.plugins.insert(id.clone(), LoadedPlugin { meta, lib, module });
        Ok((id, meta_out))
    }

    pub fn list(&self) -> Vec<String> {
        self.plugins.keys().cloned().collect()
    }

    /// Unload a plugin by id
    pub fn unload_plugin(&mut self, id: &str) -> anyhow::Result<()> {
        if let Some(_loaded) = self.plugins.remove(id) {
            tracing::info!("Unloaded plugin: {}", id);
            Ok(())
        } else {
            anyhow::bail!("Plugin not found: {}", id)
        }
    }

    /// Rescan the module directory and load any new plugins
    pub fn rescan(&mut self) -> (usize, usize) {
        let mut loaded = 0;
        let mut failed = 0;
        if !self.module_dir.exists() {
            return (0, 0);
        }
        if let Ok(entries) = std::fs::read_dir(&self.module_dir) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.extension().and_then(|s| s.to_str()) == Some("so") {
                    if !self.is_loaded(&path) {
                        match self.load_plugin(&path) {
                            Ok(_) => loaded += 1,
                            Err(_) => failed += 1,
                        }
                    }
                }
            }
        }
        (loaded, failed)
    }

    fn is_loaded(&self, path: &Path) -> bool {
        self.plugins.values().any(
            |p| p.meta.id == path.file_stem().and_then(|s| s.to_str()).unwrap_or(""),
        )
    }

    /// List all modules with full metadata and cards for UI consumption
    pub fn list_full(&self) -> Vec<serde_json::Value> {
        self.plugins
            .values()
            .map(|plugin| {
                serde_json::json!({
                    "id": plugin.meta.id,
                    "name": plugin.meta.name,
                    "version": plugin.meta.version,
                    "category": plugin.meta.config.as_ref()
                        .and_then(|c| c.get("category").cloned())
                        .unwrap_or(serde_json::Value::String("Unknown".to_string())),
                    "icon": plugin.meta.config.as_ref()
                        .and_then(|c| c.get("icon").cloned())
                        .unwrap_or(serde_json::Value::String("📦".to_string())),
                    "description": plugin.meta.description.clone().unwrap_or_default(),
                    "cards": plugin.meta.cards.clone().unwrap_or_else(|| plugin.module.cards()),
                })
            })
            .collect()
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
