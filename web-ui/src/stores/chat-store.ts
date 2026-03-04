import { create } from 'zustand';
import type {
  ChatMessage,
  ToolCallInfo,
  TokenUsage,
  SessionMessage,
} from '@/types/protocol';
import { GatewayClient } from '@/lib/gateway-client';

const DEFAULT_SESSION_KEY = 'main:main:*:main';

interface ChatStore {
  activeSessionKey: string;
  messages: ChatMessage[];
  isStreaming: boolean;
  error: string | null;

  setActiveSession: (key: string) => void;
  loadHistory: (client: GatewayClient) => Promise<void>;
  sendMessage: (client: GatewayClient, text: string) => Promise<void>;
  stopAgent: (client: GatewayClient) => Promise<void>;
  clearMessages: () => void;
  registerEventHandlers: (client: GatewayClient) => () => void;
}

function sessionMessagesToChatMessages(messages: SessionMessage[]): ChatMessage[] {
  const result: ChatMessage[] = [];
  let currentAssistant: ChatMessage | null = null;

  for (const msg of messages) {
    if (msg.type === 'user') {
      currentAssistant = null;
      result.push({
        id: msg.id,
        role: 'user',
        content: msg.text || '',
        timestamp: msg.timestamp,
      });
    } else if (msg.type === 'assistant') {
      currentAssistant = {
        id: msg.id,
        role: 'assistant',
        content: msg.text || '',
        timestamp: msg.timestamp,
        model: msg.model,
        usage: msg.usage,
        toolCalls: [],
      };
      result.push(currentAssistant);
    } else if (msg.type === 'tool_call' && currentAssistant) {
      currentAssistant.toolCalls = currentAssistant.toolCalls || [];
      currentAssistant.toolCalls.push({
        name: msg.toolName || 'unknown',
        args: msg.args || {},
        status: 'complete',
      });
    } else if (msg.type === 'tool_result' && currentAssistant) {
      const tc = currentAssistant.toolCalls?.find(
        (t) => t.name === msg.toolName && !t.result
      );
      if (tc) {
        tc.result = msg.result;
        tc.durationMs = msg.durationMs;
      }
    }
  }

  return result;
}

export const useChatStore = create<ChatStore>((set, get) => ({
  activeSessionKey: DEFAULT_SESSION_KEY,
  messages: [],
  isStreaming: false,
  error: null,

  setActiveSession: (key: string) => {
    set({ activeSessionKey: key, messages: [], isStreaming: false, error: null });
  },

  loadHistory: async (client: GatewayClient) => {
    const { activeSessionKey } = get();
    try {
      const res = await client.request('sessions.history', {
        key: activeSessionKey,
        limit: 100,
      });
      const sessionMessages = (res.messages || []) as SessionMessage[];
      const chatMessages = sessionMessagesToChatMessages(sessionMessages);
      set({ messages: chatMessages, error: null });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  sendMessage: async (client: GatewayClient, text: string) => {
    const { activeSessionKey, messages } = get();
    const userMsg: ChatMessage = {
      id: crypto.randomUUID(),
      role: 'user',
      content: text,
      timestamp: new Date().toISOString(),
    };
    const assistantMsg: ChatMessage = {
      id: crypto.randomUUID(),
      role: 'assistant',
      content: '',
      timestamp: new Date().toISOString(),
      isStreaming: true,
      toolCalls: [],
    };

    set({
      messages: [...messages, userMsg, assistantMsg],
      isStreaming: true,
      error: null,
    });

    try {
      const res = await client.request('agent.run', {
        key: activeSessionKey,
        message: text,
      });
      // Update model info from response
      if (res.model) {
        set((state) => ({
          messages: state.messages.map((m) =>
            m.id === assistantMsg.id ? { ...m, model: res.model as string } : m
          ),
        }));
      }
    } catch (e) {
      set((state) => ({
        isStreaming: false,
        error: (e as Error).message,
        messages: state.messages.map((m) =>
          m.id === assistantMsg.id
            ? { ...m, isStreaming: false, content: `Error: ${(e as Error).message}` }
            : m
        ),
      }));
    }
  },

  stopAgent: async (client: GatewayClient) => {
    const { activeSessionKey } = get();
    try {
      await client.request('agent.stop', { key: activeSessionKey });
    } catch {
      // ignore
    }
  },

  clearMessages: () => {
    set({ messages: [], isStreaming: false, error: null });
  },

  registerEventHandlers: (client: GatewayClient) => {
    const handleToken = (payload: Record<string, unknown>) => {
      const text = payload.text as string;
      set((state) => {
        const msgs = [...state.messages];
        const last = msgs[msgs.length - 1];
        if (last?.role === 'assistant' && last.isStreaming) {
          msgs[msgs.length - 1] = { ...last, content: last.content + text };
        }
        return { messages: msgs };
      });
    };

    const handleToolStart = (payload: Record<string, unknown>) => {
      const tool: ToolCallInfo = {
        name: payload.name as string,
        args: (payload.args as Record<string, unknown>) || {},
        status: 'running',
      };
      set((state) => {
        const msgs = [...state.messages];
        const last = msgs[msgs.length - 1];
        if (last?.role === 'assistant' && last.isStreaming) {
          msgs[msgs.length - 1] = {
            ...last,
            toolCalls: [...(last.toolCalls || []), tool],
          };
        }
        return { messages: msgs };
      });
    };

    const handleToolResult = (payload: Record<string, unknown>) => {
      const name = payload.name as string;
      const result = payload.result as string;
      const durationMs = payload.durationMs as number;
      set((state) => {
        const msgs = [...state.messages];
        const last = msgs[msgs.length - 1];
        if (last?.role === 'assistant' && last.isStreaming) {
          const toolCalls = (last.toolCalls || []).map((tc) =>
            tc.name === name && tc.status === 'running'
              ? { ...tc, result, durationMs, status: 'complete' as const }
              : tc
          );
          msgs[msgs.length - 1] = { ...last, toolCalls };
        }
        return { messages: msgs };
      });
    };

    const handleComplete = (payload: Record<string, unknown>) => {
      const finalText = payload.finalText as string;
      const usage = payload.usage as TokenUsage | undefined;
      set((state) => {
        const msgs = [...state.messages];
        const last = msgs[msgs.length - 1];
        if (last?.role === 'assistant' && last.isStreaming) {
          msgs[msgs.length - 1] = {
            ...last,
            content: finalText || last.content,
            isStreaming: false,
            usage,
          };
        }
        return { messages: msgs, isStreaming: false };
      });
    };

    const handleError = (payload: Record<string, unknown>) => {
      const message = payload.message as string;
      set((state) => {
        const msgs = [...state.messages];
        const last = msgs[msgs.length - 1];
        if (last?.role === 'assistant' && last.isStreaming) {
          msgs[msgs.length - 1] = {
            ...last,
            content: last.content || `Error: ${message}`,
            isStreaming: false,
          };
        }
        return { messages: msgs, isStreaming: false, error: message };
      });
    };

    client.on('agent.token', handleToken);
    client.on('agent.tool.start', handleToolStart);
    client.on('agent.tool.result', handleToolResult);
    client.on('agent.complete', handleComplete);
    client.on('agent.error', handleError);

    return () => {
      client.off('agent.token', handleToken);
      client.off('agent.tool.start', handleToolStart);
      client.off('agent.tool.result', handleToolResult);
      client.off('agent.complete', handleComplete);
      client.off('agent.error', handleError);
    };
  },
}));
