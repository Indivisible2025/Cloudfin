import { useEffect, useState } from 'react';
import { getStatus, onStatusUpdate } from '../api/core';

export default function Status() {
  const [status, setStatus] = useState<any>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    getStatus()
      .then(setStatus)
      .catch(() => setError(true));

    const unsub = onStatusUpdate((data) => {
      setStatus(data);
    });

    const interval = setInterval(() => {
      getStatus().then(setStatus).catch(() => {});
    }, 5000);

    return () => {
      unsub();
      clearInterval(interval);
    };
  }, []);

  if (error) {
    return (
      <div className="page status-page">
        <div className="card error-card">
          <div className="error-icon">⚠️</div>
          <h2>未连接到 Cloudfin Core</h2>
          <p>请确保 Core 已在运行（端口 19001）</p>
          <button className="btn-primary" onClick={() => window.location.reload()}>重试</button>
        </div>
      </div>
    );
  }

  if (!status) {
    return <div className="page"><div className="loading">正在连接 Core...</div></div>;
  }

  const upTime = () => {
    const s = status.uptime || 0;
    const d = Math.floor(s / 86400);
    const h = Math.floor((s % 86400) / 3600);
    const m = Math.floor((s % 3600) / 60);
    if (d > 0) return `${d} 天 ${h} 小时`;
    if (h > 0) return `${h} 小时 ${m} 分钟`;
    return `${m} 分钟`;
  };

  return (
    <div className="page status-page">
      <h1 className="page-title">📊 状态总览</h1>

      <div className="card status-hero">
        <div className="status-indicator">
          <div className={`dot ${status.online ? 'online' : 'offline'}`}></div>
          <span>{status.online ? '在线' : '离线'}</span>
        </div>
        <div className="hero-stats">
          <div className="hero-stat">
            <div className="stat-value">{status.version || '—'}</div>
            <div className="stat-label">版本</div>
          </div>
          <div className="hero-stat">
            <div className="stat-value">{upTime()}</div>
            <div className="stat-label">运行时长</div>
          </div>
          <div className="hero-stat">
            <div className="stat-value">{status.modules_loaded || 0}/{status.modules_total || 0}</div>
            <div className="stat-label">已加载模块</div>
          </div>
        </div>
      </div>

      <div className="card">
        <h3 className="card-title">🌐 网络</h3>
        <div className="stat-row">
          <span>已连接节点</span>
          <span className="value">{status.network?.peers_connected ?? 0}</span>
        </div>
        <div className="stat-row">
          <span>↓ 流入</span>
          <span className="value">{formatBw(status.network?.bandwidth_in ?? 0)}</span>
        </div>
        <div className="stat-row">
          <span>↑ 流出</span>
          <span className="value">{formatBw(status.network?.bandwidth_out ?? 0)}</span>
        </div>
      </div>
    </div>
  );
}

function formatBw(bytes: number): string {
  if (bytes < 1024) return `${bytes} B/s`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB/s`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB/s`;
}
