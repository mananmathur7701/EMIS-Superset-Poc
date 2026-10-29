// React's hook for remembering values.
import { useState } from 'react';
// Icons.
import { ExternalLink } from 'lucide-react';
// Our helper for calling the backend.
import { putJson } from '../../api/api';
// Reads the list when the page opens.
import useLoad from '../../api/useLoad';
// Browser-console logging.
import { log } from '../../logging/logger';

// The roles a dashboard can be published to, with their column headings.
const ROLES = [
  // The Minister: whole country.
  { id: 'MINISTER', label: 'Minister' },
  // CPU analysts: whole country.
  { id: 'CPU_ANALYST', label: 'CPU analyst' },
  // DEOs: their district only.
  { id: 'DEO', label: 'DEO' },
  // Principals: their school only.
  { id: 'PRINCIPAL', label: 'Principal' },
];

// The CPU analyst's dashboard access page: every Superset dashboard, and the roles that may open it.
// onOpenBuilder(request) opens a dashboard in the chart builder.
export default function DashboardAccessPage({ onOpenBuilder }) {
  // The dashboards with their roles.
  const dashboards = useLoad('/api/admin/dashboards');
  // The dashboard being saved (its id), to show "Saving…".
  const [savingId, setSavingId] = useState(null);
  // A message after saving ({ text, error }).
  const [message, setMessage] = useState(null);

  // Ticks or unticks a role for a dashboard, and saves at once.
  async function toggleRole(dashboard, role) {
    // Now we will log what the user did.
    log('dashboard', `Toggling role ${role} on "${dashboard.title}"`);
    // The new list of roles.
    const roles = dashboard.roles.includes(role) ? dashboard.roles.filter((r) => r !== role) : [...dashboard.roles, role];
    // Show "Saving…" on this row and clear an old message.
    setSavingId(dashboard.id);
    setMessage(null);
    // Catch errors so we can show them.
    try {
      // Now we will publish the dashboard to those roles.
      await putJson(`/api/admin/dashboards/${dashboard.id}`, { title: dashboard.title, roles });
      // Read the list again.
      await dashboards.reload();
      // Say what happened.
      setMessage({ text: `"${dashboard.title}" is now ${roles.length ? `open to: ${roles.map((r) => ROLES.find((x) => x.id === r).label).join(', ')}` : 'unpublished'}.`, error: false });
    } catch (error) {
      // Show the backend's message.
      setMessage({ text: error.message, error: true });
    } finally {
      // Remove "Saving…".
      setSavingId(null);
    }
  }

  // Now we will describe the page.
  return (
    // The page content.
    <div className="page-content">
      {/* What this page does. */}
      <p className="notice">
        Build dashboards in the Chart builder, then tick who may open each one here. Row-level security still applies:
        DEOs see only their district and principals only their school.
      </p>
      {/* The result of saving. */}
      {message && <p className={message.error ? 'form-error' : 'notice success'}>{message.text}</p>}
      {/* A loading error. */}
      {dashboards.error && <p className="form-error">{dashboards.error}</p>}
      {/* Loading. */}
      {!dashboards.data && !dashboards.error && <p className="muted">Loading dashboards from Superset…</p>}
      {/* The table. */}
      {dashboards.data && (
        // A white panel holding the table.
        <div className="surface table-panel">
          {/* The table. */}
          <table className="data-table">
            {/* Column headings: dashboard, one per role, last change, open. */}
            <thead>
              <tr>
                <th>Dashboard</th>
                {ROLES.map((role) => <th key={role.id} className="center">{role.label}</th>)}
                <th>Changed</th>
                <th />
              </tr>
            </thead>
            {/* One row per dashboard. */}
            <tbody>
              {dashboards.data.map((dashboard) => (
                // The row.
                <tr key={dashboard.id}>
                  {/* Title, with "Saving…" while saving. */}
                  <td>
                    <strong>{dashboard.title}</strong>
                    {/* Superset's own draft flag. */}
                    {!dashboard.published && <span className="muted small"> (draft in Superset)</span>}
                    {/* Saving. */}
                    {savingId === dashboard.id && <div className="muted small">Saving…</div>}
                  </td>
                  {/* One checkbox per role. */}
                  {ROLES.map((role) => (
                    // The checkbox.
                    <td key={role.id} className="center">
                      <input type="checkbox" aria-label={`${role.label} may open ${dashboard.title}`}
                        checked={dashboard.roles.includes(role.id)} disabled={savingId !== null}
                        onChange={() => toggleRole(dashboard, role.id)} />
                    </td>
                  ))}
                  {/* When it last changed, e.g. "2 hours ago". */}
                  <td className="muted">{dashboard.changed}</td>
                  {/* Open it in the builder. */}
                  <td className="row-action">
                    <button type="button" className="button secondary small" onClick={() => onOpenBuilder({ page: 'dashboard', dashboardId: dashboard.id })}>
                      {/* Link icon. */}
                      <ExternalLink size={14} /> Edit in builder
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
