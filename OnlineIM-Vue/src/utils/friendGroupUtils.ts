import type { Friend } from '@/type/Friends.ts'
import type { UserGroupInfo } from '@/type/userGroup.ts'

export interface GroupedFriends {
  group: UserGroupInfo
  friends: Friend[]
}

export async function groupAndSortFriends(
  friends: Friend[],
  groups: UserGroupInfo[],
  storeToGlobal: boolean = true
): Promise<GroupedFriends[]> {
  const sortedGroups = [...groups].sort((a, b) => a.sort - b.sort)
  
  // 创建分组映射
  const groupMap = new Map<string, GroupedFriends>()
  
  // 初始化所有分组
  sortedGroups.forEach(group => {
    groupMap.set(group.id, {
      group,
      friends: []
    })
  })
  
  // 将好友分配到对应分组
  friends.forEach(friend => {
    const groupId = friend.friend_info.friend_group_id
    if (groupId && groupMap.has(groupId)) {
      groupMap.get(groupId)?.friends.push(friend)
    }
  })
  
  // 转换为数组
  const result = Array.from(groupMap.values())
  
  // 如果需要存储到全局状态
  if (storeToGlobal) {
    const { useListStore } = await import('@/stores/list')
    const listStore = useListStore()
    listStore.groupedFriends = result
  }
  
  console.log('分组排序结果:', result)
  return result
}