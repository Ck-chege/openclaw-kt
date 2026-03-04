import { ScrollArea } from '@/components/ui/scroll-area';
import { useDashboardStore } from '@/stores/dashboard-store';
import { useChatStore } from '@/stores/chat-store';
import { useConnectionStore } from '@/stores/connection-store';
import { SessionItem } from './SessionItem';
import { Loader2, Inbox } from 'lucide-react';

export function SessionList() {
  const { sessions, loading, deleteSession, resetSession, fetchSessions } = useDashboardStore();
  const { activeSessionKey, setActiveSession, loadHistory, clearMessages } = useChatStore();
  const { client } = useConnectionStore();

  const handleSelect = async (key: string) => {
    if (key === activeSessionKey) return;
    setActiveSession(key);
    if (client) {
      await loadHistory(client);
    }
  };

  const handleDelete = async (key: string) => {
    if (!client) return;
    await deleteSession(client, key);
    if (key === activeSessionKey) {
      clearMessages();
    }
  };

  const handleReset = async (key: string) => {
    if (!client) return;
    await resetSession(client, key);
    if (key === activeSessionKey) {
      clearMessages();
    }
    await fetchSessions(client);
  };

  if (loading && sessions.length === 0) {
    return (
      <div className="flex items-center justify-center py-8">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (sessions.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-8 text-muted-foreground">
        <Inbox className="h-8 w-8 mb-2 opacity-30" />
        <p className="text-xs">No sessions yet</p>
      </div>
    );
  }

  return (
    <ScrollArea className="flex-1">
      <div className="p-2 space-y-0.5">
        {sessions.map((session) => (
          <SessionItem
            key={session.key}
            session={session}
            isActive={session.key === activeSessionKey}
            onSelect={() => handleSelect(session.key)}
            onDelete={() => handleDelete(session.key)}
            onReset={() => handleReset(session.key)}
          />
        ))}
      </div>
    </ScrollArea>
  );
}
