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
import { formatTimestamp, formatTokens } from '@/lib/format';
import { cn } from '@/lib/utils';
import { Bot, Check, Copy, Sparkles, User } from 'lucide-react';

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

  if (message.role === 'approval' && message.approval) {
    return <ApprovalBubble message={message} />;
  }

  return (
    <div className={cn('group flex w-full gap-3 sm:gap-4', isUser ? 'justify-end' : 'justify-start')}>
      {!isUser && (
        <div className="mt-1 flex-shrink-0">
          <div className="flex h-9 w-9 items-center justify-center rounded-2xl bg-[linear-gradient(145deg,rgba(17,24,39,0.96),rgba(15,118,110,0.9))] text-white shadow-[0_16px_30px_-22px_rgba(13,148,136,0.95)]">
            <Bot className="h-4 w-4" />
          </div>
        </div>
      )}

      <div className={cn('flex min-w-0 flex-col gap-2', isUser ? 'items-end' : 'items-start')}>
        <div className="flex flex-wrap items-center gap-2 px-1">
          <span className="text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
            {isUser ? 'You' : 'KClaw'}
          </span>
          <span className="text-[11px] text-muted-foreground">{formatTimestamp(message.timestamp)}</span>
          {!isUser && message.isStreaming && (
            <Badge
              variant="outline"
              className="gap-1 rounded-full border-primary/20 bg-primary/5 px-2 py-0.5 text-[10px] text-primary"
            >
              <Sparkles className="h-3 w-3" />
              Streaming
            </Badge>
          )}
        </div>

        {!isUser && message.toolCalls && message.toolCalls.length > 0 && (
          <ToolCallGroup toolCalls={message.toolCalls} />
        )}

        {(message.content || message.isStreaming) && (
          <div
            className={cn(
              'relative overflow-hidden rounded-[24px] border px-4 py-3.5 text-sm shadow-sm',
              isUser
                ? 'max-w-[min(42rem,82vw)] border-teal-700/20 bg-[linear-gradient(145deg,rgba(17,24,39,0.98),rgba(15,118,110,0.92))] text-white shadow-[0_24px_60px_-36px_rgba(15,118,110,0.75)]'
                : 'max-w-[min(48rem,86vw)] border-border/70 bg-background/90 text-foreground shadow-[0_18px_48px_-42px_rgba(15,23,42,0.6)] backdrop-blur-sm'
            )}
          >
            {!isUser && (
              <div className="pointer-events-none absolute inset-x-5 top-0 h-px bg-gradient-to-r from-transparent via-primary/30 to-transparent" />
            )}

            {isUser ? (
              <p className="whitespace-pre-wrap text-[14.5px] leading-7 text-white/96">{message.content}</p>
            ) : message.isStreaming ? (
              <StreamingContent content={message.content} startTime={message.timestamp} />
            ) : (
              <MarkdownContent content={message.content} />
            )}

            {message.content && !message.isStreaming && (
              <div className="absolute right-3 top-3 opacity-0 transition-opacity group-hover:opacity-100">
                <Tooltip>
                  <TooltipTrigger asChild>
                    <Button
                      variant="outline"
                      size="icon-xs"
                      className={cn(
                        'rounded-full shadow-sm backdrop-blur',
                        isUser
                          ? 'border-white/15 bg-white/10 text-white hover:bg-white/15 hover:text-white'
                          : 'border-border/70 bg-background/90'
                      )}
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

        {!isUser && !message.isStreaming && (message.usage || message.model) && (
          <div className="flex flex-wrap items-center gap-2 px-1">
            {message.model && (
              <Badge
                variant="outline"
                className="rounded-full border-border/70 bg-background/80 px-2.5 py-1 text-[11px] font-normal text-muted-foreground"
              >
                {message.model}
              </Badge>
            )}
            {message.usage && (
              <Badge
                variant="outline"
                className="rounded-full border-border/70 bg-background/80 px-2.5 py-1 text-[11px] font-normal text-muted-foreground"
              >
                {formatTokens(message.usage.totalTokens)} tokens
              </Badge>
            )}
          </div>
        )}
      </div>

      {isUser && (
        <div className="mt-1 flex-shrink-0">
          <div className="flex h-9 w-9 items-center justify-center rounded-2xl border border-border/60 bg-background/90 text-foreground shadow-sm backdrop-blur-sm">
            <User className="h-4 w-4" />
          </div>
        </div>
      )}
    </div>
  );
});

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
          className="flex items-center gap-2 rounded-full border border-border/60 bg-background/80 px-3 py-1.5 text-xs text-muted-foreground transition-colors hover:bg-background hover:text-foreground"
        >
          Used {toolCalls.length} tools
          {runningCount > 0 && (
            <Badge variant="outline" className="h-4 rounded-full text-[10px]">
              {runningCount} running
            </Badge>
          )}
        </button>
      )}
      {expanded &&
        toolCalls.map((tc, i) => (
          <ToolCallBlock key={`${tc.name}-${i}`} toolCall={tc} />
        ))}
      {expanded && toolCalls.length > 3 && (
        <button
          onClick={() => setExpanded(false)}
          className="px-2 text-[11px] text-muted-foreground transition-colors hover:text-foreground"
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
