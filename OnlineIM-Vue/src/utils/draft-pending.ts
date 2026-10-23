import type { DraftMention } from '@/utils/draft'

/**
 * 还没确认写到服务端的草稿。按用户分开，避免同一浏览器换账号串草稿。
 * 输入一变就同步写入，这样 0.5 秒防抖还没到、或刷新把请求取消时，重新打开仍能恢复。
 */
const KEY_PREFIX = 'onlineim.draft.pending.'

export interface PendingDraft {
  content: string
  replyToMessageId: string | null
  atUsers: DraftMention[]
  savedAt: number
}

function storageKey(userId: string): string {
  return `${KEY_PREFIX}${userId}`
}

function readAll(userId: string): Record<string, PendingDraft> {
  if (!userId || typeof localStorage === 'undefined') return {}
  try {
    const raw = localStorage.getItem(storageKey(userId))
    if (!raw) return {}
    const parsed = JSON.parse(raw) as unknown
    if (!parsed || typeof parsed !== 'object') return {}
    return parsed as Record<string, PendingDraft>
  } catch {
    return {}
  }
}

function writeAll(userId: string, all: Record<string, PendingDraft>): void {
  if (!userId || typeof localStorage === 'undefined') return
  try {
    if (Object.keys(all).length === 0) localStorage.removeItem(storageKey(userId))
    else localStorage.setItem(storageKey(userId), JSON.stringify(all))
  } catch {
    // 隐私模式或配额满时，只能靠页面关闭时的 keepalive 请求
  }
}

function normalize(item: PendingDraft | undefined): PendingDraft | null {
  if (!item || typeof item.content !== 'string') return null
  const atUsers = Array.isArray(item.atUsers)
    ? item.atUsers.filter(mention => mention?.user_id && mention?.name)
    : []
  return {
    content: item.content,
    replyToMessageId: item.replyToMessageId || null,
    atUsers,
    savedAt: typeof item.savedAt === 'number' ? item.savedAt : 0,
  }
}

export function readPendingDraft(userId: string, conversationId: string): PendingDraft | null {
  if (!userId || !conversationId) return null
  return normalize(readAll(userId)[conversationId])
}

export function writePendingDraft(userId: string, conversationId: string, draft: PendingDraft): void {
  if (!userId || !conversationId) return
  const all = readAll(userId)
  const normalized = normalize({
    ...draft,
    atUsers: draft.atUsers || [],
  })
  if (!normalized) return
  all[conversationId] = normalized
  writeAll(userId, all)
}

export function clearPendingDraft(userId: string, conversationId: string): void {
  if (!userId || !conversationId) return
  const all = readAll(userId)
  if (!(conversationId in all)) return
  delete all[conversationId]
  writeAll(userId, all)
}
