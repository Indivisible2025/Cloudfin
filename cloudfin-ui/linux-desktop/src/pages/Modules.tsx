import { useEffect, useState } from 'react';
import { getModules, toggleModule } from '../api/core';

interface Module {
  id: string;
  name: string;
  status: 'active' | 'inactive' | 'error';
  version: string;
  description: string;
}

export default function Modules() {
  const [modules, setModules] = useState<Module[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    load();
    const interval = setInterval(load, 10000);
    return () => clearInterval(interval);
  }, []);

  async function load() {
    try {
      const data = await getModules();
      setModules(data);
    } catch {
      setModules([]);
    } finally {
      setLoading(false);
    }
  }

  async function handleToggle(mod: Module) {
    try {
      await toggleModule(mod.id, mod.status !== 'active');
      await load();
    } catch (e) {
      console.error('Failed to toggle module', e);
    }
  }

  if (loading) return <div className="page"><div className="loading">加载中...</div></div>;

  return (
    <div className="page modules-page">
      <h1 className="page-title">🧩 模块管理</h1>

      {modules.length === 0 ? (
        <div className="card">
          <p className="text-muted">暂无模块信息</p>
        </div>
      ) : (
        <div className="module-list">
          {modules.map(mod => (
            <div key={mod.id} className="card module-card">
              <div className="module-info">
                <div className="module-header">
                  <span className={`module-status status-${mod.status}`}></span>
                  <h3>{mod.name}</h3>
                  <span className="version-badge">v{mod.version}</span>
                </div>
                <p className="module-desc">{mod.description}</p>
              </div>
              <button
                className={`btn-toggle ${mod.status === 'active' ? 'active' : ''}`}
                onClick={() => handleToggle(mod)}
              >
                {mod.status === 'active' ? '已启用' : '已禁用'}
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
