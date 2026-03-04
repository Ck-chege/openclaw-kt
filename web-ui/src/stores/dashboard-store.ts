import { create } from 'zustand';
import type { SessionMeta } from '@/types/protocol';
import type { GatewayClient } from '@/lib/gateway-client';

interface DashboardStore {
  sessions: SessionMeta[];
  config: Record<string, unknown> | null;
  models: string[];
  loading: boolean;

  fetchSessions: (client: GatewayClient) => Promise<void>;
  fetchConfig: (client: GatewayClient) => Promise<void>;
  fetchModels: (client: GatewayClient) => Promise<void>;
  deleteSession: (client: GatewayClient, key: string) => Promise<void>;
  resetSession: (client: GatewayClient, key: string) => Promise<void>;
}

export const useDashboardStore = create<DashboardStore>((set) => ({
  sessions: [],
  config: null,
  models: [],
  loading: false,

  fetchSessions: async (client: GatewayClient) => {
    set({ loading: true });
    try {
      const res = await client.request('sessions.list');
      set({ sessions: (res.sessions || []) as SessionMeta[], loading: false });
    } catch {
      set({ loading: false });
    }
  },

  fetchConfig: async (client: GatewayClient) => {
    try {
      const res = await client.request('config.get');
      set({ config: res });
    } catch {
      // config endpoint may not exist yet
    }
  },

  fetchModels: async (client: GatewayClient) => {
    try {
      const res = await client.request('models.list');
      set({ models: (res.models || []) as string[] });
    } catch {
      // models endpoint may not exist yet
    }
  },

  deleteSession: async (client: GatewayClient, key: string) => {
    try {
      await client.request('sessions.delete', { key });
      set((state) => ({
        sessions: state.sessions.filter((s) => s.key !== key),
      }));
    } catch {
      // ignore
    }
  },

  resetSession: async (client: GatewayClient, key: string) => {
    try {
      await client.request('sessions.reset', { key });
    } catch {
      // ignore
    }
  },
}));
