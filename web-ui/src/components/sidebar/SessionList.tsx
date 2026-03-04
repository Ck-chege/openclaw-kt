import { useState, useMemo } from 'react';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Input } from '@/components/ui/input';
import { useDashboardStore } from '@/stores/dashboard-store';
import { useChatStore } from '@/stores/chat-store';
import { useConnectionStore } from '@/stores/connection-store';
import { SessionItem } from './SessionItem';
import { Loader2, Inbox, Search } from 'lucide-react';
import type { SessionMeta } from '@/types/protocol';

function groupByDate(sessions: SessionMeta[]): { label: string; sessions: SessionMeta[] }[] {
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const yesterday = new Date(today.getTime() - 86400000);
  const thisWeek = new Date(today.getTime() - 7 * 86400000);

  const groups: Record<string, SessionMeta[]> = {
    Today: [],
    Yesterday: [],
    'This Week': [],
    Older: [],
  };

  for (const s of sessions) {
    const date = new Date(s.lastMessageAt || s.createdAt);
    if (date >= today) groups['Today'].push(s);
    else if (date >= yesterday) groups['Yesterday'].push(s);
    else if (date >= thisWeek) groups['This Week'].push(s);
    else groups['Older'].push(s);
  }

  return Object.entries(groups)
    .filter(([, list]) => list.length > 0)
    .map(([label, list]) => ({ label, sessions: list }));
}

export function SessionList() {
  const { sessions, loading, deleteSession, resetSession, fetchSessions } = useDashboardStore();
  const { activeSessionKey, setActiveSession, loadHistory, clearMessages } = useChatStore();
  const { client } = useConnectionStore();
  const [search, setSearch] = useState('');

  const filtered = useMemo(() => {
    if (!search) return sessions;
    const q = search.toLowerCase();
    return sessions.filter((s) => s.key.toLowerCase().includes(q) || s.agentId.toLowerCase().includes(q));
  }, [sessions, search]);

  // Sort by last activity descending
  const sorted = useMemo(() =>
    [...filtered].sort((a, b) =>
      (b.lastMessageAt || b.createdAt).localeCompare(a.lastMessageAt || a.createdAt)
    ),
  [filtered]);

  const groups = useMemo(() => groupByDate(sorted), [sorted]);

  const handleSelect = async (key: string) => {
    if (key === activeSessionKey) return;
    setActiveSession(key);
    if (client) await loadHistory(client);
  };

  const handleDelete = async (key: string) => {
    if (!client) return;
    await deleteSession(client, key);
    if (key === activeSessionKey) clearMessages();
  };

  const handleReset = async (key: string) => {
    if (!client) return;
    await resetSession(client, key);
    if (key === activeSessionKey) clearMessages();
    await fetchSessions(client);
  };

  if (loading && sessions.length === 0) {
    return (
      <div className="flex items-center justify-center py-8">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="flex flex-col flex-1 min-h-0">
      {/* Search */}
      {sessions.length > 3 && (
        <div className="px-2 pb-1.5">
          <div className="relative">
            <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3 w-3 text-muted-foreground" />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Filter sessions..."
              className="h-6 text-[11px] pl-6 pr-2 bg-sidebar-accent/50 border-sidebar-border"
            />
          </div>
        </div>
      )}

      {sessions.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-8 text-muted-foreground">
          <Inbox className="h-8 w-8 mb-2 opacity-30" />
          <p className="text-xs">No sessions yet</p>
        </div>
      ) : filtered.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-4 text-muted-foreground">
          <p className="text-xs">No matches</p>
        </div>
      ) : (
        <ScrollArea className="flex-1">
          <div className="px-2 pb-1">
            {groups.map((group) => (
              <div key={group.label}>
                <p className="text-[10px] font-medium text-sidebar-foreground/40 uppercase tracking-wider px-1 pt-2 pb-1">
                  {group.label}
                </p>
                <div className="space-y-0.5">
                  {group.sessions.map((session) => (
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
              </div>
            ))}
          </div>
        </ScrollArea>
      )}
    </div>
  );
}
