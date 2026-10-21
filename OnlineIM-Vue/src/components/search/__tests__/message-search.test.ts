// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h, nextTick, ref } from 'vue'

const searchMessages = vi.fn()
vi.mock('@/services/message.service', () => ({ MessageService: { searchMessages } }))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ loggedInUser: { user_id: 'usr_a' } }) }))

const result = (id: string, extra: Record<string, unknown> = {}) => ({
  message: {
    message_id: id,
    conversation_id: 'grp_1',
    sender_info: { user_id: 'usr_b', nickname: 'Bob' },
    message_type: 'text',
    seq_id: '1',
    content: `爬山 ${id}`,
    status: 1,
    timestamp: '2026-10-22 01:00:00',
    is_recalled: false,
    client_message_id: id,
  },
  conversation: { conversation_id: 'grp_1', type: 'group', name: '一群' },
  snippet: `<i>爬山</i> ${id}`,
  highlights: [{ start: 3, length: 2 }],
  ...extra,
})

const page = (ids: string[], next: string | null = null) => ({
  keyword: '爬山',
  conversation_id: null,
  results: ids.map(id => result(id)),
  next_cursor: next,
  has_more: !!next,
})

describe('message search components', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('debounces, renders highlighted snippets as text and emits select', async () => {
    const { default: MessageSearchResults } = await import('@/components/search/MessageSearchResults.vue')
    searchMessages.mockResolvedValue(page(['msg_1']))
    const wrapper = mount(MessageSearchResults, { props: { query: '', showConversation: true } })
    expect(wrapper.find('[data-testid="message-search-results"]').exists()).toBe(false)

    await wrapper.setProps({ query: '爬' })
    await wrapper.setProps({ query: ' 爬山 ' })
    expect(wrapper.find('[data-testid="message-search-loading"]').exists()).toBe(true)
    await vi.advanceTimersByTimeAsync(299)
    expect(searchMessages).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)
    await flushPromises()
    expect(searchMessages).toHaveBeenCalledTimes(1)
    expect(searchMessages.mock.calls[0][0]).toEqual({ q: '爬山', conversation_id: undefined, cursor: undefined, size: 20 })

    const row = wrapper.get('[data-testid="message-search-result"]')
    expect(row.get('[data-testid="message-search-title"]').text()).toBe('一群 · Bob')
    expect(row.findAll('[data-testid="search-hit"]').map(mark => mark.text())).toEqual(['爬山'])
    expect(row.get('[data-testid="message-search-snippet"]').text()).toBe('<i>爬山</i> msg_1')
    expect(row.find('i').exists()).toBe(false)
    await row.trigger('click')
    expect(wrapper.emitted('select')?.[0][0]).toMatchObject({ message: { message_id: 'msg_1' } })
  })

  it('loads more with the cursor, skips duplicates, and shows empty state', async () => {
    const { default: MessageSearchResults } = await import('@/components/search/MessageSearchResults.vue')
    searchMessages.mockResolvedValueOnce(page(['msg_1', 'msg_2'], 'c1'))
    const wrapper = mount(MessageSearchResults, { props: { query: '爬山', conversationId: 'grp_1' } })
    await wrapper.setProps({ query: '爬山 ' })
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(searchMessages.mock.calls[0][0]).toMatchObject({ q: '爬山', conversation_id: 'grp_1' })
    expect(wrapper.get('[data-testid="message-search-title"]').text()).toBe('Bob')

    searchMessages.mockResolvedValueOnce(page(['msg_2', 'msg_3']))
    await wrapper.get('[data-testid="message-search-more"]').trigger('click')
    await flushPromises()
    expect(searchMessages.mock.calls[1][0]).toMatchObject({ cursor: 'c1' })
    expect(wrapper.findAll('[data-testid="message-search-result"]').map(row => row.attributes('data-result-message-id')))
      .toEqual(['msg_1', 'msg_2', 'msg_3'])
    expect(wrapper.find('[data-testid="message-search-more"]').exists()).toBe(false)

    searchMessages.mockResolvedValueOnce(page([]))
    await wrapper.setProps({ query: '没有' })
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(wrapper.get('[data-testid="message-search-empty"]').text()).toBe('没有找到相关聊天记录')
  })

  it('ignores stale responses that arrive after a newer search', async () => {
    const { useMessageSearch } = await import('@/composables/useMessageSearch')
    let resolveOld: (value: unknown) => void = () => {}
    searchMessages.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    searchMessages.mockResolvedValueOnce(page(['msg_new']))
    const query = ref('旧')
    let search!: ReturnType<typeof useMessageSearch>
    mount(defineComponent({ setup() { search = useMessageSearch(query); return () => h('div') } }))
    search.searchNow()
    query.value = '新'
    await nextTick()
    search.searchNow()
    await flushPromises()
    resolveOld(page(['msg_old']))
    await flushPromises()
    expect(search.results.value.map(item => item.message.message_id)).toEqual(['msg_new'])
    expect(searchMessages.mock.calls[0][1]?.aborted).toBe(true)

    query.value = '  '
    await nextTick()
    expect(search.keyword.value).toBe('')
    expect(search.results.value).toEqual([])
  })

  it('shows the error message when the search fails', async () => {
    const { default: MessageSearchResults } = await import('@/components/search/MessageSearchResults.vue')
    searchMessages.mockRejectedValueOnce({ status: 403, message: '无权搜索该会话' })
    const wrapper = mount(MessageSearchResults, { props: { query: '' } })
    await wrapper.setProps({ query: 'x' })
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(wrapper.get('[data-testid="message-search-error"]').text()).toBe('无权搜索该会话')
  })

  it('conversation search panel searches only this conversation and closes on Escape', async () => {
    const { default: ConversationSearchPanel } = await import('@/components/search/ConversationSearchPanel.vue')
    searchMessages.mockResolvedValue(page(['msg_1']))
    const wrapper = mount(ConversationSearchPanel, { props: { open: true, conversationId: 'conv_1' }, attachTo: document.body })
    await wrapper.get('[data-testid="conversation-search-input"]').setValue('爬山')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(searchMessages.mock.calls[0][0]).toMatchObject({ q: '爬山', conversation_id: 'conv_1' })
    await wrapper.get('[data-testid="message-search-result"]').trigger('click')
    expect(wrapper.emitted('select')).toHaveLength(1)
    await wrapper.get('[data-testid="conversation-search-input"]').trigger('keydown', { key: 'Escape' })
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })
})
