import { MessageList } from './MessageList';
import { ChatInput } from './ChatInput';
import { useChatStore } from '@/stores/chat-store';
import { useDashboardStore } from '@/stores/dashboard-store';
import { useConnectionStore } from '@/stores/connection-store';

export function ChatView() {
  const { messages, isStreaming, sendMessage, stopAgent } = useChatStore();
  const { selectedModelId, models } = useDashboardStore();
  const { client, state } = useConnectionStore();

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
    <div className="flex flex-col flex-1 min-h-0">
      <MessageList messages={messages} />
      <ChatInput
        onSend={handleSend}
        onStop={handleStop}
        isStreaming={isStreaming}
        disabled={state !== 'connected'}
      />
    </div>
  );
}
