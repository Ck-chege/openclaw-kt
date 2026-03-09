import { create } from 'zustand';
import type { SessionMeta } from '@/types/protocol';
import type { GatewayClient } from '@/lib/gateway-client';
import { notify } from '@/lib/notifications';

interface ModelEntry {
  id: string;
  displayName: string;
  provider?: string;
  ready?: boolean;
  unavailableReason?: string;
}

interface DashboardStore {
  sessions: SessionMeta[];
  config: Record<string, unknown> | null;
  models: ModelEntry[];
  supportedModels: ModelEntry[];
  selectedModelId: string | null;
  loading: boolean;

  fetchSessions: (client: GatewayClient) => Promise<void>;
  fetchConfig: (client: GatewayClient) => Promise<void>;
  fetchModels: (client: GatewayClient) => Promise<void>;
  setSelectedModel: (modelId: string) => void;
  deleteSession: (client: GatewayClient, key: string) => Promise<void>;
  resetSession: (client: GatewayClient, key: string) => Promise<void>;
}

export const useDashboardStore = create<DashboardStore>((set) => ({
  sessions: [],
  config: null,
  models: [],
  supportedModels: [],
  selectedModelId: null,
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
      const parseModels = (value: unknown): ModelEntry[] => {
        const raw = (value || []) as Array<string | { id?: string; name?: string; provider?: string; ready?: boolean; unavailableReason?: string }>;
        return raw.map((m) => {
          if (typeof m === 'string') return { id: m, displayName: m, ready: true };
          return {
            id: m.id || m.name || String(m),
            displayName: m.name || m.id || String(m),
            provider: m.provider,
            ready: m.ready ?? true,
            unavailableReason: m.unavailableReason,
          };
        });
      };

      const models = parseModels(res.models);
      const supportedModels = parseModels(res.supportedModels ?? res.models);

      set((state) => {
        const selectedStillReady = state.selectedModelId != null && models.some((model) => model.id === state.selectedModelId);
        return {
          models,
          supportedModels,
          selectedModelId: selectedStillReady ? state.selectedModelId : (models[0]?.id ?? null),
        };
      });
    } catch {
      // models endpoint may not exist yet
    }
  },

  setSelectedModel: (modelId: string) => {
    set({ selectedModelId: modelId });
  },

  deleteSession: async (client: GatewayClient, key: string) => {
    try {
      await client.request('sessions.delete', { key });
      set((state) => ({
        sessions: state.sessions.filter((s) => s.key !== key),
      }));
      notify.sessionDeleted(key);
    } catch (e) {
      notify.rpcError('sessions.delete', (e as Error).message);
    }
  },

  resetSession: async (client: GatewayClient, key: string) => {
    try {
      await client.request('sessions.reset', { key });
      notify.sessionReset(key);
    } catch (e) {
      notify.rpcError('sessions.reset', (e as Error).message);
    }
  },
}));
