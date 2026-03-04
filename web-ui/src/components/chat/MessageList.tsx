import { useRef, useState, useCallback } from 'react';
import { Virtuoso, type VirtuosoHandle } from 'react-virtuoso';
import { MessageBubble } from './MessageBubble';
import { Button } from '@/components/ui/button';
import type { ChatMessage } from '@/types/protocol';
import { MessageSquare, ArrowDown } from 'lucide-react';

interface MessageListProps {
  messages: ChatMessage[];
}

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
      <div className="flex-1 flex items-center justify-center">
        <div className="text-center text-muted-foreground">
          <MessageSquare className="h-12 w-12 mx-auto mb-4 opacity-30" />
          <p className="text-lg font-medium">No messages yet</p>
          <p className="text-sm">Send a message to start a conversation</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 relative min-h-0">
      <Virtuoso
        ref={virtuosoRef}
        data={messages}
        atBottomStateChange={handleAtBottomChange}
        followOutput={atBottom ? 'smooth' : false}
        atBottomThreshold={100}
        increaseViewportBy={{ top: 200, bottom: 200 }}
        itemContent={(_, message) => (
          <div className="max-w-3xl mx-auto px-4 py-2">
            <MessageBubble message={message} />
          </div>
        )}
        components={{
          Header: () => <div className="h-4" />,
          Footer: () => <div className="h-4" />,
        }}
      />

      {/* Scroll to bottom FAB */}
      {showScrollButton && (
        <div className="absolute bottom-4 left-1/2 -translate-x-1/2 z-10">
          <Button
            variant="outline"
            size="sm"
            className="rounded-full shadow-lg gap-1.5 bg-background/95 backdrop-blur-sm"
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
