//! Cloudfin CLI - cloudfinctl

use anyhow::Result;
use clap::{Parser, Subcommand};
use reqwest::Client;
use serde::Deserialize;

#[derive(Parser)]
#[command(name = "cloudfinctl")]
#[command(about = "Cloudfin Core CLI", long_about = None)]
struct Cli {
    #[arg(short, long, default_value = "http://127.0.0.1:19001")]
    url: String,

    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// Get Core status
    Status,
    /// Get/Set configuration
    Config {
        #[command(subcommand)]
        action: ConfigAction,
    },
    /// List loaded modules
    Modules {
        #[command(subcommand)]
        action: Option<ModuleAction>,
    },
}

#[derive(Subcommand)]
enum ConfigAction {
    Get,
    Set { key: String, value: String },
}

#[derive(Subcommand)]
enum ModuleAction {
    List,
    Load { path: String },
    Unload { id: String },
}

#[derive(Deserialize)]
struct ApiResponse<T> {
    ok: bool,
    data: Option<T>,
    error: Option<String>,
}

#[derive(Deserialize)]
struct CoreStatus {
    version: String,
    uptime_secs: u64,
    #[allow(dead_code)]
    modules: serde_json::Value,
}

#[tokio::main]
async fn main() -> Result<()> {
    let cli = Cli::parse();
    let client = Client::new();

    match &cli.command {
        Commands::Status => {
            let resp = client
                .get(format!("{}/api/core/status", cli.url))
                .send()
                .await?;
            
            if !resp.status().is_success() {
                anyhow::bail!("Request failed: {}", resp.status());
            }
            
            let body: ApiResponse<CoreStatus> = resp.json().await?;
            if body.ok {
                if let Some(data) = body.data {
                    println!("🌙 Cloudfin Core");
                    println!("   Version: {}", data.version);
                    println!("   Uptime: {}s", data.uptime_secs);
                    println!("   Modules: {}", data.modules);
                }
            } else {
                anyhow::bail!("Error: {:?}", body.error);
            }
        }
        
        Commands::Config { action } => {
            match action {
                ConfigAction::Get => {
                    let resp = client
                        .get(format!("{}/api/config", cli.url))
                        .send()
                        .await?;
                    let body: ApiResponse<serde_json::Value> = resp.json().await?;
                    println!("{}", serde_json::to_string_pretty(&body.data)?);
                }
                ConfigAction::Set { key, value } => {
                    println!("Config set {} = {} (not yet implemented)", key, value);
                }
            }
        }
        
        Commands::Modules { action: _ } => {
            let resp = client
                .get(format!("{}/api/modules", cli.url))
                .send()
                .await?;
            let body: ApiResponse<serde_json::Value> = resp.json().await?;
            println!("{}", serde_json::to_string_pretty(&body.data)?);
        }
    }

    Ok(())
}
