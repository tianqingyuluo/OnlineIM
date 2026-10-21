import { useListStore } from '@/stores/list'
import type { DraftPayload } from '@/utils/draft'

type DraftListener = (payload: DraftPayload) => void

const listeners = new Set<DraftListener>()

/** 会话列表上的草稿摘要。没有草稿时清掉。 */
export function applyDraftPreview(conversationId: string, preview: string | null | undefined) {
  const conversation = useListStore().conversations?.find(item => item.conversation_id === conversationId)
  if (conversation) {
    conversation.draft_preview = preview || null
  }
}

export function subscribeDraftUpdates(listener: DraftListener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** 本机保存成功或收到 DRAFT_UPDATED 时调用：更新列表，并通知打开着的输入框。 */
export function publishDraftUpdate(payload: DraftPayload) {
  try {
    applyDraftPreview(payload.conversation_id, payload.cleared ? null : payload.draft_preview)
  } catch {
    // Pinia 尚未安装时忽略列表更新
  }
  listeners.forEach(listener => listener(payload))
}
