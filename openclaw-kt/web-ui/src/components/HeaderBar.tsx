import { useLocation, useNavigate } from 'react-router-dom';
import { useConnectionStore } from '@/stores/connection-store';
import { useChatStore } from '@/stores/chat-store';
import { ThemeToggle } from './ThemeToggle';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Wifi, WifiOff, Loader2, Search, LogOut, Shield, Menu } from 'lucide-react';

const routeTitles: Record<string, string> = {
  '/chat': 'Chat',
  '/agents': 'Agents',
  '/sessions': 'Sessions',
  '/channels': 'Channels',
  '/tools': 'Tools',
  '/skills': 'Skills',
  '/cron': 'Scheduled Jobs',
  '/logs': 'Logs',
  '/usage': 'Usage Analytics',
  '/settings': 'Settings',
};

function ConnectionPill() {
  const { state } = useConnectionStore();

  if (state === 'connected') {
    return (
      <Badge variant="outline" className="gap-1.5 text-[11px] font-normal text-status-success border-status-success-border bg-status-success-bg">
        <Wifi className="h-3 w-3" />
        Connected
      </Badge>
    );
  }

  if (state === 'connecting') {
    return (
      <Badge variant="outline" className="gap-1.5 text-[11px] font-normal text-status-warning border-status-warning-border bg-status-warning-bg">
        <Loader2 className="h-3 w-3 animate-spin" />
        Connecting
      </Badge>
    );
  }

  return (
    <Badge variant="outline" className="gap-1.5 text-[11px] font-normal text-status-error border-status-error-border bg-status-error-bg">
      <WifiOff className="h-3 w-3" />
      Disconnected
    </Badge>
  );
}

export function HeaderBar() {
  const location = useLocation();
  const navigate = useNavigate();
  const { disconnect, state } = useConnectionStore();
  const { pendingApprovals } = useChatStore();
  const title = routeTitles[location.pathname] || 'OpenClaw';

  return (
    <header className="flex items-center justify-between h-12 px-4 border-b border-border bg-background flex-shrink-0">
      {/* Left: hamburger (mobile) + Page title */}
      <div className="flex items-center gap-2">
        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8 md:hidden"
          onClick={() => (window as unknown as Record<string, () => void>).__toggleMobileNav?.()}
          aria-label="Toggle navigation menu"
        >
          <Menu className="h-4 w-4" />
        </Button>
        <h1 className="text-sm font-semibold">{title}</h1>
      </div>

      {/* Right: Actions */}
      <div className="flex items-center gap-2">
        {/* Pending approvals badge */}
        {pendingApprovals > 0 && (
          <Button
            variant="outline"
            size="sm"
            className="h-7 gap-1.5 text-xs text-status-warning border-status-warning-border"
            onClick={() => navigate('/chat')}
          >
            <Shield className="h-3.5 w-3.5" />
            {pendingApprovals} pending
          </Button>
        )}

        <Button variant="ghost" size="sm" className="h-7 gap-1.5 text-xs text-muted-foreground">
          <Search className="h-3.5 w-3.5" />
          <span className="hidden sm:inline">Search</span>
          <kbd className="hidden sm:inline-flex ml-1 pointer-events-none h-5 items-center gap-1 rounded border border-border bg-muted px-1.5 font-mono text-[10px] font-medium text-muted-foreground">
            <span className="text-xs">&#8984;</span>K
          </kbd>
        </Button>

        <ConnectionPill />
        <ThemeToggle />

        {state === 'connected' && (
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-muted-foreground hover:text-destructive"
            onClick={disconnect}
            title="Disconnect"
          >
            <LogOut className="h-3.5 w-3.5" />
          </Button>
        )}
      </div>
    </header>
  );
}
