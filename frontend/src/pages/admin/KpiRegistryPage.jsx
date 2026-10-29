// React's hook for remembering values.
import { useState } from 'react';
// Icons.
import { Pencil, Plus } from 'lucide-react';
// Our helpers for calling the backend.
import { postJson, putJson } from '../../api/api';
// The dialog.
import Modal from '../../components/Modal';
// The status pill.
import StatusBadge from '../../components/StatusBadge';
// Reads lists when the page opens.
import useLoad from '../../api/useLoad';
// Browser-console logging.
import { log } from '../../logging/logger';

// KPI groups from the EMIS mockup, offered as suggestions (any text is allowed).
const CATEGORIES = ['Enrolment & Access', 'Teacher Development', 'Infrastructure', 'Student Support',
  'Equity & Inclusion', 'Quality & Learning Outcomes'];

// An empty KPI for the "New KPI" dialog.
const NEW_KPI = { code: '', name: '', category: '', description: '', multiplier: 100, status: 'DRAFT',
  numeratorFieldId: '', denominatorFieldId: '', scopeSchoolTypes: [], scopeDistrictCodes: [], scopeSchoolIds: [] };

// Writes a KPI's formula for people, e.g. "trained_teachers × 100 ÷ total_teachers".
function formulaText(numerator, denominator, multiplier) {
  // Not chosen yet.
  if (!numerator) return '—';
  // The top: the numerator times the multiplier (×1 is left out).
  const top = multiplier === 1 ? `Σ ${numerator}` : `Σ ${numerator} × ${multiplier}`;
  // With a denominator: divide; without: a plain total.
  return denominator ? `${top} ÷ Σ ${denominator}` : top;
}

// Describes who reports a KPI, e.g. "Private · D02 · 3 schools", or "All schools".
function scopeText(kpi, schools) {
  // The parts we will join.
  const parts = [...kpi.scopeSchoolTypes, ...kpi.scopeDistrictCodes];
  // Hand-picked schools: one name, or a count.
  if (kpi.scopeSchoolIds.length === 1) parts.push(schools?.find((s) => s.id === kpi.scopeSchoolIds[0])?.name ?? '1 school');
  // Several schools: a count.
  if (kpi.scopeSchoolIds.length > 1) parts.push(`${kpi.scopeSchoolIds.length} schools`);
  // Nothing chosen means every school.
  return parts.length ? parts.join(' · ') : 'All schools';
}

// Adds a value to a list, or removes it if it is already there.
function toggle(list, value) {
  // Remove it if present, add it if not.
  return list.includes(value) ? list.filter((item) => item !== value) : [...list, value];
}

// The CPU analyst's KPI registry: every KPI with its formula, who reports it, and its status.
export default function KpiRegistryPage() {
  // The KPIs.
  const kpis = useLoad('/api/admin/kpis');
  // The field library (for the numerator and denominator lists).
  const fields = useLoad('/api/admin/fields');
  // Districts, schools and school types (for the scope).
  const places = useLoad('/api/admin/places');
  // The KPI being edited in the dialog (null when closed).
  const [editing, setEditing] = useState(null);
  // An error from saving.
  const [saveError, setSaveError] = useState(null);
  // True while saving.
  const [saving, setSaving] = useState(false);

  // Opens the dialog for a KPI (or a new one).
  function openEditor(kpi) {
    // Now we will log what the user did.
    log('kpi', kpi.id ? `Editing KPI ${kpi.code}` : 'Starting a new KPI');
    // Clear an old error.
    setSaveError(null);
    // Copy the KPI so typing does not change the list until it is saved.
    setEditing({ ...kpi, numeratorFieldId: kpi.numeratorFieldId ?? '', denominatorFieldId: kpi.denominatorFieldId ?? '' });
  }

  // Changes one value of the KPI being edited, e.g. change('name', 'Trained teacher %').
  function change(name, value) {
    // Copy the KPI with the new value.
    setEditing((kpi) => ({ ...kpi, [name]: value }));
  }

  // Saves the KPI being edited.
  async function save() {
    // Now we will log what the user did.
    log('kpi', `Saving KPI ${editing.code}`, editing);
    // Disable the button and clear an old error.
    setSaving(true);
    setSaveError(null);
    // Catch errors so we can show them.
    try {
      // The values the backend expects; an empty denominator means "none".
      const body = { ...editing, multiplier: Number(editing.multiplier), denominatorFieldId: editing.denominatorFieldId || null };
      // Now we will create (no id yet) or update the KPI.
      await (editing.id ? putJson(`/api/admin/kpis/${editing.id}`, body) : postJson('/api/admin/kpis', body));
      // Close the dialog.
      setEditing(null);
      // Read the KPIs and fields again (fields show which KPIs use them).
      kpis.reload();
      fields.reload();
    } catch (error) {
      // Show the backend's message.
      setSaveError(error.message);
    } finally {
      // Enable the button again.
      setSaving(false);
    }
  }

  // The number fields: only they can be on top or bottom of a KPI.
  const numberFields = fields.data?.filter((field) => field.fieldType === 'NUMBER') ?? [];
  // Look up a field code by id, for the formula preview.
  const codeOf = (id) => numberFields.find((field) => field.id === id)?.code;

  // Now we will describe the page.
  return (
    // The page content.
    <div className="page-content">
      {/* The toolbar: count on the left, "New KPI" on the right. */}
      <div className="toolbar">
        {/* How many KPIs. */}
        <span className="muted">{kpis.data ? `${kpis.data.length} KPIs` : 'Loading…'}</span>
        {/* Open the dialog for a new KPI. */}
        <button type="button" className="button" onClick={() => openEditor(NEW_KPI)} disabled={!fields.data}>
          {/* Plus icon. */}
          <Plus size={16} /> New KPI
        </button>
      </div>
      {/* A loading error. */}
      {kpis.error && <p className="form-error">{kpis.error}</p>}

      {/* The table of KPIs. */}
      {kpis.data && (
        // A white panel holding the table.
        <div className="surface table-panel">
          {/* The table. */}
          <table className="data-table">
            {/* Column headings. */}
            <thead>
              <tr><th>Code</th><th>KPI</th><th>Formula</th><th>Reported by</th><th>Status</th><th /></tr>
            </thead>
            {/* One row per KPI. */}
            <tbody>
              {kpis.data.map((kpi) => (
                // The row.
                <tr key={kpi.id}>
                  {/* The code, e.g. KPI-TCH-01. */}
                  <td><code className="code-chip">{kpi.code}</code></td>
                  {/* Name with category and description below. */}
                  <td>
                    {/* Name. */}
                    <strong>{kpi.name}</strong>
                    {/* Category. */}
                    <div className="muted small">{kpi.category}</div>
                  </td>
                  {/* The formula. */}
                  <td className="mono small">{formulaText(kpi.numeratorField, kpi.denominatorField, kpi.multiplier)}</td>
                  {/* Who reports it. */}
                  <td>{scopeText(kpi, places.data?.schools)}</td>
                  {/* Status pill. */}
                  <td><StatusBadge status={kpi.status} /></td>
                  {/* Edit button. */}
                  <td className="row-action">
                    {/* Opens the dialog for this KPI. */}
                    <button type="button" className="icon-button" onClick={() => openEditor(kpi)} aria-label={`Edit ${kpi.code}`}>
                      {/* Pencil icon. */}
                      <Pencil size={16} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* The editor dialog. */}
      {editing && (
        // The dialog.
        <Modal wide title={editing.id ? `Edit ${editing.code}` : 'New KPI'} onClose={() => setEditing(null)}
          footer={(
            // The dialog's buttons.
            <>
              {/* Cancel. */}
              <button type="button" className="button secondary" onClick={() => setEditing(null)}>Cancel</button>
              {/* Save. */}
              <button type="button" className="button" onClick={save} disabled={saving}>{saving ? 'Saving…' : 'Save KPI'}</button>
            </>
          )}>
          {/* The definition. */}
          <div className="form-grid">
            {/* Code (fixed once saved, because Superset's metric is named after it). */}
            <label className="input-label">Code
              <input className="text-input" value={editing.code} disabled={Boolean(editing.id)}
                onChange={(event) => change('code', event.target.value.toUpperCase())} placeholder="KPI-ENR-01" />
            </label>
            {/* Name. */}
            <label className="input-label">Name
              <input className="text-input" value={editing.name} onChange={(event) => change('name', event.target.value)}
                placeholder="Gross enrolment rate" />
            </label>
            {/* Category, with suggestions. */}
            <label className="input-label">Category
              <input className="text-input" list="kpi-categories" value={editing.category ?? ''}
                onChange={(event) => change('category', event.target.value)} />
              {/* The suggestions. */}
              <datalist id="kpi-categories">{CATEGORIES.map((category) => <option key={category} value={category} />)}</datalist>
            </label>
            {/* Status. */}
            <label className="input-label">Status
              <select className="text-input" value={editing.status} onChange={(event) => change('status', event.target.value)}>
                {/* Being defined. */}
                <option value="DRAFT">Draft</option>
                {/* In use: forms ask for it and Superset gets its metric. */}
                <option value="ACTIVE">Active</option>
                {/* No longer used. */}
                <option value="RETIRED">Retired</option>
              </select>
            </label>
            {/* Definition, across both columns. */}
            <label className="input-label span-2">Definition
              <textarea className="text-input" rows={2} value={editing.description ?? ''}
                onChange={(event) => change('description', event.target.value)} />
            </label>
          </div>

          {/* The calculation. */}
          <h3 className="section-title">Calculation</h3>
          <div className="form-grid three">
            {/* Numerator. */}
            <label className="input-label">Numerator (added up)
              <select className="text-input" value={editing.numeratorFieldId} onChange={(event) => change('numeratorFieldId', event.target.value)}>
                {/* Nothing chosen. */}
                <option value="">Choose a number field…</option>
                {/* One option per number field. */}
                {numberFields.map((field) => <option key={field.id} value={field.id}>{field.label} ({field.code})</option>)}
              </select>
            </label>
            {/* Denominator. */}
            <label className="input-label">Denominator (optional)
              <select className="text-input" value={editing.denominatorFieldId} onChange={(event) => change('denominatorFieldId', event.target.value)}>
                {/* No denominator: the KPI is a total. */}
                <option value="">None — the KPI is a total</option>
                {/* One option per number field. */}
                {numberFields.map((field) => <option key={field.id} value={field.id}>{field.label} ({field.code})</option>)}
              </select>
            </label>
            {/* Multiplier. */}
            <label className="input-label">Multiplier
              <select className="text-input" value={editing.multiplier} onChange={(event) => change('multiplier', Number(event.target.value))}>
                {/* Percentage. */}
                <option value={100}>× 100 (percentage)</option>
                {/* Ratio or total. */}
                <option value={1}>× 1 (ratio or total)</option>
              </select>
            </label>
          </div>
          {/* The formula as Superset will use it. */}
          <p className="formula-preview">
            Formula: <code>{formulaText(codeOf(editing.numeratorFieldId), codeOf(editing.denominatorFieldId), editing.multiplier)}</code>
            <span className="muted small"> — added up first, then divided, so every district and school total is right.</span>
          </p>

          {/* Who reports it. */}
          <h3 className="section-title">Who reports this KPI</h3>
          <p className="muted small">Leave a group empty to include everyone. Schools outside the scope are not asked for this KPI's fields.</p>
          <div className="form-grid three">
            {/* School types. */}
            <fieldset className="check-group">
              <legend>School types</legend>
              {/* One checkbox per type. */}
              {places.data?.schoolTypes.map((type) => (
                // The checkbox and its label.
                <label key={type} className="check">
                  <input type="checkbox" checked={editing.scopeSchoolTypes.includes(type)}
                    onChange={() => change('scopeSchoolTypes', toggle(editing.scopeSchoolTypes, type))} />
                  {type}
                </label>
              ))}
            </fieldset>
            {/* Districts. */}
            <fieldset className="check-group">
              <legend>Districts</legend>
              {/* One checkbox per district. */}
              {places.data?.districts.map((district) => (
                // The checkbox and its label.
                <label key={district.code} className="check">
                  <input type="checkbox" checked={editing.scopeDistrictCodes.includes(district.code)}
                    onChange={() => change('scopeDistrictCodes', toggle(editing.scopeDistrictCodes, district.code))} />
                  {district.code} — {district.name}
                </label>
              ))}
            </fieldset>
            {/* Hand-picked schools, in a scrolling box. */}
            <fieldset className="check-group scroll">
              <legend>Specific schools</legend>
              {/* One checkbox per school. */}
              {places.data?.schools.map((school) => (
                // The checkbox and its label.
                <label key={school.id} className="check">
                  <input type="checkbox" checked={editing.scopeSchoolIds.includes(school.id)}
                    onChange={() => change('scopeSchoolIds', toggle(editing.scopeSchoolIds, school.id))} />
                  {school.name} <span className="muted small">{school.districtCode}</span>
                </label>
              ))}
            </fieldset>
          </div>
          {/* A save error. */}
          {saveError && <p className="form-error">{saveError}</p>}
        </Modal>
      )}
    </div>
  );
}
