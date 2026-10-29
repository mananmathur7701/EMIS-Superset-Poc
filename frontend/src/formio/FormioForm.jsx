// React's hooks: run code after drawing, and point at a div.
import { useEffect, useRef } from 'react';
// Loads Form.io when needed.
import { loadFormio } from './formio';

// Shows a Form.io form with answers filled in. formRef.current becomes the Form.io form, so the
// page can read the answers (formRef.current.submission.data) and check them (submit()).
export default function FormioForm({ schema, data, readOnly, formRef }) {
  // Points at the div the form is drawn into.
  const mountRef = useRef(null);

  // Draw the form again whenever the definition, the answers or read-only mode change.
  useEffect(() => {
    // The Form.io form, once drawn.
    let form;
    // Becomes true if the page goes away before the form is ready.
    let cancelled = false;

    // Loads Form.io and draws the form.
    async function draw() {
      // Now we will load Form.io.
      const Formio = await loadFormio();
      // The page went away meanwhile: stop.
      if (cancelled) return;
      // Now we will draw the form; noAlerts hides Form.io's own banner (errors still show per question).
      form = await Formio.createForm(mountRef.current, schema, { readOnly, noAlerts: true });
      // The page went away meanwhile: remove the form again.
      if (cancelled) {
        // Remove it.
        form.destroy();
        // Stop.
        return;
      }
      // Fill in the saved answers.
      form.submission = { data: data ?? {} };
      // Let the page reach the form.
      if (formRef) formRef.current = form;
    }

    // Start drawing.
    draw();
    // When the page goes away or the inputs change, remove this form.
    return () => {
      // Stop a draw that is still running.
      cancelled = true;
      // Remove the form, if it was drawn.
      form?.destroy();
      // The page can no longer reach it.
      if (formRef) formRef.current = null;
    };
  }, [schema, data, readOnly, formRef]);

  // The div Form.io draws into.
  return <div ref={mountRef} className="formio-mount" />;
}
