// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const toastError = vi.fn()
vi.mock('vue-sonner', () => ({ toast: { error: toastError } }))

const friendsApi = vi.hoisted(() => ({
  handleFriendRequest: vi.fn(),
  setFriendGroup: vi.fn(),
}))
vi.mock('@/services/friends.service', () => ({ friendsService: friendsApi }))

const refresh = vi.hoisted(() => vi.fn())
vi.mock('@/stores/presence', () => ({
  usePresenceStore: () => ({ refresh }),
}))

const listState = vi.hoisted(() => ({
  friends: [] as unknown[],
  userGroups: [{ group_id: 'grp_default', name: '我的好友', friends: [] as unknown[] }],
  FriendRequestsList: [{
    request_id: 'req_1',
    status: '0',
    created_at: '2026-10-21T00:00:00',
    message: '你好',
    sender_info: { user_id: 'usr_new', username: 'new', nickname: '新朋友', avatar_url: '/a.png' },
  }],
  rememberAcceptedFriend: vi.fn(),
}))
vi.mock('@/stores/list', () => ({ useListStore: () => listState }))

describe('FriendRequestsList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    friendsApi.handleFriendRequest.mockResolvedValue({ friend_id: 'rel_new' })
    friendsApi.setFriendGroup.mockResolvedValue({ friendship_id: 'rel_new', friendship_group_id: 'grp_default' })
    refresh.mockResolvedValue(undefined)
    listState.rememberAcceptedFriend.mockResolvedValue(undefined)
    listState.userGroups.splice(0, listState.userGroups.length, {
      group_id: 'grp_default',
      name: '我的好友',
      friends: [],
    })
    listState.FriendRequestsList[0].status = '0'
  })

  it('loads presence and adds the friend when a request is accepted', async () => {
    const { default: FriendRequestsList } = await import('@/components/independent/founding/FriendRequestsList.vue')
    const wrapper = mount(FriendRequestsList)

    await wrapper.get('.accept-btn').trigger('click')
    await flushPromises()

    expect(friendsApi.setFriendGroup).toHaveBeenCalledWith('rel_new', 'grp_default')
    expect(refresh).toHaveBeenCalledWith(['usr_new'])
    expect(listState.rememberAcceptedFriend).toHaveBeenCalledWith(
      expect.objectContaining({
        friendship_id: 'rel_new',
        created_at: '2026-10-21T00:00:00',
        friend_info: expect.objectContaining({
          user_id: 'usr_new',
          username: 'new',
          nickname: '新朋友',
          avatar_url: '/a.png',
        }),
      }),
      'grp_default',
    )
    expect(refresh.mock.invocationCallOrder[0]).toBeLessThan(listState.rememberAcceptedFriend.mock.invocationCallOrder[0])
    expect(listState.FriendRequestsList[0].status).toBe('1')
  })

  it('does not fetch presence when the request is rejected', async () => {
    friendsApi.handleFriendRequest.mockResolvedValue({})
    const { default: FriendRequestsList } = await import('@/components/independent/founding/FriendRequestsList.vue')
    const wrapper = mount(FriendRequestsList)

    await wrapper.get('.reject-btn').trigger('click')
    await flushPromises()

    expect(refresh).not.toHaveBeenCalled()
    expect(listState.rememberAcceptedFriend).not.toHaveBeenCalled()
    expect(friendsApi.setFriendGroup).not.toHaveBeenCalled()
    expect(listState.FriendRequestsList[0].status).toBe('2')
  })
})
