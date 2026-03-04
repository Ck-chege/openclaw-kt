import { FileQuestion } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';

export default function NotFoundPage() {
  return (
    <div className="flex-1 flex items-center justify-center">
      <div className="text-center text-muted-foreground">
        <FileQuestion className="h-12 w-12 mx-auto mb-4 opacity-30" />
        <p className="text-lg font-medium">Page not found</p>
        <p className="text-sm mb-4">The page you're looking for doesn't exist.</p>
        <Button asChild variant="outline" size="sm">
          <Link to="/chat">Go to Chat</Link>
        </Button>
      </div>
    </div>
  );
}
