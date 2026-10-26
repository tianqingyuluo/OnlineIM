export const API_BASE_URL: string
export const WS_API_URL: string
export const DEFAULT_API_BASE_URL: string
export const DEFAULT_WS_API_URL: string
export const SERVER_CONFIG_STORAGE_KEY: string

export function normalizeServerEndpoint(kind: 'api' | 'ws', value: unknown): string
export function getApiBaseUrl(): string
export function getWsBaseUrl(): string
export function applyServerEndpoints(next: { api_base_url?: string, ws_base_url?: string }): {
  api_base_url: string
  ws_base_url: string
}
export function loadStoredServerEndpoints(): { api_base_url: string, ws_base_url: string } | null
export function resetServerEndpoints(): { api_base_url: string, ws_base_url: string }
