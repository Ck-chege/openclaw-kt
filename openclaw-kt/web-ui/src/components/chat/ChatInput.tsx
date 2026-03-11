import { useState, useRef, useEffect, useCallback } from 'react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useDashboardStore } from '@/stores/dashboard-store';
import { ChevronDown, Cpu, Send, Square } from 'lucide-react';

interface ChatInputProps {
  onSend: (text: string) => void;
  onStop: () => void;
  isStreaming: boolean;
  disabled: boolean;
}

export function ChatInput({ onSend, onStop, isStreaming, disabled }: ChatInputProps) {
  const [text, setText] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const { models, selectedModelId, setSelectedModel } = useDashboardStore();
  const activeModel = models.find((model) => model.id === selectedModelId) || models[0];

  useEffect(() => {
    if (!isStreaming && textareaRef.current) {
      textareaRef.current.focus();
    }
  }, [isStreaming]);

  const handleSubmit = useCallback(() => {
    const trimmed = text.trim();
    if (!trimmed || disabled || isStreaming) return;
    onSend(trimmed);
    setText('');
  }, [text, disabled, isStreaming, onSend]);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
    if (e.key === 'Escape' && isStreaming) {
      onStop();
    }
  }, [handleSubmit, isStreaming, onStop]);

  useEffect(() => {
    const el = textareaRef.current;
    if (el) {
      el.style.height = 'auto';
      el.style.height = `${Math.min(el.scrollHeight, 160)}px`;
    }
  }, [text]);
  const placeholder = disabled
    ? (models.length === 0 ? 'No ready models configured' : 'Connecting to the gateway...')
    : 'Ask for a code change, review, or investigation...';

  return (
    <div className="border-t border-border/60 bg-[linear-gradient(180deg,rgba(248,250,252,0.2)_0%,rgba(248,250,252,0.92)_18%,rgba(255,255,255,0.98)_100%)] px-3 pb-3 pt-4 dark:bg-[linear-gradient(180deg,rgba(10,14,19,0.2)_0%,rgba(10,14,19,0.94)_18%,rgba(7,10,15,0.98)_100%)] sm:px-4 sm:pb-4">
      <div className="mx-auto max-w-4xl rounded-[24px] border border-border/70 bg-background/86 p-2.5 shadow-[0_18px_48px_-36px_rgba(15,23,42,0.5)] backdrop-blur-xl">
        <div className="flex flex-col gap-3">
          <div className="flex items-start gap-3">
            <div className="flex-1 rounded-[20px] border border-border/60 bg-muted/30 px-4 py-2.5 transition-colors focus-within:border-primary/35 focus-within:bg-background/90">
              <textarea
                ref={textareaRef}
                value={text}
                onChange={(e) => setText(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={placeholder}
                disabled={disabled}
                rows={1}
                className="min-h-[52px] max-h-[160px] w-full resize-none bg-transparent text-[15px] leading-6 outline-none placeholder:text-muted-foreground disabled:opacity-50"
              />
            </div>

            {isStreaming ? (
              <Button
                onClick={onStop}
                variant="destructive"
                size="icon-lg"
                className="h-11 w-11 rounded-2xl flex-shrink-0 self-end"
              >
                <Square className="h-4 w-4" />
              </Button>
            ) : (
              <Button
                onClick={handleSubmit}
                disabled={disabled || !text.trim()}
                size="icon-lg"
                className="h-11 w-11 rounded-2xl flex-shrink-0 self-end"
              >
                <Send className="h-4 w-4" />
              </Button>
            )}
          </div>

          <div className="flex flex-col gap-2 rounded-[18px] border border-border/60 bg-muted/20 px-3 py-2 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex flex-wrap items-center gap-2">
              {models.length > 0 ? (
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <button className="flex h-8 items-center gap-1.5 rounded-full border border-border/70 bg-background/85 px-3 text-xs text-muted-foreground transition-colors hover:text-foreground">
                      <Cpu className="h-3.5 w-3.5" />
                      <span>{activeModel?.displayName || models[0].displayName}</span>
                      <ChevronDown className="h-3.5 w-3.5" />
                    </button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="start">
                    {models.map((model) => (
                      <DropdownMenuItem
                        key={model.id}
                        onClick={() => setSelectedModel(model.id)}
                        className="text-xs font-mono"
                      >
                        {model.displayName}
                        {(selectedModelId === model.id || (!selectedModelId && model.id === models[0].id)) && (
                          <Badge variant="outline" className="ml-2 h-4 text-[9px]">
                            active
                          </Badge>
                        )}
                      </DropdownMenuItem>
                    ))}
                  </DropdownMenuContent>
                </DropdownMenu>
              ) : (
                <span className="text-[11px] text-muted-foreground">No ready models</span>
              )}

              <span className="text-[11px] text-muted-foreground">
                {isStreaming ? 'Esc to stop the current response' : 'Enter to send'}
              </span>
            </div>

            <span className="text-[11px] text-muted-foreground">Shift+Enter adds a newline</span>
          </div>
        </div>
      </div>
    </div>
  );
}
