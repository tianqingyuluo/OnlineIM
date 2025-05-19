import express from 'express';
import { createProxyMiddleware } from 'http-proxy-middleware';
import { API_BASE_URL } from './shared/config.js';
const app = express();

app.use('/', createProxyMiddleware({
    target: API_BASE_URL,
    changeOrigin: true,
    onProxyReq: (proxyReq, req, res) => {
        console.log("接受到请求");
        // 添加认证头
        const authHeader = req.headers['authorization'];
        if (authHeader) {
            proxyReq.setHeader('Authorization', authHeader);
        }
    }
}));

app.listen(8000, '0.0.0.0', () => {
    console.log(`Proxy server running on http://10.191.7.166:8000, forwarding to ${API_BASE_URL}`);
});