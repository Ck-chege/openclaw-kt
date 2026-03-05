import { ScrollArea } from '@/components/ui/scroll-area';
import { Badge } from '@/components/ui/badge';
import { useDashboardStore } from '@/stores/dashboard-store';
import { useConnectionStore } from '@/stores/connection-store';
import { Cpu, Radio, Wifi } from 'lucide-react';

export function StatusView() {
  const { models } = useDashboardStore();
  const { state, wsSessionId } = useConnectionStore();

  return (
    <ScrollArea className="flex-1">
      <div className="p-3 space-y-4">
        {/* Connection Status */}
        <div>
          <h3 className="text-xs font-medium mb-2 flex items-center gap-1.5">
            <Wifi className="h-3 w-3" />
            Connection
          </h3>
          <div className="space-y-1.5 text-xs">
            <div className="flex justify-between">
              <span className="text-muted-foreground">Status</span>
              <Badge
                variant={state === 'connected' ? 'default' : 'outline'}
                className="text-[11px]"
              >
                {state}
              </Badge>
            </div>
            {wsSessionId && (
              <div className="flex justify-between">
                <span className="text-muted-foreground">Session ID</span>
                <span className="font-mono text-[11px] truncate max-w-[120px]">{wsSessionId}</span>
              </div>
            )}
          </div>
        </div>

        {/* Models */}
        {models.length > 0 && (
          <div>
            <h3 className="text-xs font-medium mb-2 flex items-center gap-1.5">
              <Cpu className="h-3 w-3" />
              Models
            </h3>
            <div className="space-y-1">
              {models.map((model) => (
                <div key={model} className="text-xs font-mono text-muted-foreground px-2 py-1 rounded bg-muted/50">
                  {model}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Channels */}
        <div>
          <h3 className="text-xs font-medium mb-2 flex items-center gap-1.5">
            <Radio className="h-3 w-3" />
            Channels
          </h3>
          <p className="text-xs text-muted-foreground">
            Channel status not yet available
          </p>
        </div>
      </div>
    </ScrollArea>
  );
}
