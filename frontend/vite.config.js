import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    // PWA instalável: service worker com atualização automática + manifest.
    // As cores correspondem aos design tokens do tema escuro (--bg em src/styles.css).
    VitePWA({
      registerType: 'autoUpdate',
      manifest: {
        name: 'Tracky',
        short_name: 'Tracky',
        description: 'Finanças pessoais',
        lang: 'pt-PT',
        display: 'standalone',
        start_url: '/',
        background_color: '#0b0d13',
        theme_color: '#0b0d13',
        icons: [
          { src: '/pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: '/pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          { src: '/pwa-maskable-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
    }),
  ],
  server: {
    port: 3000,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
  },
})
