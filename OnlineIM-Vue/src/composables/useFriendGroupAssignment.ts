import { computed, ref, type Ref } from 'vue'
import { toast } from 'vue-sonner'
import { friendsService } from '@/services/friends.service'
import { useListStore } from '@/stores/list'
import type { Friend } from '@/type/Friends'
import { friendMoveErrorText } from '@/utils/friend-group'

/**
 * 资料页里把好友移到另一个分组。选择器只在接口成功后改值，失败时保持原分组并提示原因。
 */
export function useFriendGroupAssignment(friendInfo: Ref<Friend | null>) {
  const listStore = useListStore()
  const selectKey = ref(0)
  const selectedGroupId = computed(() => friendInfo.value?.friend_info.friend_group_id || undefined)

  async function changeGroup(value: unknown) {
    const friend = friendInfo.value
    if (!friend) return
    const newGroupId = typeof value === 'string' ? value : ''
    const previous = friend.friend_info.friend_group_id
    if (!newGroupId || newGroupId === previous) return
    try {
      const response = await friendsService.setFriendGroup(friend.friendship_id, newGroupId)
      const applied = response?.friendship_group_id || newGroupId
      friend.friend_info.friend_group_id = applied
      await listStore.updateFriendGroup(friend.friendship_id, applied)
    } catch (error) {
      friend.friend_info.friend_group_id = previous
      // 下拉框可能已经停在新分组上，换 key 让它按原值重新渲染
      selectKey.value += 1
      console.error('修改分组失败:', error)
      toast.error(friendMoveErrorText(error))
    }
  }

  return { selectedGroupId, selectKey, changeGroup }
}
