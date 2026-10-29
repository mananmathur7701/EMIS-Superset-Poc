// React's hook for remembering values.
import { useState } from 'react';
// Icons.
import { ClipboardCheck } from 'lucide-react';
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

// The reviewer's page (principal, DEO, CPU analyst): this year's returns they may see, the ones
// waiting for them first. Clicking a row opens the return.
export default function ReviewsPage() {
  // The list from the backend.
  const { data: items, error, reload } = useLoad('/api/returns/reviews');
  // Show only returns waiting for this user (true) or all of them (false).
  const [waitingOnly, setWaitingOnly] = useState(true);
  // The open return, or null while the list is shown.
  const [open, setOpen] = useState(null);
  // An error from opening a return.
  const [openError, setOpenError] = useState(null);

  // Opens one return.
  function openItem(item) {
    // Now we will log what the user did.
    log('return', `Opening return ${item.id} (${item.schoolName}, ${item.status})`);
    // Clear an old error.
    setOpenError(null);
    // Now we will load it with its answers and history.
    getJson(`/api/returns/${item.id}`).then(setOpen).catch((err) => setOpenError(err.message));
  }

  // A return is open: show it.
  if (open) return <ReturnView detail={open} onBack={() => { setOpen(null); reload(); }} onChanged={reload} />;

  // How many are waiting for this user.
  const waiting = items?.filter((item) => item.actionable).length ?? 0;
  // The rows to show.
  const rows = items?.filter((item) => !waitingOnly || item.actionable) ?? [];

  // Now we will describe the list.
  return (
    // The page content.
    <div className="page-content">
      {/* The filter buttons. */}
      <div className="toolbar">
        {/* Two buttons that work like tabs. */}
        <div className="segmented" role="tablist">
          {/* Waiting for me. */}
          <button type="button" className={waitingOnly ? 'active' : ''} onClick={() => setWaitingOnly(true)}>
            Waiting for me <span className="count">{waiting}</span>
          </button>
          {/* Everything I may see. */}
          <button type="button" className={waitingOnly ? '' : 'active'} onClick={() => setWaitingOnly(false)}>
            All returns <span className="count">{items?.length ?? 0}</span>
          </button>
        </div>
      </div>
      {/* Loading or error messages. */}
      {error && <p className="form-error">{error}</p>}
      {openError && <p className="form-error">{openError}</p>}
      {!items && !error && <p className="muted">Loading returns…</p>}
      {/* Nothing to show. */}
      {items && rows.length === 0 && (
        // An empty state.
        <div className="empty-state">
          {/* Icon. */}
          <ClipboardCheck size={32} />
          {/* Message. */}
          <p>{waitingOnly ? 'Nothing is waiting for your review.' : 'No returns yet this year.'}</p>
        </div>
      )}
      {/* The table. */}
      {rows.length > 0 && (
        // A white panel holding the table.
        <div className="surface table-panel">
          {/* The table. */}
          <table className="data-table">
            {/* Column headings. */}
            <thead>
              <tr><th>School</th><th>District</th><th>Form</th><th>Year</th><th>Status</th><th>Updated</th><th /></tr>
            </thead>
            {/* One row per return. */}
            <tbody>
              {rows.map((item) => (
                // Clicking the row opens it.
                <tr key={item.id} className="clickable" onClick={() => openItem(item)}>
                  {/* School name. */}
                  <td><strong>{item.schoolName}</strong></td>
                  {/* District code. */}
                  <td>{item.districtCode}</td>
                  {/* Form name. */}
                  <td>{item.formName}</td>
                  {/* Academic year. */}
                  <td>{item.academicYear}</td>
                  {/* Status pill. */}
                  <td><StatusBadge status={item.status} /></td>
                  {/* Last change, in local time. */}
                  <td className="muted">{new Date(item.updatedAt).toLocaleDateString()}</td>
                  {/* "Review" for the ones waiting for this user, "View" otherwise. */}
                  <td className="row-action">{item.actionable ? <span className="pill-action">Review</span> : 'View'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
