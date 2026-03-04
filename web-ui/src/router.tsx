import { lazy, Suspense } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppShell } from '@/components/AppShell';
import { Loader2 } from 'lucide-react';

// Lazy-loaded pages
const ChatPage = lazy(() => import('@/pages/ChatPage'));
const AgentsPage = lazy(() => import('@/pages/AgentsPage'));
const SessionsPage = lazy(() => import('@/pages/SessionsPage'));
const ChannelsPage = lazy(() => import('@/pages/ChannelsPage'));
const ToolsPage = lazy(() => import('@/pages/ToolsPage'));
const SkillsPage = lazy(() => import('@/pages/SkillsPage'));
const CronPage = lazy(() => import('@/pages/CronPage'));
const LogsPage = lazy(() => import('@/pages/LogsPage'));
const UsagePage = lazy(() => import('@/pages/UsagePage'));
const SettingsPage = lazy(() => import('@/pages/SettingsPage'));
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage'));

function PageLoader() {
  return (
    <div className="flex-1 flex items-center justify-center">
      <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
    </div>
  );
}

function SuspenseWrapper({ children }: { children: React.ReactNode }) {
  return <Suspense fallback={<PageLoader />}>{children}</Suspense>;
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppShell />,
    children: [
      { index: true, element: <Navigate to="/chat" replace /> },
      { path: 'chat', element: <SuspenseWrapper><ChatPage /></SuspenseWrapper> },
      { path: 'agents', element: <SuspenseWrapper><AgentsPage /></SuspenseWrapper> },
      { path: 'sessions', element: <SuspenseWrapper><SessionsPage /></SuspenseWrapper> },
      { path: 'channels', element: <SuspenseWrapper><ChannelsPage /></SuspenseWrapper> },
      { path: 'tools', element: <SuspenseWrapper><ToolsPage /></SuspenseWrapper> },
      { path: 'skills', element: <SuspenseWrapper><SkillsPage /></SuspenseWrapper> },
      { path: 'cron', element: <SuspenseWrapper><CronPage /></SuspenseWrapper> },
      { path: 'logs', element: <SuspenseWrapper><LogsPage /></SuspenseWrapper> },
      { path: 'usage', element: <SuspenseWrapper><UsagePage /></SuspenseWrapper> },
      { path: 'settings', element: <SuspenseWrapper><SettingsPage /></SuspenseWrapper> },
      { path: '*', element: <SuspenseWrapper><NotFoundPage /></SuspenseWrapper> },
    ],
  },
]);
