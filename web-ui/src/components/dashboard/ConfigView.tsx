import { ScrollArea } from '@/components/ui/scroll-area';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { useDashboardStore } from '@/stores/dashboard-store';
import { ChevronRight, FileJson } from 'lucide-react';
import { useState } from 'react';

function ConfigSection({ name, data }: { name: string; data: unknown }) {
  const [open, setOpen] = useState(false);

  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <CollapsibleTrigger className="flex items-center gap-2 w-full text-left px-3 py-2 text-xs hover:bg-muted rounded transition-colors">
        <ChevronRight className={`h-3 w-3 transition-transform ${open ? 'rotate-90' : ''}`} />
        <span className="font-medium">{name}</span>
      </CollapsibleTrigger>
      <CollapsibleContent>
        <pre className="mx-3 mb-2 p-2 rounded bg-code-bg border border-border text-xs font-mono overflow-x-auto max-h-60 overflow-y-auto">
          {JSON.stringify(data, null, 2)}
        </pre>
      </CollapsibleContent>
    </Collapsible>
  );
}

export function ConfigView() {
  const { config } = useDashboardStore();

  if (!config) {
    return (
      <div className="flex flex-col items-center justify-center py-8 text-muted-foreground">
        <FileJson className="h-8 w-8 mb-2 opacity-30" />
        <p className="text-xs">Config not loaded</p>
      </div>
    );
  }

  const sections = Object.entries(config);

  return (
    <ScrollArea className="flex-1">
      <div className="py-2">
        {sections.map(([key, value]) => (
          <ConfigSection key={key} name={key} data={value} />
        ))}
      </div>
    </ScrollArea>
  );
}
