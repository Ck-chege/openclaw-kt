import { useConnectionStore } from '@/stores/connection-store';
import { Wifi, WifiOff, Loader2 } from 'lucide-react';

export function ConnectionBanner() {
  const { state } = useConnectionStore();

  if (state === 'connected') {
    return (
      <div className="flex items-center gap-2 px-4 py-1.5 bg-status-success-bg border-b border-status-success-border text-status-success text-xs transition-colors">
        <Wifi className="h-3 w-3" />
        <span>Connected to gateway</span>
      </div>
    );
  }

  if (state === 'connecting') {
    return (
      <div className="flex items-center gap-2 px-4 py-1.5 bg-status-warning-bg border-b border-status-warning-border text-status-warning text-xs transition-colors">
        <Loader2 className="h-3 w-3 animate-spin" />
        <span>Connecting...</span>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2 px-4 py-1.5 bg-status-error-bg border-b border-status-error-border text-status-error text-xs transition-colors">
      <WifiOff className="h-3 w-3" />
      <span>Disconnected — reconnecting...</span>
    </div>
  );
}
