// React's hooks: remember values and point at the form.
import { useRef, useState } from 'react';
// Icons for the buttons.
import { ArrowLeft, Check, CircleAlert, Save, Send, Undo2 } from 'lucide-react';
// Our helpers for calling the backend.
import { postJson, putJson } from '../../api/api';
// The Form.io form.
import FormioForm from '../../formio/FormioForm';
// The tracker and history.
import { ReturnHistory, WorkflowTracker } from './ReturnTimeline';
// The status pill.
import StatusBadge from '../../components/StatusBadge';
// Browser-console logging.
import { log } from '../../logging/logger';

// One return on screen: the form with its answers, where it is in the review flow, its history,
// and the buttons this user may press. The registrar can save and submit a draft; the reviewer at
// the current step can approve it or send it back. onBack() closes it; onChanged() runs after a change.
export default function ReturnView({ detail, onBack, onChanged }) {
  // The Form.io form, so we can read and check the answers.
  const formRef = useRef(null);
  // The return as last loaded or saved.
  const [current, setCurrent] = useState(detail);
  // The reviewer's comment.
  const [comment, setComment] = useState('');
  // True while a request is running (the buttons are then disabled).
  const [busy, setBusy] = useState(false);
  // A message after an action, e.g. "Draft saved." ({ text, error }).
  const [message, setMessage] = useState(null);

  // The parts we use below.
  const { summary, schema, data, history, canEdit } = current;
  // The address that saves this school's answers to this form.
  const savePath = `/api/returns/form/${summary.formId}/school/${summary.schoolId}`;

  // Runs a request, shows its result and tells the page. action() returns the updated return.
  async function run(action, doneText) {
    // Disable the buttons and clear the old message.
    setBusy(true);
    setMessage(null);
    // Catch errors so we can show them.
    try {
      // Now we will run the request and keep the updated return.
      const updated = await action();
      // Show it.
      setCurrent(updated);
      // Clear the comment box.
      setComment('');
      // Say it worked.
      setMessage({ text: doneText, error: false });
      // Let the page refresh its list.
      onChanged?.();
    } catch (error) {
      // Show what went wrong (Form.io's own check errors are shown on the questions instead).
      setMessage({ text: error.message ?? 'Please fix the highlighted answers.', error: true });
    } finally {
      // Enable the buttons again.
      setBusy(false);
    }
  }

  // "Save draft": store the answers without checking them.
  function saveDraft() {
    // Now we will log what the user did.
    log('return', `Save draft: ${summary.formName}, ${summary.schoolName}`);
    // Send the current answers.
    run(() => putJson(savePath, { data: formRef.current.submission.data, submit: false }), 'Draft saved.');
  }

  // "Submit for review": check the answers in the form, then send them to the principal.
  function submit() {
    // Now we will log what the user did.
    log('return', `Submit for review: ${summary.formName}, ${summary.schoolName}`);
    // Run the request.
    run(async () => {
      // Now we will let Form.io check every answer (it marks wrong ones and stops here if any are wrong).
      const submission = await formRef.current.submit().catch(() => {
        // Stop with a short message; the questions show the details.
        throw new Error('Please fix the highlighted answers before submitting.');
      });
      // Now we will save and submit.
      return putJson(savePath, { data: submission.data, submit: true });
    }, 'Submitted to the principal.');
  }

  // "Approve" or "Send back".
  function review(action) {
    // Now we will log what the user did.
    log('return', `${action} return ${summary.id} (${summary.schoolName}, now ${summary.status})`, { comment });
    // Send the decision and the comment.
    run(() => postJson(`/api/returns/${summary.id}/review`, { action, comment }),
      action === 'APPROVE' ? 'Approved.' : 'Sent back one step.');
  }

  // The approve button's text: the CPU analyst's approval freezes the return.
  const approveText = summary.status === 'WITH_CPU' ? 'Approve and freeze' : 'Approve';
  // Only a reviewer at the current step (not the registrar) sees the review box.
  const canReview = summary.actionable && summary.status !== 'DRAFT' && summary.id;

  // Now we will describe what to show.
  return (
    // The whole view.
    <div className="return-view">
      {/* The header: back button, title and status. */}
      <div className="return-header">
        {/* Back to the list. */}
        <button type="button" className="button secondary" onClick={onBack}>
          {/* Arrow icon. */}
          <ArrowLeft size={16} /> Back
        </button>
        {/* Form name and school. */}
        <div className="return-title">
          {/* e.g. "Annual School Census". */}
          <h2>{summary.formName}</h2>
          {/* e.g. "Sample Primary School A · D03 · 2026/2027". */}
          <span className="muted">{summary.schoolName} · {summary.districtCode} · {summary.academicYear}</span>
        </div>
        {/* The status pill. */}
        <StatusBadge status={summary.status} />
      </div>

      {/* Where the return is in the flow. */}
      <WorkflowTracker status={summary.status} />

      {/* A returned draft: show the reviewer's reason to the registrar. */}
      {canEdit && summary.lastReturnComment && (
        // An amber notice.
        <p className="notice warning">
          {/* Warning icon. */}
          <CircleAlert size={16} /> Sent back: {summary.lastReturnComment}
        </p>
      )}

      {/* The form on the left, actions and history on the right. */}
      <div className="return-body">
        {/* The form, editable only for the registrar's draft. */}
        <section className="surface">
          {/* Form.io draws the questions here. */}
          <FormioForm schema={schema} data={data} readOnly={!canEdit} formRef={formRef} />
        </section>

        {/* The side column. */}
        <aside className="return-side">
          {/* The registrar's buttons. */}
          {canEdit && (
            // A panel with the two buttons.
            <section className="surface">
              {/* Heading. */}
              <h3>Your return</h3>
              {/* Explanation. */}
              <p className="muted small">Save as often as you like. Submit when every answer is in; the principal reviews it next.</p>
              {/* The buttons. */}
              <div className="button-row">
                {/* Save without checking. */}
                <button type="button" className="button secondary" onClick={saveDraft} disabled={busy}>
                  {/* Disk icon. */}
                  <Save size={16} /> Save draft
                </button>
                {/* Check and submit. */}
                <button type="button" className="button" onClick={submit} disabled={busy}>
                  {/* Paper plane icon. */}
                  <Send size={16} /> Submit for review
                </button>
              </div>
            </section>
          )}

          {/* The reviewer's box. */}
          {canReview && (
            // A panel with the comment and the two buttons.
            <section className="surface">
              {/* Heading. */}
              <h3>Your review</h3>
              {/* The comment box. */}
              <label className="input-label" htmlFor="review-comment">Comment (required to send back)</label>
              {/* The comment itself. */}
              <textarea id="review-comment" className="text-input" rows={3} value={comment}
                onChange={(event) => setComment(event.target.value)} placeholder="e.g. Please check the trained teachers figure." />
              {/* The buttons. */}
              <div className="button-row">
                {/* One step back; needs a comment. */}
                <button type="button" className="button secondary" onClick={() => review('RETURN')} disabled={busy || !comment.trim()}>
                  {/* Undo icon. */}
                  <Undo2 size={16} /> Send back
                </button>
                {/* One step forward. */}
                <button type="button" className="button" onClick={() => review('APPROVE')} disabled={busy}>
                  {/* Tick icon. */}
                  <Check size={16} /> {approveText}
                </button>
              </div>
            </section>
          )}

          {/* The result of the last action. */}
          {message && <p className={message.error ? 'form-error' : 'notice'}>{message.text}</p>}

          {/* The history. */}
          <section className="surface">
            {/* Heading. */}
            <h3>History</h3>
            {/* The events. */}
            <ReturnHistory history={history} />
          </section>
        </aside>
      </div>
    </div>
  );
}
