// React's hooks: remember values, run code after drawing, keep a stable function.
import { useCallback, useEffect, useState } from 'react';
// Icons.
import { ChartPie, RefreshCw } from 'lucide-react';
// The embedded Superset dashboard.
import EmbeddedAnalyticsWorkspace from './EmbeddedAnalyticsWorkspace';
// Reads the list when the page opens.
import useLoad from '../../api/useLoad';
// Browser-console logging.
import { log } from '../../logging/logger';

// Dashboards refresh by themselves every 15 minutes.
const REFRESH_INTERVAL_MS = 15 * 60 * 1000;

// The dashboards page: the dashboards published to the user's role, one at a time, with a
// "Refresh now" button. Refreshing loads the dashboard again, so newly frozen returns show up.
export default function DashboardsPage({ onSessionEnded, onActivity }) {
  // The dashboards this user may open.
  const { data: dashboards, error, reload } = useLoad('/api/analytics/dashboards');
  // The chosen dashboard's id (null means "the first one").
  const [selectedId, setSelectedId] = useState(null);
  // Goes up by one on every refresh; it is part of the dashboard's key, so a new value reloads it.
  const [refreshCount, setRefreshCount] = useState(0);
  // When the dashboard was last loaded.
  const [refreshedAt, setRefreshedAt] = useState(() => new Date());

  // Loads the list and the dashboard again.
  const refresh = useCallback(() => {
    // Now we will log what the user did.
    log('dashboard', 'Refreshing the dashboards (Refresh now or the 15-minute timer)');
    // Read the list again (the CPU analyst may have published another dashboard).
    reload();
    // Reload the dashboard.
    setRefreshCount((count) => count + 1);
    // Remember when.
    setRefreshedAt(new Date());
  }, [reload]);

  // Refresh every 15 minutes while the page exists (it stays in place while other pages are open).
  useEffect(() => {
    // Start the timer.
    const timer = setInterval(refresh, REFRESH_INTERVAL_MS);
    // Stop it when the page goes away.
    return () => clearInterval(timer);
  }, [refresh]);

  // The dashboard to show: the chosen one if it is still published, otherwise the first.
  const current = dashboards?.find((d) => d.dashboardId === selectedId) ?? dashboards?.[0];

  // Now we will describe the page.
  return (
    // The page: toolbar above, dashboard below.
    <div className="dashboards-page">
      {/* The toolbar. */}
      <div className="toolbar">
        {/* One tab per dashboard (just the title when there is only one). */}
        {dashboards && dashboards.length > 1 ? (
          // The tabs.
          <div className="segmented" role="tablist">
            {dashboards.map((dashboard) => (
              // One tab.
              <button key={dashboard.dashboardId} type="button" role="tab"
                className={dashboard.dashboardId === current?.dashboardId ? 'active' : ''}
                aria-selected={dashboard.dashboardId === current?.dashboardId}
                onClick={() => setSelectedId(dashboard.dashboardId)}>
                {dashboard.title}
              </button>
            ))}
          </div>
        ) : (
          // The only dashboard's title.
          <strong>{current?.title ?? ''}</strong>
        )}
        {/* When it was loaded, and the refresh button. */}
        <div className="toolbar-right">
          {/* e.g. "Updated 10:42 · refreshes every 15 min". */}
          <span className="muted small">
            Updated {refreshedAt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} · refreshes every 15 min
          </span>
          {/* Refresh now. */}
          <button type="button" className="button secondary" onClick={refresh}>
            {/* Circular arrows icon. */}
            <RefreshCw size={16} /> Refresh now
          </button>
        </div>
      </div>

      {/* The dashboard, or a message. */}
      <div className="dashboards-body">
        {/* A loading error. */}
        {error && <div className="loading-cover error">{error}</div>}
        {/* Nothing published to this role. */}
        {dashboards && dashboards.length === 0 && (
          // An empty state.
          <div className="empty-state">
            {/* Icon. */}
            <ChartPie size={32} />
            {/* Message. */}
            <p>No dashboard is published to your role yet.</p>
          </div>
        )}
        {/* The dashboard; a new key (other dashboard or a refresh) loads it again. */}
        {current && (
          // The embedded dashboard.
          <EmbeddedAnalyticsWorkspace key={`${current.dashboardId}-${refreshCount}`} dashboardId={current.dashboardId}
            onSessionEnded={onSessionEnded} onActivity={onActivity} />
        )}
      </div>
    </div>
  );
}
