import { useState, useEffect, useCallback } from 'react';
import { useConnectionStore } from '@/stores/connection-store';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import {
  Sparkles,
  Search,
  Loader2,
  RefreshCw,
  ChevronRight,
  FileText,
  Shield,
  Check,
  X,
  FolderOpen,
  ArrowLeft,
} from 'lucide-react';

interface SkillInfo {
  name: string;
  description?: string;
  trigger?: string;
  source?: 'workspace' | 'user' | 'bundled';
  enabled?: boolean;
  gates?: { type: string; value: string; passed: boolean }[];
  content?: string;
}

const SOURCE_LABELS: Record<string, { label: string; className: string }> = {
  workspace: { label: 'Workspace', className: 'text-blue-600 dark:text-blue-400 border-blue-300 dark:border-blue-800' },
  user: { label: 'User', className: 'text-green-600 dark:text-green-400 border-green-300 dark:border-green-800' },
  bundled: { label: 'Bundled', className: 'text-muted-foreground' },
};

export default function SkillsPage() {
  const { client } = useConnectionStore();
  const [skills, setSkills] = useState<SkillInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [selectedSkill, setSelectedSkill] = useState<SkillInfo | null>(null);

  const fetchSkills = useCallback(async () => {
    if (!client) return;
    setLoading(true);
    try {
      const res = await client.request('skills.list');
      setSkills((res.skills || []) as SkillInfo[]);
    } catch {
      setSkills([]);
    } finally {
      setLoading(false);
    }
  }, [client]);

  useEffect(() => { fetchSkills(); }, [fetchSkills]);

  const filtered = skills.filter((s) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return s.name.toLowerCase().includes(q) || (s.description || '').toLowerCase().includes(q);
  });

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (selectedSkill) {
    return (
      <SkillDetail skill={selectedSkill} onBack={() => setSelectedSkill(null)} />
    );
  }

  return (
    <div className="flex-1 p-6 overflow-auto">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <Sparkles className="h-5 w-5" /> Skills
          </h2>
          <p className="text-sm text-muted-foreground">{skills.length} skills loaded</p>
        </div>
        <Button variant="outline" size="sm" onClick={fetchSkills} className="gap-1.5">
          <RefreshCw className="h-3.5 w-3.5" /> Refresh
        </Button>
      </div>

      {skills.length === 0 ? (
        <div className="flex-1 flex items-center justify-center py-16">
          <div className="text-center text-muted-foreground">
            <Sparkles className="h-12 w-12 mx-auto mb-4 opacity-30" />
            <p className="text-sm">No skills loaded. Skills are defined in SKILL.md files in your workspace.</p>
          </div>
        </div>
      ) : (
        <>
          <div className="flex items-center gap-2 mb-4">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
              <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search skills..." className="pl-9 h-8 text-sm" />
            </div>
          </div>

          {/* Precedence note */}
          <div className="flex items-center gap-4 mb-4 text-[11px] text-muted-foreground">
            <span>Precedence:</span>
            <span className="flex items-center gap-1"><Badge variant="outline" className={`text-[10px] ${SOURCE_LABELS.workspace.className}`}>Workspace</Badge> highest</span>
            <span>{'>'}</span>
            <span className="flex items-center gap-1"><Badge variant="outline" className={`text-[10px] ${SOURCE_LABELS.user.className}`}>User</Badge></span>
            <span>{'>'}</span>
            <span className="flex items-center gap-1"><Badge variant="outline" className="text-[10px]">Bundled</Badge> lowest</span>
          </div>

          <div className="space-y-2">
            {filtered.map((skill) => (
              <button
                key={skill.name}
                onClick={() => setSelectedSkill(skill)}
                className="w-full text-left border border-border rounded-lg p-3.5 hover:bg-muted/30 transition-colors flex items-start gap-3"
              >
                <div className="h-8 w-8 rounded-md bg-primary/10 flex items-center justify-center flex-shrink-0">
                  <Sparkles className="h-4 w-4 text-primary" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-0.5">
                    <span className="font-medium text-sm">{skill.name}</span>
                    {skill.source && (
                      <Badge variant="outline" className={`text-[10px] ${SOURCE_LABELS[skill.source]?.className || ''}`}>
                        {SOURCE_LABELS[skill.source]?.label || skill.source}
                      </Badge>
                    )}
                    {skill.enabled === false && (
                      <Badge variant="outline" className="text-[10px] text-status-error border-status-error-border">Disabled</Badge>
                    )}
                  </div>
                  {skill.description && (
                    <p className="text-xs text-muted-foreground truncate">{skill.description}</p>
                  )}
                  {skill.trigger && (
                    <p className="text-[11px] text-muted-foreground/70 mt-0.5">
                      Trigger: <span className="font-mono">{skill.trigger}</span>
                    </p>
                  )}
                </div>

                {/* Gates */}
                {skill.gates && skill.gates.length > 0 && (
                  <div className="flex items-center gap-1 flex-shrink-0">
                    {skill.gates.map((gate, i) => (
                      <span key={i} title={`${gate.type}: ${gate.value}`}>
                        {gate.passed
                          ? <Check className="h-3.5 w-3.5 text-status-success" />
                          : <X className="h-3.5 w-3.5 text-status-error" />
                        }
                      </span>
                    ))}
                  </div>
                )}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

function SkillDetail({ skill, onBack }: { skill: SkillInfo; onBack: () => void }) {
  return (
    <div className="flex-1 flex flex-col min-h-0 p-6">
      <div className="flex items-center gap-3 mb-6">
        <Button variant="ghost" size="icon" className="h-8 w-8" onClick={onBack}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div className="h-10 w-10 rounded-lg bg-primary/10 flex items-center justify-center">
          <Sparkles className="h-5 w-5 text-primary" />
        </div>
        <div>
          <h2 className="text-lg font-semibold">{skill.name}</h2>
          {skill.description && <p className="text-xs text-muted-foreground">{skill.description}</p>}
        </div>
        <div className="ml-auto flex items-center gap-2">
          {skill.source && (
            <Badge variant="outline" className={`text-[10px] ${SOURCE_LABELS[skill.source]?.className || ''}`}>
              <FolderOpen className="h-3 w-3 mr-1" />
              {SOURCE_LABELS[skill.source]?.label || skill.source}
            </Badge>
          )}
        </div>
      </div>

      {/* Gates */}
      {skill.gates && skill.gates.length > 0 && (
        <div className="mb-4">
          <h3 className="text-xs font-medium text-muted-foreground mb-2 flex items-center gap-1.5">
            <Shield className="h-3.5 w-3.5" /> Gates
          </h3>
          <div className="flex flex-wrap gap-2">
            {skill.gates.map((gate, i) => (
              <Badge
                key={i}
                variant="outline"
                className={`text-[11px] gap-1 ${gate.passed ? 'text-status-success border-status-success-border' : 'text-status-error border-status-error-border'}`}
              >
                {gate.passed ? <Check className="h-3 w-3" /> : <X className="h-3 w-3" />}
                {gate.type}: {gate.value}
              </Badge>
            ))}
          </div>
        </div>
      )}

      {/* Content */}
      {skill.content ? (
        <div className="flex-1 border border-border rounded-lg overflow-hidden flex flex-col min-h-0">
          <div className="flex items-center gap-2 px-4 py-2 border-b border-border bg-muted/30">
            <FileText className="h-3.5 w-3.5 text-muted-foreground" />
            <span className="text-xs font-mono font-medium">SKILL.md</span>
          </div>
          <ScrollArea className="flex-1">
            <pre className="p-4 text-xs font-mono leading-relaxed whitespace-pre-wrap">{skill.content}</pre>
          </ScrollArea>
        </div>
      ) : (
        <div className="flex-1 border border-border rounded-lg flex items-center justify-center text-muted-foreground">
          <div className="text-center">
            <FileText className="h-8 w-8 mx-auto mb-2 opacity-30" />
            <p className="text-xs">Skill content not available from this view</p>
          </div>
        </div>
      )}
    </div>
  );
}
