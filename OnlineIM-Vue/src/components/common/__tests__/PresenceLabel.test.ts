// @vitest-environment jsdom

import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PresenceLabel from '@/components/common/PresenceLabel.vue'
import { usePresenceStore } from '@/stores/presence'

describe('PresenceLabel', () => {
  it('renders online and offline from the shared store', () => {
    setActivePinia(createPinia())
    const store = usePresenceStore()
    store.byUserId.usr_a = { user_id: 'usr_a', status: 'online', visible: true, last_seen_at: null }
    store.byUserId.usr_b = { user_id: 'usr_b', status: 'offline', visible: true, last_seen_at: null }

    const online = mount(PresenceLabel, { props: { userId: 'usr_a' } })
    const offline = mount(PresenceLabel, { props: { userId: 'usr_b' } })

    expect(online.text()).toContain('在线')
    expect(online.attributes('data-online')).toBe('true')
    expect(offline.text()).toContain('离线')
    expect(offline.attributes('data-online')).toBe('false')
    online.unmount()
    offline.unmount()
  })

  it('refreshes relative offline time while the page stays open', async () => {
    vi.useFakeTimers()
    const start = new Date(2026, 9, 21, 15, 0, 0).getTime()
    vi.setSystemTime(start)
    setActivePinia(createPinia())
    const store = usePresenceStore()
    store.byUserId.usr_b = {
      user_id: 'usr_b',
      status: 'offline',
      visible: true,
      last_seen_at: start - (5 * 60_000 - 1000),
    }
    const offline = mount(PresenceLabel, { props: { userId: 'usr_b' } })
    try {
      expect(offline.text()).toContain('4 分钟前')
      await vi.advanceTimersByTimeAsync(30_000)
      expect(offline.text()).toContain('5 分钟前')

      vi.setSystemTime(start + 90_000)
      document.dispatchEvent(new Event('visibilitychange'))
      await Promise.resolve()
      expect(offline.text()).toContain('6 分钟前')
    } finally {
      offline.unmount()
      vi.useRealTimers()
    }
  })
})