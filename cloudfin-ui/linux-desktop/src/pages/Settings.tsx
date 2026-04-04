import { useEffect, useState } from 'react';
import { getConfig, setConfig } from '../api/core';

export default function Settings() {
  const [config, setConfigState] = useState<Record<string, any>>({});
  const [loading, setLoading] = useState(true);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    load();
  }, []);

  async function load() {
    try {
      const data = await getConfig();
      setConfigState(data || {});
    } catch {
      setConfigState({});
    } finally {
      setLoading(false);
    }
  }

  async function handleSave(key: string, value: any) {
    try {
      await setConfig(key, value);
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    } catch (e) {
      console.error('Failed to save config', e);
    }
  }

  if (loading) return <div className="page"><div className="loading">加载中...</div></div>;

  return (
    <div className="page settings-page">
      <h1 className="page-title">⚙️ 设置</h1>

      <div className="card">
        <h3 className="card-title">监听地址</h3>
        <div className="setting-row">
          <span className="setting-label">地址</span>
          <input
            className="input"
            type="text"
            defaultValue={config.listen_address || '0.0.0.0:19001'}
            onBlur={(e) => handleSave('listen_address', e.target.value)}
          />
        </div>
      </div>

      <div className="card">
        <h3 className="card-title">日志</h3>
        <div className="setting-row">
          <span className="setting-label">日志级别</span>
          <select
            className="select"
            defaultValue={config.log_level || 'info'}
            onChange={(e) => handleSave('log_level', e.target.value)}
          >
            <option value="trace">Trace</option>
            <option value="debug">Debug</option>
            <option value="info">Info</option>
            <option value="warn">Warn</option>
            <option value="error">Error</option>
          </select>
        </div>
      </div>

      <div className="card">
        <h3 className="card-title">网络</h3>
        <div className="setting-row">
          <span className="setting-label">最大对等节点</span>
          <input
            className="input"
            type="number"
            defaultValue={config.max_peers || 10}
            onBlur={(e) => handleSave('max_peers', parseInt(e.target.value))}
          />
        </div>
        <div className="setting-row">
          <span className="setting-label">端口</span>
          <input
            className="input"
            type="number"
            defaultValue={config.port || 19001}
            onBlur={(e) => handleSave('port', parseInt(e.target.value))}
          />
        </div>
      </div>

      <div className="card about-card">
        <h3 className="card-title">关于</h3>
        <div className="stat-row">
          <span>Cloudfin Core</span>
          <span className="value">v2026.04.04.15.51</span>
        </div>
        <div className="stat-row">
          <span>监听地址</span>
          <span className="value">{config.listen_address || '0.0.0.0:19001'}</span>
        </div>
      </div>

      {saved && <div className="toast success">设置已保存</div>}
    </div>
  );
}
