import { memo, useState, useEffect } from 'react';
import type { ApprovalRequest } from '@/types/protocol';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Shield, Check, X, Clock, Terminal, AlertTriangle } from 'lucide-react';

interface ApprovalCardProps {
  approval: ApprovalRequest;
  onRespond: (requestId: string, approved: boolean) => void;
}

export const ApprovalCard = memo(function ApprovalCard({ approval, onRespond }: ApprovalCardProps) {
  const isPending = approval.status === 'pending';
  const [remainingMs, setRemainingMs] = useState<number | null>(null);

  // Countdown timer
  useEffect(() => {
    if (!isPending || !approval.timeoutMs) return;
    const deadline = new Date(approval.requestedAt).getTime() + approval.timeoutMs;

    const tick = () => {
      const remaining = deadline - Date.now();
      if (remaining <= 0) {
        setRemainingMs(0);
      } else {
        setRemainingMs(remaining);
      }
    };

    tick();
    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [isPending, approval.timeoutMs, approval.requestedAt]);

  const statusConfig = {
    pending: { label: 'Pending', className: 'text-status-warning border-status-warning-border bg-status-warning-bg', icon: <Clock className="h-3 w-3" /> },
    approved: { label: 'Approved', className: 'text-status-success border-status-success-border bg-status-success-bg', icon: <Check className="h-3 w-3" /> },
    denied: { label: 'Denied', className: 'text-status-error border-status-error-border bg-status-error-bg', icon: <X className="h-3 w-3" /> },
    expired: { label: 'Expired', className: 'text-muted-foreground', icon: <Clock className="h-3 w-3" /> },
  }[approval.status];

  const argsStr = Object.entries(approval.args)
    .map(([k, v]) => `${k}: ${typeof v === 'string' ? v : JSON.stringify(v)}`)
    .join('\n');

  return (
    <div className="flex gap-3 justify-start">
      <div className="flex-shrink-0 mt-1">
        <div className="h-7 w-7 rounded-full bg-status-warning-bg flex items-center justify-center">
          <Shield className="h-4 w-4 text-status-warning" />
        </div>
      </div>

      <div className="max-w-[85%]">
        <div className={`rounded-lg border p-3.5 ${isPending ? 'border-status-warning-border bg-status-warning-bg/30' : 'border-border bg-muted/30'}`}>
          {/* Header */}
          <div className="flex items-center gap-2 mb-2">
            <AlertTriangle className="h-3.5 w-3.5 text-status-warning flex-shrink-0" />
            <span className="text-xs font-medium">Approval Required</span>
            <Badge variant="outline" className={`text-[10px] gap-1 ml-auto ${statusConfig.className}`}>
              {statusConfig.icon}
              {statusConfig.label}
            </Badge>
          </div>

          {/* Tool info */}
          <div className="flex items-center gap-2 mb-2">
            <Terminal className="h-3.5 w-3.5 text-muted-foreground" />
            <span className="text-sm font-mono font-medium">{approval.toolName}</span>
          </div>

          {/* Args */}
          {argsStr && (
            <pre className="text-xs font-mono text-muted-foreground bg-background/50 rounded-md p-2 mb-3 overflow-x-auto whitespace-pre-wrap">
              {argsStr}
            </pre>
          )}

          {/* Countdown */}
          {isPending && remainingMs !== null && remainingMs > 0 && (
            <div className="flex items-center gap-1.5 mb-3 text-[11px] text-muted-foreground">
              <Clock className="h-3 w-3" />
              Expires in {Math.ceil(remainingMs / 1000)}s
            </div>
          )}
          {isPending && remainingMs === 0 && (
            <div className="flex items-center gap-1.5 mb-3 text-[11px] text-status-error">
              <Clock className="h-3 w-3" />
              Expired
            </div>
          )}

          {/* Actions */}
          {isPending && (remainingMs === null || remainingMs > 0) && (
            <div className="flex items-center gap-2">
              <Button
                size="sm"
                className="h-7 gap-1.5 text-xs"
                onClick={() => onRespond(approval.requestId, true)}
              >
                <Check className="h-3 w-3" /> Approve
              </Button>
              <Button
                variant="outline"
                size="sm"
                className="h-7 gap-1.5 text-xs"
                onClick={() => onRespond(approval.requestId, false)}
              >
                <X className="h-3 w-3" /> Deny
              </Button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
});
