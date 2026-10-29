// React's hooks: remember values, run code after drawing, keep a stable function.
import { useCallback, useEffect, useState } from 'react';
// Our helper for reading from the backend.
import { getJson } from './api';

// Reads JSON from the backend when a page opens. Returns { data, error, reload }: data is null
// while loading, error is a message if the call failed, and reload() reads it again.
export default function useLoad(path) {
  // What we read, and any error.
  const [state, setState] = useState({ data: null, error: null });

  // Reads the path again (a stable function, so pages can call it after saving).
  const reload = useCallback(() => {
    // Now we will call the backend.
    return getJson(path)
      // Keep what it sent.
      .then((data) => setState({ data, error: null }))
      // Or keep the error message.
      .catch((error) => setState({ data: null, error: error.message }));
  }, [path]);

  // Read once when the page opens (and again if the path changes).
  useEffect(() => {
    // Start reading.
    reload();
  }, [reload]);

  // Hand back the data, the error and the reload function.
  return { ...state, reload };
}
