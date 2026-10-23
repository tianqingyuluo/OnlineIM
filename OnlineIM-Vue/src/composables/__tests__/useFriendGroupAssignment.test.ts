import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import type { Friend } from '@/type/Friends'

const setFriendGroup = vi.fn()
const updateFriendGroup = vi.fn()
const toastError = vi.fn()

vi.mock('@/services/friends.service', () => ({
  friendsService: { setFriendGroup },
}))
vi.mock('@/stores/list', () => ({
  useListStore: () => ({ updateFriendGroup }),
}))
vi.mock('vue-sonner', () => ({ toast: { error: toastError } }))

function friend(groupId = 'fgrp_old'): Friend {
  return {
    friendship_id: 'rel_1',
    created_at: '2026-01-01',
    friend_info: {
      user_id: 'usr_2',
      username: 'bob',
      nickname: '鲍勃',
      friend_group_id: groupId,
    },
  }
}

describe('useFriendGroupAssignment', () => {
  it('shows the server message and keeps the previous group when the move fails', async () => {
    setFriendGroup.mockRejectedValue({ message: '好友分组不存在', status: 404 })
    updateFriendGroup.mockClear()
    toastError.mockClear()
    const { useFriendGroupAssignment } = await import('@/composables/useFriendGroupAssignment')
    const info = ref<Friend | null>(friend())
    const { selectedGroupId, changeGroup } = useFriendGroupAssignment(info)

    await changeGroup('fgrp_new')

    expect(setFriendGroup).toHaveBeenCalledWith('rel_1', 'fgrp_new')
    expect(info.value?.friend_info.friend_group_id).toBe('fgrp_old')
    expect(selectedGroupId.value).toBe('fgrp_old')
    expect(updateFriendGroup).not.toHaveBeenCalled()
    expect(toastError).toHaveBeenCalledWith('好友分组不存在')
  })

  it('moves the friend after the server accepts', async () => {
    setFriendGroup.mockResolvedValue({ friendship_id: 'rel_1', friendship_group_id: 'fgrp_new' })
    updateFriendGroup.mockResolvedValue(undefined)
    toastError.mockClear()
    const { useFriendGroupAssignment } = await import('@/composables/useFriendGroupAssignment')
    const info = ref<Friend | null>(friend())
    const { changeGroup } = useFriendGroupAssignment(info)

    await changeGroup('fgrp_new')

    expect(info.value?.friend_info.friend_group_id).toBe('fgrp_new')
    expect(updateFriendGroup).toHaveBeenCalledWith('rel_1', 'fgrp_new')
    expect(toastError).not.toHaveBeenCalled()
  })
})
