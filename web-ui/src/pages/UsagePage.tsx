import { useState, useEffect, useCallback, useMemo } from 'react';
import { useConnectionStore } from '@/stores/connection-store';
import { useDashboardStore } from '@/stores/dashboard-store';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  BarChart3,
  RefreshCw,
  Loader2,
  Zap,
  DollarSign,
  MessageSquare,
  Clock,
  TrendingUp,
  ChevronDown,
} from 'lucide-react';
import {
  AreaChart,
  Area,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from 'recharts';

type TimeRange = '24h' | '7d' | '30d';

interface UsageData {
  summary: {
    totalTokens: number;
    estimatedCost: number;
    totalMessages: number;
    avgResponseTime: number;
  };
  tokensByTime: { time: string; input: number; output: number }[];
  tokensByModel: { name: string; tokens: number }[];
  tokensByAgent: { agent: string; tokens: number }[];
  topSessions: { key: string; tokens: number; messages: number }[];
}

const PIE_COLORS = [
  'hsl(221, 83%, 53%)',  // blue
  'hsl(262, 83%, 58%)',  // purple
  'hsl(142, 71%, 45%)',  // green
  'hsl(38, 92%, 50%)',   // amber
  'hsl(0, 84%, 60%)',    // red
  'hsl(199, 89%, 48%)',  // cyan
];

function generateDemoData(range: TimeRange): UsageData {
  const now = Date.now();
  const points = range === '24h' ? 24 : range === '7d' ? 7 : 30;
  const intervalMs = range === '24h' ? 3600000 : 86400000;

  const tokensByTime = Array.from({ length: points }, (_, i) => {
    const time = new Date(now - (points - 1 - i) * intervalMs);
    const label = range === '24h'
      ? time.toLocaleTimeString('en-US', { hour: '2-digit', hour12: false })
      : time.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    const base = 800 + Math.random() * 2000;
    return {
      time: label,
      input: Math.round(base * (0.6 + Math.random() * 0.4)),
      output: Math.round(base * (0.3 + Math.random() * 0.3)),
    };
  });

  const totalInput = tokensByTime.reduce((s, d) => s + d.input, 0);
  const totalOutput = tokensByTime.reduce((s, d) => s + d.output, 0);
  const totalTokens = totalInput + totalOutput;

  return {
    summary: {
      totalTokens,
      estimatedCost: totalTokens * 0.000003 + totalOutput * 0.000012,
      totalMessages: Math.round(totalTokens / 450),
      avgResponseTime: 1800 + Math.random() * 2200,
    },
    tokensByTime,
    tokensByModel: [
      { name: 'Claude Sonnet 4.5', tokens: Math.round(totalTokens * 0.55) },
      { name: 'Claude Opus 4.6', tokens: Math.round(totalTokens * 0.2) },
      { name: 'GPT-4.1', tokens: Math.round(totalTokens * 0.15) },
      { name: 'GPT-4o', tokens: Math.round(totalTokens * 0.1) },
    ],
    tokensByAgent: [
      { agent: 'main', tokens: Math.round(totalTokens * 0.7) },
      { agent: 'code-review', tokens: Math.round(totalTokens * 0.2) },
      { agent: 'research', tokens: Math.round(totalTokens * 0.1) },
    ],
    topSessions: [
      { key: 'main:main:*:main', tokens: Math.round(totalTokens * 0.3), messages: 47 },
      { key: 'main:main:*:refactor-auth', tokens: Math.round(totalTokens * 0.15), messages: 23 },
      { key: 'code-review:main:*:pr-42', tokens: Math.round(totalTokens * 0.1), messages: 12 },
      { key: 'main:main:*:debug-ws', tokens: Math.round(totalTokens * 0.08), messages: 18 },
      { key: 'research:main:*:koog-docs', tokens: Math.round(totalTokens * 0.06), messages: 9 },
    ],
  };
}

function formatTokens(n: number): string {
  if (n < 1000) return String(n);
  if (n < 1_000_000) return `${(n / 1000).toFixed(1)}k`;
  return `${(n / 1_000_000).toFixed(2)}M`;
}

function formatCost(n: number): string {
  if (n < 0.01) return `$${n.toFixed(4)}`;
  if (n < 1) return `$${n.toFixed(3)}`;
  return `$${n.toFixed(2)}`;
}

function formatMs(ms: number): string {
  if (ms < 1000) return `${Math.round(ms)}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

export default function UsagePage() {
  const { client } = useConnectionStore();
  const [data, setData] = useState<UsageData | null>(null);
  const [loading, setLoading] = useState(true);
  const [range, setRange] = useState<TimeRange>('7d');

  const fetchUsage = useCallback(async () => {
    if (!client) {
      setData(generateDemoData(range));
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const res = await client.request('usage.summary', { range });
      setData(res as unknown as UsageData);
    } catch {
      setData(generateDemoData(range));
    } finally {
      setLoading(false);
    }
  }, [client, range]);

  useEffect(() => { fetchUsage(); }, [fetchUsage]);

  if (loading || !data) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-auto p-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <BarChart3 className="h-5 w-5" /> Usage Analytics
          </h2>
          <p className="text-sm text-muted-foreground">Token consumption and cost tracking</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex border border-border rounded-md overflow-hidden">
            {(['24h', '7d', '30d'] as TimeRange[]).map((r) => (
              <button
                key={r}
                onClick={() => setRange(r)}
                className={`px-3 py-1 text-xs font-medium transition-colors ${
                  range === r ? 'bg-primary text-primary-foreground' : 'hover:bg-muted'
                }`}
              >
                {r}
              </button>
            ))}
          </div>
          <Button variant="outline" size="sm" onClick={fetchUsage} className="gap-1.5">
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </Button>
        </div>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <SummaryCard
          icon={<Zap className="h-4 w-4" />}
          label="Total Tokens"
          value={formatTokens(data.summary.totalTokens)}
          iconColor="text-yellow-500"
        />
        <SummaryCard
          icon={<DollarSign className="h-4 w-4" />}
          label="Estimated Cost"
          value={formatCost(data.summary.estimatedCost)}
          iconColor="text-green-500"
        />
        <SummaryCard
          icon={<MessageSquare className="h-4 w-4" />}
          label="Messages"
          value={String(data.summary.totalMessages)}
          iconColor="text-blue-500"
        />
        <SummaryCard
          icon={<Clock className="h-4 w-4" />}
          label="Avg Response"
          value={formatMs(data.summary.avgResponseTime)}
          iconColor="text-purple-500"
        />
      </div>

      {/* Charts row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 mb-6">
        {/* Token usage over time — spans 2 cols */}
        <div className="lg:col-span-2 border border-border rounded-lg p-4">
          <h3 className="text-xs font-medium text-muted-foreground mb-4 flex items-center gap-1.5">
            <TrendingUp className="h-3.5 w-3.5" /> Token Usage Over Time
          </h3>
          <div className="h-[250px]">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={data.tokensByTime} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="inputGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="hsl(221, 83%, 53%)" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="hsl(221, 83%, 53%)" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="outputGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="hsl(262, 83%, 58%)" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="hsl(262, 83%, 58%)" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
                <XAxis dataKey="time" tick={{ fontSize: 10 }} className="fill-muted-foreground" />
                <YAxis tick={{ fontSize: 10 }} className="fill-muted-foreground" tickFormatter={(v) => formatTokens(v)} />
                <Tooltip
                  contentStyle={{ fontSize: 11, borderRadius: 8, border: '1px solid hsl(var(--border))' }}
                  labelStyle={{ fontWeight: 600 }}
                  formatter={(value: number) => [formatTokens(value), '']}
                />
                <Legend wrapperStyle={{ fontSize: 11 }} />
                <Area type="monotone" dataKey="input" name="Input" stroke="hsl(221, 83%, 53%)" fill="url(#inputGrad)" strokeWidth={2} />
                <Area type="monotone" dataKey="output" name="Output" stroke="hsl(262, 83%, 58%)" fill="url(#outputGrad)" strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* By model — pie chart */}
        <div className="border border-border rounded-lg p-4">
          <h3 className="text-xs font-medium text-muted-foreground mb-4">Breakdown by Model</h3>
          <div className="h-[200px]">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={data.tokensByModel}
                  dataKey="tokens"
                  nameKey="name"
                  cx="50%"
                  cy="50%"
                  outerRadius={70}
                  innerRadius={40}
                  paddingAngle={2}
                  strokeWidth={0}
                >
                  {data.tokensByModel.map((_, i) => (
                    <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{ fontSize: 11, borderRadius: 8, border: '1px solid hsl(var(--border))' }}
                  formatter={(value: number) => [formatTokens(value), 'tokens']}
                />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="space-y-1.5 mt-2">
            {data.tokensByModel.map((m, i) => (
              <div key={m.name} className="flex items-center gap-2 text-xs">
                <span className="h-2.5 w-2.5 rounded-full flex-shrink-0" style={{ background: PIE_COLORS[i % PIE_COLORS.length] }} />
                <span className="flex-1 truncate">{m.name}</span>
                <span className="text-muted-foreground tabular-nums">{formatTokens(m.tokens)}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Bottom row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* By agent — bar chart */}
        <div className="border border-border rounded-lg p-4">
          <h3 className="text-xs font-medium text-muted-foreground mb-4">Breakdown by Agent</h3>
          <div className="h-[200px]">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data.tokensByAgent} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
                <XAxis dataKey="agent" tick={{ fontSize: 10 }} className="fill-muted-foreground" />
                <YAxis tick={{ fontSize: 10 }} className="fill-muted-foreground" tickFormatter={(v) => formatTokens(v)} />
                <Tooltip
                  contentStyle={{ fontSize: 11, borderRadius: 8, border: '1px solid hsl(var(--border))' }}
                  formatter={(value: number) => [formatTokens(value), 'tokens']}
                />
                <Bar dataKey="tokens" fill="hsl(221, 83%, 53%)" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Top sessions table */}
        <div className="border border-border rounded-lg p-4">
          <h3 className="text-xs font-medium text-muted-foreground mb-4">Top Sessions by Token Usage</h3>
          <div className="overflow-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b border-border">
                  <th className="text-left py-1.5 font-medium text-muted-foreground">Session</th>
                  <th className="text-right py-1.5 font-medium text-muted-foreground">Messages</th>
                  <th className="text-right py-1.5 font-medium text-muted-foreground">Tokens</th>
                </tr>
              </thead>
              <tbody>
                {data.topSessions.map((s) => (
                  <tr key={s.key} className="border-b border-border/50">
                    <td className="py-1.5 font-mono text-[11px] truncate max-w-[200px]">{s.key}</td>
                    <td className="py-1.5 text-right tabular-nums text-muted-foreground">{s.messages}</td>
                    <td className="py-1.5 text-right tabular-nums">{formatTokens(s.tokens)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}

function SummaryCard({ icon, label, value, iconColor }: {
  icon: React.ReactNode;
  label: string;
  value: string;
  iconColor: string;
}) {
  return (
    <div className="border border-border rounded-lg p-4">
      <div className="flex items-center gap-2 mb-2">
        <div className={`h-8 w-8 rounded-md bg-muted flex items-center justify-center ${iconColor}`}>
          {icon}
        </div>
        <span className="text-xs text-muted-foreground">{label}</span>
      </div>
      <p className="text-2xl font-semibold tabular-nums">{value}</p>
    </div>
  );
}
