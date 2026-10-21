import { computed, ref, type Ref } from 'vue'
import { toast } from 'vue-sonner'
import { MessageService } from '@/services/message.service'
import { useHistoryStore } from '@/stores/history'
import type { MessageForwardResponse, MessageResponse } from '@/type/message'
import {
  FORWARD_MAX_MERGED,
  FORWARD_MAX_SEPARATE,
  isForwardable,
  type ForwardMode,
} from '@/utils/chat-record'

/** 只含 [A-Za-z0-9_-]，与服务端 client_request_id 校验一致 */
function newRequestId(): string {
  const random = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID().replace(/-/g, '')
    : `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 12)}`
  return `fwd_${random}`.slice(0, 64)
}

/** api 拦截器已对 400/403/网络错误弹过提示，这里只补其他状态（404 消息不存在、409 已撤回等） */
function notifyForwardError(error: unknown) {
  const err = (error || {}) as { status?: number; message?: string }
  if (err.status === 400 || err.status === 403 || err.status === 0) return
  toast.error(err.message && /[\u4e00-\u9fa5]/u.test(err.message) ? err.message : '转发失败，请重试')
}

/**
 * 私聊 / 群聊共用的转发状态：右键“转发”（单条）、“多选”后逐条或合并转发、目标选择器。
 * 成功后把返回的新消息交给 historyStore（当前打开的目标会话会直接显示，侧栏预览立即更新）。
 */
export function useMessageForward(
  conversationId: Ref<string | undefined> | (() => string | undefined),
  messages: () => MessageResponse[],
) {
  const historyStore = useHistoryStore()
  const sourceId = computed(() =>
    typeof conversationId === 'function' ? conversationId() : conversationId.value)

  const selecting = ref(false)
  const selectedIds = ref<string[]>([])
  const pickerOpen = ref(false)
  const pickerMode = ref<ForwardMode>('separate')
  const pickerIds = ref<string[]>([])
  const busy = ref(false)
  let requestId = newRequestId()

  const selectedCount = computed(() => selectedIds.value.length)

  function isSelected(message: MessageResponse): boolean {
    return selectedIds.value.includes(message.message_id)
  }

  function openPicker(ids: string[], mode: ForwardMode) {
    const max = mode === 'merged' ? FORWARD_MAX_MERGED : FORWARD_MAX_SEPARATE
    if (!ids.length) {
      toast.info('请先选择要转发的消息')
      return
    }
    if (ids.length > max) {
      toast.error(`${mode === 'merged' ? '合并转发' : '逐条转发'}最多 ${max} 条消息`)
      return
    }
    pickerIds.value = ids
    pickerMode.value = mode
    requestId = newRequestId()
    pickerOpen.value = true
  }

  /** 右键菜单“转发”：单条 */
  function forwardOne(message: MessageResponse) {
    if (!isForwardable(message)) {
      toast.info('这条消息不能转发')
      return
    }
    openPicker([message.message_id], 'separate')
  }

  /** 右键菜单“多选”：进入多选模式并选中当前消息 */
  function startSelect(message?: MessageResponse) {
    selecting.value = true
    selectedIds.value = message && isForwardable(message) ? [message.message_id] : []
  }

  function toggleSelect(message: MessageResponse) {
    if (!selecting.value || !isForwardable(message)) return
    selectedIds.value = isSelected(message)
      ? selectedIds.value.filter(id => id !== message.message_id)
      : [...selectedIds.value, message.message_id]
  }

  function cancelSelect() {
    selecting.value = false
    selectedIds.value = []
  }

  /** 多选底栏：按当前列表顺序（即时间顺序）提交 */
  function forwardSelected(mode: ForwardMode) {
    const order = new Map(messages().map((message, index) => [message.message_id, index]))
    const ids = [...selectedIds.value]
      .filter(id => order.has(id))
      .sort((a, b) => (order.get(a) ?? 0) - (order.get(b) ?? 0))
    openPicker(ids, mode)
  }

  function closePicker() {
    if (busy.value) return
    pickerOpen.value = false
  }

  async function confirm(targets: string[]): Promise<MessageForwardResponse | null> {
    const source = sourceId.value
    if (!source || !targets.length || busy.value) return null
    busy.value = true
    try {
      const response = await MessageService.forwardMessages({
        source_conversation_id: source,
        message_ids: pickerIds.value,
        targets,
        mode: pickerMode.value,
        client_request_id: requestId,
      })
      for (const result of response.results || []) {
        const isGroup = result.target_id.startsWith('grp_')
        for (const message of result.messages || []) {
          historyStore.handleIncomingMessage({ ...message, delivery_state: message.delivery_state || 'sent' }, isGroup)
        }
      }
      toast.success(targets.length > 1 ? `已转发到 ${targets.length} 个会话` : '已转发')
      pickerOpen.value = false
      cancelSelect()
      return response
    } catch (error) {
      notifyForwardError(error)
      return null
    } finally {
      busy.value = false
    }
  }

  return {
    selecting,
    selectedIds,
    selectedCount,
    pickerOpen,
    pickerMode,
    pickerIds,
    busy,
    isSelected,
    forwardOne,
    startSelect,
    toggleSelect,
    cancelSelect,
    forwardSelected,
    closePicker,
    confirm,
  }
}
