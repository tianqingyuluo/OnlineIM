import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = {
  get: vi.fn(),
  post: vi.fn(),
}

vi.mock('@/services/api.service', () => ({ default: apiMock }))
vi.mock('@/utils/indexedDB', () => ({ dbService: {} }))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ loggedInUser: { user_id: 'usr_me' } }) }))

describe('MessageService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('requests a bounded message context window', async () => {
    apiMock.get.mockResolvedValue({
      data: {
        target_message_id: 'msg_100',
        messages: [],
        has_more_before: true,
        has_more_after: false,
      },
    })
    const { MessageService } = await import('@/services/message.service')

    const context = await MessageService.getMessageContext('conv_1', 'msg_100', 12, 8)

    expect(apiMock.get).toHaveBeenCalledWith('/messages/conv_1/msg_100/context', {
      params: { before: 12, after: 8 },
    })
    expect(context.target_message_id).toBe('msg_100')
  })

  it('accepts the backend sync list response without dropping messages', async () => {
    apiMock.get.mockResolvedValue({
      data: [{ message_id: 'msg_101', conversation_id: 'conv_1' }],
    })
    const { MessageService } = await import('@/services/message.service')

    const messages = await MessageService.syncMessages('conv_1', '100')

    expect(messages).toHaveLength(1)
    expect(messages[0]?.message_id).toBe('msg_101')
  })

  it('sends the HTTP reply contract with a stable client message id', async () => {
    apiMock.post.mockResolvedValue({
      data: { message_id: 'msg_reply', conversation_id: 'grp_1' },
    })
    const { MessageService } = await import('@/services/message.service')

    await MessageService.putMessage(
      'grp_1',
      'text',
      '回复正文',
      'msg_target',
      'client_http_1',
    )

    expect(apiMock.post).toHaveBeenCalledWith('/messages/send', {
      target_id: 'grp_1',
      message_type: 'text',
      content: '回复正文',
      reply_to_message_id: 'msg_target',
      client_message_id: 'client_http_1',
    })
  })
})
