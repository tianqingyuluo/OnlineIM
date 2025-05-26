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

import { useUserStore } from '@/stores/user.ts';
import { onClickOutside } from '@vueuse/core'
import FriendSettingsCard from "@/components/independent/friends/FriendSettingsCard.vue";
import { useHistoryStore } from '@/stores/history.ts'; // 引入 historyStore
import { conversationService } from "@/services/conversation.service";
import { websocketService } from '@/services/websocket.service'; // 导入 websocketService
import { CircleEllipsis, AlertCircle } from 'lucide-vue-next';
import type {MessageResponse} from "@/type/message.ts"; // 导入加载图标和错误图标
// import type { MessageResponse } from '@/type/message'; // 导入 MessageResponse 类型 - 不再需要在这里处理消息类型

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
  let conversationId = Array.isArray(id) ? id[0] : id
  let conversation = listStore.conversations.find(c => c.conversation_id === conversationId)
  return conversation?.target_info.id || ''
})

// 消息相关状态 - 改为使用 historyStore
const historyStore = useHistoryStore();


function handleScroll(e: Event) {
  const target = e.target as HTMLElement
  const scrollThreshold = 100 // 设置滚动阈值

  // 当滚动到接近顶部(阈值范围内)且还有更多消息可加载时
  // 改为使用 historyStore 的状态和方法
  if (target.scrollTop <= scrollThreshold && historyStore.hasMore && !historyStore.isLoading) {
    console.log("试图拉取私聊数据", userId.value)
    historyStore.loadMessages(userId.value, false); // 调用 historyStore 的 loadMessages，isGroup 为 false
  }
}


onMounted(async () => {
  try {
    currentUser.value = await userService.getUserById(userId.value)
    // 改为调用 historyStore 的初始化和加载方法
    await historyStore.init(); // 初始化 store 状态
    await historyStore.loadInitialHistory(userStore.loggedInUser.user_id, userId.value, false); // 加载初始历史，isGroup 为 false

    const chatContainer = document.querySelector('.overflow-y-auto')
    if (chatContainer) {
      chatContainer.addEventListener('scroll', handleScroll)
      // 初始加载后滚动到底部
      await nextTick(() => {
        chatContainer.scrollTop = chatContainer.scrollHeight;
      });
    }

    const textarea = document.getElementById('message-2')
    if (textarea) {
      textarea.addEventListener('keydown', handleKeyDown)
    }



  } catch (error) {
    console.error('初始化失败:', error)
  } finally {
    // 确保在加载完成后滚动到底部，无论是否出错
    await nextTick(() => {
      const chatContainer = document.querySelector('.overflow-y-auto')
      if (chatContainer) {
        chatContainer.scrollTop = chatContainer.scrollHeight;
      }
    });
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
const conversation = computed(() => {
  const conversationId = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id;
  return conversationService.getConversationById(conversationId);
});



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
  const textareaEl = document.getElementById('message-2') as HTMLTextAreaElement;
  const messageContent = textareaEl?.value.trim();

  if (messageContent) {
    try {
      const conversationId = (await conversation.value).conversation_id || '';
      const receiverId = userId.value;
      const messageType = 'text';

      // 调用 historyStore 的 sendMessage 方法
      historyStore.sendMessage(conversationId, receiverId, messageType, messageContent,false);

      // 清空输入框
      textareaEl.value = '';

      // 滚动到底部
      await nextTick(() => {
        const chatContainer = document.querySelector('.overflow-y-auto');
        chatContainer?.scrollTo(0, chatContainer.scrollHeight);
      });

    } catch (error) {
      console.error('发送消息失败:', error);
    }
  }
}


async function handleResendMessage(message: MessageResponse) {
  try {
    // 更新消息时间戳
    const oldTimestamp = message.timestamp;
    message.timestamp = new Date().toISOString();
    console.log('重发消息 - 时间戳变化:', { old: oldTimestamp, new: message.timestamp });
    
    // 更新 historyStore 中的时间戳
    await historyStore.updateMessageTimestamp(message.client_message_id);
    
    // 直接通过WebSocket重新发送消息
    const websocketMessage = {
      conversation_id: message.conversation_id,
      receiver_id: userId.value,
      message_type: 'text',
      content: message.content,
      client_message_id: message.client_message_id
    };
    
    // 发送WebSocket消息
    websocketService.sendMessage({ type: 'PRIVATE_MESSAGE_REQUEST', message: websocketMessage });
    
    // 触发视图更新
    historyStore.chatMessages = [...historyStore.chatMessages];
  } catch (error) {
    console.error('重新发送消息失败:', error);
  }
}
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
          <FriendSettingsCard :conversation=conversation :userId=userId />
        </div>
      </Transition>
    </div>

    <!-- 主内容区 -->
    <div class="flex-1 overflow-y-auto p-4" @scroll="handleScroll"> <!-- 添加滚动事件监听 -->
      <!-- 改为使用 historyStore 的状态 -->
      <div v-if="historyStore.noMoreInfo" class="flex justify-center py-2 text-sm text-gray-500"> <!-- 添加没有更多信息提示 -->
        没有更多信息
      </div>
      <!-- 改为从 historyStore.chatMessages 获取消息 -->
      <div v-if="historyStore.chatMessages.length > 0" class="space-y-4">
        <template v-for="(msg, index) in historyStore.chatMessages" :key="msg.message_id">
          <!-- 时间显示 -->

          <div class="flex justify-center">
            <span class="text-[13px] text-gray-500 truncate">
              {{ msg.timestamp }}
            </span>
          </div>


          <!-- 消息内容 -->
          <div :class="['flex', msg.sender_info.user_id === userStore.loggedInUser?.user_id ? 'justify-end' : 'justify-start']">
            <template v-if="msg.sender_info.user_id !== userStore.loggedInUser?.user_id">
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
                <div class="relative">
                  <CircleEllipsis
                      v-if="historyStore.isMessagePending(msg.client_message_id) &&!historyStore.isMessageTimeout(msg.client_message_id)"
                      class="w-5 h-5 mr-2 text-gray-400 loading-spinner"
                  />
                  <AlertCircle
                      v-if="historyStore.isMessagePending(msg.client_message_id) && historyStore.isMessageTimeout(msg.client_message_id)"
                      class="w-5 h-5 mr-2 text-red-500 cursor-pointer"
                      @click="handleResendMessage(msg)"
                  />
                </div>
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
      <!-- 改为使用 historyStore 的状态 -->
      <div v-if="historyStore.isLoading" class="flex justify-center py-2 text-sm text-gray-500"> <!-- 添加加载中提示 -->
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
.slide-enter-active,
.slide-leave-active {
  transition: transform 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
}

/* 加载动画 - 3秒后停止 */
.loading-spinner {
  animation: spin 1s linear infinite;
  animation-duration: 3s;
  animation-iteration-count: 3;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
