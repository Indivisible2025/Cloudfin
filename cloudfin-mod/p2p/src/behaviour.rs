//! libp2p Behaviour 配置

use libp2p::{
    identify::Behaviour as Identify,
    kad::{store::MemoryStore, Behaviour as Kademlia},
    mdns::tokio::Behaviour as Mdns,
    ping::Behaviour as Ping,
    swarm::NetworkBehaviour,
};

/// Combined libp2p Behaviour
#[derive(NetworkBehaviour)]
pub struct Behaviour {
    pub kad: Kademlia<MemoryStore>,
    pub mdns: Mdns,
    pub ping: Ping,
    pub identify: Identify,
}
