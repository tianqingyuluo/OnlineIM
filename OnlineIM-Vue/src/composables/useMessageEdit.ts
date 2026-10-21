import { computed, ref, type Ref } from 'vue'
import { toast } from 'vue-sonner'
import { MessageService } from '@/services/message.service'
import { useHistoryStore } from '@/stores/history'
import type { MessageResponse } from '@/type/message'
import { EDIT_MAX_LENGTH, EDIT_WINDOW_MS, isEditable, isTerminalEditError } from '@/utils/message-edit'
import { parseTimestamp } from '@/utils/conversation-time'

export interface EditingState {
  messageId: string
  conversationId: string
  /** 进入编辑前的原内容（用于“内容没变”判断与编辑条预览） */
  original: string
  /** 群聊：原 @ 列表 */
  atUsers?: string[] | null
}

/**
 * 私聊 / 群聊共用的“编辑消息”：右键“编辑”把原文放进输入框（暂存当前草稿），
 * 回车或“保存”提交，Esc / ✕ 取消并恢复草稿。成功后用返回的消息立即更新界面，
 * 其他成员与自己的其他标签页由 MESSAGE_EDITED 推送更新。
 */
export function useMessageEdit(
  draft: Ref<string>,
  currentUserId: () => string | undefined,
  focusInput: () => void,
) {
  const historyStore = useHistoryStore()
  const editing = ref<EditingState | null>(null)
  const saving = ref(false)
  let stashedDraft = ''

  const isEditing = computed(() => editing.value !== null)

  /** 菜单里“编辑”的截止时间；不可编辑返回 0 */
  function editUntil(message: MessageResponse): number {
    if (!isEditable(message, currentUserId(), 0)) return 0
    return parseTimestamp(message.timestamp) + EDIT_WINDOW_MS
  }

  function start(message: MessageResponse) {
    if (!isEditable(message, currentUserId())) {
      toast.error('消息发送已超过2分钟，无法编辑')
      return
    }
    if (!editing.value) stashedDraft = draft.value
    editing.value = {
      messageId: message.message_id,
      conversationId: message.conversation_id,
      original: message.content,
      atUsers: message.at_users,
    }
    historyStore.cancelReplyTarget()
    draft.value = message.content
    focusInput()
    // 右键菜单关闭（焦点陷阱释放）后再聚焦一次
    setTimeout(focusInput, 0)
  }

  function cancel() {
    if (!editing.value) return
    editing.value = null
    draft.value = stashedDraft
    stashedDraft = ''
  }

  /** 会话切换时直接丢弃编辑态（不恢复草稿到新会话） */
  function reset() {
    editing.value = null
    stashedDraft = ''
  }

  /**
   * 保存编辑。atUsers：群聊编辑后的 @ 列表（undefined 表示保持原列表）。
   * 返回是否已退出编辑态。
   */
  async function save(atUsers?: string[] | null): Promise<boolean> {
    const state = editing.value
    if (!state || saving.value) return false
    const content = draft.value.trim()
    if (!content) {
      toast.error('编辑后的内容不能为空')
      return false
    }
    if (content.length > EDIT_MAX_LENGTH) {
      toast.error(`编辑后的内容不能超过${EDIT_MAX_LENGTH}个字符`)
      return false
    }
    const sameMentions = atUsers === undefined
      || [...(atUsers || [])].sort().join(',') === [...(state.atUsers || [])].sort().join(',')
    if (content === state.original.trim() && sameMentions) {
      cancel()
      return true
    }
    saving.value = true
    try {
      const request = atUsers === undefined
        ? { content }
        : { content, at_users: atUsers || [] }
      const updated = await MessageService.editMessage(state.messageId, request)
      await historyStore.handleMessageEdited(updated)
      cancel()
      return true
    } catch (error) {
      const err = (error || {}) as { status?: number; code?: string; message?: string }
      // 400/403/网络错误已由 api 拦截器提示
      if (err.status !== 400 && err.status !== 403 && err.status !== 0) {
        toast.error(err.message && /[\u4e00-\u9fa5]/u.test(err.message) ? err.message : '编辑失败，请重试')
      }
      if (isTerminalEditError(err.code)) cancel()
      return false
    } finally {
      saving.value = false
    }
  }

  return { editing, saving, isEditing, editUntil, start, cancel, reset, save }
}
