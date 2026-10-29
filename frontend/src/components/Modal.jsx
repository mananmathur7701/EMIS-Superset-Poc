// The close icon.
import { X } from 'lucide-react';

// A dialog over the page: a title bar with a close button, the content, and buttons at the bottom.
// onClose() is called by the close button and by clicking outside the dialog.
export default function Modal({ title, onClose, children, footer, wide }) {
  // Now we will describe what to show.
  return (
    // The dark cover behind the dialog; clicking it (not the dialog) closes the dialog.
    <div className="dialog-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      {/* The dialog itself; "wide" makes it wider for bigger editors. */}
      <div className={wide ? 'dialog wide' : 'dialog'} role="dialog" aria-modal="true" aria-label={title}>
        {/* The title bar. */}
        <div className="dialog-header">
          {/* The title. */}
          <h2>{title}</h2>
          {/* The close button. */}
          <button type="button" className="icon-button" onClick={onClose} aria-label="Close">
            {/* A cross. */}
            <X size={18} />
          </button>
        </div>
        {/* The content, which scrolls if it is long. */}
        <div className="dialog-body">{children}</div>
        {/* The buttons. */}
        <div className="dialog-footer">{footer}</div>
      </div>
    </div>
  );
}
