export type IconName =
  | 'activity' | 'alert' | 'arrow' | 'calendar' | 'chart' | 'check' | 'chevron' | 'clock'
  | 'close' | 'home' | 'inbox' | 'layers' | 'logout' | 'plus' | 'refresh' | 'search'
  | 'send' | 'settings' | 'shield' | 'ticket';

const iconPaths: Record<IconName, string> = {
  activity: 'M3 12h4l3-8 4 16 3-8h4',
  alert: 'M10.3 3.9 2.8 17a2 2 0 0 0 1.7 3h15a2 2 0 0 0 1.7-3l-7.5-13.1a2 2 0 0 0-3.4 0ZM12 9v4m0 4h.01',
  arrow: 'M5 12h14m-6-6 6 6-6 6',
  calendar: 'M8 2v4m8-4v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14H3V6a2 2 0 0 1 2-2Z',
  chart: 'M3 3v18h18M8 15l4-4 4 3 5-7',
  check: 'm5 12 4 4L19 6',
  chevron: 'm9 18 6-6-6-6',
  clock: 'M12 8v4l3 2m6-2a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z',
  close: 'm18 6-12 12M6 6l12 12',
  home: 'm3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7h-6v7H4a1 1 0 0 1-1-1Z',
  inbox: 'M4 4h16l2 11h-6l-2 3h-4l-2-3H2L4 4Zm0 0 2 11m12-11-2 11',
  layers: 'm12 3 9 5-9 5-9-5 9-5Zm-9 9 9 5 9-5m-18 5 9 5 9-5',
  logout: 'M10 17l5-5-5-5m5 5H3m9-9h6a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-6',
  plus: 'M12 5v14m-7-7h14',
  refresh: 'M20 7v5h-5M4 17v-5h5m-3.5-3A7 7 0 0 1 17.5 6L20 12M4 12l2.5 6A7 7 0 0 0 18.5 15',
  search: 'm20 20-4.5-4.5M18 10.5a7.5 7.5 0 1 1-15 0 7.5 7.5 0 0 1 15 0Z',
  send: 'm22 2-7 20-4-9-9-4 20-7ZM22 2 11 13',
  settings: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Zm0-6v3m0 14v3m10-10h-3M5 12H2m17.1-7.1-2.1 2.1M7 17l-2.1 2.1m14.2 0L17 17M7 7 4.9 4.9',
  shield: 'M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11Zm-3-11 2 2 4-4',
  ticket: 'M3 8V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v3a3 3 0 0 0 0 6v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-3a3 3 0 0 0 0-6Zm9-2v2m0 3v2m0 3v2'
};

export default function Icon({ name, className = '' }: { name: IconName; className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={iconPaths[name]} />
    </svg>
  );
}
