import { watch } from 'vue'
import {
  applyServerEndpoints,
  getApiBaseUrl,
  getWsBaseUrl,
  loadStoredServerEndpoints,
  resetServerEndpoints,
  type ServerEndpoints,
} from '../../shared/config'

export interface FocusState {
  hidden: boolean
  hasFocus: boolean
}

export type NoticeRoute = 'native' | 'toast' | 'silent'

export interface IncomingNotice {
  title: string
  body: string
  conversationId?: string | null
  viewing?: boolean
  focus?: FocusState
  toast?: (title: string, body: string) => void
}

type Invoke = (command: string, args?: Record<string, unknown>) => Promise<unknown>

let invokeOverride: Invoke | null = null

export function setTauriInvokeForTests(invoke: Invoke | null): void {
  invokeOverride = invoke
}

export function isTauriRuntime(): boolean {
  return typeof window !== 'undefined' && ('__TAURI_INTERNALS__' in window || '__TAURI__' in window)
}

export function readDocumentFocus(doc: Pick<Document, 'hidden' | 'hasFocus'> | null = typeof document === 'undefined' ? null : document): FocusState {
  if (!doc) return { hidden: false, hasFocus: true }
  return { hidden: doc.hidden, hasFocus: doc.hasFocus() }
}

/** 只有桌面壳在窗口隐藏或失焦时走系统通知。浏览器和前台窗口继续用应用内 toast。 */
export function shouldUseNativeNotification(focus: FocusState, tauri = isTauriRuntime()): boolean {
  return tauri && (focus.hidden || !focus.hasFocus)
}

async function callInvoke<T>(command: string, args?: Record<string, unknown>): Promise<T> {
  if (invokeOverride) return await invokeOverride(command, args) as T
  const mod = await import('@tauri-apps/api/core')
  return mod.invoke<T>(command, args)
}

export async function syncUnreadBadge(count: number): Promise<void> {
  if (!isTauriRuntime()) return
  const safe = Number.isFinite(count) && count > 0 ? Math.floor(count) : 0
  await callInvoke('set_unread_badge', { count: safe })
}

export async function sendNativeNotification(title: string, body: string, conversationId?: string | null): Promise<boolean> {
  if (!isTauriRuntime()) return false
  return callInvoke<boolean>('send_native_notification', {
    title,
    body,
    conversationId: conversationId || null,
  })
}

async function defaultToast(title: string, body: string): Promise<void> {
  const { toast } = await import('vue-sonner')
  toast.info(title, { description: body })
}

export async function notifyIncomingMessage(input: IncomingNotice): Promise<NoticeRoute> {
  const focus = input.focus ?? readDocumentFocus()
  if (input.viewing && !shouldUseNativeNotification(focus)) return 'silent'
  if (shouldUseNativeNotification(focus)) {
    await sendNativeNotification(input.title, input.body, input.conversationId)
    return 'native'
  }
  if (input.toast) {
    input.toast(input.title, input.body)
  } else {
    await defaultToast(input.title, input.body)
  }
  return 'toast'
}

export function notifyIncomingCall(input: Omit<IncomingNotice, 'viewing'>): Promise<NoticeRoute> {
  return notifyIncomingMessage({ ...input, viewing: false })
}

export async function getServerConfig(): Promise<ServerEndpoints> {
  if (!isTauriRuntime()) {
    return { api_base_url: getApiBaseUrl(), ws_base_url: getWsBaseUrl() }
  }
  const remote = await callInvoke<ServerEndpoints>('get_server_config')
  return applyServerEndpoints(remote)
}

export async function setServerConfig(apiBaseUrl: string, wsBaseUrl: string): Promise<ServerEndpoints> {
  if (!isTauriRuntime()) {
    return applyServerEndpoints({ api_base_url: apiBaseUrl, ws_base_url: wsBaseUrl })
  }
  const saved = await callInvoke<ServerEndpoints>('set_server_config', {
    apiBaseUrl,
    wsBaseUrl,
  })
  return applyServerEndpoints(saved)
}

export async function saveE2eeKeyBundle(userId: string, bundleJson: string): Promise<void> {
  if (!isTauriRuntime()) return
  await callInvoke('save_e2ee_key_bundle', { userId, bundleJson })
}

export async function loadE2eeKeyBundle(userId: string): Promise<string | null> {
  if (!isTauriRuntime()) return null
  const value = await callInvoke<string | null>('load_e2ee_key_bundle', { userId })
  return value ?? null
}

export async function deleteE2eeKeyBundle(userId: string): Promise<void> {
  if (!isTauriRuntime()) return
  await callInvoke('delete_e2ee_key_bundle', { userId })
}

export async function quitApp(): Promise<void> {
  if (!isTauriRuntime()) return
  await callInvoke('quit_app')
}

export interface E2eeBundleMerge {
  bundle: string | null
  persistNative: boolean
  persistLocal: boolean
}

/** IndexedDB 有密钥时以它为准并补写本机文件；只有本机文件时再写回 IndexedDB。 */
export function mergeE2eeBundleSources(local: string | null, native: string | null): E2eeBundleMerge {
  if (local && !native) return { bundle: local, persistNative: true, persistLocal: false }
  if (!local && native) return { bundle: native, persistNative: false, persistLocal: true }
  return { bundle: local, persistNative: false, persistLocal: false }
}

export function currentServerEndpoints(): ServerEndpoints {
  return { api_base_url: getApiBaseUrl(), ws_base_url: getWsBaseUrl() }
}

export { loadStoredServerEndpoints, resetServerEndpoints }

function conversationPath(conversationId: string): string {
  const kind = conversationId.startsWith('grp_') ? 'group' : 'private'
  return `/main/chat/${kind}/${conversationId}`
}

export async function bootstrapTauriDesktop(): Promise<void> {
  if (!isTauriRuntime()) return
  const stored = loadStoredServerEndpoints()
  if (stored) applyServerEndpoints(stored)
  try {
    const remote = await callInvoke<ServerEndpoints>('get_server_config')
    applyServerEndpoints(remote)
  } catch (error) {
    console.error('读取桌面端服务器地址失败:', error)
  }
  try {
    const { useListStore } = await import('@/stores/list')
    const list = useListStore()
    watch(() => list.totalUnread, (count) => {
      void syncUnreadBadge(count).catch(error => console.error('更新托盘未读数失败:', error))
    }, { immediate: true })
  } catch (error) {
    console.error('监听未读数失败:', error)
  }
  try {
    const { listen } = await import('@tauri-apps/api/event')
    const routerModule = await import('@/router')
    await listen<{ conversation_id?: string | null }>('notification-activated', (event) => {
      const conversationId = event.payload?.conversation_id
      if (!conversationId) return
      void routerModule.default.push(conversationPath(conversationId))
    })
  } catch (error) {
    console.error('监听系统通知点击失败:', error)
  }
}
