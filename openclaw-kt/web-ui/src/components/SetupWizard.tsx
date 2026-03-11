import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useConnectionStore } from '@/stores/connection-store';
import { notify } from '@/lib/notifications';
import { Settings, Key, CheckCircle, ArrowLeft, ArrowRight, Loader2 } from 'lucide-react';

type Step = 'welcome' | 'api-keys' | 'confirm';
const STEPS: Step[] = ['welcome', 'api-keys', 'confirm'];

const MODELS = [
  { value: 'claude-sonnet-4-20250514', label: 'Claude Sonnet 4' },
  { value: 'claude-opus-4-20250514', label: 'Claude Opus 4' },
  { value: 'gpt-4o', label: 'GPT-4o' },
  { value: 'gpt-4o-mini', label: 'GPT-4o Mini' },
];

export function SetupWizard() {
  const { client, clearSetupRequired } = useConnectionStore();
  const [step, setStep] = useState<Step>('welcome');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Form state
  const [anthropicKey, setAnthropicKey] = useState('');
  const [openaiKey, setOpenaiKey] = useState('');
  const [defaultModel, setDefaultModel] = useState('claude-sonnet-4-20250514');

  const stepIndex = STEPS.indexOf(step);
  const isFirst = stepIndex === 0;
  const isLast = stepIndex === STEPS.length - 1;

  const canAdvance = (): boolean => {
    if (step === 'api-keys') {
      return anthropicKey.trim().length > 0 || openaiKey.trim().length > 0;
    }
    return true;
  };

  const goNext = () => {
    if (!isLast && canAdvance()) {
      setStep(STEPS[stepIndex + 1]);
      setError(null);
    }
  };

  const goBack = () => {
    if (!isFirst) {
      setStep(STEPS[stepIndex - 1]);
      setError(null);
    }
  };

  const handleComplete = async () => {
    if (!client) return;
    setSubmitting(true);
    setError(null);

    try {
      const params: Record<string, unknown> = {};
      if (anthropicKey.trim()) params.anthropicKey = anthropicKey.trim();
      if (openaiKey.trim()) params.openaiKey = openaiKey.trim();
      params.model = defaultModel;

      await client.request('setup.configure', params);
      await client.request('setup.complete', {});

      clearSetupRequired();
      notify.success('Setup complete! Welcome to KClaw.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Setup failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex h-screen w-screen items-center justify-center bg-background">
      <div className="w-[480px] p-8 rounded-xl border border-border bg-card shadow-lg">
        {/* Step indicator */}
        <div className="flex justify-center gap-2 mb-8">
          {STEPS.map((s, i) => (
            <div
              key={s}
              className={`h-2 w-8 rounded-full transition-colors ${
                i <= stepIndex ? 'bg-primary' : 'bg-muted'
              }`}
            />
          ))}
        </div>

        {/* Step content */}
        <div className="min-h-[280px]">
          {step === 'welcome' && <WelcomeStep />}
          {step === 'api-keys' && (
            <ApiKeysStep
              anthropicKey={anthropicKey}
              openaiKey={openaiKey}
              onAnthropicChange={setAnthropicKey}
              onOpenaiChange={setOpenaiKey}
            />
          )}
          {step === 'confirm' && (
            <ConfirmStep
              hasAnthropicKey={anthropicKey.trim().length > 0}
              hasOpenaiKey={openaiKey.trim().length > 0}
              defaultModel={defaultModel}
              onModelChange={setDefaultModel}
            />
          )}
        </div>

        {/* Error */}
        {error && (
          <p className="text-sm text-destructive mt-2">{error}</p>
        )}

        {/* Navigation */}
        <div className="flex justify-between mt-6">
          <Button
            variant="ghost"
            onClick={goBack}
            disabled={isFirst || submitting}
            className="gap-1"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </Button>

          {isLast ? (
            <Button
              onClick={handleComplete}
              disabled={submitting || !canAdvance()}
              className="gap-1"
            >
              {submitting ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <CheckCircle className="h-4 w-4" />
              )}
              Complete Setup
            </Button>
          ) : (
            <Button
              onClick={goNext}
              disabled={!canAdvance()}
              className="gap-1"
            >
              Next
              <ArrowRight className="h-4 w-4" />
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}

function WelcomeStep() {
  return (
    <div className="flex flex-col items-center gap-4 text-center">
      <div className="h-14 w-14 rounded-full bg-primary/10 flex items-center justify-center">
        <Settings className="h-7 w-7 text-primary" />
      </div>
      <h2 className="text-2xl font-semibold">Welcome to KClaw</h2>
      <p className="text-muted-foreground leading-relaxed">
        Your personal AI assistant gateway needs a quick setup before you can start.
        This wizard will help you configure your API keys and default model.
      </p>
      <p className="text-sm text-muted-foreground">
        This only takes a minute.
      </p>
    </div>
  );
}

function ApiKeysStep({
  anthropicKey,
  openaiKey,
  onAnthropicChange,
  onOpenaiChange,
}: {
  anthropicKey: string;
  openaiKey: string;
  onAnthropicChange: (v: string) => void;
  onOpenaiChange: (v: string) => void;
}) {
  return (
    <div className="space-y-5">
      <div className="flex items-center gap-3 mb-2">
        <div className="h-10 w-10 rounded-full bg-primary/10 flex items-center justify-center">
          <Key className="h-5 w-5 text-primary" />
        </div>
        <div>
          <h2 className="text-lg font-semibold">API Keys</h2>
          <p className="text-sm text-muted-foreground">At least one key is required.</p>
        </div>
      </div>

      <div className="space-y-2">
        <label className="text-sm font-medium">Anthropic API Key</label>
        <Input
          type="password"
          value={anthropicKey}
          onChange={(e) => onAnthropicChange(e.target.value)}
          placeholder="sk-ant-..."
          className="h-10"
        />
      </div>

      <div className="space-y-2">
        <label className="text-sm font-medium">OpenAI API Key</label>
        <Input
          type="password"
          value={openaiKey}
          onChange={(e) => onOpenaiChange(e.target.value)}
          placeholder="sk-..."
          className="h-10"
        />
      </div>

      {!anthropicKey.trim() && !openaiKey.trim() && (
        <p className="text-sm text-amber-500">
          Enter at least one API key to continue.
        </p>
      )}
    </div>
  );
}

function ConfirmStep({
  hasAnthropicKey,
  hasOpenaiKey,
  defaultModel,
  onModelChange,
}: {
  hasAnthropicKey: boolean;
  hasOpenaiKey: boolean;
  defaultModel: string;
  onModelChange: (v: string) => void;
}) {
  return (
    <div className="space-y-5">
      <div className="flex items-center gap-3 mb-2">
        <div className="h-10 w-10 rounded-full bg-primary/10 flex items-center justify-center">
          <CheckCircle className="h-5 w-5 text-primary" />
        </div>
        <div>
          <h2 className="text-lg font-semibold">Confirm Setup</h2>
          <p className="text-sm text-muted-foreground">Review and choose your default model.</p>
        </div>
      </div>

      {/* Summary */}
      <div className="rounded-lg border border-border p-4 space-y-2 text-sm">
        <div className="flex justify-between">
          <span className="text-muted-foreground">Anthropic API Key</span>
          <span className={hasAnthropicKey ? 'text-green-500' : 'text-muted-foreground'}>
            {hasAnthropicKey ? 'Configured' : 'Not set'}
          </span>
        </div>
        <div className="flex justify-between">
          <span className="text-muted-foreground">OpenAI API Key</span>
          <span className={hasOpenaiKey ? 'text-green-500' : 'text-muted-foreground'}>
            {hasOpenaiKey ? 'Configured' : 'Not set'}
          </span>
        </div>
      </div>

      {/* Model picker */}
      <div className="space-y-2">
        <label className="text-sm font-medium">Default Model</label>
        <select
          value={defaultModel}
          onChange={(e) => onModelChange(e.target.value)}
          className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          {MODELS.map((m) => (
            <option key={m.value} value={m.value}>
              {m.label}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}
