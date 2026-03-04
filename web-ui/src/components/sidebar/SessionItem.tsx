import type { SessionMeta } from '@/types/protocol';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Button } from '@/components/ui/button';
import { formatSessionKey, formatTimestamp, formatTokens } from '@/lib/format';
import { MoreHorizontal, Trash2, RotateCcw } from 'lucide-react';

interface SessionItemProps {
  session: SessionMeta;
  isActive: boolean;
  onSelect: () => void;
  onDelete: () => void;
  onReset: () => void;
}

export function SessionItem({ session, isActive, onSelect, onDelete, onReset }: SessionItemProps) {
  return (
    <div
      onClick={onSelect}
      className={`group flex items-center gap-2 px-2.5 py-2 rounded-md cursor-pointer text-sm transition-colors ${
        isActive
          ? 'bg-accent text-accent-foreground'
          : 'text-muted-foreground hover:bg-muted hover:text-foreground'
      }`}
    >
      <div className="flex-1 min-w-0">
        <p className="truncate font-medium text-xs">{formatSessionKey(session.key)}</p>
        <div className="flex items-center gap-2 text-[11px] text-muted-foreground mt-0.5">
          <span>{session.messageCount} msgs</span>
          {session.totalTokens > 0 && (
            <span>{formatTokens(session.totalTokens)} tok</span>
          )}
          {session.lastMessageAt && (
            <span>{formatTimestamp(session.lastMessageAt)}</span>
          )}
        </div>
      </div>

      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="ghost"
            size="icon"
            className="h-6 w-6 opacity-0 group-hover:opacity-100 flex-shrink-0"
            onClick={(e) => e.stopPropagation()}
          >
            <MoreHorizontal className="h-3.5 w-3.5" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onClick={onReset}>
            <RotateCcw className="h-3.5 w-3.5 mr-2" />
            Reset
          </DropdownMenuItem>
          <DropdownMenuItem onClick={onDelete} className="text-destructive">
            <Trash2 className="h-3.5 w-3.5 mr-2" />
            Delete
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}
