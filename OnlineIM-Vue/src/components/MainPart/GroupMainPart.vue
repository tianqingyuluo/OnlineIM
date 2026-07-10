<script setup lang="ts">
import { Ellipsis, CircleEllipsis, AlertCircle, Check, CheckCheck } from 'lucide-vue-next'
import { computed, nextTick, onBeforeUnmount, onMounted, onUpdated, provide, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Textarea } from '@/components/ui/textarea'
import { Button } from '@/components/ui/button'
import Tools from '@/components/MainPart/tools.vue'
import UserTextArea from '@/components/MainPart/UserTextArea.vue'
import GroupInfoCard from '@/components/independent/group/GroupInfoCard.vue'
import GroupAvatarWithMenu from '@/components/independent/group/GroupAvatarWithMenu.vue'
import { groupService } from '@/services/group.service'
import { GroupSettingService } from '@/services/groupsetting.service'
import type { GroupResponse } from '@/type/group'
import type { GroupSetting } from '@/type/groupsetting'
import type { Conversation } from '@/type/Conversation'
import type { MessageResponse } from '@/type/message'
import { useUserStore } from '@/stores/user'
import { useHistoryStore } from '@/stores/history'
import { conversationService } from '@/services/conversation.service'

const route = useRoute()
const userStore = useUserStore()
const historyStore = useHistoryStore()
const currentGroup = ref<GroupResponse | null>(null)
const currentGroupSettings = ref<GroupSetting | null>(null)
const conversation = ref<Conversation | null>(null)
const showMenu = ref(false)
const menuRef = ref<HTMLElement | null>(null)
const menuButtonRef = ref<HTMLElement | null>(null)
const messageScrollRef = ref<HTMLElement | null>(null)
const messageListRef = ref<HTMLElement | null>(null)
const messageInputRef = ref<HTMLTextAreaElement | null>(null)
const visibleTimers = new Map<string, ReturnType<typeof setTimeout>>()
let messageObserver: IntersectionObserver | null = null

provide('messageInputRef', messageInputRef)

const groupId = computed(() => {
  const id = route.params.id
  return Array.isArray(id) ? id[0] : id
})
const currentUser = computed(() => userStore.loggedInUser)
const deliveryLabels: Record<string, string> = {
  sending: '发送中',
  failed: '发送失败',
  sent: '已发送',
  delivered: '已送达',
  read: '已读',
}

function messageState(message: MessageResponse): string {
  return message.delivery_state || 'sent'
}

function stateLabel(message: MessageResponse): string {
  return deliveryLabels[messageState(message)] || '已发送'
}

function isOwn(message: MessageResponse): boolean {
  return message.sender_info.user_id === currentUser.value.user_id
}

function toggleMenu() {
  showMenu.value = !showMenu.value
}

function handleScroll(event: Event) {
  const target = event.target as HTMLElement
  if (target.scrollTop <= 100 && historyStore.hasMore && !historyStore.isLoading) {
    void historyStore.loadMessages(groupId.value, true)
  }
}

async function loadConversation() {
  try {
    const [groupInfo, groupSettings, conversationInfo] = await Promise.all([
      groupService.getGroupInfo(groupId.value),
      GroupSettingService.getGroupSetting(groupId.value),
      conversationService.getConversationById(groupId.value),
    ])
    currentGroup.value = groupInfo
    currentGroupSettings.value = groupSettings
    conversation.value = conversationInfo
    await historyStore.init()
    await historyStore.loadInitialHistory(userStore.loggedInUser.user_id, groupId.value, true)
    await nextTick()
    scrollToBottom()
    installMessageObserver()
  } catch (error) {
    console.error('初始化群聊失败:', error)
  }
}

function scrollToBottom() {
  const container = messageScrollRef.value
  if (container) container.scrollTop = container.scrollHeight
}

function handleKeyDown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.ctrlKey && !event.shiftKey) {
    event.preventDefault()
    void handleSendClick()
  }
}

function handleEmojiSelect(emoji: string) {
  const textarea = messageInputRef.value
  if (!textarea) return
  const start = textarea.selectionStart || 0
  textarea.value = `${textarea.value.slice(0, start)}${emoji}${textarea.value.slice(start)}`
  textarea.selectionStart = textarea.selectionEnd = start + emoji.length
  textarea.focus()
}

async function handleSendClick() {
  const content = messageInputRef.value?.value.trim()
  if (!content) return
  historyStore.sendMessage(groupId.value, groupId.value, 'text', content, true)
  if (messageInputRef.value) messageInputRef.value.value = ''
  await nextTick()
  scrollToBottom()
}

function handleRetry(message: MessageResponse) {
  historyStore.retryMessage(message, true, groupId.value)
}

function toggleReaders(message: MessageResponse) {
  historyStore.toggleMessageReaders(message)
}

function markVisible(messageId: string) {
  const message = historyStore.groupMessages.find(item => item.message_id === messageId)
  if (message) historyStore.markMessageVisible(message)
}

function installMessageObserver() {
  messageObserver?.disconnect()
  if (!messageListRef.value || typeof IntersectionObserver === 'undefined') return
  messageObserver = new IntersectionObserver(entries => {
    for (const entry of entries) {
      const messageId = (entry.target as HTMLElement).dataset.messageId
      if (!messageId) continue
      const existingTimer = visibleTimers.get(messageId)
      if (!entry.isIntersecting) {
        if (existingTimer) clearTimeout(existingTimer)
        visibleTimers.delete(messageId)
        continue
      }
      if (existingTimer) continue
      const timer = setTimeout(() => {
        visibleTimers.delete(messageId)
        markVisible(messageId)
      }, 300)
      visibleTimers.set(messageId, timer)
    }
  }, { root: messageScrollRef.value, threshold: 0.6 })
  messageListRef.value.querySelectorAll<HTMLElement>('[data-message-id]').forEach(element => {
    messageObserver?.observe(element)
  })
}

onMounted(() => {
  void loadConversation()
})

onUpdated(() => {
  installMessageObserver()
})

onBeforeUnmount(() => {
  messageObserver?.disconnect()
  visibleTimers.forEach(timer => clearTimeout(timer))
  visibleTimers.clear()
})
</script>

<template>
  <div v-if="currentGroup && conversation" class="flex flex-col h-full">
    <div class="flex items-center justify-between w-full p-4 border-b relative">
      <span class="text-lg font-semibold">{{ currentGroup.name }}</span>
      <div class="flex items-center gap-2">
        <Button variant="ghost" size="sm" @click="historyStore.markConversationReadToLatest(conversation.conversation_id)">
          全部标为已读
        </Button>
        <button ref="menuButtonRef" @click="toggleMenu"><Ellipsis class="w-5 h-5" /></button>
      </div>
      <Transition name="slide">
        <div v-if="showMenu" ref="menuRef" class="absolute right-0 top-full w-80 bg-white shadow-lg z-50 h-[calc(100vh-60px)]">
          <GroupInfoCard
            :group="currentGroup"
            :group-settings="currentGroupSettings || undefined"
            :myRole="currentGroup.my_role"
            :conversation="conversation"
            class="h-full overflow-y-auto"
          />
        </div>
      </Transition>
    </div>

    <div ref="messageScrollRef" class="flex-1 overflow-y-auto p-4" @scroll="handleScroll">
      <div v-if="historyStore.noMoreInfo" class="flex justify-center py-2 text-sm text-gray-500">没有更多信息</div>
      <div v-if="historyStore.groupMessages.length > 0" ref="messageListRef" class="space-y-4">
        <div
          v-for="message in historyStore.groupMessages"
          :key="message.message_id"
          :data-message-id="message.message_id"
        >
          <div class="flex justify-center">
            <span class="text-[13px] text-gray-500 truncate">{{ message.timestamp }}</span>
          </div>
          <div :class="['flex', isOwn(message) ? 'justify-end' : 'justify-start']">
            <div v-if="!isOwn(message)" class="flex items-start max-w-[80%]">
              <GroupAvatarWithMenu
                :avatar-url="message.sender_info.avatar_url || '/images/group.png'"
                :alt-text="message.sender_info.nickname || message.sender_info.user_id"
                :message="message"
              />
              <div class="flex flex-col">
                <span class="text-xs text-gray-500 mb-1">{{ message.sender_info.nickname || message.sender_info.username }}</span>
                <UserTextArea :message="message.content" :isSelf="false" />
              </div>
            </div>
            <div v-else class="flex items-end max-w-[80%]">
              <div class="flex flex-col items-end mr-2 text-xs text-gray-500">
                <span v-if="messageState(message) === 'failed'" class="flex items-center gap-1 text-red-500 cursor-pointer" @click="handleRetry(message)">
                  <AlertCircle class="w-4 h-4" /> 点击重试
                </span>
                <span v-else class="flex items-center gap-1">
                  <CircleEllipsis v-if="messageState(message) === 'sending'" class="w-4 h-4 animate-spin" />
                  <Check v-else-if="messageState(message) === 'sent'" class="w-4 h-4" />
                  <CheckCheck v-else class="w-4 h-4" />
                  {{ stateLabel(message) }}
                </span>
                <button
                  v-if="message.readers?.length"
                  class="flex items-center gap-1 mt-1 hover:text-blue-600"
                  @click="toggleReaders(message)"
                >
                  <span class="flex -space-x-1">
                    <img
                      v-for="reader in message.readers.slice(0, 3)"
                      :key="reader.user_id"
                      :src="reader.avatar_url || '/images/default-avatar.png'"
                      :alt="reader.nickname || reader.user_id"
                      class="w-4 h-4 rounded-full border border-white object-cover"
                    />
                  </span>
                  {{ message.readers.length }} 人已读
                </button>
                <div v-if="message.readers_expanded && message.readers?.length" class="mt-1 rounded bg-white shadow p-2 text-left min-w-36">
                  <div v-for="reader in message.readers" :key="reader.user_id" class="flex items-center gap-2 py-1">
                    <img :src="reader.avatar_url || '/images/default-avatar.png'" class="w-5 h-5 rounded-full object-cover" />
                    <span>{{ reader.nickname || reader.username || reader.user_id }}</span>
                  </div>
                </div>
              </div>
              <UserTextArea :message="message.content" :isSelf="true" />
              <img :src="currentUser.avatar_url" :alt="currentUser.username" class="w-10 h-10 rounded-full ml-2 object-cover" />
            </div>
          </div>
        </div>
      </div>
      <div v-else class="flex items-center justify-center h-full text-gray-500">暂无消息记录</div>
      <div v-if="historyStore.isLoading" class="flex justify-center py-2 text-sm text-gray-500">加载中...</div>
    </div>

    <Tools @select="handleEmojiSelect" />
    <div class="relative h-1/4 border-t">
      <Textarea
        ref="messageInputRef"
        class="h-full w-full resize-none pr-20 rounded-none focus:ring-0 focus:shadow-none whitespace-pre-wrap"
        style="outline: none; box-shadow: none; font-size: 24px; word-break: break-all"
        placeholder="输入消息..."
        @keydown="handleKeyDown"
      />
      <Button class="absolute bottom-4 right-4" @click="handleSendClick">发送</Button>
    </div>
  </div>
  <div v-else class="flex items-center justify-center h-full">加载中...</div>
</template>

<style scoped>
.slide-enter-active,
.slide-leave-active { transition: transform 0.3s ease; }
.slide-enter-from,
.slide-leave-to { transform: translateX(100%); }
</style>
