// The sign-out icon.
import { LogOut } from 'lucide-react';
// The app's pages.
import { pagesFor } from './pages';

// The left-hand menu: EMIS brand at the top, the pages the user may open (grouped), and "Sign out".
// page is the open page's id; onNavigate(id) opens another one.
export default function Sidebar({ viewer, page, onNavigate, onLogout }) {
  // The pages this user may open.
  const pages = pagesFor(viewer);
  // The group names, in menu order, each once.
  const groups = [...new Set(pages.map((item) => item.group))];

  // Now we will describe what to show.
  return (
    // The menu column.
    <aside className="sidebar">
      {/* The brand: blue "EM" mark, "EMIS 2.0" and the country. */}
      <div className="brand">
        {/* The blue square mark. */}
        <span className="brand-mark">EM</span>
        {/* Name and country, one above the other. */}
        <span className="brand-text">
          {/* "EMIS" with a small "2.0" chip. */}
          <span className="brand-name">EMIS <span className="brand-version">2.0</span></span>
          {/* The country. */}
          <span className="brand-country">Saint Lucia</span>
        </span>
      </div>

      {/* The menu itself. */}
      <nav className="side-nav" aria-label="Main navigation">
        {/* One block per group. */}
        {groups.map((group) => (
          // The group.
          <div key={group} className="side-nav-section">
            {/* The group heading. */}
            <span className="side-nav-group">{group}</span>
            {/* One button per page in the group. */}
            {pages.filter((item) => item.group === group).map((item) => (
              // The button; the open page is highlighted.
              <button key={item.id} type="button" onClick={() => onNavigate(item.id)}
                className={page === item.id ? 'side-nav-item active' : 'side-nav-item'}
                aria-current={page === item.id ? 'page' : undefined} title={item.label}>
                {/* The page's icon. */}
                <item.icon size={18} strokeWidth={1.75} />
                {/* The page's name (hidden on narrow screens). */}
                <span className="side-nav-label">{item.label}</span>
              </button>
            ))}
          </div>
        ))}
      </nav>

      {/* The bottom of the menu: sign out. */}
      <div className="sidebar-footer">
        {/* The sign-out button. */}
        <button type="button" className="side-nav-item" onClick={onLogout} title="Sign out">
          {/* Door icon. */}
          <LogOut size={18} strokeWidth={1.75} />
          {/* Its label (hidden on narrow screens). */}
          <span className="side-nav-label">Sign out</span>
        </button>
      </div>
    </aside>
  );
}
