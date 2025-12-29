import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 8081,
    strictPort: true,
    watch: {
      usePolling: true,
      interval: 1000,
    },
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  cacheDir: '/tmp/.vite',
  optimizeDeps: {
    force: true,
  },
});
