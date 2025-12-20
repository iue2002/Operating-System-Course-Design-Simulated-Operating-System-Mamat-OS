import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
    plugins: [vue()],
    server: {
        port: 5173,
        hmr: {
            host: 'localhost',
            protocol: 'ws',
            clientPort: 5173
        },
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
                secure: false,
                rewrite: (path) => path
            },
            '/agent-api': {
                target: 'https://agentapi.baidu.com',
                changeOrigin: true,
                secure: true,
                rewrite: (path) => path.replace(/^\/agent-api/, '')
            }
        }
    }
})

