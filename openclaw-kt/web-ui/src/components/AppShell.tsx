import { useEffect, useRef } from 'react';
import { Outlet } from 'react-router-dom';
import { useConnectionStore } from '@/stores/connection-store';
import { useChatStore } from '@/stores/chat-store';
import { useDashboardStore } from '@/stores/dashboard-store';
import '@/stores/theme-store';
import { ConnectDialog } from './ConnectDialog';
import { SetupWizard } from './SetupWizard';
import { AppSidebar } from './AppSidebar';
import { HeaderBar } from './HeaderBar';
import { CommandPalette } from './CommandPalette';
import { useKeyboardShortcuts } from '@/hooks/useKeyboardShortcuts';
import { Toaster } from 'sonner';

export function AppShell() {
  const { state, client, connect, setupRequired } = useConnectionStore();
  const { registerEventHandlers, ensureSession, loadHistory } = useChatStore();
  const { fetchSessions, fetchConfig, fetchModels } = useDashboardStore();
  const cleanupRef = useRef<(() => void) | null>(null);
  const hasAttemptedRef = useRef(false);

  useKeyboardShortcuts();

  // Auto-connect if token is stored
  useEffect(() => {
    const storedToken = localStorage.getItem('openclaw-token');
    if (storedToken) {
      connect(undefined, storedToken);
      hasAttemptedRef.current = true;
    }
    return () => {
      useConnectionStore.getState().disconnect();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Register event handlers and load data when connected
  useEffect(() => {
    if (state === 'connected' && client) {
      hasAttemptedRef.current = true;
      cleanupRef.current = registerEventHandlers(client);
      void (async () => {
        try {
          await ensureSession(client);
        } catch {
          // Missing initial session is handled by sessions.history fallback.
        }
        await loadHistory(client);
        await fetchSessions(client);
        await fetchConfig(client);
        await fetchModels(client);
      })();
    }
    return () => {
      if (cleanupRef.current) {
        cleanupRef.current();
        cleanupRef.current = null;
      }
    };
  }, [state, client, registerEventHandlers, ensureSession, loadHistory, fetchSessions, fetchConfig, fetchModels]);

  // Show connect dialog if never attempted
  if (!hasAttemptedRef.current && state === 'disconnected') {
    return <ConnectDialog />;
  }

  // Show setup wizard only while required configuration is missing.
  if (state === 'connected' && setupRequired) {
    return <SetupWizard />;
  }

  return (
    <div className="flex h-screen w-screen overflow-hidden bg-background">
      {/* Skip to content link for accessibility */}
      <a href="#main-content" className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-[100] focus:px-4 focus:py-2 focus:bg-primary focus:text-primary-foreground focus:rounded-md">
        Skip to content
      </a>
      <AppSidebar />
      <div className="flex flex-col flex-1 min-w-0">
        <HeaderBar />
        <main id="main-content" className="flex flex-col flex-1 min-h-0" role="main">
          <Outlet />
        </main>
      </div>
      <CommandPalette />
      <Toaster
        position="bottom-right"
        toastOptions={{
          className: 'text-sm',
        }}
      />
    </div>
  );
}
