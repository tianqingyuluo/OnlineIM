<script setup lang="ts">
import { Ellipsis } from "lucide-vue-next"
import { ref, provide, computed, onMounted, nextTick, onBeforeUnmount } from "vue"
import { useRoute } from 'vue-router'
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import Tools from "@/components/MainPart/tools.vue";
import UserTextArea from "@/components/MainPart/UserTextArea.vue";
import { userService } from '@/services/user.service'
import type { User } from '@/type/User'
import { useListStore } from "@/stores/list";
import { MessageService } from "@/services/message.service.ts";
import { useUserStore } from '@/stores/user.ts';

const route = useRoute()
const currentUser = ref<User | null>(null)
const userStore = useUserStore()
const listStore = useListStore()
const userId = computed(() => {
  const id = route.params.id
  const conversationId = Array.isArray(id) ? id[0] : id
  const conversation = listStore.conversations.find(c => c.conversation_id === conversationId)
  return conversation?.target_info.id || ''
})

// 消息相关状态
const userMessages = ref<any[]>([])
const isLoading = ref(false)
const hasMore = ref(true)
const noMoreInfo = ref(false)

async function loadMessages() {
  if (isLoading.value || !hasMore.value) return

  isLoading.value = true
  try {
    const before_message_id = userMessages.value.length > 0
        ? userMessages.value[0].message_id
        : undefined

    const response = await MessageService.getMessageHistory(
        userId.value,
        before_message_id
    )
    const messages = response.messages

    if (messages.length > 0) {
      if (!before_message_id) {
        userMessages.value = messages
      } else {
        userMessages.value = [...messages, ...userMessages.value]
      }

      hasMore.value = response.has_more_before
      noMoreInfo.value = !response.has_more_before
    }
  } catch (error) {
    console.error('获取消息历史失败:', error)
  } finally {
    isLoading.value = false
  }
}

function handleScroll(e: Event) {
  const target = e.target as HTMLElement
  const scrollThreshold = 100

  if (target.scrollTop <= scrollThreshold && hasMore.value && !isLoading.value) {
    loadMessages()
  }
}

onMounted(async () => {
  try {
    currentUser.value = await userService.getUserById(userId.value)
    await loadMessages()

    const chatContainer = document.querySelector('.overflow-y-auto')
    if (chatContainer) {
      chatContainer.addEventListener('scroll', handleScroll)
    }

    const textarea = document.getElementById('message-2')
    if (textarea) {
      textarea.addEventListener('keydown', handleKeyDown)
    }
  } catch (error) {
    console.error('初始化失败:', error)
  }
})

onBeforeUnmount(() => {
  const chatContainer = document.querySelector('.overflow-y-auto')
  if (chatContainer) {
    chatContainer.removeEventListener('scroll', handleScroll)
  }

  const textarea = document.getElementById('message-2')
  if (textarea) {
    textarea.removeEventListener('keydown', handleKeyDown)
  }
})

// 日期处理函数
function parseChineseDate(dateStr: string) {
  if (!dateStr) return new Date();
  const match = dateStr.match(/(\d+)年(\d+)月(\d+)[，,](\d+)\.(\d+)/);
  if (match) {
    const [_, year, month, day, hour, minute] = match;
    return new Date(
        parseInt(year),
        parseInt(month) - 1,
        parseInt(day),
        parseInt(hour),
        parseInt(minute)
    )
  }
  return new Date(dateStr);
}

function shouldShowTime(index: number, list: any[]) {
  if (index === 0) return true;
  const prevTime = parseChineseDate(list[index - 1].created_at).getTime();
  const currentTime = parseChineseDate(list[index].created_at).getTime();
  return currentTime - prevTime > 5 * 60 * 1000;
}

const messageInputRef = ref<HTMLTextAreaElement | null>(null)
provide('messageInputRef', messageInputRef)

function handleEmojiSelect(emoji: string) {
  const textareaEl = document.getElementById('message-2') as HTMLTextAreaElement
  if (textareaEl) {
    const cursorPos = textareaEl.selectionStart || 0
    const currentValue = textareaEl.value || ''
    const newValue = currentValue.substring(0, cursorPos) + emoji + currentValue.substring(cursorPos)
    textareaEl.value = newValue
    textareaEl.selectionStart = textareaEl.selectionEnd = cursorPos + emoji.length
    textareaEl.focus()
  }
}

function handleKeyDown(e: KeyboardEvent) {
  const textareaEl = e.target as HTMLTextAreaElement
  if (e.key === 'Enter' && !e.ctrlKey && !e.shiftKey) {
    e.preventDefault()
    handleSendClick()
  } else if (e.key === 'Enter' && e.ctrlKey) {
    const cursorPos = textareaEl.selectionStart || 0
    textareaEl.value =
        textareaEl.value.substring(0, cursorPos) +
        '\n' +
        textareaEl.value.substring(cursorPos)
    textareaEl.selectionStart = textareaEl.selectionEnd = cursorPos + 1
  }
}

async function handleSendClick() {
  const textareaEl = document.getElementById('message-2') as HTMLTextAreaElement
  if (textareaEl?.value.trim()) {
    try {
      const response = await MessageService.putMessage(
          userId.value,
          0,
          textareaEl.value
      )
      userMessages.value.push(response)
      textareaEl.value = ''
      nextTick(() => {
        const chatContainer = document.querySelector('.overflow-y-auto')
        chatContainer?.scrollTo(0, chatContainer.scrollHeight)
      })
    } catch (error) {
      console.error('发送消息失败:', error)
    }
  }
}

const items = [
  { title: "更多", url: "#", icon: Ellipsis }
]
</script>

<template>
  <div v-if="currentUser" class="flex flex-col h-full">
    <!-- 顶栏 -->
    <div class="flex items-center justify-between w-full p-4 border-b">
      <span class="text-lg font-semibold">{{ currentUser.username }}</span>
      <div class="flex space-x-4">
        <button v-for="item in items" :key="item.title">
          <a :href="item.url" class="flex items-center">
            <component :is="item.icon" class="w-5 h-5" />
          </a>
        </button>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="flex-1 overflow-y-auto p-4" @scroll="handleScroll">
      <div v-if="noMoreInfo" class="flex justify-center py-2 text-sm text-gray-500">
        没有更多信息
      </div>
      <div v-if="userMessages.length > 0" class="space-y-4">
        <template v-for="(msg, index) in userMessages" :key="msg.message_id">
          <!-- 时间显示 -->
          <div v-if="shouldShowTime(index, userMessages)" class="flex justify-center">
            <span class="text-[13px] text-gray-500 truncate">
              {{ new Date(msg.created_at).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') }}
            </span>
          </div>

          <!-- 消息内容 -->
          <div :class="['flex', msg.sender_id === userStore.loggedInUser?.user_id ? 'justify-end' : 'justify-start']">
            <template v-if="msg.sender_id !== userStore.loggedInUser?.user_id">
              <div class="flex items-start max-w-[80%]">
                <img :src="currentUser.avatar_url" :alt="currentUser.username" class="w-10 h-10 rounded-full mr-2" />
                <UserTextArea
                    :message="msg.content"
                    :isSelf="false"
                />
              </div>
            </template>

            <template v-else>
              <div class="flex items-start">
                <UserTextArea
                    :message="msg.content"
                    :isSelf="true"
                />
                <img :src="userStore.loggedInUser?.avatar_url" :alt="userStore.loggedInUser?.username"
                     class="w-10 h-10 rounded-full ml-2 object-cover" />
              </div>
            </template>
          </div>
        </template>
      </div>
      <div v-else class="flex items-center justify-center h-full text-gray-500">
        暂无消息记录
      </div>
    </div>

    <Tools @select="handleEmojiSelect" />
    <!-- 输入区域 -->
    <div class="relative h-1/4 border-t">
      <Textarea
          id="message-2"
          ref="messageInputRef"
          class="h-full w-full resize-none pr-20 rounded-none focus:ring-0 focus:shadow-none"
          style="outline: none;box-shadow: none; font-size: 24px"
      />
      <Button
          class="absolute bottom-4 right-4 transition-all duration-200 active:scale-95 hover:bg-primary/90 hover:scale-125"
          @click="handleSendClick"
      >
        发送
      </Button>
    </div>
  </div>
  <div v-else class="flex items-center justify-center h-full">
    加载中...
  </div>
</template>

<style scoped>
/* 保留原有样式 */
</style>