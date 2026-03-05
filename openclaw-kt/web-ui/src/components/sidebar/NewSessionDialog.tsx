import { useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  DialogFooter,
  DialogClose,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useChatStore } from '@/stores/chat-store';
import { useConnectionStore } from '@/stores/connection-store';
import { useDashboardStore } from '@/stores/dashboard-store';
import { Plus } from 'lucide-react';

export function NewSessionDialog() {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const { setActiveSession, loadHistory, clearMessages } = useChatStore();
  const { client } = useConnectionStore();
  const { fetchSessions } = useDashboardStore();

  const handleCreate = async () => {
    const sessionName = name.trim() || 'main';
    const key = `main:main:*:${sessionName}`;
    setActiveSession(key);
    clearMessages();
    if (client) {
      await loadHistory(client);
      await fetchSessions(client);
    }
    setName('');
    setOpen(false);
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm" className="w-full text-xs">
          <Plus className="h-3.5 w-3.5 mr-1.5" />
          New Session
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>New Session</DialogTitle>
        </DialogHeader>
        <div className="space-y-3 py-2">
          <div>
            <label className="text-xs text-muted-foreground mb-1 block">
              Session name
            </label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="main"
              className="text-sm"
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleCreate();
              }}
            />
            <p className="text-[11px] text-muted-foreground mt-1">
              Creates key: main:main:*:{name.trim() || 'main'}
            </p>
          </div>
        </div>
        <DialogFooter>
          <DialogClose asChild>
            <Button variant="ghost" size="sm">Cancel</Button>
          </DialogClose>
          <Button size="sm" onClick={handleCreate}>Create</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
