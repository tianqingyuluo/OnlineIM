import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const db = vi.hoisted(() => ({
  bulkPut: vi.fn().mockResolvedValue(undefined),
}))

vi.mock('@/utils/indexedDB', () => ({
  dbService: {
    bulkPut: db.bulkPut,
    getAll: vi.fn(),
    addImageFromUrl: vi.fn(),
  },
  STORES: {
    CONVERSATIONS: 'conversations',
    FRIENDS: 'friends',
    GROUPS: 'groups',
    USER_GROUPS: 'user_groups',
    BLACKLIST: 'blacklist',
  },
}))

vi.mock('vue-sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }))

describe('rememberAcceptedFriend', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    db.bulkPut.mockClear()
  })

  it('puts a new friend into the default group so the list can show them', async () => {
    const { useListStore } = await import('@/stores/list')
    const store = useListStore()
    store.userGroups = [{
      group_id: 'grp_default',
      name: '我的好友',
      sort: 0,
      createdAt: '',
      friends: [],
    }]

    await store.rememberAcceptedFriend({
      friendship_id: 'rel_new',
      created_at: '2026-10-21T00:00:00',
      friend_info: {
        user_id: 'usr_new',
        username: 'new',
        nickname: '新朋友',
        avatar_url: '/a.png',
      },
    }, 'grp_default')

    expect(store.friends.map(friend => friend.friend_info.user_id)).toEqual(['usr_new'])
    expect(store.friends[0].friend_info.friend_group_id).toBe('grp_default')
    expect(store.userGroups[0].friends.map(friend => friend.user_id)).toEqual(['usr_new'])
    expect(db.bulkPut).toHaveBeenCalled()
  })
})
