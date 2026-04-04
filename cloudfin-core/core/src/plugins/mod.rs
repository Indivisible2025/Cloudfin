//! Plugin system - dynamic module loading
//! Spec: SPEC.md Section 5

mod manager;

pub use manager::{PluginManager, LoadedPlugin, ModuleMeta};
