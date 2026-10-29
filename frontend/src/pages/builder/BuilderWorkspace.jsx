// React's hooks: remember values, run code after drawing, and point at a value.
import { useEffect, useRef, useState } from 'react';
// Icons for the toolbar.
import { ChartColumnIncreasing, LayoutDashboard, Maximize2, Minimize2, Plus } from 'lucide-react';
// Our helper for calling the backend.
import { getJson } from '../../api/api';
// The EMIS loading cover.
import Loader from '../../components/Loader';
// Browser-console logging.
import { log } from '../../logging/logger';

// The builder's pages: a new chart on the census data, the saved charts, and the dashboards.
const BUILDER_PAGES = [
  // A new chart (Superset Explore).
  { page: 'explore', label: 'New chart', icon: Plus },
  // The list of saved charts.
  { page: 'charts', label: 'Charts', icon: ChartColumnIncreasing },
  // The list of dashboards.
  { page: 'dashboards', label: 'Dashboards', icon: LayoutDashboard },
];

// The CPU analyst's builder: Superset in an iframe. Every link comes from the backend and signs
// her in to Superset automatically, as her own Superset user. request ({ page, dashboardId }) lets
// another page open something here, e.g. "Edit in builder" on Dashboard access.
export default function BuilderWorkspace({ request }) {
  // The address loaded in the iframe.
  const [frameUrl, setFrameUrl] = useState(null);
  // The builder page open now.
  const [current, setCurrent] = useState('explore');
  // True until the iframe has finished loading.
  const [loading, setLoading] = useState(true);
  // An error message to show, if something went wrong.
  const [error, setError] = useState(null);
  // Superset's address, for signing out when the analyst leaves.
  const supersetDomain = useRef(null);
  // True while the builder covers the whole window (more room for Superset's preview).
  const [fullScreen, setFullScreen] = useState(false);

  // Opens a builder page: asks the backend for a fresh sign-in link and loads it.
  async function open(page, dashboardId) {
    // Now we will log what the user did.
    log('builder', `Opening Superset page "${page}"`, { dashboardId });
    // Show the loader and clear an old error.
    setLoading(true);
    setError(null);
    // Remember which page is open.
    setCurrent(page);
    // Catch errors so we can show them.
    try {
      // The backend address for this page (with the dashboard id for one dashboard).
      const path = `/api/analytics/builder?page=${page}${dashboardId ? `&dashboardId=${dashboardId}` : ''}`;
      // Now we will ask for the link.
      const builder = await getJson(path);
      // Remember Superset's address.
      supersetDomain.current = builder.supersetDomain;
      // Load the link in the iframe.
      setFrameUrl(builder.url);
    } catch (err) {
      // Show the message.
      setError(err.message);
    }
  }

  // When the builder first appears, open a new chart; when it goes away (sign out, timeout or
  // account switch), sign out of Superset too, through a hidden frame that opens /logout/.
  useEffect(() => {
    // Open a new chart.
    open('explore');
    // When the builder goes away ...
    return () => {
      // ... and Superset was used ...
      if (!supersetDomain.current) return;
      // ... make a hidden frame ...
      const frame = document.createElement('iframe');
      // ... that nobody sees ...
      frame.style.display = 'none';
      // ... open Superset's sign-out page in it ...
      frame.src = `${supersetDomain.current}/logout/`;
      // ... add it to the page ...
      document.body.appendChild(frame);
      // ... and remove it again after a few seconds.
      setTimeout(() => frame.remove(), 5000);
    };
  }, []);

  // When another page asks for something (e.g. "Edit in builder"), open it.
  useEffect(() => {
    // Nothing asked.
    if (!request) return;
    // Open the asked page.
    open(request.page, request.dashboardId);
  }, [request]);

  // Now we will describe what to show.
  return (
    // The builder: toolbar above, Superset below. In full screen it covers the whole window;
    // only its CSS changes, so Superset in the iframe does not reload.
    <div className={fullScreen ? 'superset-builder is-fullscreen' : 'superset-builder'}>
      {/* The toolbar. */}
      <div className="builder-toolbar">
        {/* One button per builder page. */}
        {BUILDER_PAGES.map((item) => (
          // The button; the open page is highlighted.
          <button key={item.page} type="button" className={current === item.page ? 'builder-tab active' : 'builder-tab'} onClick={() => open(item.page)}>
            {/* Icon. */}
            <item.icon size={16} /> {item.label}
          </button>
        ))}
        {/* A reminder of the next step. */}
        <span className="muted small builder-hint">Saved a dashboard? Publish it to roles under Dashboard access.</span>
        {/* Full screen on and off. */}
        <button type="button" className="builder-tab builder-fullscreen-button" onClick={() => setFullScreen(!fullScreen)}
          title={fullScreen ? 'Back to the normal page' : 'Use the whole window, so the chart preview has more room'}>
          {/* Shrink or expand icon. */}
          {fullScreen ? <Minimize2 size={16} /> : <Maximize2 size={16} />} {fullScreen ? 'Exit full screen' : 'Full screen'}
        </button>
      </div>
      {/* Superset. */}
      <div className="workspace">
        {/* The iframe; hide the loader when it has loaded. */}
        {frameUrl && <iframe src={frameUrl} title="Superset builder" onLoad={() => setLoading(false)} />}
        {/* While loading, cover the box with the EMIS spinner. */}
        {loading && !error && <Loader message="Opening the builder…" />}
        {/* If something failed, cover the box with the error. */}
        {error && <div className="loading-cover error">{error}</div>}
      </div>
    </div>
  );
}
