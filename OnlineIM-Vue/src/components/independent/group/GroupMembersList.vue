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

const props = defineProps<{
  groupId: string
}>()

const contextMenuRef = ref<InstanceType<typeof ContextMenu> | null>(null)

defineExpose({ contextMenuRef })

const members = ref<GroupMember[]>([])
const offset = ref(0)
const loading = ref(false)
const hasMore = ref(true)

function handleKick(member: GroupMember) {
  console.log('踢出群聊:', member.user_id)
}

function handleSetAdmin(member: GroupMember) {
  console.log('设为管理员:', member.user_id)
}

function handleViewProfile(member: GroupMember) {
  console.log('查看资料:', member.user_id)
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
            <ContextMenuItem @click="handleKick(member)">
              踢出群聊
            </ContextMenuItem>
            <ContextMenuItem @click="handleSetAdmin(member)">
              设为管理员
            </ContextMenuItem>
            <ContextMenuItem @click="handleViewProfile(member)">
              查看资料
            </ContextMenuItem>
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