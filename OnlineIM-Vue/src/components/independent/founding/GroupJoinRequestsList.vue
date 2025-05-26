<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useListStore } from '@/stores/list'
import type { GroupJoinRequestResponse } from '@/type/group'
import { groupService } from '@/services/group.service'

const listStore = useListStore()
const requests = ref<GroupJoinRequestResponse[]>([])

const handleRequest = async (request: GroupJoinRequestResponse, action: string) => {
  try {
    await groupService.handleJoinRequest(request.groupID, request.requestID, action)
    // 更新请求状态
    request.status = action === 'accept' ? 'accepted' : 'rejected'
  } catch (error) {
    console.error('处理加群请求失败:', error)
  }
}

onMounted(() => {
    requests.value = listStore.groupJoinRequestList
})
</script>

<template>
  <div class="request-list-container">
    <div v-if="requests.length === 0" class="empty-message">
      暂无加群请求
    </div>
    <div v-else>
      <div v-for="request in requests" :key="request.requestID" class="request-item">
        <img 
          :src="request.userInfo?.avatarUrl"
          class="avatar"
        >
        <div class="request-info">
          <span class="nickname">{{ request.userInfo.nickname }}</span>
          <span v-if="request.message" class="message">{{ request.message }}</span>
          <span class="group-name">申请加入: {{ request.groupName }}</span>
        </div>
        <div class="action-buttons">
          <template v-if="request.status === 'pending'">
            <button class="accept-btn" @click="handleRequest(request, 'accept')">同意</button>
            <button class="reject-btn" @click="handleRequest(request, 'reject')">拒绝</button>
          </template>
          <span v-else-if="request.status === 'accepted'" class="status-text p-2">已同意</span>
          <span v-else class="status-text p-2">已拒绝</span>
        </div>
      </div>
      <div class="no-more">没有更多好友请求了</div>
    </div>
  </div>
</template>

<style scoped>
.request-list-container {
  height: 100%;
  max-height: 80vh;
  overflow-y: auto;
  padding: 0.75rem;
}

.empty-message {
  text-align: center;
  padding: 1.25rem;
  color: hsl(0, 0%, 60%);
}

.request-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  border-bottom: 1px solid hsl(0, 0%, 90%);
}

.avatar {
  width: 2.5rem;
  height: 2.5rem;
  min-width: 2.5rem;
  border-radius: 50%;
  object-fit: cover;
}

.request-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.nickname {
  font-weight: bold;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.no-more {
  text-align: center;
  padding: 0.75rem;
  color: hsl(0, 0%, 60%);
}
.message,
.group-name {
  color: hsl(0, 0%, 40%);
  font-size: 0.875rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.loading {
  text-align: center;
  padding: 0.75rem;
  color: hsl(0, 0%, 60%);
}

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-left: auto;
}

.reject-btn {
  padding: 0.375em 0.75em;
  background: white;
  color: black;
  border: 1px solid hsl(0, 0%, 80%);
  border-radius: 0.375rem;
  cursor: pointer;
  min-width: 4rem;
}

.accept-btn {
  padding: 0.375em 0.75em;
  background: black;
  color: white;
  border: 1px solid black;
  border-radius: 0.375rem;
  cursor: pointer;
  min-width: 4rem;
}

@media (max-width: 640px) {
  .request-item {
    padding: 0.5rem;
  }
  
  .avatar {
    width: 2rem;
    height: 2rem;
    min-width: 2rem;
  }
  
  .action-buttons {
    flex-direction: row;
  }
}
</style>