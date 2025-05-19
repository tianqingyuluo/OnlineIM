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
import {MessageService} from "@/services/message.service.ts";
import { useUserStore } from '@/stores/user.ts';
import { GroupSettingService } from "@/services/groupsetting.service";
import GroupAvatarWithMenu from "@/components/MainPart/GroupAvatarWithMenu.vue";

// 添加菜单元素的引用
const menuRef = ref<HTMLElement | null>(null)//右上角群info
const menuButtonRef = ref<HTMLElement | null>(null)

// 点击区域外关闭菜单
onClickOutside(menuRef, (event) => {
  // 检查点击是否来自菜单按钮，如果是则不关闭
  if (menuButtonRef.value && menuButtonRef.value.contains(event.target as Node)) {
    return
  }
  showMenu.value = false
}, {
  ignore: [menuButtonRef] // 忽略菜单按钮的点击
})

const route = useRoute()
const currentGroup = ref<GroupResponse | null>(null)
const currentGroupSettings = ref<any>(null)
const userStore = useUserStore()
const currentUser = computed(() => userStore.loggedInUser)

const groupId = computed(() => {
  const id = route.params.id
  return Array.isArray(id) ? id[0] : id
})


onMounted(async () => {
  try {
    console.log("当前群组ID:", groupId.value) // 添加这行检查路由参数
    const [groupInfo, groupSettings] = await Promise.all([
      groupService.getGroupInfo(groupId.value),
      GroupSettingService.getGroupSetting(groupId.value)
    ])
    console.log("API返回数据:", groupInfo) // 检查API返回
    currentGroup.value = groupInfo
    currentGroupSettings.value = groupSettings
    console.log("设置后的群组信息:", currentGroup.value) // 检查响应式数据
    
    // 确保加载群组信息后立即加载消息
    await loadMessages()
  } catch (error) {
    console.error('获取群组信息失败:', error)
  }
})
const groupMessages = ref<any[]>([])
const isLoading = ref(false)
const hasMore = ref(true)
const page = ref(1)
const pageSize = 20
const noMoreInfo = ref(false)

async function loadMessages() {
  if (isLoading.value || !hasMore.value) return
  
  isLoading.value = true
  try {
    const timestamp = groupMessages.value.length > 0 
      ? groupMessages.value[0].created_at 
      : undefined
    const messageHistory = await MessageService.getMessageHistory(groupId.value, timestamp)
    console.log('获取到的消息历史:', messageHistory)
    if (messageHistory) {
      const newMessages = messageHistory.messages
      const total=messageHistory.total
      if (total < pageSize) {
        hasMore.value = false
        noMoreInfo.value = true
      }
      
      if (!timestamp) {
        groupMessages.value = newMessages
      } else {
        groupMessages.value = [...newMessages, ...groupMessages.value]
      }
      
      console.log('获取到的消息历史:', newMessages)
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
    page.value += 1
    loadMessages()
  }
}

onMounted(() => {
  loadMessages()
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

// 解析中文日期格式
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

// 判断是否需要显示时间（5分钟间隔）
function shouldShowTime(index: number, list: any[]) {
  if (index === 0) return true;
  const prevTime = parseChineseDate(list[index - 1].sendTime).getTime();
  const currentTime = parseChineseDate(list[index].sendTime).getTime();
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
  if (textareaEl && textareaEl.value.trim()) {
    try {
      const response = await MessageService.putMessage(
        groupId.value,
        0, // 文本消息
        textareaEl.value
      )
            // 添加到消息列表
      groupMessages.value.push(response)
      
      // 清空输入框
      textareaEl.value = ''
      
      // 滚动到底部
      nextTick(() => {
        const chatContainer = document.querySelector('.overflow-y-auto')
        if (chatContainer) {
          chatContainer.scrollTop = chatContainer.scrollHeight
        }
      })
    } catch (error) {
      console.error('发送消息失败:', error)
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
          <GroupInfoCard :group="currentGroup" :group-settings="currentGroupSettings" :myRole="currentGroup.my_role" class="h-full overflow-y-auto" />
        </div>
      </Transition>
    </div>

    <!-- 主内容区 -->
    <div class="flex-1 overflow-y-auto p-4" @scroll="handleScroll">
      <div v-if="noMoreInfo" class="flex justify-center py-2 text-sm text-gray-500">
        没有更多信息
      </div>
      <div v-if="groupMessages.length > 0" class="space-y-4">
        <template v-for="(msg, index) in groupMessages" :key="msg.message_id">
          <!-- 时间显示 -->
          <div v-if="shouldShowTime(index, groupMessages)" class="flex justify-center">
            <span class="text-[13px] text-gray-500 truncate">
              {{ new Date(msg.created_at).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') }}
            </span>
          </div>

          <!-- 消息内容 -->
          <div :class="['flex', msg.sender_id === currentUser.user_id ? 'justify-end' : 'justify-start']">
            <!-- 对方消息 -->
            <template v-if="msg.sender_id !== currentUser.user_id">
              <div class="flex items-start max-w-[80%]">
                <GroupAvatarWithMenu :avatar-url="currentGroup.avatar_url || '/images/group.png'" :alt-text="msg.sender_id" />
                <UserTextArea
                  :message="msg.content"
                  :isSelf="false"
                />
              </div>
            </template>

            <!-- 自己的消息 -->
            <template v-else>
              <div class="flex items-start">
                <UserTextArea
                  :message="msg.content"
                  :isSelf="true"
                />
                <img :src="currentUser.avatar_url" :alt="currentUser.username" class="w-10 h-10 rounded-full ml-2" />
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
</style>