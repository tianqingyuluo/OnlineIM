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
    requests.value = [
    {
    requestID: "req_001",
    groupID: "grp_001",
    groupName: "前端开发群",
    userInfo: {
      username: "user123",
      nickname: "开发者小张",
      avatarUrl: "/avatars/user123.jpg",
      userID: "usr_001"
    },
    inviterInfo: {
      username: "admin456",
      nickname: "群主老王",
      avatarUrl: "/avatars/admin456.jpg",
      userID: "usr_002"
    },
    message: "希望能加入贵群学习前端技术",
    status: "pending",
    createdAt: "2023-06-15T10:30:00Z"
  },
  {
    requestID: "req_002",
    groupID: "grp_002",
    groupName: "Vue技术交流",
    userInfo: {
      username: "vue_lover",
      nickname: "Vue爱好者",
      avatarUrl: "/avatars/vue_lover.jpg",
      userID: "usr_003"
    },
    inviterInfo: {
      username: "vue_master",
      nickname: "Vue专家",
      avatarUrl: "/avatars/vue_master.jpg",
      userID: "usr_004"
    },
    message: "申请加入Vue技术交流群",
    status: "accepted",
    createdAt: "2023-06-16T14:20:00Z"
  },
  {
    requestID: "req_003",
    groupID: "grp_003",
    groupName: "Node.js实战",
    userInfo: {
      username: "node_newbie",
      nickname: "Node新手",
      avatarUrl: "/avatars/node_newbie.jpg",
      userID: "usr_005"
    },
    inviterInfo: {
      username: "node_guru",
      nickname: "Node大神",
      avatarUrl: "/avatars/node_guru.jpg",
      userID: "usr_006"
    },
    message: "",
    status: "rejected",
    createdAt: "2023-06-17T09:15:00Z"
  }
    ]
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