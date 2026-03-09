import { useState, useEffect, useCallback } from 'react';
import { useConnectionStore } from '@/stores/connection-store';
import { useDashboardStore } from '@/stores/dashboard-store';
import { useThemeStore } from '@/stores/theme-store';
import { notify } from '@/lib/notifications';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import {
  Settings,
  Globe,
  Cpu,
  Palette,
  Keyboard,
  Database,
  Info,
  Sun,
  Moon,
  Monitor,
  Check,
  Eye,
  EyeOff,
  Trash2,
  Download,
  ExternalLink,
  Zap,
  ChevronDown,
  ChevronRight,
  Loader2,
  Pencil,
  X,
} from 'lucide-react';

type SettingsTab = 'general' | 'models' | 'appearance' | 'shortcuts' | 'data' | 'configuration' | 'about';

const TABS: { id: SettingsTab; label: string; icon: React.ReactNode }[] = [
  { id: 'general', label: 'General', icon: <Globe className="h-4 w-4" /> },
  { id: 'models', label: 'Models', icon: <Cpu className="h-4 w-4" /> },
  { id: 'configuration', label: 'Configuration', icon: <Zap className="h-4 w-4" /> },
  { id: 'appearance', label: 'Appearance', icon: <Palette className="h-4 w-4" /> },
  { id: 'shortcuts', label: 'Shortcuts', icon: <Keyboard className="h-4 w-4" /> },
  { id: 'data', label: 'Data', icon: <Database className="h-4 w-4" /> },
  { id: 'about', label: 'About', icon: <Info className="h-4 w-4" /> },
];

export default function SettingsPage() {
  const [activeTab, setActiveTab] = useState<SettingsTab>('general');

  return (
    <div className="flex-1 flex min-h-0 p-6">
      {/* Tab nav */}
      <div className="w-48 flex-shrink-0 pr-6 border-r border-border">
        <h2 className="text-lg font-semibold flex items-center gap-2 mb-4">
          <Settings className="h-5 w-5" /> Settings
        </h2>
        <nav className="space-y-0.5">
          {TABS.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-md text-sm transition-colors ${
                activeTab === tab.id
                  ? 'bg-muted font-medium'
                  : 'text-muted-foreground hover:text-foreground hover:bg-muted/50'
              }`}
            >
              {tab.icon}
              {tab.label}
            </button>
          ))}
        </nav>
      </div>

      {/* Tab content */}
      <div className="flex-1 pl-6 overflow-auto">
        {activeTab === 'general' && <GeneralTab />}
        {activeTab === 'models' && <ModelsTab />}
        {activeTab === 'configuration' && <ConfigurationTab />}
        {activeTab === 'appearance' && <AppearanceTab />}
        {activeTab === 'shortcuts' && <ShortcutsTab />}
        {activeTab === 'data' && <DataTab />}
        {activeTab === 'about' && <AboutTab />}
      </div>
    </div>
  );
}

/* ── General Tab ────────────────────────────────────────── */
function GeneralTab() {
  const { state } = useConnectionStore();
  const [gatewayUrl, setGatewayUrl] = useState(
    localStorage.getItem('openclaw-gateway-url') || `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`
  );
  const [token, setToken] = useState(localStorage.getItem('openclaw-token') || '');
  const [showToken, setShowToken] = useState(false);
  const [autoReconnect, setAutoReconnect] = useState(
    localStorage.getItem('openclaw-auto-reconnect') !== 'false'
  );
  const { connect } = useConnectionStore();

  const handleSaveConnection = () => {
    localStorage.setItem('openclaw-gateway-url', gatewayUrl);
    if (token) localStorage.setItem('openclaw-token', token);
    else localStorage.removeItem('openclaw-token');
    connect(gatewayUrl, token || undefined);
  };

  const handleToggleAutoReconnect = () => {
    const next = !autoReconnect;
    setAutoReconnect(next);
    localStorage.setItem('openclaw-auto-reconnect', String(next));
  };

  return (
    <div className="max-w-lg space-y-6">
      <SettingsSection title="Gateway Connection" description="Configure the WebSocket connection to your OpenClaw gateway.">
        <div className="space-y-3">
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1.5 block">Gateway URL</label>
            <Input
              value={gatewayUrl}
              onChange={(e) => setGatewayUrl(e.target.value)}
              placeholder="ws://localhost:8080/ws"
              className="h-8 text-sm font-mono"
            />
          </div>
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1.5 block">Auth Token (optional)</label>
            <div className="relative">
              <Input
                type={showToken ? 'text' : 'password'}
                value={token}
                onChange={(e) => setToken(e.target.value)}
                placeholder="Bearer token"
                className="h-8 text-sm font-mono pr-8"
              />
              <button
                onClick={() => setShowToken(!showToken)}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
              >
                {showToken ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
              </button>
            </div>
          </div>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Badge variant="outline" className={`text-[10px] ${
                state === 'connected' ? 'text-status-success border-status-success-border' :
                state === 'connecting' ? 'text-status-warning border-status-warning-border' :
                'text-muted-foreground'
              }`}>
                {state}
              </Badge>
            </div>
            <Button size="sm" onClick={handleSaveConnection}>
              Save & Reconnect
            </Button>
          </div>
        </div>
      </SettingsSection>

      <SettingsSection title="Behavior" description="General behavior preferences.">
        <ToggleRow
          label="Auto-reconnect"
          description="Automatically reconnect when the connection drops"
          checked={autoReconnect}
          onChange={handleToggleAutoReconnect}
        />
      </SettingsSection>
    </div>
  );
}

/* ── Models Tab ─────────────────────────────────────────── */
function ModelsTab() {
  const { models, supportedModels, selectedModelId, setSelectedModel } = useDashboardStore();
  const unavailableModels = supportedModels.filter((model) => !model.ready);

  return (
    <div className="max-w-lg space-y-6">
      <SettingsSection title="Ready Models" description="Runnable models for chat right now. Select a default for new conversations.">
        {models.length === 0 ? (
          <p className="text-xs text-muted-foreground">No ready models loaded. Add provider credentials or configuration first.</p>
        ) : (
          <div className="space-y-2">
            {models.map((model) => {
              const isSelected = selectedModelId === model.id || (!selectedModelId && model.id === models[0].id);
              return (
                <button
                  key={model.id}
                  onClick={() => setSelectedModel(model.id)}
                  className={`w-full text-left border rounded-lg p-3 transition-colors ${
                    isSelected ? 'border-primary bg-primary/5' : 'border-border hover:bg-muted/30'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium">{model.displayName}</p>
                      <p className="text-[11px] font-mono text-muted-foreground">{model.id}</p>
                    </div>
                    {isSelected && (
                      <Badge className="text-[10px] gap-1">
                        <Check className="h-3 w-3" /> Default
                      </Badge>
                    )}
                  </div>
                </button>
              );
            })}
          </div>
        )}
      </SettingsSection>

      <SettingsSection title="Unavailable Models" description="Supported models that are currently hidden from chat because they are not runnable with the current configuration.">
        {unavailableModels.length === 0 ? (
          <p className="text-xs text-muted-foreground">All supported models are currently ready.</p>
        ) : (
          <div className="space-y-2">
            {unavailableModels.map((model) => (
              <div
                key={model.id}
                className="w-full text-left border rounded-lg p-3 border-border bg-muted/20 opacity-80"
              >
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-medium">{model.displayName}</p>
                    <p className="text-[11px] font-mono text-muted-foreground break-all">{model.id}</p>
                  </div>
                  <Badge variant="outline" className="text-[10px] flex-shrink-0">
                    unavailable
                  </Badge>
                </div>
                <p className="text-[11px] text-muted-foreground mt-2">
                  {model.unavailableReason || 'Provider not configured'}
                </p>
              </div>
            ))}
          </div>
        )}
      </SettingsSection>
    </div>
  );
}

/* ── Configuration Tab ──────────────────────────────────── */
const CONFIG_MODELS = [
  { value: 'claude-sonnet-4-20250514', label: 'Claude Sonnet 4' },
  { value: 'claude-opus-4-20250514', label: 'Claude Opus 4' },
  { value: 'gpt-4o', label: 'GPT-4o' },
  { value: 'gpt-4o-mini', label: 'GPT-4o Mini' },
];

type EditableField = 'anthropicKey' | 'openaiKey' | 'token' | 'model';

const SECRET_PATHS = [
  ['secrets', 'anthropicApiKey'],
  ['secrets', 'openaiApiKey'],
  ['gateway', 'auth', 'token'],
  ['gateway', 'auth', 'password'],
];

function redactConfig(config: Record<string, unknown>): Record<string, unknown> {
  const clone = JSON.parse(JSON.stringify(config));
  for (const path of SECRET_PATHS) {
    let obj = clone;
    for (let i = 0; i < path.length - 1; i++) {
      if (obj && typeof obj === 'object' && path[i] in obj) {
        obj = obj[path[i]] as Record<string, unknown>;
      } else {
        obj = null as unknown as Record<string, unknown>;
        break;
      }
    }
    if (obj && typeof obj === 'object') {
      const key = path[path.length - 1];
      if (obj[key] != null && obj[key] !== '') {
        obj[key] = '\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF';
      }
    }
  }
  return clone;
}

function ConfigRow({ label, value, status, statusOk, onEdit }: {
  label: string;
  value: string;
  status: string;
  statusOk: boolean;
  onEdit: () => void;
}) {
  return (
    <div className="flex items-center justify-between py-2.5 border-b border-border/50 last:border-0">
      <div className="flex items-center gap-3 min-w-0">
        <span className="text-sm">{label}</span>
        <span className="text-xs font-mono text-muted-foreground truncate">{value}</span>
      </div>
      <div className="flex items-center gap-2 flex-shrink-0">
        <Badge variant="outline" className={`text-[10px] ${
          statusOk ? 'text-status-success border-status-success-border' : 'text-muted-foreground'
        }`}>
          {status}
        </Badge>
        <button
          onClick={onEdit}
          className="p-1 rounded-md text-muted-foreground hover:text-foreground hover:bg-muted/50 transition-colors"
          title={`Edit ${label}`}
        >
          <Pencil className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}

function ConfigurationTab() {
  const { client } = useConnectionStore();
  const dashboardStore = useDashboardStore();
  const config = dashboardStore.config;

  const [status, setStatus] = useState<{
    hasAnthropicKey: boolean;
    hasOpenaiKey: boolean;
    hasAuthToken: boolean;
    defaultModel: string;
  } | null>(null);

  const [editing, setEditing] = useState<EditableField | null>(null);
  const [editValue, setEditValue] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showConfig, setShowConfig] = useState(false);

  const fetchStatus = useCallback(async () => {
    if (!client) return;
    try {
      const res = await client.request('setup.status');
      setStatus(res as typeof status);
    } catch {
      // status not available
    }
  }, [client]);

  useEffect(() => {
    fetchStatus();
  }, [fetchStatus]);

  const startEditing = (field: EditableField) => {
    setEditing(field);
    setError(null);
    // Pre-fill model with current value; secrets start blank
    if (field === 'model') {
      setEditValue(status?.defaultModel || CONFIG_MODELS[0].value);
    } else {
      setEditValue('');
    }
  };

  const cancelEditing = () => {
    setEditing(null);
    setEditValue('');
    setError(null);
  };

  const handleSave = async () => {
    if (!client || !editing) return;
    setSaving(true);
    setError(null);

    try {
      const params: Record<string, unknown> = {};
      if (editing === 'anthropicKey') params.anthropicKey = editValue.trim();
      if (editing === 'openaiKey') params.openaiKey = editValue.trim();
      if (editing === 'token') params.token = editValue.trim();
      if (editing === 'model') params.model = editValue;

      await client.request('setup.configure', params);

      // Sync localStorage if auth token changed
      if (editing === 'token' && editValue.trim()) {
        localStorage.setItem('openclaw-token', editValue.trim());
      }

      // Refresh state
      await fetchStatus();
      if (client) await dashboardStore.fetchConfig(client);

      // Reset editor
      setEditing(null);
      setEditValue('');
      notify.success('Configuration updated.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save configuration');
    } finally {
      setSaving(false);
    }
  };

  const renderEditor = (field: EditableField) => {
    if (editing !== field) return null;

    return (
      <div className="py-2 pl-4 space-y-2">
        {field === 'model' ? (
          <select
            value={editValue}
            onChange={(e) => setEditValue(e.target.value)}
            className="flex h-8 w-full rounded-md border border-input bg-background px-3 py-1 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            autoFocus
          >
            {CONFIG_MODELS.map((m) => (
              <option key={m.value} value={m.value}>
                {m.label}
              </option>
            ))}
          </select>
        ) : (
          <Input
            type="password"
            value={editValue}
            onChange={(e) => setEditValue(e.target.value)}
            placeholder={field === 'token' ? 'Enter auth token' : 'Enter API key'}
            className="h-8 text-sm font-mono"
            autoFocus
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleSave();
              if (e.key === 'Escape') cancelEditing();
            }}
          />
        )}
        <div className="flex items-center gap-2">
          <Button size="sm" onClick={handleSave} disabled={saving} className="gap-1.5 h-7 text-xs">
            {saving ? <Loader2 className="h-3 w-3 animate-spin" /> : <Check className="h-3 w-3" />}
            Save
          </Button>
          <Button size="sm" variant="ghost" onClick={cancelEditing} disabled={saving} className="gap-1.5 h-7 text-xs">
            <X className="h-3 w-3" /> Cancel
          </Button>
          {error && <p className="text-xs text-destructive">{error}</p>}
        </div>
      </div>
    );
  };

  return (
    <div className="max-w-lg space-y-6">
      <SettingsSection title="API Keys" description="LLM provider API keys configured on the server.">
        <div>
          <ConfigRow
            label="Anthropic API Key"
            value={status?.hasAnthropicKey ? '\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF' : '\u2014'}
            status={status?.hasAnthropicKey ? 'Configured' : 'Not set'}
            statusOk={status?.hasAnthropicKey ?? false}
            onEdit={() => startEditing('anthropicKey')}
          />
          {renderEditor('anthropicKey')}

          <ConfigRow
            label="OpenAI API Key"
            value={status?.hasOpenaiKey ? '\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF' : '\u2014'}
            status={status?.hasOpenaiKey ? 'Configured' : 'Not set'}
            statusOk={status?.hasOpenaiKey ?? false}
            onEdit={() => startEditing('openaiKey')}
          />
          {renderEditor('openaiKey')}
        </div>
      </SettingsSection>

      <SettingsSection title="Authentication" description="Gateway authentication token.">
        <div>
          <ConfigRow
            label="Auth Token"
            value={status?.hasAuthToken ? '\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF' : '\u2014'}
            status={status?.hasAuthToken ? 'Configured' : 'Not set'}
            statusOk={status?.hasAuthToken ?? false}
            onEdit={() => startEditing('token')}
          />
          {renderEditor('token')}
        </div>
      </SettingsSection>

      <SettingsSection title="Default Model" description="The model used for new conversations.">
        <div>
          <ConfigRow
            label="Model"
            value={status?.defaultModel || '\u2014'}
            status={status?.defaultModel ? 'Set' : 'Default'}
            statusOk={!!status?.defaultModel}
            onEdit={() => startEditing('model')}
          />
          {renderEditor('model')}
        </div>
      </SettingsSection>

      {/* Read-only server config */}
      {config && (
        <SettingsSection title="Server Config" description="Read-only view of the current gateway configuration.">
          <button
            onClick={() => setShowConfig(!showConfig)}
            className="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors"
          >
            {showConfig ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
            {showConfig ? 'Hide' : 'Show'} raw config
          </button>
          {showConfig && (
            <pre className="text-xs font-mono text-muted-foreground bg-muted/50 rounded-md p-3 overflow-auto mt-2">
              {JSON.stringify(redactConfig(config), null, 2)}
            </pre>
          )}
        </SettingsSection>
      )}
    </div>
  );
}

/* ── Appearance Tab ─────────────────────────────────────── */
function AppearanceTab() {
  const { theme, setTheme } = useThemeStore();

  const themes: { value: 'light' | 'dark' | 'system'; label: string; icon: React.ReactNode }[] = [
    { value: 'light', label: 'Light', icon: <Sun className="h-4 w-4" /> },
    { value: 'dark', label: 'Dark', icon: <Moon className="h-4 w-4" /> },
    { value: 'system', label: 'System', icon: <Monitor className="h-4 w-4" /> },
  ];

  return (
    <div className="max-w-lg space-y-6">
      <SettingsSection title="Theme" description="Choose your preferred color scheme.">
        <div className="flex gap-2">
          {themes.map((t) => (
            <button
              key={t.value}
              onClick={() => setTheme(t.value)}
              className={`flex-1 flex flex-col items-center gap-2 p-4 rounded-lg border transition-colors ${
                theme === t.value
                  ? 'border-primary bg-primary/5'
                  : 'border-border hover:bg-muted/30'
              }`}
            >
              {t.icon}
              <span className="text-xs font-medium">{t.label}</span>
              {theme === t.value && <Check className="h-3.5 w-3.5 text-primary" />}
            </button>
          ))}
        </div>
      </SettingsSection>
    </div>
  );
}

/* ── Shortcuts Tab ──────────────────────────────────────── */
function ShortcutsTab() {
  const isMac = navigator.platform.includes('Mac');
  const mod = isMac ? '\u2318' : 'Ctrl';

  const shortcuts = [
    { keys: `${mod} K`, description: 'Open command palette' },
    { keys: `${mod} N`, description: 'New session' },
    { keys: `${mod} ,`, description: 'Open settings' },
    { keys: '/', description: 'Focus chat input' },
    { keys: 'Enter', description: 'Send message' },
    { keys: 'Shift + Enter', description: 'New line in input' },
    { keys: 'Escape', description: 'Stop streaming / close dialog' },
  ];

  return (
    <div className="max-w-lg space-y-6">
      <SettingsSection title="Keyboard Shortcuts" description="Available keyboard shortcuts for quick navigation.">
        <div className="space-y-1">
          {shortcuts.map((s) => (
            <div key={s.keys} className="flex items-center justify-between py-2 border-b border-border/50 last:border-0">
              <span className="text-sm">{s.description}</span>
              <kbd className="px-2 py-0.5 rounded bg-muted border border-border text-[11px] font-mono font-medium">
                {s.keys}
              </kbd>
            </div>
          ))}
        </div>
      </SettingsSection>
    </div>
  );
}

/* ── Data Tab ───────────────────────────────────────────── */
function DataTab() {
  const { sessions } = useDashboardStore();

  const handleExportSessions = () => {
    const blob = new Blob([JSON.stringify(sessions, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `openclaw-sessions-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleClearLocal = () => {
    const keys = Object.keys(localStorage).filter((k) => k.startsWith('openclaw-'));
    keys.forEach((k) => localStorage.removeItem(k));
    window.location.reload();
  };

  return (
    <div className="max-w-lg space-y-6">
      <SettingsSection title="Export" description="Export your session data for backup.">
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={handleExportSessions} className="gap-1.5">
            <Download className="h-3.5 w-3.5" /> Export Sessions (JSON)
          </Button>
        </div>
        <p className="text-[11px] text-muted-foreground mt-2">
          {sessions.length} session{sessions.length !== 1 ? 's' : ''} available for export
        </p>
      </SettingsSection>

      <SettingsSection title="Clear Local Data" description="Remove all locally stored preferences and cached data.">
        <Button variant="destructive" size="sm" onClick={handleClearLocal} className="gap-1.5">
          <Trash2 className="h-3.5 w-3.5" /> Clear Local Storage
        </Button>
        <p className="text-[11px] text-muted-foreground mt-2">
          This will reset your theme, gateway URL, and other preferences. Session data on the server is not affected.
        </p>
      </SettingsSection>
    </div>
  );
}

/* ── About Tab ──────────────────────────────────────────── */
function AboutTab() {
  const { state } = useConnectionStore();
  const { client } = useConnectionStore();
  const [health, setHealth] = useState<Record<string, unknown> | null>(null);

  useEffect(() => {
    if (!client) return;
    client.request('health.check').then((res) => setHealth(res)).catch(() => setHealth(null));
  }, [client]);

  return (
    <div className="max-w-lg space-y-6">
      <SettingsSection title="OpenClaw" description="Open-source personal AI assistant platform.">
        <div className="space-y-2 text-sm">
          <div className="flex justify-between">
            <span className="text-muted-foreground">Version</span>
            <span className="font-mono text-xs">0.1.0-dev</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Platform</span>
            <span className="font-mono text-xs">Kotlin / Ktor + Koog</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Gateway</span>
            <Badge variant="outline" className={`text-[10px] ${
              state === 'connected' ? 'text-status-success border-status-success-border' : 'text-muted-foreground'
            }`}>
              {state}
            </Badge>
          </div>
        </div>
      </SettingsSection>

      {health && (
        <SettingsSection title="Health Check" description="Gateway service status.">
          <pre className="text-xs font-mono text-muted-foreground bg-muted/50 rounded-md p-3 overflow-auto">
            {JSON.stringify(health, null, 2)}
          </pre>
        </SettingsSection>
      )}

      <SettingsSection title="Links" description="">
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" size="sm" className="gap-1.5 text-xs" asChild>
            <a href="https://github.com/openclaw/openclaw" target="_blank" rel="noopener noreferrer">
              <ExternalLink className="h-3 w-3" /> GitHub
            </a>
          </Button>
        </div>
      </SettingsSection>
    </div>
  );
}

/* ── Shared components ──────────────────────────────────── */
function SettingsSection({ title, description, children }: {
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <h3 className="text-sm font-semibold mb-0.5">{title}</h3>
      {description && <p className="text-xs text-muted-foreground mb-3">{description}</p>}
      {children}
    </div>
  );
}

function ToggleRow({ label, description, checked, onChange }: {
  label: string;
  description: string;
  checked: boolean;
  onChange: () => void;
}) {
  return (
    <div className="flex items-center justify-between py-2">
      <div>
        <p className="text-sm">{label}</p>
        <p className="text-[11px] text-muted-foreground">{description}</p>
      </div>
      <button
        onClick={onChange}
        className={`relative h-5 w-9 rounded-full transition-colors ${checked ? 'bg-primary' : 'bg-muted border border-border'}`}
      >
        <span className={`absolute top-0.5 h-4 w-4 rounded-full bg-white shadow transition-transform ${checked ? 'translate-x-4' : 'translate-x-0.5'}`} />
      </button>
    </div>
  );
}
