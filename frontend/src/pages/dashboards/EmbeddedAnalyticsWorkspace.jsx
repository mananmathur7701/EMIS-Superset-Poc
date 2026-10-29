// React's hooks: remember values, run code after the panel appears, and point at a div.
import { useEffect, useRef, useState } from 'react';
// Superset's official SDK for embedding dashboards.
import { embedDashboard } from '@superset-ui/embedded-sdk';
// Our helper for calling the backend.
import { getJson } from '../../api/api';
// The EMIS loading cover.
import Loader from '../../components/Loader';
// Browser-console logging.
import { log } from '../../logging/logger';

// Shows one published Superset dashboard (dashboardId is Superset's id). The backend gives us guest
// tokens that only let Superset return the logged-in user's rows, and only while their session is
// valid. onSessionEnded(message) is called when the backend says the session is over;
// onActivity() is called when the user changes a filter inside the dashboard.
export default function EmbeddedAnalyticsWorkspace({ dashboardId, onSessionEnded, onActivity }) {
  // Points at the div the dashboard is drawn into.
  const mountRef = useRef(null);
  // True until the dashboard is on screen.
  const [loading, setLoading] = useState(true);
  // An error message to show, if something went wrong.
  const [error, setError] = useState(null);

  // Run when the panel first appears (and again for another dashboard).
  useEffect(() => {
    // The backend address that gives out guest tokens for this dashboard.
    const tokenPath = `/api/analytics/auth/guest-token?dashboardId=${dashboardId}`;
    // Will hold the embedded dashboard, so we can remove it later.
    let dashboard;

    // Asks the backend for a guest token. A 401 means the user logged out or timed out.
    async function fetchGuestToken() {
      // Now we will log what the user did.
      log('dashboard', `Superset asked for a new visitor pass for dashboard ${dashboardId}`);
      // Catch the error so we can react to a 401.
      try {
        // Now we will ask the backend for a fresh token and return it.
        return (await getJson(tokenPath)).token;
      } catch (err) {
        // The session is over: go back to the login page (this also removes the dashboard).
        if (err.status === 401) onSessionEnded(err.message);
        // Pass the error on.
        throw err;
      }
    }

    // Gets the dashboard details and draws the dashboard.
    async function showDashboard() {
      // Now we will log what the user did.
      log('dashboard', `Embedding dashboard ${dashboardId}`);
      // Catch any error so we can show it on screen.
      try {
        // Now we will ask the backend for the dashboard id and where Superset is.
        const grant = await getJson(tokenPath);

        // Now we will let the SDK draw the dashboard inside our div.
        dashboard = await embedDashboard({
          // The dashboard's embed id.
          id: grant.dashboardId,
          // Where Superset is.
          supersetDomain: grant.supersetDomain,
          // The div to draw into.
          mountPoint: mountRef.current,
          // The SDK calls this for a token now, and again before each token expires.
          fetchGuestToken,
          // Hide the dashboard title and keep the filter bar closed.
          dashboardUiConfig: { hideTitle: true, filters: { expanded: false } },
        });

        // Changing a filter inside the dashboard counts as activity.
        dashboard.observeDataMask(() => onActivity());
        // The dashboard is on screen, so remove the loading message.
        setLoading(false);
      } catch (err) {
        // The session is over: go back to the login page.
        if (err.status === 401) {
          // Show the login page with the backend's message.
          onSessionEnded(err.message);
        } else {
          // Any other problem: show the message on the panel.
          setError(err.message);
        }
      }
    }

    // Now we will start loading the dashboard.
    showDashboard();
    // When the panel goes away (logout, timeout, account switch), remove the dashboard too.
    return () => dashboard?.unmount();
  }, [dashboardId, onSessionEnded, onActivity]);

  // Now we will describe what to show.
  return (
    // A box that fills the page.
    <div className="workspace">
      {/* The div the SDK draws the dashboard iframe into. */}
      <div ref={mountRef} className="dashboard-mount" />
      {/* While loading, cover the box with the EMIS spinner. */}
      {loading && !error && <Loader message="Loading dashboard…" />}
      {/* If something failed, cover the box with the error. */}
      {error && <div className="loading-cover error">{error}</div>}
    </div>
  );
}
