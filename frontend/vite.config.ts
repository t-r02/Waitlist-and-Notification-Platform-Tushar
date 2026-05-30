import path from "path";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    proxy: {
      "/api/public": {
        target: "http://localhost:8081",
        changeOrigin: true,
      },
      "/api/admin": {
        target: "http://localhost:8082",
        changeOrigin: true,
      },
    },
  },
});
