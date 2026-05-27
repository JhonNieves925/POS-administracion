import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'icons/*.png'],
      manifest: {
        name: 'DISTRIASOCIADOS',
        short_name: 'Distri',
        description: 'Sistema de inventario para distribuidora de bebidas',
        theme_color: '#D32F2F',
        background_color: '#D32F2F',
        display: 'standalone',
        orientation: 'portrait',
        start_url: '/',
        scope: '/',
        lang: 'es',
        icons: [
          {
            src: '/icons/icon-192.png',
            sizes: '192x192',
            type: 'image/png',
            purpose: 'any'
          },
          {
            src: '/icons/icon-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable'
          }
        ]
      },
      devOptions: {
        enabled: true,
        type: 'module'
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg}'],
        navigateFallbackDenylist: [/^\/api/],
        runtimeCaching: [
          {
            urlPattern: /\/api\/cargues.*/,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'cargues-cache',
              expiration: { maxEntries: 50, maxAgeSeconds: 86400 }
            }
          },
          {
            urlPattern: /\/api\/remisiones.*/,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'remisiones-cache',
              expiration: { maxEntries: 200, maxAgeSeconds: 86400 }
            }
          },
          {
            urlPattern: /\/api\/productos.*/,
            handler: 'StaleWhileRevalidate',
            options: {
              cacheName: 'productos-cache',
              expiration: { maxEntries: 200, maxAgeSeconds: 604800 }
            }
          },
          {
            urlPattern: /\/api\/clientes.*/,
            handler: 'StaleWhileRevalidate',
            options: {
              cacheName: 'clientes-cache',
              expiration: { maxEntries: 500, maxAgeSeconds: 86400 }
            }
          }
        ]
      }
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
