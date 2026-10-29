// React's hooks: remember values, run code after drawing, keep stable functions and values.
import { useCallback, useEffect, useRef, useState } from 'react';
// An icon for the empty page.
import { Inbox } from 'lucide-react';
// Our helpers for calling the backend.
import { getJson, postJson, setSessionEndedHandler } from './api/api';
// Superset's builder in an iframe, for CPU analysts.
import BuilderWorkspace from './pages/builder/BuilderWorkspace';
// The CPU analyst's dashboard access page.
import DashboardAccessPage from './pages/admin/DashboardAccessPage';
// The published dashboards.
import DashboardsPage from './pages/dashboards/DashboardsPage';
// The CPU analyst's field library.
import FieldLibraryPage from './pages/admin/FieldLibraryPage';
// The CPU analyst's form builder.
import FormBuilderPage from './pages/admin/FormBuilderPage';
// The CPU analyst's KPI registry.
import KpiRegistryPage from './pages/admin/KpiRegistryPage';
// A full-page loading cover.
import Loader from './components/Loader';
// The login page.
import LoginScreen from './auth/LoginScreen';
// The registrar's forms.
import MyFormsPage from './pages/returns/MyFormsPage';
// The app's pages and who may open them.
import { pagesFor } from './layout/pages';
// The reviewers' page.
import ReviewsPage from './pages/returns/ReviewsPage';
// The role switcher in the top bar.
import RoleSwitcher from './auth/RoleSwitcher';
// The left-hand menu.
import Sidebar from './layout/Sidebar';
// Browser-console logging.
import { log } from './logging/logger';

// Send at most one "the user is active" message per minute.
const ACTIVITY_PING_INTERVAL_MS = 60 * 1000;
// Browser events that mean the user is doing something. "blur" fires when they click into a
// Superset iframe, whose own clicks the page cannot see.
const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'wheel', 'blur'];

// The page shown to users who may open no page at all (teachers and students).
const NO_PAGE = { id: 'none', group: 'EMIS', title: 'Nothing to show', description: '' };

// The first page a user may open, or "none".
function firstPage(viewer) {
  // The id of the first page in menu order.
  return pagesFor(viewer)[0]?.id ?? 'none';
}

// The whole app on one screen: the login page, or the EMIS shell with the user's page.
export default function CensusAnalyticsDashboard() {
  // The logged-in user: undefined while we check, null when logged out.
  const [viewer, setViewer] = useState(undefined);
  // A message for the login page, e.g. "Your session timed out".
  const [notice, setNotice] = useState(null);
  // The open page's id, e.g. "dashboards" (set after login).
  const [page, setPage] = useState('none');
  // A request for the builder from another page, e.g. "Edit in builder" ({ page, dashboardId }).
  const [builderRequest, setBuilderRequest] = useState(null);
  // When we last told the backend the user was active.
  const lastActivitySent = useRef(0);

  // Run once, when the app opens: are we already logged in (e.g. after a page reload)?
  useEffect(() => {
    // Now we will ask the backend who is logged in.
    getJson('/api/auth/me')
      // Logged in: remember the user and open their first page.
      .then((user) => {
        // Now we will log who is still logged in.
        log('session', `Already logged in as ${user.username} (${user.roles.join(', ')})`);
        // Open their first page.
        setPage(firstPage(user));
        // Remember the user.
        setViewer(user);
      })
      // Not logged in (or timed out): show the login page.
      .catch(() => {
        // Now we will log that the login page is needed.
        log('session', 'Nobody logged in: showing the login page');
        // Show the login page.
        setViewer(null);
      });
  }, []);

  // Goes back to the login page with a message. Removing the pages also removes the dashboard.
  const endSession = useCallback((message) => {
    // Now we will log why the session ended.
    log('session', `Session ended: ${message}`);
    // Forget the user, which shows the login page.
    setViewer(null);
    // Show why.
    setNotice(message);
    // Forget the open page.
    setPage('none');
  }, []);

  // Any backend call that finds the session over (HTTP 401) goes back to the login page.
  useEffect(() => {
    // Register endSession with the API helper.
    setSessionEndedHandler(endSession);
  }, [endSession]);

  // Tells the backend the user is active, at most once a minute; this restarts its idle clock.
  const recordActivity = useCallback(() => {
    // Too soon since the last message: do nothing.
    if (Date.now() - lastActivitySent.current < ACTIVITY_PING_INTERVAL_MS) return;
    // Remember that we are sending one now.
    lastActivitySent.current = Date.now();
    // Now we will log the activity ping.
    log('session', 'User is active: telling the backend (restarts the idle clock)');
    // Now we will tell the backend; if the session already ended, go to the login page.
    postJson('/api/auth/activity').catch((error) => {
      // HTTP 401 means the session ended (timed out or logged out elsewhere).
      if (error.status === 401) endSession(error.message);
    });
  }, [endSession]);

  // While someone is logged in, listen for clicks, keys and scrolling anywhere on the page.
  useEffect(() => {
    // Nothing to track when nobody is logged in.
    if (!viewer) return;
    // Now we will start listening for each activity event.
    ACTIVITY_EVENTS.forEach((name) => window.addEventListener(name, recordActivity));
    // When logged out (or the app closes), stop listening.
    return () => ACTIVITY_EVENTS.forEach((name) => window.removeEventListener(name, recordActivity));
  }, [viewer, recordActivity]);

  // Runs when the user clicks "Sign out".
  async function handleLogout() {
    // Now we will log the sign-out.
    log('session', `${viewer.username} clicked Sign out`);
    // Now we will tell the backend to end the session (ignore errors: we leave either way).
    await postJson('/api/auth/logout').catch(() => {});
    // Show the login page.
    endSession('You have signed out.');
  }

  // Runs after a successful login.
  function handleLogin(newViewer) {
    // Now we will log who logged in and what they may do.
    log('session', `Logged in as ${newViewer.username}`, { roles: newViewer.roles, capabilities: newViewer.capabilities });
    // Clear any old message.
    setNotice(null);
    // Open their first page.
    setPage(firstPage(newViewer));
    // Remember the user, which shows their page.
    setViewer(newViewer);
  }

  // Runs after the role switcher changed the account.
  function handleSwitched(newViewer) {
    // Now we will log the switch.
    log('session', `Switched account to ${newViewer.username}`, { roles: newViewer.roles });
    // Start on the new account's first page.
    setPage(firstPage(newViewer));
    // Remember the new user, which reloads their pages.
    setViewer(newViewer);
  }

  // While we are still checking the login, show the EMIS spinner.
  if (viewer === undefined) {
    // A full-page loading cover.
    return <div className="login-page"><Loader message="Loading…" /></div>;
  }

  // Nobody logged in: show the login page.
  if (viewer === null) {
    // The login page, with the reason we are here (if any).
    return <LoginScreen onLogin={handleLogin} notice={notice} />;
  }

  // The pages this user may open.
  const pages = pagesFor(viewer);
  // The open page's title and description.
  const current = pages.find((item) => item.id === page) ?? NO_PAGE;
  // True when the user may open a page (the dashboard and builder stay loaded in the background).
  const has = (id) => pages.some((item) => item.id === id);

  // Opens another page from the menu, and logs it.
  function navigate(id) {
    // Now we will log the page change.
    log('page', `Opened page "${id}"`);
    // Show it.
    setPage(id);
  }

  // Opens the builder at a page, e.g. a dashboard to edit.
  function openBuilder(request) {
    // Now we will log the request.
    log('page', 'Opening the chart builder at', request);
    // Pass the request to the builder (a new object each time, so asking twice works).
    setBuilderRequest({ ...request });
    // Show the builder.
    setPage('builder');
  }

  // Now we will describe the logged-in screen.
  return (
    // The whole window: top bar above, menu and page below.
    <div className="shell">
      {/* The top bar. */}
      <header className="topbar">
        {/* Where we are: section / page. */}
        <nav className="breadcrumbs" aria-label="Breadcrumbs">
          {/* The section. */}
          <span>{current.group}</span>
          {/* Separator. */}
          <span className="breadcrumb-separator">/</span>
          {/* The open page. */}
          <strong>{current.title}</strong>
        </nav>
        {/* The right-hand side of the top bar. */}
        <div className="topbar-right">
          {/* Reminder that the data is not real. */}
          <span className="sample-badge" title="The records on these screens are not real">Sample data</span>
          {/* Who is logged in, with the demo role switcher. */}
          <RoleSwitcher viewer={viewer} onSwitched={handleSwitched} onSessionEnded={endSession} />
        </div>
      </header>

      {/* Below the top bar: the menu and the page. */}
      <div className="shell-body">
        {/* The left-hand menu. */}
        <Sidebar viewer={viewer} page={page} onNavigate={navigate} onLogout={handleLogout} />

        {/* The page. */}
        <main className="page">
          {/* The page header: title and description on the left, row filter on the right. */}
          <div className="page-header">
            {/* Title and description. */}
            <div>
              {/* The page title. */}
              <h1>{current.title}</h1>
              {/* The page description. */}
              {current.description && <p>{current.description}</p>}
            </div>
            {/* On dashboards: which rows this user may see, i.e. the filter Superset adds to every query. */}
            {page === 'dashboards' && (
              // The chip.
              <span className="scope-chip" title="Row-level security applied by Superset to every query">
                {/* e.g. "Rows: district_code = 'D03'", or "Rows: all" for national users. */}
                Rows: <code>{viewer.rlsClause ?? 'all'}</code>
              </span>
            )}
          </div>

          {/* The pages sit on top of each other. The dashboard and the builder stay loaded and are
              only hidden, so they never reload (or show a loader) when the user moves around.
              The other pages are drawn when opened, so they always show fresh data. */}
          <div className="pages">
            {/* The dashboards. "key" reloads them completely when the user changes. */}
            {has('dashboards') && (
              // The dashboards page.
              <div className={page === 'dashboards' ? 'page-pane' : 'page-pane is-hidden'}>
                {/* The published dashboards. */}
                <DashboardsPage key={viewer.username} onSessionEnded={endSession} onActivity={recordActivity} />
              </div>
            )}
            {/* The builder, only for CPU analysts. It loads in the background, ready to use. */}
            {has('builder') && (
              // The builder page.
              <div className={page === 'builder' ? 'page-pane framed' : 'page-pane framed is-hidden'}>
                {/* Superset. */}
                <BuilderWorkspace key={viewer.username} request={builderRequest} />
              </div>
            )}
            {/* The other pages, drawn only while open. */}
            {page === 'forms' && <div className="page-pane scroll"><MyFormsPage /></div>}
            {page === 'reviews' && <div className="page-pane scroll"><ReviewsPage /></div>}
            {page === 'kpis' && <div className="page-pane scroll"><KpiRegistryPage /></div>}
            {page === 'fields' && <div className="page-pane scroll"><FieldLibraryPage /></div>}
            {page === 'formBuilder' && <div className="page-pane scroll"><FormBuilderPage /></div>}
            {page === 'access' && <div className="page-pane scroll"><DashboardAccessPage onOpenBuilder={openBuilder} /></div>}
            {/* A user with no pages (teachers and students). */}
            {page === 'none' && (
              // The empty page.
              <div className="page-pane scroll">
                <div className="empty-state">
                  {/* Icon. */}
                  <Inbox size={36} />
                  {/* Message. */}
                  <p><strong>Nothing to show for your role.</strong></p>
                  {/* Explanation. */}
                  <p className="muted">KPI dashboards and forms are for ministry officers, DEOs, principals and registrars.</p>
                </div>
              </div>
            )}
          </div>
        </main>
      </div>
    </div>
  );
}
