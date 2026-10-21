<script setup lang="ts">
import { Ellipsis, CircleEllipsis, AlertCircle, Check, CheckCheck, Search } from 'lucide-vue-next'
import { computed, nextTick, onBeforeUnmount, onMounted, onUpdated, provide, reactive, ref, watch } from 'vue'
import { useLoadedConversation } from '@/composables/useLoadedConversation'
import { useRoute, useRouter } from 'vue-router'
import ConversationSearchPanel from '@/components/search/ConversationSearchPanel.vue'
import type { MessageSearchItem } from '@/type/message'
import { Textarea } from '@/components/ui/textarea'
import { Button } from '@/components/ui/button'
import Tools from '@/components/MainPart/tools.vue'
import UserTextArea from '@/components/MainPart/UserTextArea.vue'
import type { ChatMedia, ChatMediaKind } from '@/utils/media-message'
import type { CustomEmoji } from '@/utils/sticker-message'
import { useEmojiStore } from '@/stores/emoji'
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
import { useConversationDraft } from '@/composables/useConversationDraft'
import type { DraftMention } from '@/utils/draft'
import { mentionLabelsForEdit } from '@/utils/message-edit'
import EditComposerBar from '@/components/MainPart/EditComposerBar.vue'
import RecallNotice from '@/components/MainPart/RecallNotice.vue'
import RecallConfirmDialog from '@/components/MainPart/RecallConfirmDialog.vue'
import { useMessageRecall } from '@/composables/useMessageRecall'
import { isRecalledMessage } from '@/utils/message-recall'
import { usePresenceStore } from '@/stores/presence'
import { useListStore } from '@/stores/list'
import type { GroupMemberAll } from '@/type/group'
import {
  buildMentionCandidates,
  collectAtUsers,
  filterMentionCandidates,
  findMentionQuery,
  insertMention,
  MENTION_ALL_ID,
  withMentionAll,
  type MentionCandidate,
} from '@/utils/mention'
import GroupAnnouncementBar from '@/components/MainPart/GroupAnnouncementBar.vue'

const route = useRoute()
const userStore = useUserStore()
const historyStore = useHistoryStore()
const emojiStore = useEmojiStore()
const currentGroup = ref<GroupResponse | null>(null)
const currentGroupSettings = ref<GroupSetting | null>(null)
const conversation = ref<Conversation | null>(null)
const showMenu = ref(false)
/** 会话内搜索面板 */
const searchOpen = ref(false)
/** 首屏历史已加载，可以执行 ?msg= 跳转 */
const historyReady = ref(false)
const router = useRouter()
const menuRef = ref<HTMLElement | null>(null)
const menuButtonRef = ref<HTMLElement | null>(null)
const messageScrollRef = ref<HTMLElement | null>(null)
const messageListRef = ref<HTMLElement | null>(null)
const messageInputRef = ref<HTMLTextAreaElement | null>(null)
const draft = ref('')
const listStore = useListStore()
const presenceStore = usePresenceStore()
const groupMembers = ref<GroupMemberAll[]>([])
/** 本次草稿中通过选择器选中的成员：user_id → 插入的名字 */
const pickedMentions = new Map<string, string>()
const mention = ref<{ start: number; query: string } | null>(null)
const mentionIndex = ref(0)
const mentionListRef = ref<HTMLElement | null>(null)
const visibleTimers = new Map<string, ReturnType<typeof setTimeout>>()
let messageObserver: IntersectionObserver | null = null

provide('messageInputRef', messageInputRef)

const groupId = computed(() => {
  const id = route.params.id
  return Array.isArray(id) ? id[0] : id
})
/** 本窗口实际加载的会话，卸载时据此离开（不再视为正在查看） */
const loadedConversation = useLoadedConversation(id => historyStore.leaveConversation(id))
/** 转发：右键“转发”/“多选”，多选后逐条或合并转发 */
const forward = reactive(useMessageForward(() => groupId.value, () => historyStore.groupMessages))
const currentUser = computed(() => userStore.loggedInUser)
/** 编辑：右键“编辑”把原文放进输入框，回车 / 保存提交；@ 成员按文本里仍存在的 `@名字` 重新计算 */
const edit = reactive(useMessageEdit(draft, () => userStore.loggedInUser.user_id, () => inputElement()?.focus(), {
  beforeRestore: () => restoreStashedMentions(),
}))
/** 进入编辑前的 @ 选择，取消或保存后放回输入框，避免编辑态覆盖服务端草稿 */
let stashedMentions: Array<[string, string]> = []
const draftSync = useConversationDraft({
  conversationId: groupId,
  draft,
  isEditing: computed(() => edit.isEditing),
  getExtra: id => {
    const atUsers: DraftMention[] = []
    for (const [userId, name] of pickedMentions) {
      if (collectAtUsers(draft.value, new Map([[userId, name]]))?.includes(userId)) {
        atUsers.push({ user_id: userId, name })
      }
    }
    return {
      replyToMessageId: id && historyStore.replyTarget?.conversationId === id
        ? historyStore.replyTarget.reference.message_id
        : null,
      atUsers,
    }
  },
  applyExtra: (extra, payload) => {
    pickedMentions.clear()
    for (const item of extra.atUsers) pickedMentions.set(item.user_id, item.name)
    const id = groupId.value
    if (!id) return
    if (extra.replyToMessageId && payload?.reply_to) {
      historyStore.replyTarget = { conversationId: id, reference: payload.reply_to }
    } else if (historyStore.replyTarget?.conversationId === id) {
      historyStore.cancelReplyTarget()
    }
  },
})
/** 撤回：右键 / 长按“撤回” → 确认 → 所有成员看到“X 撤回了一条消息”，自己的文本可“重新编辑” */
const recall = reactive(useMessageRecall(() => userStore.loggedInUser.user_id, {
  beforeRecall: message => {
    if (edit.editing?.messageId === message.message_id) cancelEdit()
  },
}))
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

// 新消息到达时：自己发的总是滚到底部，别人的只在本来停在底部附近时滚动
useStickToBottom(messageScrollRef, () => historyStore.groupMessages, isOwn)

const friendRemarks = computed<Record<string, string | undefined>>(() => {
  const map: Record<string, string | undefined> = {}
  for (const friend of listStore.friends || []) {
    const info = friend.friend_info
    if (info?.user_id && info.remark) map[info.user_id] = info.remark
  }
  return map
})
const mentionCandidates = computed(() =>
  withMentionAll(
    buildMentionCandidates(groupMembers.value, currentUser.value?.user_id, friendRemarks.value),
    currentGroup.value?.my_role,
  ))
const mentionOptions = computed(() =>
  mention.value ? filterMentionCandidates(mentionCandidates.value, mention.value.query) : [])
const mentionOpen = computed(() => !!mention.value && mentionOptions.value.length > 0)
/** 气泡高亮用：user_id → 可能出现在 @ 后面的名字（含自己） */
const mentionNames = computed<Record<string, string[]>>(() => {
  const map: Record<string, string[]> = {}
  for (const member of groupMembers.value) {
    const info = member.user_info
    if (!info?.user_id) continue
    map[info.user_id] = [member.group_nickname, info.nickname, info.username]
      .filter((name): name is string => !!name && !!name.trim())
  }
  map[MENTION_ALL_ID] = ['所有人']
  return map
})

const onlineCount = computed(() => groupMembers.value.filter(member => {
  const record = presenceStore.byUserId[member.user_info?.user_id]
  return record?.visible !== false && record?.status === 'online'
}).length)

watch(groupMembers, members => {
  const ids = members.map(member => member.user_info?.user_id).filter((id): id is string => !!id)
  if (ids.length) void presenceStore.refresh(ids).catch(error => console.error('加载群成员在线状态失败:', error))
})

async function loadMembers() {
  const id = groupId.value
  try {
    const response = await groupService.getGroupMembers(id)
    if (id === groupId.value) groupMembers.value = response?.members || []
  } catch (error) {
    console.error('获取群成员失败（@ 选择器不可用）:', error)
    groupMembers.value = []
  }
}

/** Textarea 组件的根元素就是原生 textarea（其暴露的 element 未绑定，取 $el） */
function inputElement(): HTMLTextAreaElement | null {
  const input = messageInputRef.value as unknown as { element?: HTMLTextAreaElement | null; $el?: Element } | null
  const element = input?.element ?? input?.$el
  return typeof HTMLTextAreaElement !== 'undefined' && element instanceof HTMLTextAreaElement ? element : null
}

function updateMentionQuery() {
  const element = inputElement()
  const caret = element?.selectionStart ?? draft.value.length
  const next = findMentionQuery(draft.value, caret)
  if (!next) {
    mention.value = null
    return
  }
  if (!mention.value || mention.value.start !== next.start || mention.value.query !== next.query) {
    mentionIndex.value = 0
  }
  mention.value = next
}

function closeMention() {
  mention.value = null
}

/** 失焦时稍后再关，避免手机上点选项前列表先消失 */
function closeMentionSoon() {
  setTimeout(() => {
    if (document.activeElement !== inputElement()) closeMention()
  }, 200)
}

async function selectMention(candidate: MentionCandidate) {
  const current = mention.value
  if (!current) return
  const caret = current.start + 1 + current.query.length
  const next = insertMention(draft.value, current.start, caret, candidate.label)
  draft.value = next.text
  pickedMentions.set(candidate.user_id, candidate.label)
  mention.value = null
  await nextTick()
  const element = inputElement()
  if (element) {
    element.focus()
    element.setSelectionRange(next.caret, next.caret)
  }
}

/** 手机上不方便输入 @ 时，用按钮插入一个 @ 并打开成员列表 */
async function openMentionPicker() {
  const element = inputElement()
  const caret = element?.selectionStart ?? draft.value.length
  const prev = caret > 0 ? draft.value[caret - 1] : ''
  const insert = prev && /[A-Za-z0-9]/u.test(prev) ? ' @' : '@'
  draft.value = `${draft.value.slice(0, caret)}${insert}${draft.value.slice(caret)}`
  await nextTick()
  const at = caret + insert.length
  element?.focus()
  element?.setSelectionRange(at, at)
  updateMentionQuery()
}

function moveMention(delta: number) {
  const count = mentionOptions.value.length
  if (!count) return
  mentionIndex.value = (mentionIndex.value + delta + count) % count
  void nextTick(() => {
    mentionListRef.value
      ?.querySelector<HTMLElement>(`[data-index="${mentionIndex.value}"]`)
      ?.scrollIntoView({ block: 'nearest' })
  })
}

function toggleMenu() {
  showMenu.value = !showMenu.value
  if (showMenu.value) searchOpen.value = false
}

function handleScroll(event: Event) {
  const target = event.target as HTMLElement
  if (target.scrollTop <= 100 && historyStore.hasMore && !historyStore.isLoading) {
    void historyStore.loadMessages(groupId.value, true)
  }
}

const loadError = ref('')

async function loadConversation() {
  loadError.value = ''
  historyReady.value = false
  try {
    const [groupInfo, groupSettings, conversationInfo] = await Promise.all([
      groupService.getGroupInfo(groupId.value),
      // 群设置只影响管理入口等附加信息，读取失败不应让整个聊天窗口卡在“加载中”
      GroupSettingService.getGroupSetting(groupId.value).catch((error) => {
        console.error('获取群设置失败:', error)
        return null
      }),
      conversationService.getConversationById(groupId.value),
    ])
    currentGroup.value = groupInfo
    currentGroupSettings.value = groupSettings
    conversation.value = conversationInfo
    void loadMembers()
    await historyStore.init()
    await historyStore.loadInitialHistory(userStore.loggedInUser.user_id, groupId.value, true)
    loadedConversation.enter(groupId.value)
    await draftSync.reload()
    await nextTick()
    scrollToBottom()
    installMessageObserver()
    historyReady.value = true
    await consumePendingJump()
  } catch (error) {
    console.error('初始化群聊失败:', error)
    loadError.value = '会话加载失败，可能已无权访问或网络异常'
  }
}

function scrollToBottom() {
  const container = messageScrollRef.value
  if (container) container.scrollTop = container.scrollHeight
}

function handleKeyDown(event: KeyboardEvent) {
  if (mentionOpen.value && !event.isComposing) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      moveMention(event.key === 'ArrowDown' ? 1 : -1)
      return
    }
    if (event.key === 'Enter' || event.key === 'Tab') {
      event.preventDefault()
      const candidate = mentionOptions.value[mentionIndex.value]
      if (candidate) void selectMention(candidate)
      return
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      closeMention()
      return
    }
  }
  if (event.isComposing) return
  if (event.key === 'Escape') {
    if (edit.isEditing) cancelEdit()
    else historyStore.cancelReplyTarget()
    return
  }
  if (event.key === 'Enter' && !event.ctrlKey && !event.shiftKey) {
    event.preventDefault()
    void handleSendClick()
  }
}

async function handleEmojiSelect(emoji: string) {
  const element = inputElement()
  const start = element?.selectionStart ?? draft.value.length
  draft.value = `${draft.value.slice(0, start)}${emoji}${draft.value.slice(start)}`
  await nextTick()
  element?.focus()
  element?.setSelectionRange(start + emoji.length, start + emoji.length)
}

async function handleMediaUploaded(kind: ChatMediaKind, media: ChatMedia) {
  if (!groupId.value) return
  historyStore.sendMessage(groupId.value, groupId.value, kind, JSON.stringify(media), true)
  await nextTick()
  scrollToBottom()
}

/** 自定义表情：content 只需 emoji_id，其余字段用于本地乐观显示，服务端会按收藏记录重新填充 */
async function handleStickerSend(emoji: CustomEmoji) {
  if (!groupId.value) return
  historyStore.sendMessage(groupId.value, groupId.value, 'emoji', JSON.stringify(emoji), true)
  await nextTick()
  scrollToBottom()
}

function handleSaveEmoji(message: MessageResponse) {
  if (!groupId.value) return
  void emojiStore.saveFromMessage(groupId.value, message.message_id)
}

function restoreStashedMentions() {
  pickedMentions.clear()
  for (const [userId, name] of stashedMentions) pickedMentions.set(userId, name)
  stashedMentions = []
}

async function startEdit(message: MessageResponse) {
  if (!edit.isEditing) {
    await draftSync.flush()
    stashedMentions = [...pickedMentions.entries()]
  }
  edit.start(message)
  if (!edit.isEditing) {
    stashedMentions = []
    return
  }
  pickedMentions.clear()
  for (const [userId, label] of mentionLabelsForEdit(message.content, message.at_users, mentionNames.value)) {
    pickedMentions.set(userId, label)
  }
  mention.value = null
}

function cancelEdit() {
  edit.cancel()
  restoreStashedMentions()
  mention.value = null
}

async function handleSendClick() {
  if (edit.isEditing) {
    const atUsers = collectAtUsers(draft.value.trim(), pickedMentions) ?? []
    if (await edit.save(atUsers)) {
      mention.value = null
    }
    return
  }
  const content = draft.value.trim()
  if (!content) return
  const atUsers = collectAtUsers(content, pickedMentions)
  historyStore.sendMessage(groupId.value, groupId.value, 'text', content, true, atUsers)
  draft.value = ''
  pickedMentions.clear()
  stashedMentions = []
  mention.value = null
  await draftSync.flush()
  await nextTick()
  scrollToBottom()
}

function handleRetry(message: MessageResponse) {
  historyStore.retryMessage(message, true, groupId.value)
}

/** “重新编辑”：把撤回的原文放回输入框（输入框已有内容时接在后面），原来 @ 的成员仍然有效 */
async function handleReedit(message: MessageResponse) {
  const original = recall.original(message)
  if (!original) return
  if (edit.isEditing) cancelEdit()
  if (!draft.value.trim()) pickedMentions.clear()
  for (const [userId, label] of mentionLabelsForEdit(original.content, original.atUsers, mentionNames.value)) {
    pickedMentions.set(userId, label)
  }
  draft.value = draft.value.trim() ? `${draft.value}${original.content}` : original.content
  mention.value = null
  await nextTick()
  const element = inputElement()
  element?.focus()
  element?.setSelectionRange(draft.value.length, draft.value.length)
}

function handleReply(message: MessageResponse) {
  if (edit.isEditing) cancelEdit()
  if (historyStore.selectReplyTarget(message)) messageInputRef.value?.focus()
}

/** 跳到会话里的某条消息并高亮：不在已加载列表中时从本地缓存或服务端上下文加载 */
async function navigateToMessage(messageId: string, failText: string, highlightMs = 2000): Promise<boolean> {
  const container = messageScrollRef.value
  const previousScrollTop = container?.scrollTop
  try {
    const target = await historyStore.jumpToMessage(groupId.value, messageId)
    if (!target) throw new Error('message unavailable')
    await nextTick()
    if (!container || !focusMessageElement(container, target.message_id, highlightMs)) {
      throw new Error('message element unavailable')
    }
    return true
  } catch (error) {
    if (container && previousScrollTop !== undefined) container.scrollTop = previousScrollTop
    toast.error(failText)
    return false
  }
}

async function handleReplyNavigate(reference: ReplyReference) {
  await navigateToMessage(reference.message_id, '原消息不可用')
}

function toggleSearch() {
  searchOpen.value = !searchOpen.value
  if (searchOpen.value) showMenu.value = false
}

/** 会话内搜索结果：关闭面板后跳到该消息 */
async function handleSearchSelect(item: MessageSearchItem) {
  searchOpen.value = false
  await navigateToMessage(item.message.message_id, '该消息已不可用', 3000)
}

/** 全局搜索结果打开的地址带 ?msg=：首屏历史加载后跳到该消息，然后去掉参数（刷新不再重复跳转） */
const pendingJumpId = computed(() => typeof route.query.msg === 'string' ? route.query.msg : '')
async function consumePendingJump() {
  const messageId = pendingJumpId.value
  if (!messageId || !historyReady.value) return
  await router.replace({ path: route.path, query: {} })
  // 首屏加载后“滚到底部”还有一次 120ms 的补滚（useStickToBottom），等它结束再定位，避免被拉回底部
  await new Promise(resolve => setTimeout(resolve, 200))
  await navigateToMessage(messageId, '该消息已不可用', 3000)
}
watch(pendingJumpId, id => {
  if (id) void consumePendingJump()
})

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

watch(groupId, (nextId, previousId) => {
  if (previousId && nextId !== previousId) {
    forward.cancelSelect()
    edit.reset()
    searchOpen.value = false
    draft.value = ''
    pickedMentions.clear()
    mention.value = null
    groupMembers.value = []
    void loadConversation()
  }
})

watch(() => historyStore.failedReplyDraft, async draftItem => {
  if (!draftItem || draftItem.conversationId !== groupId.value) return
  await nextTick()
  if (draft.value.trim()) return
  draft.value = draftItem.content
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
  // 卸载时路由已经切走，groupId 可能已经是空的：用实际加载的会话
  const leftId = loadedConversation.current() || groupId.value
  if (leftId && historyStore.replyTarget?.conversationId === leftId) {
    historyStore.cancelReplyTarget()
  }
  loadedConversation.leave(groupId.value)
})
</script>

<template>
  <div v-if="currentGroup && conversation" class="flex flex-col h-full">
    <div class="flex items-center justify-between w-full p-4 border-b relative">
      <div class="min-w-0">
        <div class="text-lg font-semibold truncate">{{ currentGroup.name }}</div>
        <div class="text-xs text-gray-500" data-testid="group-online-count">{{ onlineCount }} 人在线</div>
      </div>
      <div class="flex items-center gap-2">
        <button
          type="button"
          data-testid="open-conversation-search"
          aria-label="搜索聊天记录"
          :aria-pressed="searchOpen"
          :class="['flex h-8 w-8 items-center justify-center rounded-full hover:bg-gray-100', searchOpen ? 'bg-gray-100 text-gray-900' : 'text-gray-600']"
          @click="toggleSearch"
        >
          <Search class="w-5 h-5" aria-hidden="true" />
        </button>
        <Button variant="ghost" size="sm" @click="historyStore.markConversationReadToLatest(conversation.conversation_id)">
          全部标为已读
        </Button>
        <button ref="menuButtonRef" @click="toggleMenu"><Ellipsis class="w-5 h-5" /></button>
      </div>
      <ConversationSearchPanel
        :open="searchOpen"
        :conversation-id="conversation.conversation_id"
        @close="searchOpen = false"
        @select="handleSearchSelect"
      />
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

    <GroupAnnouncementBar :group-id="groupId" :my-role="currentGroup.my_role" />

    <div ref="messageScrollRef" data-testid="message-scroll" class="flex-1 overflow-y-auto p-4" @scroll="handleScroll">
      <div v-if="historyStore.noMoreInfo" class="flex justify-center py-2 text-sm text-gray-500">没有更多信息</div>
      <div v-if="historyStore.groupMessages.length > 0" ref="messageListRef" class="space-y-4">
        <div
          v-for="message in historyStore.groupMessages"
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
          <RecallNotice
            v-if="isRecalledMessage(message)"
            :is-self="isOwn(message)"
            :sender-name="message.sender_info?.nickname || message.sender_info?.username"
            :timestamp="message.timestamp"
            :can-reedit="isOwn(message) && !!recall.original(message)"
            @reedit="handleReedit(message)"
          />
          <template v-else>
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
                <UserTextArea
                  :message="message.content"
                  :message-type="message.message_type"
                  :is-self="false"
                  :at-users="message.at_users"
                  :mention-names="mentionNames"
                  :current-user-id="currentUser.user_id"
                  :can-reply="!message.is_recalled && String(message.status) !== '3'"
                  :recalled="message.is_recalled || String(message.status) === '3'"
                  :reply-to="message.reply_to"
                  :forwarded-from="message.forwarded_from"
                  :can-forward="!forward.selecting && isForwardable(message)"
                  :can-select="!forward.selecting"
                  :edited="message.edited"
                  :edited-at="message.edited_at"
                  @forward="forward.forwardOne(message)"
                  @select="forward.startSelect(message)"
                  @reply="handleReply(message)"
                  @navigate="handleReplyNavigate"
                  @save-emoji="handleSaveEmoji(message)"
                />
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
              <UserTextArea
                :message="message.content"
                :message-type="message.message_type"
                :is-self="true"
                :at-users="message.at_users"
                :mention-names="mentionNames"
                :current-user-id="currentUser.user_id"
                :can-reply="!message.is_recalled && String(message.status) !== '3'"
                :recalled="message.is_recalled || String(message.status) === '3'"
                :reply-to="message.reply_to"
                :forwarded-from="message.forwarded_from"
                :can-forward="!forward.selecting && isForwardable(message)"
                :can-select="!forward.selecting"
                :edited="message.edited"
                :edited-at="message.edited_at"
                :edit-until="forward.selecting ? 0 : edit.editUntil(message)"
                :recall-until="forward.selecting ? 0 : recall.recallUntil(message)"
                @edit="startEdit(message)"
                @recall="recall.request(message)"
                @forward="forward.forwardOne(message)"
                @select="forward.startSelect(message)"
                @reply="handleReply(message)"
                @navigate="handleReplyNavigate"
                @save-emoji="handleSaveEmoji(message)"
              />
              <img :src="currentUser.avatar_url" :alt="currentUser.username" class="w-10 h-10 rounded-full ml-2 object-cover" />
            </div>
          </div>
          </template>
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
      @cancel="cancelEdit()"
    />
    <ReplyComposerBar
      v-else-if="!forward.selecting && historyStore.replyTarget?.conversationId === groupId"
      :reference="historyStore.replyTarget.reference"
      @cancel="historyStore.cancelReplyTarget()"
    />
    <div v-show="!forward.selecting" class="relative h-1/4 border-t">
      <div
        v-if="mentionOpen"
        ref="mentionListRef"
        data-testid="mention-picker"
        role="listbox"
        aria-label="选择要提醒的成员"
        class="absolute bottom-full left-2 right-2 z-40 mb-1 max-h-60 overflow-y-auto rounded-lg border bg-white p-1 shadow-lg sm:right-auto sm:w-72"
      >
        <div class="px-2 py-1 text-xs text-gray-400">选择要提醒的人</div>
        <button
          v-for="(candidate, index) in mentionOptions"
          :key="candidate.user_id"
          type="button"
          role="option"
          data-testid="mention-option"
          :data-user-id="candidate.user_id"
          :data-index="index"
          :aria-selected="index === mentionIndex"
          :class="[
            'flex w-full items-center gap-2 rounded-md px-2 py-2 text-left text-sm',
            index === mentionIndex ? 'bg-blue-50 text-blue-700' : 'hover:bg-gray-50',
          ]"
          @pointerdown.prevent
          @mouseenter="mentionIndex = index"
          @click="selectMention(candidate)"
        >
          <span
            v-if="candidate.user_id === 'all'"
            class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-amber-100 text-xs font-semibold text-amber-700"
          >@</span>
          <img
            v-else
            :src="candidate.avatar_url || '/images/default-avatar.png'"
            alt=""
            class="h-7 w-7 shrink-0 rounded-full object-cover"
          />
          <span class="min-w-0 flex-1 truncate">
            {{ candidate.label }}
            <span v-if="candidate.remark && candidate.remark !== candidate.label" class="text-xs text-gray-400">（{{ candidate.remark }}）</span>
          </span>
          <span v-if="candidate.username && candidate.username !== candidate.label" class="shrink-0 text-xs text-gray-400">{{ candidate.username }}</span>
        </button>
      </div>
      <Textarea
        ref="messageInputRef"
        v-model="draft"
        data-testid="message-input"
        class="h-full w-full resize-none pr-32 rounded-none focus:ring-0 focus:shadow-none whitespace-pre-wrap"
        style="outline: none; box-shadow: none; font-size: 24px; word-break: break-all"
        placeholder="输入消息，@ 提醒成员..."
        @keydown="handleKeyDown"
        @input="updateMentionQuery"
        @click="updateMentionQuery"
        @keyup="(event: KeyboardEvent) => { if (event.key === 'ArrowLeft' || event.key === 'ArrowRight' || event.key === 'Home' || event.key === 'End') updateMentionQuery() }"
        @blur="closeMentionSoon"
      />
      <button
        type="button"
        data-testid="mention-button"
        aria-label="提醒成员"
        title="@ 提醒成员"
        class="absolute bottom-4 right-24 flex h-9 w-9 items-center justify-center rounded-full text-lg text-gray-500 hover:bg-gray-100"
        @pointerdown.prevent
        @click="openMentionPicker"
      >@</button>
      <Button class="absolute bottom-4 right-4" data-testid="send-button" :disabled="edit.saving" @click="handleSendClick">{{ edit.isEditing ? '保存' : '发送' }}</Button>
    </div>
    <RecallConfirmDialog
      :open="recall.confirmOpen"
      :busy="recall.busy"
      @cancel="recall.cancel()"
      @confirm="recall.confirm()"
    />
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
