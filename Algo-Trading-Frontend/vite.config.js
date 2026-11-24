import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendTarget = env.VITE_BACKEND_TARGET || 'http://localhost:8080'
  return {
  plugins: [react()],
  server: {
    proxy: {
      '/api': { target: backendTarget, changeOrigin: true },
      '/actuator': { target: backendTarget, changeOrigin: true },
      '/ws': { target: backendTarget, changeOrigin: true, ws: true },
    },
  },
  }
})
