import { onBeforeUnmount, onMounted, watch, type ComputedRef, type Ref } from 'vue'
import { toast } from 'vue-sonner'
import { conversationService } from '@/services/conversation.service'
import {
  applyDraftPreview,
  publishDraftUpdate,
  subscribeDraftUpdates,
} from '@/utils/draft-bus'
import {
  DRAFT_DEBOUNCE_MS,
  DRAFT_MAX_LENGTH,
  draftPreview,
  draftSignature,
  EMPTY_DRAFT_EXTRA,
  type DraftExtra,
  type DraftPayload,
} from '@/utils/draft'

/**
 * 把输入框里还没发出的文字、@ 和回复目标按会话同步到服务端。
 * 编辑消息期间不同步（输入框里是待编辑的原文，原来的草稿由编辑逻辑暂存）。
 */
export function useConversationDraft(options: {
  conversationId: Ref<string | undefined>
  draft: Ref<string>
  isEditing: Ref<boolean> | ComputedRef<boolean>
  /** 取某个会话当前的回复目标 / @；参数是草稿所属的会话（切换瞬间路由参数可能已经是新会话） */
  getExtra: (conversationId: string | undefined) => DraftExtra
  applyExtra: (extra: DraftExtra, payload: DraftPayload | null) => void
}) {
  const { conversationId, draft, isEditing, getExtra, applyExtra } = options
  let ready = false
  let applying = false
  let generation = 0
  let warnedLength = false
  let lastSynced = draftSignature('', EMPTY_DRAFT_EXTRA)
  let timer: ReturnType<typeof setTimeout> | null = null
  let retryTimer: ReturnType<typeof setTimeout> | null = null
  /**
   * 输入框里的内容属于哪个会话。私聊、群聊路由共用 :id 参数，从私聊点到群聊时
   * 路由参数先变成群 ID、旧组件随后才卸载，不能用 conversationId.value 判断草稿归属。
   */
  let boundId: string | undefined = conversationId.value

  function clearTimers() {
    if (timer) clearTimeout(timer)
    if (retryTimer) clearTimeout(retryTimer)
    timer = null
    retryTimer = null
  }

  function currentSignature() {
    return draftSignature(draft.value, getExtra(boundId))
  }

  function apply(payload: DraftPayload | null) {
    applying = true
    const cleared = !payload || payload.cleared || !payload.content
    const content = cleared ? '' : payload!.content || ''
    if (draft.value !== content) draft.value = content
    if (cleared) {
      applyExtra(EMPTY_DRAFT_EXTRA, payload)
    } else {
      applyExtra({
        replyToMessageId: payload!.reply_to_message_id || payload!.reply_to?.message_id || null,
        atUsers: (payload!.at_users || []).filter(item => item.user_id && item.name),
      }, payload)
    }
    applying = false
    lastSynced = currentSignature()
    if (payload?.conversation_id) {
      applyDraftPreview(payload.conversation_id, cleared ? null : payload.draft_preview || draftPreview(content))
    }
  }

  async function persist(id: string, content: string, extra: DraftExtra) {
    const savedSignature = draftSignature(content, extra)
    if (savedSignature === lastSynced) return
    if (!content.trim()) {
      try {
        await conversationService.clearDraft(id)
        const cleared: DraftPayload = {
          conversation_id: id,
          content: '',
          draft_preview: null,
          cleared: true,
          updated_at: Date.now(),
        }
        publishDraftUpdate(cleared)
        if (id === boundId && currentSignature() === savedSignature) {
          lastSynced = savedSignature
        }
      } catch (error) {
        scheduleRetry(id, error)
      }
      return
    }
    if (content.length > DRAFT_MAX_LENGTH) {
      if (!warnedLength) {
        warnedLength = true
        toast.error(`草稿不能超过${DRAFT_MAX_LENGTH}个字符`)
      }
      return
    }
    warnedLength = false
    try {
      const saved = await conversationService.saveDraft(id, {
        content,
        reply_to_message_id: extra.replyToMessageId,
        at_users: extra.atUsers,
      })
      if (saved) publishDraftUpdate(saved)
      if (id === boundId && currentSignature() === savedSignature) {
        lastSynced = savedSignature
      } else if (id === boundId && !isEditing.value) {
        schedule()
      }
    } catch (error) {
      scheduleRetry(id, error)
    }
  }

  function scheduleRetry(id: string, error: unknown) {
    const status = (error as { status?: number } | null)?.status
    if (status === 400 || status === 403 || id !== boundId) return
    if (retryTimer) clearTimeout(retryTimer)
    retryTimer = setTimeout(() => {
      retryTimer = null
      if (id === boundId && ready && !isEditing.value && currentSignature() !== lastSynced) {
        void flush()
      }
    }, 2000)
  }

  function schedule() {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      timer = null
      void flush()
    }, DRAFT_DEBOUNCE_MS)
  }

  async function flush() {
    clearTimers()
    const id = boundId
    if (!id || id !== conversationId.value || !ready || isEditing.value) return
    const content = draft.value
    const extra = getExtra(id)
    if (draftSignature(content, extra) === lastSynced) return
    await persist(id, content, extra)
  }

  async function reload() {
    const id = conversationId.value
    if (!id) return
    const gen = ++generation
    boundId = id
    ready = false
    clearTimers()
    try {
      const data = await conversationService.getDraft(id)
      if (gen !== generation || id !== conversationId.value || isEditing.value) return
      const dirty = currentSignature() !== lastSynced && draft.value.trim()
      if (dirty) {
        ready = true
        schedule()
        return
      }
      apply(data ? { ...data, conversation_id: data.conversation_id || id } : {
        conversation_id: id,
        cleared: true,
      })
    } catch {
      if (gen !== generation || id !== conversationId.value) return
      lastSynced = currentSignature()
    } finally {
      if (gen === generation && id === conversationId.value && !isEditing.value) ready = true
    }
  }

  watch(conversationId, (next, prev) => {
    const from = boundId ?? prev
    const editing = isEditing.value
    const content = draft.value
    const extra = getExtra(from)
    const wasReady = ready
    ready = false
    clearTimers()
    generation += 1
    boundId = next
    if (from && from !== next && !editing && wasReady) {
      void persist(from, content, extra)
    }
  })

  watch(() => draftSignature(draft.value, getExtra(boundId)), (next) => {
    if (applying || !ready || isEditing.value) return
    if (next === lastSynced) return
    schedule()
  }, { flush: 'sync' })

  watch(isEditing, (editing, wasEditing) => {
    if (editing) clearTimers()
    else if (wasEditing && !ready) void reload()
  })

  const unsubscribe = subscribeDraftUpdates(payload => {
    if (!payload?.conversation_id) return
    applyDraftPreview(payload.conversation_id, payload.cleared ? null : payload.draft_preview)
    if (payload.conversation_id !== boundId) return
    if (!ready || isEditing.value || applying) return
    if (currentSignature() !== lastSynced) return
    apply(payload.cleared ? { ...payload, content: '' } : payload)
  })

  function onVisible() {
    if (document.visibilityState !== 'visible') return
    if (!ready || isEditing.value || currentSignature() !== lastSynced) return
    void reload()
  }

  onMounted(() => document.addEventListener('visibilitychange', onVisible))
  onBeforeUnmount(() => {
    document.removeEventListener('visibilitychange', onVisible)
    unsubscribe()
    const id = boundId
    if (id && ready && !isEditing.value && currentSignature() !== lastSynced) {
      void persist(id, draft.value, getExtra(id))
    }
    clearTimers()
  })

  return { flush, reload }
}
