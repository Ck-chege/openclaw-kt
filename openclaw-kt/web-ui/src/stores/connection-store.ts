import { create } from 'zustand';
import { GatewayClient } from '@/lib/gateway-client';
import { resolveGatewayUrl } from '@/lib/gateway-url';
import { notify } from '@/lib/notifications';

type ConnectionState = 'disconnected' | 'connecting' | 'connected';

interface ConnectionStore {
  state: ConnectionState;
  client: GatewayClient | null;
  wsSessionId: string | null;
  setupRequired: boolean;
  onboardingPhase: string | null;
  error: string | null;

  connect: (url?: string, token?: string, role?: 'operator' | 'webchat' | 'node') => void;
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

  connect: (url?: string, token?: string, role: 'operator' | 'webchat' | 'node' = 'operator') => {
    const existing = get().client;
    if (existing) existing.disconnect();

    const wsUrl = resolveGatewayUrl(url);
    const authToken = token || localStorage.getItem('openclaw-token') || undefined;
    const client = new GatewayClient(wsUrl, authToken, role);

    client.onConnection((connState, detail) => {
      set({
        state: connState,
        wsSessionId: connState === 'connected' ? client.wsSessionId : null,
        setupRequired: connState === 'connected' ? client.setupRequired : false,
        onboardingPhase: connState === 'connected' ? client.onboardingPhase : null,
        error: connState === 'disconnected' ? (detail || client.lastDisconnectReason) : null,
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
