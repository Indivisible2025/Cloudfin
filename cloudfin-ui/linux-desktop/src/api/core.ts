// Cloudfin Core WebSocket API
// Connects to Core at ws://127.0.0.1:19001/ws

export interface CoreStatus {
  online: boolean;
  version: string;
  uptime: number;
  modules_loaded: number;
  modules_total: number;
  network: {
    peers_connected: number;
    bandwidth_in: number;
    bandwidth_out: number;
  };
}

export interface Module {
  id: string;
  name: string;
  status: 'active' | 'inactive' | 'error';
  version: string;
  description: string;
}

export interface Peer {
  id: string;
  address: string;
  latency: number;
  connected: boolean;
}

let ws: WebSocket | null = null;
let requestId = 0;
const listeners: Map<string, Set<(data: any) => void>> = new Map();

function genId(): string {
  return `${Date.now()}-${++requestId}`;
}

export function connect(): Promise<void> {
  return new Promise((resolve, reject) => {
    try {
      ws = new WebSocket('ws://127.0.0.1:19001/ws');

      ws.onopen = () => {
        console.log('[Cloudfin] Connected to Core');
        resolve();
      };

      ws.onerror = (e) => {
        console.error('[Cloudfin] WebSocket error', e);
        reject(new Error('Connection failed'));
      };

      ws.onclose = () => {
        console.log('[Cloudfin] Disconnected from Core');
        ws = null;
        // Try reconnect after 3s
        setTimeout(() => connect().catch(() => {}), 3000);
      };

      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);
          if (msg.type === 'response' && msg.request_id) {
            const id = msg.request_id as string;
            listeners.get(id)?.forEach(cb => cb(msg.data));
            listeners.delete(id);
          } else if (msg.type === 'status_update') {
            listeners.get('status_update')?.forEach(cb => cb(msg.data));
          }
        } catch (e) {
          console.error('[Cloudfin] Failed to parse message', e);
        }
      };
    } catch (e) {
      reject(e);
    }
  });
}

export function disconnect() {
  ws?.close();
  ws = null;
}

function sendRequest(action: string, data: any = {}): Promise<any> {
  return new Promise((resolve, reject) => {
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      reject(new Error('Not connected'));
      return;
    }
    const id = genId();
    const listener = (respData: any) => resolve(respData);
    listeners.set(id, new Set([listener]));
    setTimeout(() => {
      listeners.delete(id);
      reject(new Error('Request timeout'));
    }, 10000);
    ws.send(JSON.stringify({ type: 'request', action, request_id: id, data }));
  });
}

export function onStatusUpdate(cb: (data: any) => void) {
  if (!listeners.has('status_update')) {
    listeners.set('status_update', new Set());
  }
  listeners.get('status_update')!.add(cb);
  return () => listeners.get('status_update')?.delete(cb);
}

export async function getStatus(): Promise<CoreStatus> {
  return sendRequest('status', {});
}

export async function getModules(): Promise<Module[]> {
  return sendRequest('modules.list', {});
}

export async function toggleModule(id: string, enabled: boolean): Promise<void> {
  return sendRequest('modules.toggle', { id, enabled });
}

export async function getPeers(): Promise<Peer[]> {
  return sendRequest('network.peers', {});
}

export async function getSyncStatus(): Promise<any> {
  return sendRequest('sync.status', {});
}

export async function getConfig(): Promise<any> {
  return sendRequest('config.get', {});
}

export async function setConfig(key: string, value: any): Promise<void> {
  return sendRequest('config.set', { key, value });
}
