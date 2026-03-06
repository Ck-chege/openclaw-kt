import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useConnectionStore } from '@/stores/connection-store';
import { Wifi } from 'lucide-react';

export function ConnectDialog() {
  const [token, setToken] = useState(localStorage.getItem('openclaw-token') || '');
  const { connect, error } = useConnectionStore();

  const handleConnect = () => {
    const trimmed = token.trim();
    if (trimmed) {
      localStorage.setItem('openclaw-token', trimmed);
    }
    connect(undefined, trimmed || undefined);
  };

  return (
    <div className="flex h-screen w-screen items-center justify-center bg-background">
      <div className="w-96 space-y-6 p-8 rounded-xl border border-border bg-card shadow-lg">
        <div className="flex flex-col items-center gap-3">
          <div className="h-12 w-12 rounded-full bg-primary/10 flex items-center justify-center">
            <Wifi className="h-6 w-6 text-primary" />
          </div>
          <div className="text-center">
            <h2 className="text-xl font-semibold">OpenClaw</h2>
            <p className="text-sm text-muted-foreground mt-1">
              Connect to your gateway to get started.
            </p>
          </div>
        </div>
        <div className="space-y-2">
          <label className="text-sm font-medium">Auth Token</label>
          <Input
            type="password"
            value={token}
            onChange={(e) => setToken(e.target.value)}
            placeholder="Enter token or leave empty"
            className="h-10"
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleConnect();
            }}
          />
          <p className="text-[11px] text-muted-foreground">
            The auth token configured in your gateway settings.
          </p>
        </div>
        {error && (
          <div className="text-xs text-destructive bg-destructive/10 border border-destructive/20 rounded-md p-2">
            {error}
          </div>
        )}
        <Button className="w-full h-10" onClick={handleConnect}>
          Connect
        </Button>
      </div>
    </div>
  );
}
