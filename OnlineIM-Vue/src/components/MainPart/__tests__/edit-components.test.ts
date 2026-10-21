// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref } from 'vue'
import { toServerTimestamp } from '@/utils/conversation-time'

const toastError = vi.fn()
vi.mock('vue-sonner', () => ({ toast: { error: toastError, success: vi.fn(), info: vi.fn() } }))

const editMessage = vi.fn()
vi.mock('@/services/message.service', () => ({ MessageService: { editMessage } }))

const handleMessageEdited = vi.fn()
const cancelReplyTarget = vi.fn()
const historyStore = {
  handleMessageEdited,
  cancelReplyTarget,
  replyTarget: null as { conversationId: string; reference: { message_id: string } } | null,
}
vi.mock('@/stores/history', () => ({ useHistoryStore: () => historyStore }))

const message = (extra: Record<string, unknown> = {}) => ({
  message_id: 'msg_1',
  conversation_id: 'conv_1',
  sender_info: { user_id: 'usr_me' },
  message_type: 'text',
  seq_id: '1',
  content: '原文',
  status: 1,
  delivery_state: 'sent',
  timestamp: toServerTimestamp(Date.now() - 10_000),
  is_recalled: false,
  client_message_id: 'c1',
  ...extra,
})

async function menuItems(wrapper: ReturnType<typeof mount>) {
  await wrapper.get('[data-testid="bubble"]').trigger('contextmenu')
  await flushPromises()
  return [...document.querySelectorAll('[role="menuitem"]')].map(item => item.textContent?.trim())
}

describe('edit components', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    historyStore.replyTarget = null
    setActivePinia(createPinia())
    document.body.innerHTML = ''
  })

  it('shows the 已编辑 marker on edited text, not on recalled messages', async () => {
    const { default: UserTextArea } = await import('@/components/MainPart/UserTextArea.vue')
    const edited = mount(UserTextArea, { props: { message: '改过了', edited: true, editedAt: '2026-10-22 01:00:00', isSelf: true } })
    const marker = edited.get('[data-testid="edited-marker"]')
    expect(marker.text()).toBe('已编辑')
    expect(marker.attributes('title')).toBe('编辑于 2026-10-22 01:00:00')
    expect(mount(UserTextArea, { props: { message: '没改' } }).find('[data-testid="edited-marker"]').exists()).toBe(false)
    expect(mount(UserTextArea, { props: { message: '', edited: true, recalled: true } })
      .find('[data-testid="edited-marker"]').exists()).toBe(false)
  })

  it('context menu shows 编辑 only before the deadline', async () => {
    const { default: MessageActions } = await import('@/components/MainPart/MessageActions.vue')
    const open = mount(MessageActions, {
      props: { canCopy: true, canReply: true, editUntil: Date.now() + 60_000 },
      slots: { default: '<span data-testid="bubble">hi</span>' },
      attachTo: document.body,
    })
    expect(await menuItems(open)).toEqual(['复制', '编辑', '回复'])
    document.querySelector<HTMLElement>('[data-testid="edit-action"]')!.click()
    await flushPromises()
    expect(open.emitted('edit')).toHaveLength(1)
    open.unmount()
    document.body.innerHTML = ''

    const expired = mount(MessageActions, {
      props: { canCopy: true, canReply: true, editUntil: Date.now() - 1 },
      slots: { default: '<span data-testid="bubble">hi</span>' },
      attachTo: document.body,
    })
    expect(await menuItems(expired)).toEqual(['复制', '回复'])
    expired.unmount()
  })

  it('composable stashes the draft, saves the edit and restores the draft', async () => {
    const { useMessageEdit } = await import('@/composables/useMessageEdit')
    const draft = ref('写了一半的草稿')
    const focus = vi.fn()
    const edit = useMessageEdit(draft, () => 'usr_me', focus)

    expect(edit.editUntil(message() as never)).toBeGreaterThan(Date.now())
    expect(edit.editUntil(message({ sender_info: { user_id: 'usr_x' } }) as never)).toBe(0)

    const reply = { conversationId: 'conv_1', reference: { message_id: 'msg_quote' } }
    historyStore.replyTarget = reply
    edit.start(message() as never)
    expect(edit.isEditing.value).toBe(true)
    expect(draft.value).toBe('原文')
    expect(cancelReplyTarget).toHaveBeenCalled()
    expect(historyStore.replyTarget).toBeNull()
    expect(focus).toHaveBeenCalled()

    draft.value = '  新内容 '
    const updated = message({ content: '新内容', edited: true, content_revision: 2 })
    editMessage.mockResolvedValue(updated)
    expect(await edit.save()).toBe(true)
    expect(editMessage).toHaveBeenCalledWith('msg_1', { content: '新内容' })
    expect(handleMessageEdited).toHaveBeenCalledWith(updated)
    expect(edit.isEditing.value).toBe(false)
    expect(draft.value).toBe('写了一半的草稿')
    expect(historyStore.replyTarget).toBe(reply)

    // 群聊带 at_users；内容和 @ 都没变直接退出，不请求
    edit.start(message({ at_users: ['usr_a'] }) as never)
    expect(await edit.save(['usr_a'])).toBe(true)
    expect(editMessage).toHaveBeenCalledTimes(1)

    edit.start(message() as never)
    draft.value = '   '
    expect(await edit.save()).toBe(false)
    expect(toastError).toHaveBeenCalledWith('编辑后的内容不能为空')

    // 超过时间窗等终止性错误：退出编辑并恢复草稿；冲突错误保留编辑态
    draft.value = '再改'
    editMessage.mockRejectedValueOnce({ status: 409, code: 'EDIT_CONFLICT', message: '消息刚刚被修改过，请重试' })
    expect(await edit.save()).toBe(false)
    expect(edit.isEditing.value).toBe(true)
    expect(toastError).toHaveBeenCalledWith('消息刚刚被修改过，请重试')
    editMessage.mockRejectedValueOnce({ status: 400, code: 'EDIT_WINDOW_EXPIRED', message: '消息发送已超过2分钟，无法编辑' })
    expect(await edit.save()).toBe(false)
    expect(edit.isEditing.value).toBe(false)
    expect(draft.value).toBe('写了一半的草稿')

    // 过了时间窗的消息不能进入编辑
    edit.start(message({ timestamp: toServerTimestamp(Date.now() - 5 * 60_000) }) as never)
    expect(edit.isEditing.value).toBe(false)
  })
})
