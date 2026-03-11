import type { ReactNode } from 'react';

interface FeatureStateProps {
  icon: ReactNode;
  title: string;
  description: string;
  action?: ReactNode;
}

export function FeatureState({ icon, title, description, action }: FeatureStateProps) {
  return (
    <div className="flex flex-1 items-center justify-center p-6">
      <div className="max-w-md text-center text-muted-foreground">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-muted/50">
          <div className="opacity-60">{icon}</div>
        </div>
        <h3 className="text-sm font-semibold text-foreground">{title}</h3>
        <p className="mt-2 text-sm leading-relaxed">{description}</p>
        {action ? <div className="mt-4 flex justify-center">{action}</div> : null}
      </div>
    </div>
  );
}
