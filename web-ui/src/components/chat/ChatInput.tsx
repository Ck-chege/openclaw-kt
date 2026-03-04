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
import { Send, Square, ChevronDown, Cpu } from 'lucide-react';

interface ChatInputProps {
  onSend: (text: string) => void;
  onStop: () => void;
  isStreaming: boolean;
  disabled: boolean;
}

const SUGGESTED_PROMPTS = [
  'What can you help me with?',
  'Summarize the latest changes in my workspace',
  'List my active sessions',
  'What tools do you have access to?',
];

export function ChatInput({ onSend, onStop, isStreaming, disabled }: ChatInputProps) {
  const [text, setText] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const { models, selectedModelId, setSelectedModel } = useDashboardStore();

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

  // Auto-resize textarea
  useEffect(() => {
    const el = textareaRef.current;
    if (el) {
      el.style.height = 'auto';
      el.style.height = Math.min(el.scrollHeight, 200) + 'px';
    }
  }, [text]);

  const showSuggestions = !disabled && !isStreaming && !text;

  return (
    <div className="border-t border-border p-4 bg-background">
      {/* Suggested prompts for empty state */}
      {showSuggestions && (
        <div className="max-w-3xl mx-auto mb-3 flex flex-wrap gap-2">
          {SUGGESTED_PROMPTS.map((prompt) => (
            <button
              key={prompt}
              onClick={() => onSend(prompt)}
              className="text-xs px-3 py-1.5 rounded-full border border-border bg-muted/50 hover:bg-muted text-muted-foreground hover:text-foreground transition-colors"
            >
              {prompt}
            </button>
          ))}
        </div>
      )}

      <div className="max-w-3xl mx-auto">
        <div className="flex gap-2 items-end">
          <div className="flex-1 relative">
            <textarea
              ref={textareaRef}
              value={text}
              onChange={(e) => setText(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={disabled ? 'Connecting...' : 'Send a message...'}
              disabled={disabled}
              rows={1}
              className="w-full resize-none bg-muted border border-input rounded-lg px-4 py-2.5 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring disabled:opacity-50 min-h-[40px] max-h-[200px] pr-12"
            />
          </div>

          {isStreaming ? (
            <Button
              onClick={onStop}
              variant="destructive"
              size="icon"
              className="h-10 w-10 flex-shrink-0"
            >
              <Square className="h-4 w-4" />
            </Button>
          ) : (
            <Button
              onClick={handleSubmit}
              disabled={disabled || !text.trim()}
              size="icon"
              className="h-10 w-10 flex-shrink-0"
            >
              <Send className="h-4 w-4" />
            </Button>
          )}
        </div>

        {/* Bottom bar: model selector + hints */}
        <div className="flex items-center justify-between mt-2 px-1">
          {/* Model selector */}
          {models.length > 0 ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button className="flex items-center gap-1 text-[11px] text-muted-foreground hover:text-foreground transition-colors">
                  <Cpu className="h-3 w-3" />
                  <span>{models.find((m) => m.id === selectedModelId)?.displayName || models[0].displayName}</span>
                  <ChevronDown className="h-3 w-3" />
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
                      <Badge variant="outline" className="text-[9px] ml-2 h-4">active</Badge>
                    )}
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          ) : (
            <span className="text-[11px] text-muted-foreground">No models loaded</span>
          )}

          <span className="text-[11px] text-muted-foreground">
            Enter to send, Shift+Enter for newline
          </span>
        </div>
      </div>
    </div>
  );
}
