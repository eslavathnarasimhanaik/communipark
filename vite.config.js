import { defineConfig } from 'vite';
import { resolve } from 'path';
import { copyFileSync, existsSync, mkdirSync } from 'fs';

// Plugin to copy standalone (non-module) JS files to dist
function copyStandaloneJs() {
  const files = ['s.js', 'dash.js', 'Reservations.js', 'admin.js'];
  return {
    name: 'copy-standalone-js',
    closeBundle() {
      const distDir = resolve(__dirname, 'dist');
      if (!existsSync(distDir)) mkdirSync(distDir, { recursive: true });
      for (const file of files) {
        const src = resolve(__dirname, file);
        const dest = resolve(distDir, file);
        if (existsSync(src)) {
          copyFileSync(src, dest);
          console.log(`Copied ${file} → dist/${file}`);
        }
      }
    }
  };
}

export default defineConfig({
  build: {
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        payment: resolve(__dirname, 'payment.html'),
        checkout: resolve(__dirname, 'checkout.html'),
        reservations: resolve(__dirname, 'Reservations.html'),
        profile: resolve(__dirname, 'Profile.html'),
        admin: resolve(__dirname, 'admin.html'),
        dashboard: resolve(__dirname, 'dashboard.html'),
        exit: resolve(__dirname, 'exit.html'),
        bank_gateway: resolve(__dirname, 'bank_gateway.html'),
      },
    },
  },
  plugins: [copyStandaloneJs()],
});
