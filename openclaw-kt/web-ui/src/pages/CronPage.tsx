import { useState, useEffect, useCallback, useMemo } from 'react';
import { useConnectionStore } from '@/stores/connection-store';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { FeatureState } from '@/components/FeatureState';
import { ScrollArea } from '@/components/ui/scroll-area';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import {
  Clock,
  Plus,
  RefreshCw,
  Loader2,
  Search,
  Play,
  Pause,
  MoreHorizontal,
  Trash2,
  Pencil,
  ChevronRight,
  Check,
  X,
  CalendarClock,
  Timer,
  Bot,
  Inbox,
} from 'lucide-react';

interface CronJob {
  id: string;
  name: string;
  schedule: string;
  message: string;
  agentId?: string;
  enabled: boolean;
  lastRun?: string;
  nextRun?: string;
  lastStatus?: 'success' | 'error' | 'running';
  lastDuration?: number;
  runCount?: number;
  runs?: CronRun[];
}

interface CronRun {
  id: string;
  timestamp: string;
  duration: number;
  status: 'success' | 'error' | 'running';
  output?: string;
  error?: string;
}

const SCHEDULE_PRESETS: { label: string; value: string }[] = [
  { label: 'Every minute', value: '* * * * *' },
  { label: 'Every 5 minutes', value: '*/5 * * * *' },
  { label: 'Every 15 minutes', value: '*/15 * * * *' },
  { label: 'Every hour', value: '0 * * * *' },
  { label: 'Every 6 hours', value: '0 */6 * * *' },
  { label: 'Every day at midnight', value: '0 0 * * *' },
  { label: 'Every day at 9am', value: '0 9 * * *' },
  { label: 'Every Monday at 9am', value: '0 9 * * 1' },
  { label: 'Every 1st of month', value: '0 0 1 * *' },
];

function humanizeSchedule(cron: string): string {
  const parts = cron.split(' ');
  if (parts.length !== 5) return cron;
  const [min, hour, dom, , dow] = parts;

  if (cron === '* * * * *') return 'Every minute';
  if (min.startsWith('*/') && hour === '*') return `Every ${min.slice(2)} minutes`;
  if (min === '0' && hour.startsWith('*/')) return `Every ${hour.slice(2)} hours`;
  if (min === '0' && hour === '*') return 'Every hour';
  if (min === '0' && hour !== '*' && dom === '*' && dow === '*') return `Daily at ${hour.padStart(2, '0')}:00`;
  if (min === '0' && hour !== '*' && dom === '*' && dow !== '*') {
    const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    return `${days[Number(dow)] || dow} at ${hour.padStart(2, '0')}:00`;
  }
  if (min === '0' && hour === '0' && dom === '1') return 'Monthly (1st at midnight)';
  return cron;
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  return `${Math.floor(ms / 60000)}m ${Math.round((ms % 60000) / 1000)}s`;
}

function formatRelative(iso: string): string {
  const ms = Date.now() - new Date(iso).getTime();
  if (ms < 0) {
    const absMs = Math.abs(ms);
    const min = Math.floor(absMs / 60000);
    if (min < 1) return 'in <1m';
    if (min < 60) return `in ${min}m`;
    const hr = Math.floor(absMs / 3600000);
    if (hr < 24) return `in ${hr}h`;
    return `in ${Math.floor(absMs / 86400000)}d`;
  }
  const min = Math.floor(ms / 60000);
  if (min < 1) return 'just now';
  if (min < 60) return `${min}m ago`;
  const hr = Math.floor(ms / 3600000);
  if (hr < 24) return `${hr}h ago`;
  return `${Math.floor(ms / 86400000)}d ago`;
}

export default function CronPage() {
  const { client, state } = useConnectionStore();
  const [jobs, setJobs] = useState<CronJob[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [showDialog, setShowDialog] = useState(false);
  const [editingJob, setEditingJob] = useState<CronJob | null>(null);

  const fetchJobs = useCallback(async () => {
    if (!client || state !== 'connected') {
      setJobs([]);
      setLoading(false);
      setError('Connect to the gateway to manage scheduled jobs.');
      return;
    }
    setLoading(true);
    try {
      const res = await client.request('cron.list');
      setJobs((res.jobs || []) as CronJob[]);
      setError(null);
    } catch (e) {
      setJobs([]);
      setError((e as Error).message || 'This gateway does not expose cron management yet.');
    } finally {
      setLoading(false);
    }
  }, [client, state]);

  useEffect(() => { fetchJobs(); }, [fetchJobs]);

  const filtered = useMemo(() => {
    if (!search) return jobs;
    const q = search.toLowerCase();
    return jobs.filter((j) => j.name.toLowerCase().includes(q) || j.message.toLowerCase().includes(q) || j.schedule.includes(q));
  }, [jobs, search]);

  const handleToggle = useCallback(async (job: CronJob) => {
    if (!client) return;
    try {
      await client.request('cron.update', {
        id: job.id,
        name: job.name,
        schedule: job.schedule,
        message: job.message,
        agentId: job.agentId,
        enabled: !job.enabled,
      });
    } catch { /* ignore */ }
    setJobs((prev) => prev.map((j) => j.id === job.id ? { ...j, enabled: !j.enabled } : j));
  }, [client]);

  const handleRunNow = useCallback(async (jobId: string) => {
    if (!client) return;
    try {
      await client.request('cron.run', { id: jobId });
    } catch { /* ignore */ }
    fetchJobs();
  }, [client, fetchJobs]);

  const handleDelete = useCallback(async (jobId: string) => {
    if (!client) return;
    try {
      await client.request('cron.remove', { id: jobId });
    } catch { /* ignore */ }
    setJobs((prev) => prev.filter((j) => j.id !== jobId));
  }, [client]);

  const handleSave = useCallback(async (data: { name: string; schedule: string; message: string; agentId: string }) => {
    if (!client) return;
    try {
      if (editingJob) {
        await client.request('cron.update', { id: editingJob.id, ...data });
      } else {
        await client.request('cron.add', data);
      }
    } catch { /* ignore */ }
    setShowDialog(false);
    setEditingJob(null);
    fetchJobs();
  }, [client, editingJob, fetchJobs]);

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (error && jobs.length === 0) {
    return (
      <FeatureState
        icon={<Clock className="h-6 w-6" />}
        title="Scheduled Jobs Unavailable"
        description={error}
        action={<Button variant="outline" size="sm" onClick={fetchJobs}>Retry</Button>}
      />
    );
  }

  return (
    <div className="flex-1 flex flex-col min-h-0 p-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <Clock className="h-5 w-5" /> Scheduled Jobs
          </h2>
          <p className="text-sm text-muted-foreground">
            {jobs.filter((j) => j.enabled).length} of {jobs.length} enabled
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={fetchJobs} className="gap-1.5">
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </Button>
          <Button size="sm" onClick={() => { setEditingJob(null); setShowDialog(true); }} className="gap-1.5">
            <Plus className="h-3.5 w-3.5" /> New Job
          </Button>
        </div>
      </div>

      {/* Search */}
      {jobs.length > 0 && (
        <div className="flex items-center gap-2 mb-4">
          <div className="relative flex-1 max-w-sm">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
            <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search jobs..." className="pl-9 h-8 text-sm" />
          </div>
        </div>
      )}

      {/* Job list */}
      {filtered.length === 0 ? (
        <div className="flex-1 flex items-center justify-center">
          <div className="text-center text-muted-foreground">
            <Inbox className="h-12 w-12 mx-auto mb-4 opacity-30" />
            <p className="text-sm">{jobs.length === 0 ? 'No scheduled jobs yet' : 'No jobs match your search'}</p>
            {jobs.length === 0 && (
              <Button variant="outline" size="sm" className="mt-3" onClick={() => { setEditingJob(null); setShowDialog(true); }}>
                <Plus className="h-3.5 w-3.5 mr-1.5" /> Create your first job
              </Button>
            )}
          </div>
        </div>
      ) : (
        <ScrollArea className="flex-1">
          <div className="space-y-2">
            {filtered.map((job) => (
              <CronJobRow
                key={job.id}
                job={job}
                onToggle={() => handleToggle(job)}
                onRunNow={() => handleRunNow(job.id)}
                onEdit={() => { setEditingJob(job); setShowDialog(true); }}
                onDelete={() => handleDelete(job.id)}
              />
            ))}
          </div>
        </ScrollArea>
      )}

      {/* Create/Edit Dialog */}
      <CronJobDialog
        key={editingJob?.id || 'new-job'}
        open={showDialog}
        editingJob={editingJob}
        onClose={() => { setShowDialog(false); setEditingJob(null); }}
        onSave={handleSave}
      />
    </div>
  );
}

function CronJobRow({ job, onToggle, onRunNow, onEdit, onDelete }: {
  job: CronJob;
  onToggle: () => void;
  onRunNow: () => void;
  onEdit: () => void;
  onDelete: () => void;
}) {
  return (
    <Collapsible>
      <div className={`border border-border rounded-lg transition-colors ${job.enabled ? 'hover:bg-muted/30' : 'opacity-60'}`}>
        <div className="flex items-center gap-3 p-3.5">
          {/* Status indicator */}
          <button
            onClick={onToggle}
            className={`h-8 w-8 rounded-md flex items-center justify-center flex-shrink-0 transition-colors ${
              job.enabled
                ? 'bg-primary/10 text-primary hover:bg-primary/20'
                : 'bg-muted text-muted-foreground hover:bg-muted/80'
            }`}
            title={job.enabled ? 'Disable' : 'Enable'}
          >
            {job.enabled ? <Play className="h-4 w-4" /> : <Pause className="h-4 w-4" />}
          </button>

          {/* Info */}
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-0.5">
              <span className="font-medium text-sm">{job.name}</span>
              <Badge variant="outline" className="text-[10px] font-mono">{job.schedule}</Badge>
              <span className="text-[11px] text-muted-foreground">{humanizeSchedule(job.schedule)}</span>
              {!job.enabled && (
                <Badge variant="outline" className="text-[10px] text-muted-foreground">Paused</Badge>
              )}
            </div>
            <p className="text-xs text-muted-foreground truncate">{job.message}</p>
          </div>

          {/* Stats */}
          <div className="flex items-center gap-4 flex-shrink-0 text-xs text-muted-foreground">
            {job.agentId && (
              <span className="flex items-center gap-1" title="Target agent">
                <Bot className="h-3 w-3" /> {job.agentId}
              </span>
            )}
            {job.nextRun && job.enabled && (
              <span className="flex items-center gap-1" title="Next run">
                <CalendarClock className="h-3 w-3" /> {formatRelative(job.nextRun)}
              </span>
            )}
            {job.lastRun && (
              <span className="flex items-center gap-1" title="Last run">
                <Timer className="h-3 w-3" /> {formatRelative(job.lastRun)}
              </span>
            )}
            {job.lastStatus && (
              <StatusBadge status={job.lastStatus} />
            )}
          </div>

          {/* Expand toggle for run history */}
          {job.runs && job.runs.length > 0 && (
            <CollapsibleTrigger asChild>
              <Button variant="ghost" size="icon" className="h-7 w-7 flex-shrink-0">
                <ChevronRight className="h-3.5 w-3.5 transition-transform [[data-state=open]_&]:rotate-90" />
              </Button>
            </CollapsibleTrigger>
          )}

          {/* Actions */}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon" className="h-7 w-7 flex-shrink-0">
                <MoreHorizontal className="h-3.5 w-3.5" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={onRunNow}>
                <Play className="h-3.5 w-3.5 mr-2" /> Run Now
              </DropdownMenuItem>
              <DropdownMenuItem onClick={onEdit}>
                <Pencil className="h-3.5 w-3.5 mr-2" /> Edit
              </DropdownMenuItem>
              <DropdownMenuItem onClick={onToggle}>
                {job.enabled
                  ? <><Pause className="h-3.5 w-3.5 mr-2" /> Disable</>
                  : <><Play className="h-3.5 w-3.5 mr-2" /> Enable</>
                }
              </DropdownMenuItem>
              <DropdownMenuItem onClick={onDelete} className="text-destructive">
                <Trash2 className="h-3.5 w-3.5 mr-2" /> Delete
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>

        {/* Run history */}
        <CollapsibleContent>
          {job.runs && job.runs.length > 0 && (
            <div className="border-t border-border px-3.5 py-3 bg-muted/20">
              <h4 className="text-[11px] font-medium text-muted-foreground mb-2">Recent Runs</h4>
              <div className="space-y-1.5">
                {job.runs.map((run) => (
                  <div key={run.id} className="flex items-center gap-3 text-xs">
                    <StatusBadge status={run.status} />
                    <span className="text-muted-foreground w-20 flex-shrink-0">{formatRelative(run.timestamp)}</span>
                    <span className="text-muted-foreground tabular-nums w-16 flex-shrink-0">{formatDuration(run.duration)}</span>
                    <span className="flex-1 truncate text-muted-foreground">
                      {run.error || run.output || '—'}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </CollapsibleContent>
      </div>
    </Collapsible>
  );
}

function StatusBadge({ status }: { status: 'success' | 'error' | 'running' }) {
  const config = {
    success: { icon: <Check className="h-3 w-3" />, className: 'text-status-success border-status-success-border' },
    error: { icon: <X className="h-3 w-3" />, className: 'text-status-error border-status-error-border' },
    running: { icon: <Loader2 className="h-3 w-3 animate-spin" />, className: 'text-status-warning border-status-warning-border' },
  }[status];

  return (
    <Badge variant="outline" className={`text-[10px] gap-1 ${config.className}`}>
      {config.icon}
      {status}
    </Badge>
  );
}

function CronJobDialog({ open, editingJob, onClose, onSave }: {
  open: boolean;
  editingJob: CronJob | null;
  onClose: () => void;
  onSave: (data: { name: string; schedule: string; message: string; agentId: string }) => void;
}) {
  const [name, setName] = useState(() => editingJob?.name || '');
  const [schedule, setSchedule] = useState(() => editingJob?.schedule || '0 * * * *');
  const [message, setMessage] = useState(() => editingJob?.message || '');
  const [agentId, setAgentId] = useState(() => editingJob?.agentId || 'main');

  const isValid = name.trim() && schedule.trim() && message.trim();

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) onClose(); }}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{editingJob ? 'Edit Job' : 'New Scheduled Job'}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* Name */}
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1.5 block">Name</label>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g., Daily Summary" className="h-8 text-sm" />
          </div>

          {/* Schedule */}
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1.5 block">Schedule (cron expression)</label>
            <div className="flex gap-2">
              <Input value={schedule} onChange={(e) => setSchedule(e.target.value)} placeholder="0 * * * *" className="h-8 text-sm font-mono flex-1" />
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="outline" size="sm" className="h-8 text-xs">Presets</Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent>
                  {SCHEDULE_PRESETS.map((p) => (
                    <DropdownMenuItem key={p.value} onClick={() => setSchedule(p.value)}>
                      <span className="flex-1">{p.label}</span>
                      <span className="text-[10px] font-mono text-muted-foreground ml-3">{p.value}</span>
                    </DropdownMenuItem>
                  ))}
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
            <p className="text-[11px] text-muted-foreground mt-1">{humanizeSchedule(schedule)}</p>
          </div>

          {/* Message */}
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1.5 block">Message (sent to agent)</label>
            <textarea
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              placeholder="What should the agent do on each run?"
              rows={3}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring resize-none"
            />
          </div>

          {/* Target Agent */}
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1.5 block">Target Agent</label>
            <Input value={agentId} onChange={(e) => setAgentId(e.target.value)} placeholder="main" className="h-8 text-sm" />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button disabled={!isValid} onClick={() => onSave({ name: name.trim(), schedule: schedule.trim(), message: message.trim(), agentId: agentId.trim() })}>
            {editingJob ? 'Save Changes' : 'Create Job'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
