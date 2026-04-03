# Cloudfin P2P Module

基于 libp2p 的 P2P 网络模块，作为 Cloudfin Core 的插件。

## 功能

- Kademlia DHT 分布式哈希表
- mDNS 本地网络节点发现
- Ping 协议
- Identify 协议

## 配置

```json
{
  "listen_addr": "/ip4/0.0.0.0/tcp/0",
  "bootstrap_nodes": [],
  "enable_mdns": true,
  "kbucket_size": 20
}
```

## 构建

```bash
cargo build -p cloudfin-p2p
```
