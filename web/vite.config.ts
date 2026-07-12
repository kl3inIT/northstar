import { defineConfig } from 'vite'
import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    // React Compiler (Vite 8 form): build-time auto-memoization — state
    // changes re-render only the affected components, no hand-written memo.
    babel({ presets: [reactCompilerPreset()] }),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // Dev: forward API calls to the Spring api app.
      '/api': {
        target: process.env.NORTHSTAR_API_TARGET ?? 'http://127.0.0.1:8888',
        changeOrigin: true,
      },
    },
  },
})
