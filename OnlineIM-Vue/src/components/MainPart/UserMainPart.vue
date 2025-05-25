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
import { onClickOutside } from '@vueuse/core'
import FriendSettingsCard from "@/components/independent/friends/FriendSettingsCard.vue";

const menuRef = ref<HTMLElement | null>(null)
const menuButtonRef = ref<HTMLElement | null>(null)

const setupClickOutside = () => {
  onClickOutside(
    menuRef,
    () => {
      showMenu.value = false
    },
    {
      ignore: [
        menuButtonRef
      ]
    }
  )
}

onMounted(() => {
  setupClickOutside()
})

const route = useRoute()
const currentUser = ref<User | null>(null)
const userStore = useUserStore()
const listStore = useListStore()
const showMenu = ref(false)

function toggleMenu() {
  showMenu.value = !showMenu.value
}
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
const lastHistorySeqId = ref<string | null>(null); // 新增：用于记录最后一条历史消息的 seq_id
const inMyHistory = ref(false); // 新增：是否在我的历史记录中

async function loadMessages() {
  if (isLoading.value || !hasMore.value) return

  isLoading.value = true
  try {
    const before_message_id = userMessages.value.length > 0
        ? userMessages.value[0].seq_id
        : undefined

    const response = await MessageService.getMessageHistory(
        userId.value,
        before_message_id
    )
    const messages = response.messages
    const has_more_before = response.has_more_before; // 新增：从响应中获取是否有更多之前的消息

    if (messages.length > 0) {
      const sortedMessages = messages.sort((a, b) => a.seq_id - b.seq_id);
      if (!before_message_id) {
        userMessages.value = sortedMessages
      } else {
        // 将新加载的消息添加到列表的开头
        userMessages.value = [...sortedMessages, ...userMessages.value]
      }

      hasMore.value = has_more_before; // 更新 hasMore 状态
      noMoreInfo.value = !has_more_before; // 更新 noMoreInfo 状态
    }
  } catch (error) {
    console.error('获取消息历史失败:', error)
  } finally {
    isLoading.value = false
  }
}

function handleScroll(e: Event) {
  const target = e.target as HTMLElement
  const scrollThreshold = 100 // 设置滚动阈值

  // 当滚动到接近顶部(阈值范围内)且还有更多消息可加载时
  if (target.scrollTop <= scrollThreshold && hasMore.value && !isLoading.value) {
    loadMessages()
  }
}

onMounted(async () => {
  try {
    currentUser.value = await userService.getUserById(userId.value)
    // 直接加载消息，不需要等待 currentUser
    await loadMessages()

    const chatContainer = document.querySelector('.overflow-y-auto')
    if (chatContainer) {
      chatContainer.addEventListener('scroll', handleScroll)
      // 初始加载后滚动到底部
      nextTick(() => {
        chatContainer.scrollTop = chatContainer.scrollHeight;
      });
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

// 日期处理函数 (从 GroupMainPart.vue 复制)
function parseChineseDate(dateStr: string) {
  if (!dateStr) return new Date();

  // 处理 "2001年1月1，0.00" 这种格式
  const match = dateStr.match(/(\d+)年(\d+)月(\d+)[，,](\d+)\.(\d+)/);
  if (match) {
    const [_, year, month, day, hour, minute] = match;
    return new Date(
        parseInt(year),
        parseInt(month) - 1,
        parseInt(day),
        parseInt(hour),
        parseInt(minute)
    );
  }

  // 尝试解析ISO格式或其它格式
  const date = new Date(dateStr);
  return isNaN(date.getTime()) ? new Date() : date;
}

  // 判断是否需要显示时间（5分钟间隔） (从 GroupMainPart.vue 复制)
function shouldShowTime(index: number, list: any[]) {
  if (index === 0) return true;
  // 注意：这里需要根据实际消息对象的字段调整，GroupMainPart.vue 使用的是 sendTime
  // 假设 UserMainPart.vue 的消息对象时间字段是 created_at
  const prevTime = parseChineseDate(list[index - 1].created_at).getTime();
  const currentTime = parseChineseDate(list[index].created_at).getTime();
  return currentTime - prevTime > 5 * 60 * 1000;
}

const messageInputRef = ref<HTMLTextAreaElement | null>(null)
provide('messageInputRef', messageInputRef)

function handleEmojiSelect(emoji: string) {
  const textareaEl = document.getElementById('message-2') as HTMLTextAreaElement
  if (textareaEl) {
    // 获取当前光标位置
    const cursorPos = textareaEl.selectionStart || 0
    const currentValue = textareaEl.value || ''

    // 在光标位置插入表情
    const newValue =
      currentValue.substring(0, cursorPos) +
      emoji +
      currentValue.substring(cursorPos)

    // 更新文本框值
    textareaEl.value = newValue

    // 设置新的光标位置
    const newCursorPos = cursorPos + emoji.length
    textareaEl.selectionStart = newCursorPos
    textareaEl.selectionEnd = newCursorPos

    // 保持焦点
    textareaEl.focus()
  }
}

function handleKeyDown(e: KeyboardEvent) {
  const textareaEl = e.target as HTMLTextAreaElement
  if (e.key === 'Enter' && !e.ctrlKey && !e.shiftKey) {
    e.preventDefault()
    handleSendClick()
  } else if (e.key === 'Enter' && e.ctrlKey) {
    // Ctrl+Enter换行
    const cursorPos = textareaEl.selectionStart || 0
    const currentValue = textareaEl.value || ''
    textareaEl.value =
      currentValue.substring(0, cursorPos) +
      '\n' +
      currentValue.substring(cursorPos)
    textareaEl.selectionStart = cursorPos + 1
    textareaEl.selectionEnd = cursorPos + 1
  }
}

async function handleSendClick() {
  const textareaEl = document.getElementById('message-2') as HTMLTextAreaElement
  if (textareaEl?.value.trim()) {
    try {
      const response = await MessageService.putMessage(
          userId.value,
          0, // 私聊消息类型可能需要确认
          textareaEl.value
      )
      // 将新消息添加到列表末尾
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
    <div class="flex items-center justify-between w-full p-4 border-b relative">
      <span class="text-lg font-semibold">{{ currentUser.username }}</span>
      <button @click="toggleMenu" ref="menuButtonRef">
        <a href="#" class="flex items-center">
          <Ellipsis class="w-5 h-5" />
        </a>
      </button>

      <!-- 滑动菜单 -->
      <Transition name="slide">
        <div
          v-if="showMenu"
          ref="menuRef"
          class="absolute right-0 top-full w-80 bg-white shadow-lg z-50 h-[calc(100vh-60px)]"
        >
          <FriendSettingsCard />
        </div>
      </Transition>
    </div>

    <!-- 主内容区 -->
    <div class="flex-1 overflow-y-auto p-4" @scroll="handleScroll"> <!-- 添加滚动事件监听 -->
      <div v-if="noMoreInfo" class="flex justify-center py-2 text-sm text-gray-500"> <!-- 添加没有更多信息提示 -->
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
      <div v-if="isLoading" class="flex justify-center py-2 text-sm text-gray-500"> <!-- 添加加载中提示 -->
        加载中...
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
/* 滑动动画 */
.slide-enter-active,
.slide-leave-active {
  transition: transform 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
}
</style>
