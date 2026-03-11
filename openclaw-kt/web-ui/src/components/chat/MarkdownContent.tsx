import { memo, useMemo } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { CodeBlock } from './CodeBlock';
import type { Components } from 'react-markdown';

interface MarkdownContentProps {
  content: string;
}

const components: Components = {
  code({ className, children, ...props }) {
    const match = /language-(\w+)/.exec(className || '');
    const node = (props as { node?: { position?: { start?: { column?: number } } } }).node;
    const isBlock = node?.position?.start?.column === 1 || String(children).includes('\n');

    if (match || isBlock) {
      return (
        <CodeBlock
          code={String(children).replace(/\n$/, '')}
          language={match?.[1]}
        />
      );
    }

    // Inline code
    return (
      <code className="px-1.5 py-0.5 rounded bg-code-bg text-code-text text-[13px] font-mono" {...props}>
        {children}
      </code>
    );
  },
  // Don't wrap code blocks in an extra <pre>
  pre({ children }) {
    return <>{children}</>;
  },
  a({ href, children }) {
    return (
      <a
        href={href}
        target="_blank"
        rel="noopener noreferrer"
        className="text-link-text hover:underline"
      >
        {children}
      </a>
    );
  },
  table({ children }) {
    return (
      <div className="overflow-x-auto my-2">
        <table className="min-w-full text-xs border border-border">{children}</table>
      </div>
    );
  },
  th({ children }) {
    return (
      <th className="px-3 py-1.5 text-left font-medium bg-muted/50 border-b border-border">
        {children}
      </th>
    );
  },
  td({ children }) {
    return (
      <td className="px-3 py-1.5 border-b border-border">{children}</td>
    );
  },
};

export const MarkdownContent = memo(function MarkdownContent({ content }: MarkdownContentProps) {
  const memoizedComponents = useMemo(() => components, []);

  return (
    <div className="prose prose-sm dark:prose-invert max-w-none prose-p:my-1.5 prose-headings:mt-3 prose-headings:mb-1.5 prose-li:my-0.5 prose-ul:my-1 prose-ol:my-1 prose-blockquote:my-2 prose-blockquote:border-l-2 prose-blockquote:pl-4 prose-blockquote:text-muted-foreground prose-hr:my-3">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={memoizedComponents}>
        {content}
      </ReactMarkdown>
    </div>
  );
});
