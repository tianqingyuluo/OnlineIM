import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/services/presence.service', () => ({
  presenceService: {
    query: vi.fn(),
    getSettings: vi.fn(),
    updateSettings: vi.fn(),
  },
}))

vi.mock('@/stores/list', () => ({
  useListStore: () => ({
    friends: [{ friend_info: { user_id: 'usr_friend' } }],
    userGroups: [{ friends: [{ user_id: 'usr_group' }, { user_id: 'usr_friend' }] }],
  }),
}))

describe('presence store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('queries in batches of 100 and stores each record', async () => {
    const { presenceService } = await import('@/services/presence.service')
    vi.mocked(presenceService.query).mockImplementation(async ids => ({
      presences: ids.map(id => ({ user_id: id, status: 'offline' as const, visible: true, last_seen_at: null })),
    }))
    const { usePresenceStore } = await import('@/stores/presence')
    const store = usePresenceStore()
    const ids = Array.from({ length: 101 }, (_, index) => `usr_${index}`)

    await store.refresh(ids)

    expect(presenceService.query).toHaveBeenCalledTimes(2)
    expect(vi.mocked(presenceService.query).mock.calls[0][0]).toHaveLength(100)
    expect(vi.mocked(presenceService.query).mock.calls[1][0]).toEqual(['usr_100'])
    expect(store.byUserId.usr_100.status).toBe('offline')
  })

  it('applies a realtime update and saves the privacy switch', async () => {
    const { presenceService } = await import('@/services/presence.service')
    vi.mocked(presenceService.updateSettings).mockResolvedValue({ hide_online_status: true })
    const { usePresenceStore } = await import('@/stores/presence')
    const store = usePresenceStore()

    store.applyUpdate({ user_id: 'usr_a', status: 'online', last_seen_at: null, server_time: 1 })
    expect(store.byUserId.usr_a).toEqual({
      user_id: 'usr_a',
      status: 'online',
      last_seen_at: null,
      visible: true,
    })
    store.applyUpdate({ user_id: '', status: 'online' })
    expect(store.byUserId['']).toBeUndefined()

    await store.setHidden(true)
    expect(store.hideOnlineStatus).toBe(true)
  })

  it('loads friend ids once when refreshing contacts', async () => {
    const { presenceService } = await import('@/services/presence.service')
    vi.mocked(presenceService.query).mockResolvedValue({ presences: [] })
    const { refreshContactPresence } = await import('@/stores/presence')

    await refreshContactPresence()

    expect(vi.mocked(presenceService.query).mock.calls[0][0].sort()).toEqual(['usr_friend', 'usr_group'])
  })
})
