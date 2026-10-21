import type { Conversation, MessagePreviewResponse } from '@/type/Conversation'
import type { MessageResponse } from '@/type/message'
import { useListStore } from '@/stores/list'
import { useUserStore } from '@/stores/user'
import { conversationService } from '@/services/conversation.service'
import { parseTimestamp, toServerTimestamp } from '@/utils/conversation-time'
import { compareSeqId, maxSeqId } from '@/utils/seq-id'

/**
 * 会话列表实时更新：收发消息、ACK、撤回、已读时立即修改侧栏（预览、时间、未读角标），
 * 必要时再用 GET /conversations/{id} 拉一次服务端权威值（新会话、撤回、已读后的未读数）。
 */

export const RECALLED_PREVIEW = '[消息已撤回]'

const refreshTimers = new Map<string, ReturnType<typeof setTimeout>>()
const countedMessageIds = new Set<string>()
/** 本页会话里本地加过未读、且可能还没被服务端快照覆盖的消息 */
const unreadExtras = new Map<string, { conversationId: string, seq: string }>()
/** 已处理过未读扣减的撤回（同一撤回可能先后收到 REST 结果与推送） */
const recalledMessageIds = new Set<string>()
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
    case 'chat_record': return '[聊天记录]'
    default: return '[消息]'
  }
}

function rememberCounted(messageId: string): boolean {
  if (countedMessageIds.has(messageId)) return false
  countedMessageIds.add(messageId)
  if (countedMessageIds.size > MAX_COUNTED) {
    const first = countedMessageIds.values().next().value
    if (first) {
      countedMessageIds.delete(first)
      unreadExtras.delete(first)
    }
  }
  return true
}

/** 上线补齐里的消息已经体现在服务端未读数里，实时帧再到达时不要再加一次 */
export function noteCountedMessages(messageIds: Iterable<string | undefined>): void {
  for (const messageId of messageIds) {
    if (messageId) rememberCounted(messageId)
  }
}

function extraUnread(conversationId: string, afterSeq: string): number {
  let count = 0
  for (const item of unreadExtras.values()) {
    if (item.conversationId === conversationId && compareSeqId(item.seq, afterSeq) > 0) count++
  }
  return count
}

function clearExtras(conversationId: string): void {
  for (const [messageId, item] of unreadExtras) {
    if (item.conversationId === conversationId) unreadExtras.delete(messageId)
  }
}

function bumpLatest(conversation: Conversation, seqId?: string): void {
  if (!seqId || seqId === '0') return
  conversation.latest_seq = maxSeqId(conversation.latest_seq, seqId)
}

/** 纯函数：把一条消息应用到会话（不负责排序和网络） */
export function applyMessageToConversation(
  conversation: Conversation,
  message: MessageResponse,
  options: { countUnread: boolean },
): void {
  const timestamp = toServerTimestamp(message.timestamp || Date.now())
  const current = conversation.last_message
  bumpLatest(conversation, message.seq_id)
  // 会话列表里这条已经是最后一条（例如登录后、连接前它就已经在快照里）：重放不再加未读
  const countUnread = options.countUnread && current?.message_id !== message.message_id
  // 乱序到达的旧消息不覆盖更新的预览
  if (current && current.message_id !== message.message_id
      && parseTimestamp(current.timestamp) > parseTimestamp(timestamp)) {
    if (countUnread) {
      conversation.unread_count = (conversation.unread_count || 0) + 1
      conversation.notreadednumber = conversation.unread_count
    }
    return
  }
  const preview: MessagePreviewResponse = {
    message_id: message.message_id,
    sender_id: message.sender_info?.user_id || null,
    sender_nickname: message.sender_info?.nickname || message.sender_info?.username,
    content_preview: previewOfMessage(message),
    message_type: message.message_type,
    timestamp,
    is_recalled: false,
  }
  conversation.last_message = preview
  conversation.last_activity_time = timestamp
  if (countUnread) conversation.unread_count = (conversation.unread_count || 0) + 1
  conversation.notreadednumber = conversation.unread_count || 0
}

function findConversation(conversationId: string): Conversation | undefined {
  return useListStore().conversations?.find(c => c.conversation_id === conversationId)
}

export function isViewingConversation(conversationId: string, activeConversationId: string | null): boolean {
  if (!activeConversationId || activeConversationId !== conversationId) return false
  return typeof document === 'undefined' || document.visibilityState !== 'hidden'
}

/** 该消息是否 @ 了 userId（帧上的 at_me 优先，否则看 at_users） */
export function mentionsUser(message: MessageResponse, userId: string | undefined, atMe?: boolean): boolean {
  if (atMe !== undefined && atMe) return true
  if (!userId || message.is_recalled || String(message.status) === '3') return false
  return Array.isArray(message.at_users) && message.at_users.includes(userId)
}

/** 收到或发出一条消息；atMe 来自 GROUP_MESSAGE_RESPONSE 帧顶层字段 */
export function onConversationMessage(
  message: MessageResponse,
  activeConversationId: string | null,
  atMe?: boolean,
): void {
  if (!message?.conversation_id) return
  const me = useUserStore().loggedInUser?.user_id
  const fromOther = !!message.sender_info?.user_id && message.sender_info.user_id !== me
  const viewing = isViewingConversation(message.conversation_id, activeConversationId)
  const firstTime = !!message.message_id && rememberCounted(message.message_id)
  const countUnread = fromOther && !viewing && firstTime
  const conversation = findConversation(message.conversation_id)
  if (!conversation) {
    // 列表里还没有（第一次收到该会话的消息，或之前删除过会话）：向服务端取权威记录（含 at_me）
    scheduleConversationRefresh(message.conversation_id, 0)
    return
  }
  const replay = conversation.last_message?.message_id === message.message_id
  const countThis = countUnread && !replay
  if (countThis && message.message_id) {
    unreadExtras.set(message.message_id, { conversationId: message.conversation_id, seq: message.seq_id || '0' })
  }
  applyMessageToConversation(conversation, message, { countUnread: countThis })
  if (countThis && mentionsUser(message, me, atMe)) {
    // “[有人@我]”：直到读到该消息（已读推进后服务端清除，本地在 onConversationRead 清除）
    conversation.at_me = true
    conversation.at_me_count = (conversation.at_me_count || 0) + 1
    conversation.at_me_message_id = conversation.at_me_message_id || message.message_id
  }
}

/** 自己发出的消息收到 ACK：把预览里的本地 ID 换成服务端 ID，方便之后撤回时匹配 */
export function onConversationAck(conversationId: string, clientMessageId: string, messageId: string): void {
  const conversation = findConversation(conversationId)
  if (conversation?.last_message && conversation.last_message.message_id === `local_${clientMessageId}`) {
    conversation.last_message.message_id = messageId
  }
}

/** 消息被编辑：如果它是会话的最后一条，预览换成新内容（不改时间、不重新排序） */
export function onConversationEdit(conversationId: string, message: MessageResponse): void {
  const conversation = findConversation(conversationId)
  if (conversation?.last_message?.message_id === message.message_id && !conversation.last_message.is_recalled) {
    conversation.last_message = {
      ...conversation.last_message,
      content_preview: previewOfMessage(message),
    }
  }
}

export function onConversationRecall(
  conversationId: string,
  messageId: string,
  options: { recallUserId?: string; seqId?: string; activeConversationId?: string | null; adjustUnread?: boolean } = {},
): void {
  const conversation = findConversation(conversationId)
  if (conversation?.last_message?.message_id === messageId) {
    conversation.last_message = {
      ...conversation.last_message,
      // 只能撤回自己发的消息，撤回者就是发送者
      sender_id: conversation.last_message.sender_id || options.recallUserId || null,
      content_preview: RECALLED_PREVIEW,
      message_type: 'recalled',
      is_recalled: true,
    }
  }
  // 别人撤回了我还没读到的消息：未读数立即减一（稍后以服务端值校正）
  const me = useUserStore().loggedInUser?.user_id
  const firstRecall = !recalledMessageIds.has(messageId)
  recalledMessageIds.add(messageId)
  if (recalledMessageIds.size > MAX_COUNTED) {
    const first = recalledMessageIds.values().next().value
    if (first) recalledMessageIds.delete(first)
  }
  const adjustUnread = options.adjustUnread !== false
  if (adjustUnread && firstRecall && conversation && options.recallUserId && options.recallUserId !== me
      && !isViewingConversation(conversationId, options.activeConversationId ?? null)
      && (conversation.unread_count || 0) > 0
      && (!options.seqId || !conversation.read_seq || compareSeqId(options.seqId, conversation.read_seq) > 0)) {
    conversation.unread_count = (conversation.unread_count || 0) - 1
    conversation.notreadednumber = conversation.unread_count
    if (conversation.at_me_message_id === messageId) clearMention(conversation)
  }
  if (adjustUnread) scheduleConversationRefresh(conversationId, 600)
}

/** 当前用户把会话读到 remaining 条未读（通常为 0），稍后用服务端值校正 */
export function onConversationRead(conversationId: string, remaining = 0): void {
  const conversation = findConversation(conversationId)
  if (conversation) {
    conversation.unread_count = Math.max(0, remaining)
    conversation.notreadednumber = conversation.unread_count
    if (conversation.unread_count === 0) clearMention(conversation)
  }
  scheduleConversationRefresh(conversationId, 800)
}

export function clearMention(conversation: Conversation): void {
  conversation.at_me = false
  conversation.at_me_count = 0
  conversation.at_me_message_id = undefined
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

/**
 * 用上线补齐快照覆盖会话列表的未读数和最新预览。
 * 快照之后才到达的实时消息（seq 更大）保留本地加过的未读，避免被旧快照盖掉。
 * 正在查看的会话不加未读，和实时消息的口径一致。
 * 草稿摘要跟着会话快照走（与 GET /conversations 的 draft_preview 相同），和未读、消息 seq 无关。
 * 在线状态不在这帧里，连接成功后由 presence store 自己补拉。
 */
export function applyOfflineConversations(
  incoming: Conversation[],
  activeConversationId: string | null,
): void {
  const listStore = useListStore()
  for (const fresh of incoming) {
    if (!fresh?.conversation_id) continue
    const patch = { ...fresh, notreadednumber: fresh.unread_count ?? 0 }
    const existing = findConversation(fresh.conversation_id)
    if (!existing) {
      listStore.conversations = [patch, ...(listStore.conversations || [])]
      continue
    }
    existing.is_muted = fresh.is_muted
    existing.is_pinned = fresh.is_pinned
    if (Object.prototype.hasOwnProperty.call(fresh, 'draft_preview')) {
      existing.draft_preview = fresh.draft_preview || null
    }
    if (isViewingConversation(fresh.conversation_id, activeConversationId)) {
      if (compareSeqId(fresh.latest_seq, existing.latest_seq) >= 0) {
        existing.last_message = fresh.last_message
        existing.last_activity_time = fresh.last_activity_time || existing.last_activity_time
        existing.latest_seq = fresh.latest_seq || existing.latest_seq
        existing.read_seq = fresh.read_seq
        existing.delivered_seq = fresh.delivered_seq
      }
      continue
    }
    const serverLatest = fresh.latest_seq || '0'
    if (compareSeqId(existing.latest_seq, serverLatest) > 0) {
      const unread = (fresh.unread_count ?? 0) + extraUnread(fresh.conversation_id, serverLatest)
      existing.unread_count = unread
      existing.notreadednumber = unread
      continue
    }
    Object.assign(existing, patch)
    clearExtras(fresh.conversation_id)
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
  unreadExtras.clear()
  recalledMessageIds.clear()
}
