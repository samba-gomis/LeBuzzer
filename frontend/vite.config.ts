import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The backend is never called by its own URL from the browser: everything goes
// through the Vite dev server, so the frontend only knows relative paths
// (`/api/...`, `/ws`) and there is no CORS to configure for the REST calls.
const BACKEND_URL = 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    // Listen on the local network too, so phones on the same Wi-Fi can join.
    host: true,
    proxy: {
      '/api': BACKEND_URL,
      // `ws: true` forwards the WebSocket upgrade request (STOMP endpoint).
      '/ws': { target: BACKEND_URL, ws: true },
    },
  },
})
