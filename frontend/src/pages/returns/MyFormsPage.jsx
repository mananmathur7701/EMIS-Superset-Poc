// React's hook for remembering values.
import { useState } from 'react';
// Icons.
import { CircleAlert, ClipboardPen } from 'lucide-react';
// Our helper for reading from the backend.
import { getJson } from '../../api/api';
// The return screen.
import ReturnView from './ReturnView';
// The status pill.
import StatusBadge from '../../components/StatusBadge';
// Reads the list when the page opens.
import useLoad from '../../api/useLoad';
// Browser-console logging.
import { log } from '../../logging/logger';

// The registrar's page: the forms their school must fill in this year. Clicking one opens it.
export default function MyFormsPage() {
  // The list from the backend.
  const { data: items, error, reload } = useLoad('/api/returns/mine');
  // The open return, or null while the list is shown.
  const [open, setOpen] = useState(null);
  // An error from opening a return.
  const [openError, setOpenError] = useState(null);

  // Opens one form for the school.
  async function openItem(item) {
    // Now we will log what the user did.
    log('return', `Opening ${item.formName} for ${item.schoolName} (${item.status})`);
    // Clear an old error.
    setOpenError(null);
    // Now we will load the form with the saved answers.
    getJson(`/api/returns/form/${item.formId}/school/${item.schoolId}`).then(setOpen).catch((err) => setOpenError(err.message));
  }

  // A return is open: show it.
  if (open) return <ReturnView detail={open} onBack={() => { setOpen(null); reload(); }} onChanged={reload} />;

  // Now we will describe the list.
  return (
    // The page content.
    <div className="page-content">
      {/* Loading or error messages. */}
      {error && <p className="form-error">{error}</p>}
      {openError && <p className="form-error">{openError}</p>}
      {!items && !error && <p className="muted">Loading your forms…</p>}
      {/* No forms for this school. */}
      {items && items.length === 0 && (
        // An empty state.
        <div className="empty-state">
          {/* Icon. */}
          <ClipboardPen size={32} />
          {/* Message. */}
          <p>No forms are open for your school right now.</p>
        </div>
      )}
      {/* One card per form. */}
      <div className="card-grid">
        {items?.map((item) => (
          // The card is a button that opens the form.
          <button key={`${item.formId}-${item.schoolId}`} type="button" className="tile" onClick={() => openItem(item)}>
            {/* Form name and status. */}
            <div className="tile-top">
              {/* e.g. "Annual School Census". */}
              <strong>{item.formName}</strong>
              {/* e.g. "Draft". */}
              <StatusBadge status={item.status} />
            </div>
            {/* School and year. */}
            <span className="muted">{item.schoolName} · {item.academicYear}</span>
            {/* A draft sent back: show why. */}
            {item.status === 'DRAFT' && item.lastReturnComment && (
              // An amber line.
              <span className="tile-warning"><CircleAlert size={14} /> Sent back: {item.lastReturnComment}</span>
            )}
            {/* What clicking does. */}
            <span className="tile-action">{item.actionable ? (item.id ? 'Continue' : 'Start') : 'View'} →</span>
          </button>
        ))}
      </div>
    </div>
  );
}
