// Browser-console logging. Every line starts with "[EMIS <area>]", so in the browser's developer
// tools (F12 → Console) you can type "EMIS" in the filter box to see only these lines.
// Backend calls also carry a request id: the same id is on the backend's log lines for that request.

// The look of the "[EMIS …]" tag in the console.
const TAG_STYLE = 'color:#1F5FBF;font-weight:600';

// Writes an information line, e.g. log('page', 'Opened KPI registry').
export function log(area, message, details) {
  // Now we will write it (the details object only if there is one).
  console.info(`%c[EMIS ${area}]`, TAG_STYLE, message, ...(details === undefined ? [] : [details]));
}

// Writes a warning line, e.g. a refused request.
export function warn(area, message, details) {
  // Now we will write it.
  console.warn(`%c[EMIS ${area}]`, TAG_STYLE, message, ...(details === undefined ? [] : [details]));
}

// Makes a short id for one backend request, e.g. "3f9a1c2e".
export function newRequestId() {
  // The first 8 characters of a random id.
  return crypto.randomUUID().slice(0, 8);
}
