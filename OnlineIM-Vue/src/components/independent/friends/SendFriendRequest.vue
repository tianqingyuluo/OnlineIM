<script setup lang="ts">
import { ref } from 'vue'
import { friendsService } from '@/services/friends.service'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { z } from 'zod'

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

const { handleSubmit, errors, defineField } = useForm({
  validationSchema: toTypedSchema(
      z.object({
        message: z.string().max(50, '消息不能超过50字').optional() // 明确设置为可选
      })
  ),
  initialValues: {
    message: ''
  }
})

// 使用 defineField 进行双向绑定
const [messageValue, messageAttrs] = defineField('message')

const loading = ref(false)

const sendRequest = handleSubmit(async (values) => {
  try {
    loading.value = true
    console.log('提交数据:', values) // 调试输出

    await friendsService.sendFriendRequest({
      receiver_id: props.user.user_id,
      message: values.message || '' // 明确处理空值
    })

    emit('success')
    emit('close')
  } catch (error) {
    console.error('发送好友请求失败:', error)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="flex flex-col bg-white p-6 rounded-lg max-w-lg mx-auto border-[2px] shadow-2xl">
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
          @{{ user.username ||user.nickname }}
        </p>
      </div>
    </div>

    <!-- 消息输入区域 -->
    <div class="mb-4">
      <label class="block text-sm font-medium text-gray-700 mb-1">验证消息</label>
      <textarea
          rows="3"
          placeholder="请输入验证消息（可选）"
          class="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-1 focus:ring-gray-500"
          :class="{ 'border-red-500': errors.message }"
          v-model="messageValue"
      v-bind="messageAttrs"
      ></textarea>
      <p v-if="errors.message" class="mt-1 text-sm text-red-600">
        {{ errors.message }}
      </p>
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