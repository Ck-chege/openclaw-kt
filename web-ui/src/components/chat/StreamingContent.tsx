import { memo, useMemo } from 'react';
import { MarkdownContent } from './MarkdownContent';
import { StreamingCursor } from './StreamingCursor';

interface StreamingContentProps {
  content: string;
  startTime?: string;
}

/**
 * During streaming, we split content into completed markdown blocks
 * (memoized) and the in-progress tail (rendered as plain text with cursor).
 * This prevents re-parsing the entire content on every token.
 */
export const StreamingContent = memo(function StreamingContent({ content, startTime }: StreamingContentProps) {
  const { completedBlocks, tail } = useMemo(() => {
    // Split on double-newline to find paragraph boundaries
    const lastDoubleNewline = content.lastIndexOf('\n\n');
    if (lastDoubleNewline === -1) {
      return { completedBlocks: '', tail: content };
    }
    return {
      completedBlocks: content.slice(0, lastDoubleNewline),
      tail: content.slice(lastDoubleNewline + 2),
    };
  }, [content]);

  const elapsed = useMemo(() => {
    if (!startTime) return null;
    const ms = Date.now() - new Date(startTime).getTime();
    return (ms / 1000).toFixed(1);
  }, // Re-compute every render (acceptable since streaming is frequent)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  [startTime, content]);

  return (
    <div>
      {completedBlocks && <MarkdownContent content={completedBlocks} />}
      {tail && (
        <p className="whitespace-pre-wrap text-sm leading-relaxed">
          {tail}
          <StreamingCursor />
        </p>
      )}
      {!content && <StreamingCursor />}
      {elapsed && (
        <span className="text-[11px] text-muted-foreground mt-1 block">
          Thinking... {elapsed}s
        </span>
      )}
    </div>
  );
});
