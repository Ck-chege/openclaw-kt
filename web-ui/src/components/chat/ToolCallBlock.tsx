import { memo, useState, useCallback } from 'react';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { formatDuration } from '@/lib/format';
import {
  ChevronRight,
  Wrench,
  Loader2,
  Check,
  AlertCircle,
  Terminal,
  FileText,
  Globe,
  Search,
  Pencil,
  Copy,
} from 'lucide-react';
import type { ToolCallInfo } from '@/types/protocol';

interface ToolCallBlockProps {
  toolCall: ToolCallInfo;
}

// Map tool names to specific icons
function getToolIcon(name: string) {
  const n = name.toLowerCase();
  if (n.includes('exec') || n.includes('process') || n === 'shell') return <Terminal className="h-3.5 w-3.5" />;
  if (n.includes('read') || n.includes('file')) return <FileText className="h-3.5 w-3.5" />;
  if (n.includes('write') || n.includes('create')) return <Pencil className="h-3.5 w-3.5" />;
  if (n.includes('edit')) return <Pencil className="h-3.5 w-3.5" />;
  if (n.includes('web_search') || n.includes('search')) return <Search className="h-3.5 w-3.5" />;
  if (n.includes('web_fetch') || n.includes('fetch') || n.includes('http')) return <Globe className="h-3.5 w-3.5" />;
  return <Wrench className="h-3.5 w-3.5" />;
}

function getStatusColor(status: ToolCallInfo['status']) {
  switch (status) {
    case 'running': return 'text-status-warning';
    case 'error': return 'text-status-error';
    case 'complete': return 'text-status-success';
  }
}

export const ToolCallBlock = memo(function ToolCallBlock({ toolCall }: ToolCallBlockProps) {
  const [open, setOpen] = useState(false);
  const [copiedResult, setCopiedResult] = useState(false);

  const handleCopyResult = useCallback(() => {
    if (toolCall.result) {
      navigator.clipboard.writeText(toolCall.result);
      setCopiedResult(true);
      setTimeout(() => setCopiedResult(false), 2000);
    }
  }, [toolCall.result]);

  const isExec = toolCall.name.toLowerCase().includes('exec') || toolCall.name.toLowerCase().includes('process');

  const statusIcon =
    toolCall.status === 'running' ? (
      <Loader2 className={`h-3.5 w-3.5 animate-spin ${getStatusColor('running')}`} />
    ) : toolCall.status === 'error' ? (
      <AlertCircle className={`h-3.5 w-3.5 ${getStatusColor('error')}`} />
    ) : (
      <Check className={`h-3.5 w-3.5 ${getStatusColor('complete')}`} />
    );

  // Extract file path from args if present (for read/write/edit tools)
  const filePath = typeof toolCall.args === 'object'
    ? (toolCall.args as Record<string, unknown>).path || (toolCall.args as Record<string, unknown>).file_path || (toolCall.args as Record<string, unknown>).filePath
    : null;

  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <CollapsibleTrigger className="flex items-center gap-2 text-xs text-muted-foreground hover:text-foreground transition-colors w-full text-left py-1.5 px-2.5 rounded-md bg-muted/50 hover:bg-muted border border-transparent hover:border-border">
        <ChevronRight
          className={`h-3 w-3 transition-transform flex-shrink-0 ${open ? 'rotate-90' : ''}`}
        />
        {getToolIcon(toolCall.name)}
        <span className="font-mono font-medium">{toolCall.name}</span>

        {/* File path badge */}
        {filePath && typeof filePath === 'string' && (
          <Badge variant="outline" className="text-[10px] font-mono font-normal h-4 max-w-[200px] truncate">
            {filePath}
          </Badge>
        )}

        <div className="flex items-center gap-1.5 ml-auto">
          {statusIcon}
          {toolCall.durationMs !== undefined && (
            <Badge variant="outline" className="text-[10px] font-normal h-4">
              {formatDuration(toolCall.durationMs)}
            </Badge>
          )}
        </div>
      </CollapsibleTrigger>

      <CollapsibleContent>
        <div className="ml-5 mt-1.5 space-y-2 text-xs">
          {/* Args */}
          {Object.keys(toolCall.args).length > 0 && (
            <div>
              <span className="text-muted-foreground font-medium">Args</span>
              <pre className="mt-1 p-3 rounded-md bg-code-bg border border-border overflow-x-auto text-xs font-mono leading-relaxed">
                {JSON.stringify(toolCall.args, null, 2)}
              </pre>
            </div>
          )}

          {/* Result */}
          {toolCall.result !== undefined && (
            <div>
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground font-medium">Result</span>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-5 w-5"
                  onClick={handleCopyResult}
                >
                  {copiedResult ? (
                    <Check className="h-3 w-3 text-status-success" />
                  ) : (
                    <Copy className="h-3 w-3" />
                  )}
                </Button>
              </div>
              <pre className={`mt-1 p-3 rounded-md border border-border overflow-x-auto text-xs font-mono max-h-60 overflow-y-auto leading-relaxed ${
                isExec
                  ? 'bg-[oklch(0.15_0_0)] text-[oklch(0.85_0.10_155)] dark:bg-[oklch(0.12_0_0)]'
                  : 'bg-code-bg'
              }`}>
                {toolCall.result}
              </pre>
            </div>
          )}
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
});
