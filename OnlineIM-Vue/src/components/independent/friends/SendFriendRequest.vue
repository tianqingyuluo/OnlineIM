<script setup lang="ts">
import { ref } from 'vue'
import { friendsService } from '@/services/friends.service'

const props = defineProps({
  user: {
    type: Object,
    required: true,
    default: () => ({
      user_id: '',
      username: '',
      nickname: '',
      avatar_url: ''
    })
  }
})

const emit = defineEmits(['close', 'success'])

const message = ref('')
const loading = ref(false)

const sendRequest = async () => {
  try {
    loading.value = true
    await friendsService.sendFriendRequest({
      receiver_id: props.user.user_id,
      message: message.value
    })
    emit('success')
    emit('close')
  } catch (error) {
    console.error('发送好友请求失败:', error)
  } finally {
    loading.value = false
  }
}
</script>
<template>
  <div class="flex flex-col bg-white p-6 rounded-lg shadow-sm max-w-lg mx-auto border-[2px] shadow-2xl">
    <!-- 用户信息区域 -->
    <div class="flex items-center mb-6">
      <img 
        :src="user.avatar_url || '/images/default-avatar.png'"
        class="w-16 h-16 rounded-full mr-4"
        :alt="user.nickname || user.username"
      >
      <div>
        <h3 class="text-lg font-medium text-gray-800">
          {{ user.nickname || user.username }}
        </h3>
        <p class="text-sm text-gray-500">
          @{{ user.username }}
        </p>
      </div>
    </div>

    <!-- 消息输入区域 -->
    <div class="mb-4">
      <label class="block text-sm font-medium text-gray-700 mb-1">验证消息</label>
      <textarea
        v-model="message"
        rows="3"
        placeholder="请输入验证消息（可选）"
        class="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-1 focus:ring-gray-500"
      ></textarea>
    </div>

    <!-- 操作按钮 -->
    <div class="flex space-x-3">
      <button
        @click="emit('close')"
        class="flex-1 py-2 px-4 bg-gray-100 text-gray-800 rounded-md hover:bg-gray-200 transition-colors"
      >
        取消
      </button>
      <button
        @click="sendRequest"
        :disabled="loading"
        class="flex-1 py-2 px-4 bg-gray-800 text-white rounded-md hover:bg-gray-700 transition-colors"
      >
        <span v-if="loading">发送中...</span>
        <span v-else>发送请求</span>
      </button>
    </div>
  </div>
</template>



<style scoped>
/* 可根据需要添加自定义样式 */
</style>