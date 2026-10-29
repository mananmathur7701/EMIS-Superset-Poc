// React's hook for remembering values.
import { useState } from 'react';
// Icons.
import { Pencil, Plus } from 'lucide-react';
// Our helpers for calling the backend.
import { postJson, putJson } from '../../api/api';
// The dialog.
import Modal from '../../components/Modal';
// Reads the list when the page opens.
import useLoad from '../../api/useLoad';
// Browser-console logging.
import { log } from '../../logging/logger';

// How each field type is written on screen.
const TYPES = { NUMBER: 'Number', TEXT: 'Text', SELECT: 'Choice list', DATE: 'Date' };

// An empty field for the "New field" dialog.
const NEW_FIELD = { code: '', label: '', description: '', fieldType: 'NUMBER', options: '', active: true };

// The CPU analyst's field library: everything a form can ask. KPIs are built from number fields,
// and one field can feed many KPIs and appear in many forms.
export default function FieldLibraryPage() {
  // The fields.
  const fields = useLoad('/api/admin/fields');
  // The field being edited in the dialog (null when closed).
  const [editing, setEditing] = useState(null);
  // An error from saving.
  const [saveError, setSaveError] = useState(null);
  // True while saving.
  const [saving, setSaving] = useState(false);

  // Changes one value of the field being edited.
  function change(name, value) {
    // Copy the field with the new value.
    setEditing((field) => ({ ...field, [name]: value }));
  }

  // Opens the dialog for a field (or a new one).
  function openEditor(field) {
    // Now we will log what the user did.
    log('field', field.id ? `Editing field ${field.code}` : 'Starting a new field');
    // Clear an old error.
    setSaveError(null);
    // Copy the field so typing does not change the list until it is saved.
    setEditing({ ...field, options: field.options ?? '', description: field.description ?? '' });
  }

  // Saves the field being edited.
  async function save() {
    // Now we will log what the user did.
    log('field', `Saving field ${editing.code}`, editing);
    // Disable the button and clear an old error.
    setSaving(true);
    setSaveError(null);
    // Catch errors so we can show them.
    try {
      // Now we will create (no id yet) or update the field.
      await (editing.id ? putJson(`/api/admin/fields/${editing.id}`, editing) : postJson('/api/admin/fields', editing));
      // Close the dialog.
      setEditing(null);
      // Read the list again.
      fields.reload();
    } catch (error) {
      // Show the backend's message.
      setSaveError(error.message);
    } finally {
      // Enable the button again.
      setSaving(false);
    }
  }

  // Now we will describe the page.
  return (
    // The page content.
    <div className="page-content">
      {/* The toolbar. */}
      <div className="toolbar">
        {/* How many fields. */}
        <span className="muted">{fields.data ? `${fields.data.length} fields` : 'Loading…'}</span>
        {/* Open the dialog for a new field. */}
        <button type="button" className="button" onClick={() => openEditor(NEW_FIELD)}>
          {/* Plus icon. */}
          <Plus size={16} /> New field
        </button>
      </div>
      {/* A loading error. */}
      {fields.error && <p className="form-error">{fields.error}</p>}

      {/* The table of fields. */}
      {fields.data && (
        // A white panel holding the table.
        <div className="surface table-panel">
          {/* The table. */}
          <table className="data-table">
            {/* Column headings. */}
            <thead>
              <tr><th>Code</th><th>Label</th><th>Type</th><th>Used by KPIs</th><th>Status</th><th /></tr>
            </thead>
            {/* One row per field. */}
            <tbody>
              {fields.data.map((field) => (
                // The row.
                <tr key={field.id}>
                  {/* The code, e.g. total_pupils. */}
                  <td><code className="code-chip">{field.code}</code></td>
                  {/* Label with description below. */}
                  <td>
                    {/* Label. */}
                    <strong>{field.label}</strong>
                    {/* Description. */}
                    <div className="muted small">{field.description}</div>
                  </td>
                  {/* Type, with the choices of a choice list. */}
                  <td>{TYPES[field.fieldType]}{field.options && <div className="muted small">{field.options}</div>}</td>
                  {/* The KPIs that use it. */}
                  <td>{field.usedByKpis.length ? field.usedByKpis.map((code) => <code key={code} className="code-chip">{code}</code>) : <span className="muted">—</span>}</td>
                  {/* Active or not. */}
                  <td>{field.active ? <span className="status-badge status-active">Active</span> : <span className="status-badge status-retired">Inactive</span>}</td>
                  {/* Edit button. */}
                  <td className="row-action">
                    {/* Opens the dialog for this field. */}
                    <button type="button" className="icon-button" onClick={() => openEditor(field)} aria-label={`Edit ${field.code}`}>
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
        <Modal title={editing.id ? `Edit ${editing.code}` : 'New field'} onClose={() => setEditing(null)}
          footer={(
            // The dialog's buttons.
            <>
              {/* Cancel. */}
              <button type="button" className="button secondary" onClick={() => setEditing(null)}>Cancel</button>
              {/* Save. */}
              <button type="button" className="button" onClick={save} disabled={saving}>{saving ? 'Saving…' : 'Save field'}</button>
            </>
          )}>
          {/* The field's settings. */}
          <div className="form-grid">
            {/* Code (fixed once saved: forms and the analytics view use it). */}
            <label className="input-label">Code
              <input className="text-input" value={editing.code} disabled={Boolean(editing.id)}
                onChange={(event) => change('code', event.target.value.toLowerCase())} placeholder="girls_enrolled" />
            </label>
            {/* Type (fixed once saved). */}
            <label className="input-label">Type
              <select className="text-input" value={editing.fieldType} disabled={Boolean(editing.id)}
                onChange={(event) => change('fieldType', event.target.value)}>
                {/* One option per type. */}
                {Object.entries(TYPES).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </label>
            {/* Label, across both columns. */}
            <label className="input-label span-2">Label (the question registrars see)
              <input className="text-input" value={editing.label} onChange={(event) => change('label', event.target.value)}
                placeholder="Girls on roll" />
            </label>
            {/* Description, across both columns. */}
            <label className="input-label span-2">Help text
              <textarea className="text-input" rows={2} value={editing.description} onChange={(event) => change('description', event.target.value)} />
            </label>
            {/* Choices, for a choice list only. */}
            {editing.fieldType === 'SELECT' && (
              // The choices, comma-separated.
              <label className="input-label span-2">Choices (comma-separated)
                <input className="text-input" value={editing.options} onChange={(event) => change('options', event.target.value)}
                  placeholder="Yes, No" />
              </label>
            )}
            {/* Active. */}
            <label className="check span-2">
              <input type="checkbox" checked={editing.active} onChange={(event) => change('active', event.target.checked)} />
              Active (offered in the form builder and KPI editor)
            </label>
          </div>
          {/* What a number field does. */}
          {editing.fieldType === 'NUMBER' && !editing.id && (
            // A blue note.
            <p className="notice">A number field also becomes a column of the census data in Superset.</p>
          )}
          {/* A save error. */}
          {saveError && <p className="form-error">{saveError}</p>}
        </Modal>
      )}
    </div>
  );
}
