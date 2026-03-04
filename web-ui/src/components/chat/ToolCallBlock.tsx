import { useState } from 'react';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { Badge } from '@/components/ui/badge';
import { formatDuration } from '@/lib/format';
import { ChevronRight, Wrench, Loader2, Check, AlertCircle } from 'lucide-react';
import type { ToolCallInfo } from '@/types/protocol';

interface ToolCallBlockProps {
  toolCall: ToolCallInfo;
}

export function ToolCallBlock({ toolCall }: ToolCallBlockProps) {
  const [open, setOpen] = useState(false);

  const statusIcon =
    toolCall.status === 'running' ? (
      <Loader2 className="h-3 w-3 animate-spin text-status-warning" />
    ) : toolCall.status === 'error' ? (
      <AlertCircle className="h-3 w-3 text-status-error" />
    ) : (
      <Check className="h-3 w-3 text-status-success" />
    );

  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <CollapsibleTrigger className="flex items-center gap-2 text-xs text-muted-foreground hover:text-foreground transition-colors w-full text-left py-1 px-2 rounded bg-muted/50 hover:bg-muted">
        <ChevronRight
          className={`h-3 w-3 transition-transform ${open ? 'rotate-90' : ''}`}
        />
        <Wrench className="h-3 w-3" />
        <span className="font-mono">{toolCall.name}</span>
        {statusIcon}
        {toolCall.durationMs !== undefined && (
          <Badge variant="outline" className="text-[11px] ml-auto font-normal">
            {formatDuration(toolCall.durationMs)}
          </Badge>
        )}
      </CollapsibleTrigger>
      <CollapsibleContent>
        <div className="ml-5 mt-1 space-y-1.5 text-xs">
          {Object.keys(toolCall.args).length > 0 && (
            <div>
              <span className="text-muted-foreground">Args:</span>
              <pre className="mt-0.5 p-2 rounded bg-code-bg border border-border overflow-x-auto text-xs font-mono">
                {JSON.stringify(toolCall.args, null, 2)}
              </pre>
            </div>
          )}
          {toolCall.result !== undefined && (
            <div>
              <span className="text-muted-foreground">Result:</span>
              <pre className="mt-0.5 p-2 rounded bg-code-bg border border-border overflow-x-auto text-xs font-mono max-h-40 overflow-y-auto">
                {toolCall.result}
              </pre>
            </div>
          )}
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
}
