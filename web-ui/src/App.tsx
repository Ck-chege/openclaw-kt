import { Layout } from '@/components/Layout';
import { TooltipProvider } from '@/components/ui/tooltip';

export default function App() {
  return (
    <TooltipProvider>
      <Layout />
    </TooltipProvider>
  );
}
