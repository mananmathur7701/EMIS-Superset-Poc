// React's hooks: remember values and point at a value.
import { useRef, useState } from 'react';
// Icons.
import { ArrowLeft, ListChecks, Pencil, Plus, Save } from 'lucide-react';
// Our helpers for calling the backend.
import { getJson, postJson, putJson } from '../../api/api';
// Form.io helpers.
import { componentFor } from '../../formio/formio';
// The Form.io builder.
import FormioBuilder from '../../formio/FormioBuilder';
// The status pill.
import StatusBadge from '../../components/StatusBadge';
// Reads lists when the page opens.
import useLoad from '../../api/useLoad';
// Browser-console logging.
import { log } from '../../logging/logger';

// An empty form for "New form".
const NEW_FORM = { form: { code: '', name: '', description: '', status: 'DRAFT', kpiIds: [] },
  schema: { display: 'form', components: [] } };

// Returns the key of every question in a Form.io definition (the same rule as the backend).
function questionKeys(node, keys = []) {
  // A question: an object that takes an answer and is not a button.
  if (node && typeof node === 'object' && node.input === true && node.type !== 'button') keys.push(node.key);
  // Look inside every child value.
  if (node && typeof node === 'object') Object.values(node).forEach((child) => questionKeys(child, keys));
  // Return the keys.
  return keys;
}

// The CPU analyst's form builder: a list of forms, and an editor with the Form.io builder.
export default function FormBuilderPage() {
  // The forms.
  const forms = useLoad('/api/admin/forms');
  // The KPIs (to link to a form).
  const kpis = useLoad('/api/admin/kpis');
  // The field library (for the builder's side menu).
  const fields = useLoad('/api/admin/fields');
  // The form being edited: its settings (null while the list is shown).
  const [editing, setEditing] = useState(null);
  // The definition the builder is drawn from; changing it redraws the builder.
  const [builderSchema, setBuilderSchema] = useState(null);
  // The latest definition from the builder (kept here so typing in the builder does not redraw it).
  const schemaRef = useRef(null);
  // A message after saving ({ text, error }).
  const [message, setMessage] = useState(null);
  // True while saving.
  const [saving, setSaving] = useState(false);

  // Opens a form in the editor (null for a new one).
  async function openEditor(id) {
    // Now we will log what the user did.
    log('form', id ? `Opening form ${id} in the builder` : 'Starting a new form');
    // Clear an old message.
    setMessage(null);
    // Now we will load the form with its definition (or start an empty one).
    const detail = id ? await getJson(`/api/admin/forms/${id}`) : NEW_FORM;
    // Keep the settings.
    setEditing({ ...detail.form, description: detail.form.description ?? '' });
    // Keep the definition for saving ...
    schemaRef.current = detail.schema;
    // ... and draw the builder from it.
    setBuilderSchema(detail.schema);
  }

  // Changes one setting of the form being edited.
  function change(name, value) {
    // Copy the settings with the new value.
    setEditing((form) => ({ ...form, [name]: value }));
  }

  // Ticks or unticks a KPI.
  function toggleKpi(kpiId) {
    // Remove it if linked, add it if not.
    change('kpiIds', editing.kpiIds.includes(kpiId) ? editing.kpiIds.filter((id) => id !== kpiId) : [...editing.kpiIds, kpiId]);
  }

  // Adds one section per ticked KPI with the questions its fields need that the form lacks.
  function addKpiQuestions() {
    // Now we will log what the user did.
    log('form', 'Adding questions for the ticked KPIs', editing.kpiIds);
    // The questions the form already asks.
    const existing = new Set(questionKeys(schemaRef.current));
    // The new sections.
    const sections = [];
    // Go through each ticked KPI.
    for (const kpi of kpis.data.filter((k) => editing.kpiIds.includes(k.id))) {
      // Its fields that are not asked yet (numerator, then denominator).
      const missing = [kpi.numeratorFieldId, kpi.denominatorFieldId]
        // Look each field up in the library.
        .map((id) => fields.data.find((field) => field.id === id))
        // Keep fields that exist and are not in the form yet.
        .filter((field) => field && !existing.has(field.code));
      // Nothing missing for this KPI.
      if (missing.length === 0) continue;
      // Remember them, so a field shared by two KPIs is added once.
      missing.forEach((field) => existing.add(field.code));
      // A section titled after the KPI, holding those questions.
      sections.push({ type: 'panel', key: `kpi_${kpi.code.toLowerCase().replace(/-/g, '_')}`, title: `${kpi.name} (${kpi.code})`,
        input: false, components: missing.map(componentFor) });
    }
    // Nothing to add.
    if (sections.length === 0) {
      // Say so.
      setMessage({ text: 'The form already asks every field the ticked KPIs need.', error: false });
      // Stop.
      return;
    }
    // The definition with the new sections at the end.
    const updated = { ...schemaRef.current, components: [...schemaRef.current.components, ...sections] };
    // Keep it for saving ...
    schemaRef.current = updated;
    // ... and redraw the builder with it.
    setBuilderSchema(updated);
    // Say what happened.
    setMessage({ text: `Added ${sections.length} section(s) with the KPIs' fields.`, error: false });
  }

  // Saves the form.
  async function save() {
    // Now we will log what the user did.
    log('form', `Saving form ${editing.code} as ${editing.status}`);
    // Disable the button and clear an old message.
    setSaving(true);
    setMessage(null);
    // Catch errors so we can show them.
    try {
      // The settings plus the latest definition.
      const body = { ...editing, schema: schemaRef.current };
      // Now we will create (no id yet) or update the form.
      const saved = await (editing.id ? putJson(`/api/admin/forms/${editing.id}`, body) : postJson('/api/admin/forms', body));
      // Keep the saved settings (a new form now has an id).
      setEditing({ ...saved.form, description: saved.form.description ?? '' });
      // Say it worked.
      setMessage({ text: saved.form.status === 'PUBLISHED' ? 'Saved and published to registrars.' : 'Saved as a draft.', error: false });
      // Read the list again.
      forms.reload();
    } catch (error) {
      // Show the backend's message.
      setMessage({ text: error.message, error: true });
    } finally {
      // Enable the button again.
      setSaving(false);
    }
  }

  // The editor is open.
  if (editing) {
    // Now we will describe the editor.
    return (
      // The page content.
      <div className="page-content">
        {/* The header: back, title, save. */}
        <div className="toolbar">
          {/* Back to the list. */}
          <button type="button" className="button secondary" onClick={() => setEditing(null)}>
            {/* Arrow icon. */}
            <ArrowLeft size={16} /> All forms
          </button>
          {/* Save. */}
          <button type="button" className="button" onClick={save} disabled={saving}>
            {/* Disk icon. */}
            <Save size={16} /> {saving ? 'Saving…' : 'Save form'}
          </button>
        </div>
        {/* The result of saving. */}
        {message && <p className={message.error ? 'form-error' : 'notice'}>{message.text}</p>}

        {/* The settings. */}
        <section className="surface">
          {/* Code, name, status and description. */}
          <div className="form-grid four">
            {/* Code (fixed once saved). */}
            <label className="input-label">Code
              <input className="text-input" value={editing.code} disabled={Boolean(editing.id)}
                onChange={(event) => change('code', event.target.value.toUpperCase())} placeholder="CENSUS" />
            </label>
            {/* Name. */}
            <label className="input-label span-2">Name
              <input className="text-input" value={editing.name} onChange={(event) => change('name', event.target.value)}
                placeholder="Annual School Census" />
            </label>
            {/* Status. */}
            <label className="input-label">Status
              <select className="text-input" value={editing.status} onChange={(event) => change('status', event.target.value)}>
                {/* Still being built: registrars do not see it. */}
                <option value="DRAFT">Draft</option>
                {/* Given to registrars of the schools its KPIs cover. */}
                <option value="PUBLISHED">Published</option>
              </select>
            </label>
            {/* Description. */}
            <label className="input-label span-4">Description
              <input className="text-input" value={editing.description} onChange={(event) => change('description', event.target.value)} />
            </label>
          </div>

          {/* The KPIs this form collects data for. */}
          <h3 className="section-title">KPIs this form collects</h3>
          {/* One checkbox per KPI. */}
          <div className="kpi-checks">
            {kpis.data?.map((kpi) => (
              // The checkbox and its label.
              <label key={kpi.id} className="check">
                <input type="checkbox" checked={editing.kpiIds.includes(kpi.id)} onChange={() => toggleKpi(kpi.id)} />
                <code className="code-chip">{kpi.code}</code> {kpi.name}
                {kpi.status !== 'ACTIVE' && <StatusBadge status={kpi.status} />}
              </label>
            ))}
          </div>
          {/* Add the ticked KPIs' questions. */}
          <button type="button" className="button secondary" onClick={addKpiQuestions} disabled={!editing.kpiIds.length || !fields.data}>
            {/* List icon. */}
            <ListChecks size={16} /> Add questions for the ticked KPIs
          </button>
          {/* How keys work. */}
          <p className="muted small">
            Drag questions from <strong>Field library</strong> in the builder below. A question saves its answer to the field
            whose code is its <em>Property Name</em> (API tab), so keep that equal to a field code. Schools outside a KPI's scope
            are not shown questions that only that KPI needs.
          </p>
        </section>

        {/* The Form.io builder (drawn once the field library has loaded). */}
        <section className="surface builder-panel">
          {fields.data && builderSchema && (
            // Redrawn when builderSchema changes; every edit is kept in schemaRef.
            <FormioBuilder schema={builderSchema} fields={fields.data} onChange={(schema) => { schemaRef.current = schema; }} />
          )}
        </section>
      </div>
    );
  }

  // Now we will describe the list of forms.
  return (
    // The page content.
    <div className="page-content">
      {/* The toolbar. */}
      <div className="toolbar">
        {/* How many forms. */}
        <span className="muted">{forms.data ? `${forms.data.length} forms` : 'Loading…'}</span>
        {/* Start a new form. */}
        <button type="button" className="button" onClick={() => openEditor(null)}>
          {/* Plus icon. */}
          <Plus size={16} /> New form
        </button>
      </div>
      {/* A loading error. */}
      {forms.error && <p className="form-error">{forms.error}</p>}
      {/* The table of forms. */}
      {forms.data && (
        // A white panel holding the table.
        <div className="surface table-panel">
          {/* The table. */}
          <table className="data-table">
            {/* Column headings. */}
            <thead>
              <tr><th>Form</th><th>KPIs</th><th>Questions</th><th>Status</th><th>Updated</th><th /></tr>
            </thead>
            {/* One row per form. */}
            <tbody>
              {forms.data.map((form) => (
                // The row.
                <tr key={form.id}>
                  {/* Name, with code below. */}
                  <td><strong>{form.name}</strong><div className="muted small">{form.code}</div></td>
                  {/* Linked KPIs. */}
                  <td>{form.kpiCodes.map((code) => <code key={code} className="code-chip">{code}</code>)}</td>
                  {/* Number of questions. */}
                  <td>{form.questionCount}</td>
                  {/* Status pill. */}
                  <td><StatusBadge status={form.status} /></td>
                  {/* Last change. */}
                  <td className="muted">{new Date(form.updatedAt).toLocaleDateString()}</td>
                  {/* Edit button. */}
                  <td className="row-action">
                    {/* Opens the editor for this form. */}
                    <button type="button" className="icon-button" onClick={() => openEditor(form.id)} aria-label={`Edit ${form.name}`}>
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
    </div>
  );
}
