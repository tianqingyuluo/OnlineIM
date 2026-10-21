import type { MessageSearchItem, SearchHighlight } from '@/type/message'

export const SEARCH_MAX_KEYWORD_LENGTH = 50
export const SEARCH_PAGE_SIZE = 20
export const SEARCH_DEBOUNCE_MS = 300

export interface SnippetSegment {
  text: string
  hit: boolean
}

/** 去掉首尾空白；超过上限按字符（码点）截断 */
export function normalizeSearchKeyword(value: string): string {
  const trimmed = (value || '').trim()
  const chars = Array.from(trimmed)
  return chars.length > SEARCH_MAX_KEYWORD_LENGTH ? chars.slice(0, SEARCH_MAX_KEYWORD_LENGTH).join('') : trimmed
}

/** 按服务端给的命中位置把片段拆成普通 / 高亮段（不使用 v-html），越界或重叠的位置被忽略 */
export function splitSnippet(snippet: string, highlights: SearchHighlight[] | null | undefined): SnippetSegment[] {
  const text = snippet || ''
  const ranges = [...(highlights || [])]
    .filter(range => Number.isInteger(range.start) && Number.isInteger(range.length) && range.length > 0
      && range.start >= 0 && range.start + range.length <= text.length)
    .sort((left, right) => left.start - right.start)
  const segments: SnippetSegment[] = []
  let cursor = 0
  for (const range of ranges) {
    if (range.start < cursor) continue
    if (range.start > cursor) segments.push({ text: text.slice(cursor, range.start), hit: false })
    segments.push({ text: text.slice(range.start, range.start + range.length), hit: true })
    cursor = range.start + range.length
  }
  if (cursor < text.length) segments.push({ text: text.slice(cursor), hit: false })
  return segments
}

/** 结果所在会话的聊天地址，带 ?msg= 让聊天窗口加载后跳到并高亮该消息 */
export function searchResultLocation(item: MessageSearchItem): { path: string, query: { msg: string } } {
  const conversationId = item.conversation?.conversation_id || item.message.conversation_id
  const isGroup = item.conversation?.type === 'group' || conversationId.startsWith('grp_')
  return {
    path: `/main/chat/${isGroup ? 'group' : 'private'}/${conversationId}`,
    query: { msg: item.message.message_id },
  }
}

/** 发送者显示名：自己显示“我” */
export function searchSenderName(item: MessageSearchItem, currentUserId: string): string {
  const sender = item.message.sender_info
  if (!sender) return ''
  if (sender.user_id === currentUserId) return '我'
  return sender.nickname || sender.username || sender.user_id
}
