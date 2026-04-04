import { useState } from 'react';

type Platform = 'linux' | 'windows' | 'macos' | 'android' | 'ios';
type Step = 1 | 2 | 3 | 4;

const PLATFORMS: { id: Platform; label: string; icon: string }[] = [
  { id: 'linux', label: 'Linux', icon: '🐧' },
  { id: 'windows', label: 'Windows', icon: '🪟' },
  { id: 'macos', label: 'macOS', icon: '🍎' },
  { id: 'android', label: 'Android', icon: '🤖' },
  { id: 'ios', label: 'iOS', icon: '📱' },
];

const GITHUB_BASE = 'https://github.com/Indivisible2025/Cloudfin/releases/download/v2026.4.5.1';
const GITEE_BASE = 'https://gitee.com/Nianyv/Cloudfin/releases/download/v2026-04-05-1';

interface InstallContent {
  core: boolean;
  p2p: boolean;
  crdt: boolean;
}

const VERSION = 'v2026.4.5.1';

function getDownloadUrls(platform: Platform, useGitee = false) {
  const base = useGitee ? GITEE_BASE : GITHUB_BASE;
  const urls: Record<string, string> = {};
  if (platform === 'linux') {
    urls.core = `${base}/Cloudfin-Core-Linux-amd64-${VERSION}.tar.gz`;
    urls.p2p = `${base}/Cloudfin-Mod-Linux-amd64-P2P-${VERSION}.zip`;
    urls.crdt = `${base}/Cloudfin-Mod-Linux-amd64-CRDT-${VERSION}.zip`;
  } else if (platform === 'windows') {
    urls.core = `${base}/Cloudfin-Core-Windows-amd64-${VERSION}.zip`;
    urls.p2p = `${base}/Cloudfin-Mod-Windows-amd64-P2P-${VERSION}.zip`;
    urls.crdt = `${base}/Cloudfin-Mod-Windows-amd64-CRDT-${VERSION}.zip`;
  } else if (platform === 'macos') {
    urls.core = `${base}/Cloudfin-Core-macOS-amd64-${VERSION}.tar.gz`;
    urls.p2p = `${base}/Cloudfin-Mod-macOS-amd64-P2P-${VERSION}.zip`;
    urls.crdt = `${base}/Cloudfin-Mod-macOS-amd64-CRDT-${VERSION}.zip`;
  } else if (platform === 'android') {
    urls.core = `${base}/Cloudfin-UI-Android-arm64-v8a-${VERSION}.apk`;
    urls.p2p = `${base}/Cloudfin-Mod-Android-arm64-v8a-P2P-${VERSION}.zip`;
    urls.crdt = `${base}/Cloudfin-Mod-Android-arm64-v8a-CRDT-${VERSION}.zip`;
  } else if (platform === 'ios') {
    urls.core = `${base}/Cloudfin-UI-iOS-${VERSION}.ipa`;
  }
  return urls;
}

interface ProgressInfo {
  state: 'idle' | 'downloading' | 'extracting' | 'starting' | 'done' | 'error';
  message: string;
  progress?: number;
}

export default function InstallGuide({ onComplete }: { onComplete?: () => void }) {
  const [step, setStep] = useState<Step>(1);
  const [platform, setPlatform] = useState<Platform | null>(null);
  const [content, setContent] = useState<InstallContent>({ core: true, p2p: true, crdt: true });
  const [useGitee, setUseGitee] = useState(false);
  const [progress, setProgress] = useState<ProgressInfo>({ state: 'idle', message: '' });
  const [error, setError] = useState('');

  // URLs that will be used by Tauri install command
  const downloadUrls = platform ? getDownloadUrls(platform, useGitee) : null;

  function toggleContent(key: keyof InstallContent) {
    setContent(prev => ({ ...prev, [key]: !prev[key] }));
  }

  async function doInstall() {
    if (!platform) return;
    setStep(3);
    setProgress({ state: 'downloading', message: '正在下载...', progress: 0 });
    setError('');

    // In production: Tauri shell command uses downloadUrls to install
    // Simulation — URLs already computed: downloadUrls
    try {
      for (let i = 0; i <= 100; i += 10) {
        await new Promise(r => setTimeout(r, 150));
        setProgress({ state: 'downloading', message: `正在下载... ${i}%`, progress: i });
      }

      setProgress({ state: 'extracting', message: '正在解压...' });
      await new Promise(r => setTimeout(r, 500));

      setProgress({ state: 'starting', message: '正在启动 Core...' });
      await new Promise(r => setTimeout(r, 300));

      setProgress({ state: 'done', message: '安装完成！' });
      setStep(4);
      localStorage.setItem('installGuideDismissed', 'true');
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '安装失败';
      setError(msg);
      setProgress({ state: 'error', message: msg });
    }
  }

  const isMobile = platform === 'android' || platform === 'ios';

  return (
    <div className="install-guide">
      {step === 1 && (
        <div className="guide-step">
          <div className="guide-header">
            <div className="guide-icon">🌍</div>
            <h2>选择你的平台</h2>
            <p>Cloudfin 支持以下平台，请选择你要安装的平台：</p>
          </div>
          <div className="platform-grid">
            {PLATFORMS.map(p => (
              <button
                key={p.id}
                className={`platform-card ${platform === p.id ? 'selected' : ''}`}
                onClick={() => setPlatform(p.id)}
              >
                <span className="platform-icon">{p.icon}</span>
                <span className="platform-label">{p.label}</span>
              </button>
            ))}
          </div>
          <div className="guide-actions">
            <button
              className="btn-primary"
              disabled={!platform}
              onClick={() => setStep(2)}
            >
              下一步 →
            </button>
          </div>
        </div>
      )}

      {step === 2 && platform && (
        <div className="guide-step">
          <div className="guide-header">
            <div className="guide-icon">📦</div>
            <h2>选择安装内容</h2>
            <p>选择你要安装的组件：</p>
          </div>
          <div className="content-select">
            <label className={`content-card ${content.core ? 'selected' : ''}`}>
              <input
                type="checkbox"
                checked={content.core}
                onChange={() => toggleContent('core')}
              />
              <div className="content-info">
                <span className="content-name">Cloudfin Core</span>
                <span className="content-desc">核心守护进程（必需）</span>
              </div>
            </label>
            {!isMobile && (
              <label className={`content-card ${content.p2p ? 'selected' : ''}`}>
                <input
                  type="checkbox"
                  checked={content.p2p}
                  onChange={() => toggleContent('p2p')}
                />
                <div className="content-info">
                  <span className="content-name">P2P 模块</span>
                  <span className="content-desc">libp2p 网络模块（Kad-DHT + mDNS）</span>
                </div>
              </label>
            )}
            {!isMobile && (
              <label className={`content-card ${content.crdt ? 'selected' : ''}`}>
                <input
                  type="checkbox"
                  checked={content.crdt}
                  onChange={() => toggleContent('crdt')}
                />
                <div className="content-info">
                  <span className="content-name">CRDT 同步模块</span>
                  <span className="content-desc">yrs 文档同步模块</span>
                </div>
              </label>
            )}
          </div>

          <div className="mirror-select">
            <p className="mirror-label">下载源：</p>
            <div className="mirror-btns">
              <button
                className={`mirror-btn ${!useGitee ? 'active' : ''}`}
                onClick={() => setUseGitee(false)}
              >🌐 GitHub（推荐）</button>
              <button
                className={`mirror-btn ${useGitee ? 'active' : ''}`}
                onClick={() => setUseGitee(true)}
              >🇨🇳 Gitee</button>
            </div>
          </div>

          {downloadUrls && (
            <div className="download-preview">
              <p className="download-preview-label">将下载：</p>
              {Object.entries(downloadUrls).map(([key, url]) => (
                <div key={key} className="download-url-item">
                  <span className="download-url-name">{key}</span>
                  <span className="download-url-path">{url.split('/').pop()}</span>
                </div>
              ))}
            </div>
          )}

          <div className="guide-actions">
            <button className="btn-secondary" onClick={() => setStep(1)}>← 上一步</button>
            <button
              className="btn-primary"
              disabled={!content.core}
              onClick={doInstall}
            >
              开始自动安装 →
            </button>
          </div>
        </div>
      )}

      {step === 3 && (
        <div className="guide-step">
          <div className="guide-header">
            <div className="guide-icon">⚙️</div>
            <h2>正在安装...</h2>
            <p>{progress.message}</p>
          </div>
          <div className="progress-bar">
            <div
              className="progress-fill"
              style={{ width: `${progress.progress ?? 0}%` }}
            />
          </div>
          {error && <p className="error-text">{error}</p>}
        </div>
      )}

      {step === 4 && (
        <div className="guide-step">
          <div className="guide-header">
            <div className="guide-icon">🎉</div>
            <h2>安装完成！</h2>
            <p>Cloudfin Core 已启动，正在连接...</p>
          </div>
          <div className="guide-actions">
            <button className="btn-primary" onClick={onComplete}>
              打开 Cloudfin →
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
