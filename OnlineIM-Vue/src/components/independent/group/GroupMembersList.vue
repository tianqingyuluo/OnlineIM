<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { groupService } from '@/services/group.service'
import type { GroupMember } from '@/type/group'
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger,
} from '@/components/ui/context-menu'
import { useUserStore } from '@/stores/user';
import { groupMembersService } from '@/services/groupmembers.service';

const props = defineProps<{
  groupId: string
  myRole: string
}>()

const contextMenuRef = ref<InstanceType<typeof ContextMenu> | null>(null)

defineExpose({ contextMenuRef })

const members = ref<GroupMember[]>([])
const offset = ref(0)
const loading = ref(false)
const hasMore = ref(true)
const userStore=useUserStore()
async function handleKick(member: GroupMember) {
  try {
    await groupMembersService.removeMember(props.groupId, member.user_id)
    members.value = members.value.filter(m => m.user_id !== member.user_id)
  } catch (error) {
    console.error('踢出群聊失败:', error)
  }
}

async function handleSetAdmin(member: GroupMember) {
  try {
    await groupMembersService.setAdmin(props.groupId, member.user_id)
    const index = members.value.findIndex(m => m.user_id === member.user_id)
    if (index !== -1) {
      members.value[index].role = 'admin'
    }
  } catch (error) {
    console.error('设为管理员失败:', error)
  }
}

async function handleRemoveAdmin(member: GroupMember) {
  try {
    await groupMembersService.removeAdmin(props.groupId, member.user_id)
    const index = members.value.findIndex(m => m.user_id === member.user_id)
    if (index !== -1) {
      members.value[index].role = 'member'
    }
  } catch (error) {
    console.error('取消管理员失败:', error)
  }
}

async function handleMute(member: GroupMember) {
  try {
    await groupMembersService.muteMember(props.groupId, member.user_id, 3600)
  } catch (error) {
    console.error('禁言失败:', error)
  }
}

async function handleUnmute(member: GroupMember) {
  try {
    await groupMembersService.unmuteMember(props.groupId, member.user_id)
    const index = members.value.findIndex(m => m.user_id === member.user_id)
    if (index !== -1) {
      members.value[index].role = 'member'
    }
  } catch (error) {
    console.error('取消禁言失败:', error)
  }
}

async function handleTransferOwner(member: GroupMember) {
  try {
    await groupMembersService.transferOwnership(props.groupId, member.user_id)
    const index = members.value.findIndex(m => m.user_id === member.user_id)
    if (index !== -1) {
      members.value[index].role = 'owner'
      const myIndex = members.value.findIndex(m => m.user_id === userStore.loggedInUser.user_id)
      if (myIndex !== -1) {
        members.value[myIndex].role = 'admin'
      }
    }
  } catch (error) {
    console.error('转让群主失败:', error)
  }
}

async function handleUpdateNickname(member: GroupMember) {
  const newNickname = prompt('请输入新的昵称', member.username)
  if (newNickname && newNickname !== member.username) {
    try {
      await groupMembersService.updateNickname(props.groupId, member.user_id, newNickname)
      const index = members.value.findIndex(m => m.user_id === member.user_id)
      if (index !== -1) {
        members.value[index].username = newNickname
      }
    } catch (error) {
      console.error('更新昵称失败:', error)
    }
  }
}


async function loadMembers() {
  if (loading.value || !hasMore.value) return

  loading.value = true
  try {
    const response = await groupService.getGroupMembers(props.groupId, {
      offset: offset.value
    })
    members.value = [...members.value, ...response.members]
    hasMore.value = response.members.length === 8
    offset.value += 8
  } catch (error) {
    console.error('加载群成员失败:', error)
  } finally {
    loading.value = false
  }
}

function handleScroll() {
  const element = document.querySelector('.members-list-container')
  if (element && element.scrollHeight - element.scrollTop <= element.clientHeight + 100) {
    loadMembers()
  }
}

onMounted(() => {
  loadMembers()
  const container = document.querySelector('.members-list-container')
  if (container) {
    container.addEventListener('scroll', handleScroll)
  }
})

onUnmounted(() => {
  const container = document.querySelector('.members-list-container')
  if (container) {
    container.removeEventListener('scroll', handleScroll)
  }
})
</script>

<template>
  <div class="members-list-container">
    <div class="list-header flex items-center">
      <button
          class="mr-2 p-1 rounded-full hover:bg-gray-100 transition-colors"
          @click="$emit('back')"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M15 18l-6-6 6-6"/>
        </svg>
      </button>
      <h3>群聊成员</h3>
    </div>
    <div v-if="members.length === 0 && !loading" class="empty-message">
      暂无群成员
    </div>
    <div v-else>
      <!-- 将上下文菜单移到循环内部 -->
      <div v-for="member in members" :key="member.user_id">
        <ContextMenu ref="contextMenuRef">
          <ContextMenuTrigger>
            <div class="member-item">
              <img
                  :src="member.avatar_url || '/images/default-avatar.png'"
                  class="avatar"
              >
              <div class="member-info">
                <span class="nickname">{{ member.username }}</span>
                <span class="role">{{ member.role }}</span>
              </div>
            </div>
          </ContextMenuTrigger>
          <ContextMenuContent>
            <template v-if="myRole === 'owner'">
              <ContextMenuItem @click="handleKick(member)">
                踢出群聊
              </ContextMenuItem>
              <ContextMenuItem @click="handleSetAdmin(member)" v-if="member.role !== 'admin'">
                设为管理员
              </ContextMenuItem>
              <ContextMenuItem @click="handleRemoveAdmin(member)" v-if="member.role === 'admin'">
                取消管理员
              </ContextMenuItem>
              <!-- <ContextMenuItem @click="handleMute(member)" v-if="member.role !== 'muted'">
                禁言
              </ContextMenuItem>
              <ContextMenuItem @click="handleUnmute(member)" v-if="member.role === 'muted'">
                取消禁言
              </ContextMenuItem> -->
              <ContextMenuItem @click="handleTransferOwner(member)" v-if="member.user_id !== userStore.loggedInUser.user_id">
                转让群主
              </ContextMenuItem>
              <ContextMenuItem @click="handleUpdateNickname(member)">
                更新昵称
              </ContextMenuItem>
            </template>
            <template v-else-if="myRole === 'admin'">
              <ContextMenuItem @click="handleKick(member)" v-if="member.role === 'member'">
                踢出群聊
              </ContextMenuItem>
              <!-- <ContextMenuItem @click="handleMute(member)" v-if="member.role !== 'muted'">
                禁言
              </ContextMenuItem>
              <ContextMenuItem @click="handleUnmute(member)" v-if="member.role === 'muted'">
                取消禁言
              </ContextMenuItem> -->
            </template>
          </ContextMenuContent>
        </ContextMenu>
      </div>
      <div v-if="loading" class="loading">加载中...</div>
      <div v-if="!hasMore && members.length > 0" class="no-more">没有更多成员了</div>
    </div>
  </div>
</template>

<style scoped>
.members-list-container {
  width: 100%;
  height: 100%;
  overflow-y: auto;
  padding: 10px;
  position: absolute;
  background: white;
}

.list-header {
  padding-bottom: 10px;
  border-bottom: 1px solid #eee;
  margin-bottom: 10px;
}

.list-header h3 {
  font-size: 16px;
  font-weight: 500;
}

.empty-message {
  text-align: center;
  padding: 20px;
  color: #999;
}

.member-item {
  display: flex;
  align-items: center;
  padding: 8px 0;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.member-item:hover {
  background-color: #f5f5f5;
  border-radius: 6px;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  margin-right: 10px;
}

.member-info {
  display: flex;
  flex-direction: column;
}

.nickname {
  font-weight: bold;
}

.role {
  color: #666;
  font-size: 0.8em;
}

.loading, .no-more {
  text-align: center;
  padding: 10px;
  color: #999;
}
</style>