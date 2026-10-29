// React's hooks: remember values, run code after drawing, and point at an element.
import { useEffect, useRef, useState } from 'react';
// Icons from the same set EMIS 2.0 uses.
import { Building2, ChevronDown, Globe, MapPin, Sparkles, UserCheck } from 'lucide-react';
// Our helpers for calling the backend.
import { getJson, postJson } from '../api/api';
// How each role looks (colour, labels) and helpers for initials and scope.
import { initials, roleStyle, scopeType } from './roles';
// Browser-console logging.
import { log } from '../logging/logger';

// The small icon in front of a scope: globe (national), pin (district) or building (school).
function ScopeIcon({ user }) {
  // Work out which kind of scope this user has.
  const type = scopeType(user);
  // National users get a globe.
  if (type === 'national') return <Globe size={12} className="scope-icon national" />;
  // District users get a map pin.
  if (type === 'district') return <MapPin size={12} className="scope-icon district" />;
  // School users get a building.
  return <Building2 size={12} className="scope-icon school" />;
}

// The square with the user's initials, coloured by role.
function Avatar({ user, size = 24 }) {
  // Now we will draw the coloured square.
  return (
    // The square; its colour and size come from the role and the size given.
    <span className="avatar" style={{ backgroundColor: roleStyle(user).color, width: size, height: size }}>
      {/* The initials, e.g. "MF". */}
      {initials(user.fullName)}
    </span>
  );
}

// The role switcher in the top bar: shows who is logged in and, in the demo, lets you switch.
export default function RoleSwitcher({ viewer, onSwitched, onSessionEnded }) {
  // Accounts the demo switcher offers; empty when switching is turned off.
  const [demoUsers, setDemoUsers] = useState([]);
  // Whether the dropdown list is open.
  const [open, setOpen] = useState(false);
  // Points at the switcher, so clicks outside it can close the list.
  const switcherRef = useRef(null);

  // Run once: ask the backend which accounts we may switch to.
  useEffect(() => {
    // Now we will fetch the list.
    getJson('/api/auth/demo-users')
      // Remember it.
      .then(setDemoUsers)
      // If that fails, just offer no switching.
      .catch(() => setDemoUsers([]));
  }, []);

  // While the list is open, close it when the user clicks elsewhere or into an iframe.
  useEffect(() => {
    // Nothing to do while the list is closed.
    if (!open) return;
    // Closes the list if the click was outside the switcher.
    function handleClick(event) {
      // A click outside the switcher closes the list.
      if (!switcherRef.current.contains(event.target)) setOpen(false);
    }
    // Closes the list (used when focus moves into an iframe).
    function close() {
      // Close it.
      setOpen(false);
    }
    // Now we will listen for clicks on the page ...
    document.addEventListener('mousedown', handleClick);
    // ... and for focus leaving the page (a click into the dashboard).
    window.addEventListener('blur', close);
    // Stop listening when the list closes.
    return () => {
      // Remove the click listener.
      document.removeEventListener('mousedown', handleClick);
      // Remove the blur listener.
      window.removeEventListener('blur', close);
    };
  }, [open]);

  // Runs when another account is picked.
  async function handleSwitch(username) {
    // Now we will log what the user did.
    log('session', `Switching account to ${username}`);
    // Close the list.
    setOpen(false);
    // Picking the current user changes nothing.
    if (username === viewer.username) return;
    // Catch failures so we can react to them.
    try {
      // Now we will ask the backend to switch this session to the picked account.
      const newViewer = await postJson('/api/auth/switch', { username });
      // Tell the main screen who is logged in now.
      onSwitched(newViewer);
    } catch (error) {
      // HTTP 401 means the session already ended: go back to the login page.
      if (error.status === 401) {
        // Show the login page with the backend's message.
        onSessionEnded(error.message);
      } else {
        // Any other problem: show the message.
        window.alert(error.message);
      }
    }
  }

  // Switching is possible only when the backend offered accounts.
  const canSwitch = demoUsers.length > 0;

  // Now we will describe what to show.
  return (
    // The switcher; the dropdown is positioned against it.
    <div className="role-switcher" ref={switcherRef}>
      {/* The pill: avatar, name, role chip and scope. Clicking it opens the list (when allowed). */}
      <button type="button" className="role-pill" onClick={() => setOpen(!open)} disabled={!canSwitch}
        aria-expanded={open} aria-label="Switch role">
        {/* The coloured initials. */}
        <Avatar user={viewer} />
        {/* Name and scope, one above the other. */}
        <span className="role-pill-text">
          {/* Top line: name and short role. */}
          <span className="role-pill-name">
            {/* The user's name. */}
            <strong>{viewer.fullName}</strong>
            {/* The short role, e.g. "DEO". */}
            <span className="role-chip">{roleStyle(viewer).short}</span>
          </span>
          {/* Bottom line: the scope with its icon. */}
          <span className="role-pill-scope">
            {/* Globe, pin or building. */}
            <ScopeIcon user={viewer} />
            {/* e.g. "District D03". */}
            {viewer.scopes.join(', ')}
          </span>
        </span>
        {/* The arrow, only when switching is possible; it flips while the list is open. */}
        {canSwitch && <ChevronDown size={14} className={open ? 'chevron open' : 'chevron'} />}
      </button>

      {/* The dropdown list of demo accounts. */}
      {open && (
        // The floating panel.
        <div className="role-menu">
          {/* The panel heading. */}
          <div className="role-menu-header">
            {/* Title with a sparkle icon. */}
            <strong><Sparkles size={14} className="sparkle" /> Live demo role switcher</strong>
            {/* What it is for. */}
            <span>Switch user to see the dashboard as each role sees it.</span>
          </div>
          {/* One row per account. */}
          {demoUsers.map((user, index) => (
            // A row; the current user is highlighted.
            <button key={user.username} type="button" onClick={() => handleSwitch(user.username)}
              className={user.username === viewer.username ? 'role-option active' : 'role-option'}>
              {/* A numbered square in the role's colour. */}
              <span className="avatar" style={{ backgroundColor: roleStyle(user).color, width: 28, height: 28 }}>{index + 1}</span>
              {/* Name, role and scope. */}
              <span className="role-option-text">
                {/* Top line: name, plus "Active" for the current user. */}
                <span className="role-option-name">
                  {/* The name. */}
                  <strong>{user.fullName}</strong>
                  {/* "Active" badge for the logged-in user. */}
                  {user.username === viewer.username && <span className="active-badge"><UserCheck size={10} /> Active</span>}
                </span>
                {/* The full role name. */}
                <span>{roleStyle(user).long}</span>
                {/* The scope with its icon. */}
                <span className="role-option-scope"><ScopeIcon user={user} /> Scope: {user.scopes.join(', ')}</span>
              </span>
            </button>
          ))}
          {/* Footer. */}
          <div className="role-menu-footer">Saint Lucia EMIS · demo accounts</div>
        </div>
      )}
    </div>
  );
}
