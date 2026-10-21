import type { MessageResponse } from '@/type/message'
import { parseTimestamp } from '@/utils/conversation-time'
import { collectAtUsers } from '@/utils/mention'

/** 与服务端 im.message.edit-window-seconds 默认值一致（= 撤回时间窗 2 分钟）；最终以服务端校验为准 */
export const EDIT_WINDOW_MS = 2 * 60 * 1000
/** 与服务端 im.message.edit-max-length 默认值一致 */
export const EDIT_MAX_LENGTH = 5000

/**
 * 能否显示“编辑”：自己发出、已送达服务端（有正式 message_id）、未撤回的文本消息，且仍在时间窗内。
 */
export function isEditable(
  message: Pick<MessageResponse, 'message_type' | 'is_recalled' | 'status' | 'delivery_state' | 'message_id'
    | 'timestamp' | 'sender_info'>,
  currentUserId: string | undefined,
  now: number = Date.now(),
): boolean {
  if (!currentUserId || message.sender_info?.user_id !== currentUserId) return false
  if (message.is_recalled || String(message.status) === '3') return false
  if (message.delivery_state === 'sending' || message.delivery_state === 'failed') return false
  if (!message.message_id || message.message_id.startsWith('local_')) return false
  if ((message.message_type || 'text').toLowerCase() !== 'text') return false
  const sentAt = parseTimestamp(message.timestamp)
  if (!sentAt) return false
  return now - sentAt <= EDIT_WINDOW_MS
}

/** “已编辑”标记的悬停提示 */
export function editedTitle(editedAt?: string | null): string {
  return editedAt ? `编辑于 ${editedAt}` : '已编辑'
}

/** 收到的版本是否比本地新（content_revision 缺省按 1；同版本也接受，以服务端内容为准） */
export function isNewerRevision(incoming?: number | null, current?: number | null): boolean {
  return (incoming ?? 1) >= (current ?? 1)
}

/** 编辑出错时是否应退出编辑态（消息已不能再编辑） */
export function isTerminalEditError(code?: string): boolean {
  return code === 'EDIT_WINDOW_EXPIRED'
    || code === 'MESSAGE_ALREADY_RECALLED'
    || code === 'MESSAGE_NOT_FOUND'
    || code === 'EDIT_NOT_SUPPORTED'
    || code === 'NOT_MESSAGE_SENDER'
    || code === 'NOT_CONVERSATION_MEMBER'
}

/**
 * 群聊编辑：把原消息的 @ 成员放回“已选成员”表（user_id → 文本里实际出现的名字），
 * 保存时再用 collectAtUsers 只保留文本里仍有 `@名字` 的成员。
 */
export function mentionLabelsForEdit(
  content: string,
  atUsers: string[] | null | undefined,
  mentionNames: Record<string, string[]>,
): Map<string, string> {
  const picked = new Map<string, string>()
  for (const userId of atUsers || []) {
    const names = mentionNames[userId] || []
    const label = names.find(name => collectAtUsers(content, new Map([[userId, name]]))?.length)
    if (label) picked.set(userId, label)
  }
  return picked
}
