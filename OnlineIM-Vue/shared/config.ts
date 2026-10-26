/**
 * 桌面端服务器地址。浏览器不调用这里；Tauri 启动时用它覆盖 shared/endpoints.js。
 * Web 端继续使用 endpoints.js 里的 localhost 默认值。
 */
export {
  API_BASE_URL,
  DEFAULT_API_BASE_URL,
  DEFAULT_WS_API_URL,
  SERVER_CONFIG_STORAGE_KEY,
  WS_API_URL,
  applyServerEndpoints,
  getApiBaseUrl,
  getWsBaseUrl,
  loadStoredServerEndpoints,
  normalizeServerEndpoint,
  resetServerEndpoints,
} from './endpoints.js'

export interface ServerEndpoints {
  api_base_url: string
  ws_base_url: string
}
