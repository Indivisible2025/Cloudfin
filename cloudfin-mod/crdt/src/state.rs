//! 状态管理

use std::collections::HashMap;
use tokio::sync::RwLock;
use std::sync::Arc;

pub struct StateManager {
    docs: Arc<RwLock<HashMap<String, Vec<u8>>>>,
}

impl StateManager {
    pub fn new() -> Self {
        Self {
            docs: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    pub async fn save(&self, name: &str, state: Vec<u8>) {
        self.docs.write().await.insert(name.into(), state);
    }

    pub async fn load(&self, name: &str) -> Option<Vec<u8>> {
        self.docs.read().await.get(name).cloned()
    }
}
