import { useEffect, useState } from 'react';
import { getPeers } from '../api/core';

interface Peer {
  id: string;
  address: string;
  latency: number;
  connected: boolean;
}

export default function Network() {
  const [peers, setPeers] = useState<Peer[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    load();
    const interval = setInterval(load, 5000);
    return () => clearInterval(interval);
  }, []);

  async function load() {
    try {
      const data = await getPeers();
      setPeers(data);
    } catch {
      setPeers([]);
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <div className="page"><div className="loading">加载中...</div></div>;

  const connected = peers.filter(p => p.connected);
  const disconnected = peers.filter(p => !p.connected);

  return (
    <div className="page network-page">
      <h1 className="page-title">🌐 网络</h1>

      <div className="card">
        <h3 className="card-title">连接概览</h3>
        <div className="stat-row">
          <span>总节点数</span>
          <span className="value">{peers.length}</span>
        </div>
        <div className="stat-row">
          <span>已连接</span>
          <span className="value text-success">{connected.length}</span>
        </div>
        <div className="stat-row">
          <span>未连接</span>
          <span className="value text-danger">{disconnected.length}</span>
        </div>
      </div>

      <h2 className="section-title">节点列表</h2>

      {peers.length === 0 ? (
        <div className="card">
          <p className="text-muted">暂无节点信息</p>
        </div>
      ) : (
        <div className="peer-list">
          {peers.map(peer => (
            <div key={peer.id} className="card peer-card">
              <div className="peer-info">
                <div className="peer-header">
                  <span className={`peer-dot ${peer.connected ? 'connected' : 'disconnected'}`}></span>
                  <span className="peer-id">{peer.id.substring(0, 8)}...</span>
                  <span className="peer-address">{peer.address}</span>
                </div>
              </div>
              <div className="peer-meta">
                {peer.connected && <span className="peer-latency">⏱ {peer.latency}ms</span>}
                <span className={`peer-status ${peer.connected ? 'text-success' : 'text-danger'}`}>
                  {peer.connected ? '已连接' : '未连接'}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
