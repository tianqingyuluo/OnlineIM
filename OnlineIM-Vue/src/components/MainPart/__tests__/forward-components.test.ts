// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

const toastError = vi.fn()
const toastSuccess = vi.fn()
const toastInfo = vi.fn()
vi.mock('vue-sonner', () => ({ toast: { error: toastError, success: toastSuccess, info: toastInfo } }))

const forwardMessages = vi.fn()
vi.mock('@/services/message.service', () => ({ MessageService: { forwardMessages } }))

const handleIncomingMessage = vi.fn()
vi.mock('@/stores/history', () => ({ useHistoryStore: () => ({ handleIncomingMessage }) }))

const listStoreMock = {
  friends: [
    { friendship_id: 'f1', created_at: '', friend_info: { user_id: 'usr_wang', username: 'wang', nickname: '王小二', remark: '老王' } },
    { friendship_id: 'f2', created_at: '', friend_info: { user_id: 'usr_li', username: 'li', nickname: '李四' } },
  ],
  groups: [
    { group_id: 'grp_home', name: '家人群', owner_id: 'usr_x', member_count: 5, my_role: 'member', create_at: '' },
  ],
}
vi.mock('@/stores/list', () => ({ useListStore: () => listStoreMock }))

const record = JSON.stringify({
  title: '群聊“家人群”的聊天记录',
  count: 4,
  items: [
    { sender_id: 'usr_a', sender_name: '小明', message_type: 'text', content: '第一条' },
    { sender_id: 'usr_b', sender_name: '小红', message_type: 'image', content: '{"file_id":"upl_1","type":"image","url":"/a.png","name":"a.png","size":1,"mime":"image/png"}' },
    { sender_id: 'usr_a', sender_name: '小明', message_type: 'text', content: '第三条', forwarded_from: { user_id: 'usr_o', display_name: '原作者' } },
    { sender_id: 'usr_b', sender_name: '小红', message_type: 'chat_record', content: JSON.stringify({ title: '内层记录', count: 1, items: [{ sender_id: 'usr_c', sender_name: '小刚', message_type: 'text', content: '内层' }] }) },
  ],
})

const message = (id: string, extra: Record<string, unknown> = {}) => ({
  message_id: id,
  conversation_id: 'conv_src',
  sender_info: { user_id: 'usr_me' },
  message_type: 'text',
  seq_id: '1',
  content: id,
  status: 0,
  delivery_state: 'sent',
  timestamp: '',
  is_recalled: false,
  client_message_id: id,
  ...extra,
})

describe('forward components', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    document.body.innerHTML = ''
  })

  it('shows the forwarded-from label and renders chat records as an openable card', async () => {
    const { default: UserTextArea } = await import('@/components/MainPart/UserTextArea.vue')
    const forwarded = mount(UserTextArea, {
      props: { message: '你好', messageType: 'text', forwardedFrom: { user_id: 'usr_o', display_name: '原作者' } },
    })
    expect(forwarded.get('[data-testid="forwarded-label"]').text()).toBe('转发自 原作者')
    const recalled = mount(UserTextArea, {
      props: { message: '你好', recalled: true, forwardedFrom: { user_id: 'usr_o', display_name: '原作者' } },
    })
    expect(recalled.find('[data-testid="forwarded-label"]').exists()).toBe(false)

    const card = mount(UserTextArea, { props: { message: record, messageType: 'chat_record' }, attachTo: document.body })
    expect(card.get('[data-testid="chat-record-title"]').text()).toBe('群聊“家人群”的聊天记录')
    expect(card.findAll('[data-testid="chat-record-line"]').map(line => line.text()))
      .toEqual(['小明: 第一条', '小红: [图片]', '小明: 第三条'])
    expect(card.text()).toContain('聊天记录 · 4 条')

    await card.get('[data-testid="chat-record-card"]').trigger('click')
    const viewer = document.querySelector('[data-testid="chat-record-viewer"]')!
    expect(viewer).not.toBeNull()
    expect(viewer.querySelectorAll('[data-testid="chat-record-item"]')).toHaveLength(4)
    expect(viewer.textContent).toContain('转发自 原作者')
    expect(viewer.querySelector('[data-message-type="image"] img')).not.toBeNull()
    // 嵌套的聊天记录也能再打开
    ;(viewer.querySelector('[data-message-type="chat_record"] [data-testid="chat-record-card"]') as HTMLElement).click()
    await flushPromises()
    expect(document.querySelectorAll('[data-testid="chat-record-viewer"]')).toHaveLength(2)
    expect(document.body.textContent).toContain('内层')
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()
    expect(document.querySelectorAll('[data-testid="chat-record-viewer"]').length).toBeLessThan(2)
    card.unmount()
  })

  it('target picker searches friends and groups, limits selection and emits targets', async () => {
    const { default: Picker } = await import('@/components/MainPart/ForwardTargetPicker.vue')
    const wrapper = mount(Picker, { props: { open: false, mode: 'merged', messageCount: 3 }, attachTo: document.body })
    await wrapper.setProps({ open: true })
    const picker = () => document.querySelector('[data-testid="forward-picker"]') as HTMLElement
    expect(picker().dataset.mode).toBe('merged')
    expect(picker().textContent).toContain('合并转发')
    const targets = () => [...document.querySelectorAll<HTMLElement>('[data-testid="forward-target"]')].map(el => el.dataset.targetId)
    expect(targets()).toEqual(['usr_wang', 'usr_li', 'grp_home'])
    expect(picker().textContent).toContain('老王')

    const search = document.querySelector<HTMLInputElement>('[data-testid="forward-search"]')!
    search.value = '王小'
    search.dispatchEvent(new Event('input'))
    await flushPromises()
    expect(targets()).toEqual(['usr_wang'])
    search.value = '家人'
    search.dispatchEvent(new Event('input'))
    await flushPromises()
    expect(targets()).toEqual(['grp_home'])
    document.querySelector<HTMLElement>('[data-target-id="grp_home"]')!.click()
    search.value = 'li'
    search.dispatchEvent(new Event('input'))
    await flushPromises()
    document.querySelector<HTMLElement>('[data-target-id="usr_li"]')!.click()
    await flushPromises()
    expect(document.querySelector('[data-testid="forward-selected"]')!.textContent).toContain('家人群')
    document.querySelector<HTMLElement>('[data-testid="forward-confirm"]')!.click()
    expect(wrapper.emitted('confirm')?.[0]).toEqual([['grp_home', 'usr_li']])

    search.value = 'nobody'
    search.dispatchEvent(new Event('input'))
    await flushPromises()
    expect(document.querySelector('[data-testid="forward-empty"]')).not.toBeNull()
    wrapper.unmount()
  })

  it('context menu offers 转发 and 多选 only when allowed', async () => {
    const { default: MessageActions } = await import('@/components/MainPart/MessageActions.vue')
    const wrapper = mount(MessageActions, {
      props: { canForward: true, canSelect: true, canCopy: false, canReply: false },
      slots: { default: '<span data-testid="bubble">hi</span>' },
      attachTo: document.body,
    })
    await wrapper.get('[data-testid="bubble"]').trigger('contextmenu')
    await flushPromises()
    const items = [...document.querySelectorAll('[role="menuitem"]')].map(item => item.textContent?.trim())
    expect(items).toEqual(['转发', '多选'])
    wrapper.unmount()
  })

  it('composable forwards one / selected messages and applies the results', async () => {
    const { useMessageForward } = await import('@/composables/useMessageForward')
    const messages = [message('msg_1'), message('msg_2'), message('msg_3', { is_recalled: true }), message('local_x', { delivery_state: 'sending' })]
    const forward = useMessageForward(() => 'conv_src', () => messages as never)

    forward.forwardOne(messages[2] as never)
    expect(forward.pickerOpen.value).toBe(false)
    expect(toastInfo).toHaveBeenCalledWith('这条消息不能转发')

    forward.startSelect(messages[1] as never)
    forward.toggleSelect(messages[0] as never)
    forward.toggleSelect(messages[2] as never)
    forward.toggleSelect(messages[3] as never)
    expect(forward.selectedIds.value).toEqual(['msg_2', 'msg_1'])
    forward.forwardSelected('merged')
    expect(forward.pickerOpen.value).toBe(true)
    expect(forward.pickerIds.value).toEqual(['msg_1', 'msg_2'])

    forwardMessages.mockResolvedValue({
      mode: 'merged',
      results: [
        { target_id: 'usr_li', conversation_id: 'conv_li', messages: [message('msg_new1', { message_type: 'chat_record' })] },
        { target_id: 'grp_home', conversation_id: 'grp_home', messages: [message('msg_new2', { message_type: 'chat_record' })] },
      ],
    })
    await forward.confirm(['usr_li', 'grp_home'])
    const request = forwardMessages.mock.calls[0][0]
    expect(request).toMatchObject({ source_conversation_id: 'conv_src', message_ids: ['msg_1', 'msg_2'], targets: ['usr_li', 'grp_home'], mode: 'merged' })
    expect(request.client_request_id).toMatch(/^fwd_[A-Za-z0-9_-]+$/)
    expect(handleIncomingMessage).toHaveBeenCalledTimes(2)
    expect(handleIncomingMessage.mock.calls[0][1]).toBe(false)
    expect(handleIncomingMessage.mock.calls[1][1]).toBe(true)
    expect(toastSuccess).toHaveBeenCalledWith('已转发到 2 个会话')
    expect(forward.selecting.value).toBe(false)
    expect(forward.pickerOpen.value).toBe(false)

    // 失败：404/409 提示服务端消息，保持选择器打开以便重试
    forward.forwardOne(messages[0] as never)
    forwardMessages.mockRejectedValue({ status: 409, message: '已撤回的消息不能转发' })
    await forward.confirm(['usr_li'])
    expect(toastError).toHaveBeenCalledWith('已撤回的消息不能转发')
    expect(forward.pickerOpen.value).toBe(true)
    forwardMessages.mockRejectedValue({ status: 403, message: '只能转发给好友' })
    toastError.mockClear()
    await forward.confirm(['usr_li'])
    expect(toastError).not.toHaveBeenCalled()
  })
})
