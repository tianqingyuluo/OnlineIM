<script setup lang="ts">
import { Ellipsis } from "lucide-vue-next"
import { ref, provide, onMounted, computed, nextTick, onBeforeUnmount } from "vue"
import { useRoute } from 'vue-router'
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import Tools from "@/components/MainPart/tools.vue";
import UserTextArea from "@/components/MainPart/UserTextArea.vue";
import { groupService } from '@/services/group.service'
import type { GroupResponse } from '@/type/group'
import { onActivated } from 'vue'
import GroupInfoCard from '@/components/independent/group/GroupInfoCard.vue'
import { onClickOutside } from '@vueuse/core'
import { useUserStore } from '@/stores/user.ts';
import { GroupSettingService } from "@/services/groupsetting.service";
import GroupAvatarWithMenu from "@/components/independent/group/GroupAvatarWithMenu.vue";
import {useOtherStore} from "@/stores/otherStore.ts";
import {conversationService} from "@/services/conversation.service.ts";
import type {Conversation} from "@/type/Conversation.ts";
import { useHistoryStore } from '@/stores/history.ts';
import type {MessageResponse} from "@/type/message.ts";
import {websocketService} from "@/services/websocket.service.ts";
import { CircleEllipsis, AlertCircle } from 'lucide-vue-next';
const menuRef = ref<HTMLElement | null>(null)//右上角群info
const menuButtonRef = ref<HTMLElement | null>(null)

const setupClickOutside = () => {
  onClickOutside(
      menuRef,
      () => {
        const otherStore = useOtherStore()
        if (!otherStore.isContextMenuOpen) {
          showMenu.value = false
          console.log('菜单状态已设置为关闭')
        }
      },
      {
        ignore: [
        menuButtonRef,

        ]
      }
  )
}

onMounted(() => {
  setupClickOutside()
})

const route = useRoute()
const conversation =ref<Conversation | null>(null)
const currentGroup = ref<GroupResponse | null>(null)
const currentGroupSettings = ref<any>(null)
const userStore = useUserStore()
const currentUser = computed(() => userStore.loggedInUser)
const groupId = computed(() => {
  const id = route.params.id
  return Array.isArray(id) ? id[0] : id
})

const historyStore = useHistoryStore();

onMounted(async () => {
  try {
    console.log("当前群组ID:", groupId.value);
    const [groupInfo, groupSettings, Conversation] = await Promise.all([
      groupService.getGroupInfo(groupId.value),
      GroupSettingService.getGroupSetting(groupId.value),
      conversationService.getConversationById(groupId.value)
    ]);
    currentGroup.value = groupInfo;
    currentGroupSettings.value = groupSettings;
    conversation.value = Conversation;

    // 确保组件渲染完成
    await nextTick();

    // 绑定键盘事件
    const textarea = document.getElementById('message-2');
    if (textarea) {
      textarea.addEventListener('keydown', handleKeyDown);
      console.log('键盘事件绑定成功');
    }

    // 加载消息
    await historyStore.loadInitialHistory(userStore.loggedInUser.user_id, groupId.value, true);
  } catch (error) {
    console.error('初始化失败:', error);
  }
});

function handleScroll(e: Event) {
  const target = e.target as HTMLElement
  const scrollThreshold = 100 // 设置滚动阈值

  // 当滚动到接近顶部(阈值范围内)且还有更多消息可加载时
  if (target.scrollTop <= scrollThreshold && historyStore.hasMore && !historyStore.isLoading) {
    console.log("试图拉取数据", groupId.value)
    historyStore.loadMessages(groupId.value, true);
  }
}

onMounted(() => {
  historyStore.init()
  historyStore.loadInitialHistory(userStore.loggedInUser.user_id, groupId.value, true);
  const chatContainer = document.querySelector('.overflow-y-auto')
  if (chatContainer) {
    chatContainer.addEventListener('scroll', handleScroll)
  }

  // 添加键盘事件监听
  const textarea = document.getElementById('message-2')
  if (textarea) {
    textarea.addEventListener('keydown', handleKeyDown)
  }
})

onBeforeUnmount(() => {
  const chatContainer = document.querySelector('.overflow-y-auto')
  if (chatContainer) {
    chatContainer.removeEventListener('scroll', handleScroll)
  }

  // 移除键盘事件监听
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

function handleKeyDown(e: KeyboardEvent) {
  console.log('按键:', e.key); // 调试
  const textareaEl = e.target as HTMLTextAreaElement;
  if (e.key === 'Enter' && !e.ctrlKey && !e.shiftKey) {
    e.preventDefault();
    console.log('触发发送');
    handleSendClick();
  } else if (e.key === 'Enter' && e.ctrlKey) {
    // Ctrl+Enter 换行
    const cursorPos = textareaEl.selectionStart || 0;
    const currentValue = textareaEl.value || '';
    textareaEl.value = currentValue.substring(0, cursorPos) + '\n' + currentValue.substring(cursorPos);
    textareaEl.selectionStart = cursorPos + 1;
    textareaEl.selectionEnd = cursorPos + 1;
  }
}
const clearGroupMessages=async () =>  {
  console.log('聊天记录clearGroupMessages触发')
  try {
    historyStore.groupMessages = [];
  } catch (error) {
    console.error('清空聊天记录失败:', error);
  }
}

provide('clearGroupMessages', clearGroupMessages);

async function handleSendClick() {
  const textareaEl = document.getElementById('message-2') as HTMLTextAreaElement;
  const messageContent = textareaEl?.value.trim();

  if (messageContent) {
    try {
      if (conversation.value){
        const conversationId = conversation.value.conversation_id || '';
        const receiverId = groupId.value;
        const messageType = 'text';
        historyStore.sendMessage(conversationId, receiverId, messageType, messageContent,true);
      }
      
      

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

onActivated(async () => {
  console.log('组件被激活')
  // 可以在这里添加数据刷新逻辑
})
const showMenu = ref(false)

function toggleMenu() {
  showMenu.value = !showMenu.value
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
    const websocketMessage : any = {
      conversation_id: message.conversation_id,
      receiver_id: groupId.value,
      message_type: 'text',
      content: message.content,
      client_message_id: message.client_message_id
    };
    if (message.mentioned_user_ids) {
      websocketMessage.at_user=message.mentioned_user_ids;
    }
    
    // 发送WebSocket消息
    websocketService.sendMessage({ type: 'PRIVATE_MESSAGE_REQUEST', message: websocketMessage });
    
    // 触发视图更新
    historyStore.groupMessages = [...historyStore.groupMessages];
  } catch (error) {
    console.error('重新发送消息失败:', error);
  }
}
</script>

<template>
  <div v-if="currentGroup" class="flex flex-col h-full">
    <!-- 顶栏 -->
    <div class="flex items-center justify-between w-full p-4 border-b relative">
      <span class="text-lg font-semibold">{{ currentGroup.name }}</span>
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
          <GroupInfoCard
  :group="currentGroup" 
  :group-settings="currentGroupSettings" 
  :myRole="currentGroup.my_role"
  :conversation="conversation"
  class="h-full overflow-y-auto"
  @click.stop
/>
        </div>
      </Transition>
    </div>

    <!-- 主内容区 -->
    <div class="flex-1 overflow-y-auto p-4" @scroll="handleScroll">
      <div v-if="historyStore.noMoreInfo" class="flex justify-center py-2 text-sm text-gray-500">
        没有更多信息
      </div>
      <div v-if="historyStore.groupMessages.length > 0" class="space-y-4">
        <template v-for="(msg, index) in historyStore.groupMessages" :key="msg.message_id">
          <!-- 时间显示 -->
          <div class="flex justify-center">
            <span class="text-[13px] text-gray-500 truncate">
              {{ msg.timestamp }}
            </span>
          </div>

          <!-- 消息内容 -->
          <div :class="['flex', msg.sender_info.user_id === currentUser.user_id ? 'justify-end' : 'justify-start']">
            <!-- 对方消息 -->
            <template v-if="msg.sender_info.user_id !== currentUser.user_id">
              <div class="flex items-start max-w-[80%]">
                <GroupAvatarWithMenu :avatar-url="currentGroup.avatar_url || '/images/group.png'" :alt-text="msg.sender_info.user_id"
                :message="msg"/>
                <div class="flex flex-col">
                  <span class="text-xs text-gray-500 mb-1">{{ msg.sender_info.nickname }}</span>
                  <UserTextArea
                    :message="msg.content"
                    :isSelf="false"
                  />
                </div>
              </div>
            </template>

            <!-- 自己的消息 -->
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
                <img :src="currentUser.avatar_url" :alt="currentUser.username" class="w-10 h-10 rounded-full ml-2 object-cover" />
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
/* 滑动动画 */
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