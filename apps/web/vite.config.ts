import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { quasar, transformAssetUrls } from '@quasar/vite-plugin'
import { VitePWA } from 'vite-plugin-pwa'
export default defineConfig({
  plugins: [vue({ template: { transformAssetUrls } }), quasar(), VitePWA({
    registerType: 'autoUpdate', includeAssets: ['icon.svg', 'apple-touch-icon.png'],
    manifest: { name: 'MY TODAY · 나의 오늘', short_name: 'MY TODAY', lang: 'ko', start_url: '/', scope: '/', display: 'standalone', background_color: '#f7f9fc', theme_color: '#3665db', icons: [{ src: '/pwa-192.png', sizes: '192x192', type: 'image/png' }, { src: '/pwa-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' }] },
    workbox: { globPatterns: ['**/*.{js,css,html,svg,png,woff2}'], navigateFallbackDenylist: [/^\/api(?:\/|$)/], cleanupOutdatedCaches: true },
  })],
  server: { port: 5173, proxy: { '/api': { target: process.env.API_PROXY_TARGET || 'http://localhost:8080' } } },
})
