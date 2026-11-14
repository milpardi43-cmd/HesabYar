import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// The live preview is served through an arbitrary *.e2b.app hostname.
// Keep the development server reachable from that proxy, on all interfaces.
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
    allowedHosts: true,
  },
  preview: {
    host: '0.0.0.0',
    port: 4173,
    strictPort: true,
    allowedHosts: true,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    restoreMocks: true,
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
  },
})
