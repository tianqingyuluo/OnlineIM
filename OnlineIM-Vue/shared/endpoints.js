/**
 * 本地开发默认地址。浏览器里不读覆盖值；Tauri 由 shared/config.ts 在启动时注入。
 * 前端服务和 proxy-server 共用这里的默认值。
 */
export const DEFAULT_API_BASE_URL = 'http://localhost:8080/api/v1'
export const DEFAULT_WS_API_URL = 'ws://localhost:8081/api/v1/chat'
export const SERVER_CONFIG_STORAGE_KEY = 'onlineim.server_config'

export let API_BASE_URL = DEFAULT_API_BASE_URL
export let WS_API_URL = DEFAULT_WS_API_URL

function browserStorage() {
  try {
    if (typeof localStorage === 'undefined') return null
    return localStorage
  } catch {
    return null
  }
}

export function normalizeServerEndpoint(kind, value) {
  const trimmed = String(value ?? '').trim().replace(/\/+$/, '')
  if (!trimmed) {
    throw new Error('地址不能为空')
  }
  if (/\s/.test(trimmed)) {
    throw new Error('地址不能包含空白')
  }
  const lower = trimmed.toLowerCase()
  if (kind === 'api' && !/^https?:\/\//.test(lower)) {
    throw new Error('API 地址必须以 http:// 或 https:// 开头')
  }
  if (kind === 'ws' && !/^wss?:\/\//.test(lower)) {
    throw new Error('WebSocket 地址必须以 ws:// 或 wss:// 开头')
  }
  return trimmed
}

export function getApiBaseUrl() {
  return API_BASE_URL
}

export function getWsBaseUrl() {
  return WS_API_URL
}

export function applyServerEndpoints(next) {
  const apiBaseUrl = normalizeServerEndpoint('api', next?.api_base_url)
  const wsBaseUrl = normalizeServerEndpoint('ws', next?.ws_base_url)
  API_BASE_URL = apiBaseUrl
  WS_API_URL = wsBaseUrl
  const config = { api_base_url: apiBaseUrl, ws_base_url: wsBaseUrl }
  const storage = browserStorage()
  if (storage) {
    storage.setItem(SERVER_CONFIG_STORAGE_KEY, JSON.stringify(config))
  }
  return config
}

export function loadStoredServerEndpoints() {
  const storage = browserStorage()
  if (!storage) return null
  const raw = storage.getItem(SERVER_CONFIG_STORAGE_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    return {
      api_base_url: normalizeServerEndpoint('api', parsed?.api_base_url),
      ws_base_url: normalizeServerEndpoint('ws', parsed?.ws_base_url),
    }
  } catch {
    return null
  }
}

export function resetServerEndpoints() {
  API_BASE_URL = DEFAULT_API_BASE_URL
  WS_API_URL = DEFAULT_WS_API_URL
  const storage = browserStorage()
  if (storage) storage.removeItem(SERVER_CONFIG_STORAGE_KEY)
  return { api_base_url: API_BASE_URL, ws_base_url: WS_API_URL }
}
