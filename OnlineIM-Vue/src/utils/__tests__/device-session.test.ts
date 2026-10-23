import { describe, expect, it } from 'vitest'
import { buildDeviceDisplayName, buildDeviceLoginFields, detectDeviceType, deviceDisplayName, deviceTypeLabel, getOrCreateDeviceId } from '@/utils/device-session'

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
      device_name: '网页端',
    })
    expect(deviceTypeLabel('phone')).toBe('手机端')
    expect(deviceTypeLabel('desktop')).toBe('桌面端')
    expect(deviceTypeLabel('web')).toBe('网页端')
  })

  it('names desktop and phone from the browser and OS, and prefers a client name', () => {
    const desktop = buildDeviceLoginFields({
      uaDeviceType: undefined,
      override: 'desktop',
      browser: 'Chrome',
      os: 'Windows',
      storage: memoryStorage(),
      random: 'desk',
      now: 1,
    })
    const phone = buildDeviceLoginFields({
      uaDeviceType: 'mobile',
      browser: 'Mobile Safari',
      os: 'iOS',
      storage: memoryStorage(),
      random: 'ph',
      now: 2,
    })
    expect(desktop.device_type).toBe('desktop')
    expect(desktop.device_name).toBe('桌面端 · Chrome / Windows')
    expect(phone.device_type).toBe('phone')
    expect(phone.device_name).toBe('手机端 · Mobile Safari / iOS')
    expect(buildDeviceDisplayName({
      deviceType: 'phone',
      browser: 'Chrome',
      os: 'Mac OS',
      clientName: '  我的手机  ',
    })).toBe('我的手机')
    expect(buildDeviceDisplayName({ deviceType: 'desktop', os: 'Mac OS' })).toBe('桌面端 · macOS')
  })

  it('does not show an opaque device id as the display name', () => {
    expect(deviceDisplayName({
      device_id: 'web-abc',
      device_name: 'web-abc',
      device_type: 'web',
    })).toBe('网页端')
    expect(deviceDisplayName({
      device_id: 'phone-1',
      device_name: '手机端 · Safari / iOS',
      device_type: 'phone',
    })).toBe('手机端 · Safari / iOS')
    expect(deviceDisplayName({ device_id: 'desktop-1', device_type: 'desktop' })).toBe('桌面端')
  })
})
