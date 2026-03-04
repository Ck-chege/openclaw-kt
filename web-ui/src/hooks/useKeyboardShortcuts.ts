import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

export function useKeyboardShortcuts() {
  const navigate = useNavigate();

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      const meta = e.metaKey || e.ctrlKey;
      const target = e.target as HTMLElement;
      const isInput = target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable;

      // Cmd+N — New session (go to chat)
      if (meta && e.key === 'n') {
        e.preventDefault();
        navigate('/chat');
        return;
      }

      // Cmd+Shift+S — Toggle sidebar (handled by sidebar itself via CSS, but we can navigate)
      if (meta && e.shiftKey && e.key === 'S') {
        e.preventDefault();
        // Sidebar toggle is managed by hover/pin state — this is a placeholder for a future store-driven toggle
        return;
      }

      // Cmd+, — Settings
      if (meta && e.key === ',') {
        e.preventDefault();
        navigate('/settings');
        return;
      }

      // "/" — Focus chat input (only when not already in an input)
      if (e.key === '/' && !isInput) {
        e.preventDefault();
        const chatInput = document.querySelector('textarea') as HTMLTextAreaElement | null;
        chatInput?.focus();
        return;
      }
    }

    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [navigate]);
}
