import { beforeEach, describe, expect, it, vi } from 'vitest'

const userStore = {
  token: 'test-token',
  loggedInUser: { user_id: 'usr_test' },
}

const historyStore = {
  handleWebSocketMessage: vi.fn(),
  handleGroupWebSocketMessage: vi.fn(),
  handleMessageAck: vi.fn(),
  handleReceipt: vi.fn(),
  handleReadReceipt: vi.fn(),
  handleServerError: vi.fn(),
  syncActiveConversation: vi.fn(),
}

const dbServiceMock = {
  getOutboundQueue: vi.fn(),
  getAllOutboundQueue: vi.fn(),
  addOutboundMessage: vi.fn(),
  markOutboundSent: vi.fn(),
  enqueueReceipt: vi.fn(),
  getPendingReceipts: vi.fn(),
  deleteReceipt: vi.fn(),
  deleteReceiptIfCurrent: vi.fn(),
}

vi.mock('@/stores/user', () => ({ useUserStore: () => userStore }))
vi.mock('@/stores/history', () => ({ useHistoryStore: () => historyStore }))
vi.mock('@/utils/indexedDB.ts', () => ({ dbService: dbServiceMock }))
vi.mock('vue-sonner', () => ({
  toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}))

class FakeWebSocket {
  static CONNECTING = 0
  static OPEN = 1
  static CLOSING = 2
  static CLOSED = 3
  static instances: FakeWebSocket[] = []

  readyState = FakeWebSocket.CONNECTING
  sent: string[] = []
  onopen: ((event: Event) => void) | null = null
  onmessage: ((event: MessageEvent) => void) | null = null
  onerror: ((event: Event) => void) | null = null
  onclose: ((event: CloseEvent) => void) | null = null

  constructor(public readonly url: string) {
    FakeWebSocket.instances.push(this)
  }

  open(): void {
    this.readyState = FakeWebSocket.OPEN
    this.onopen?.(new Event('open'))
  }

  send(message: string): void {
    this.sent.push(message)
  }

  close(): void {
    this.readyState = FakeWebSocket.CLOSED
    this.onclose?.(new CloseEvent('close'))
  }

  receive(data: unknown): void {
    this.onmessage?.(new MessageEvent('message', { data: JSON.stringify(data) }))
  }
}

describe('WebSocketService', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    FakeWebSocket.instances = []
    dbServiceMock.getOutboundQueue.mockResolvedValue([])
    dbServiceMock.getAllOutboundQueue.mockResolvedValue([])
    dbServiceMock.getPendingReceipts.mockResolvedValue([])
    dbServiceMock.enqueueReceipt.mockResolvedValue(1)
    vi.stubGlobal('WebSocket', FakeWebSocket)
    vi.stubGlobal('crypto', { randomUUID: () => 'generated-client-id' })
    vi.spyOn(Math, 'random').mockReturnValue(0.5)
  })

  it('does not create a second socket while the first is connecting', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()

    service.connect()
    service.connect()

    expect(FakeWebSocket.instances).toHaveLength(1)
  })

  it('ignores close events from an obsolete socket', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()

    service.connect()
    const first = FakeWebSocket.instances[0]
    service.disconnect()
    service.manualRetry()
    const second = FakeWebSocket.instances[1]
    second.open()

    first.onclose?.(new CloseEvent('close'))

    expect(service.connectionState.value).toBe('online')
    expect(FakeWebSocket.instances).toHaveLength(2)
  })

  it('closes a connection after two missed heartbeat acknowledgements', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const { HEARTBEAT_INTERVAL_MS } = await import('@/utils/reconnect-utils')
    const service = new WebSocketService()

    service.connect()
    const socket = FakeWebSocket.instances[0]
    socket.open()

    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL_MS * 2)

    expect(socket.readyState).toBe(FakeWebSocket.CLOSED)
    expect(service.connectionState.value).toBe('reconnecting')
  })

  it('keeps the offline state visible during background retries', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()

    service.connect()
    FakeWebSocket.instances[0].open()

    for (let attempt = 0; attempt < 5; attempt++) {
      FakeWebSocket.instances.at(-1)?.close()
      if (attempt < 4) {
        await vi.advanceTimersByTimeAsync(30_000)
      }
    }

    expect(service.connectionState.value).toBe('offline')
    await vi.advanceTimersByTimeAsync(30_000)

    expect(FakeWebSocket.instances.at(-1)?.readyState).toBe(FakeWebSocket.CONNECTING)
    expect(service.connectionState.value).toBe('offline')
  })

  it('persists the generated client id in both queue metadata and payload', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()

    service.sendMessage({
      type: 'PRIVATE_MESSAGE_REQUEST',
      message: { conversation_id: 'conv_1', content: 'hello' },
    })
    await vi.waitFor(() => expect(dbServiceMock.addOutboundMessage).toHaveBeenCalledOnce())

    expect(dbServiceMock.addOutboundMessage).toHaveBeenCalledWith(expect.objectContaining({
      client_local_id: 'generated-client-id',
      payload: expect.objectContaining({ client_message_id: 'generated-client-id' }),
    }))
  })

  it('flushes pending messages and syncs history after reconnecting', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()
    dbServiceMock.getAllOutboundQueue.mockResolvedValue([{
      conversation_id: 'conv_1',
      type: 'PRIVATE_MESSAGE_REQUEST',
      payload: { conversation_id: 'conv_1', client_message_id: 'client_1' },
      status: 'pending',
      created_at: 1,
    }])

    service.connect()
    const first = FakeWebSocket.instances[0]
    first.open()
    first.close()
    service.manualRetry()
    const second = FakeWebSocket.instances[1]
    second.open()
    await vi.waitFor(() => expect(second.sent).toHaveLength(2))

    const businessMessage = second.sent
      .map(message => JSON.parse(message))
      .find(message => message.type === 'PRIVATE_MESSAGE_REQUEST')
    expect(businessMessage).toEqual({
      type: 'PRIVATE_MESSAGE_REQUEST',
      message: { conversation_id: 'conv_1', client_message_id: 'client_1' },
    })
    expect(historyStore.syncActiveConversation).toHaveBeenCalledOnce()
  })

  it('marks an outbound message sent when its response arrives', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()

    service.connect()
    const socket = FakeWebSocket.instances[0]
    socket.open()
    socket.receive({
      type: 'PRIVATE_MESSAGE_RESPONSE',
      message: { client_message_id: 'client_1' },
    })
    await vi.waitFor(() => expect(dbServiceMock.markOutboundSent).toHaveBeenCalledOnce())

    expect(dbServiceMock.markOutboundSent).toHaveBeenCalledWith('usr_test', 'client_1')
  })

  it('routes MESSAGE_ACK and advances the local send confirmation', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()
    service.connect()
    const socket = FakeWebSocket.instances[0]
    socket.open()

    socket.receive({
      type: 'MESSAGE_ACK',
      message: {
        client_message_id: 'client_1',
        message_id: 'msg_1',
        conversation_id: 'conv_1',
        seq_id: '10',
        delivery_state: 'sent',
        server_time: 1,
      },
    })

    expect(historyStore.handleMessageAck).toHaveBeenCalledWith(expect.objectContaining({
      client_message_id: 'client_1',
      message_id: 'msg_1',
    }))
    await vi.waitFor(() => expect(dbServiceMock.markOutboundSent).toHaveBeenCalledWith('usr_test', 'client_1'))
  })

  it('persists read receipts while offline and sends them after reconnect', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()

    service.sendReadReceipt({ conversation_id: 'conv_1', read_seq: '20' })
    await vi.waitFor(() => expect(dbServiceMock.enqueueReceipt).toHaveBeenCalledWith(expect.objectContaining({
      type: 'READ_RECEIPT',
      dedupe_key: 'READ_RECEIPT:conv_1',
    })))

    service.connect()
    FakeWebSocket.instances[0].open()
    expect(historyStore.handleReadReceipt).not.toHaveBeenCalled()
  })

})
