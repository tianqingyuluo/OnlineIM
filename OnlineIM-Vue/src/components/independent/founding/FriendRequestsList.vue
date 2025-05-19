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
  height: 100%;          /* 填充父容器 */
  max-height: 80vh;      /* 限制最大高度 */
  overflow-y: auto;      /* 允许垂直滚动 */
  padding: 0.75rem;      /* 使用 rem 单位 */
}

.empty-message {
  text-align: center;
  padding: 1.25rem;
  color: hsl(0, 0%, 60%); /* 使用 hsl 颜色 */
}

.request-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;          /* 替代 margin-right */
  padding: 0.75rem;
  border-bottom: 1px solid hsl(0, 0%, 90%);
}

.avatar {
  width: 2.5rem;         /* 使用 rem 单位 */
  height: 2.5rem;
  min-width: 2.5rem;     /* 防止被压缩 */
  border-radius: 50%;
  object-fit: cover;     /* 确保图片比例正确 */
}

.request-info {
  flex: 1;               /* 自动填充剩余空间 */
  min-width: 0;          /* 防止文本溢出 */
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

.message {
  color: hsl(0, 0%, 40%);
  font-size: 0.875rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.loading,
.no-more {
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
  padding: 0.375em 0.75em; /* 使用 em 单位 */
  background: white;
  color: black;
  border: 1px solid hsl(0, 0%, 80%);
  border-radius: 0.375rem;
  cursor: pointer;
  min-width: 4rem;       /* 确保按钮最小宽度 */
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

/* 移动端适配 */
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
    flex-direction: row; /* 水平排列按钮 */
  }
}
</style>