import { fileURLToPath, URL } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';
import { VitePWA } from 'vite-plugin-pwa';

const src = fileURLToPath(new URL('./src', import.meta.url));
const contract = fileURLToPath(new URL('../packages/contract', import.meta.url));

export default defineConfig({
  resolve: {
    alias: { '@': src, '@contract': contract },
  },
  server: { fs: { allow: ['..'] } },
  build: {
    target: 'es2023',
    sourcemap: true,
    manifest: true,
  },
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'prompt',
      injectRegister: false,
      includeAssets: ['favicon.svg', 'icons/*.png', 'fonts/*.woff2'],
      manifest: {
        name: 'Kpakam — sales and debt book',
        short_name: 'Kpakam',
        description: 'Record sales, expenses, and who owes you, in one sentence. Works offline.',
        theme_color: '#0f5132',
        background_color: '#fbfaf7',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        lang: 'en-NG',
        categories: ['business', 'finance', 'productivity'],
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,woff2,webmanifest}'],
        navigateFallback: '/index.html',
        cleanupOutdatedCaches: true,
        runtimeCaching: [],
      },
      devOptions: { enabled: false },
    }),
  ],
});
