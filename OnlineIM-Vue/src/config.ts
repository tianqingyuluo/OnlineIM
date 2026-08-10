// 前端 API / WebSocket 地址配置
// 构建时可通过环境变量覆盖:VITE_API_BASE_URL、VITE_WS_API_URL
// 开发模式默认直连本机后端;生产构建默认同域相对路径(由 nginx 反代,免跨域)
const devApiBase = 'http://localhost:8080/api/v1'
const devWsUrl = 'ws://localhost:8081/api/v1/chat'

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || (import.meta.env.DEV ? devApiBase : '/api/v1')

const wsProtocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
export const WS_API_URL =
  import.meta.env.VITE_WS_API_URL || (import.meta.env.DEV ? devWsUrl : `${wsProtocol}://${window.location.host}/api/v1/chat`)
