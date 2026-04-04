import { useState } from 'react';
import Status from './pages/Status';
import Modules from './pages/Modules';
import Network from './pages/Network';
import Sync from './pages/Sync';
import Settings from './pages/Settings';
import { connect } from './api/core';
import { useEffect } from 'react';
import { ThemeProvider } from './contexts/ThemeContext';
import { GuideProvider, useGuide } from './contexts/GuideContext';
import InstallGuide from './components/InstallGuide';

type Page = 'status' | 'modules' | 'network' | 'sync' | 'settings';

const NAV_ITEMS: { id: Page; label: string; icon: string }[] = [
  { id: 'status', label: '核心', icon: '💻' },
  { id: 'modules', label: '通信（通信模块）', icon: '📡' },
  { id: 'network', label: '加密（加密模块）', icon: '🔒' },
  { id: 'sync', label: '同步（同步模块）', icon: '🔄' },
  { id: 'settings', label: '设置', icon: '⚙️' },
];

function AppContent() {
  const [page, setPage] = useState<Page>('status');
  const [connected, setConnected] = useState(false);
  const { guideDismissed, showGuide, dismissGuide } = useGuide();

  useEffect(() => {
    connect()
      .then(() => setConnected(true))
      .catch(() => setConnected(false));
  }, []);

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="sidebar-header">
          <div className="logo">
            <img
              src="https://raw.githubusercontent.com/Indivisible2025/Cloudfin/main/Cloudfin-Icon.png"
              alt="Cloudfin"
              className="logo-img"
              onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
            />
            <span className="logo-text">Cloudfin</span>
          </div>
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
        {!connected && !guideDismissed && (
          <InstallGuide onComplete={dismissGuide} />
        )}
        {page === 'status' && <Status />}
        {page === 'modules' && <Modules />}
        {page === 'network' && <Network />}
        {page === 'sync' && <Sync />}
        {page === 'settings' && <Settings onShowGuide={showGuide} />}
      </main>
    </div>
  );
}

export default function App() {
  return (
    <ThemeProvider>
      <GuideProvider>
        <AppContent />
      </GuideProvider>
    </ThemeProvider>
  );
}
