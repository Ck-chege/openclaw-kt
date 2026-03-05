import { useState, useMemo, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDashboardStore } from '@/stores/dashboard-store';
import { useChatStore } from '@/stores/chat-store';
import { useConnectionStore } from '@/stores/connection-store';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { formatSessionKey, formatTimestamp, formatTokens } from '@/lib/format';
import type { SessionMeta } from '@/types/protocol';
import {
  History,
  Search,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  MessageSquare,
  Trash2,
  RotateCcw,
  ExternalLink,
  MoreHorizontal,
  Inbox,
  Download,
  Filter,
  X,
} from 'lucide-react';

type SortField = 'key' | 'messageCount' | 'totalTokens' | 'createdAt' | 'lastMessageAt';
type SortDir = 'asc' | 'desc';

export default function SessionsPage() {
  const { sessions, deleteSession, resetSession } = useDashboardStore();
  const { setActiveSession, loadHistory } = useChatStore();
  const { client } = useConnectionStore();
  const navigate = useNavigate();

  const [search, setSearch] = useState('');
  const [sortField, setSortField] = useState<SortField>('lastMessageAt');
  const [sortDir, setSortDir] = useState<SortDir>('desc');
  const [filterAgent, setFilterAgent] = useState<string | null>(null);
  const [filterChannel, setFilterChannel] = useState<string | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());

  // Extract unique agents and channels
  const agents = useMemo(() => [...new Set(sessions.map((s) => s.agentId))], [sessions]);
  const channels = useMemo(() => [...new Set(sessions.map((s) => s.channel).filter(Boolean))], [sessions]);

  // Filter and sort
  const filtered = useMemo(() => {
    let result = sessions;

    if (search) {
      const q = search.toLowerCase();
      result = result.filter((s) => s.key.toLowerCase().includes(q) || s.agentId.toLowerCase().includes(q));
    }
    if (filterAgent) {
      result = result.filter((s) => s.agentId === filterAgent);
    }
    if (filterChannel) {
      result = result.filter((s) => s.channel === filterChannel);
    }

    result = [...result].sort((a, b) => {
      let cmp = 0;
      switch (sortField) {
        case 'key': cmp = a.key.localeCompare(b.key); break;
        case 'messageCount': cmp = a.messageCount - b.messageCount; break;
        case 'totalTokens': cmp = a.totalTokens - b.totalTokens; break;
        case 'createdAt': cmp = a.createdAt.localeCompare(b.createdAt); break;
        case 'lastMessageAt': cmp = (a.lastMessageAt || '').localeCompare(b.lastMessageAt || ''); break;
      }
      return sortDir === 'asc' ? cmp : -cmp;
    });

    return result;
  }, [sessions, search, sortField, sortDir, filterAgent, filterChannel]);

  const toggleSort = useCallback((field: SortField) => {
    if (sortField === field) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortField(field);
      setSortDir('desc');
    }
  }, [sortField]);

  const openInChat = useCallback((key: string) => {
    setActiveSession(key);
    if (client) loadHistory(client);
    navigate('/chat');
  }, [setActiveSession, loadHistory, client, navigate]);

  const toggleSelect = useCallback((key: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  }, []);

  const handleBulkDelete = useCallback(async () => {
    if (!client) return;
    for (const key of selected) {
      await deleteSession(client, key);
    }
    setSelected(new Set());
  }, [client, selected, deleteSession]);

  const SortIcon = ({ field }: { field: SortField }) => {
    if (sortField !== field) return <ArrowUpDown className="h-3 w-3 opacity-30" />;
    return sortDir === 'asc' ? <ArrowUp className="h-3 w-3" /> : <ArrowDown className="h-3 w-3" />;
  };

  const hasFilters = !!filterAgent || !!filterChannel;

  return (
    <div className="flex-1 flex flex-col min-h-0 p-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <History className="h-5 w-5" />
            Sessions
          </h2>
          <p className="text-sm text-muted-foreground">{filtered.length} of {sessions.length} sessions</p>
        </div>

        {selected.size > 0 && (
          <div className="flex items-center gap-2">
            <span className="text-sm text-muted-foreground">{selected.size} selected</span>
            <Button variant="destructive" size="sm" onClick={handleBulkDelete}>
              <Trash2 className="h-3.5 w-3.5 mr-1.5" />
              Delete
            </Button>
            <Button variant="ghost" size="sm" onClick={() => setSelected(new Set())}>
              Clear
            </Button>
          </div>
        )}
      </div>

      {/* Search + filters */}
      <div className="flex items-center gap-2 mb-4">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search sessions..."
            className="pl-9 h-8 text-sm"
          />
        </div>

        {agents.length > 1 && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant={filterAgent ? 'secondary' : 'outline'} size="sm" className="h-8 text-xs gap-1.5">
                <Filter className="h-3 w-3" />
                Agent{filterAgent && `: ${filterAgent}`}
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              <DropdownMenuItem onClick={() => setFilterAgent(null)}>All agents</DropdownMenuItem>
              {agents.map((a) => (
                <DropdownMenuItem key={a} onClick={() => setFilterAgent(a)}>{a}</DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        )}

        {channels.length > 0 && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant={filterChannel ? 'secondary' : 'outline'} size="sm" className="h-8 text-xs gap-1.5">
                <Filter className="h-3 w-3" />
                Channel{filterChannel && `: ${filterChannel}`}
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              <DropdownMenuItem onClick={() => setFilterChannel(null)}>All channels</DropdownMenuItem>
              {channels.map((c) => (
                <DropdownMenuItem key={c} onClick={() => setFilterChannel(c!)}>{c}</DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        )}

        {hasFilters && (
          <Button variant="ghost" size="sm" className="h-8 text-xs" onClick={() => { setFilterAgent(null); setFilterChannel(null); }}>
            <X className="h-3 w-3 mr-1" /> Clear filters
          </Button>
        )}
      </div>

      {/* Table */}
      {filtered.length === 0 ? (
        <div className="flex-1 flex items-center justify-center">
          <div className="text-center text-muted-foreground">
            <Inbox className="h-12 w-12 mx-auto mb-4 opacity-30" />
            <p className="text-sm">{search || hasFilters ? 'No sessions match your filters' : 'No sessions yet'}</p>
          </div>
        </div>
      ) : (
        <div className="flex-1 overflow-auto rounded-lg border border-border">
          <table className="w-full text-sm">
            <thead className="bg-muted/50 sticky top-0">
              <tr>
                <th className="w-8 px-3 py-2">
                  <input
                    type="checkbox"
                    checked={selected.size === filtered.length && filtered.length > 0}
                    onChange={(e) => {
                      if (e.target.checked) setSelected(new Set(filtered.map((s) => s.key)));
                      else setSelected(new Set());
                    }}
                    className="rounded"
                  />
                </th>
                <SortableHeader label="Session" field="key" current={sortField} dir={sortDir} onSort={toggleSort} />
                <th className="px-3 py-2 text-left text-xs font-medium text-muted-foreground">Agent</th>
                <th className="px-3 py-2 text-left text-xs font-medium text-muted-foreground">Channel</th>
                <SortableHeader label="Messages" field="messageCount" current={sortField} dir={sortDir} onSort={toggleSort} />
                <SortableHeader label="Tokens" field="totalTokens" current={sortField} dir={sortDir} onSort={toggleSort} />
                <SortableHeader label="Last Activity" field="lastMessageAt" current={sortField} dir={sortDir} onSort={toggleSort} />
                <th className="w-10" />
              </tr>
            </thead>
            <tbody>
              {filtered.map((session) => (
                <SessionRow
                  key={session.key}
                  session={session}
                  isSelected={selected.has(session.key)}
                  onToggleSelect={() => toggleSelect(session.key)}
                  onOpenChat={() => openInChat(session.key)}
                  onReset={() => client && resetSession(client, session.key)}
                  onDelete={() => client && deleteSession(client, session.key)}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function SortableHeader({ label, field, current, dir, onSort }: {
  label: string;
  field: SortField;
  current: SortField;
  dir: SortDir;
  onSort: (f: SortField) => void;
}) {
  const isActive = current === field;
  return (
    <th
      className="px-3 py-2 text-left text-xs font-medium text-muted-foreground cursor-pointer hover:text-foreground select-none"
      onClick={() => onSort(field)}
    >
      <span className="flex items-center gap-1">
        {label}
        {isActive
          ? (dir === 'asc' ? <ArrowUp className="h-3 w-3" /> : <ArrowDown className="h-3 w-3" />)
          : <ArrowUpDown className="h-3 w-3 opacity-30" />
        }
      </span>
    </th>
  );
}

function SessionRow({ session, isSelected, onToggleSelect, onOpenChat, onReset, onDelete }: {
  session: SessionMeta;
  isSelected: boolean;
  onToggleSelect: () => void;
  onOpenChat: () => void;
  onReset: () => void;
  onDelete: () => void;
}) {
  return (
    <tr className="border-t border-border hover:bg-muted/30 transition-colors">
      <td className="px-3 py-2.5">
        <input type="checkbox" checked={isSelected} onChange={onToggleSelect} className="rounded" />
      </td>
      <td className="px-3 py-2.5">
        <button onClick={onOpenChat} className="font-mono text-xs hover:underline text-left">
          {formatSessionKey(session.key)}
        </button>
      </td>
      <td className="px-3 py-2.5">
        <Badge variant="outline" className="text-[11px] font-normal">{session.agentId}</Badge>
      </td>
      <td className="px-3 py-2.5">
        {session.channel ? (
          <Badge variant="outline" className="text-[11px] font-normal">{session.channel}</Badge>
        ) : (
          <span className="text-xs text-muted-foreground">—</span>
        )}
      </td>
      <td className="px-3 py-2.5 text-xs tabular-nums">{session.messageCount}</td>
      <td className="px-3 py-2.5 text-xs tabular-nums">{formatTokens(session.totalTokens)}</td>
      <td className="px-3 py-2.5 text-xs text-muted-foreground">
        {session.lastMessageAt ? formatTimestamp(session.lastMessageAt) : '—'}
      </td>
      <td className="px-3 py-2.5">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon" className="h-7 w-7">
              <MoreHorizontal className="h-3.5 w-3.5" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={onOpenChat}>
              <ExternalLink className="h-3.5 w-3.5 mr-2" /> Open in Chat
            </DropdownMenuItem>
            <DropdownMenuItem onClick={onReset}>
              <RotateCcw className="h-3.5 w-3.5 mr-2" /> Reset
            </DropdownMenuItem>
            <DropdownMenuItem onClick={onDelete} className="text-destructive">
              <Trash2 className="h-3.5 w-3.5 mr-2" /> Delete
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </td>
    </tr>
  );
}
