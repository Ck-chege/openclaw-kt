import { MessageList } from './MessageList';
import { ChatInput } from './ChatInput';
import { useChatStore } from '@/stores/chat-store';
import { useDashboardStore } from '@/stores/dashboard-store';
import { useConnectionStore } from '@/stores/connection-store';
import { Badge } from '@/components/ui/badge';
import { Bot, Cpu, MessagesSquare, Radio } from 'lucide-react';
import { formatSessionKey } from '@/lib/format';

export function ChatView() {
  const { messages, isStreaming, sendMessage, stopAgent, activeSessionKey, error } = useChatStore();
  const { selectedModelId, models } = useDashboardStore();
  const { client, state } = useConnectionStore();
  const activeModel = models.find((model) => model.id === selectedModelId) || models[0];

  const handleSend = async (text: string) => {
    if (!client) return;
    const modelId = selectedModelId || models[0]?.id;
    await sendMessage(client, text, modelId);
  };

  const handleStop = async () => {
    if (!client) return;
    await stopAgent(client);
  };

  return (
    <div className="relative flex flex-1 min-h-0 overflow-hidden bg-[radial-gradient(circle_at_top,rgba(14,165,233,0.08),transparent_30%),linear-gradient(180deg,rgba(251,253,255,1)_0%,rgba(246,248,251,1)_100%)] dark:bg-[radial-gradient(circle_at_top,rgba(45,212,191,0.08),transparent_28%),linear-gradient(180deg,rgba(9,14,20,1)_0%,rgba(7,10,15,1)_100%)]">
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute left-[-7rem] top-[-6rem] h-56 w-56 rounded-full bg-sky-400/10 blur-3xl dark:bg-sky-500/8" />
        <div className="absolute bottom-[-8rem] right-[-4rem] h-64 w-64 rounded-full bg-emerald-300/12 blur-3xl dark:bg-emerald-400/8" />
      </div>

      <div className="relative mx-auto flex w-full max-w-5xl flex-1 min-h-0 flex-col px-3 py-3 sm:px-5 sm:py-5">
        <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-[30px] border border-border/60 bg-background/82 shadow-[0_28px_90px_-52px_rgba(15,23,42,0.6)] backdrop-blur-xl">
          <section className="border-b border-border/60 px-4 py-4 sm:px-5">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
              <div className="min-w-0">
                <div className="flex items-center gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-[linear-gradient(145deg,rgba(17,24,39,0.96),rgba(15,118,110,0.9))] text-white shadow-[0_16px_30px_-22px_rgba(13,148,136,0.9)]">
                    <Bot className="h-4 w-4" />
                  </div>
                  <div className="min-w-0">
                    <h1
                      className="truncate text-lg font-semibold tracking-tight sm:text-xl"
                      style={{ fontFamily: 'var(--font-display)' }}
                    >
                      {formatSessionKey(activeSessionKey)}
                    </h1>
                    <p className="text-sm text-muted-foreground">
                      {isStreaming ? 'Responding live' : messages.length > 0 ? 'Conversation active' : 'Ready for a new prompt'}
                    </p>
                  </div>
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <HeaderBadge
                  icon={Radio}
                  label={state === 'connected' ? 'Live' : state === 'connecting' ? 'Connecting' : 'Offline'}
                />
                <HeaderBadge icon={Cpu} label={activeModel?.displayName || 'No model'} />
                <HeaderBadge icon={MessagesSquare} label={`${messages.length} messages`} />
              </div>
            </div>

            {error && (
              <div className="mt-3 rounded-2xl border border-status-error-border bg-status-error-bg px-3 py-2 text-sm text-status-error">
                {error}
              </div>
            )}
          </section>

          <MessageList messages={messages} />

          <ChatInput
            onSend={handleSend}
            onStop={handleStop}
            isStreaming={isStreaming}
            disabled={state !== 'connected' || models.length === 0}
          />
        </div>
      </div>
    </div>
  );
}

function HeaderBadge({
  icon: Icon,
  label,
}: {
  icon: typeof Bot;
  label: string;
}) {
  return (
    <Badge
      variant="outline"
      className="h-8 gap-1.5 rounded-full border-border/70 bg-background/75 px-3 text-xs font-normal text-muted-foreground"
    >
      <Icon className="h-3.5 w-3.5" />
      {label}
    </Badge>
  );
}
