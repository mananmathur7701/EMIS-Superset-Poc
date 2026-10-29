// Vite's helper for writing this config.
import { defineConfig } from 'vite';
// Vite's React plugin (understands JSX and reloads changes instantly).
import react from '@vitejs/plugin-react';

// Where the backend runs during development: the same port as server.port in application.yml.
// Set BACKEND_URL to use another one, e.g. BACKEND_URL=http://localhost:8180 for the Docker backend.
const backendUrl = process.env.BACKEND_URL ?? 'http://localhost:8081';

// Now we will export the Vite settings.
export default defineConfig({
  // Turn on React support.
  plugins: [react()],
  // The dev server (npm run dev).
  server: {
    // Run on port 3000, the address Superset allows to embed it; fail rather than pick another port.
    port: 3000,
    strictPort: true,
    // Forward every /api call to the backend, so the browser only ever talks to localhost:3000.
    proxy: { '/api': backendUrl },
  },
});
