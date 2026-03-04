import { create } from 'zustand';
import { GatewayClient } from '@/lib/gateway-client';

type ConnectionState = 'disconnected' | 'connecting' | 'connected';

interface ConnectionStore {
  state: ConnectionState;
  client: GatewayClient | null;
  wsSessionId: string | null;
  error: string | null;

  connect: (url?: string, token?: string) => void;
  disconnect: () => void;
}

export const useConnectionStore = create<ConnectionStore>((set, get) => ({
  state: 'disconnected',
  client: null,
  wsSessionId: null,
  error: null,

  connect: (url?: string, token?: string) => {
    const existing = get().client;
    if (existing) existing.disconnect();

    const wsUrl = url || `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`;
    const authToken = token || localStorage.getItem('openclaw-token') || undefined;
    const client = new GatewayClient(wsUrl, authToken);

    client.onConnection((state) => {
      set({
        state,
        wsSessionId: state === 'connected' ? client.wsSessionId : null,
        error: null,
      });
    });

    set({ client, state: 'connecting', error: null });
    client.connect();
  },

  disconnect: () => {
    const { client } = get();
    if (client) client.disconnect();
    set({ state: 'disconnected', client: null, wsSessionId: null });
  },
}));
