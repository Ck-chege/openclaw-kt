import { memo, useEffect, useState, useCallback } from 'react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Check, Copy, Download } from 'lucide-react';

interface CodeBlockProps {
  code: string;
  language?: string;
}

// Lazy-loaded highlighter singleton
let highlighterPromise: Promise<import('shiki').Highlighter> | null = null;

function getHighlighter() {
  if (!highlighterPromise) {
    highlighterPromise = import('shiki').then((shiki) =>
      shiki.createHighlighter({
        themes: ['github-dark-default', 'github-light-default'],
        langs: [
          'javascript', 'typescript', 'python', 'java', 'kotlin', 'rust', 'go',
          'html', 'css', 'json', 'yaml', 'toml', 'xml', 'sql', 'bash', 'shell',
          'markdown', 'dockerfile', 'graphql', 'c', 'cpp', 'swift', 'ruby',
          'php', 'lua', 'zig', 'text',
        ],
      })
    );
  }
  return highlighterPromise;
}

export const CodeBlock = memo(function CodeBlock({ code, language }: CodeBlockProps) {
  const [html, setHtml] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const lang = language?.toLowerCase() || 'text';

  useEffect(() => {
    let cancelled = false;
    getHighlighter().then((highlighter) => {
      if (cancelled) return;
      try {
        const loadedLangs = highlighter.getLoadedLanguages();
        const resolvedLang = loadedLangs.includes(lang as never) ? lang : 'text';
        const result = highlighter.codeToHtml(code, {
          lang: resolvedLang,
          themes: {
            dark: 'github-dark-default',
            light: 'github-light-default',
          },
        });
        setHtml(result);
      } catch {
        // Fallback to plain text
        setHtml(null);
      }
    });
    return () => { cancelled = true; };
  }, [code, lang]);

  const handleCopy = useCallback(() => {
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }, [code]);

  const handleDownload = useCallback(() => {
    const ext = lang === 'typescript' ? 'ts' : lang === 'javascript' ? 'js' : lang === 'python' ? 'py' : lang === 'kotlin' ? 'kt' : lang;
    const blob = new Blob([code], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `code.${ext}`;
    a.click();
    URL.revokeObjectURL(url);
  }, [code, lang]);

  return (
    <div className="group relative rounded-lg border border-border overflow-hidden my-2">
      {/* Header bar */}
      <div className="flex items-center justify-between px-3 py-1.5 bg-muted/50 border-b border-border">
        <Badge variant="outline" className="text-[10px] font-mono font-normal h-5">
          {lang}
        </Badge>
        <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
          <Button
            variant="ghost"
            size="icon"
            className="h-6 w-6"
            onClick={handleDownload}
            title="Download"
          >
            <Download className="h-3 w-3" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-6 w-6"
            onClick={handleCopy}
            title={copied ? 'Copied!' : 'Copy'}
          >
            {copied ? <Check className="h-3 w-3 text-status-success" /> : <Copy className="h-3 w-3" />}
          </Button>
        </div>
      </div>

      {/* Code content */}
      {html ? (
        <div
          className="overflow-x-auto text-xs [&_pre]:!bg-transparent [&_pre]:p-4 [&_pre]:m-0 [&_code]:!bg-transparent bg-code-bg"
          dangerouslySetInnerHTML={{ __html: html }}
        />
      ) : (
        <pre className="overflow-x-auto text-xs p-4 bg-code-bg font-mono">
          <code>{code}</code>
        </pre>
      )}
    </div>
  );
});
