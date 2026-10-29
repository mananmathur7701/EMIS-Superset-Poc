// React's hooks: run code after drawing, and point at a div or value.
import { useEffect, useRef } from 'react';
// Form.io helpers.
import { componentFor, iconFor, loadFormio } from './formio';

// The Form.io drag-and-drop form builder. Its side menu starts with a "Field library" group: one
// ready-made question per library field, whose key is already the field code. onChange(schema) is
// called with the new form definition after every change.
export default function FormioBuilder({ schema, fields, onChange }) {
  // Points at the div the builder is drawn into.
  const mountRef = useRef(null);
  // The latest onChange, so a new function from the page does not redraw the builder.
  const onChangeRef = useRef(onChange);
  // Keep it up to date on every draw.
  onChangeRef.current = onChange;

  // Draw the builder again whenever the starting definition or the field library changes.
  useEffect(() => {
    // The builder, once drawn.
    let builder;
    // Becomes true if the page goes away before the builder is ready.
    let cancelled = false;

    // Loads Form.io and draws the builder.
    async function draw() {
      // Now we will load Form.io.
      const Formio = await loadFormio();
      // The page went away meanwhile: stop.
      if (cancelled) return;
      // One side-menu entry per active field: field code -> its ready-made question.
      const libraryEntries = Object.fromEntries(fields.filter((field) => field.active).map((field) => [field.code, {
        // The name shown in the side menu.
        title: field.label,
        // The question's key.
        key: field.code,
        // A small icon for the field type.
        icon: iconFor(field),
        // The question that is dropped into the form.
        schema: componentFor(field),
      }]));
      // Now we will draw the builder.
      builder = await Formio.builder(mountRef.current, schema, {
        // Do not add a "Submit" button: the EMIS page has its own buttons.
        noDefaultSubmitButton: true,
        // The side menu groups.
        builder: {
          // Our field library first, open by default.
          fieldLibrary: { title: 'Field library', weight: 0, default: true, components: libraryEntries },
          // Hide the "Data" group (grids and containers store answers in a shape we do not read).
          data: false,
          // Hide the paid components.
          premium: false,
        },
      });
      // The page went away meanwhile: remove the builder again.
      if (cancelled) {
        // Remove it.
        builder.destroy();
        // Stop.
        return;
      }
      // After every change, hand the new definition to the page.
      builder.on('change', () => onChangeRef.current(builder.schema));
    }

    // Start drawing.
    draw();
    // When the page goes away or the inputs change, remove this builder.
    return () => {
      // Stop a draw that is still running.
      cancelled = true;
      // Remove the builder, if it was drawn.
      builder?.destroy();
    };
  }, [schema, fields]);

  // The div Form.io draws into.
  return <div ref={mountRef} className="formio-builder-mount" />;
}
