import { createContext, useContext, useState, ReactNode } from 'react';

interface GuideContextValue {
  guideDismissed: boolean;
  showGuide: () => void;
  dismissGuide: () => void;
}

const GuideContext = createContext<GuideContextValue | null>(null);

export function GuideProvider({ children }: { children: ReactNode }) {
  const [guideDismissed, setGuideDismissed] = useState(
    localStorage.getItem('installGuideDismissed') === 'true'
  );

  function showGuide() {
    localStorage.removeItem('installGuideDismissed');
    setGuideDismissed(false);
  }

  function dismissGuide() {
    localStorage.setItem('installGuideDismissed', 'true');
    setGuideDismissed(true);
  }

  return (
    <GuideContext.Provider value={{ guideDismissed, showGuide, dismissGuide }}>
      {children}
    </GuideContext.Provider>
  );
}

export function useGuide() {
  const ctx = useContext(GuideContext);
  if (!ctx) throw new Error('useGuide must be used within GuideProvider');
  return ctx;
}
