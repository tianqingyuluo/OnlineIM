import type { Conversation, MessagePreviewResponse } from '@/type/Conversation'
import type { MessageResponse } from '@/type/message'
import { useListStore } from '@/stores/list'
import { useUserStore } from '@/stores/user'
import { conversationService } from '@/services/conversation.service'
import { parseTimestamp, toServerTimestamp } from '@/utils/conversation-time'

/**
 * 会话列表实时更新：收发消息、ACK、撤回、已读时立即修改侧栏（预览、时间、未读角标），
 * 必要时再用 GET /conversations/{id} 拉一次服务端权威值（新会话、撤回、已读后的未读数）。
 */

export const RECALLED_PREVIEW = '[消息已撤回]'

const refreshTimers = new Map<string, ReturnType<typeof setTimeout>>()
const countedMessageIds = new Set<string>()
const MAX_COUNTED = 2000

const PREVIEW_MAX_LENGTH = 100

/** 与服务端 ConversationRecordService.preview 保持一致，刷新前后显示相同 */
export function previewOfMessage(message: MessageResponse): string {
  if (message.is_recalled || String(message.status) === '3') return RECALLED_PREVIEW
  switch ((message.message_type || 'text').toLowerCase()) {
    case 'text':
    case '': {
      const text = typeof message.content === 'string' ? message.content : ''
      return text.length > PREVIEW_MAX_LENGTH ? text.slice(0, PREVIEW_MAX_LENGTH) : text
    }
    case 'image': return '[图片]'
    case 'file': return '[文件]'
    case 'voice':
    case 'audio': return '[语音]'
    case 'video': return '[视频]'
    case 'emoji':
    case 'sticker': return '[表情]'
    default: return '[消息]'
  }
}

function rememberCounted(messageId: string): boolean {
  if (countedMessageIds.has(messageId)) return false
  countedMessageIds.add(messageId)
  if (countedMessageIds.size > MAX_COUNTED) {
    const first = countedMessageIds.values().next().value
    if (first) countedMessageIds.delete(first)
  }
  return true
}

/** 纯函数：把一条消息应用到会话（不负责排序和网络） */
export function applyMessageToConversation(
  conversation: Conversation,
  message: MessageResponse,
  options: { countUnread: boolean },
): void {
  const timestamp = toServerTimestamp(message.timestamp || Date.now())
  const current = conversation.last_message
  // 乱序到达的旧消息不覆盖更新的预览
  if (current && current.message_id !== message.message_id
      && parseTimestamp(current.timestamp) > parseTimestamp(timestamp)) {
    if (options.countUnread) conversation.unread_count = (conversation.unread_count || 0) + 1
    return
  }
  const preview: MessagePreviewResponse = {
    message_id: message.message_id,
    sender_nickname: message.sender_info?.nickname || message.sender_info?.username,
    content_preview: previewOfMessage(message),
    message_type: message.message_type,
    timestamp,
    is_recalled: false,
  }
  conversation.last_message = preview
  conversation.last_activity_time = timestamp
  if (options.countUnread) conversation.unread_count = (conversation.unread_count || 0) + 1
  conversation.notreadednumber = conversation.unread_count || 0
}

function findConversation(conversationId: string): Conversation | undefined {
  return useListStore().conversations?.find(c => c.conversation_id === conversationId)
}

export function isViewingConversation(conversationId: string, activeConversationId: string | null): boolean {
  if (!activeConversationId || activeConversationId !== conversationId) return false
  return typeof document === 'undefined' || document.visibilityState !== 'hidden'
}

/** 收到或发出一条消息 */
export function onConversationMessage(message: MessageResponse, activeConversationId: string | null): void {
  if (!message?.conversation_id) return
  const me = useUserStore().loggedInUser?.user_id
  const fromOther = !!message.sender_info?.user_id && message.sender_info.user_id !== me
  const countUnread = fromOther
    && !isViewingConversation(message.conversation_id, activeConversationId)
    && !!message.message_id
    && rememberCounted(message.message_id)
  const conversation = findConversation(message.conversation_id)
  if (!conversation) {
    // 列表里还没有（第一次收到该会话的消息，或之前删除过会话）：向服务端取权威记录
    scheduleConversationRefresh(message.conversation_id, 0)
    return
  }
  applyMessageToConversation(conversation, message, { countUnread })
}

/** 自己发出的消息收到 ACK：把预览里的本地 ID 换成服务端 ID，方便之后撤回时匹配 */
export function onConversationAck(conversationId: string, clientMessageId: string, messageId: string): void {
  const conversation = findConversation(conversationId)
  if (conversation?.last_message && conversation.last_message.message_id === `local_${clientMessageId}`) {
    conversation.last_message.message_id = messageId
  }
}

export function onConversationRecall(conversationId: string, messageId: string): void {
  const conversation = findConversation(conversationId)
  if (conversation?.last_message?.message_id === messageId) {
    conversation.last_message = {
      ...conversation.last_message,
      content_preview: RECALLED_PREVIEW,
      is_recalled: true,
    }
  }
  // 撤回可能影响未读数，以服务端为准
  scheduleConversationRefresh(conversationId, 600)
}

/** 当前用户把会话读到 remaining 条未读（通常为 0），稍后用服务端值校正 */
export function onConversationRead(conversationId: string, remaining = 0): void {
  const conversation = findConversation(conversationId)
  if (conversation) {
    conversation.unread_count = Math.max(0, remaining)
    conversation.notreadednumber = conversation.unread_count
  }
  scheduleConversationRefresh(conversationId, 800)
}

export function scheduleConversationRefresh(conversationId: string, delay = 500): void {
  const existing = refreshTimers.get(conversationId)
  if (existing) clearTimeout(existing)
  refreshTimers.set(conversationId, setTimeout(() => {
    refreshTimers.delete(conversationId)
    void refreshConversation(conversationId)
  }, delay))
}

export async function refreshConversation(conversationId: string): Promise<void> {
  try {
    const fresh = await conversationService.getConversationById(conversationId)
    if (!fresh?.conversation_id) return
    const listStore = useListStore()
    const existing = findConversation(conversationId)
    const patch = { ...fresh, notreadednumber: fresh.unread_count ?? 0 }
    if (existing) Object.assign(existing, patch)
    else listStore.conversations = [patch, ...(listStore.conversations || [])]
  } catch (error) {
    // 404：会话已不可见（被移出群等），从列表移除
    if ((error as { status?: number })?.status === 404) {
      const listStore = useListStore()
      listStore.conversations = (listStore.conversations || []).filter(c => c.conversation_id !== conversationId)
    } else {
      console.error('刷新会话失败:', error)
    }
  }
}

/** 侧栏排序：按最近活动时间倒序（置顶分组由调用方单独处理） */
export function sortByActivity(conversations: Conversation[]): Conversation[] {
  return [...conversations].sort((a, b) =>
    parseTimestamp(b.last_activity_time || b.last_message?.timestamp)
    - parseTimestamp(a.last_activity_time || a.last_message?.timestamp))
}

/** 测试用 */
export function resetConversationLiveState(): void {
  refreshTimers.forEach(timer => clearTimeout(timer))
  refreshTimers.clear()
  countedMessageIds.clear()
}
