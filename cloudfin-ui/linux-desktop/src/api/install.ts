import { invoke } from '@tauri-apps/api/core';
import { listen, UnlistenFn } from '@tauri-apps/api/event';

export type InstallProgress = {
  stage: 'downloading' | 'extracting' | 'done';
  percent: number;
  message: string;
};

export async function installExecute(
  items: { name: string; url: string; filename: string }[],
  installDir?: string
): Promise<void> {
  await invoke('install_execute', {
    request: { items, install_dir: installDir }
  });
}

export function onInstallProgress(
  callback: (progress: InstallProgress) => void
): Promise<UnlistenFn> {
  return listen<InstallProgress>('install_progress', (event: { payload: InstallProgress }) => {
    callback(event.payload);
  });
}
