import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Command } from 'cmdk';
import { useDashboardStore } from '@/stores/dashboard-store';
import { useChatStore } from '@/stores/chat-store';
import { useConnectionStore } from '@/stores/connection-store';
import { useThemeStore } from '@/stores/theme-store';
import { formatSessionKey } from '@/lib/format';
import {
  MessageSquare,
  Users,
  History,
  Radio,
  Wrench,
  Sparkles,
  Clock,
  ScrollText,
  BarChart3,
  Settings,
  Plus,
  Sun,
  Moon,
  Monitor,
  LogOut,
  Wifi,
} from 'lucide-react';

export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const { sessions } = useDashboardStore();
  const { setActiveSession, loadHistory } = useChatStore();
  const { client, disconnect } = useConnectionStore();
  const { setTheme } = useThemeStore();

  // Global Cmd+K listener
  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setOpen((prev) => !prev);
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, []);

  const runAction = useCallback((action: () => void) => {
    action();
    setOpen(false);
  }, []);

  const goTo = useCallback((path: string) => {
    runAction(() => navigate(path));
  }, [navigate, runAction]);

  const switchSession = useCallback((key: string) => {
    runAction(() => {
      setActiveSession(key);
      if (client) loadHistory(client);
      navigate('/chat');
    });
  }, [setActiveSession, loadHistory, client, navigate, runAction]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50" onClick={() => setOpen(false)}>
      <div className="fixed inset-0 bg-black/50 animate-in fade-in-0 duration-150" />
      <div className="fixed top-[20%] left-1/2 -translate-x-1/2 w-full max-w-lg" onClick={(e) => e.stopPropagation()}>
        <Command
          className="rounded-xl border border-border bg-popover shadow-2xl overflow-hidden"
          loop
        >
          <Command.Input
            placeholder="Type a command or search..."
            className="w-full px-4 py-3 text-sm bg-transparent border-b border-border outline-none placeholder:text-muted-foreground"
            autoFocus
          />
          <Command.List className="max-h-80 overflow-y-auto p-2">
            <Command.Empty className="py-6 text-center text-sm text-muted-foreground">
              No results found.
            </Command.Empty>

            {/* Navigation */}
            <Command.Group heading="Navigate" className="text-[11px] font-medium text-muted-foreground px-2 py-1.5">
              <CommandItem icon={<MessageSquare />} onSelect={() => goTo('/chat')}>Chat</CommandItem>
              <CommandItem icon={<Users />} onSelect={() => goTo('/agents')}>Agents</CommandItem>
              <CommandItem icon={<History />} onSelect={() => goTo('/sessions')}>Sessions</CommandItem>
              <CommandItem icon={<Radio />} onSelect={() => goTo('/channels')}>Channels</CommandItem>
              <CommandItem icon={<Wrench />} onSelect={() => goTo('/tools')}>Tools</CommandItem>
              <CommandItem icon={<Sparkles />} onSelect={() => goTo('/skills')}>Skills</CommandItem>
              <CommandItem icon={<Clock />} onSelect={() => goTo('/cron')}>Scheduled Jobs</CommandItem>
              <CommandItem icon={<ScrollText />} onSelect={() => goTo('/logs')}>Logs</CommandItem>
              <CommandItem icon={<BarChart3 />} onSelect={() => goTo('/usage')}>Usage Analytics</CommandItem>
              <CommandItem icon={<Settings />} onSelect={() => goTo('/settings')}>Settings</CommandItem>
            </Command.Group>

            {/* Recent Sessions */}
            {sessions.length > 0 && (
              <Command.Group heading="Recent Sessions" className="text-[11px] font-medium text-muted-foreground px-2 py-1.5">
                {sessions.slice(0, 8).map((s) => (
                  <CommandItem
                    key={s.key}
                    icon={<MessageSquare />}
                    onSelect={() => switchSession(s.key)}
                  >
                    {formatSessionKey(s.key)}
                    <span className="ml-auto text-[11px] text-muted-foreground">{s.messageCount} msgs</span>
                  </CommandItem>
                ))}
              </Command.Group>
            )}

            {/* Quick Actions */}
            <Command.Group heading="Actions" className="text-[11px] font-medium text-muted-foreground px-2 py-1.5">
              <CommandItem icon={<Plus />} onSelect={() => goTo('/chat')}>New Session</CommandItem>
              <CommandItem icon={<Sun />} onSelect={() => runAction(() => setTheme('light'))}>Theme: Light</CommandItem>
              <CommandItem icon={<Moon />} onSelect={() => runAction(() => setTheme('dark'))}>Theme: Dark</CommandItem>
              <CommandItem icon={<Monitor />} onSelect={() => runAction(() => setTheme('system'))}>Theme: System</CommandItem>
              {client && (
                <CommandItem icon={<LogOut />} onSelect={() => runAction(disconnect)}>Disconnect</CommandItem>
              )}
            </Command.Group>
          </Command.List>

          <div className="flex items-center justify-between px-4 py-2 text-[11px] text-muted-foreground border-t border-border">
            <div className="flex items-center gap-3">
              <span><kbd className="rounded border border-border bg-muted px-1 font-mono">&#8593;&#8595;</kbd> Navigate</span>
              <span><kbd className="rounded border border-border bg-muted px-1 font-mono">&#8629;</kbd> Select</span>
            </div>
            <span><kbd className="rounded border border-border bg-muted px-1 font-mono">Esc</kbd> Close</span>
          </div>
        </Command>
      </div>
    </div>
  );
}

function CommandItem({
  children,
  icon,
  onSelect,
}: {
  children: React.ReactNode;
  icon: React.ReactNode;
  onSelect: () => void;
}) {
  return (
    <Command.Item
      onSelect={onSelect}
      className="flex items-center gap-2.5 px-2 py-2 text-sm rounded-md cursor-pointer text-foreground aria-selected:bg-accent aria-selected:text-accent-foreground [&_svg]:h-4 [&_svg]:w-4 [&_svg]:text-muted-foreground"
    >
      {icon}
      {children}
    </Command.Item>
  );
}
