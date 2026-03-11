import { useRef, useState, useCallback } from 'react';
import { Virtuoso, type VirtuosoHandle } from 'react-virtuoso';
import { MessageBubble } from './MessageBubble';
import { Button } from '@/components/ui/button';
import type { ChatMessage } from '@/types/protocol';
import { ArrowDown, FileCode2, Search, Wrench } from 'lucide-react';

interface MessageListProps {
  messages: ChatMessage[];
}

const STARTER_IDEAS = [
  {
    icon: FileCode2,
    title: 'Make a change',
    detail: 'Refactor a component, fix a bug, or wire up a new feature.',
  },
  {
    icon: Search,
    title: 'Review the workspace',
    detail: 'Summarize recent changes or inspect a specific part of the codebase.',
  },
  {
    icon: Wrench,
    title: 'Run tools',
    detail: 'Trace shell commands, file edits, and approvals inside the thread.',
  },
];

export function MessageList({ messages }: MessageListProps) {
  const virtuosoRef = useRef<VirtuosoHandle>(null);
  const [atBottom, setAtBottom] = useState(true);
  const [showScrollButton, setShowScrollButton] = useState(false);

  const handleAtBottomChange = useCallback((bottom: boolean) => {
    setAtBottom(bottom);
    setShowScrollButton(!bottom);
  }, []);

  const scrollToBottom = useCallback(() => {
    virtuosoRef.current?.scrollToIndex({
      index: 'LAST',
      behavior: 'smooth',
    });
  }, []);

  if (messages.length === 0) {
    return (
      <div className="flex flex-1 items-center justify-center px-6 py-10">
        <div className="w-full max-w-2xl text-center">
          <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-muted-foreground">
            Conversation
          </p>
          <h2
            className="mt-3 text-3xl font-semibold tracking-tight text-balance"
            style={{ fontFamily: 'var(--font-display)' }}
          >
            Ask for a change, a review, or a summary.
          </h2>
          <p className="mx-auto mt-3 max-w-xl text-sm leading-6 text-muted-foreground sm:text-[15px]">
            The transcript stays front and center. Tool calls, approvals, and streamed output appear inline as the
            agent works.
          </p>

          <div className="mt-8 grid gap-3 sm:grid-cols-3">
            {STARTER_IDEAS.map(({ icon: Icon, title, detail }) => (
              <div
                key={title}
                className="rounded-[22px] border border-border/60 bg-background/65 p-4 text-left shadow-sm"
              >
                <div className="flex h-9 w-9 items-center justify-center rounded-2xl bg-primary/8 text-primary">
                  <Icon className="h-4 w-4" />
                </div>
                <p className="mt-4 text-sm font-semibold">{title}</p>
                <p className="mt-1 text-xs leading-5 text-muted-foreground">{detail}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="relative flex-1 min-h-0">
      <Virtuoso
        ref={virtuosoRef}
        data={messages}
        atBottomStateChange={handleAtBottomChange}
        followOutput={atBottom ? 'smooth' : false}
        atBottomThreshold={100}
        increaseViewportBy={{ top: 240, bottom: 240 }}
        itemContent={(_, message) => (
          <div className="mx-auto max-w-4xl px-4 py-3 sm:px-6">
            <MessageBubble message={message} />
          </div>
        )}
        components={{
          Header: () => <div className="h-5" />,
          Footer: () => <div className="h-6" />,
        }}
      />

      {showScrollButton && (
        <div className="absolute bottom-4 left-1/2 z-10 -translate-x-1/2">
          <Button
            variant="outline"
            size="sm"
            className="h-10 rounded-full border-border/70 bg-background/92 px-4 shadow-lg backdrop-blur-md"
            onClick={scrollToBottom}
          >
            <ArrowDown className="h-3.5 w-3.5" />
            Jump to bottom
          </Button>
        </div>
      )}
    </div>
  );
}
