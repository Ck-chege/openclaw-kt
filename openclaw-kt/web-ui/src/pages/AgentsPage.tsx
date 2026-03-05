import { useState, useEffect, useCallback } from 'react';
import { useConnectionStore } from '@/stores/connection-store';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import {
  Users,
  Bot,
  Cpu,
  Loader2,
  ChevronRight,
  FileText,
  Wrench,
  Sparkles,
  Activity,
  ArrowLeft,
  RefreshCw,
  Circle,
} from 'lucide-react';

interface AgentInfo {
  id: string;
  model?: string;
  isRunning?: boolean;
  sessionCount?: number;
  totalTokens?: number;
  tools?: string[];
  skills?: string[];
  workspaceFiles?: string[];
}

export default function AgentsPage() {
  const { client } = useConnectionStore();
  const [agents, setAgents] = useState<AgentInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedAgent, setSelectedAgent] = useState<AgentInfo | null>(null);
  const [fileContent, setFileContent] = useState<{ name: string; content: string } | null>(null);

  const fetchAgents = useCallback(async () => {
    if (!client) return;
    setLoading(true);
    try {
      const res = await client.request('agents.list');
      const list = (res.agents || []) as AgentInfo[];
      setAgents(list);
    } catch {
      // Fallback: show a default agent
      setAgents([{
        id: 'main',
        model: 'anthropic/claude-sonnet-4-20250514',
        isRunning: false,
        sessionCount: 0,
        totalTokens: 0,
        tools: ['exec', 'read', 'write', 'edit', 'web_search', 'web_fetch'],
        skills: [],
        workspaceFiles: ['SOUL.md', 'AGENTS.md', 'IDENTITY.md', 'USER.md', 'TOOLS.md', 'HEARTBEAT.md', 'MEMORY.md', 'BOOTSTRAP.md'],
      }]);
    } finally {
      setLoading(false);
    }
  }, [client]);

  useEffect(() => { fetchAgents(); }, [fetchAgents]);

  const loadFile = useCallback(async (agentId: string, fileName: string) => {
    if (!client) return;
    try {
      const res = await client.request('workspace.read', { agentId, file: fileName });
      setFileContent({ name: fileName, content: (res.content as string) || '' });
    } catch {
      setFileContent({ name: fileName, content: `(Could not load ${fileName})` });
    }
  }, [client]);

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  // Detail view
  if (selectedAgent) {
    return (
      <AgentDetail
        agent={selectedAgent}
        fileContent={fileContent}
        onBack={() => { setSelectedAgent(null); setFileContent(null); }}
        onLoadFile={(name) => loadFile(selectedAgent.id, name)}
      />
    );
  }

  // Card grid
  return (
    <div className="flex-1 p-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <Users className="h-5 w-5" />
            Agents
          </h2>
          <p className="text-sm text-muted-foreground">{agents.length} configured agent{agents.length !== 1 ? 's' : ''}</p>
        </div>
        <Button variant="outline" size="sm" onClick={fetchAgents} className="gap-1.5">
          <RefreshCw className="h-3.5 w-3.5" /> Refresh
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {agents.map((agent) => (
          <button
            key={agent.id}
            onClick={() => setSelectedAgent(agent)}
            className="text-left border border-border rounded-lg p-4 hover:bg-muted/50 transition-colors group"
          >
            <div className="flex items-start justify-between mb-3">
              <div className="flex items-center gap-2.5">
                <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center">
                  <Bot className="h-5 w-5 text-primary" />
                </div>
                <div>
                  <p className="font-medium text-sm">{agent.id}</p>
                  {agent.model && (
                    <p className="text-[11px] text-muted-foreground font-mono truncate max-w-[180px]">{agent.model}</p>
                  )}
                </div>
              </div>
              <StatusDot running={agent.isRunning} />
            </div>

            <div className="flex items-center gap-3 text-xs text-muted-foreground">
              {agent.sessionCount !== undefined && (
                <span className="flex items-center gap-1">
                  <Activity className="h-3 w-3" /> {agent.sessionCount} sessions
                </span>
              )}
              {agent.tools && (
                <span className="flex items-center gap-1">
                  <Wrench className="h-3 w-3" /> {agent.tools.length} tools
                </span>
              )}
              {agent.skills && agent.skills.length > 0 && (
                <span className="flex items-center gap-1">
                  <Sparkles className="h-3 w-3" /> {agent.skills.length} skills
                </span>
              )}
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}

function StatusDot({ running }: { running?: boolean }) {
  return (
    <Badge variant="outline" className={`text-[10px] gap-1 ${running ? 'text-status-success border-status-success-border' : 'text-muted-foreground'}`}>
      <Circle className={`h-2 w-2 ${running ? 'fill-status-success text-status-success' : 'fill-muted-foreground/30 text-muted-foreground/30'}`} />
      {running ? 'Running' : 'Idle'}
    </Badge>
  );
}

function AgentDetail({ agent, fileContent, onBack, onLoadFile }: {
  agent: AgentInfo;
  fileContent: { name: string; content: string } | null;
  onBack: () => void;
  onLoadFile: (name: string) => void;
}) {
  return (
    <div className="flex-1 flex flex-col min-h-0 p-6">
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <Button variant="ghost" size="icon" className="h-8 w-8" onClick={onBack}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div className="h-10 w-10 rounded-lg bg-primary/10 flex items-center justify-center">
          <Bot className="h-5 w-5 text-primary" />
        </div>
        <div>
          <h2 className="text-lg font-semibold">{agent.id}</h2>
          {agent.model && <p className="text-xs text-muted-foreground font-mono">{agent.model}</p>}
        </div>
        <div className="ml-auto">
          <StatusDot running={agent.isRunning} />
        </div>
      </div>

      <div className="flex-1 flex gap-6 min-h-0">
        {/* Left: Info panels */}
        <div className="w-64 flex-shrink-0 space-y-4">
          {/* Model config */}
          <InfoCard title="Model" icon={<Cpu className="h-3.5 w-3.5" />}>
            <p className="text-xs font-mono text-muted-foreground">{agent.model || 'Not configured'}</p>
          </InfoCard>

          {/* Tools */}
          {agent.tools && agent.tools.length > 0 && (
            <InfoCard title="Tools" icon={<Wrench className="h-3.5 w-3.5" />}>
              <div className="flex flex-wrap gap-1">
                {agent.tools.map((t) => (
                  <Badge key={t} variant="outline" className="text-[10px] font-mono">{t}</Badge>
                ))}
              </div>
            </InfoCard>
          )}

          {/* Skills */}
          {agent.skills && agent.skills.length > 0 && (
            <InfoCard title="Skills" icon={<Sparkles className="h-3.5 w-3.5" />}>
              <div className="flex flex-wrap gap-1">
                {agent.skills.map((s) => (
                  <Badge key={s} variant="outline" className="text-[10px]">{s}</Badge>
                ))}
              </div>
            </InfoCard>
          )}

          {/* Workspace files */}
          {agent.workspaceFiles && (
            <InfoCard title="Workspace Files" icon={<FileText className="h-3.5 w-3.5" />}>
              <div className="space-y-0.5">
                {agent.workspaceFiles.map((f) => (
                  <button
                    key={f}
                    onClick={() => onLoadFile(f)}
                    className={`w-full text-left text-xs px-2 py-1 rounded hover:bg-muted transition-colors font-mono ${
                      fileContent?.name === f ? 'bg-muted font-medium' : 'text-muted-foreground'
                    }`}
                  >
                    {f}
                  </button>
                ))}
              </div>
            </InfoCard>
          )}
        </div>

        {/* Right: File viewer */}
        <div className="flex-1 border border-border rounded-lg overflow-hidden flex flex-col min-h-0">
          {fileContent ? (
            <>
              <div className="flex items-center gap-2 px-4 py-2 border-b border-border bg-muted/30">
                <FileText className="h-3.5 w-3.5 text-muted-foreground" />
                <span className="text-xs font-mono font-medium">{fileContent.name}</span>
              </div>
              <ScrollArea className="flex-1">
                <pre className="p-4 text-xs font-mono leading-relaxed whitespace-pre-wrap">
                  {fileContent.content}
                </pre>
              </ScrollArea>
            </>
          ) : (
            <div className="flex-1 flex items-center justify-center text-muted-foreground">
              <div className="text-center">
                <FileText className="h-8 w-8 mx-auto mb-2 opacity-30" />
                <p className="text-xs">Select a workspace file to view its contents</p>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function InfoCard({ title, icon, children }: { title: string; icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="border border-border rounded-lg p-3">
      <h3 className="text-xs font-medium flex items-center gap-1.5 mb-2 text-muted-foreground">
        {icon} {title}
      </h3>
      {children}
    </div>
  );
}
