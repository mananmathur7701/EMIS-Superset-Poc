// How each status is written on screen.
const LABELS = {
  // A form not opened yet this year.
  NOT_STARTED: 'Not started',
  // Being filled in by the registrar (or a KPI/form still being defined).
  DRAFT: 'Draft',
  // Waiting for the principal.
  WITH_PRINCIPAL: 'With principal',
  // Waiting for the DEO.
  WITH_DEO: 'With DEO',
  // Waiting for the CPU analyst.
  WITH_CPU: 'With CPU analyst',
  // Final; counts in dashboards.
  FROZEN: 'Frozen',
  // A KPI in use.
  ACTIVE: 'Active',
  // A KPI no longer used.
  RETIRED: 'Retired',
  // A form given to registrars.
  PUBLISHED: 'Published',
};

// A small coloured pill showing a status, e.g. "With DEO".
export default function StatusBadge({ status }) {
  // The CSS class picks the colour, e.g. "status-badge status-with_deo".
  return <span className={`status-badge status-${status.toLowerCase()}`}>{LABELS[status] ?? status}</span>;
}

// Returns the on-screen name of a status, e.g. statusLabel('WITH_DEO') is "With DEO".
export function statusLabel(status) {
  // Use the label, or the raw status if we have none.
  return LABELS[status] ?? status;
}
