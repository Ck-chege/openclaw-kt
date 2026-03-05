import { create } from 'zustand';
import { GatewayClient } from '@/lib/gateway-client';
import { notify } from '@/lib/notifications';

type ConnectionState = 'disconnected' | 'connecting' | 'connected';

interface ConnectionStore {
  state: ConnectionState;
  client: GatewayClient | null;
  wsSessionId: string | null;
  setupRequired: boolean;
  onboardingPhase: string | null;
  error: string | null;

  connect: (url?: string, token?: string) => void;
  disconnect: () => void;
  clearSetupRequired: () => void;
}

export const useConnectionStore = create<ConnectionStore>((set, get) => ({
  state: 'disconnected',
  client: null,
  wsSessionId: null,
  setupRequired: false,
  onboardingPhase: null,
  error: null,

  connect: (url?: string, token?: string) => {
    const existing = get().client;
    if (existing) existing.disconnect();

    const wsUrl = url || `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`;
    const authToken = token || localStorage.getItem('openclaw-token') || undefined;
    const client = new GatewayClient(wsUrl, authToken);

    client.onConnection((connState) => {
      set({
        state: connState,
        wsSessionId: connState === 'connected' ? client.wsSessionId : null,
        setupRequired: connState === 'connected' ? client.setupRequired : false,
        onboardingPhase: connState === 'connected' ? client.onboardingPhase : null,
        error: null,
      });
      if (connState === 'connected') notify.connected();
      else if (connState === 'disconnected') notify.disconnected();
    });

    set({ client, state: 'connecting', error: null });
    client.connect();
  },

  disconnect: () => {
    const { client } = get();
    if (client) client.disconnect();
    set({ state: 'disconnected', client: null, wsSessionId: null, setupRequired: false, onboardingPhase: null });
  },

  clearSetupRequired: () => {
    set({ setupRequired: false, onboardingPhase: null });
  },
}));
