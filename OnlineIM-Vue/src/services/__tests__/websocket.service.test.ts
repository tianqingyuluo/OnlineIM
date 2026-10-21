import { beforeEach, describe, expect, it, vi } from 'vitest'
import { toast } from 'vue-sonner'

const userStore = {
  token: 'test-token',
  loggedInUser: { user_id: 'usr_test' },
  clearLocalSession: vi.fn(),
}

const presenceStore = {
  applyUpdate: vi.fn(),
  onConnected: vi.fn(() => Promise.resolve()),
  setSelf: vi.fn(),
}
const routerPush = vi.hoisted(() => vi.fn())

const historyStore = {
  handleWebSocketMessage: vi.fn(),
  handleGroupWebSocketMessage: vi.fn(),
  handleMessageAck: vi.fn(),
  handleReceipt: vi.fn(),
  handleReadReceipt: vi.fn(),
  handleServerError: vi.fn(),
  handleMessageRecalled: vi.fn(),
  syncActiveConversation: vi.fn(),
  requestOfflineSync: vi.fn(),
  applyOfflineSync: vi.fn(),
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
vi.mock('@/router', () => ({ default: { push: routerPush } }))
vi.mock('@/stores/history', () => ({ useHistoryStore: () => historyStore }))
vi.mock('@/stores/presence', () => ({ usePresenceStore: () => presenceStore }))
vi.mock('@/utils/indexedDB.ts', () => ({ dbService: dbServiceMock }))
vi.mock('vue-sonner', () => ({
  toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}))

const fetchMock = vi.fn()

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
    fetchMock.mockReset()
    fetchMock.mockResolvedValue({ status: 200 })
    vi.stubGlobal('fetch', fetchMock)
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

  it('preserves reply_to_message_id in the offline outbound payload', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()

    service.sendMessage({
      type: 'PRIVATE_MESSAGE_REQUEST',
      message: {
        conversation_id: 'conv_1',
        content: 'hello',
        client_message_id: 'client_reply',
        reply_to_message_id: 'msg_target',
      },
    })
    await vi.waitFor(() => expect(dbServiceMock.addOutboundMessage).toHaveBeenCalledOnce())

    expect(dbServiceMock.addOutboundMessage).toHaveBeenCalledWith(expect.objectContaining({
      payload: expect.objectContaining({ reply_to_message_id: 'msg_target' }),
    }))
  })

  it('uses group_id as the outbound queue conversation key', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()

    service.sendMessage({
      type: 'GROUP_MESSAGE_REQUEST',
      message: {
        group_id: 'grp_1',
        content: 'group reply',
        client_message_id: 'client_group_reply',
        reply_to_message_id: 'msg_target',
      },
    })
    await vi.waitFor(() => expect(dbServiceMock.addOutboundMessage).toHaveBeenCalledOnce())

    expect(dbServiceMock.addOutboundMessage).toHaveBeenCalledWith(expect.objectContaining({
      conversation_id: 'grp_1',
      payload: expect.objectContaining({
        client_message_id: 'client_group_reply',
        reply_to_message_id: 'msg_target',
      }),
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
    expect(historyStore.requestOfflineSync).toHaveBeenCalledTimes(2)
  })

  it('asks for missed messages on the first successful connect', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()
    service.connect()
    FakeWebSocket.instances[0].open()
    expect(historyStore.requestOfflineSync).toHaveBeenCalledOnce()
    expect(historyStore.syncActiveConversation).not.toHaveBeenCalled()
  })

  it('routes OFFLINE_SYNC to the history store', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()
    service.connect()
    const socket = FakeWebSocket.instances[0]
    socket.open()
    socket.receive({
      type: 'OFFLINE_SYNC',
      message: { conversations: [], messages: [{ message_id: 'msg_1' }], revisions: [], truncated: false },
    })
    expect(historyStore.applyOfflineSync).toHaveBeenCalledWith(expect.objectContaining({
      messages: [expect.objectContaining({ message_id: 'msg_1' })],
      truncated: false,
    }))
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

  it('writes PRESENCE_UPDATE into the presence store', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()
    service.connect()
    const socket = FakeWebSocket.instances[0]
    socket.open()

    socket.receive({
      type: 'PRESENCE_UPDATE',
      message: { user_id: 'usr_a', status: 'offline', last_seen_at: 1700000000000, server_time: 1700000001000 },
    })

    expect(presenceStore.applyUpdate).toHaveBeenCalledWith({
      user_id: 'usr_a',
      status: 'offline',
      last_seen_at: 1700000000000,
      server_time: 1700000001000,
    })
  })

  it('tells the presence store when the socket opens and closes', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()
    service.connect()
    const socket = FakeWebSocket.instances[0]

    socket.open()
    expect(presenceStore.onConnected).toHaveBeenCalledWith('usr_test')

    socket.close()
    expect(presenceStore.setSelf).toHaveBeenCalledWith('usr_test', false)
  })

  it('logout closes the socket quietly and the next login is a fresh connection', async () => {
    const { toast } = await import('vue-sonner')
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()
    service.connect()
    const first = FakeWebSocket.instances[0]
    first.open()
    vi.mocked(toast.warning).mockClear()
    vi.mocked(toast.success).mockClear()

    service.logout()

    expect(first.readyState).toBe(FakeWebSocket.CLOSED)
    expect(toast.warning).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(60_000)
    expect(FakeWebSocket.instances).toHaveLength(1)

    service.connect()
    const second = FakeWebSocket.instances[1]
    expect(service.connectionState.value).toBe('connecting')
    second.open()
    expect(service.connectionState.value).toBe('online')
    expect(toast.success).not.toHaveBeenCalled()
  })

  it('routes top-level MESSAGE_RECALLED frames to the history store', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()
    service.connect()
    const socket = FakeWebSocket.instances[0]
    socket.open()

    socket.receive({
      type: 'MESSAGE_RECALLED',
      message_id: 'msg_target',
      conversation_id: 'conv_1',
    })

    expect(historyStore.handleMessageRecalled).toHaveBeenCalledWith(expect.objectContaining({
      message_id: 'msg_target',
      conversation_id: 'conv_1',
    }))
  })

  it('routes DRAFT_UPDATED to the draft bus', async () => {
    const { subscribeDraftUpdates } = await import('@/utils/draft-bus')
    const seen: Array<{ conversation_id?: string; content?: string | null }> = []
    const stop = subscribeDraftUpdates(payload => seen.push(payload))
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()
    service.connect()
    const socket = FakeWebSocket.instances[0]
    socket.open()
    socket.receive({
      type: 'DRAFT_UPDATED',
      message: { conversation_id: 'conv_1', content: '写了一半', draft_preview: '写了一半', cleared: false },
    })
    expect(seen).toEqual([expect.objectContaining({ conversation_id: 'conv_1', content: '写了一半' })])
    stop()
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

  it('logs the current device out when the server kicks the session', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()
    service.connect()
    const socket = FakeWebSocket.instances[0]
    socket.open()

    socket.receive({
      type: 'SESSION_KICKED',
      message: {
        reason: 'SAME_DEVICE_TYPE_LOGIN',
        message: '您的账号已在其他网页端登录，当前设备已下线',
        device_type: 'web',
      },
    })

    expect(userStore.clearLocalSession).toHaveBeenCalledOnce()
    expect(toast.error).toHaveBeenCalledWith('您的账号已在其他网页端登录，当前设备已下线')
    await vi.waitFor(() => expect(routerPush).toHaveBeenCalledWith('/login'))
    expect(service.connectionState.value).toBe('offline')
    const syncCalls = historyStore.requestOfflineSync.mock.calls.length

    await vi.advanceTimersByTimeAsync(30_000)
    expect(FakeWebSocket.instances).toHaveLength(1)
    expect(historyStore.requestOfflineSync).toHaveBeenCalledTimes(syncCalls)
  })

  it('stops reconnecting when the handshake is refused because the token was revoked', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()
    service.connect()
    FakeWebSocket.instances[0].open()
    // 同设备另一个标签页退出登录：服务端安静关闭这条连接，之后的握手都被拒绝
    fetchMock.mockResolvedValue({ status: 401 })
    FakeWebSocket.instances[0].close()
    await vi.advanceTimersByTimeAsync(30_000)
    const attempts = FakeWebSocket.instances.length
    expect(attempts).toBe(2)
    FakeWebSocket.instances[1].close()

    await vi.waitFor(() => expect(userStore.clearLocalSession).toHaveBeenCalledOnce())
    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/\/users\/me$/), expect.anything())
    expect(toast.error).toHaveBeenCalledWith('登录已失效，请重新登录')
    await vi.waitFor(() => expect(routerPush).toHaveBeenCalledWith('/login'))
    await vi.advanceTimersByTimeAsync(60_000)
    expect(FakeWebSocket.instances).toHaveLength(attempts)
    expect(service.connectionState.value).toBe('offline')
  })

  it('keeps reconnecting after a refused handshake when the token is still valid', async () => {
    const { WebSocketService } = await import('@/services/websocket.service')
    const service = new WebSocketService()
    service.connect()
    FakeWebSocket.instances[0].close()
    await vi.advanceTimersByTimeAsync(30_000)

    expect(FakeWebSocket.instances.length).toBeGreaterThan(1)
    expect(userStore.clearLocalSession).not.toHaveBeenCalled()
    service.disconnect()
  })

})
