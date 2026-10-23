import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
  defaults: { baseURL: 'http://api.example/api/v1' },
}
const listStore: { conversations: Array<Record<string, unknown>> } = { conversations: [] }

vi.mock('@/services/api.service', () => ({ default: apiMock }))
vi.mock('@/stores/list', () => ({ useListStore: () => listStore }))

describe('conversationService 与后端 ConversationController 路由一致', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.post.mockResolvedValue({ data: { status: 'success' } })
    apiMock.delete.mockResolvedValue({ data: { status: 'success' } })
    listStore.conversations = [{ conversation_id: 'conv_1', is_pinned: false, is_muted: false }]
  })

  it('置顶走 POST /conversations/{id}/top，并更新本地列表', async () => {
    const { conversationService } = await import('@/services/conversation.service')
    await conversationService.topConversation('conv_1')
    expect(apiMock.post).toHaveBeenCalledWith('/conversations/conv_1/top')
    expect(apiMock.put).not.toHaveBeenCalled()
    expect(listStore.conversations[0].is_pinned).toBe(true)

    await conversationService.unTopConversation('conv_1')
    expect(apiMock.delete).toHaveBeenCalledWith('/conversations/conv_1/top')
    expect(listStore.conversations[0].is_pinned).toBe(false)
  })

  it('免打扰走 POST /conversations/{id}/mute，取消走 DELETE', async () => {
    const { conversationService } = await import('@/services/conversation.service')
    await conversationService.muteConversation('conv_1')
    expect(apiMock.post).toHaveBeenCalledWith('/conversations/conv_1/mute')
    expect(listStore.conversations[0].is_muted).toBe(true)

    await conversationService.unmuteConversation('conv_1')
    expect(apiMock.delete).toHaveBeenCalledWith('/conversations/conv_1/mute')
    expect(listStore.conversations[0].is_muted).toBe(false)
    expect(apiMock.put).not.toHaveBeenCalled()
  })

  it('会话列表用服务端的每人 unread_count 初始化未读角标', async () => {
    apiMock.get.mockResolvedValue({
      data: [
        { conversation_id: 'conv_1', unread_count: 3 },
        { conversation_id: 'grp_1', unread_count: 0 },
      ],
    })
    const { conversationService } = await import('@/services/conversation.service')
    const list = await conversationService.getConversations()
    expect(apiMock.get).toHaveBeenCalledWith('/conversations', { params: undefined })
    expect(list.map((c) => c.notreadednumber)).toEqual([3, 0])
    expect(listStore.conversations).toBe(list)
  })

  it('草稿走 /conversations/{id}/draft：保存、读取、清空', async () => {
    apiMock.put.mockResolvedValue({ status: 200, data: { conversation_id: 'conv_1', content: 'hi', draft_preview: 'hi' } })
    apiMock.get.mockResolvedValueOnce({ status: 200, data: { conversation_id: 'conv_1', content: 'hi' } })
    apiMock.get.mockResolvedValueOnce({ status: 204, data: '' })
    const { conversationService } = await import('@/services/conversation.service')
    await conversationService.saveDraft('conv_1', { content: 'hi', reply_to_message_id: 'msg_1', at_users: [{ user_id: 'usr_b', name: '小明' }] })
    expect(apiMock.put).toHaveBeenCalledWith('/conversations/conv_1/draft', {
      content: 'hi', reply_to_message_id: 'msg_1', at_users: [{ user_id: 'usr_b', name: '小明' }],
    })
    await expect(conversationService.getDraft('conv_1')).resolves.toMatchObject({ content: 'hi' })
    await expect(conversationService.getDraft('conv_1')).resolves.toBeNull()
    await conversationService.clearDraft('conv_1')
    expect(apiMock.delete).toHaveBeenCalledWith('/conversations/conv_1/draft')
  })

  it('页面关闭时用 keepalive 提交或删除草稿，失败不抛出', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true })
    vi.stubGlobal('fetch', fetchMock)
    const { conversationService } = await import('@/services/conversation.service')
    const body = { content: '刚打的', reply_to_message_id: null, at_users: [] as { user_id: string; name: string }[] }
    conversationService.flushDraftKeepalive('conv_1', body, 'token-1')
    expect(fetchMock).toHaveBeenCalledWith('http://api.example/api/v1/conversations/conv_1/draft', expect.objectContaining({
      method: 'PUT',
      keepalive: true,
      body: JSON.stringify(body),
      headers: expect.objectContaining({ Authorization: 'Bearer token-1' }),
    }))

    fetchMock.mockClear()
    conversationService.flushDraftKeepalive('conv_1', { ...body, content: '   ' }, 'token-1')
    expect(fetchMock).toHaveBeenCalledWith('http://api.example/api/v1/conversations/conv_1/draft', expect.objectContaining({
      method: 'DELETE',
      keepalive: true,
      body: undefined,
    }))

    fetchMock.mockClear()
    fetchMock.mockRejectedValueOnce(new Error('offline'))
    expect(() => conversationService.flushDraftKeepalive('conv_1', body, 'token-1')).not.toThrow()
    conversationService.flushDraftKeepalive('conv_1', body, '')
    expect(fetchMock).toHaveBeenCalledTimes(1)
    vi.unstubAllGlobals()
  })

  it('打开会话走幂等的 POST /conversations/create', async () => {
    apiMock.post.mockResolvedValue({ data: { conversation_id: 'grp_1' } })
    const { conversationService } = await import('@/services/conversation.service')
    await conversationService.createConversation('grp_1', 'group')
    expect(apiMock.post).toHaveBeenCalledWith('/conversations/create', { target_id: 'grp_1', type: 'group' })
  })
})
