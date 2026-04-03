//! 节点发现

use anyhow::Result;

pub struct Discovery;

impl Discovery {
    pub fn new() -> Self {
        Self
    }

    /// 从 bootstrap 节点获取 peers
    pub async fn bootstrap(&self, _addrs: &[String]) -> Result<Vec<String>> {
        // TODO: 实现 Kademlia bootstrap
        Ok(vec![])
    }

    /// 本地网络发现（mDNS）
    pub async fn discover_local(&self) -> Result<Vec<String>> {
        // TODO: 实现 mDNS 发现
        Ok(vec![])
    }
}

impl Default for Discovery {
    fn default() -> Self {
        Self::new()
    }
}
