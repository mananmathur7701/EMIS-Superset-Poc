// React's hook for remembering values between renders.
import { useState } from 'react';
// Icons from the same set EMIS 2.0 uses.
import { CircleAlert, Info, Lock, ShieldCheck, User } from 'lucide-react';
// Our helper for calling the backend.
import { postJson } from '../api/api';
// Browser-console logging.
import { log } from '../logging/logger';

// The login page. onLogin receives the logged-in user; notice is an optional message such as
// "Your session timed out".
export default function LoginScreen({ onLogin, notice }) {
  // What the user typed as username.
  const [username, setUsername] = useState('');
  // What the user typed as password.
  const [password, setPassword] = useState('');
  // An error message to show, e.g. "Wrong username or password."
  const [error, setError] = useState(null);
  // True while we wait for the backend, so the button cannot be pressed twice.
  const [busy, setBusy] = useState(false);

  // Runs when the form is submitted.
  async function handleSubmit(event) {
    // Now we will log what the user did.
    log('session', `Signing in as ${username}`);
    // Stop the browser from reloading the page.
    event.preventDefault();
    // Show that we are working ...
    setBusy(true);
    // ... and clear any old error.
    setError(null);
    // Catch a failed login so we can show why.
    try {
      // Now we will send the username and password to the backend.
      const viewer = await postJson('/api/auth/login', { username, password });
      // Logged in: hand the user to the main screen.
      onLogin(viewer);
    } catch (err) {
      // Show the backend's message.
      setError(err.message);
      // Let the user try again.
      setBusy(false);
    }
  }

  // Now we will describe what to show.
  return (
    // The whole page: a slim top bar and the login card below it.
    <div className="login-page">
      {/* The slim top bar with the EMIS brand. */}
      <header className="login-topbar">
        {/* The blue square mark. */}
        <span className="brand-mark">EM</span>
        {/* The product name. */}
        <strong>EMIS</strong>
        {/* The country chip. */}
        <span className="login-country">Saint Lucia</span>
      </header>

      {/* Centres the card in the remaining space. */}
      <div className="login-center">
        {/* The login card. */}
        <form className="login-card" onSubmit={handleSubmit}>
          {/* A thin blue stripe along the top of the card. */}
          <div className="login-stripe" />
          {/* The card's content. */}
          <div className="login-body">
            {/* The blue shield tile. */}
            <span className="login-icon"><ShieldCheck size={28} strokeWidth={1.75} /></span>
            {/* Title. */}
            <h1>Sign in to EMIS</h1>
            {/* Subtitle. */}
            <p className="login-subtitle">Census analytics for the Ministry of Education.</p>

            {/* An optional message, e.g. after a timeout or sign-out. */}
            {notice && <p className="notice"><Info size={16} /> {notice}</p>}
            {/* The login error, if any. */}
            {error && <p className="form-error"><CircleAlert size={16} /> {error}</p>}

            {/* Username label and box. */}
            <label htmlFor="username">Username</label>
            {/* The box with a person icon inside. */}
            <div className="input-with-icon">
              {/* Person icon. */}
              <User size={16} />
              {/* Keep the typed username in state. */}
              <input id="username" value={username} onChange={(e) => setUsername(e.target.value)}
                autoComplete="username" placeholder="e.g. mfelix" autoFocus required />
            </div>

            {/* Password label and box. */}
            <label htmlFor="password">Password</label>
            {/* The box with a lock icon inside. */}
            <div className="input-with-icon">
              {/* Lock icon. */}
              <Lock size={16} />
              {/* Keep the typed password in state; dots instead of letters. */}
              <input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password" required />
            </div>

            {/* The sign-in button, disabled while we wait. */}
            <button type="submit" className="button" disabled={busy}>
              {/* Button text changes while we wait. */}
              {busy ? 'Signing in…' : 'Sign in'}
            </button>
            {/* Where to find the demo accounts. */}
            <p className="hint">Demo accounts are listed in ACCOUNTS.md.</p>
          </div>
        </form>
      </div>
    </div>
  );
}
