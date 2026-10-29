// Helpers shared by the Form.io form and builder.

// Loads Form.io only when a form is first shown, so other users never download it.
export async function loadFormio() {
  // Now we will load the library and hand back its main object.
  return (await import('@formio/js')).Formio;
}

// Turns a field from the field library into a Form.io question whose key is the field code,
// so the answer is stored against that field.
export function componentFor(field) {
  // What every question has: its key, label, help text, and that it takes an answer.
  const base = { key: field.code, label: field.label, description: field.description ?? '', input: true };
  // A number: required, never negative.
  if (field.fieldType === 'NUMBER') return { ...base, type: 'number', validate: { required: true, min: 0 } };
  // A choice from a list: one entry per comma-separated option.
  if (field.fieldType === 'SELECT') {
    // The options, e.g. "Yes, No" becomes two choices.
    const values = (field.options ?? '').split(',').map((option) => option.trim()).filter(Boolean)
      // Each choice is shown and stored as the same text.
      .map((option) => ({ label: option, value: option }));
    // A dropdown with those choices.
    return { ...base, type: 'select', dataSrc: 'values', data: { values }, validate: { required: true } };
  }
  // A date (no time).
  if (field.fieldType === 'DATE') {
    // Form.io's date picker, date only.
    return { ...base, type: 'datetime', enableTime: false, format: 'yyyy-MM-dd', validate: { required: true } };
  }
  // Anything else: a one-line text box.
  return { ...base, type: 'textfield', validate: { required: true } };
}

// The icon (from Bootstrap Icons) shown next to a field in the builder's side menu.
export function iconFor(field) {
  // # for numbers, a list for choices, a calendar for dates, letters for text.
  return { NUMBER: 'hash', SELECT: 'list-ul', DATE: 'calendar', TEXT: 'fonts' }[field.fieldType] ?? 'fonts';
}
