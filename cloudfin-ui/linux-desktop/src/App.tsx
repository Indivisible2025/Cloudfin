import { useState } from 'react';
import Status from './pages/Status';
import Modules from './pages/Modules';
import Network from './pages/Network';
import Sync from './pages/Sync';
import Settings from './pages/Settings';
import { connect } from './api/core';
import { useEffect } from 'react';

type Page = 'status' | 'modules' | 'network' | 'sync' | 'settings';

const NAV_ITEMS: { id: Page; label: string; icon: string }[] = [
  { id: 'status', label: '状态', icon: '📊' },
  { id: 'modules', label: '模块', icon: '🧩' },
  { id: 'network', label: '网络', icon: '🌐' },
  { id: 'sync', label: '同步', icon: '🔄' },
  { id: 'settings', label: '设置', icon: '⚙️' },
];

export default function App() {
  const [page, setPage] = useState<Page>('status');
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    connect()
      .then(() => setConnected(true))
      .catch(() => setConnected(false));
  }, []);

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="sidebar-header">
          <div className="logo">☁️ Cloudfin</div>
          <div className={`connection-badge ${connected ? 'connected' : 'disconnected'}`}>
            {connected ? 'Core 已连接' : 'Core 未连接'}
          </div>
        </div>
        <nav className="nav">
          {NAV_ITEMS.map(item => (
            <button
              key={item.id}
              className={`nav-item ${page === item.id ? 'active' : ''}`}
              onClick={() => setPage(item.id)}
            >
              <span className="nav-icon">{item.icon}</span>
              <span className="nav-label">{item.label}</span>
            </button>
          ))}
        </nav>
      </aside>

      <main className="main-content">
        {page === 'status' && <Status />}
        {page === 'modules' && <Modules />}
        {page === 'network' && <Network />}
        {page === 'sync' && <Sync />}
        {page === 'settings' && <Settings />}
      </main>
    </div>
  );
}
