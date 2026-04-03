//! Plugin Manager - Dynamic Module Loading

use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::RwLock;

use libloading::Library;

use crate::{ApiResponse, Module, ModuleStatus};

/// Loaded plugin handle
pub struct PluginHandle {
    lib: Library,
    id: String,
}

/// Plugin manager - handles dynamic loading/unloading of .so/.dylib modules
pub struct PluginManager {
    plugins: RwLock<HashMap<String, PluginHandle>>,
    plugins_dir: PathBuf,
}

impl PluginManager {
    pub fn new(plugins_dir: PathBuf) -> Self {
        Self {
            plugins: RwLock::new(HashMap::new()),
            plugins_dir,
        }
    }

    /// Load a module from disk
    pub fn load_module(&self, path: &Path) -> ApiResponse<Module> {
        let id = path.file_stem()
            .and_then(|s| s.to_str())
            .unwrap_or("unknown")
            .to_string();

        match self.load_library(path) {
            Ok(lib) => {
                let module = Module {
                    id: id.clone(),
                    name: id.clone(),
                    path: path.to_string_lossy().into_owned(),
                    status: ModuleStatus::Loaded,
                    registered_at: chrono::Utc::now(),
                };
                let handle_id = id.clone();
                self.plugins.write().unwrap().insert(id.clone(), PluginHandle { lib, id: handle_id });
                tracing::info!("Module loaded: {}", id);
                ApiResponse::ok(module)
            }
            Err(e) => {
                tracing::error!("Failed to load module: {}", e);
                ApiResponse::err(format!("Failed to load: {}", e))
            }
        }
    }

    /// Load a dynamic library
    fn load_library(&self, path: &Path) -> Result<Library, libloading::Error> {
        unsafe { Library::new(path) }
    }

    /// Unload a module
    pub fn unload_module(&self, id: &str) -> ApiResponse<()> {
        match self.plugins.write().unwrap().remove(id) {
            Some(_) => {
                tracing::info!("Module unloaded: {}", id);
                ApiResponse::ok(())
            }
            None => ApiResponse::err("module not found"),
        }
    }

    /// List all loaded modules
    pub fn list_modules(&self) -> Vec<String> {
        self.plugins.read().unwrap().keys().cloned().collect()
    }
}

impl Default for PluginManager {
    fn default() -> Self {
        Self::new(PathBuf::from("./modules"))
    }
}
