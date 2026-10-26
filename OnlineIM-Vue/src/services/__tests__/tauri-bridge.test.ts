import { afterEach, describe, expect, it, vi } from 'vitest'
import { buildDeviceLoginFields, tauriDesktopDeviceName } from '@/utils/device-session'
import {
  currentServerEndpoints,
  deleteE2eeKeyBundle,
  getServerConfig,
  isTauriRuntime,
  loadE2eeKeyBundle,
  mergeE2eeBundleSources,
  notifyIncomingCall,
  notifyIncomingMessage,
  resetServerEndpoints,
  saveE2eeKeyBundle,
  setServerConfig,
  setTauriInvokeForTests,
  shouldUseNativeNotification,
  syncUnreadBadge,
} from '@/services/tauri-bridge'

function memoryStorage(initial: Record<string, string> = {}) {
  const data = { ...initial }
  return {
    getItem(key: string) {
      return data[key] ?? null
    },
    setItem(key: string, value: string) {
      data[key] = value
    },
  }
}

function markTauri() {
  ;(window as Window & { __TAURI_INTERNALS__?: unknown }).__TAURI_INTERNALS__ = {}
}

function clearTauri() {
  const runtime = window as Window & { __TAURI_INTERNALS__?: unknown, __TAURI__?: unknown }
  delete runtime.__TAURI_INTERNALS__
  delete runtime.__TAURI__
  setTauriInvokeForTests(null)
  resetServerEndpoints()
}

describe('tauri bridge', () => {
  afterEach(() => {
    clearTauri()
  })

  it('detects the tauri runtime and stays quiet in a browser', () => {
    expect(isTauriRuntime()).toBe(false)
    markTauri()
    expect(isTauriRuntime()).toBe(true)
    clearTauri()
    ;(window as Window & { __TAURI__?: unknown }).__TAURI__ = {}
    expect(isTauriRuntime()).toBe(true)
  })

  it('routes native notifications only when the desktop window is hidden or blurred', async () => {
    markTauri()
    const invoke = vi.fn(async () => true)
    setTauriInvokeForTests(invoke)
    const toast = vi.fn()

    await expect(notifyIncomingMessage({
      title: '小明',
      body: '你好',
      conversationId: 'conv_1',
      viewing: false,
      focus: { hidden: true, hasFocus: false },
      toast,
    })).resolves.toBe('native')
    expect(invoke).toHaveBeenCalledWith('send_native_notification', {
      title: '小明',
      body: '你好',
      conversationId: 'conv_1',
    })
    expect(toast).not.toHaveBeenCalled()

    invoke.mockClear()
    await expect(notifyIncomingMessage({
      title: '小明',
      body: '在线',
      conversationId: 'conv_1',
      focus: { hidden: false, hasFocus: false },
      toast,
    })).resolves.toBe('native')

    invoke.mockClear()
    await expect(notifyIncomingMessage({
      title: '小明',
      body: '正在看',
      conversationId: 'conv_1',
      focus: { hidden: false, hasFocus: true },
      toast,
    })).resolves.toBe('toast')
    expect(invoke).not.toHaveBeenCalled()
    expect(toast).toHaveBeenCalledWith('小明', '正在看')
    expect(shouldUseNativeNotification({ hidden: false, hasFocus: true }, true)).toBe(false)
  })

  it('keeps vue toasts in the browser even when the tab is hidden', async () => {
    const toast = vi.fn()
    const invoke = vi.fn()
    setTauriInvokeForTests(invoke)
    await expect(notifyIncomingCall({
      title: '小明',
      body: '邀请你视频通话',
      conversationId: 'conv_call',
      focus: { hidden: true, hasFocus: false },
      toast,
    })).resolves.toBe('toast')
    expect(invoke).not.toHaveBeenCalled()
    expect(toast).toHaveBeenCalledWith('小明', '邀请你视频通话')
    await expect(notifyIncomingMessage({
      title: '小明',
      body: '当前会话',
      viewing: true,
      focus: { hidden: false, hasFocus: true },
      toast,
    })).resolves.toBe('silent')
  })

  it('pushes the unread total to the tray only inside tauri', async () => {
    const invoke = vi.fn(async () => undefined)
    setTauriInvokeForTests(invoke)
    await syncUnreadBadge(0)
    expect(invoke).not.toHaveBeenCalled()

    markTauri()
    await syncUnreadBadge(3.8)
    await syncUnreadBadge(-2)
    expect(invoke).toHaveBeenNthCalledWith(1, 'set_unread_badge', { count: 3 })
    expect(invoke).toHaveBeenNthCalledWith(2, 'set_unread_badge', { count: 0 })
  })

  it('overrides server endpoints and asks the shell to persist them', async () => {
    expect(currentServerEndpoints()).toEqual({
      api_base_url: 'http://localhost:8080/api/v1',
      ws_base_url: 'ws://localhost:8081/api/v1/chat',
    })
    const webSaved = await setServerConfig('https://im.example.com/api/v1/', 'wss://im.example.com/api/v1/chat')
    expect(webSaved.api_base_url).toBe('https://im.example.com/api/v1')
    expect(currentServerEndpoints().ws_base_url).toBe('wss://im.example.com/api/v1/chat')
    resetServerEndpoints()

    markTauri()
    const invoke = vi.fn(async (_command: string, args?: Record<string, unknown>) => ({
      api_base_url: args?.apiBaseUrl,
      ws_base_url: args?.wsBaseUrl,
    }))
    setTauriInvokeForTests(invoke)
    const saved = await setServerConfig('http://10.0.0.8:8080/api/v1', 'ws://10.0.0.8:8081/api/v1/chat')
    expect(invoke).toHaveBeenCalledWith('set_server_config', {
      apiBaseUrl: 'http://10.0.0.8:8080/api/v1',
      wsBaseUrl: 'ws://10.0.0.8:8081/api/v1/chat',
    })
    expect(saved).toEqual({
      api_base_url: 'http://10.0.0.8:8080/api/v1',
      ws_base_url: 'ws://10.0.0.8:8081/api/v1/chat',
    })
    invoke.mockResolvedValueOnce({
      api_base_url: 'http://10.0.0.8:8080/api/v1',
      ws_base_url: 'ws://10.0.0.8:8081/api/v1/chat',
    })
    await expect(getServerConfig()).resolves.toEqual(saved)
    await expect(setServerConfig('notaurl', 'ws://localhost:8081/api/v1/chat')).rejects.toThrow(/http/)
  })

  it('reports a desktop device when the shell is tauri', () => {
    const storage = memoryStorage()
    const linux = buildDeviceLoginFields({
      uaDeviceType: 'mobile',
      override: 'web',
      os: 'Linux',
      shell: 'tauri',
      storage,
      random: 'desk',
      now: 8,
    })
    expect(linux.device_type).toBe('desktop')
    expect(linux.device_name).toBe('Tauri Desktop (Linux)')
    expect(linux.device_id.startsWith('desktop-')).toBe(true)
    expect(tauriDesktopDeviceName('Windows')).toBe('Tauri Desktop (Windows)')
    expect(buildDeviceLoginFields({
      os: 'Windows',
      shell: 'tauri',
      clientName: '会议室电脑',
      storage: memoryStorage(),
      random: 'w',
      now: 9,
    }).device_name).toBe('会议室电脑')
    expect(buildDeviceLoginFields({
      uaDeviceType: undefined,
      storage: memoryStorage({ 'onlineim.device_id.web': 'web-stable' }),
    }).device_type).toBe('web')
  })

  it('mirrors the e2ee key bundle through the native store', async () => {
    const invoke = vi.fn(async (command: string) => {
      if (command === 'load_e2ee_key_bundle') return '{"keyId":"native"}'
      return undefined
    })
    setTauriInvokeForTests(invoke)
    await saveE2eeKeyBundle('usr_1', '{"keyId":"local"}')
    expect(invoke).not.toHaveBeenCalled()

    markTauri()
    await saveE2eeKeyBundle('usr_1', '{"keyId":"local"}')
    expect(invoke).toHaveBeenCalledWith('save_e2ee_key_bundle', {
      userId: 'usr_1',
      bundleJson: '{"keyId":"local"}',
    })
    await expect(loadE2eeKeyBundle('usr_1')).resolves.toBe('{"keyId":"native"}')
    await deleteE2eeKeyBundle('usr_1')
    expect(invoke).toHaveBeenCalledWith('delete_e2ee_key_bundle', { userId: 'usr_1' })
    expect(mergeE2eeBundleSources(null, '{"keyId":"native"}')).toEqual({
      bundle: '{"keyId":"native"}',
      persistNative: false,
      persistLocal: true,
    })
    expect(mergeE2eeBundleSources('{"keyId":"local"}', null)).toEqual({
      bundle: '{"keyId":"local"}',
      persistNative: true,
      persistLocal: false,
    })
    expect(mergeE2eeBundleSources('{"keyId":"local"}', '{"keyId":"native"}').bundle).toBe('{"keyId":"local"}')
  })
})
