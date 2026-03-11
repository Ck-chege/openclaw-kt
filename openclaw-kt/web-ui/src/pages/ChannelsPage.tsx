import { useState, useEffect, useCallback } from 'react';
import { useConnectionStore } from '@/stores/connection-store';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { FeatureState } from '@/components/FeatureState';
import {
  Radio,
  MessageSquare,
  RefreshCw,
  Loader2,
  Circle,
  Clock,
  Hash,
  Globe,
  ArrowLeft,
} from 'lucide-react';

// Platform brand colors
const PLATFORM_CONFIG: Record<string, { label: string; color: string; bgColor: string; icon: React.ReactNode }> = {
  telegram: {
    label: 'Telegram',
    color: 'text-[#26A5E4]',
    bgColor: 'bg-[#26A5E4]/10',
    icon: <Globe className="h-5 w-5" />,
  },
  discord: {
    label: 'Discord',
    color: 'text-[#5865F2]',
    bgColor: 'bg-[#5865F2]/10',
    icon: <Hash className="h-5 w-5" />,
  },
  slack: {
    label: 'Slack',
    color: 'text-[#4A154B]',
    bgColor: 'bg-[#4A154B]/10',
    icon: <Hash className="h-5 w-5" />,
  },
  webchat: {
    label: 'WebChat',
    color: 'text-primary',
    bgColor: 'bg-primary/10',
    icon: <MessageSquare className="h-5 w-5" />,
  },
  whatsapp: {
    label: 'WhatsApp',
    color: 'text-[#25D366]',
    bgColor: 'bg-[#25D366]/10',
    icon: <MessageSquare className="h-5 w-5" />,
  },
  signal: {
    label: 'Signal',
    color: 'text-[#3A76F0]',
    bgColor: 'bg-[#3A76F0]/10',
    icon: <MessageSquare className="h-5 w-5" />,
  },
};

function getPlatformConfig(id: string) {
  return PLATFORM_CONFIG[id.toLowerCase()] || {
    label: id,
    color: 'text-muted-foreground',
    bgColor: 'bg-muted',
    icon: <Radio className="h-5 w-5" />,
  };
}

interface ChannelInfo {
  id: string;
  status: 'connected' | 'disconnected' | 'error' | 'unconfigured';
  lastActivity?: string;
  messageCount24h?: number;
  error?: string;
  config?: Record<string, unknown>;
}

export default function ChannelsPage() {
  const { client, state } = useConnectionStore();
  const [channels, setChannels] = useState<ChannelInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedChannel, setSelectedChannel] = useState<ChannelInfo | null>(null);

  const fetchChannels = useCallback(async () => {
    if (!client || state !== 'connected') {
      setChannels([]);
      setLoading(false);
      setError('Connect to the gateway to inspect channel status.');
      return;
    }
    setLoading(true);
    try {
      const res = await client.request('channels.status');
      setChannels((res.channels || []) as ChannelInfo[]);
      setError(null);
    } catch (e) {
      setChannels([]);
      setSelectedChannel(null);
      setError((e as Error).message || 'This gateway does not expose channel status yet.');
    } finally {
      setLoading(false);
    }
  }, [client, state]);

  useEffect(() => { fetchChannels(); }, [fetchChannels]);

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (error && channels.length === 0) {
    return (
      <FeatureState
        icon={<Radio className="h-6 w-6" />}
        title="Channels Unavailable"
        description={error}
        action={<Button variant="outline" size="sm" onClick={fetchChannels}>Retry</Button>}
      />
    );
  }

  if (selectedChannel) {
    return (
      <ChannelDetail
        channel={selectedChannel}
        onBack={() => setSelectedChannel(null)}
      />
    );
  }

  return (
    <div className="flex-1 p-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <Radio className="h-5 w-5" />
            Channels
          </h2>
          <p className="text-sm text-muted-foreground">
            {channels.filter((c) => c.status === 'connected').length} of {channels.length} connected
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={fetchChannels} className="gap-1.5">
          <RefreshCw className="h-3.5 w-3.5" /> Refresh
        </Button>
      </div>

      {channels.length === 0 ? (
        <FeatureState
          icon={<Radio className="h-6 w-6" />}
          title="No Channel Data"
          description="The gateway returned no channel status entries."
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
        {channels.map((channel) => {
          const platform = getPlatformConfig(channel.id);
          return (
            <button
              key={channel.id}
              onClick={() => setSelectedChannel(channel)}
              className="text-left border border-border rounded-lg p-4 hover:bg-muted/50 transition-colors"
            >
              <div className="flex items-start justify-between mb-3">
                <div className={`h-10 w-10 rounded-lg ${platform.bgColor} flex items-center justify-center ${platform.color}`}>
                  {platform.icon}
                </div>
                <ChannelStatusBadge status={channel.status} />
              </div>

              <p className="font-medium text-sm mb-1">{platform.label}</p>

              <div className="flex items-center gap-3 text-xs text-muted-foreground">
                {channel.messageCount24h !== undefined && (
                  <span className="flex items-center gap-1">
                    <MessageSquare className="h-3 w-3" /> {channel.messageCount24h} / 24h
                  </span>
                )}
                {channel.lastActivity && (
                  <span className="flex items-center gap-1">
                    <Clock className="h-3 w-3" /> {formatRelative(channel.lastActivity)}
                  </span>
                )}
              </div>

              {channel.error && (
                <p className="text-[11px] text-status-error mt-2 truncate">{channel.error}</p>
              )}
            </button>
          );
        })}
        </div>
      )}
    </div>
  );
}

function ChannelStatusBadge({ status }: { status: ChannelInfo['status'] }) {
  const config = {
    connected: { label: 'Connected', className: 'text-status-success border-status-success-border' },
    disconnected: { label: 'Disconnected', className: 'text-status-error border-status-error-border' },
    error: { label: 'Error', className: 'text-status-error border-status-error-border' },
    unconfigured: { label: 'Not Configured', className: 'text-muted-foreground' },
  }[status];

  return (
    <Badge variant="outline" className={`text-[10px] gap-1 ${config.className}`}>
      <Circle className={`h-2 w-2 ${status === 'connected' ? 'fill-status-success' : status === 'unconfigured' ? 'fill-muted-foreground/30' : 'fill-status-error'}`} />
      {config.label}
    </Badge>
  );
}

function ChannelDetail({ channel, onBack }: { channel: ChannelInfo; onBack: () => void }) {
  const platform = getPlatformConfig(channel.id);

  return (
    <div className="flex-1 p-6">
      <div className="flex items-center gap-3 mb-6">
        <Button variant="ghost" size="icon" className="h-8 w-8" onClick={onBack}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div className={`h-10 w-10 rounded-lg ${platform.bgColor} flex items-center justify-center ${platform.color}`}>
          {platform.icon}
        </div>
        <div>
          <h2 className="text-lg font-semibold">{platform.label}</h2>
          <p className="text-xs text-muted-foreground">Channel configuration and status</p>
        </div>
        <div className="ml-auto">
          <ChannelStatusBadge status={channel.status} />
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 max-w-3xl">
        {/* Status card */}
        <div className="border border-border rounded-lg p-4">
          <h3 className="text-xs font-medium text-muted-foreground mb-3">Connection Status</h3>
          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-muted-foreground">Status</span>
              <ChannelStatusBadge status={channel.status} />
            </div>
            {channel.lastActivity && (
              <div className="flex justify-between">
                <span className="text-muted-foreground">Last Activity</span>
                <span className="text-xs">{formatRelative(channel.lastActivity)}</span>
              </div>
            )}
            {channel.messageCount24h !== undefined && (
              <div className="flex justify-between">
                <span className="text-muted-foreground">Messages (24h)</span>
                <span className="text-xs tabular-nums">{channel.messageCount24h}</span>
              </div>
            )}
          </div>
        </div>

        {/* Config card */}
        <div className="border border-border rounded-lg p-4">
          <h3 className="text-xs font-medium text-muted-foreground mb-3">Configuration</h3>
          {channel.status === 'unconfigured' ? (
            <p className="text-xs text-muted-foreground">
              This channel is not yet configured. Add it in your gateway config file.
            </p>
          ) : channel.config ? (
            <pre className="text-xs font-mono text-muted-foreground overflow-x-auto">
              {JSON.stringify(channel.config, null, 2)}
            </pre>
          ) : (
            <p className="text-xs text-muted-foreground">Configuration details are managed in the gateway config.</p>
          )}
        </div>

        {/* Error card */}
        {channel.error && (
          <div className="border border-status-error-border rounded-lg p-4 bg-status-error-bg md:col-span-2">
            <h3 className="text-xs font-medium text-status-error mb-1">Error</h3>
            <p className="text-xs text-status-error/80">{channel.error}</p>
          </div>
        )}
      </div>
    </div>
  );
}

function formatRelative(iso: string): string {
  const ms = Date.now() - new Date(iso).getTime();
  const min = Math.floor(ms / 60000);
  if (min < 1) return 'just now';
  if (min < 60) return `${min}m ago`;
  const hr = Math.floor(ms / 3600000);
  if (hr < 24) return `${hr}h ago`;
  return `${Math.floor(ms / 86400000)}d ago`;
}
