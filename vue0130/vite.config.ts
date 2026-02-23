import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vite.dev/config/

export default defineConfig({
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/api": {
        target: "http://172.17.0.1:8080",   // SpringBoot在宿主机
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, ""), // ✅ 注意这里
	configure: (proxy) => {
      	  proxy.on("proxyReq", (proxyReq) => {
        	proxyReq.removeHeader("origin");
      	  });
    	},
      }
    }
  },
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  }
})
