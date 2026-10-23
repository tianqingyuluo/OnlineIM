export type DeviceType = 'web' | 'desktop' | 'phone'

const STORAGE_PREFIX = 'onlineim.device_id.'

export function normalizeDeviceType(value?: string | null): DeviceType | null {
  if (!value) return null
  switch (value.trim().toLowerCase()) {
    case 'web':
    case 'browser':
      return 'web'
    case 'desktop':
    case 'pc':
    case 'tauri':
      return 'desktop'
    case 'phone':
    case 'mobile':
    case 'android':
    case 'ios':
    case 'tablet':
      return 'phone'
    default:
      return null
  }
}

/** 浏览器按 UA 区分网页和手机；桌面壳可以通过 onlineim.device_type 指定 desktop。 */
export function detectDeviceType(uaDeviceType?: string | null, override?: string | null): DeviceType {
  return normalizeDeviceType(override) ?? (uaDeviceType === 'mobile' || uaDeviceType === 'tablet' || uaDeviceType === 'wearable'
    ? 'phone'
    : 'web')
}

export function deviceTypeLabel(deviceType?: string | null): string {
  switch (normalizeDeviceType(deviceType) ?? 'web') {
    case 'desktop':
      return '桌面端'
    case 'phone':
      return '手机端'
    default:
      return '网页端'
  }
}

const MAX_DEVICE_NAME = 64

/** 去掉控制字符和多余空白。空串返回 null。 */
export function sanitizeDeviceName(raw?: string | null): string | null {
  if (!raw) return null
  const cleaned = raw.replace(/[\u0000-\u001F\u007F]/g, '').replace(/\s+/g, ' ').trim()
  if (!cleaned) return null
  return cleaned.length > MAX_DEVICE_NAME ? cleaned.slice(0, MAX_DEVICE_NAME).trim() || null : cleaned
}

function osLabel(os?: string | null): string | null {
  const cleaned = sanitizeDeviceName(os)
  if (!cleaned) return null
  return cleaned === 'Mac OS' ? 'macOS' : cleaned
}

/**
 * 登录时上报的展示名。客户端指定的名称优先；否则用「桌面端 / 手机端 / 网页端」加上浏览器和系统。
 */
export function buildDeviceDisplayName(options: {
  deviceType?: string | null
  browser?: string | null
  os?: string | null
  clientName?: string | null
} = {}): string {
  const supplied = sanitizeDeviceName(options.clientName)
  if (supplied) return supplied
  const label = deviceTypeLabel(options.deviceType)
  const detail = [sanitizeDeviceName(options.browser), osLabel(options.os)].filter(Boolean).join(' / ')
  return detail ? `${label} · ${detail}` : label
}

/** 列表展示。没有可用名称、或名称就是设备标识时，退回类型标签。 */
export function deviceDisplayName(device: {
  device_id?: string | null
  device_name?: string | null
  device_type?: string | null
}): string {
  const supplied = sanitizeDeviceName(device.device_name)
  if (supplied && supplied !== device.device_id) return supplied
  return deviceTypeLabel(device.device_type)
}

export function deviceStorageKey(deviceType: DeviceType): string {
  return STORAGE_PREFIX + deviceType
}

export function getOrCreateDeviceId(
  deviceType: DeviceType,
  storage: Pick<Storage, 'getItem' | 'setItem'> = localStorage,
  random = Math.random().toString(36).slice(2, 10),
  now = Date.now(),
): string {
  const key = deviceStorageKey(deviceType)
  const existing = storage.getItem(key)
  if (existing) return existing
  const created = `${deviceType}-${random}-${now.toString(36)}`
  storage.setItem(key, created)
  return created
}

export function rememberDeviceId(
  deviceType: DeviceType,
  deviceId: string,
  storage: Pick<Storage, 'setItem'> = localStorage,
): void {
  if (!deviceId) return
  storage.setItem(deviceStorageKey(deviceType), deviceId)
}

export function buildDeviceLoginFields(options: {
  uaDeviceType?: string | null
  override?: string | null
  browser?: string | null
  os?: string | null
  /** 桌面壳等可以另给名称，例如 localStorage 的 onlineim.device_name */
  clientName?: string | null
  storage?: Pick<Storage, 'getItem' | 'setItem'>
  random?: string
  now?: number
} = {}): { device_id: string; device_type: DeviceType; device_name: string } {
  const deviceType = detectDeviceType(options.uaDeviceType, options.override)
  const storage = options.storage ?? localStorage
  const deviceId = getOrCreateDeviceId(deviceType, storage, options.random, options.now)
  const supplied = sanitizeDeviceName(options.clientName)
  return {
    device_id: deviceId,
    device_type: deviceType,
    device_name: buildDeviceDisplayName({
      deviceType,
      browser: options.browser,
      os: options.os,
      clientName: supplied === deviceId ? null : supplied,
    }),
  }
}
