import { useEffect, useState } from 'react';
import { getSyncStatus } from '../api/core';

export default function Sync() {
  const [sync, setSync] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    load();
    const interval = setInterval(load, 5000);
    return () => clearInterval(interval);
  }, []);

  async function load() {
    try {
      const data = await getSyncStatus();
      setSync(data);
    } catch {
      setSync(null);
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <div className="page"><div className="loading">加载中...</div></div>;

  return (
    <div className="page sync-page">
      <h1 className="page-title">🔄 同步</h1>

      {sync ? (
        <>
          <div className="card">
            <h3 className="card-title">同步状态</h3>
            <div className="stat-row">
              <span>状态</span>
              <span className={`value ${sync.active ? 'text-success' : 'text-muted'}`}>
                {sync.active ? '● 同步中' : '○ 已同步'}
              </span>
            </div>
            {sync.last_sync && (
              <div className="stat-row">
                <span>上次同步</span>
                <span className="value">{new Date(sync.last_sync * 1000).toLocaleString('zh-CN')}</span>
              </div>
            )}
            {sync.next_sync && (
              <div className="stat-row">
                <span>下次同步</span>
                <span className="value">{new Date(sync.next_sync * 1000).toLocaleString('zh-CN')}</span>
              </div>
            )}
          </div>

          {sync.peers && sync.peers.length > 0 && (
            <div className="card">
              <h3 className="card-title">同步节点</h3>
              {sync.peers.map((peer: any, i: number) => (
                <div key={i} className="stat-row">
                  <span>{peer.name || peer.id?.substring(0, 8)}</span>
                  <span className="value text-success">{peer.synced ? '✓ 已同步' : '同步中'}</span>
                </div>
              ))}
            </div>
          )}
        </>
      ) : (
        <div className="card">
          <p className="text-muted">暂无同步信息</p>
          <p className="text-muted">请确保 CRDT 模块已启用</p>
        </div>
      )}
    </div>
  );
}
