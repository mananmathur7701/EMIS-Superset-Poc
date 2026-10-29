// A full-size cover with the EMIS spinner and a message, laid over a workspace while it loads.
// It is the same spinner Superset now shows (see superset_config.py), so the hand-over is seamless.
export default function Loader({ message }) {
  // Now we will describe what to show.
  return (
    // Covers the whole workspace; announced to screen readers as a status.
    <div className="loading-cover" role="status">
      {/* The turning ring. */}
      <span className="spinner" aria-hidden="true" />
      {/* The message, e.g. "Loading census dashboard…". */}
      <span>{message}</span>
    </div>
  );
}
