import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        // 백엔드 실행 주소 및 포트. .env의 VITE_API_BASE_URL로 로컬 백엔드를 가리킬 수 있음
        target:
          loadEnv(mode, process.cwd(), '').VITE_API_BASE_URL ||
          'https://van-card-news-backend-571061095487.asia-northeast3.run.app',
        changeOrigin: true,
      },
    },
  },
}));
