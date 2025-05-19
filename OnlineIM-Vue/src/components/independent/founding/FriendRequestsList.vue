<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { friendsService } from '@/services/friends.service'
import type { FriendRequest } from '@/type/Friends'

const requests = ref<FriendRequest[]>([])
const offset = ref(0)
const loading = ref(false)
const hasMore = ref(true)

async function loadRequests() {
  if (loading.value || !hasMore.value) return
  
  loading.value = true
  try {
    const response = await friendsService.getReceivedFriendRequests({
      offset: offset.value
    })
    requests.value = [...requests.value, ...response.requests]
    hasMore.value = response.total == 8
    offset.value += 8
  } catch (error) {
    console.error('加载好友请求失败:', error)
  } finally {
    loading.value = false
  }
}

function handleScroll() {
  const element = document.querySelector('.request-list-container')
  if (element && element.scrollHeight - element.scrollTop <= element.clientHeight + 100) {
    loadRequests()
  }
}

onMounted(() => {
  loadRequests()
  const container = document.querySelector('.request-list-container')
  if (container) {
    container.addEventListener('scroll', handleScroll)
  }
})

onUnmounted(() => {
  const container = document.querySelector('.request-list-container')
  if (container) {
    container.removeEventListener('scroll', handleScroll)
  }
})
</script>

<template>
  <div class="request-list-container">
    <div v-if="requests.length === 0 && !loading" class="empty-message">
      暂无好友请求
    </div>
    <div v-else>
      <div v-for="request in requests" :key="request.request_id" class="request-item">
        <img 
          :src="request.sender_info?.avatar_url"
          class="avatar"
        >
        <div class="request-info">
          <span class="nickname">{{ request.sender_info.nickname }}</span>
          <span v-if="request.message" class="message">{{ request.message }}</span>
        </div>
        <div class="action-buttons">
          <template v-if="request.status === 'pending'">
            <button class="accept-btn">同意</button>
            <button class="reject-btn">拒绝</button>

          </template>
          <span v-else-if="request.status === 'accepted'" class="status-text">已同意</span>
          <span v-else class="status-text">已拒绝</span>
        </div>
      </div>
      <div v-if="loading" class="loading">加载中...</div>
      <div v-if="!hasMore && requests.length > 0" class="no-more">没有更多请求了</div>
    </div>
  </div>
</template>

<style scoped>
.request-list-container {
  height: 95%;
  overflow-y: auto;
  padding: 10px;
}
.empty-message {
  text-align: center;
  padding: 20px;
  color: #999;
}
.request-item {
  display: flex;
  align-items: center;
  padding: 10px;
  border-bottom: 1px solid #eee;
}
.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  margin-right: 10px;
}
.request-info {
  display: flex;
  flex-direction: column;
  width: 70%;
}
.nickname {
  font-weight: bold;
}
.message {
  color: #666;
  font-size: 0.9em;
}
.loading, .no-more {
  text-align: center;
  padding: 10px;
  color: #999;
}
.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-left: auto;
}
.reject-btn {
  padding: 5px 10px;
  background: white;
  color: black;
  border: 1px solid #ccc;
  border-radius: 4px;
  cursor: pointer;
}
.accept-btn {
  padding: 5px 10px;
  background: black;
  color: white;
  border: 1px solid black;
  border-radius: 4px;
  cursor: pointer;
}
</style>