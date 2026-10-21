import { describe, expect, it } from 'vitest'
import { buildDeviceLoginFields, detectDeviceType, deviceTypeLabel, getOrCreateDeviceId } from '@/utils/device-session'

function memoryStorage(initial: Record<string, string> = {}) {
  const data = { ...initial }
  return {
    data,
    getItem(key: string) {
      return data[key] ?? null
    },
    setItem(key: string, value: string) {
      data[key] = value
    },
  }
}

describe('device session identity', () => {
  it('treats a phone browser as phone and everything else as web unless overridden', () => {
    expect(detectDeviceType('mobile', null)).toBe('phone')
    expect(detectDeviceType('tablet', null)).toBe('phone')
    expect(detectDeviceType(undefined, null)).toBe('web')
    expect(detectDeviceType('mobile', 'desktop')).toBe('desktop')
  })

  it('keeps the same device id for the same device type', () => {
    const storage = memoryStorage()
    const first = getOrCreateDeviceId('web', storage, 'abc', 10)
    const second = getOrCreateDeviceId('web', storage, 'zzz', 99)
    const phone = getOrCreateDeviceId('phone', storage, 'p', 11)
    expect(second).toBe(first)
    expect(phone).not.toBe(first)
    expect(phone.startsWith('phone-')).toBe(true)
  })

  it('builds the login fields and labels', () => {
    const storage = memoryStorage({ 'onlineim.device_id.web': 'web-stable' })
    expect(buildDeviceLoginFields({ uaDeviceType: undefined, storage })).toEqual({
      device_id: 'web-stable',
      device_type: 'web',
    })
    expect(deviceTypeLabel('phone')).toBe('手机端')
    expect(deviceTypeLabel('desktop')).toBe('桌面端')
    expect(deviceTypeLabel('web')).toBe('网页端')
  })
})
