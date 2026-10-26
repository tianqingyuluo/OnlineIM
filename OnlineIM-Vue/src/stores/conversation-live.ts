import type { Conversation, MessagePreviewResponse } from '@/type/Conversation'
import type { MessageResponse } from '@/type/message'
import { useListStore } from '@/stores/list'
import { useUserStore } from '@/stores/user'
import { conversationService } from '@/services/conversation.service'
import { parseTimestamp, toServerTimestamp } from '@/utils/conversation-time'
import { compareSeqId, maxSeqId } from '@/utils/seq-id'
import { isRecalledMessage } from '@/utils/message-recall'
import { callPreviewText } from '@/utils/call-message'
import { ENCRYPTED_PREVIEW, looksLikeEnvelope } from '@/utils/e2ee-message'

/**
 * 会话列表实时更新：收发消息、ACK、撤回、已读时立即修改侧栏（预览、时间、未读角标），
 * 必要时再用 GET /conversations/{id} 拉一次服务端权威值（新会话、撤回、已读后的未读数）。
 */

export const RECALLED_PREVIEW = '[消息已撤回]'

const refreshTimers = new Map<string, ReturnType<typeof setTimeout>>()
const countedMessageIds = new Set<string>()
/** 本页会话里本地加过未读、且可能还没被服务端快照覆盖的消息 */
const unreadExtras = new Map<string, { conversationId: string, seq: string, atMe: boolean, atAll: boolean, atPersonal: boolean }>()
/** 已处理过未读扣减的撤回（同一撤回可能先后收到 REST 结果与推送） */
const recalledMessageIds = new Set<string>()
const MAX_COUNTED = 2000

const PREVIEW_MAX_LENGTH = 100

/** 与服务端 ConversationRecordService.preview 保持一致，刷新前后显示相同 */
export function previewOfMessage(message: MessageResponse): string {
  if (message.is_recalled || String(message.status) === '3') return RECALLED_PREVIEW
  const content = typeof message.content === 'string' ? message.content : ''
  const stillCipher = looksLikeEnvelope(content)
    || (message.is_encrypted === true && message.e2ee_status !== 'decrypted')
    || message.e2ee_status === 'locked'
  if (stillCipher) return ENCRYPTED_PREVIEW
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
    case 'call': return callPreviewText(typeof message.content === 'string' ? message.content : '')
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

function extraMentions(conversationId: string, afterSeq: string): { atMe: number, atAll: number, atPersonal: number } {
  let atMe = 0
  let atAll = 0
  let atPersonal = 0
  for (const item of unreadExtras.values()) {
    if (item.conversationId !== conversationId || compareSeqId(item.seq, afterSeq) <= 0) continue
    if (item.atMe) atMe++
    if (item.atAll) atAll++
    if (item.atPersonal) atPersonal++
  }
  return { atMe, atAll, atPersonal }
}

function clearExtras(conversationId: string): void {
  for (const [messageId, item] of unreadExtras) {
    if (item.conversationId === conversationId) unreadExtras.delete(messageId)
  }
}

function displaySenderName(info?: MessageResponse['sender_info'] | null): string | undefined {
  const nickname = info?.nickname?.trim()
  if (nickname) return nickname
  const username = info?.username?.trim()
  if (username) return username
  const userId = info?.user_id?.trim()
  return userId || undefined
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
    sender_nickname: displaySenderName(message.sender_info),
    content_preview: previewOfMessage(message),
    message_type: message.message_type,
    timestamp,
    is_recalled: isRecalledMessage(message),
  }
  conversation.last_message = preview
  conversation.last_activity_time = timestamp
  if (countUnread) conversation.unread_count = (conversation.unread_count || 0) + 1
  conversation.notreadednumber = conversation.unread_count || 0
}

function findConversation(conversationId: string): Conversation | undefined {
  return useListStore().conversations?.find(c => c.conversation_id === conversationId)
}

/** 列表里该会话当前是否带着 [有人@我] / [@所有人] */
export function hasMentionMark(conversationId: string): boolean {
  const conversation = findConversation(conversationId)
  return !!conversation && (!!conversation.at_me || !!conversation.at_all || !!conversation.at_personal)
}

export function isViewingConversation(conversationId: string, activeConversationId: string | null): boolean {
  if (!activeConversationId || activeConversationId !== conversationId) return false
  return typeof document === 'undefined' || document.visibilityState !== 'hidden'
}

/** 该消息是否 @所有人（帧上的 at_all，或 at_users 含保留值 all） */
export function mentionsAll(message: MessageResponse, atAll?: boolean): boolean {
  if (atAll === true) return true
  if (message.is_recalled || String(message.status) === '3') return false
  return Array.isArray(message.at_users) && message.at_users.includes('all')
}

/** at_users 里有这位用户自己的 ID（不含仅 @所有人） */
export function mentionsPersonally(message: MessageResponse, userId: string | undefined): boolean {
  if (!userId || message.is_recalled || String(message.status) === '3') return false
  return Array.isArray(message.at_users) && message.at_users.includes(userId)
}

/** 该消息是否 @ 了 userId（帧上的 at_me / at_all 优先，否则看 at_users，含 @所有人） */
export function mentionsUser(message: MessageResponse, userId: string | undefined, atMe?: boolean, atAll?: boolean): boolean {
  if (atMe === true || atAll === true) return true
  if (!userId || message.is_recalled || String(message.status) === '3') return false
  return Array.isArray(message.at_users) && (message.at_users.includes(userId) || message.at_users.includes('all'))
}

/** 收到或发出一条消息；atMe 来自 GROUP_MESSAGE_RESPONSE 帧顶层字段 */
export function onConversationMessage(
  message: MessageResponse,
  activeConversationId: string | null,
  atMe?: boolean,
  atAll?: boolean,
): void {
  if (!message?.conversation_id) return
  const me = useUserStore().loggedInUser?.user_id
  if (message.message_id && recalledMessageIds.has(message.message_id)) return
  const fromOther = !!message.sender_info?.user_id && message.sender_info.user_id !== me
  const viewing = isViewingConversation(message.conversation_id, activeConversationId)
  const firstTime = !!message.message_id && rememberCounted(message.message_id)
  const alreadyRecalled = isRecalledMessage(message)
  const countUnread = fromOther && !viewing && firstTime && !alreadyRecalled
  const conversation = findConversation(message.conversation_id)
  if (!conversation) {
    // 列表里还没有（第一次收到该会话的消息，或之前删除过会话）：向服务端取权威记录（含 at_me）
    scheduleConversationRefresh(message.conversation_id, 0)
    return
  }
  const replay = conversation.last_message?.message_id === message.message_id
  const countThis = countUnread && !replay
  const mentioned = mentionsUser(message, me, atMe, atAll)
  const mentionedAll = mentionsAll(message, atAll)
  const mentionedPersonally = mentionsPersonally(message, me)
  if (countThis && message.message_id) {
    unreadExtras.set(message.message_id, {
      conversationId: message.conversation_id,
      seq: message.seq_id || '0',
      atMe: mentioned,
      atAll: mentionedAll,
      atPersonal: mentionedPersonally,
    })
  }
  if (alreadyRecalled && message.message_id) recalledMessageIds.add(message.message_id)
  applyMessageToConversation(conversation, message, { countUnread: countThis })
  if (countThis && mentioned) {
    // “[有人@我] / [@所有人]”：直到读到该消息（已读推进后服务端清除，本地在 onConversationRead 清除）
    conversation.at_me = true
    conversation.at_me_count = (conversation.at_me_count || 0) + 1
    conversation.at_me_message_id = conversation.at_me_message_id || message.message_id
    if (mentionedPersonally) conversation.at_personal = true
    if (mentionsAll(message, atAll)) {
      conversation.at_all = true
      conversation.at_all_count = (conversation.at_all_count || 0) + 1
      conversation.at_all_message_id = conversation.at_all_message_id || message.message_id
    }
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
  options: {
    recallUserId?: string
    senderId?: string
    operatorRole?: string
    seqId?: string
    activeConversationId?: string | null
    adjustUnread?: boolean
  } = {},
): void {
  const conversation = findConversation(conversationId)
  if (conversation?.last_message?.message_id === messageId) {
    conversation.last_message = {
      ...conversation.last_message,
      // 发送者优先用帧上的 sender_id；没有时才退回撤回者（旧帧只能撤回自己的消息）
      sender_id: options.senderId || conversation.last_message.sender_id || options.recallUserId || null,
      recall_user_id: options.recallUserId || conversation.last_message.recall_user_id || null,
      recall_operator_role: options.operatorRole || conversation.last_message.recall_operator_role || null,
      content_preview: RECALLED_PREVIEW,
      message_type: 'recalled',
      is_recalled: true,
    }
  }
  if (conversation && options.seqId) bumpLatest(conversation, options.seqId)
  // 发送者还没被我读到的消息被撤回：未读立即减一（稍后以服务端值校正）。管理撤回时按发送者而不是撤回者。
  const me = useUserStore().loggedInUser?.user_id
  const firstRecall = !recalledMessageIds.has(messageId)
  recalledMessageIds.add(messageId)
  const mentionExtra = unreadExtras.get(messageId)
  // 整条删掉，避免离线快照把这条已撤回消息的未读和 @我 / @所有人 / @个人再加回来
  if (mentionExtra) unreadExtras.delete(messageId)
  if (recalledMessageIds.size > MAX_COUNTED) {
    const first = recalledMessageIds.values().next().value
    if (first) recalledMessageIds.delete(first)
  }
  const adjustUnread = options.adjustUnread !== false
  const senderId = options.senderId || conversation?.last_message?.sender_id || options.recallUserId
  const notYetRead = !!mentionExtra
      || !options.seqId
      || !conversation?.read_seq
      || compareSeqId(options.seqId, conversation.read_seq) > 0
  if (adjustUnread && firstRecall && conversation && senderId && senderId !== me
      && !isViewingConversation(conversationId, options.activeConversationId ?? null)
      && (conversation.unread_count || 0) > 0
      && notYetRead) {
    conversation.unread_count = (conversation.unread_count || 0) - 1
    conversation.notreadednumber = conversation.unread_count
    if (conversation.at_me_message_id === messageId || conversation.at_all_message_id === messageId) {
      clearMention(conversation)
    }
  }
  if (adjustUnread) scheduleConversationRefresh(conversationId, 600)
}

/** 自己的另一台设备把会话读到了 readSeq，这边的未读角标跟着清掉 */
export function onOwnDeviceRead(conversationId: string, readSeq: string): void {
  const conversation = findConversation(conversationId)
  if (conversation) {
    conversation.read_seq = maxSeqId(conversation.read_seq || '0', readSeq)
  }
  onConversationRead(conversationId, 0)
}

/** 当前用户把会话读到 remaining 条未读（通常为 0），稍后用服务端值校正 */
export function onConversationRead(conversationId: string, remaining = 0): void {
  const conversation = findConversation(conversationId)
  if (conversation) {
    conversation.unread_count = Math.max(0, remaining)
    conversation.notreadednumber = conversation.unread_count
    if (conversation.unread_count === 0) {
      clearMention(conversation)
      for (const item of unreadExtras.values()) {
        if (item.conversationId === conversationId) {
          item.atMe = false
          item.atAll = false
          item.atPersonal = false
        }
      }
    }
  }
  scheduleConversationRefresh(conversationId, 800)
}

export function clearMention(conversation: Conversation): void {
  conversation.at_me = false
  conversation.at_me_count = 0
  conversation.at_me_message_id = undefined
  conversation.at_all = false
  conversation.at_all_count = 0
  conversation.at_all_message_id = undefined
  conversation.at_personal = false
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

/** 上线补齐里的 @我 / @所有人 以服务端快照为准；快照之后本页才数到的提及仍保留。 */
function applyMentionSnapshot(existing: Conversation, fresh: Conversation, afterSeq: string | null): void {
  const extra = afterSeq ? extraMentions(existing.conversation_id, afterSeq) : { atMe: 0, atAll: 0, atPersonal: 0 }
  const atMe = !!fresh.at_me || extra.atMe > 0
  const atAll = !!fresh.at_all || extra.atAll > 0
  const atPersonal = !!fresh.at_personal || extra.atPersonal > 0
  const atMeCount = (fresh.at_me_count || 0) + extra.atMe
  const atAllCount = (fresh.at_all_count || 0) + extra.atAll
  existing.at_me = atMe
  existing.at_me_count = atMe ? Math.max(atMeCount, 1) : 0
  existing.at_me_message_id = atMe ? (fresh.at_me_message_id || existing.at_me_message_id) : undefined
  existing.at_all = atAll
  existing.at_all_count = atAll ? Math.max(atAllCount, 1) : 0
  existing.at_all_message_id = atAll ? (fresh.at_all_message_id || existing.at_all_message_id) : undefined
  existing.at_personal = atPersonal
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
    const serverLatest = fresh.latest_seq || '0'
    const localAhead = compareSeqId(existing.latest_seq, serverLatest) > 0
    applyMentionSnapshot(existing, fresh, localAhead ? serverLatest : null)
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
    if (localAhead) {
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
