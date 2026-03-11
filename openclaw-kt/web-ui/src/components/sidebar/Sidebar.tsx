import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { SessionList } from './SessionList';
import { NewSessionDialog } from './NewSessionDialog';
import { ConfigView } from '@/components/dashboard/ConfigView';
import { StatusView } from '@/components/dashboard/StatusView';
import { ThemeToggle } from '@/components/ThemeToggle';
import { MessageSquare, Settings, Activity, PanelLeftClose, PanelLeft } from 'lucide-react';

type Tab = 'sessions' | 'config' | 'status';

export function Sidebar() {
  const [collapsed, setCollapsed] = useState(false);
  const [tab, setTab] = useState<Tab>('sessions');

  if (collapsed) {
    return (
      <div className="w-12 border-r border-border flex flex-col items-center py-3 gap-2 bg-card transition-[width] duration-200 ease-in-out">
        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8"
          onClick={() => setCollapsed(false)}
        >
          <PanelLeft className="h-4 w-4" />
        </Button>
        <Separator className="my-1" />
        <Button
          variant={tab === 'sessions' ? 'secondary' : 'ghost'}
          size="icon"
          className="h-8 w-8"
          onClick={() => { setTab('sessions'); setCollapsed(false); }}
        >
          <MessageSquare className="h-4 w-4" />
        </Button>
        <Button
          variant={tab === 'config' ? 'secondary' : 'ghost'}
          size="icon"
          className="h-8 w-8"
          onClick={() => { setTab('config'); setCollapsed(false); }}
        >
          <Settings className="h-4 w-4" />
        </Button>
        <Button
          variant={tab === 'status' ? 'secondary' : 'ghost'}
          size="icon"
          className="h-8 w-8"
          onClick={() => { setTab('status'); setCollapsed(false); }}
        >
          <Activity className="h-4 w-4" />
        </Button>
      </div>
    );
  }

  return (
    <div className="w-72 border-r border-border flex flex-col bg-card transition-[width] duration-200 ease-in-out">
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-3">
        <h1 className="text-sm font-semibold tracking-tight">KClaw</h1>
        <div className="flex items-center gap-1">
          <ThemeToggle />
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7"
            onClick={() => setCollapsed(true)}
          >
            <PanelLeftClose className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Tab buttons */}
      <div className="flex gap-1 px-3 pb-2">
        <Button
          variant={tab === 'sessions' ? 'secondary' : 'ghost'}
          size="sm"
          className="flex-1 text-xs h-7"
          onClick={() => setTab('sessions')}
        >
          <MessageSquare className="h-3 w-3 mr-1.5" />
          Sessions
        </Button>
        <Button
          variant={tab === 'config' ? 'secondary' : 'ghost'}
          size="sm"
          className="flex-1 text-xs h-7"
          onClick={() => setTab('config')}
        >
          <Settings className="h-3 w-3 mr-1.5" />
          Config
        </Button>
        <Button
          variant={tab === 'status' ? 'secondary' : 'ghost'}
          size="sm"
          className="flex-1 text-xs h-7"
          onClick={() => setTab('status')}
        >
          <Activity className="h-3 w-3 mr-1.5" />
          Status
        </Button>
      </div>

      <Separator />

      {/* Tab content */}
      <div className="flex-1 overflow-hidden">
        {tab === 'sessions' && <SessionList />}
        {tab === 'config' && <ConfigView />}
        {tab === 'status' && <StatusView />}
      </div>

      {/* New Session at bottom */}
      {tab === 'sessions' && (
        <>
          <Separator />
          <div className="p-3">
            <NewSessionDialog />
          </div>
        </>
      )}
    </div>
  );
}
