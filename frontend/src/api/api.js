// All backend URLs start with /api on the frontend's own address. In development Vite forwards
// them to the backend (see vite.config.js); in Docker nginx does (see nginx.conf). Because the
// browser sees one address, the login cookie just works and no CORS setup is needed.

// Browser-console logging.
import { log, newRequestId, warn } from '../logging/logger';

// Called when the backend says the session is over (HTTP 401); the app sets it to "go back to the
// login page". The login and "who am I" calls handle their own 401s, so they are left out.
let sessionEndedHandler = () => {};

// Sets what happens when the session ends, e.g. setSessionEndedHandler(endSession).
export function setSessionEndedHandler(handler) {
  // Remember it.
  sessionEndedHandler = handler;
}

// Sends one request to the backend and returns the JSON it sends back.
async function sendRequest(method, path, body) {
  // A short id for this request; the backend writes the same id on its log lines for it.
  const requestId = newRequestId();
  // Note the start time.
  const started = performance.now();
  // Now we will log the call (never the body: it may hold a password).
  log('api', `→ ${method} ${path}`, { requestId });
  // The answer, once it arrives.
  let response;
  // Catch network failures (backend not running) so we can log them.
  try {
    // Now we will call the backend.
    response = await fetch(path, {
      // GET, POST or PUT.
      method,
      // Say that we send JSON, and pass the request id.
      headers: { 'Content-Type': 'application/json', 'X-Request-Id': requestId },
      // Turn the body into JSON text, if there is one.
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (networkError) {
    // Now we will log that the backend could not be reached.
    warn('api', `✖ ${method} ${path}: the backend did not answer (${networkError.message})`, { requestId });
    // Pass the error on.
    throw networkError;
  }
  // How long it took.
  const millis = Math.round(performance.now() - started);
  // HTTP 204 means "done, nothing to send back".
  const data = response.status === 204 ? null : await response.json();
  // If the backend reported an error ...
  if (!response.ok) {
    // ... make an error with the backend's message ...
    const error = new Error(data?.detail ?? `Request failed with HTTP ${response.status}`);
    // ... remember the status (401 means "not logged in any more") ...
    error.status = response.status;
    // ... log the refusal with the backend's message ...
    warn('api', `← ${response.status} ${method} ${path} in ${millis} ms: ${error.message}`, { requestId });
    // ... go back to the login page if the session is over ...
    if (response.status === 401 && !path.startsWith('/api/auth/login') && !path.startsWith('/api/auth/me')) {
      // Tell the app, with the backend's message.
      sessionEndedHandler(error.message);
    }
    // ... and stop here.
    throw error;
  }
  // Now we will log the success.
  log('api', `← ${response.status} ${method} ${path} in ${millis} ms`, { requestId });
  // Otherwise hand the JSON back to the caller.
  return data;
}

// Reads from the backend, e.g. getJson('/api/auth/me').
export function getJson(path) {
  // Send a GET request without a body.
  return sendRequest('GET', path);
}

// Sends to the backend, e.g. postJson('/api/auth/login', { username, password }).
export function postJson(path, body) {
  // Send a POST request with the body.
  return sendRequest('POST', path, body);
}

// Replaces something on the backend, e.g. putJson('/api/admin/kpis/1', kpi).
export function putJson(path, body) {
  // Send a PUT request with the body.
  return sendRequest('PUT', path, body);
}
