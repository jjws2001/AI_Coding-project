import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  define: {
    global: 'globalThis',
  },
  server: {
    port: 3000,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true
      },
      "/oauth2": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true
      },
      "/login": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true
      },
      "/ws": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
        ws: true
      }
    }
  }
});
