import { useEffect, useRef, useState } from 'react';
import { useConnectionStore } from '@/stores/connection-store';
import { useChatStore } from '@/stores/chat-store';
import { useDashboardStore } from '@/stores/dashboard-store';
import '@/stores/theme-store'; // ensure theme initializes on app boot
import { ConnectionBanner } from './ConnectionBanner';
import { ConnectDialog } from './ConnectDialog';
import { Sidebar } from './sidebar/Sidebar';
import { ChatView } from './chat/ChatView';

export function Layout() {
  const { state, client, connect } = useConnectionStore();
  const { registerEventHandlers, loadHistory } = useChatStore();
  const { fetchSessions, fetchConfig, fetchModels } = useDashboardStore();
  const cleanupRef = useRef<(() => void) | null>(null);
  const [hasAttempted, setHasAttempted] = useState(false);

  // Auto-connect if token is stored
  useEffect(() => {
    const storedToken = localStorage.getItem('openclaw-token');
    if (storedToken) {
      connect(undefined, storedToken);
      setHasAttempted(true);
    }
    return () => {
      useConnectionStore.getState().disconnect();
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Register event handlers and load data when connected
  useEffect(() => {
    if (state === 'connected' && client) {
      setHasAttempted(true);
      cleanupRef.current = registerEventHandlers(client);
      loadHistory(client);
      fetchSessions(client);
      fetchConfig(client);
      fetchModels(client);
    }
    return () => {
      if (cleanupRef.current) {
        cleanupRef.current();
        cleanupRef.current = null;
      }
    };
  }, [state, client, registerEventHandlers, loadHistory, fetchSessions, fetchConfig, fetchModels]);

  if (!hasAttempted && state === 'disconnected') {
    return <ConnectDialog />;
  }

  return (
    <div className="flex h-screen w-screen overflow-hidden">
      <Sidebar />
      <div className="flex flex-col flex-1 min-w-0">
        <ConnectionBanner />
        <ChatView />
      </div>
    </div>
  );
}
