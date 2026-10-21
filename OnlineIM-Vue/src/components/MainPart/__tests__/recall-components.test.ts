// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { toServerTimestamp } from '@/utils/conversation-time'

const toastError = vi.fn()
vi.mock('vue-sonner', () => ({ toast: { error: toastError, success: vi.fn(), info: vi.fn() } }))

const recallMessage = vi.fn()
vi.mock('@/services/message.service', () => ({ MessageService: { recallMessage } }))

const handleMessageRecalled = vi.fn()
const recalledOriginal = vi.fn()
vi.mock('@/stores/history', () => ({ useHistoryStore: () => ({ handleMessageRecalled, recalledOriginal }) }))

const message = (extra: Record<string, unknown> = {}) => ({
  message_id: 'msg_1',
  conversation_id: 'conv_1',
  sender_info: { user_id: 'usr_me' },
  message_type: 'text',
  seq_id: '7',
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

describe('recall components', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    document.body.innerHTML = ''
  })

  it('context menu shows 撤回 last and only before the deadline', async () => {
    const { default: MessageActions } = await import('@/components/MainPart/MessageActions.vue')
    const open = mount(MessageActions, {
      props: { canCopy: true, canReply: true, recallUntil: Date.now() + 60_000 },
      slots: { default: '<span data-testid="bubble">hi</span>' },
      attachTo: document.body,
    })
    expect(await menuItems(open)).toEqual(['复制', '回复', '撤回'])
    document.querySelector<HTMLElement>('[data-testid="recall-action"]')!.click()
    await flushPromises()
    expect(open.emitted('recall')).toHaveLength(1)
    open.unmount()
    document.body.innerHTML = ''

    const expired = mount(MessageActions, {
      props: { canCopy: true, canReply: true, recallUntil: Date.now() - 1 },
      slots: { default: '<span data-testid="bubble">hi</span>' },
      attachTo: document.body,
    })
    expect(await menuItems(expired)).toEqual(['复制', '回复'])
    expired.unmount()
  })

  it('recall notice wording and 重新编辑 button', async () => {
    const { default: RecallNotice } = await import('@/components/MainPart/RecallNotice.vue')
    const own = mount(RecallNotice, { props: { isSelf: true, canReedit: true } })
    expect(own.get('[data-testid="recall-notice-text"]').text()).toBe('你撤回了一条消息')
    await own.get('[data-testid="recall-reedit"]').trigger('click')
    expect(own.emitted('reedit')).toHaveLength(1)
    const other = mount(RecallNotice, { props: { isSelf: false, senderName: 'Bob' } })
    expect(other.get('[data-testid="recall-notice-text"]').text()).toBe('Bob 撤回了一条消息')
    expect(other.find('[data-testid="recall-reedit"]').exists()).toBe(false)
  })

  it('confirm dialog: confirm, cancel button, Esc and backdrop', async () => {
    const { default: RecallConfirmDialog } = await import('@/components/MainPart/RecallConfirmDialog.vue')
    const dialog = mount(RecallConfirmDialog, { props: { open: true }, attachTo: document.body })
    await flushPromises()
    expect(document.querySelector('[data-testid="recall-confirm"]')?.textContent).toContain('撤回这条消息？')
    document.querySelector<HTMLElement>('[data-testid="recall-confirm-button"]')!.click()
    document.querySelector<HTMLElement>('[data-testid="recall-cancel"]')!.click()
    document.querySelector<HTMLElement>('[data-testid="recall-confirm-button"]')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    document.querySelector<HTMLElement>('[data-testid="recall-confirm-overlay"]')!.click()
    expect(dialog.emitted('confirm')).toHaveLength(1)
    expect(dialog.emitted('cancel')).toHaveLength(3)
    await dialog.setProps({ open: false })
    expect(document.querySelector('[data-testid="recall-confirm"]')).toBeNull()
    dialog.unmount()
  })

  it('useMessageRecall: asks first, recalls through REST and updates the chat locally', async () => {
    const { useMessageRecall } = await import('@/composables/useMessageRecall')
    const beforeRecall = vi.fn()
    const recall = useMessageRecall(() => 'usr_me', { beforeRecall })
    const target = message()
    recall.request(target as never)
    expect(recall.confirmOpen.value).toBe(true)
    recall.cancel()
    expect(recall.confirmOpen.value).toBe(false)
    expect(recallMessage).not.toHaveBeenCalled()

    recall.request(target as never)
    recallMessage.mockResolvedValueOnce(undefined)
    expect(await recall.confirm()).toBe(true)
    expect(beforeRecall).toHaveBeenCalledWith(target)
    expect(recallMessage).toHaveBeenCalledWith('msg_1')
    expect(handleMessageRecalled).toHaveBeenCalledWith({ message_id: 'msg_1', conversation_id: 'conv_1', recall_user_id: 'usr_me', seq_id: '7' })
    expect(recall.confirmOpen.value).toBe(false)
  })

  it('useMessageRecall: expired / already recalled / server errors', async () => {
    const { useMessageRecall } = await import('@/composables/useMessageRecall')
    const recall = useMessageRecall(() => 'usr_me')
    recall.request(message({ timestamp: toServerTimestamp(Date.now() - 3 * 60_000) }) as never)
    expect(recall.confirmOpen.value).toBe(false)
    expect(toastError).toHaveBeenCalledWith('消息发送已超过2分钟，无法撤回')

    recall.request(message() as never)
    recallMessage.mockRejectedValueOnce({ status: 409, code: 'MESSAGE_ALREADY_RECALLED', message: '消息已撤回' })
    expect(await recall.confirm()).toBe(false)
    expect(toastError).toHaveBeenLastCalledWith('消息已撤回')
    expect(handleMessageRecalled).toHaveBeenCalledTimes(1)

    recall.request(message() as never)
    recallMessage.mockRejectedValueOnce({ status: 400, code: 'RECALL_WINDOW_EXPIRED', message: '消息发送已超过2分钟，无法撤回' })
    toastError.mockClear()
    expect(await recall.confirm()).toBe(false)
    expect(toastError).not.toHaveBeenCalled()
    expect(handleMessageRecalled).toHaveBeenCalledTimes(1)
  })
})
