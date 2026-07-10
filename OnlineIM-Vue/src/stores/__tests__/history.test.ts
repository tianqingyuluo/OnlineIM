import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const websocketMock = {
  sendMessage: vi.fn(),
  sendReceipt: vi.fn(),
  sendReadReceipt: vi.fn(),
}
const userStoreMock = {
  loggedInUser: {
    user_id: 'usr_me',
    username: 'me',
    nickname: '我',
    avatar_url: '/me.png',
  },
}

vi.mock('@/services/websocket.service', () => ({ websocketService: websocketMock }))
vi.mock('@/stores/user', () => ({ useUserStore: () => userStoreMock }))
vi.mock('@/services/conversation.service', () => ({
  conversationService: {
    getReadState: vi.fn().mockResolvedValue({
      conversation_id: 'conv_1',
      delivered_seq: '0',
      read_seq: '0',
      latest_seq: '0',
      unread_count: 0,
    }),
    getMessageReaders: vi.fn(),
  },
}))
vi.mock('@/services/message.service', () => ({ MessageService: {} }))
vi.mock('@/utils/indexedDB', () => ({ dbService: {} }))

describe('history store message receipt flow', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('moves a message through sending, sent, delivered and read', async () => {
    const { useHistoryStore } = await import('@/stores/history')
    const store = useHistoryStore()
    store.sendMessage('conv_1', 'usr_peer', 'text', 'hello', false)
    const message = store.chatMessages[0]

    expect(message.delivery_state).toBe('sending')
    store.handleMessageAck({
      client_message_id: message.client_message_id,
      message_id: 'msg_1',
      conversation_id: 'conv_1',
      seq_id: '10',
      delivery_state: 'sent',
      server_time: 1,
    })
    expect(message.delivery_state).toBe('sent')

    store.handleReceipt({
      receipt_type: 'delivered',
      message_id: 'msg_1',
      conversation_id: 'conv_1',
      seq_id: '10',
      receiver_id: 'usr_peer',
    })
    expect(message.delivery_state).toBe('delivered')

    store.handleReadReceipt({
      conversation_id: 'conv_1',
      reader_id: 'usr_peer',
      read_seq: '10',
    })
    expect(message.delivery_state).toBe('read')
  })

  it('does not downgrade after duplicate or late receipt events', async () => {
    const { useHistoryStore } = await import('@/stores/history')
    const store = useHistoryStore()
    store.sendMessage('conv_1', 'usr_peer', 'text', 'hello', false)
    const message = store.chatMessages[0]
    store.handleMessageAck({
      client_message_id: message.client_message_id,
      message_id: 'msg_1',
      conversation_id: 'conv_1',
      seq_id: '10',
      delivery_state: 'sent',
      server_time: 1,
    })
    store.handleReadReceipt({ conversation_id: 'conv_1', reader_id: 'usr_peer', read_seq: '10' })
    store.handleReceipt({
      receipt_type: 'delivered',
      message_id: 'msg_1',
      conversation_id: 'conv_1',
      seq_id: '10',
    })
    store.handleMessageAck({
      client_message_id: message.client_message_id,
      message_id: 'msg_1',
      conversation_id: 'conv_1',
      seq_id: '10',
      delivery_state: 'sent',
      server_time: 2,
    })
    expect(message.delivery_state).toBe('read')
  })

  it('retries three times before exposing failure and restores sending on retry', async () => {
    const { useHistoryStore } = await import('@/stores/history')
    const store = useHistoryStore()
    store.sendMessage('conv_1', 'usr_peer', 'text', 'hello', false)
    const message = store.chatMessages[0]

    await vi.advanceTimersByTimeAsync(15_000)
    expect(message.delivery_state).toBe('failed')

    store.retryMessage(message, false, 'usr_peer')
    expect(message.delivery_state).toBe('sending')
    expect(websocketMock.sendMessage).toHaveBeenCalledTimes(4)
  })

  it('does not skip an unseen message when advancing a continuous read cursor', async () => {
    const { useHistoryStore } = await import('@/stores/history')
    const store = useHistoryStore()
    const makeMessage = (id: string, seq: string) => ({
      message_id: id,
      conversation_id: 'conv_1',
      sender_info: { user_id: 'usr_peer', nickname: '对方' },
      message_type: 'text',
      seq_id: seq,
      content: id,
      status: 1,
      delivery_state: 'delivered' as const,
      timestamp: new Date().toISOString(),
      is_recalled: false,
      client_message_id: id,
    })
    store.chatMessages.push(makeMessage('msg_10', '10'), makeMessage('msg_20', '20'))

    store.markMessageVisible(store.chatMessages[1])
    expect(websocketMock.sendReadReceipt).not.toHaveBeenCalled()
    store.markMessageVisible(store.chatMessages[0])
    expect(websocketMock.sendReadReceipt).toHaveBeenCalledWith({
      conversation_id: 'conv_1',
      read_seq: '20',
    })
  })

  it('sends a read cursor only after a visible incoming message', async () => {
    const { useHistoryStore } = await import('@/stores/history')
    const store = useHistoryStore()
    store.chatMessages.push({
      message_id: 'msg_in',
      conversation_id: 'conv_1',
      sender_info: { user_id: 'usr_peer', nickname: '对方' },
      message_type: 'text',
      seq_id: '20',
      content: 'hi',
      status: 1,
      delivery_state: 'delivered',
      timestamp: new Date().toISOString(),
      is_recalled: false,
      client_message_id: 'peer_client',
    })

    store.markMessageVisible(store.chatMessages[0])

    expect(websocketMock.sendReadReceipt).toHaveBeenCalledWith({
      conversation_id: 'conv_1',
      read_seq: '20',
    })
  })
  it('applies a remote delivery cursor to earlier own messages without changing local read state', async () => {
    const { useHistoryStore } = await import('@/stores/history')
    const store = useHistoryStore()
    const makeMessage = (id: string, seq: string) => ({
      message_id: id,
      conversation_id: 'conv_1',
      sender_info: { user_id: 'usr_me', nickname: '我' },
      message_type: 'text',
      seq_id: seq,
      content: id,
      status: 1,
      delivery_state: 'sent' as const,
      timestamp: new Date().toISOString(),
      is_recalled: false,
      client_message_id: id,
    })
    store.chatMessages.push(makeMessage('msg_10', '10'), makeMessage('msg_20', '20'))
    store.readStates.conv_1 = {
      conversation_id: 'conv_1',
      delivered_seq: '0',
      read_seq: '0',
      latest_seq: '20',
      unread_count: 2,
    }

    store.handleReceipt({
      receipt_type: 'delivered',
      message_id: 'msg_20',
      conversation_id: 'conv_1',
      seq_id: '20',
      receiver_id: 'usr_peer',
    })
    store.handleReadReceipt({
      conversation_id: 'conv_1',
      reader_id: 'usr_peer',
      read_seq: '20',
    })

    expect(store.chatMessages.map(message => message.delivery_state)).toEqual(['read', 'read'])
    expect(store.readStates.conv_1.read_seq).toBe('0')
  })

})
