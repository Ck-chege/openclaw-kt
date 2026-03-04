import type { ChatMessage } from '@/types/protocol';
import { MarkdownContent } from './MarkdownContent';
import { ToolCallBlock } from './ToolCallBlock';
import { Badge } from '@/components/ui/badge';
import { formatTokens } from '@/lib/format';
import { User, Bot, Loader2 } from 'lucide-react';

interface MessageBubbleProps {
  message: ChatMessage;
}

export function MessageBubble({ message }: MessageBubbleProps) {
  const isUser = message.role === 'user';

  return (
    <div className={`flex gap-3 ${isUser ? 'justify-end' : 'justify-start'}`}>
      {!isUser && (
        <div className="flex-shrink-0 mt-1">
          <div className="h-7 w-7 rounded-full bg-primary/10 flex items-center justify-center">
            <Bot className="h-4 w-4 text-primary" />
          </div>
        </div>
      )}

      <div className={`max-w-[85%] space-y-2 ${isUser ? 'items-end' : 'items-start'}`}>
        {/* Tool calls before content */}
        {!isUser && message.toolCalls && message.toolCalls.length > 0 && (
          <div className="space-y-1.5">
            {message.toolCalls.map((tc, i) => (
              <ToolCallBlock key={`${tc.name}-${i}`} toolCall={tc} />
            ))}
          </div>
        )}

        {/* Message content */}
        {message.content && (
          <div
            className={`rounded-lg px-4 py-2.5 text-sm ${
              isUser
                ? 'bg-primary text-primary-foreground'
                : 'bg-muted'
            }`}
          >
            {isUser ? (
              <p className="whitespace-pre-wrap">{message.content}</p>
            ) : message.isStreaming ? (
              <div className="flex items-start gap-2">
                <p className="whitespace-pre-wrap flex-1">{message.content || ''}</p>
                <Loader2 className="h-4 w-4 animate-spin flex-shrink-0 mt-0.5 opacity-50" />
              </div>
            ) : (
              <MarkdownContent content={message.content} />
            )}
          </div>
        )}

        {/* Usage badge */}
        {!isUser && message.usage && !message.isStreaming && (
          <div className="flex items-center gap-2">
            {message.model && (
              <Badge variant="outline" className="text-[11px] font-normal opacity-60">
                {message.model}
              </Badge>
            )}
            <Badge variant="outline" className="text-[11px] font-normal opacity-60">
              {formatTokens(message.usage.totalTokens)} tokens
            </Badge>
          </div>
        )}
      </div>

      {isUser && (
        <div className="flex-shrink-0 mt-1">
          <div className="h-7 w-7 rounded-full bg-primary flex items-center justify-center">
            <User className="h-4 w-4 text-primary-foreground" />
          </div>
        </div>
      )}
    </div>
  );
}
