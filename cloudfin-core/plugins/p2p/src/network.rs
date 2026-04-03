//! P2P 网络管理

use anyhow::Result;
use libp2p::{multiaddr::Multiaddr, Swarm};

pub struct Network {
    swarm: Option<Swarm<crate::behaviour::Behaviour>>,
}

impl Network {
    pub fn new() -> Self {
        Self { swarm: None }
    }

    pub fn start(&mut self, listen_addr: &str) -> Result<String> {
        // TODO: 构建 Swarm 并开始监听
        tracing::info!("Network starting on {}", listen_addr);
        Ok(format!("{}/p2p/...", listen_addr))
    }

    pub fn dial(&mut self, addr: &Multiaddr) -> Result<()> {
        if let Some(swarm) = &mut self.swarm {
            swarm.dial(addr.clone())?;
        }
        Ok(())
    }

    pub fn num_peers(&self) -> usize {
        self.swarm
            .as_ref()
            .map(|s| s.network_info().num_peers())
            .unwrap_or(0)
    }
}

impl Default for Network {
    fn default() -> Self {
        Self::new()
    }
}
