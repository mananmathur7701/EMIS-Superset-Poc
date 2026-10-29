// How each role looks in the app, matching the EMIS 2.0 role switcher.
const ROLE_STYLES = {
  // Education Minister: rose.
  MINISTER: { short: 'Minister', long: 'Minister of Education', color: '#be123c' },
  // Corporate Planning Unit analyst: sky blue.
  CPU_ANALYST: { short: 'CPU Analyst', long: 'CPU Analyst (Corporate Planning Unit)', color: '#0284c7' },
  // District Education Officer: purple.
  DEO: { short: 'DEO', long: 'District Education Officer (DEO)', color: '#7c3aed' },
  // Principal: amber.
  PRINCIPAL: { short: 'Principal', long: 'Principal', color: '#d97706' },
  // Registrar: green.
  REGISTRAR: { short: 'Registrar', long: 'School registrar', color: '#059669' },
  // Teacher: slate grey.
  TEACHER: { short: 'Teacher', long: 'Teacher', color: '#475569' },
  // Student: slate grey.
  STUDENT: { short: 'Student', long: 'Student', color: '#475569' },
};

// Returns the look of a user's main (first) role.
export function roleStyle(user) {
  // Use the first role; fall back to teacher styling for anything unknown.
  return ROLE_STYLES[user.roles[0]] ?? ROLE_STYLES.TEACHER;
}

// Returns "national", "district" or "school", which picks the scope icon.
export function scopeType(user) {
  // The Minister and CPU analysts see the whole country.
  if (user.roles.includes('MINISTER') || user.roles.includes('CPU_ANALYST')) return 'national';
  // DEOs see one district.
  if (user.roles.includes('DEO')) return 'district';
  // Everyone else sees one school.
  return 'school';
}

// Returns up to two initials, e.g. "Kendell St. Rose" becomes "KS".
export function initials(fullName) {
  // Take the first letter of each word, keep the first two, in capitals.
  return fullName.split(' ').map((word) => word[0]).join('').slice(0, 2).toUpperCase();
}

// True when the user may do something, e.g. can(viewer, 'REVIEW').
export function can(user, capability) {
  // The backend sends the list of what the user may do.
  return user.capabilities.includes(capability);
}
