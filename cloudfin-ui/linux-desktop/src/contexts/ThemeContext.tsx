import { createContext, useContext, useState, useEffect, ReactNode } from 'react';

export type Theme = 'system' | 'light' | 'dark';
export const THEME_ORDER: Theme[] = ['system', 'light', 'dark'];

const ThemeContext = createContext<{
  theme: Theme;
  resolvedTheme: 'light' | 'dark';
  setTheme: (t: Theme) => void;
}>({
  theme: 'system',
  resolvedTheme: 'dark',
  setTheme: () => {},
});

function getSystemTheme(): 'light' | 'dark' {
  if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
    return 'dark';
  }
  return 'light';
}

function resolveTheme(theme: Theme): 'light' | 'dark' {
  return theme === 'system' ? getSystemTheme() : theme;
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(() => {
    const stored = localStorage.getItem('cloudfin-theme');
    return (stored as Theme) || 'system';
  });
  const [resolvedTheme, setResolvedTheme] = useState<'light' | 'dark'>(() => resolveTheme(theme));

  useEffect(() => {
    const root = document.documentElement;
    root.classList.remove('theme-light', 'theme-dark');
    root.classList.add(`theme-${resolvedTheme}`);
  }, [resolvedTheme]);

  useEffect(() => {
    if (theme === 'system') {
      const mq = window.matchMedia('(prefers-color-scheme: dark)');
      const handler = () => setResolvedTheme(getSystemTheme());
      mq.addEventListener('change', handler);
      setResolvedTheme(getSystemTheme());
      return () => mq.removeEventListener('change', handler);
    } else {
      setResolvedTheme(theme);
    }
  }, [theme]);

  function setTheme(t: Theme) {
    setThemeState(t);
    localStorage.setItem('cloudfin-theme', t);
  }

  return (
    <ThemeContext.Provider value={{ theme, resolvedTheme, setTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  return useContext(ThemeContext);
}
