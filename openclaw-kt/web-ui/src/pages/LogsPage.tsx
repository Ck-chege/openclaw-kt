import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { useConnectionStore } from '@/stores/connection-store';
import { Button } from '@/components/ui/button';
import { FeatureState } from '@/components/FeatureState';
import { Input } from '@/components/ui/input';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  ScrollText,
  RefreshCw,
  Loader2,
  Search,
  Filter,
  X,
  ArrowDownToLine,
  Pause,
  Play,
  Copy,
  Download,
  Clock,
  Inbox,
} from 'lucide-react';

type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';

interface LogEntry {
  id: string;
  timestamp: string;
  level: LogLevel;
  source: string;
  message: string;
}

const LEVEL_CONFIG: Record<LogLevel, { className: string; dotClassName: string }> = {
  DEBUG: { className: 'text-muted-foreground', dotClassName: 'bg-muted-foreground/50' },
  INFO: { className: 'text-blue-600 dark:text-blue-400', dotClassName: 'bg-blue-500' },
  WARN: { className: 'text-status-warning', dotClassName: 'bg-yellow-500' },
  ERROR: { className: 'text-status-error', dotClassName: 'bg-red-500' },
};

const ALL_LEVELS: LogLevel[] = ['DEBUG', 'INFO', 'WARN', 'ERROR'];

function formatTimestamp(iso: string, relative: boolean): string {
  if (relative) {
    const ms = Date.now() - new Date(iso).getTime();
    const sec = Math.floor(ms / 1000);
    if (sec < 1) return 'now';
    if (sec < 60) return `${sec}s ago`;
    const min = Math.floor(ms / 60000);
    if (min < 60) return `${min}m ago`;
    const hr = Math.floor(ms / 3600000);
    return `${hr}h ago`;
  }
  const d = new Date(iso);
  return d.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' })
    + '.' + String(d.getMilliseconds()).padStart(3, '0');
}

export default function LogsPage() {
  const { client, state } = useConnectionStore();
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [filterLevel, setFilterLevel] = useState<LogLevel | null>(null);
  const [filterSource, setFilterSource] = useState<string | null>(null);
  const [autoScroll, setAutoScroll] = useState(true);
  const [paused, setPaused] = useState(false);
  const [relativeTime, setRelativeTime] = useState(true);
  const scrollRef = useRef<HTMLDivElement>(null);
  const pauseBufferRef = useRef<LogEntry[]>([]);

  const fetchLogs = useCallback(async () => {
    if (!client || state !== 'connected') {
      setLogs([]);
      setLoading(false);
      setError('Connect to the gateway to inspect logs.');
      return;
    }
    setLoading(true);
    try {
      const res = await client.request('logs.recent');
      setLogs((res.entries || []) as LogEntry[]);
      setError(null);
    } catch (e) {
      setLogs([]);
      setError((e as Error).message || 'This gateway does not expose log retrieval yet.');
    } finally {
      setLoading(false);
    }
  }, [client, state]);

  // Subscribe to log stream
  useEffect(() => {
    if (!client || state !== 'connected') return;

    const handler = (payload: Record<string, unknown>) => {
      const entry = (payload.entry ?? payload) as Partial<LogEntry>;
      if (entry.id && entry.timestamp && entry.level && entry.source && entry.message) {
        if (paused) {
          pauseBufferRef.current.push(entry as LogEntry);
        } else {
          setLogs((prev) => [...prev.slice(-999), entry as LogEntry]);
        }
      }
    };

    client.on('log.entry', handler);
    return () => { client.off('log.entry', handler); };
  }, [client, state, paused]);

  // Flush pause buffer when resuming
  useEffect(() => {
    if (!paused && pauseBufferRef.current.length > 0) {
      setLogs((prev) => [...prev, ...pauseBufferRef.current].slice(-1000));
      pauseBufferRef.current = [];
    }
  }, [paused]);

  useEffect(() => { fetchLogs(); }, [fetchLogs]);

  // Auto-scroll
  useEffect(() => {
    if (autoScroll && !paused && scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [logs, autoScroll, paused]);

  const sources = useMemo(() => [...new Set(logs.map((l) => l.source))].sort(), [logs]);

  const filtered = useMemo(() => {
    return logs.filter((l) => {
      if (filterLevel && l.level !== filterLevel) return false;
      if (filterSource && l.source !== filterSource) return false;
      if (search) {
        const q = search.toLowerCase();
        return l.message.toLowerCase().includes(q) || l.source.toLowerCase().includes(q);
      }
      return true;
    });
  }, [logs, filterLevel, filterSource, search]);

  const levelCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    for (const l of logs) {
      counts[l.level] = (counts[l.level] || 0) + 1;
    }
    return counts;
  }, [logs]);

  const handleCopyLine = useCallback((entry: LogEntry) => {
    const text = `[${entry.timestamp}] [${entry.level}] [${entry.source}] ${entry.message}`;
    navigator.clipboard.writeText(text);
  }, []);

  const handleExport = useCallback(() => {
    const text = filtered.map((l) => `[${l.timestamp}] [${l.level}] [${l.source}] ${l.message}`).join('\n');
    const blob = new Blob([text], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `logs-${new Date().toISOString().slice(0, 19).replace(/:/g, '-')}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  }, [filtered]);

  const hasFilters = !!filterLevel || !!filterSource;

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (error && logs.length === 0) {
    return (
      <FeatureState
        icon={<ScrollText className="h-6 w-6" />}
        title="Logs Unavailable"
        description={error}
        action={<Button variant="outline" size="sm" onClick={fetchLogs}>Retry</Button>}
      />
    );
  }

  return (
    <div className="flex-1 flex flex-col min-h-0 p-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <ScrollText className="h-5 w-5" /> Logs
          </h2>
          <p className="text-sm text-muted-foreground">
            {filtered.length} entries
            {hasFilters && ` (of ${logs.length})`}
            {paused && ' — paused'}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {/* Level counts */}
          <div className="flex items-center gap-1.5 mr-2">
            {ALL_LEVELS.map((level) => (
              <button
                key={level}
                onClick={() => setFilterLevel(filterLevel === level ? null : level)}
                className={`flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-medium transition-colors ${
                  filterLevel === level
                    ? `${LEVEL_CONFIG[level].className} bg-muted`
                    : 'text-muted-foreground/60 hover:text-muted-foreground'
                }`}
              >
                <span className={`h-1.5 w-1.5 rounded-full ${LEVEL_CONFIG[level].dotClassName}`} />
                {levelCounts[level] || 0}
              </button>
            ))}
          </div>

          <Button
            variant={paused ? 'secondary' : 'outline'}
            size="sm"
            onClick={() => setPaused(!paused)}
            className="gap-1.5"
          >
            {paused ? <Play className="h-3.5 w-3.5" /> : <Pause className="h-3.5 w-3.5" />}
            {paused ? 'Resume' : 'Pause'}
          </Button>

          <Button
            variant={autoScroll ? 'secondary' : 'outline'}
            size="sm"
            onClick={() => setAutoScroll(!autoScroll)}
            className="gap-1.5"
            title="Auto-scroll to newest"
          >
            <ArrowDownToLine className="h-3.5 w-3.5" />
          </Button>

          <Button variant="outline" size="sm" onClick={() => setRelativeTime(!relativeTime)} className="gap-1.5" title="Toggle timestamp format">
            <Clock className="h-3.5 w-3.5" />
          </Button>

          <Button variant="outline" size="sm" onClick={handleExport} className="gap-1.5" title="Export logs">
            <Download className="h-3.5 w-3.5" />
          </Button>

          <Button variant="outline" size="sm" onClick={fetchLogs} className="gap-1.5">
            <RefreshCw className="h-3.5 w-3.5" />
          </Button>
        </div>
      </div>

      {/* Search + filters */}
      <div className="flex items-center gap-2 mb-3">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
          <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search logs..." className="pl-9 h-8 text-sm" />
        </div>

        {sources.length > 1 && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant={filterSource ? 'secondary' : 'outline'} size="sm" className="h-8 text-xs gap-1.5">
                <Filter className="h-3 w-3" />
                Source{filterSource && `: ${filterSource}`}
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              <DropdownMenuItem onClick={() => setFilterSource(null)}>All sources</DropdownMenuItem>
              {sources.map((s) => (
                <DropdownMenuItem key={s} onClick={() => setFilterSource(s)}>{s}</DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        )}

        {hasFilters && (
          <Button variant="ghost" size="sm" className="h-8 text-xs" onClick={() => { setFilterLevel(null); setFilterSource(null); }}>
            <X className="h-3 w-3 mr-1" /> Clear filters
          </Button>
        )}
      </div>

      {/* Log entries */}
      {filtered.length === 0 ? (
        <div className="flex-1 flex items-center justify-center">
          <div className="text-center text-muted-foreground">
            <Inbox className="h-12 w-12 mx-auto mb-4 opacity-30" />
            <p className="text-sm">{logs.length === 0 ? 'No log entries' : 'No logs match your filters'}</p>
          </div>
        </div>
      ) : (
        <div
          ref={scrollRef}
          className="flex-1 overflow-auto rounded-lg border border-border bg-[hsl(var(--card))] font-mono text-xs"
        >
          <div className="min-w-[700px]">
            {/* Header row */}
            <div className="flex items-center gap-2 px-3 py-1.5 border-b border-border bg-muted/30 text-[10px] font-medium text-muted-foreground uppercase tracking-wider sticky top-0">
              <span className="w-[90px]">Time</span>
              <span className="w-[52px]">Level</span>
              <span className="w-[120px]">Source</span>
              <span className="flex-1">Message</span>
            </div>

            {filtered.map((entry) => {
              const levelCfg = LEVEL_CONFIG[entry.level];
              return (
                <div
                  key={entry.id}
                  className={`flex items-start gap-2 px-3 py-1 border-b border-border/50 hover:bg-muted/30 transition-colors group ${
                    entry.level === 'ERROR' ? 'bg-red-500/5' : entry.level === 'WARN' ? 'bg-yellow-500/5' : ''
                  }`}
                >
                  {/* Timestamp */}
                  <span className="w-[90px] flex-shrink-0 text-muted-foreground tabular-nums">
                    {formatTimestamp(entry.timestamp, relativeTime)}
                  </span>

                  {/* Level */}
                  <span className={`w-[52px] flex-shrink-0 font-medium ${levelCfg.className}`}>
                    {entry.level}
                  </span>

                  {/* Source */}
                  <span className="w-[120px] flex-shrink-0 text-muted-foreground truncate">
                    {entry.source}
                  </span>

                  {/* Message */}
                  <span className="flex-1 whitespace-pre-wrap break-all">
                    {entry.message}
                  </span>

                  {/* Copy button (on hover) */}
                  <button
                    onClick={() => handleCopyLine(entry)}
                    className="flex-shrink-0 opacity-0 group-hover:opacity-100 transition-opacity p-0.5 hover:bg-muted rounded"
                    title="Copy log line"
                  >
                    <Copy className="h-3 w-3 text-muted-foreground" />
                  </button>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
