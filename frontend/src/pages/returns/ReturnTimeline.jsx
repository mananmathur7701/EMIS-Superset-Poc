// Icons: a tick for finished steps.
import { Check } from 'lucide-react';
// Status names.
import { statusLabel } from '../../components/StatusBadge';

// The review flow in order: each status and who holds the return then.
const STEPS = [
  // The registrar fills it in.
  { status: 'DRAFT', label: 'Registrar' },
  // The principal checks it.
  { status: 'WITH_PRINCIPAL', label: 'Principal' },
  // The DEO checks it.
  { status: 'WITH_DEO', label: 'DEO' },
  // The CPU analyst checks it.
  { status: 'WITH_CPU', label: 'CPU analyst' },
  // Final: it counts in dashboards.
  { status: 'FROZEN', label: 'Frozen' },
];

// How each history action is written, e.g. "approved".
const ACTIONS = {
  // The registrar saved without submitting.
  SAVED: 'saved a draft',
  // The registrar sent it to the principal.
  SUBMITTED: 'submitted it for review',
  // A reviewer moved it on.
  APPROVED: 'approved it',
  // A reviewer sent it one step back.
  RETURNED: 'sent it back',
};

// The steps of the review flow, with the current one highlighted.
export function WorkflowTracker({ status }) {
  // Where the return is now (a return not started yet is at the registrar's step).
  const current = status === 'NOT_STARTED' ? 0 : STEPS.findIndex((step) => step.status === status);
  // Now we will describe what to show.
  return (
    // The row of steps.
    <ol className="tracker">
      {/* One item per step. */}
      {STEPS.map((step, index) => {
        // Finished: before the current step (or every step once frozen).
        const done = index < current || status === 'FROZEN';
        // The step the return is at now.
        const active = index === current && status !== 'FROZEN';
        // Now we will draw the step.
        return (
          // The step, coloured by its state.
          <li key={step.status} className={done ? 'done' : active ? 'active' : ''}>
            {/* The circle: a tick when done, otherwise the step number. */}
            <span className="tracker-dot">{done ? <Check size={14} /> : index + 1}</span>
            {/* Who holds the return at this step. */}
            <span className="tracker-label">{step.label}</span>
          </li>
        );
      })}
    </ol>
  );
}

// The return's history, newest first: who did what, when, and their comment.
export function ReturnHistory({ history }) {
  // Nothing happened yet.
  if (history.length === 0) return <p className="muted">Nothing has happened yet.</p>;
  // Now we will list the events, newest first.
  return (
    // The list.
    <ul className="history">
      {/* One item per event (copy the list before reversing it). */}
      {[...history].reverse().map((event, index) => (
        // The event; a "sent back" is marked in amber.
        <li key={index} className={event.action === 'RETURNED' ? 'returned' : ''}>
          {/* Who did what, e.g. "Brenda Francis approved it → With DEO". */}
          <div>
            {/* The person. */}
            <strong>{event.personName ?? 'Someone'}</strong> {ACTIONS[event.action] ?? event.action}
            {/* Where it went, when it moved. */}
            {event.fromStatus !== event.toStatus && <span className="muted"> → {statusLabel(event.toStatus)}</span>}
          </div>
          {/* When, in the browser's local date and time. */}
          <div className="muted small">{new Date(event.createdAt).toLocaleString()}</div>
          {/* Their comment, if any. */}
          {event.comment && <blockquote>{event.comment}</blockquote>}
        </li>
      ))}
    </ul>
  );
}
