// 本地开发代理服务器(proxy-server.js)使用的后端地址
// 可通过环境变量 API_BASE_URL 覆盖
export const API_BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080/api/v1';
