// @vitest-environment jsdom

import { describe, expect, it } from 'vitest'
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
  })
})