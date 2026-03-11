import { useState, useEffect, useCallback } from 'react';
import { useConnectionStore } from '@/stores/connection-store';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { FeatureState } from '@/components/FeatureState';
import { Input } from '@/components/ui/input';
import {
  Wrench,
  Terminal,
  FileText,
  Globe,
  Search,
  Cpu,
  Shield,
  Loader2,
  RefreshCw,
} from 'lucide-react';

interface ToolInfo {
  name: string;
  description?: string;
  group?: string;
  permission?: string;
  params?: Record<string, unknown>;
}

const GROUP_ICONS: Record<string, React.ReactNode> = {
  exec: <Terminal className="h-4 w-4" />,
  filesystem: <FileText className="h-4 w-4" />,
  web: <Globe className="h-4 w-4" />,
  runtime: <Cpu className="h-4 w-4" />,
  sessions: <Search className="h-4 w-4" />,
  native: <Wrench className="h-4 w-4" />,
};

const GROUP_COLORS: Record<string, string> = {
  exec: 'bg-orange-500/10 text-orange-600 dark:text-orange-400',
  filesystem: 'bg-blue-500/10 text-blue-600 dark:text-blue-400',
  web: 'bg-green-500/10 text-green-600 dark:text-green-400',
  runtime: 'bg-purple-500/10 text-purple-600 dark:text-purple-400',
  sessions: 'bg-cyan-500/10 text-cyan-600 dark:text-cyan-400',
  native: 'bg-gray-500/10 text-gray-600 dark:text-gray-400',
};

export default function ToolsPage() {
  const { client, state } = useConnectionStore();
  const [tools, setTools] = useState<ToolInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [filterGroup, setFilterGroup] = useState<string | null>(null);

  const fetchTools = useCallback(async () => {
    if (!client || state !== 'connected') {
      setTools([]);
      setLoading(false);
      setError('Connect to the gateway to inspect the tool catalog.');
      return;
    }
    setLoading(true);
    try {
      const res = await client.request('tools.catalog');
      setTools((res.tools || []) as ToolInfo[]);
      setError(null);
    } catch (e) {
      setTools([]);
      setError((e as Error).message || 'This gateway does not expose a tool catalog yet.');
    } finally {
      setLoading(false);
    }
  }, [client, state]);

  useEffect(() => { fetchTools(); }, [fetchTools]);

  const groups = [...new Set(tools.map((t) => t.group || 'other'))];

  const filtered = tools.filter((t) => {
    if (filterGroup && t.group !== filterGroup) return false;
    if (search) {
      const q = search.toLowerCase();
      return t.name.toLowerCase().includes(q) || (t.description || '').toLowerCase().includes(q);
    }
    return true;
  });

  // Group filtered tools
  const grouped = filtered.reduce<Record<string, ToolInfo[]>>((acc, t) => {
    const g = t.group || 'other';
    if (!acc[g]) acc[g] = [];
    acc[g].push(t);
    return acc;
  }, {});

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (error && tools.length === 0) {
    return (
      <FeatureState
        icon={<Wrench className="h-6 w-6" />}
        title="Tool Catalog Unavailable"
        description={error}
        action={<Button variant="outline" size="sm" onClick={fetchTools}>Retry</Button>}
      />
    );
  }

  return (
    <div className="flex-1 p-6 overflow-auto">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <Wrench className="h-5 w-5" /> Tools
          </h2>
          <p className="text-sm text-muted-foreground">{tools.length} tools available</p>
        </div>
        <Button variant="outline" size="sm" onClick={fetchTools} className="gap-1.5">
          <RefreshCw className="h-3.5 w-3.5" /> Refresh
        </Button>
      </div>

      {/* Search + filter */}
      <div className="flex items-center gap-2 mb-6">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
          <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search tools..." className="pl-9 h-8 text-sm" />
        </div>
        <div className="flex gap-1">
          <Button
            variant={filterGroup === null ? 'secondary' : 'outline'}
            size="sm" className="h-8 text-xs"
            onClick={() => setFilterGroup(null)}
          >
            All
          </Button>
          {groups.map((g) => (
            <Button
              key={g}
              variant={filterGroup === g ? 'secondary' : 'outline'}
              size="sm" className="h-8 text-xs gap-1"
              onClick={() => setFilterGroup(filterGroup === g ? null : g)}
            >
              {GROUP_ICONS[g] || <Wrench className="h-3 w-3" />}
              {g}
            </Button>
          ))}
        </div>
      </div>

      {/* Tool cards by group */}
      <div className="space-y-6">
        {Object.entries(grouped).map(([group, groupTools]) => (
          <div key={group}>
            <h3 className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-3 flex items-center gap-1.5">
              {GROUP_ICONS[group] || <Wrench className="h-3.5 w-3.5" />}
              {group} ({groupTools.length})
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
              {groupTools.map((tool) => (
                <div key={tool.name} className="border border-border rounded-lg p-3.5 hover:bg-muted/30 transition-colors">
                  <div className="flex items-start justify-between mb-1.5">
                    <div className="flex items-center gap-2">
                      <div className={`h-7 w-7 rounded-md flex items-center justify-center ${GROUP_COLORS[group] || 'bg-muted text-muted-foreground'}`}>
                        {GROUP_ICONS[group] || <Wrench className="h-3.5 w-3.5" />}
                      </div>
                      <span className="font-mono text-sm font-medium">{tool.name}</span>
                    </div>
                    {tool.permission === 'elevated' && (
                      <Badge variant="outline" className="text-[10px] gap-1 text-status-warning border-status-warning-border">
                        <Shield className="h-2.5 w-2.5" /> elevated
                      </Badge>
                    )}
                  </div>
                  {tool.description && (
                    <p className="text-xs text-muted-foreground leading-relaxed">{tool.description}</p>
                  )}
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
