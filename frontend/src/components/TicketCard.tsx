import { useEffect, useState } from 'react';
import Icon from './Icon';

export type TicketStatus = 'OPEN' | 'ASSIGNED' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
type Comment = { id: string; authorId: string; authorName: string; content: string; createdAt: string };
export type Ticket = {
  id: string;
  title: string;
  description: string;
  priority: string;
  status: TicketStatus;
  categoryName: string;
  locationName: string;
  reporterName: string;
  assigneeName?: string;
  createdAt?: string;
  updatedAt?: string;
  dueAt?: string;
  responseDeadline?: string;
  resolvedAt?: string;
  escalated?: boolean;
  resolutionNotes?: string;
  comments?: Comment[];
};

const nextStatuses: Record<TicketStatus, TicketStatus[]> = {
  OPEN: ['ASSIGNED', 'IN_PROGRESS', 'CLOSED'],
  ASSIGNED: ['IN_PROGRESS', 'RESOLVED', 'CLOSED'],
  IN_PROGRESS: ['RESOLVED', 'ASSIGNED', 'OPEN', 'CLOSED'],
  RESOLVED: ['OPEN', 'CLOSED'],
  CLOSED: ['OPEN']
};

function dateLabel(value?: string) {
  if (!value) return 'Recently created';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Recently created' : new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(date);
}

export default function TicketCard({ ticket, canManage, staff, busy, onOpen, onAssign, onStatus, compact = false }: {
  ticket: Ticket;
  canManage: boolean;
  staff: { id: string; fullName: string; role: string }[];
  busy: boolean;
  onOpen: (ticket: Ticket) => void;
  onAssign: (ticketId: string, assigneeId: string) => void;
  onStatus: (ticketId: string, status: TicketStatus) => void;
  compact?: boolean;
}) {
  const [assigneeId, setAssigneeId] = useState('');
  const [nextStatus, setNextStatus] = useState<TicketStatus>(ticket.status);
  useEffect(() => setNextStatus(ticket.status), [ticket.status]);
  const statusOptions = [ticket.status, ...nextStatuses[ticket.status]];
  return (
    <article className={`ticket-card ${compact ? 'ticket-card-compact' : ''}`}>
      <div className="ticket-card-main">
        <div className="ticket-card-heading">
          <div className="ticket-title-wrap">
            <span className={`priority-dot priority-dot-${ticket.priority.toLowerCase()}`} />
            <button className="ticket-title-button" onClick={() => void onOpen(ticket)}>{ticket.title}</button>
            {ticket.escalated && <span className="escalated-mark" title="Escalated">Escalated</span>}
          </div>
          <div className="ticket-labels"><span className={`status-badge status-${ticket.status.toLowerCase()}`}>{ticket.status.replace('_', ' ').toLowerCase()}</span><span className={`priority-badge priority-label-${ticket.priority.toLowerCase()}`}>{ticket.priority.toLowerCase()}</span></div>
        </div>
        {!compact && <p className="ticket-description">{ticket.description}</p>}
        <div className="ticket-meta">
          <span><Icon name="ticket" />{ticket.categoryName}</span>
          <span><Icon name="inbox" />{ticket.locationName}</span>
          {canManage && <span><Icon name="activity" />{ticket.assigneeName || 'Unassigned'}</span>}
          <span><Icon name="calendar" />{dateLabel(ticket.createdAt)}</span>
          {ticket.dueAt && <span className={new Date(ticket.dueAt).getTime() < Date.now() && !['RESOLVED', 'CLOSED'].includes(ticket.status) ? 'overdue-meta' : ''}><Icon name="clock" />Due {dateLabel(ticket.dueAt)}</span>}
        </div>
      </div>
      {canManage && <div className="ticket-actions">
        <label className="compact-control"><span className="sr-only">Assign {ticket.title}</span><select aria-label={`Assign ${ticket.title}`} value={assigneeId} onChange={(event) => setAssigneeId(event.target.value)}><option value="">Assign to…</option>{staff.map((person) => <option key={person.id} value={person.id}>{person.fullName} · {person.role.toLowerCase()}</option>)}</select></label>
        <button type="button" className="button-secondary small-action" disabled={busy || !assigneeId} onClick={() => onAssign(ticket.id, assigneeId)}>Assign</button>
        <label className="compact-control"><span className="sr-only">Update status for {ticket.title}</span><select aria-label={`Update status for ${ticket.title}`} value={nextStatus} onChange={(event) => setNextStatus(event.target.value as TicketStatus)}>{statusOptions.map((status) => <option key={status} value={status}>{status.replace('_', ' ').toLowerCase()}</option>)}</select></label>
        <button type="button" className="button-secondary small-action" disabled={busy || nextStatus === ticket.status} onClick={() => onStatus(ticket.id, nextStatus)}>Update</button>
      </div>}
      {!canManage && <button type="button" className="ticket-open-link" onClick={() => void onOpen(ticket)}>View details <Icon name="arrow" /></button>}
    </article>
  );
}
