// @vitest-environment jsdom
import { defineComponent, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useConversationDraft } from '@/composables/useConversationDraft'
import { DRAFT_DEBOUNCE_MS } from '@/utils/draft'
import { readPendingDraft, writePendingDraft } from '@/utils/draft-pending'

const { getDraft, saveDraft, clearDraft, flushDraftKeepalive, userState } = vi.hoisted(() => ({
  getDraft: vi.fn(),
  saveDraft: vi.fn(),
  clearDraft: vi.fn(),
  flushDraftKeepalive: vi.fn(),
  userState: {
    token: '',
    loggedInUser: { user_id: '' },
  },
}))

vi.mock('@/services/conversation.service', () => ({
  conversationService: { getDraft, saveDraft, clearDraft, flushDraftKeepalive },
}))
vi.mock('@/stores/user', () => ({
  useUserStore: () => userState,
}))
vi.mock('vue-sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }))

const mounted: Array<{ unmount: () => void }> = []

function track<T extends { unmount: () => void }>(wrapper: T): T {
  mounted.push(wrapper)
  const unmount = wrapper.unmount.bind(wrapper)
  wrapper.unmount = () => {
    const index = mounted.indexOf(wrapper)
    if (index >= 0) mounted.splice(index, 1)
    unmount()
  }
  return wrapper
}

async function setup(initial = '') {
  const draft = ref(initial)
  const conversationId = ref('conv_1')
  const isEditing = ref(false)
  let extra = { replyToMessageId: null as string | null, atUsers: [] as { user_id: string; name: string }[] }
  const applied: string[] = []
  const Comp = defineComponent({
    setup() {
      return useConversationDraft({
        conversationId,
        draft,
        isEditing,
        getExtra: () => extra,
        applyExtra: (next) => {
          extra = { replyToMessageId: next.replyToMessageId, atUsers: [...next.atUsers] }
          applied.push(next.replyToMessageId || '')
        },
      })
    },
    template: '<div />',
  })
  const wrapper = track(mount(Comp))
  return {
    draft,
    conversationId,
    isEditing,
    extra: () => extra,
    applied,
    flush: wrapper.vm.flush as () => Promise<void>,
    reload: wrapper.vm.reload as () => Promise<void>,
    wrapper,
  }
}

describe('useConversationDraft', () => {
  afterEach(() => {
    for (const wrapper of mounted.splice(0)) wrapper.unmount()
  })

  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    localStorage.clear()
    userState.token = ''
    userState.loggedInUser.user_id = ''
    setActivePinia(createPinia())
    getDraft.mockResolvedValue(null)
    saveDraft.mockImplementation(async (_id: string, body: { content: string }) => ({
      conversation_id: 'conv_1',
      content: body.content,
      draft_preview: body.content,
      updated_at: 1,
    }))
    clearDraft.mockResolvedValue(undefined)
  })

  it('restores a draft, debounces saves, and deletes when the input is emptied', async () => {
    getDraft.mockResolvedValue({
      conversation_id: 'conv_1',
      content: '上次的',
      draft_preview: '上次的',
      reply_to_message_id: 'msg_1',
      reply_to: { message_id: 'msg_1', state: 'active' },
      at_users: [{ user_id: 'usr_b', name: '小明' }],
      updated_at: 1,
    })
    const api = await setup()
    await api.reload()
    expect(api.draft.value).toBe('上次的')
    expect(api.extra().replyToMessageId).toBe('msg_1')
    expect(api.extra().atUsers).toEqual([{ user_id: 'usr_b', name: '小明' }])

    api.draft.value = '新的'
    await vi.advanceTimersByTimeAsync(DRAFT_DEBOUNCE_MS - 1)
    expect(saveDraft).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)
    expect(saveDraft).toHaveBeenCalledWith('conv_1', expect.objectContaining({
      content: '新的',
      reply_to_message_id: 'msg_1',
      at_users: [{ user_id: 'usr_b', name: '小明' }],
    }))

    api.draft.value = '   '
    await vi.advanceTimersByTimeAsync(DRAFT_DEBOUNCE_MS)
    expect(clearDraft).toHaveBeenCalledWith('conv_1')
  })

  it('does not upload the editor text and still accepts a remote draft while clean', async () => {
    const { publishDraftUpdate } = await import('@/utils/draft-bus')
    const { useListStore } = await import('@/stores/list')
    useListStore().conversations = [{ conversation_id: 'conv_1', draft_preview: null } as never]
    const api = await setup()
    await api.reload()
    expect(api.draft.value).toBe('')

    api.draft.value = '正在写'
    api.isEditing.value = true
    api.draft.value = '编辑中的原文'
    await vi.advanceTimersByTimeAsync(DRAFT_DEBOUNCE_MS)
    expect(saveDraft).not.toHaveBeenCalled()

    api.isEditing.value = false
    api.draft.value = '正在写'
    publishDraftUpdate({
      conversation_id: 'conv_1',
      content: '另一台设备',
      draft_preview: '另一台设备',
      cleared: false,
      updated_at: 2,
    })
    expect(api.draft.value).toBe('正在写')
    expect(useListStore().conversations[0].draft_preview).toBe('另一台设备')

    await vi.advanceTimersByTimeAsync(DRAFT_DEBOUNCE_MS)
    expect(saveDraft).toHaveBeenCalled()
    saveDraft.mockClear()

    await api.flush()
    publishDraftUpdate({
      conversation_id: 'conv_1',
      content: '另一台设备改好了',
      draft_preview: '另一台设备改好了',
      reply_to_message_id: null,
      at_users: [],
      cleared: false,
      updated_at: 3,
    })
    expect(api.draft.value).toBe('另一台设备改好了')
    expect(useListStore().conversations[0].draft_preview).toBe('另一台设备改好了')
  })

  /** 回复目标按会话存放（和真实组件一样：getExtra 只认草稿所属会话的回复） */
  async function setupPerConversation() {
    const draft = ref('')
    const conversationId = ref('conv_1')
    const replies = new Map<string, string>()
    const Comp = defineComponent({
      setup() {
        return useConversationDraft({
          conversationId,
          draft,
          isEditing: ref(false),
          getExtra: id => ({ replyToMessageId: (id && replies.get(id)) || null, atUsers: [] }),
          applyExtra: () => {},
        })
      },
      template: '<div />',
    })
    const wrapper = track(mount(Comp))
    await (wrapper.vm.reload as () => Promise<void>)()
    return { draft, conversationId, replies, wrapper }
  }

  it('从私聊点到群聊（路由参数先变、旧组件再卸载）不会把私聊草稿写进群', async () => {
    const api = await setupPerConversation()
    api.replies.set('conv_1', 'msg_1')
    api.draft.value = '还没到 0.5 秒'
    // 私聊、群聊共用 :id：路由参数已经是群 ID，旧组件同一轮里被卸载，watch 来不及执行
    api.conversationId.value = 'grp_1'
    api.wrapper.unmount()
    await vi.advanceTimersByTimeAsync(DRAFT_DEBOUNCE_MS * 2)
    expect(saveDraft).not.toHaveBeenCalledWith('grp_1', expect.anything())
    expect(clearDraft).not.toHaveBeenCalledWith('grp_1')
    expect(saveDraft).toHaveBeenCalledTimes(1)
    expect(saveDraft).toHaveBeenCalledWith('conv_1', expect.objectContaining({
      content: '还没到 0.5 秒',
      reply_to_message_id: 'msg_1',
    }))
  })

  it('同一组件里切换会话：旧会话草稿保留回复目标，快速连切不会清掉还没加载完的会话草稿', async () => {
    const api = await setupPerConversation()
    api.replies.set('conv_1', 'msg_1')
    api.draft.value = '带回复的草稿'
    await vi.advanceTimersByTimeAsync(DRAFT_DEBOUNCE_MS)
    expect(saveDraft).toHaveBeenLastCalledWith('conv_1', expect.objectContaining({ reply_to_message_id: 'msg_1' }))
    saveDraft.mockClear()

    api.draft.value = '带回复的草稿，再改一下'
    api.conversationId.value = 'conv_2'
    await nextTick()
    expect(saveDraft).toHaveBeenCalledWith('conv_1', expect.objectContaining({
      content: '带回复的草稿，再改一下',
      reply_to_message_id: 'msg_1',
    }))

    // conv_2 的草稿还没拉回来就又切走：不能把空输入框当成“清空 conv_2 草稿”
    api.draft.value = ''
    api.conversationId.value = 'conv_3'
    await nextTick()
    await vi.advanceTimersByTimeAsync(DRAFT_DEBOUNCE_MS * 2)
    expect(clearDraft).not.toHaveBeenCalledWith('conv_2')
    expect(saveDraft).not.toHaveBeenCalledWith('conv_2', expect.anything())
  })

  function signInUser() {
    userState.loggedInUser.user_id = 'usr_me'
    userState.token = 'token'
  }

  it('打字后 0.5 秒内刷新，输入框里的字还在', async () => {
    signInUser()
    const first = await setup()
    await first.reload()
    first.draft.value = '刚打的字'
    expect(saveDraft).not.toHaveBeenCalled()

    window.dispatchEvent(new Event('beforeunload'))
    window.dispatchEvent(new Event('pagehide'))
    expect(flushDraftKeepalive).toHaveBeenCalledTimes(1)
    expect(flushDraftKeepalive).toHaveBeenCalledWith('conv_1', expect.objectContaining({
      content: '刚打的字',
    }), 'token')
    expect(readPendingDraft('usr_me', 'conv_1')?.content).toBe('刚打的字')

    // 刷新会取消还在飞的 axios；本地镜像留下，新页面再补写
    saveDraft.mockRejectedValue({ status: 0, code: 'NETWORK_ERROR' })
    first.wrapper.unmount()
    await Promise.resolve()

    getDraft.mockResolvedValue(null)
    const second = await setup()
    await second.reload()
    expect(second.draft.value).toBe('刚打的字')
    expect(saveDraft).toHaveBeenCalledWith('conv_1', expect.objectContaining({ content: '刚打的字' }))
  })

  it('重新编辑放回输入框后马上刷新，原文还在，不会被服务端旧草稿盖掉', async () => {
    signInUser()
    const first = await setup()
    await first.reload()
    first.draft.value = '已有'
    await vi.advanceTimersByTimeAsync(DRAFT_DEBOUNCE_MS)
    saveDraft.mockClear()
    flushDraftKeepalive.mockClear()

    // 和私聊/群聊 handleReedit 一样：已有内容时把撤回原文接在后面
    first.draft.value = '已有撤回的原文'
    expect(saveDraft).not.toHaveBeenCalled()
    window.dispatchEvent(new Event('beforeunload'))
    expect(flushDraftKeepalive).toHaveBeenCalledWith('conv_1', expect.objectContaining({
      content: '已有撤回的原文',
    }), 'token')

    saveDraft.mockRejectedValue({ status: 0, code: 'NETWORK_ERROR' })
    first.wrapper.unmount()
    await Promise.resolve()

    getDraft.mockResolvedValue({
      conversation_id: 'conv_1',
      content: '已有',
      draft_preview: '已有',
      updated_at: 1,
    })
    const second = await setup()
    await second.reload()
    expect(second.draft.value).toBe('已有撤回的原文')
  })

  it('切到后台时不等防抖就提交；编辑中的原文不当成草稿', async () => {
    const api = await setup()
    await api.reload()
    api.draft.value = '切走了'
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' })
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(0)
    expect(saveDraft).toHaveBeenCalledWith('conv_1', expect.objectContaining({ content: '切走了' }))

    saveDraft.mockClear()
    flushDraftKeepalive.mockClear()
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' })
    api.isEditing.value = true
    api.draft.value = '编辑原文'
    window.dispatchEvent(new Event('beforeunload'))
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(DRAFT_DEBOUNCE_MS)
    expect(saveDraft).not.toHaveBeenCalled()
    expect(flushDraftKeepalive).not.toHaveBeenCalled()
  })

  it('本地镜像和服务端一致时直接采用服务端，不再重复上传', async () => {
    signInUser()
    writePendingDraft('usr_me', 'conv_1', {
      content: '刚打的字',
      replyToMessageId: null,
      atUsers: [],
      savedAt: 1,
    })
    getDraft.mockResolvedValue({
      conversation_id: 'conv_1',
      content: '刚打的字',
      draft_preview: '刚打的字',
      updated_at: 2,
    })
    const api = await setup()
    await api.reload()
    expect(api.draft.value).toBe('刚打的字')
    expect(saveDraft).not.toHaveBeenCalled()
    expect(readPendingDraft('usr_me', 'conv_1')).toBeNull()
  })
})
