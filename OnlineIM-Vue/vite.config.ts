import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

const tauriPlatform = process.env.TAURI_ENV_PLATFORM
const tauriHost = process.env.TAURI_DEV_HOST

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  clearScreen: false,
  envPrefix: ['VITE_', 'TAURI_'],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: tauriHost || '0.0.0.0',
    port: 3000,
    strictPort: true,
    hmr: tauriHost
      ? { protocol: 'ws', host: tauriHost, port: 1421 }
      : undefined,
    watch: {
      ignored: ['**/src-tauri/**'],
    },
  },
  base: tauriPlatform ? './' : '/',
  build: tauriPlatform
    ? {
        target: tauriPlatform === 'windows' ? 'chrome105' : 'safari13',
        minify: process.env.TAURI_ENV_DEBUG ? false : 'esbuild',
        sourcemap: !!process.env.TAURI_ENV_DEBUG,
      }
    : undefined,
})