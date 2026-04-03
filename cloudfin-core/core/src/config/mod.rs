//! Configuration Management

use std::path::PathBuf;
use anyhow::Context;

use crate::Config;

pub struct ConfigManager {
    config_path: PathBuf,
}

impl ConfigManager {
    pub fn new(config_dir: PathBuf) -> Self {
        Self {
            config_path: config_dir.join("config.json"),
        }
    }

    /// Load config from disk
    pub fn load(&self) -> anyhow::Result<Config> {
        if !self.config_path.exists() {
            return Ok(Config::default());
        }
        let content = std::fs::read_to_string(&self.config_path)
            .context("Failed to read config file")?;
        let config: Config = serde_json::from_str(&content)
            .context("Failed to parse config")?;
        Ok(config)
    }

    /// Save config to disk
    pub fn save(&self, config: &Config) -> anyhow::Result<()> {
        if let Some(parent) = self.config_path.parent() {
            std::fs::create_dir_all(parent)
                .context("Failed to create config dir")?;
        }
        let content = serde_json::to_string_pretty(config)
            .context("Failed to serialize config")?;
        std::fs::write(&self.config_path, content)
            .context("Failed to write config file")?;
        Ok(())
    }
}
