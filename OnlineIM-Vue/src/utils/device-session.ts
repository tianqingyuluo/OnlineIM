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
  storage?: Pick<Storage, 'getItem' | 'setItem'>
  random?: string
  now?: number
} = {}): { device_id: string; device_type: DeviceType } {
  const deviceType = detectDeviceType(options.uaDeviceType, options.override)
  const storage = options.storage ?? localStorage
  return {
    device_id: getOrCreateDeviceId(deviceType, storage, options.random, options.now),
    device_type: deviceType,
  }
}
