import { CSSProperties, FormEvent, ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import AuthPage, { Credentials } from './components/AuthPage';
import Icon, { IconName } from './components/Icon';
import TicketCard, { Ticket, TicketStatus } from './components/TicketCard';

type Dashboard = {
  totalTickets: number;
  openTickets: number;
  inProgressTickets: number;
  resolvedTickets: number;
  overdueTickets: number;
  averageResolutionHours: number;
  slaCompliancePercent: number;
  ticketsByCategory: Record<string, number>;
  ticketsByPriority: Record<string, number>;
};

type Option = { id: string; name: string; description?: string; active?: boolean };
type StaffOption = { id: string; fullName: string; role: string };
type SlaPolicy = {
  id: string;
  categoryName: string;
  priority: string;
  responseMinutes: number;
  resolutionMinutes: number;
  active: boolean;
};
type View = 'overview' | 'tickets' | 'settings';

const API_BASE = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api').replace(/\/$/, '');
const PAGE_SIZE = 50;

function roleFromToken(token: string | null): string | null {
  if (!token) return null;
  try {
    const encoded = token.split('.')[1];
    if (!encoded) return null;
    const normalized = encoded.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    return JSON.parse(atob(padded)).role ?? null;
  } catch {
    return null;
  }
}

function usernameFromToken(token: string | null): string {
  if (!token) return '';
  try {
    const payload = token.split('.')[1];
    if (!payload) return '';
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    return JSON.parse(atob(padded)).sub ?? '';
  } catch {
    return '';
  }
}

function errorMessage(body: string, fallback: string): string {
  try {
    const parsed = JSON.parse(body) as { detail?: string; message?: string; title?: string };
    return parsed.detail || parsed.message || parsed.title || fallback;
  } catch {
    return fallback;
  }
}

function App() {
  const [token, setToken] = useState(() => localStorage.getItem('campusdesk-token'));
  const [username, setUsername] = useState(() => usernameFromToken(localStorage.getItem('campusdesk-token')));
  const [view, setView] = useState<View>('overview');
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [ticketPage, setTicketPage] = useState(0);
  const [hasMoreTickets, setHasMoreTickets] = useState(false);
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [categories, setCategories] = useState<Option[]>([]);
  const [locations, setLocations] = useState<Option[]>([]);
  const [staff, setStaff] = useState<StaffOption[]>([]);
  const [policies, setPolicies] = useState<SlaPolicy[]>([]);
  const [selectedTicket, setSelectedTicket] = useState<Ticket | null>(null);
  const [searchDraft, setSearchDraft] = useState('');
  const [ticketSearch, setTicketSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [busy, setBusy] = useState(false);
  const [loadingTickets, setLoadingTickets] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [ticketTitle, setTicketTitle] = useState('');
  const [ticketDescription, setTicketDescription] = useState('');
  const [priority, setPriority] = useState('HIGH');
  const [categoryId, setCategoryId] = useState('');
  const [locationId, setLocationId] = useState('');
  const [categoryName, setCategoryName] = useState('');
  const [categoryDescription, setCategoryDescription] = useState('');
  const [locationName, setLocationName] = useState('');
  const [slaCategory, setSlaCategory] = useState('');
  const [slaPriority, setSlaPriority] = useState('HIGH');
  const [slaResponse, setSlaResponse] = useState('60');
  const [slaResolution, setSlaResolution] = useState('480');

  const role = roleFromToken(token);
  const isStaff = role === 'STAFF' || role === 'ADMIN';
  const isAdmin = role === 'ADMIN';
  const visibleTickets = useMemo(
    () => statusFilter === 'ALL' ? tickets : tickets.filter((ticket) => ticket.status === statusFilter),
    [statusFilter, tickets]
  );

  function logout(expired = false) {
    localStorage.removeItem('campusdesk-token');
    setToken(null);
    setUsername('');
    setTickets([]);
    setTicketPage(0);
    setHasMoreTickets(false);
    setDashboard(null);
    setStaff([]);
    setSelectedTicket(null);
    setView('overview');
    setError(expired ? 'Your session expired. Sign in again to continue.' : '');
    setMessage(expired ? '' : 'You have signed out.');
  }

  async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers = new Headers(init.headers);
    if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    if (token) headers.set('Authorization', `Bearer ${token}`);
    const response = await fetch(`${API_BASE}${path}`, { ...init, headers });
    if (response.status === 401 && token) {
      logout(true);
      throw new Error('Your session expired. Sign in again to continue.');
    }
    if (!response.ok) {
      const body = await response.text();
      throw new Error(errorMessage(body, `Request failed (${response.status})`));
    }
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
  }

  async function authenticate(credentials: Credentials) {
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const response = await fetch(`${API_BASE}/auth/${credentials.mode === 'register' ? 'register' : 'login'}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: credentials.username,
          password: credentials.password,
          ...(credentials.mode === 'register' ? { email: credentials.email, fullName: credentials.fullName } : {})
        })
      });
      if (!response.ok) {
        throw new Error(errorMessage(await response.text(), 'Unable to sign in. Check your details and try again.'));
      }
      const data = await response.json() as { token: string; username: string; role: string };
      localStorage.setItem('campusdesk-token', data.token);
      setUsername(data.username);
      setToken(data.token);
      setView('overview');
      setMessage(`Welcome, ${data.username}.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to complete authentication.');
    } finally {
      setBusy(false);
    }
  }

  async function loadTickets(page = 0, append = false, search = ticketSearch) {
    setLoadingTickets(true);
    setError('');
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
    if (search.trim()) params.set('search', search.trim());
    const endpoint = !search.trim() && role === 'STUDENT' ? '/tickets/my' : '/tickets';
    try {
      const result = await api<Ticket[]>(`${endpoint}?${params.toString()}`);
      setTickets((current) => append ? [...current, ...result] : result);
      setTicketPage(page);
      setHasMoreTickets(result.length === PAGE_SIZE);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load tickets.');
    } finally {
      setLoadingTickets(false);
    }
  }

  async function loadWorkspace() {
    if (!token) return;
    void loadTickets();
    const requests: Promise<void>[] = [
      api<Option[]>('/categories').then((data) => {
        setCategories(data.filter((item) => item.active !== false));
        if (data.length && !categoryId) setCategoryId(data[0].id);
      }),
      api<Option[]>('/locations').then((data) => {
        setLocations(data.filter((item) => item.active !== false));
        if (data.length && !locationId) setLocationId(data[0].id);
      })
    ];
    if (isStaff) {
      requests.push(
        api<Dashboard>('/dashboard').then(setDashboard),
        api<StaffOption[]>('/admin/users').then(setStaff)
      );
    }
    if (isAdmin) {
      requests.push(api<SlaPolicy[]>('/admin/sla-policies').then(setPolicies));
    }
    const results = await Promise.allSettled(requests);
    const failed = results.find((result): result is PromiseRejectedResult => result.status === 'rejected');
    if (failed && !error) {
      setError(failed.reason instanceof Error ? failed.reason.message : 'Some workspace data could not be loaded.');
    }
  }

  useEffect(() => {
    if (token) void loadWorkspace();
  }, [token]);

  async function createTicket(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await api<Ticket>('/tickets', {
        method: 'POST',
        body: JSON.stringify({ title: ticketTitle, description: ticketDescription, priority, categoryId, locationId })
      });
      setTicketTitle('');
      setTicketDescription('');
      setMessage('Your issue has been submitted.');
      await loadTickets(0, false, ticketSearch);
      if (isStaff) setDashboard(await api<Dashboard>('/dashboard'));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create the ticket.');
    } finally {
      setBusy(false);
    }
  }

  async function assignTicket(ticketId: string, assigneeId: string) {
    setBusy(true);
    setError('');
    try {
      await api<Ticket>(`/tickets/${ticketId}/assign`, { method: 'POST', body: JSON.stringify({ assigneeId }) });
      setMessage('Ticket assignment updated.');
      await loadTickets(0, false, ticketSearch);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not assign the ticket.');
    } finally {
      setBusy(false);
    }
  }

  async function updateStatus(ticketId: string, status: TicketStatus) {
    setBusy(true);
    setError('');
    try {
      await api<Ticket>(`/tickets/${ticketId}/status`, { method: 'PATCH', body: JSON.stringify({ status }) });
      setMessage('Ticket status updated.');
      await Promise.all([loadTickets(0, false, ticketSearch), refreshDashboard()]);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update ticket status.');
    } finally {
      setBusy(false);
    }
  }

  async function refreshDashboard() {
    if (isStaff) {
      try {
        setDashboard(await api<Dashboard>('/dashboard'));
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Could not refresh the dashboard.');
      }
    }
  }

  async function openTicket(ticket: Ticket) {
    setSelectedTicket(ticket);
    try {
      setSelectedTicket(await api<Ticket>(`/tickets/${ticket.id}`));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load ticket details.');
    }
  }

  async function addComment(ticketId: string, content: string) {
    setBusy(true);
    setError('');
    try {
      const updated = await api<Ticket>(`/tickets/${ticketId}/comments`, {
        method: 'POST',
        body: JSON.stringify({ content })
      });
      setSelectedTicket(updated);
      setTickets((current) => current.map((ticket) => ticket.id === ticketId ? updated : ticket));
      setMessage('Comment added.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not add comment.');
    } finally {
      setBusy(false);
    }
  }

  async function submitAdminForm(event: FormEvent<HTMLFormElement>, endpoint: string, body: object, successMessage: string) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await api(endpoint, { method: 'POST', body: JSON.stringify(body) });
      setMessage(successMessage);
      const [nextCategories, nextLocations, nextPolicies] = await Promise.all([
        api<Option[]>('/admin/categories'),
        api<Option[]>('/admin/locations'),
        api<SlaPolicy[]>('/admin/sla-policies')
      ]);
      setCategories(nextCategories.filter((item) => item.active !== false));
      setLocations(nextLocations.filter((item) => item.active !== false));
      setPolicies(nextPolicies);
      setCategoryName('');
      setCategoryDescription('');
      setLocationName('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save the setting.');
    } finally {
      setBusy(false);
    }
  }

  if (!token) {
    return <AuthPage busy={busy} error={error} message={message} onAuthenticate={authenticate} />;
  }

  const heading = view === 'settings' ? 'Workspace settings' : view === 'tickets' ? 'Ticket queue' : isStaff ? 'Operations overview' : 'Your campus, in order';

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#" onClick={(event) => { event.preventDefault(); setView('overview'); }}>
          <span className="brand-mark"><Icon name="layers" /></span>
          <span>campus<span className="brand-light">desk</span><small>ISSUE MANAGEMENT</small></span>
        </a>
        <div className="nav-label">WORKSPACE</div>
        <nav className="primary-nav" aria-label="Main navigation">
          <button aria-label="Overview" title="Overview" className={view === 'overview' ? 'active' : ''} onClick={() => setView('overview')}><Icon name="home" />Overview</button>
          <button aria-label={isStaff ? 'Ticket queue' : 'My tickets'} title={isStaff ? 'Ticket queue' : 'My tickets'} className={view === 'tickets' ? 'active' : ''} onClick={() => setView('tickets')}><Icon name="ticket" />{isStaff ? 'Ticket queue' : 'My tickets'}<span className="nav-count">{tickets.length || ''}</span></button>
          {isAdmin && <button aria-label="Administration" title="Administration" className={view === 'settings' ? 'active' : ''} onClick={() => setView('settings')}><Icon name="settings" />Administration</button>}
        </nav>
        <div className="sidebar-note">
          <span className="note-icon"><Icon name="shield" /></span>
          <strong>One campus. Better service.</strong>
          <p>Report issues and follow every step toward resolution.</p>
        </div>
        <div className="profile-row">
          <span className="avatar">{username.slice(0, 1).toUpperCase()}</span>
          <span className="profile-copy"><strong>{username}</strong><small>{role?.toLowerCase()}</small></span>
          <button className="icon-button logout-button" aria-label="Sign out" title="Sign out" onClick={() => logout()}><Icon name="logout" /></button>
        </div>
      </aside>

      <main className="main-panel">
        <header className="page-header">
          <div>
            <div className="breadcrumb">CampusDesk <span>/</span> {view === 'settings' ? 'Administration' : view === 'tickets' ? 'Tickets' : 'Overview'}</div>
            <h1>{heading}</h1>
            <p>{view === 'settings' ? 'Manage the service catalog and response commitments.' : view === 'tickets' ? 'Search, review, and take action on service requests.' : isStaff ? 'A clear view of requests, service levels, and what needs attention.' : 'Get the help you need and keep track of every request.'}</p>
          </div>
          <div className="header-actions">
            <span className="role-chip"><span className="online-dot" />{role?.toLowerCase()}</span>
            <button className="icon-button refresh-button" aria-label="Refresh workspace" title="Refresh" onClick={() => void loadWorkspace()}><Icon name="refresh" /></button>
          </div>
        </header>

        {(error || message) && (
          <div className={`notice ${error ? 'notice-error' : 'notice-success'}`} role={error ? 'alert' : 'status'}>
            <Icon name={error ? 'alert' : 'check'} />
            <span>{error || message}</span>
            <button className="notice-close" aria-label="Dismiss notification" onClick={() => { setError(''); setMessage(''); }}><Icon name="close" /></button>
          </div>
        )}

        {view === 'overview' && (
          <>
            {isStaff && <DashboardOverview dashboard={dashboard} onOpenTickets={() => setView('tickets')} />}
            <section className={`workspace-grid ${isStaff ? 'workspace-operations' : ''}`}>
              <CreateTicket
                title={ticketTitle} description={ticketDescription} priority={priority} categoryId={categoryId} locationId={locationId}
                categories={categories} locations={locations} busy={busy}
                setTitle={setTicketTitle} setDescription={setTicketDescription} setPriority={setPriority}
                setCategoryId={setCategoryId} setLocationId={setLocationId} onSubmit={createTicket}
              />
              <section className="panel recent-panel">
                <div className="section-heading">
                  <div><span className="section-kicker">{isStaff ? 'INCOMING WORK' : 'YOUR REQUESTS'}</span><h2>{isStaff ? 'Recent tickets' : 'Your latest tickets'}</h2></div>
                  <button className="text-button" onClick={() => setView('tickets')}>View all <Icon name="arrow" /></button>
                </div>
                <div className="ticket-stack">
                  {tickets.slice(0, 4).map((ticket) => (
                    <TicketCard key={ticket.id} ticket={ticket} canManage={isStaff} staff={staff} busy={busy}
                      onOpen={openTicket} onAssign={assignTicket} onStatus={updateStatus} compact />
                  ))}
                  {!tickets.length && <EmptyState title="A fresh start" text="No requests to show yet. Create a ticket and it will appear here." />}
                </div>
              </section>
            </section>
          </>
        )}

        {view === 'tickets' && (
          <section className="panel queue-panel">
            <div className="queue-toolbar">
              <form className="search-form" onSubmit={(event) => { event.preventDefault(); setTicketSearch(searchDraft); setStatusFilter('ALL'); void loadTickets(0, false, searchDraft); }}>
                <Icon name="search" />
                <input aria-label="Search tickets" value={searchDraft} onChange={(event) => setSearchDraft(event.target.value)} placeholder="Search by title or description" maxLength={200} />
                {searchDraft && <button className="clear-search" type="button" aria-label="Clear search" onClick={() => { setSearchDraft(''); setTicketSearch(''); void loadTickets(0, false, ''); }}><Icon name="close" /></button>}
              </form>
              <label className="filter-control"><span>Status</span>
                <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
                  <option value="ALL">All statuses</option>
                  {['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'].map((status) => <option key={status} value={status}>{status.replace('_', ' ')}</option>)}
                </select>
              </label>
            </div>
            <div className="queue-summary"><span>{ticketSearch ? `Results for “${ticketSearch}”` : 'Newest requests first'}</span><span>{visibleTickets.length} loaded</span></div>
            <div className="ticket-stack queue-stack">
              {visibleTickets.map((ticket) => (
                <TicketCard key={ticket.id} ticket={ticket} canManage={isStaff} staff={staff} busy={busy}
                  onOpen={openTicket} onAssign={assignTicket} onStatus={updateStatus} />
              ))}
              {!loadingTickets && visibleTickets.length === 0 && <EmptyState title="No matching tickets" text={ticketSearch || statusFilter !== 'ALL' ? 'Try a different search or status filter.' : 'There are no tickets in this view yet.'} />}
              {loadingTickets && <div className="loading-line"><span className="spinner" />Loading tickets…</div>}
            </div>
            {hasMoreTickets && <button className="button-secondary load-more" disabled={loadingTickets} onClick={() => void loadTickets(ticketPage + 1, true)}>{loadingTickets ? 'Loading…' : 'Load more tickets'}<Icon name="chevron" /></button>}
          </section>
        )}

        {view === 'settings' && isAdmin && (
          <section className="admin-grid">
            <AdminForm title="Issue categories" description="Organize requests into clear service areas."
              onSubmit={(event) => void submitAdminForm(event, '/admin/categories', { name: categoryName, description: categoryDescription }, 'Category created.')}>
              <Field label="Category name"><input required maxLength={80} value={categoryName} onChange={(event) => setCategoryName(event.target.value)} placeholder="e.g. Classroom maintenance" /></Field>
              <Field label="Description"><input maxLength={255} value={categoryDescription} onChange={(event) => setCategoryDescription(event.target.value)} placeholder="What belongs in this category?" /></Field>
              <button className="button-primary" disabled={busy || !categoryName.trim()}><Icon name="plus" />Add category</button>
              <SimpleList items={categories.map((item) => item.name)} empty="No categories configured." />
            </AdminForm>
            <AdminForm title="Campus locations" description="Keep ticket locations consistent and searchable."
              onSubmit={(event) => void submitAdminForm(event, '/admin/locations', { name: locationName }, 'Location created.')}>
              <Field label="Location name"><input required maxLength={80} value={locationName} onChange={(event) => setLocationName(event.target.value)} placeholder="e.g. North library" /></Field>
              <button className="button-primary" disabled={busy || !locationName.trim()}><Icon name="plus" />Add location</button>
              <SimpleList items={locations.map((item) => item.name)} empty="No locations configured." />
            </AdminForm>
            <AdminForm title="SLA policy" description="Set response and resolution targets by category and priority."
              onSubmit={(event) => void submitAdminForm(event, '/admin/sla-policies', { categoryId: slaCategory, priority: slaPriority, responseMinutes: Number(slaResponse), resolutionMinutes: Number(slaResolution) }, 'SLA policy created.')}>
              <Field label="Category"><select required value={slaCategory} onChange={(event) => setSlaCategory(event.target.value)}><option value="">Choose a category</option>{categories.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></Field>
              <div className="form-two">
                <Field label="Priority"><select value={slaPriority} onChange={(event) => setSlaPriority(event.target.value)}>{['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((item) => <option key={item}>{item}</option>)}</select></Field>
                <Field label="Response (minutes)"><input type="number" min="1" required value={slaResponse} onChange={(event) => setSlaResponse(event.target.value)} /></Field>
              </div>
              <Field label="Resolution target (minutes)"><input type="number" min={Number(slaResponse) || 1} required value={slaResolution} onChange={(event) => setSlaResolution(event.target.value)} /></Field>
              <button className="button-primary" disabled={busy || !slaCategory}><Icon name="plus" />Add SLA policy</button>
              <div className="policy-list">{policies.map((policy) => <div className="policy-row" key={policy.id}><span><strong>{policy.categoryName}</strong><small>{policy.priority.toLowerCase()} priority</small></span><span><strong>{formatDuration(policy.responseMinutes)}</strong><small>respond</small></span><span><strong>{formatDuration(policy.resolutionMinutes)}</strong><small>resolve</small></span></div>)}</div>
            </AdminForm>
          </section>
        )}

        <footer className="page-footer">CampusDesk <span>·</span> Campus services, made clearer.</footer>
      </main>

      {selectedTicket && <TicketDialog ticket={selectedTicket} busy={busy} onClose={() => setSelectedTicket(null)} onComment={addComment} />}
    </div>
  );
}

function DashboardOverview({ dashboard, onOpenTickets }: { dashboard: Dashboard | null; onOpenTickets: () => void }) {
  const cards: { label: string; value: number | string; icon: IconName; tone: string }[] = [
    { label: 'All requests', value: dashboard?.totalTickets ?? '—', icon: 'ticket', tone: 'blue' },
    { label: 'Open', value: dashboard?.openTickets ?? '—', icon: 'inbox', tone: 'amber' },
    { label: 'In progress', value: dashboard?.inProgressTickets ?? '—', icon: 'activity', tone: 'violet' },
    { label: 'Resolved', value: dashboard?.resolvedTickets ?? '—', icon: 'check', tone: 'green' },
    { label: 'Overdue', value: dashboard?.overdueTickets ?? '—', icon: 'clock', tone: 'rose' }
  ];
  const priorityEntries = Object.entries(dashboard?.ticketsByPriority ?? {});
  const maxPriority = Math.max(1, ...priorityEntries.map(([, count]) => count));
  const categoryEntries = Object.entries(dashboard?.ticketsByCategory ?? {});
  const maxCategory = Math.max(1, ...categoryEntries.map(([, count]) => count));
  return (
    <>
      <section className="stats-grid">
        {cards.map((card) => <div className="stat-card" key={card.label}><span className={`stat-icon ${card.tone}`}><Icon name={card.icon} /></span><span className="stat-label">{card.label}</span><strong>{card.value}</strong></div>)}
      </section>
      <section className="insight-grid">
        <div className="panel sla-panel">
          <div className="section-heading"><div><span className="section-kicker">SERVICE LEVEL</span><h2>SLA compliance</h2></div><span className="metric-chip"><Icon name="shield" />Current</span></div>
          <div className="sla-content">
            <div className="sla-ring" style={{ '--progress': `${Math.max(0, Math.min(100, dashboard?.slaCompliancePercent ?? 0))}%` } as CSSProperties}>
              <div><strong>{dashboard?.slaCompliancePercent?.toFixed(1) ?? '—'}<small>%</small></strong><span>within target</span></div>
            </div>
            <div className="sla-copy"><strong>{dashboard?.overdueTickets ?? '—'} overdue requests</strong><p>Requests currently beyond their resolution deadline.</p><button className="text-button" onClick={onOpenTickets}>Review ticket queue <Icon name="arrow" /></button></div>
          </div>
        </div>
        <div className="panel priority-panel">
          <div className="section-heading"><div><span className="section-kicker">QUEUE MIX</span><h2>By priority</h2></div><Icon name="chart" /></div>
          <div className="bar-list">
            {priorityEntries.length ? priorityEntries.map(([label, count]) => <div className="bar-row" key={label}><span>{label.toLowerCase()}</span><div className="bar-track"><span className={`bar-fill priority-${label.toLowerCase()}`} style={{ width: `${count / maxPriority * 100}%` }} /></div><strong>{count}</strong></div>) : <p className="muted">Priority data will appear as tickets are submitted.</p>}
          </div>
          {categoryEntries.length > 0 && <div className="category-breakdown"><span className="section-kicker">BY CATEGORY</span>{categoryEntries.slice(0, 4).map(([label, count]) => <div className="bar-row" key={label}><span title={label}>{label}</span><div className="bar-track"><span className="bar-fill category-fill" style={{ width: `${count / maxCategory * 100}%` }} /></div><strong>{count}</strong></div>)}</div>}
          <div className="avg-resolution"><span>Average resolution</span><strong>{dashboard && dashboard.resolvedTickets > 0 ? `${dashboard.averageResolutionHours.toFixed(1)}h` : '—'}</strong></div>
        </div>
      </section>
    </>
  );
}

function CreateTicket(props: {
  title: string; description: string; priority: string; categoryId: string; locationId: string;
  categories: Option[]; locations: Option[]; busy: boolean;
  setTitle: (value: string) => void; setDescription: (value: string) => void; setPriority: (value: string) => void;
  setCategoryId: (value: string) => void; setLocationId: (value: string) => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <section className="panel create-panel">
      <div className="section-heading"><div><span className="section-kicker">NEED A HAND?</span><h2>Report an issue</h2></div><span className="panel-icon"><Icon name="plus" /></span></div>
      <p className="panel-intro">Share the details and the right campus team will take it from here.</p>
      <form className="form-stack" onSubmit={props.onSubmit}>
        <Field label="What's the issue?"><input required minLength={3} maxLength={120} value={props.title} onChange={(event) => props.setTitle(event.target.value)} placeholder="A short, specific title" /></Field>
        <Field label="Description"><textarea required minLength={3} maxLength={2000} rows={4} value={props.description} onChange={(event) => props.setDescription(event.target.value)} placeholder="What happened? Include any details that could help." /></Field>
        <div className="form-two">
          <Field label="Category"><select required value={props.categoryId} onChange={(event) => props.setCategoryId(event.target.value)}><option value="">Choose category</option>{props.categories.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></Field>
          <Field label="Priority"><select value={props.priority} onChange={(event) => props.setPriority(event.target.value)}>{['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((item) => <option key={item} value={item}>{item.charAt(0) + item.slice(1).toLowerCase()}</option>)}</select></Field>
        </div>
        <Field label="Campus location"><select required value={props.locationId} onChange={(event) => props.setLocationId(event.target.value)}><option value="">Choose location</option>{props.locations.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></Field>
        <button className="button-primary submit-ticket" disabled={props.busy}><Icon name="send" />{props.busy ? 'Submitting…' : 'Submit request'}<Icon name="arrow" /></button>
      </form>
    </section>
  );
}

function TicketDialog({ ticket, busy, onClose, onComment }: { ticket: Ticket; busy: boolean; onClose: () => void; onComment: (ticketId: string, content: string) => Promise<void> }) {
  const [content, setContent] = useState('');
  const closeButton = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    closeButton.current?.focus();
  }, []);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onComment(ticket.id, content.trim());
    setContent('');
  }
  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section className="ticket-dialog" role="dialog" aria-modal="true" aria-labelledby="ticket-dialog-title"
        onKeyDown={(event) => { if (event.key === 'Escape') onClose(); }}>
        <header className="dialog-header"><div><span className="section-kicker">REQUEST DETAILS</span><h2 id="ticket-dialog-title">{ticket.title}</h2></div><button ref={closeButton} className="icon-button" aria-label="Close details" onClick={onClose}><Icon name="close" /></button></header>
        <div className="dialog-meta"><StatusBadge status={ticket.status} /><PriorityBadge priority={ticket.priority} /><span>{ticket.categoryName}</span><span>{ticket.locationName}</span></div>
        <p className="dialog-description">{ticket.description}</p>
        {ticket.resolutionNotes && <div className="resolution-note"><strong>Resolution notes</strong><p>{ticket.resolutionNotes}</p></div>}
        <div className="dialog-dates"><span><Icon name="calendar" />Created {formatDate(ticket.createdAt)}</span>{ticket.responseDeadline && <span><Icon name="clock" />Response by {formatDate(ticket.responseDeadline)}</span>}{ticket.dueAt && <span><Icon name="clock" />Resolve by {formatDate(ticket.dueAt)}</span>}</div>
        <div className="comments-section"><div className="section-heading"><div><span className="section-kicker">CONVERSATION</span><h3>Updates & comments</h3></div><span className="comment-count">{ticket.comments?.length ?? 0}</span></div>
          <div className="comment-list">{ticket.comments?.map((comment) => <article className="comment-item" key={comment.id}><div className="comment-avatar">{comment.authorName.slice(0, 1).toUpperCase()}</div><div><div className="comment-byline"><strong>{comment.authorName}</strong><time>{formatDate(comment.createdAt)}</time></div><p>{comment.content}</p></div></article>)}{!ticket.comments?.length && <p className="muted">No comments yet. Add a note to keep everyone in the loop.</p>}</div>
          <form className="comment-form" onSubmit={submit}><textarea required maxLength={2000} rows={3} value={content} onChange={(event) => setContent(event.target.value)} placeholder="Write a comment…" /><button className="button-primary" disabled={busy || !content.trim()}><Icon name="send" />Add comment</button></form>
        </div>
      </section>
    </div>
  );
}

function AdminForm({ title, description, onSubmit, children }: { title: string; description: string; onSubmit: (event: FormEvent<HTMLFormElement>) => void; children: ReactNode }) {
  return <section className="panel admin-form-panel"><div className="section-heading"><div><span className="section-kicker">ADMINISTRATION</span><h2>{title}</h2></div></div><p className="panel-intro">{description}</p><form className="form-stack" onSubmit={onSubmit}>{children}</form></section>;
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="field"><span>{label}</span>{children}</label>;
}

function SimpleList({ items, empty }: { items: string[]; empty: string }) {
  return <div className="simple-list">{items.length ? items.map((item) => <span key={item}><Icon name="check" />{item}</span>) : <p className="muted">{empty}</p>}</div>;
}

function EmptyState({ title, text }: { title: string; text: string }) {
  return <div className="empty-state"><span><Icon name="inbox" /></span><strong>{title}</strong><p>{text}</p></div>;
}

function StatusBadge({ status }: { status: string }) {
  return <span className={`status-badge status-${status.toLowerCase()}`}>{status.replace('_', ' ').toLowerCase()}</span>;
}

function PriorityBadge({ priority }: { priority: string }) {
  return <span className={`priority-badge priority-label-${priority.toLowerCase()}`}>{priority.toLowerCase()}</span>;
}

function formatDate(value?: string) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

function formatDuration(minutes: number) {
  if (minutes < 60) return `${minutes}m`;
  if (minutes % 1440 === 0) return `${minutes / 1440}d`;
  if (minutes % 60 === 0) return `${minutes / 60}h`;
  return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
}

export default App;
