import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Proxy API calls to Spring Boot services during local development
    // In production, API Gateway handles routing
    proxy: {
      '/api/auth':       { target: 'http://localhost:8081', changeOrigin: true },
      '/api/courses':    { target: 'http://localhost:8082', changeOrigin: true },
      '/api/enrollment': { target: 'http://localhost:8083', changeOrigin: true },
      '/api/progress':   { target: 'http://localhost:8084', changeOrigin: true },
      '/ai':             { target: 'http://localhost:8085', changeOrigin: true },
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    // Split vendor chunks for better caching
    rollupOptions: {
      output: {
        manualChunks: {
          react:   ['react', 'react-dom', 'react-router-dom'],
          videojs: ['video.js'],
          axios:   ['axios'],
        }
      }
    }
  }
});
