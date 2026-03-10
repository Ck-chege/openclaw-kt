import { useState, useEffect, useCallback } from 'react';
import { useConnectionStore } from '@/stores/connection-store';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { ScrollArea } from '@/components/ui/scroll-area';
import { notify } from '@/lib/notifications';
import {
  Sparkles,
  Search,
  Loader2,
  RefreshCw,
  FileText,
  Shield,
  Check,
  X,
  FolderOpen,
  ArrowLeft,
  Globe,
} from 'lucide-react';

interface RequirementInfo {
  type: string;
  value: string;
  passed: boolean;
}

interface SkillResourcesInfo {
  scripts: string[];
  references: string[];
  assets: string[];
}

interface SkillEntryInfo {
  enabled?: boolean;
  env: Record<string, string>;
  config: Record<string, string>;
  hasApiKey: boolean;
}

interface SkillInfo {
  name: string;
  description?: string;
  skillKey: string;
  source?: 'workspace' | 'user' | 'bundled' | 'extra' | 'plugin';
  enabled?: boolean;
  eligible?: boolean;
  always?: boolean;
  blockedByAllowlist?: boolean;
  filePath?: string;
  baseDir?: string;
  homepage?: string;
  requirements?: RequirementInfo[];
  resources?: SkillResourcesInfo;
  entry?: SkillEntryInfo;
}

const SOURCE_LABELS: Record<string, { label: string; className: string }> = {
  workspace: { label: 'Workspace', className: 'text-blue-600 dark:text-blue-400 border-blue-300 dark:border-blue-800' },
  user: { label: 'User', className: 'text-green-600 dark:text-green-400 border-green-300 dark:border-green-800' },
  bundled: { label: 'Bundled', className: 'text-muted-foreground' },
  extra: { label: 'Extra', className: 'text-amber-600 dark:text-amber-400 border-amber-300 dark:border-amber-800' },
  plugin: { label: 'Plugin', className: 'text-cyan-600 dark:text-cyan-400 border-cyan-300 dark:border-cyan-800' },
};

export default function SkillsPage() {
  const { client } = useConnectionStore();
  const [skills, setSkills] = useState<SkillInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [selectedSkill, setSelectedSkill] = useState<SkillInfo | null>(null);
  const [updatingSkillKey, setUpdatingSkillKey] = useState<string | null>(null);

  const fetchSkills = useCallback(async () => {
    if (!client) return;
    setLoading(true);
    try {
      const res = await client.request('skills.list');
      const nextSkills = (res.skills || []) as SkillInfo[];
      setSkills(nextSkills);
      setSelectedSkill((current) => current ? nextSkills.find((skill) => skill.skillKey === current.skillKey) || null : null);
    } catch {
      setSkills([]);
      setSelectedSkill(null);
    } finally {
      setLoading(false);
    }
  }, [client]);

  const refreshSkills = useCallback(async () => {
    if (!client) return;
    setLoading(true);
    try {
      await client.request('skills.refresh');
    } finally {
      await fetchSkills();
    }
  }, [client, fetchSkills]);

  const updateSkillEnabled = useCallback(async (skill: SkillInfo, enabled: boolean) => {
    if (!client) return;
    setUpdatingSkillKey(skill.skillKey);
    try {
      await client.request('skills.update', { skillKey: skill.skillKey, enabled });
      notify.success(`${enabled ? 'Enabled' : 'Disabled'} ${skill.name}`);
      await fetchSkills();
    } catch {
      notify.error(`Failed to update ${skill.name}`);
    } finally {
      setUpdatingSkillKey(null);
    }
  }, [client, fetchSkills]);

  const updateSkillOverrides = useCallback(async (
    skill: SkillInfo,
    overrides: {
      apiKey?: string;
      clearApiKey?: boolean;
      env?: Record<string, string>;
      config?: Record<string, string>;
    },
  ) => {
    if (!client) return;
    setUpdatingSkillKey(skill.skillKey);
    try {
      await client.request('skills.update', {
        skillKey: skill.skillKey,
        ...overrides,
      });
      notify.success(`Updated overrides for ${skill.name}`);
      await fetchSkills();
    } catch {
      notify.error(`Failed to update overrides for ${skill.name}`);
    } finally {
      setUpdatingSkillKey(null);
    }
  }, [client, fetchSkills]);

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
      <SkillDetail
        key={JSON.stringify({
          skillKey: selectedSkill.skillKey,
          enabled: selectedSkill.enabled,
          entry: selectedSkill.entry,
        })}
        skill={selectedSkill}
        updatingSkillKey={updatingSkillKey}
        onBack={() => setSelectedSkill(null)}
        onToggleEnabled={updateSkillEnabled}
        onSaveOverrides={updateSkillOverrides}
      />
    );
  }

  return (
    <div className="flex-1 p-6 overflow-auto">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <Sparkles className="h-5 w-5" /> Skills
          </h2>
          <p className="text-sm text-muted-foreground">{skills.length} skills discovered</p>
        </div>
        <Button variant="outline" size="sm" onClick={refreshSkills} className="gap-1.5">
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
                    {skill.blockedByAllowlist && (
                      <Badge variant="outline" className="text-[10px] text-status-warning border-status-warning-border">Blocked</Badge>
                    )}
                    {skill.eligible === false && skill.enabled !== false && !skill.blockedByAllowlist && (
                      <Badge variant="outline" className="text-[10px] text-status-warning border-status-warning-border">Unavailable</Badge>
                    )}
                  </div>
                  {skill.description && (
                    <p className="text-xs text-muted-foreground truncate">{skill.description}</p>
                  )}
                  {skill.filePath && (
                    <p className="text-[11px] text-muted-foreground/70 mt-0.5 truncate font-mono">
                      {skill.filePath}
                    </p>
                  )}
                </div>

                {skill.requirements && skill.requirements.length > 0 && (
                  <div className="flex items-center gap-1 flex-shrink-0">
                    {skill.requirements.map((requirement, i) => (
                      <span key={i} title={`${requirement.type}: ${requirement.value}`}>
                        {requirement.passed
                          ? <Check className="h-3.5 w-3.5 text-status-success" />
                          : <X className="h-3.5 w-3.5 text-status-error" />
                        }
                      </span>
                    ))}
                  </div>
                )}
                <div className="flex-shrink-0">
                  <Button
                    type="button"
                    variant={skill.enabled === false ? 'default' : 'outline'}
                    size="sm"
                    disabled={updatingSkillKey === skill.skillKey}
                    onClick={(event) => {
                      event.stopPropagation();
                      void updateSkillEnabled(skill, skill.enabled === false);
                    }}
                  >
                    {updatingSkillKey === skill.skillKey
                      ? 'Saving...'
                      : skill.enabled === false
                        ? 'Enable'
                        : 'Disable'}
                  </Button>
                </div>
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

function SkillDetail({
  skill,
  updatingSkillKey,
  onBack,
  onToggleEnabled,
  onSaveOverrides,
}: {
  skill: SkillInfo;
  updatingSkillKey: string | null;
  onBack: () => void;
  onToggleEnabled: (skill: SkillInfo, enabled: boolean) => Promise<void>;
  onSaveOverrides: (
    skill: SkillInfo,
    overrides: {
      apiKey?: string;
      clearApiKey?: boolean;
      env?: Record<string, string>;
      config?: Record<string, string>;
    },
  ) => Promise<void>;
}) {
  const [editorOpen, setEditorOpen] = useState(false);
  const [apiKey, setApiKey] = useState('');
  const [clearApiKey, setClearApiKey] = useState(false);
  const [envText, setEnvText] = useState(() => formatKeyValueLines(skill.entry?.env || {}));
  const [configText, setConfigText] = useState(() => formatKeyValueLines(skill.entry?.config || {}));

  const handleSaveOverrides = async () => {
    const parsedEnv = parseKeyValueLines(envText);
    if (!parsedEnv.ok) {
      notify.error(parsedEnv.error);
      return;
    }
    const parsedConfig = parseKeyValueLines(configText);
    if (!parsedConfig.ok) {
      notify.error(parsedConfig.error);
      return;
    }

    await onSaveOverrides(skill, {
      apiKey: apiKey.trim() ? apiKey.trim() : undefined,
      clearApiKey,
      env: parsedEnv.value,
      config: parsedConfig.value,
    });
    setEditorOpen(false);
  };

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

      {skill.filePath && (
        <div className="mb-4 rounded-lg border border-border bg-muted/20 px-4 py-3">
          <div className="text-[11px] uppercase tracking-wide text-muted-foreground mb-1">Source File</div>
          <div className="font-mono text-xs break-all">{skill.filePath}</div>
        </div>
      )}

      {skill.homepage && (
        <div className="mb-4">
          <a
            href={skill.homepage}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-2 text-xs text-primary hover:underline"
          >
            <Globe className="h-3.5 w-3.5" />
            {skill.homepage}
          </a>
        </div>
      )}

      {skill.requirements && skill.requirements.length > 0 && (
        <div className="mb-4">
          <h3 className="text-xs font-medium text-muted-foreground mb-2 flex items-center gap-1.5">
            <Shield className="h-3.5 w-3.5" /> Requirements
          </h3>
          <div className="flex flex-wrap gap-2">
            {skill.requirements.map((requirement, i) => (
              <Badge
                key={i}
                variant="outline"
                className={`text-[11px] gap-1 ${requirement.passed ? 'text-status-success border-status-success-border' : 'text-status-error border-status-error-border'}`}
              >
                {requirement.passed ? <Check className="h-3 w-3" /> : <X className="h-3 w-3" />}
                {requirement.type}: {requirement.value}
              </Badge>
            ))}
          </div>
        </div>
      )}

      <div className="mb-4">
        <Button
          type="button"
          variant={skill.enabled === false ? 'default' : 'outline'}
          disabled={updatingSkillKey === skill.skillKey}
          onClick={() => void onToggleEnabled(skill, skill.enabled === false)}
        >
          {updatingSkillKey === skill.skillKey
            ? 'Saving...'
            : skill.enabled === false
              ? 'Enable Skill'
              : 'Disable Skill'}
        </Button>
        <Button
          type="button"
          variant="outline"
          className="ml-2"
          disabled={updatingSkillKey === skill.skillKey}
          onClick={() => setEditorOpen(true)}
        >
          Edit Overrides
        </Button>
      </div>

      {skill.resources && (
        <div className="flex-1 border border-border rounded-lg overflow-hidden flex flex-col min-h-0">
          <div className="flex items-center gap-2 px-4 py-2 border-b border-border bg-muted/30">
            <FileText className="h-3.5 w-3.5 text-muted-foreground" />
            <span className="text-xs font-mono font-medium">Resources</span>
          </div>
          <ScrollArea className="flex-1">
            <div className="p-4 space-y-4">
              <ResourceList title="Scripts" items={skill.resources.scripts} />
              <ResourceList title="References" items={skill.resources.references} />
              <ResourceList title="Assets" items={skill.resources.assets} />
            </div>
          </ScrollArea>
        </div>
      )}

      <Dialog open={editorOpen} onOpenChange={setEditorOpen}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Edit Skill Overrides</DialogTitle>
            <DialogDescription>
              Configure per-skill API key, env overrides, and config overrides for {skill.name}.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div>
              <div className="text-xs font-medium mb-2">API Key</div>
              <Input
                type="password"
                value={apiKey}
                onChange={(event) => setApiKey(event.target.value)}
                placeholder={skill.entry?.hasApiKey ? 'API key already configured' : 'Enter API key'}
              />
              <label className="mt-2 flex items-center gap-2 text-xs text-muted-foreground">
                <input
                  type="checkbox"
                  checked={clearApiKey}
                  onChange={(event) => setClearApiKey(event.target.checked)}
                />
                Clear stored API key
              </label>
            </div>

            <div>
              <div className="text-xs font-medium mb-2">Env Overrides</div>
              <textarea
                value={envText}
                onChange={(event) => setEnvText(event.target.value)}
                placeholder="KEY=value"
                className="min-h-32 w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50"
              />
              <div className="mt-1 text-xs text-muted-foreground">One entry per line, formatted as <span className="font-mono">KEY=value</span>.</div>
            </div>

            <div>
              <div className="text-xs font-medium mb-2">Config Overrides</div>
              <textarea
                value={configText}
                onChange={(event) => setConfigText(event.target.value)}
                placeholder="region=us-east-1"
                className="min-h-32 w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50"
              />
              <div className="mt-1 text-xs text-muted-foreground">One entry per line, formatted as <span className="font-mono">key=value</span>.</div>
            </div>
          </div>

          <DialogFooter showCloseButton>
            <Button
              type="button"
              disabled={updatingSkillKey === skill.skillKey}
              onClick={() => void handleSaveOverrides()}
            >
              {updatingSkillKey === skill.skillKey ? 'Saving...' : 'Save Overrides'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function ResourceList({ title, items }: { title: string; items: string[] }) {
  return (
    <div>
      <div className="text-[11px] uppercase tracking-wide text-muted-foreground mb-2">{title}</div>
      {items.length > 0 ? (
        <div className="space-y-1">
          {items.map((item) => (
            <div key={item} className="font-mono text-xs break-all">{item}</div>
          ))}
        </div>
      ) : (
        <div className="text-xs text-muted-foreground">None</div>
      )}
    </div>
  );
}

function formatKeyValueLines(values: Record<string, string>): string {
  return Object.entries(values)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => `${key}=${value}`)
    .join('\n');
}

function parseKeyValueLines(input: string): { ok: true; value: Record<string, string> } | { ok: false; error: string } {
  const result: Record<string, string> = {};
  const lines = input.split('\n');
  for (let index = 0; index < lines.length; index += 1) {
    const raw = lines[index].trim();
    if (!raw) continue;
    const separator = raw.indexOf('=');
    if (separator <= 0) {
      return { ok: false, error: `Invalid entry on line ${index + 1}. Use KEY=value.` };
    }
    const key = raw.slice(0, separator).trim();
    const value = raw.slice(separator + 1).trim();
    if (!key) {
      return { ok: false, error: `Missing key on line ${index + 1}.` };
    }
    result[key] = value;
  }
  return { ok: true, value: result };
}
