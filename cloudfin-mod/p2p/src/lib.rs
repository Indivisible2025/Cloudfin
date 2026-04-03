//! Cloudfin P2P Module
//! 基于 libp2p 的 P2P 网络模块

pub mod behaviour;
pub mod network;
pub mod discovery;

use anyhow::Result;
use serde::{Deserialize, Serialize};
use tokio::sync::RwLock;

/// P2P 模块配置
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct P2pConfig {
    /// 监听地址，例如 "/ip4/0.0.0.0/tcp/0"
    pub listen_addr: String,
    /// Bootstrap 节点列表
    pub bootstrap_nodes: Vec<String>,
    /// 是否启用 mDNS 发现
    pub enable_mdns: bool,
    /// Kademlia 宇宙桶大小
    pub kbucket_size: usize,
}

impl Default for P2pConfig {
    fn default() -> Self {
        Self {
            listen_addr: "/ip4/0.0.0.0/tcp/0".into(),
            bootstrap_nodes: vec![],
            enable_mdns: true,
            kbucket_size: 20,
        }
    }
}

/// P2P 模块状态
#[derive(Debug, Clone)]
pub struct P2pState {
    pub connected_peers: usize,
    pub discovered_peers: usize,
    pub listening_addr: String,
}

/// Cloudfin P2P 模块主结构
pub struct P2pModule {
    config: P2pConfig,
    state: RwLock<P2pState>,
}

impl P2pModule {
    pub fn new(config: P2pConfig) -> Self {
        Self {
            config,
            state: RwLock::new(P2pState {
                connected_peers: 0,
                discovered_peers: 0,
                listening_addr: String::new(),
            }),
        }
    }

    /// 模块初始化
    pub async fn init(&mut self) -> Result<()> {
        tracing::info!("Initializing P2P module...");
        Ok(())
    }

    /// 启动 P2P 网络
    pub async fn start(&mut self) -> Result<()> {
        tracing::info!("Starting P2P module...");
        let mut state = self.state.write().await;
        state.listening_addr = self.config.listen_addr.clone();
        Ok(())
    }

    /// 停止模块
    pub async fn stop(&mut self) -> Result<()> {
        tracing::info!("Stopping P2P module...");
        Ok(())
    }

    /// 获取状态
    pub async fn status(&self) -> P2pState {
        self.state.read().await.clone()
    }

    /// 连接指定节点
    pub async fn dial(&mut self, addr: &str) -> Result<()> {
        tracing::info!("Dialing: {}", addr);
        Ok(())
    }

    /// 广播消息到所有连接节点
    pub async fn broadcast(&mut self, msg: &[u8]) -> Result<()> {
        tracing::debug!("Broadcasting {} bytes", msg.len());
        Ok(())
    }
}
