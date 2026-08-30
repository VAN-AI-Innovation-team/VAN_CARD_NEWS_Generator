import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target:
          'https://van-card-news-backend-571061095487.asia-northeast3.run.app', // 백엔드 실행 주소 및 포트
        changeOrigin: true,
      },
    },
  },
});
