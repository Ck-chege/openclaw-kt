import { create } from 'zustand';

type Theme = 'light' | 'dark' | 'system';

interface ThemeStore {
  theme: Theme;
  resolvedTheme: 'light' | 'dark';
  setTheme: (theme: Theme) => void;
}

function getSystemTheme(): 'light' | 'dark' {
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function getStoredTheme(): Theme {
  const stored = localStorage.getItem('openclaw-theme');
  if (stored === 'light' || stored === 'dark' || stored === 'system') return stored;
  return 'system';
}

function applyTheme(resolved: 'light' | 'dark') {
  document.documentElement.classList.remove('light', 'dark');
  document.documentElement.classList.add(resolved);
}

export const useThemeStore = create<ThemeStore>((set, get) => {
  const initial = getStoredTheme();
  const resolved = initial === 'system' ? getSystemTheme() : initial;

  applyTheme(resolved);

  const mql = window.matchMedia('(prefers-color-scheme: dark)');
  mql.addEventListener('change', () => {
    const { theme } = get();
    if (theme === 'system') {
      const newResolved = getSystemTheme();
      applyTheme(newResolved);
      set({ resolvedTheme: newResolved });
    }
  });

  return {
    theme: initial,
    resolvedTheme: resolved,
    setTheme: (theme: Theme) => {
      localStorage.setItem('openclaw-theme', theme);
      const resolved = theme === 'system' ? getSystemTheme() : theme;
      applyTheme(resolved);
      set({ theme, resolvedTheme: resolved });
    },
  };
});
