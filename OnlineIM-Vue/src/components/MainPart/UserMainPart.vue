<script setup lang="ts">
import { Ellipsis, CircleEllipsis, AlertCircle, Check, CheckCheck } from 'lucide-vue-next'
import { computed, nextTick, onBeforeUnmount, onMounted, onUpdated, provide, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Textarea } from '@/components/ui/textarea'
import { Button } from '@/components/ui/button'
import Tools from '@/components/MainPart/tools.vue'
import UserTextArea from '@/components/MainPart/UserTextArea.vue'
import type { ChatMedia, ChatMediaKind } from '@/utils/media-message'
import type { CustomEmoji } from '@/utils/sticker-message'
import { useEmojiStore } from '@/stores/emoji'
import FriendSettingsCard from '@/components/independent/friends/FriendSettingsCard.vue'
import { userService } from '@/services/user.service'
import type { User } from '@/type/User'
import type { Conversation } from '@/type/Conversation'
import type { MessageResponse } from '@/type/message'
import { useListStore } from '@/stores/list'
import { useUserStore } from '@/stores/user'
import { useHistoryStore } from '@/stores/history'
import { conversationService } from '@/services/conversation.service'
import ReplyComposerBar from '@/components/MainPart/ReplyComposerBar.vue'
import type { ReplyReference } from '@/type/message'
import { focusMessageElement } from '@/utils/message-navigation'
import { toast } from 'vue-sonner'
import { useStickToBottom } from '@/composables/useStickToBottom'
import { useMessageForward } from '@/composables/useMessageForward'
import ForwardTargetPicker from '@/components/MainPart/ForwardTargetPicker.vue'
import ForwardSelectionBar from '@/components/MainPart/ForwardSelectionBar.vue'
import MessageSelectOverlay from '@/components/MainPart/MessageSelectOverlay.vue'
import { isForwardable } from '@/utils/chat-record'
import { useMessageEdit } from '@/composables/useMessageEdit'
import EditComposerBar from '@/components/MainPart/EditComposerBar.vue'

const route = useRoute()
const userStore = useUserStore()
const listStore = useListStore()
const historyStore = useHistoryStore()
const emojiStore = useEmojiStore()
const currentUser = ref<User | null>(null)
const conversation = ref<Conversation | null>(null)
const showMenu = ref(false)
const menuRef = ref<HTMLElement | null>(null)
const menuButtonRef = ref<HTMLElement | null>(null)
const messageScrollRef = ref<HTMLElement | null>(null)
const messageListRef = ref<HTMLElement | null>(null)
const messageInputRef = ref<HTMLTextAreaElement | null>(null)
const draft = ref('')
const visibleTimers = new Map<string, ReturnType<typeof setTimeout>>()
let messageObserver: IntersectionObserver | null = null

provide('messageInputRef', messageInputRef)

const conversationId = computed(() => {
  const id = route.params.id
  return Array.isArray(id) ? id[0] : id
})
/** 转发：右键“转发”/“多选”，多选后逐条或合并转发 */
const forward = reactive(useMessageForward(() => conversationId.value, () => historyStore.chatMessages))
/** 编辑：右键“编辑”把原文放进输入框，回车 / 保存提交 */
const edit = reactive(useMessageEdit(draft, () => userStore.loggedInUser.user_id, () => inputElement()?.focus()))
// 对方 ID 以会话详情为准；直接打开/刷新聊天地址时侧栏列表可能还没加载，不能只依赖列表
const targetUserId = computed(() =>
  conversation.value?.target_info?.id
  || listStore.conversations.find(item => item.conversation_id === conversationId.value)?.target_info.id
  || '',
)

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
  return message.sender_info.user_id === userStore.loggedInUser.user_id
}

// 新消息到达时：自己发的总是滚到底部，别人的只在本来停在底部附近时滚动
useStickToBottom(messageScrollRef, () => historyStore.chatMessages, isOwn)

function toggleMenu() {
  showMenu.value = !showMenu.value
}

function handleScroll(event: Event) {
  const target = event.target as HTMLElement
  if (target.scrollTop <= 100 && historyStore.hasMore && !historyStore.isLoading) {
    void historyStore.loadMessages(conversationId.value, false)
  }
}

const loadError = ref('')

async function loadConversation() {
  loadError.value = ''
  try {
    const id = conversationId.value
    conversation.value = await conversationService.getConversationById(id)
    if (!targetUserId.value) throw new Error('会话缺少对方信息')
    currentUser.value = await userService.getUserById(targetUserId.value)
    await historyStore.init()
    await historyStore.loadInitialHistory(userStore.loggedInUser.user_id, id, false)
    await nextTick()
    scrollToBottom()
    installMessageObserver()
  } catch (error) {
    console.error('初始化私聊失败:', error)
    loadError.value = '会话加载失败，可能已无权访问或网络异常'
  }
}

function scrollToBottom() {
  const container = messageScrollRef.value
  if (container) container.scrollTop = container.scrollHeight
}

function handleKeyDown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    if (edit.isEditing) edit.cancel()
    else historyStore.cancelReplyTarget()
    return
  }
  if (event.key === 'Enter' && !event.ctrlKey && !event.shiftKey) {
    event.preventDefault()
    void handleSendClick()
  }
}

/** Textarea 组件的根元素就是原生 textarea（其暴露的 element 未绑定，取 $el） */
function inputElement(): HTMLTextAreaElement | null {
  const input = messageInputRef.value as unknown as { element?: HTMLTextAreaElement | null; $el?: Element } | null
  const element = input?.element ?? input?.$el
  return typeof HTMLTextAreaElement !== 'undefined' && element instanceof HTMLTextAreaElement ? element : null
}

/** 在光标处插入表情（之前读不到原生 textarea 的 selectionStart，总是插到开头） */
async function handleEmojiSelect(emoji: string) {
  const element = inputElement()
  const start = element?.selectionStart ?? draft.value.length
  const end = element?.selectionEnd ?? start
  draft.value = `${draft.value.slice(0, start)}${emoji}${draft.value.slice(end)}`
  await nextTick()
  element?.focus()
  element?.setSelectionRange(start + emoji.length, start + emoji.length)
}

async function handleMediaUploaded(kind: ChatMediaKind, media: ChatMedia) {
  if (!conversation.value) return
  historyStore.sendMessage(conversation.value.conversation_id, targetUserId.value, kind, JSON.stringify(media), false)
  await nextTick()
  scrollToBottom()
}

/** 自定义表情：content 只需 emoji_id，其余字段用于本地乐观显示，服务端会按收藏记录重新填充 */
async function handleStickerSend(emoji: CustomEmoji) {
  if (!conversation.value) return
  historyStore.sendMessage(conversation.value.conversation_id, targetUserId.value, 'emoji', JSON.stringify(emoji), false)
  await nextTick()
  scrollToBottom()
}

function handleSaveEmoji(message: MessageResponse) {
  if (!conversation.value) return
  void emojiStore.saveFromMessage(conversation.value.conversation_id, message.message_id)
}

async function handleSendClick() {
  if (edit.isEditing) {
    await edit.save()
    return
  }
  const content = draft.value.trim()
  if (!content || !conversation.value) return
  historyStore.sendMessage(conversation.value.conversation_id, targetUserId.value, 'text', content, false)
  draft.value = ''
  await nextTick()
  scrollToBottom()
}

function handleRetry(message: MessageResponse) {
  historyStore.retryMessage(message, false, targetUserId.value)
}

function handleReply(message: MessageResponse) {
  if (edit.isEditing) edit.cancel()
  if (historyStore.selectReplyTarget(message)) inputElement()?.focus()
}

async function handleReplyNavigate(reference: ReplyReference) {
  const container = messageScrollRef.value
  const previousScrollTop = container?.scrollTop
  try {
    const target = await historyStore.jumpToMessage(conversationId.value, reference.message_id)
    if (!target) throw new Error('message unavailable')
    await nextTick()
    if (!container || !focusMessageElement(container, target.message_id)) {
      throw new Error('message element unavailable')
    }
  } catch (error) {
    if (container && previousScrollTop !== undefined) container.scrollTop = previousScrollTop
    toast.error('原消息不可用')
  }
}

function markVisible(messageId: string) {
  const message = historyStore.chatMessages.find(item => item.message_id === messageId)
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

watch(conversationId, (nextId, previousId) => {
  if (previousId && nextId !== previousId) {
    forward.cancelSelect()
    edit.reset()
    draft.value = ''
    void loadConversation()
  }
})

watch(() => historyStore.failedReplyDraft, async failed => {
  if (!failed || failed.conversationId !== conversationId.value) return
  await nextTick()
  if (draft.value.trim()) return
  draft.value = failed.content
  inputElement()?.focus()
  historyStore.consumeFailedReplyDraft()
})

onUpdated(() => {
  installMessageObserver()
})

onBeforeUnmount(() => {
  messageObserver?.disconnect()
  visibleTimers.forEach(timer => clearTimeout(timer))
  visibleTimers.clear()
  if (historyStore.replyTarget?.conversationId === conversationId.value) {
    historyStore.cancelReplyTarget()
  }
  historyStore.leaveConversation(conversationId.value)
})
</script>

<template>
  <div v-if="currentUser && conversation" class="flex flex-col h-full">
    <div class="flex items-center justify-between w-full p-4 border-b relative">
      <span class="text-lg font-semibold">{{ currentUser.nickname || currentUser.username }}</span>
      <div class="flex items-center gap-2">
        <Button variant="ghost" size="sm" @click="historyStore.markConversationReadToLatest(conversation.conversation_id)">
          全部标为已读
        </Button>
        <button ref="menuButtonRef" @click="toggleMenu">
          <Ellipsis class="w-5 h-5" />
        </button>
      </div>
      <Transition name="slide">
        <div v-if="showMenu" ref="menuRef" class="absolute right-0 top-full w-80 bg-white shadow-lg z-50 h-[calc(100vh-60px)]">
          <FriendSettingsCard :conversation="conversation" :userId="targetUserId" />
        </div>
      </Transition>
    </div>

    <div ref="messageScrollRef" data-testid="message-scroll" class="flex-1 overflow-y-auto p-4" @scroll="handleScroll">
      <div v-if="historyStore.noMoreInfo" class="flex justify-center py-2 text-sm text-gray-500">没有更多信息</div>
      <div v-if="historyStore.chatMessages.length > 0" ref="messageListRef" class="space-y-4">
        <div
          v-for="message in historyStore.chatMessages"
          :key="message.message_id"
          :data-message-id="message.message_id"
          :data-selected="forward.selecting ? String(forward.isSelected(message)) : undefined"
          :class="['rounded-xl transition-shadow duration-300', forward.selecting ? 'relative pl-8' : '']"
        >
          <MessageSelectOverlay
            v-if="forward.selecting"
            :selected="forward.isSelected(message)"
            :disabled="!isForwardable(message)"
            @toggle="forward.toggleSelect(message)"
          />
          <div class="flex justify-center">
            <span class="text-[13px] text-gray-500 truncate">{{ message.timestamp }}</span>
          </div>
          <div :class="['flex', isOwn(message) ? 'justify-end' : 'justify-start']">
            <div v-if="!isOwn(message)" class="flex items-start max-w-[80%]">
              <img :src="currentUser.avatar_url" :alt="currentUser.username" class="w-10 h-10 rounded-full mr-2 object-cover" />
              <UserTextArea
                :message="message.content"
                :message-type="message.message_type"
                :is-self="false"
                :can-reply="!message.is_recalled && String(message.status) !== '3'"
                :recalled="message.is_recalled || String(message.status) === '3'"
                :reply-to="message.reply_to"
                :forwarded-from="message.forwarded_from"
                :can-forward="!forward.selecting && isForwardable(message)"
                :can-select="!forward.selecting"
                :edited="message.edited"
                :edited-at="message.edited_at"
                :edit-until="forward.selecting ? 0 : edit.editUntil(message)"
                @edit="edit.start(message)"
                @forward="forward.forwardOne(message)"
                @select="forward.startSelect(message)"
                @reply="handleReply(message)"
                @navigate="handleReplyNavigate"
                @save-emoji="handleSaveEmoji(message)"
              />
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
              </div>
              <UserTextArea
                :message="message.content"
                :message-type="message.message_type"
                :is-self="true"
                :can-reply="!message.is_recalled && String(message.status) !== '3'"
                :recalled="message.is_recalled || String(message.status) === '3'"
                :reply-to="message.reply_to"
                :forwarded-from="message.forwarded_from"
                :can-forward="!forward.selecting && isForwardable(message)"
                :can-select="!forward.selecting"
                :edited="message.edited"
                :edited-at="message.edited_at"
                :edit-until="forward.selecting ? 0 : edit.editUntil(message)"
                @edit="edit.start(message)"
                @forward="forward.forwardOne(message)"
                @select="forward.startSelect(message)"
                @reply="handleReply(message)"
                @navigate="handleReplyNavigate"
                @save-emoji="handleSaveEmoji(message)"
              />
              <img :src="userStore.loggedInUser.avatar_url" :alt="userStore.loggedInUser.username" class="w-10 h-10 rounded-full ml-2 object-cover" />
            </div>
          </div>
        </div>
      </div>
      <div v-else class="flex items-center justify-center h-full text-gray-500">暂无消息记录</div>
      <div v-if="historyStore.isLoading" class="flex justify-center py-2 text-sm text-gray-500">加载中...</div>
    </div>

    <ForwardSelectionBar
      v-if="forward.selecting"
      :count="forward.selectedCount"
      @separate="forward.forwardSelected('separate')"
      @merged="forward.forwardSelected('merged')"
      @cancel="forward.cancelSelect()"
    />
    <Tools v-show="!forward.selecting" @select="handleEmojiSelect" @uploaded="handleMediaUploaded" @sticker="handleStickerSend" />
    <EditComposerBar
      v-if="!forward.selecting && edit.editing"
      :original="edit.editing.original"
      @cancel="edit.cancel()"
    />
    <ReplyComposerBar
      v-else-if="!forward.selecting && historyStore.replyTarget?.conversationId === conversationId"
      :reference="historyStore.replyTarget.reference"
      @cancel="historyStore.cancelReplyTarget()"
    />
    <div v-show="!forward.selecting" class="relative h-1/4 border-t">
      <Textarea
        ref="messageInputRef"
        v-model="draft"
        data-testid="message-input"
        class="h-full w-full resize-none pr-20 rounded-none focus:ring-0 focus:shadow-none whitespace-pre-wrap"
        style="outline: none; box-shadow: none; font-size: 24px; word-break: break-all"
        placeholder="输入消息..."
        @keydown="handleKeyDown"
      />
      <Button class="absolute bottom-4 right-4" data-testid="send-button" :disabled="edit.saving" @click="handleSendClick">{{ edit.isEditing ? '保存' : '发送' }}</Button>
    </div>
    <ForwardTargetPicker
      :open="forward.pickerOpen"
      :mode="forward.pickerMode"
      :message-count="forward.pickerIds.length"
      :busy="forward.busy"
      @close="forward.closePicker()"
      @confirm="forward.confirm"
    />
  </div>
  <div v-else class="flex flex-col items-center justify-center h-full gap-3 text-gray-500" data-testid="chat-loading">
    <template v-if="loadError">
      <span data-testid="chat-load-error">{{ loadError }}</span>
      <Button variant="outline" size="sm" @click="loadConversation">重试</Button>
    </template>
    <template v-else>加载中...</template>
  </div>
</template>

<style scoped>
.slide-enter-active,
.slide-leave-active { transition: transform 0.3s ease; }
.slide-enter-from,
.slide-leave-to { transform: translateX(100%); }

.reply-focus-highlight {
  animation: reply-focus-pulse 2s ease-out;
}

@keyframes reply-focus-pulse {
  0%, 25% {
    background: rgba(251, 191, 36, 0.22);
    box-shadow: 0 0 0 4px rgba(251, 191, 36, 0.15);
  }
  100% {
    background: transparent;
    box-shadow: 0 0 0 0 rgba(251, 191, 36, 0);
  }
}
</style>
