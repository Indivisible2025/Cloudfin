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
  { id: 'status', label: '核心', icon: '💻' },
  { id: 'modules', label: '通信（通信模块）', icon: '📡' },
  { id: 'network', label: '加密（加密模块）', icon: '🔒' },
  { id: 'sync', label: '同步（同步模块）', icon: '🔄' },
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
          <div className="logo">
            <img
              src="https://raw.githubusercontent.com/Indivisible2025/Cloudfin/main/Cloudfin-Icon.png"
              alt="Cloudfin"
              className="logo-img"
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
        {!connected && (
          <div className="onboarding-banner">
            <div className="onboarding-icon">🚀</div>
            <h2>未检测到 Cloudfin Core</h2>
            <p>请先安装并运行 Cloudfin Core，才能使用完整功能。</p>
            <div className="onboarding-links">
              <a
                href="https://github.com/Indivisible2025/Cloudfin/releases/latest"
                target="_blank"
                rel="noopener noreferrer"
                className="onboarding-btn"
              >
                📦 GitHub 下载（推荐）
              </a>
              <a
                href="https://gitee.com/Nianyv/Cloudfin/releases"
                target="_blank"
                rel="noopener noreferrer"
                className="onboarding-btn"
              >
                📦 Gitee 下载
              </a>
            </div>
            <div className="onboarding-steps">
              <h3>安装步骤：</h3>
              <ol>
                <li>下载 <code>Cloudfin-Core-Linux-amd64-v2026.4.5.1.tar.gz</code></li>
                <li>解压并运行 <code>./cloudfin-core</code></li>
                <li>重新打开 Cloudfin UI</li>
              </ol>
              <h3>安装模块：</h3>
              <ol>
                <li>下载 <code>Cloudfin-Mod-Linux-amd64-P2P-v2026.4.5.1.zip</code></li>
                <li>解压到 Core 同目录的 <code>modules/</code> 文件夹</li>
                <li>Core 会自动扫描并加载模块</li>
              </ol>
            </div>
          </div>
        )}
        {page === 'status' && <Status />}
        {page === 'modules' && <Modules />}
        {page === 'network' && <Network />}
        {page === 'sync' && <Sync />}
        {page === 'settings' && <Settings />}
      </main>
    </div>
  );
}
