import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
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

  it('打开会话走幂等的 POST /conversations/create', async () => {
    apiMock.post.mockResolvedValue({ data: { conversation_id: 'grp_1' } })
    const { conversationService } = await import('@/services/conversation.service')
    await conversationService.createConversation('grp_1', 'group')
    expect(apiMock.post).toHaveBeenCalledWith('/conversations/create', { target_id: 'grp_1', type: 'group' })
  })
})
