//! P2P 同步协议

use anyhow::Result;
use bytes::Bytes;

pub struct SyncProtocol {
    // 同步状态
}

impl SyncProtocol {
    pub fn new() -> Self {
        Self {}
    }

    /// 生成同步消息
    pub fn generate_sync_msg(&self, state: &[u8]) -> Result<Bytes> {
        Ok(Bytes::copy_from_slice(state))
    }

    /// 处理收到的同步消息
    pub fn handle_sync_msg(&self, msg: &[u8]) -> Result<Vec<u8>> {
        Ok(msg.to_vec())
    }
}
