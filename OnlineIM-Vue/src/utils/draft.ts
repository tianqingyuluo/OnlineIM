import type { ReplyReference } from '@/type/message'

/** 与服务端 im.draft.max-length、DraftPreview.MAX_LENGTH 一致 */
export const DRAFT_DEBOUNCE_MS = 500
export const DRAFT_MAX_LENGTH = 5000
export const DRAFT_PREVIEW_LENGTH = 40

export interface DraftMention {
  user_id: string
  name: string
}

/** GET/PUT 响应，以及 WebSocket DRAFT_UPDATED.message */
export interface DraftPayload {
  conversation_id: string
  content?: string | null
  draft_preview?: string | null
  reply_to_message_id?: string | null
  reply_to?: ReplyReference | null
  at_users?: DraftMention[] | null
  updated_at?: number
  cleared?: boolean
}

export interface DraftExtra {
  replyToMessageId: string | null
  atUsers: DraftMention[]
}

export const EMPTY_DRAFT_EXTRA: DraftExtra = { replyToMessageId: null, atUsers: [] }

/** 会话列表摘要：折叠空白，按 Unicode code point 最多 40 个，多出来加 … */
export function draftPreview(content: string | null | undefined): string {
  const collapsed = (content || '').replace(/\s+/gu, ' ').trim()
  if (!collapsed) return ''
  const chars = Array.from(collapsed)
  if (chars.length <= DRAFT_PREVIEW_LENGTH) return collapsed
  return `${chars.slice(0, DRAFT_PREVIEW_LENGTH).join('')}…`
}

export function draftSignature(content: string, extra: DraftExtra): string {
  const atUsers = [...(extra.atUsers || [])]
    .filter(item => item.user_id && item.name.trim())
    .map(item => ({ user_id: item.user_id, name: item.name }))
    .sort((a, b) => a.user_id.localeCompare(b.user_id) || a.name.localeCompare(b.name))
  return JSON.stringify({
    content,
    replyToMessageId: extra.replyToMessageId || null,
    atUsers,
  })
}
