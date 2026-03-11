import { useState, useEffect } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { Separator } from '@/components/ui/separator';
import { SessionList } from './sidebar/SessionList';
import { NewSessionDialog } from './sidebar/NewSessionDialog';
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
  PanelLeft,
  PanelLeftClose,
  ChevronRight,
  X,
} from 'lucide-react';

interface NavItem {
  path: string;
  label: string;
  icon: React.ReactNode;
}

const ICON_CLASS = 'h-5 w-5 shrink-0';

const primaryNav: NavItem[] = [
  { path: '/chat', label: 'Chat', icon: <MessageSquare className={ICON_CLASS} /> },
  { path: '/agents', label: 'Agents', icon: <Users className={ICON_CLASS} /> },
  { path: '/sessions', label: 'Sessions', icon: <History className={ICON_CLASS} /> },
  { path: '/channels', label: 'Channels', icon: <Radio className={ICON_CLASS} /> },
];

const secondaryNav: NavItem[] = [
  { path: '/tools', label: 'Tools', icon: <Wrench className={ICON_CLASS} /> },
  { path: '/skills', label: 'Skills', icon: <Sparkles className={ICON_CLASS} /> },
  { path: '/cron', label: 'Cron', icon: <Clock className={ICON_CLASS} /> },
];

const tertiaryNav: NavItem[] = [
  { path: '/logs', label: 'Logs', icon: <ScrollText className={ICON_CLASS} /> },
  { path: '/usage', label: 'Usage', icon: <BarChart3 className={ICON_CLASS} /> },
];

function NavButton({ item, expanded }: { item: NavItem; expanded: boolean }) {
  const button = (
    <NavLink
      to={item.path}
      className={({ isActive }) =>
        cn(
          'flex items-center rounded-md transition-colors text-sm',
          expanded
            ? 'gap-3 px-3 py-2'
            : 'justify-center w-full aspect-square',
          isActive
            ? 'bg-sidebar-accent text-sidebar-accent-foreground font-medium'
            : 'text-sidebar-foreground/70 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground'
        )
      }
    >
      {item.icon}
      {expanded && <span className="truncate">{item.label}</span>}
    </NavLink>
  );

  if (expanded) return button;

  return (
    <Tooltip>
      <TooltipTrigger asChild>{button}</TooltipTrigger>
      <TooltipContent side="right" sideOffset={8}>
        {item.label}
      </TooltipContent>
    </Tooltip>
  );
}

export function AppSidebar() {
  const [expanded, setExpanded] = useState(false);
  const [pinned, setPinned] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const location = useLocation();
  const isOnChat = location.pathname === '/chat' || location.pathname === '/';

  const isExpanded = expanded || pinned;

  // Expose toggle for HeaderBar hamburger
  useEffect(() => {
    (window as unknown as Record<string, unknown>).__toggleMobileNav = () => setMobileOpen((v) => !v);
    return () => { delete (window as unknown as Record<string, unknown>).__toggleMobileNav; };
  }, []);

  return (
    <>
      {/* Mobile overlay */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/50 md:hidden"
          onClick={() => setMobileOpen(false)}
          aria-hidden="true"
        />
      )}

      <aside
        className={cn(
          'relative z-10 flex flex-col border-r border-sidebar-border bg-sidebar transition-[width] duration-200 ease-in-out flex-shrink-0 overflow-hidden',
          // Desktop: collapse/expand on hover
          'hidden md:flex',
          isExpanded ? 'w-64' : 'w-14'
        )}
        onMouseEnter={() => { if (!pinned) setExpanded(true); }}
        onMouseLeave={() => { if (!pinned) setExpanded(false); }}
        role="navigation"
        aria-label="Main navigation"
      >
        <SidebarContent
          isExpanded={isExpanded}
          pinned={pinned}
          onTogglePin={() => setPinned(!pinned)}
          isOnChat={isOnChat}
        />
      </aside>

      {/* Mobile sidebar */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex flex-col w-72 bg-sidebar border-r border-sidebar-border transition-transform duration-200 ease-in-out md:hidden',
          mobileOpen ? 'translate-x-0' : '-translate-x-full'
        )}
        role="navigation"
        aria-label="Main navigation"
      >
        <SidebarContent
          isExpanded={true}
          pinned={true}
          onTogglePin={() => setMobileOpen(false)}
          isOnChat={isOnChat}
          onNavigate={() => setMobileOpen(false)}
          mobileClose={<Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => setMobileOpen(false)}><X className="h-4 w-4" /></Button>}
        />
      </aside>
    </>
  );
}

function SidebarContent({ isExpanded, pinned, onTogglePin, isOnChat, onNavigate, mobileClose }: {
  isExpanded: boolean;
  pinned: boolean;
  onTogglePin: () => void;
  isOnChat: boolean;
  onNavigate?: () => void;
  mobileClose?: React.ReactNode;
}) {
  return (
    <>
      {/* Header */}
      <div className={cn(
        'flex items-center h-12 border-b border-sidebar-border flex-shrink-0',
        isExpanded ? 'px-3 justify-between' : 'justify-center'
      )}>
        {isExpanded ? (
          <>
            <span className="text-sm font-semibold text-sidebar-foreground tracking-tight">OpenClaw</span>
            {mobileClose || (
              <Button
                variant="ghost"
                size="icon"
                className="h-7 w-7 text-sidebar-foreground/70 hover:text-sidebar-foreground"
                onClick={onTogglePin}
              >
                {pinned ? <PanelLeftClose className="h-4 w-4" /> : <PanelLeft className="h-4 w-4" />}
              </Button>
            )}
          </>
        ) : (
          <div className="h-7 w-7 rounded-md bg-sidebar-primary flex items-center justify-center">
            <span className="text-xs font-bold text-sidebar-primary-foreground">OC</span>
          </div>
        )}
      </div>

      {/* Navigation */}
      <nav className="flex-1 flex flex-col gap-1 p-1.5 overflow-y-auto overflow-x-hidden" onClick={onNavigate}>
        {/* Primary */}
        <div className="space-y-0.5">
          {primaryNav.map((item) => (
            <NavButton key={item.path} item={item} expanded={isExpanded} />
          ))}
        </div>

        <Separator className="my-1.5 bg-sidebar-border" />

        {/* Secondary */}
        <div className="space-y-0.5">
          {secondaryNav.map((item) => (
            <NavButton key={item.path} item={item} expanded={isExpanded} />
          ))}
        </div>

        <Separator className="my-1.5 bg-sidebar-border" />

        {/* Tertiary */}
        <div className="space-y-0.5">
          {tertiaryNav.map((item) => (
            <NavButton key={item.path} item={item} expanded={isExpanded} />
          ))}
        </div>

        {/* Session list - only shown when expanded and on chat */}
        {isExpanded && isOnChat && (
          <>
            <Separator className="my-1.5 bg-sidebar-border" />
            <div className="flex items-center justify-between px-1 mb-1">
              <span className="text-[11px] font-medium text-sidebar-foreground/50 uppercase tracking-wider">Sessions</span>
              <ChevronRight className="h-3 w-3 text-sidebar-foreground/30" />
            </div>
            <div className="flex-1 min-h-0 overflow-hidden" onClick={(e) => e.stopPropagation()}>
              <SessionList />
            </div>
            <div className="pt-1" onClick={(e) => e.stopPropagation()}>
              <NewSessionDialog />
            </div>
          </>
        )}

        {/* Spacer */}
        <div className="flex-1" />
      </nav>

      {/* Bottom - Settings */}
      <div className="p-1.5 border-t border-sidebar-border" onClick={onNavigate}>
        <NavButton
          item={{ path: '/settings', label: 'Settings', icon: <Settings className={ICON_CLASS} /> }}
          expanded={isExpanded}
        />
      </div>
    </>
  );
}
