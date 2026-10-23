import { computed, ref } from 'vue'
import { toast } from 'vue-sonner'
import { MessageService } from '@/services/message.service'
import { useHistoryStore, type RecalledOriginal } from '@/stores/history'
import type { MessageResponse } from '@/type/message'
import { isRecallable, recallErrorText, recallUntil as computeRecallUntil } from '@/utils/message-recall'

/**
 * 私聊 / 群聊共用的“撤回消息”：右键（手机长按）“撤回” → 确认对话框 → DELETE /messages/{id}。
 * 成功后立即在本地换成撤回提示，其他成员与自己的其他标签页由 MESSAGE_RECALLED / RECALL_MESSAGE_RESPONSE 推送更新。
 */
export function useMessageRecall(
  currentUserId: () => string | undefined,
  hooks: { beforeRecall?: (message: MessageResponse) => void; allowOthers?: () => boolean } = {},
) {
  const historyStore = useHistoryStore()
  const target = ref<MessageResponse | null>(null)
  const busy = ref(false)
  const confirmOpen = computed(() => target.value !== null)

  function recallOptions() {
    return { allowOthers: !!hooks.allowOthers?.() }
  }

  function recallUntil(message: MessageResponse): number {
    return computeRecallUntil(message, currentUserId(), recallOptions())
  }

  /** 菜单选了“撤回”：先确认 */
  function request(message: MessageResponse) {
    if (!isRecallable(message, currentUserId(), Date.now(), recallOptions())) {
      toast.error('消息发送已超过2分钟，无法撤回')
      return
    }
    target.value = message
  }

  function cancel() {
    if (!busy.value) target.value = null
  }

  async function confirm(): Promise<boolean> {
    const message = target.value
    if (!message || busy.value) return false
    busy.value = true
    try {
      hooks.beforeRecall?.(message)
      await MessageService.recallMessage(message.message_id)
      await historyStore.handleMessageRecalled({
        message_id: message.message_id,
        conversation_id: message.conversation_id,
        recall_user_id: currentUserId(),
        seq_id: message.seq_id,
      })
      return true
    } catch (error) {
      const err = (error || {}) as { status?: number; code?: string; message?: string }
      const text = recallErrorText(err)
      if (text) toast.error(text)
      // 别的标签页已经撤回过：本地也换成撤回提示
      if (err.code === 'MESSAGE_ALREADY_RECALLED') {
        await historyStore.handleMessageRecalled({
          message_id: message.message_id,
          conversation_id: message.conversation_id,
          recall_user_id: currentUserId(),
          seq_id: message.seq_id,
        })
      }
      return false
    } finally {
      busy.value = false
      target.value = null
    }
  }

  /** 撤回提示上的“重新编辑”：返回原文（没有则 null） */
  function original(message: MessageResponse): RecalledOriginal | null {
    return historyStore.recalledOriginal(message.message_id)
  }

  return { target, busy, confirmOpen, recallUntil, request, cancel, confirm, original }
}
