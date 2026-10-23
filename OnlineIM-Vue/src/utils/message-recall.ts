import type { MessageResponse } from '@/type/message'
import type { Conversation } from '@/type/Conversation'
import { parseTimestamp } from '@/utils/conversation-time'

/** 与服务端 im.message.recall-window-seconds 默认值一致（2 分钟）；最终以服务端校验为准 */
export const RECALL_WINDOW_MS = 2 * 60 * 1000
/** 服务端写入会话预览的撤回占位（旧数据 / 无发送者信息时的兜底） */
export const RECALLED_PREVIEW_FALLBACK = '[消息已撤回]'

export function isRecalledMessage(message: Pick<MessageResponse, 'is_recalled' | 'status'>): boolean {
  return !!message.is_recalled || String(message.status) === '3'
}

/**
 * 能否显示“撤回”：已送达服务端（有正式 message_id）、未撤回，且仍在时间窗内。
 * 默认只能撤回自己的；群主/管理员传 allowOthers 后可以撤回群里其他人的（时间窗相同）。
 * 任何类型（文本 / 图片 / 文件 / 语音 / 表情 / 聊天记录）都可以撤回。
 */
export function isRecallable(
  message: Pick<MessageResponse, 'is_recalled' | 'status' | 'delivery_state' | 'message_id' | 'timestamp' | 'sender_info'>,
  currentUserId: string | undefined,
  now: number = Date.now(),
  options: { allowOthers?: boolean } = {},
): boolean {
  const own = !!currentUserId && message.sender_info?.user_id === currentUserId
  if (!currentUserId || (!own && !options.allowOthers)) return false
  if (isRecalledMessage(message)) return false
  if (message.delivery_state === 'sending' || message.delivery_state === 'failed') return false
  if (!message.message_id || message.message_id.startsWith('local_')) return false
  const sentAt = parseTimestamp(message.timestamp)
  if (!sentAt) return false
  return now - sentAt <= RECALL_WINDOW_MS
}

/** 菜单里“撤回”的截止时间（毫秒）；不可撤回返回 0。菜单打开时再与当前时间比较 */
export function recallUntil(
  message: Parameters<typeof isRecallable>[0],
  currentUserId: string | undefined,
  options: { allowOthers?: boolean } = {},
): number {
  if (!isRecallable(message, currentUserId, 0, options)) return 0
  return parseTimestamp(message.timestamp) + RECALL_WINDOW_MS
}

/** 聊天里的撤回提示：自己 “你撤回了一条消息”，群管理撤回他人 “群主/管理员撤回了一条消息”，否则 “X 撤回了一条消息” */
export function recallNoticeText(
  isSelf: boolean,
  senderName?: string | null,
  operatorRole?: string | null,
): string {
  if (isSelf) return '你撤回了一条消息'
  if (operatorRole === 'owner') return '群主撤回了一条消息'
  if (operatorRole === 'admin') return '管理员撤回了一条消息'
  const name = (senderName || '').trim()
  return `${name || '对方'} 撤回了一条消息`
}

/** 撤回出错时的提示（400 / 403 已由 api 拦截器提示，返回 null） */
export function recallErrorText(error: { status?: number; code?: string; message?: string } | null | undefined): string | null {
  const err = error || {}
  if (err.status === 400 || err.status === 403 || err.status === 0) return null
  if (err.code === 'MESSAGE_ALREADY_RECALLED') return '消息已撤回'
  if (err.code === 'MESSAGE_NOT_FOUND') return '消息不存在或已被删除'
  return err.message && /[\u4e00-\u9fa5]/u.test(err.message) ? err.message : '撤回失败，请重试'
}

/**
 * 会话列表的预览文字：
 * - 撤回：自己 “你撤回了一条消息”，别人 “X 撤回了一条消息”（私聊用会话显示名，群聊用发送者昵称）
 * - 群聊里别人发的消息：“昵称: 内容”
 */
export function conversationPreviewText(conversation: Pick<Conversation, 'type' | 'target_info' | 'last_message'>, currentUserId?: string): string {
  const last = conversation.last_message
  if (!last) return ''
  const content = last.content_preview || ''
  const recalled = !!last.is_recalled || last.message_type === 'recalled' || content === RECALLED_PREVIEW_FALLBACK
  const senderId = last.sender_id
  const isSelf = !!senderId && !!currentUserId && senderId === currentUserId
  const recalledByMe = last.recall_user_id
    ? !!currentUserId && last.recall_user_id === currentUserId
    : !!senderId && !!currentUserId && senderId === currentUserId
  const senderName = conversation.type === 'private'
    ? conversation.target_info?.name || last.sender_nickname
    : last.sender_nickname
  if (recalled) {
    if (!senderId && !last.recall_operator_role) return RECALLED_PREVIEW_FALLBACK
    return recallNoticeText(recalledByMe, senderName, last.recall_operator_role)
  }
  if (conversation.type === 'group' && senderId && !isSelf && last.sender_nickname && content) {
    return `${last.sender_nickname}: ${content}`
  }
  return content
}
