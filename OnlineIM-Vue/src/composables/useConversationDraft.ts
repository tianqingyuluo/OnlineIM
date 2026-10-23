import { onBeforeUnmount, onMounted, watch, type ComputedRef, type Ref } from 'vue'
import { toast } from 'vue-sonner'
import { conversationService } from '@/services/conversation.service'
import { useUserStore } from '@/stores/user'
import {
  applyDraftPreview,
  publishDraftUpdate,
  subscribeDraftUpdates,
} from '@/utils/draft-bus'
import { clearPendingDraft, readPendingDraft, writePendingDraft, type PendingDraft } from '@/utils/draft-pending'
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
 * 防抖约 0.5 秒；每次改动先记到本地，并在切到后台、刷新或关闭页时立刻补写，
 * 避免还没到防抖点就刷新把草稿弄丢。重新编辑写回输入框走同一条路径。
 */
export function useConversationDraft(options: {
  conversationId: Ref<string | undefined>
  draft: Ref<string>
  isEditing: Ref<boolean> | ComputedRef<boolean>
  /** 取某个会话当前的回复目标 / @；参数是草稿所属的会话（切换瞬间路由参数可能已经是新会话） */
  getExtra: (conversationId: string | undefined) => DraftExtra
  applyExtra: (extra: DraftExtra, payload: DraftPayload | null) => void
  /**
   * 当前界面正打开的会话。新会话 loadInitialHistory 会先清掉旧会话的 replyTarget；
   * 用它判断「已切走」，避免把丢掉回复的镜像写回服务端。未传则只按 conversationId 判断。
   */
  getActiveConversationId?: () => string | null | undefined
}) {
  const { conversationId, draft, isEditing, getExtra, applyExtra, getActiveConversationId } = options
  let ready = false
  let applying = false
  let generation = 0
  let warnedLength = false
  let lastSynced = draftSignature('', EMPTY_DRAFT_EXTRA)
  /** 与 lastSynced 对应的 extra；切走时 replyTarget 常被先清掉，靠它避免把「丢掉回复」写回服务端/本地镜像 */
  let lastSyncedExtra: DraftExtra = { ...EMPTY_DRAFT_EXTRA }
  let timer: ReturnType<typeof setTimeout> | null = null
  let retryTimer: ReturnType<typeof setTimeout> | null = null
  /** 同一次关闭里 beforeunload 和 pagehide 都会响，同一份内容只发一次 keepalive */
  let unloadSignature: string | null = null
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

  function currentUserId(): string {
    try {
      return useUserStore().loggedInUser?.user_id || ''
    } catch {
      return ''
    }
  }

  function currentToken(): string {
    try {
      return useUserStore().token || ''
    } catch {
      return ''
    }
  }

  function currentSignature() {
    return draftSignature(draft.value, getExtra(boundId))
  }

  function extraOf(pending: PendingDraft): DraftExtra {
    return {
      replyToMessageId: pending.replyToMessageId,
      atUsers: pending.atUsers,
    }
  }

  function signatureOf(content: string, extra: DraftExtra): string {
    return draftSignature(content, extra)
  }

  function copyExtra(extra: DraftExtra): DraftExtra {
    return {
      replyToMessageId: extra.replyToMessageId,
      atUsers: extra.atUsers ? extra.atUsers.map(item => ({ ...item })) : [],
    }
  }

  function markSynced(signature: string, extra: DraftExtra) {
    lastSynced = signature
    lastSyncedExtra = copyExtra(extra)
  }

  /** 新会话的 loadInitialHistory 会先清掉 replyTarget；此时 bound 会话其实已经切走 */
  function stillOnBoundConversation(id: string | undefined): boolean {
    if (!id || boundId !== id) return false
    const active = getActiveConversationId?.()
    if (active && active !== id) return false
    return id === conversationId.value
  }

  /**
   * 取用于落盘/镜像的 extra。
   * 切走或卸载时 live 回复常已被清空，若正文与上次同步（带回复）一致，沿用上次，避免误清回复目标。
   * 用户仍在本会话里主动取消回复时，live 无回复且 stillOn → 按 live 继续同步。
   */
  function resolveExtraForPersist(id: string | undefined, content: string): DraftExtra {
    const live = getExtra(id)
    if (!id || live.replyToMessageId) return live
    if (!lastSyncedExtra.replyToMessageId) return live
    if (signatureOf(content, lastSyncedExtra) !== lastSynced) return live
    if (stillOnBoundConversation(id)) return live
    return copyExtra(lastSyncedExtra)
  }

  function payloadSignature(payload: DraftPayload | null): string {
    if (!payload || payload.cleared || !payload.content) return signatureOf('', EMPTY_DRAFT_EXTRA)
    return signatureOf(payload.content, {
      replyToMessageId: payload.reply_to_message_id || payload.reply_to?.message_id || null,
      atUsers: (payload.at_users || []).filter(item => item.user_id && item.name),
    })
  }

  function rememberPending(id: string, content: string, extra: DraftExtra) {
    writePendingDraft(currentUserId(), id, {
      content,
      replyToMessageId: extra.replyToMessageId,
      atUsers: extra.atUsers,
      savedAt: Date.now(),
    })
  }

  /** 服务端已经收下这份内容时，丢掉本地镜像，避免下次打开盖掉别的设备后写的草稿 */
  function forgetPendingIfSynced(id: string, savedSignature: string) {
    const pending = readPendingDraft(currentUserId(), id)
    if (!pending) return
    if (signatureOf(pending.content, extraOf(pending)) === savedSignature) {
      clearPendingDraft(currentUserId(), id)
    }
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
    const appliedExtra = cleared ? EMPTY_DRAFT_EXTRA : {
      replyToMessageId: payload!.reply_to_message_id || payload!.reply_to?.message_id || null,
      atUsers: (payload!.at_users || []).filter(item => item.user_id && item.name),
    }
    markSynced(signatureOf(content, appliedExtra), appliedExtra)
    // 输入框已经采用这份服务端/远端草稿，本地镜像作废，避免下次打开把旧字盖回去
    if (payload?.conversation_id) {
      clearPendingDraft(currentUserId(), payload.conversation_id)
      applyDraftPreview(payload.conversation_id, cleared ? null : payload.draft_preview || draftPreview(content))
    }
  }

  /** 把本地还没同步的草稿放回输入框。调用方要保持 applying，避免这次赋值又被记成新的未同步草稿。 */
  function restorePending(id: string, pending: PendingDraft, server: DraftPayload | null) {
    const extra = extraOf(pending)
    const cleared = !pending.content
    if (draft.value !== pending.content) draft.value = pending.content
    const reply = !cleared && extra.replyToMessageId && server?.reply_to?.message_id === extra.replyToMessageId
      ? server.reply_to
      : null
    applyExtra(cleared ? EMPTY_DRAFT_EXTRA : extra, cleared ? null : {
      conversation_id: id,
      content: pending.content,
      reply_to_message_id: extra.replyToMessageId,
      reply_to: reply,
      at_users: extra.atUsers,
      cleared: false,
    })
    applyDraftPreview(id, cleared ? null : draftPreview(pending.content))
  }

  async function persist(id: string, content: string, extra: DraftExtra) {
    const savedSignature = signatureOf(content, extra)
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
        forgetPendingIfSynced(id, savedSignature)
        if (id === boundId && currentSignature() === savedSignature) {
          markSynced(savedSignature, extra)
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
      forgetPendingIfSynced(id, savedSignature)
      if (id === boundId && currentSignature() === savedSignature) {
        markSynced(savedSignature, extra)
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
    const extra = resolveExtraForPersist(id, content)
    if (signatureOf(content, extra) === lastSynced) return
    await persist(id, content, extra)
  }

  /**
   * 刷新 / 关闭时 axios 往往发不出去。先同步记到本地（新页面据此恢复），再尽力 keepalive 写服务端。
   * 编辑消息时输入框是待保存的原文，不能当成草稿。
   */
  function flushOnPageExit() {
    const id = boundId
    if (!id || isEditing.value) return
    const content = draft.value
    const extra = resolveExtraForPersist(id, content)
    const signature = signatureOf(content, extra)
    if (signature === lastSynced) return
    rememberPending(id, content, extra)
    if (content.length > DRAFT_MAX_LENGTH) return
    if (unloadSignature === signature) return
    unloadSignature = signature
    clearTimers()
    conversationService.flushDraftKeepalive(id, {
      content,
      reply_to_message_id: extra.replyToMessageId,
      at_users: extra.atUsers,
    }, currentToken())
  }

  async function reload() {
    const id = conversationId.value
    if (!id) return
    const gen = ++generation
    boundId = id
    ready = false
    clearTimers()
    let flushAfter = false
    try {
      const data = await conversationService.getDraft(id)
      if (gen !== generation || id !== conversationId.value || isEditing.value) return
      const normalized: DraftPayload = data ? { ...data, conversation_id: data.conversation_id || id } : {
        conversation_id: id,
        cleared: true,
      }
      const dirty = currentSignature() !== lastSynced && draft.value.trim()
      if (dirty) {
        rememberPending(id, draft.value, resolveExtraForPersist(id, draft.value))
        ready = true
        schedule()
        return
      }
      flushAfter = adoptPending(id, normalized)
      if (!flushAfter) apply(normalized)
    } catch {
      if (gen !== generation || id !== conversationId.value || isEditing.value) return
      const pending = readPendingDraft(currentUserId(), id)
      if (pending && !draft.value.trim()) {
        applying = true
        restorePending(id, pending, null)
        markSynced(signatureOf('', EMPTY_DRAFT_EXTRA), EMPTY_DRAFT_EXTRA)
        applying = false
        flushAfter = currentSignature() !== lastSynced
      } else {
        markSynced(currentSignature(), getExtra(id))
      }
    } finally {
      if (gen === generation && id === conversationId.value && !isEditing.value) ready = true
    }
    if (flushAfter && gen === generation && id === conversationId.value) void flush()
  }

  /** 本地镜像和服务端不一致时恢复本地并安排补写。一致则丢掉镜像，交给 apply。返回是否需要补写。 */
  function adoptPending(id: string, server: DraftPayload): boolean {
    const pending = readPendingDraft(currentUserId(), id)
    if (!pending) return false
    const pendingSig = signatureOf(pending.content, extraOf(pending))
    const serverSig = payloadSignature(server)
    if (pendingSig === serverSig) {
      clearPendingDraft(currentUserId(), id)
      return false
    }
    const serverReply = server.reply_to_message_id || server.reply_to?.message_id || null
    // 切走竞态留下的镜像：正文与服务端相同，只是丢掉了回复 → 信任服务端
    if (
      pending.content === (server.content || '') &&
      !pending.replyToMessageId &&
      serverReply
    ) {
      clearPendingDraft(currentUserId(), id)
      return false
    }
    applying = true
    restorePending(id, pending, server)
    markSynced(serverSig, {
      replyToMessageId: serverReply,
      atUsers: (server.at_users || []).filter(item => item.user_id && item.name),
    })
    applying = false
    return currentSignature() !== lastSynced
  }

  watch(conversationId, (next, prev) => {
    const from = boundId ?? prev
    const editing = isEditing.value
    const content = draft.value
    const extra = resolveExtraForPersist(from, content)
    const wasReady = ready
    ready = false
    clearTimers()
    generation += 1
    boundId = next
    if (from && from !== next && !editing && wasReady && signatureOf(content, extra) !== lastSynced) {
      rememberPending(from, content, extra)
      void persist(from, content, extra)
    }
  })

  watch(() => draftSignature(draft.value, getExtra(boundId)), (next) => {
    if (applying || !ready || isEditing.value) return
    if (!boundId) return
    const content = draft.value
    const extra = resolveExtraForPersist(boundId, content)
    const sig = signatureOf(content, extra)
    if (sig === lastSynced) {
      // 仅在 live 签名也一致时清镜像；切走竞态下 live 已丢回复但不应清掉带回复的镜像
      if (next === lastSynced) clearPendingDraft(currentUserId(), boundId)
      return
    }
    rememberPending(boundId, content, extra)
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
    if (document.visibilityState === 'hidden') {
      const id = boundId
      if (!id || isEditing.value) return
      const content = draft.value
      const extra = resolveExtraForPersist(id, content)
      if (signatureOf(content, extra) === lastSynced) return
      rememberPending(id, content, extra)
      if (ready) void flush()
      return
    }
    unloadSignature = null
    if (!ready || isEditing.value || currentSignature() !== lastSynced) return
    void reload()
  }

  function onPageShow() {
    unloadSignature = null
    if (ready && !isEditing.value && currentSignature() !== lastSynced) schedule()
  }

  onMounted(() => {
    document.addEventListener('visibilitychange', onVisible)
    window.addEventListener('beforeunload', flushOnPageExit)
    window.addEventListener('pagehide', flushOnPageExit)
    window.addEventListener('pageshow', onPageShow)
  })
  onBeforeUnmount(() => {
    document.removeEventListener('visibilitychange', onVisible)
    window.removeEventListener('beforeunload', flushOnPageExit)
    window.removeEventListener('pagehide', flushOnPageExit)
    window.removeEventListener('pageshow', onPageShow)
    unsubscribe()
    const id = boundId
    if (id && ready && !isEditing.value) {
      const content = draft.value
      const extra = resolveExtraForPersist(id, content)
      if (signatureOf(content, extra) !== lastSynced) {
        rememberPending(id, content, extra)
        void persist(id, content, extra)
      }
    }
    clearTimers()
  })

  return { flush, reload }
}
