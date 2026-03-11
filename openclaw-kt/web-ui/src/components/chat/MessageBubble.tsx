import { memo, useState, useCallback } from 'react';
import type { ChatMessage } from '@/types/protocol';
import { MarkdownContent } from './MarkdownContent';
import { StreamingContent } from './StreamingContent';
import { ToolCallBlock } from './ToolCallBlock';
import { ApprovalCard } from './ApprovalCard';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { useChatStore } from '@/stores/chat-store';
import { useConnectionStore } from '@/stores/connection-store';
import { formatTokens } from '@/lib/format';
import { User, Bot, Copy, Check } from 'lucide-react';

interface MessageBubbleProps {
  message: ChatMessage;
}

export const MessageBubble = memo(function MessageBubble({ message }: MessageBubbleProps) {
  const isUser = message.role === 'user';
  const [copied, setCopied] = useState(false);

  const handleCopy = useCallback(() => {
    navigator.clipboard.writeText(message.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }, [message.content]);

  // Approval messages
  if (message.role === 'approval' && message.approval) {
    return <ApprovalBubble message={message} />;
  }

  return (
    <div className={`group flex gap-3 ${isUser ? 'justify-end' : 'justify-start'}`}>
      {/* Assistant avatar */}
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
          <ToolCallGroup toolCalls={message.toolCalls} />
        )}

        {/* Message content */}
        {(message.content || message.isStreaming) && (
          <div
            className={`relative rounded-lg px-4 py-2.5 text-sm ${
              isUser
                ? 'bg-primary text-primary-foreground'
                : 'bg-muted'
            }`}
          >
            {isUser ? (
              <p className="whitespace-pre-wrap">{message.content}</p>
            ) : message.isStreaming ? (
              <StreamingContent content={message.content} startTime={message.timestamp} />
            ) : (
              <MarkdownContent content={message.content} />
            )}

            {/* Hover action: Copy */}
            {message.content && !message.isStreaming && (
              <div className="absolute -top-3 right-2 opacity-0 group-hover:opacity-100 transition-opacity">
                <Tooltip>
                  <TooltipTrigger asChild>
                    <Button
                      variant="outline"
                      size="icon"
                      className="h-6 w-6 bg-background shadow-sm"
                      onClick={handleCopy}
                    >
                      {copied ? (
                        <Check className="h-3 w-3 text-status-success" />
                      ) : (
                        <Copy className="h-3 w-3" />
                      )}
                    </Button>
                  </TooltipTrigger>
                  <TooltipContent side="top" className="text-xs">
                    {copied ? 'Copied!' : 'Copy message'}
                  </TooltipContent>
                </Tooltip>
              </div>
            )}
          </div>
        )}

        {/* Metadata badges */}
        {!isUser && !message.isStreaming && (message.usage || message.model) && (
          <div className="flex items-center gap-2">
            {message.model && (
              <Badge variant="outline" className="text-[11px] font-normal opacity-60">
                {message.model}
              </Badge>
            )}
            {message.usage && (
              <Badge variant="outline" className="text-[11px] font-normal opacity-60">
                {formatTokens(message.usage.totalTokens)} tokens
              </Badge>
            )}
          </div>
        )}
      </div>

      {/* User avatar */}
      {isUser && (
        <div className="flex-shrink-0 mt-1">
          <div className="h-7 w-7 rounded-full bg-primary flex items-center justify-center">
            <User className="h-4 w-4 text-primary-foreground" />
          </div>
        </div>
      )}
    </div>
  );
});

// Group 3+ consecutive tool calls under a collapsible
function ToolCallGroup({ toolCalls }: { toolCalls: NonNullable<ChatMessage['toolCalls']> }) {
  const [expanded, setExpanded] = useState(toolCalls.length <= 3);

  if (toolCalls.length <= 3) {
    return (
      <div className="space-y-1.5">
        {toolCalls.map((tc, i) => (
          <ToolCallBlock key={`${tc.name}-${i}`} toolCall={tc} />
        ))}
      </div>
    );
  }

  const runningCount = toolCalls.filter((tc) => tc.status === 'running').length;

  return (
    <div className="space-y-1.5">
      {!expanded && (
        <button
          onClick={() => setExpanded(true)}
          className="flex items-center gap-2 text-xs text-muted-foreground hover:text-foreground transition-colors py-1 px-2 rounded bg-muted/50 hover:bg-muted"
        >
          Used {toolCalls.length} tools
          {runningCount > 0 && (
            <Badge variant="outline" className="text-[10px] h-4">
              {runningCount} running
            </Badge>
          )}
        </button>
      )}
      {expanded && toolCalls.map((tc, i) => (
        <ToolCallBlock key={`${tc.name}-${i}`} toolCall={tc} />
      ))}
      {expanded && toolCalls.length > 3 && (
        <button
          onClick={() => setExpanded(false)}
          className="text-[11px] text-muted-foreground hover:text-foreground transition-colors px-2"
        >
          Collapse tools
        </button>
      )}
    </div>
  );
}

function ApprovalBubble({ message }: { message: ChatMessage }) {
  const { respondToApproval } = useChatStore();
  const { client } = useConnectionStore();

  const handleRespond = useCallback((requestId: string, approved: boolean) => {
    if (!client) return;
    respondToApproval(client, requestId, approved);
  }, [client, respondToApproval]);

  return <ApprovalCard approval={message.approval!} onRespond={handleRespond} />;
}
