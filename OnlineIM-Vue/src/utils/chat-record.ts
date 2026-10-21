import type { MessageResponse } from '@/type/message'

/** 合并转发生成的 chat_record 消息内容（服务端生成的 JSON 字符串） */
export interface ChatRecordItem {
  sender_id: string
  sender_name: string
  sender_avatar?: string
  message_type: string
  content: string
  timestamp?: string
  forwarded_from?: { user_id: string; display_name: string }
}

export interface ChatRecordContent {
  title: string
  count: number
  items: ChatRecordItem[]
}

export type ForwardMode = 'separate' | 'merged'

export const CHAT_RECORD_TYPE = 'chat_record'
/** 与后端 im.forward.* 默认值一致；最终以服务端校验为准 */
export const FORWARD_MAX_TARGETS = 9
export const FORWARD_MAX_SEPARATE = 30
export const FORWARD_MAX_MERGED = 100

const FORWARDABLE_TYPES = new Set([
  'text', 'image', 'file', 'voice', 'audio', 'video', 'emoji', 'sticker', CHAT_RECORD_TYPE,
])

export function isChatRecordType(messageType?: string): boolean {
  return messageType === CHAT_RECORD_TYPE
}

export function parseChatRecord(content: unknown): ChatRecordContent | null {
  let value: unknown = content
  if (typeof value === 'string') {
    try {
      value = JSON.parse(value)
    } catch {
      return null
    }
  }
  if (!value || typeof value !== 'object') return null
  const record = value as Partial<ChatRecordContent>
  if (!Array.isArray(record.items)) return null
  const items = record.items.filter((item): item is ChatRecordItem =>
    !!item && typeof item === 'object' && typeof (item as ChatRecordItem).message_type === 'string')
  return {
    title: typeof record.title === 'string' && record.title ? record.title : '聊天记录',
    count: typeof record.count === 'number' ? record.count : items.length,
    items,
  }
}

/** 单条记录的文字摘要（与会话列表预览一致） */
export function previewOfRecordItem(item: Pick<ChatRecordItem, 'message_type' | 'content'>): string {
  switch ((item.message_type || 'text').toLowerCase()) {
    case 'text': return typeof item.content === 'string' ? item.content : ''
    case 'image': return '[图片]'
    case 'file': {
      const name = fileNameOf(item.content)
      return name ? `[文件] ${name}` : '[文件]'
    }
    case 'voice':
    case 'audio': return '[语音]'
    case 'video': return '[视频]'
    case 'emoji':
    case 'sticker': return '[表情]'
    case CHAT_RECORD_TYPE: return '[聊天记录]'
    default: return '[消息]'
  }
}

/** 卡片上显示的前几行：“发送者: 摘要” */
export function chatRecordSummaryLines(record: ChatRecordContent, limit = 3): string[] {
  return record.items.slice(0, limit).map(item =>
    `${item.sender_name || '未知用户'}: ${previewOfRecordItem(item)}`.replace(/\s+/g, ' ').slice(0, 60))
}

function fileNameOf(content: unknown): string {
  if (typeof content !== 'string') return ''
  try {
    const parsed = JSON.parse(content) as { name?: string }
    return typeof parsed?.name === 'string' ? parsed.name : ''
  } catch {
    return ''
  }
}

/** 能否转发：已撤回、仍在发送/失败（还没有服务端 ID）或不支持的类型不能转发 */
export function isForwardable(message: Pick<MessageResponse,
  'message_type' | 'is_recalled' | 'status' | 'delivery_state' | 'message_id'>): boolean {
  if (message.is_recalled || String(message.status) === '3') return false
  if (message.delivery_state === 'sending' || message.delivery_state === 'failed') return false
  if (!message.message_id || message.message_id.startsWith('local_')) return false
  return FORWARDABLE_TYPES.has((message.message_type || 'text').toLowerCase())
}

/** ISO 时间 -> “MM-dd HH:mm”（本地时区） */
export function formatRecordTime(timestamp?: string): string {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return timestamp
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export interface ForwardTargetOption {
  id: string
  kind: 'friend' | 'group'
  label: string
  sublabel?: string
  avatar_url?: string
  keywords: string
}

/** 搜索：按备注 / 昵称 / 用户名 / 群名，大小写不敏感 */
export function filterForwardTargets(options: ForwardTargetOption[], query: string): ForwardTargetOption[] {
  const keyword = query.trim().toLowerCase()
  if (!keyword) return options
  return options.filter(option => option.keywords.includes(keyword))
}
