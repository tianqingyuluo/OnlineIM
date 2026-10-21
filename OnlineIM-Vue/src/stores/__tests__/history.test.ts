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
const messageServiceMock = {
  getMessageContext: vi.fn(),
}
const dbServiceMock = {
  getHistoryMessage: vi.fn(),
  putHistory: vi.fn(),
  markMessageRecalled: vi.fn(),
  applyMessageEdit: vi.fn(),
  getLastHistorySeq: vi.fn(),
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
vi.mock('@/services/message.service', () => ({ MessageService: messageServiceMock }))
vi.mock('@/utils/indexedDB', () => ({ dbService: dbServiceMock, STORES: {} }))

describe('history store message receipt flow', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    dbServiceMock.putHistory.mockResolvedValue(undefined)
    dbServiceMock.markMessageRecalled.mockResolvedValue(undefined)
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

  it('keeps the same direct reply target through optimistic send and retries', async () => {
    const { useHistoryStore } = await import('@/stores/history')
    const store = useHistoryStore()
    const target = {
      message_id: 'msg_target',
      conversation_id: 'conv_1',
      sender_info: { user_id: 'usr_peer', nickname: '小明' },
      message_type: 'text',
      seq_id: '100',
      content: '原消息',
      status: 1,
      delivery_state: 'sent' as const,
      timestamp: new Date().toISOString(),
      is_recalled: false,
      client_message_id: 'target_client',
    }
    expect(store.selectReplyTarget(target)).toBe(true)

    store.sendMessage('conv_1', 'usr_peer', 'text', '回复正文', false)
    const optimistic = store.chatMessages.at(-1)

    expect(optimistic?.reply_to?.message_id).toBe('msg_target')
    expect(websocketMock.sendMessage).toHaveBeenLastCalledWith(expect.objectContaining({
      message: expect.objectContaining({ reply_to_message_id: 'msg_target' }),
    }))

    await vi.advanceTimersByTimeAsync(5_000)
    expect(websocketMock.sendMessage).toHaveBeenLastCalledWith(expect.objectContaining({
      message: expect.objectContaining({ reply_to_message_id: 'msg_target' }),
    }))
  })

  it('restores the composer snapshot when the reply target becomes unavailable', async () => {
    const { useHistoryStore } = await import('@/stores/history')
    const store = useHistoryStore()
    const target = {
      message_id: 'msg_target',
      conversation_id: 'conv_1',
      sender_info: { user_id: 'usr_peer', nickname: '小明' },
      message_type: 'text',
      seq_id: '100',
      content: '原消息',
      status: 1,
      timestamp: new Date().toISOString(),
      is_recalled: false,
      client_message_id: 'target_client',
    }
    store.selectReplyTarget(target)
    const clientId = store.sendMessage('conv_1', 'usr_peer', 'text', '回复正文', false)
    store.cancelReplyTarget()

    store.handleServerError({
      client_message_id: clientId,
      code: 'REPLY_TARGET_UNAVAILABLE',
      message: '原消息不可用',
    })

    expect(store.replyTarget?.reference.message_id).toBe('msg_target')
    expect(store.failedReplyDraft).toEqual(expect.objectContaining({
      clientId,
      content: '回复正文',
    }))
    expect(store.chatMessages.at(-1)?.delivery_state).toBe('failed')
  })

  it('loads a missing reply target with one context request and merges the window', async () => {
    const { useHistoryStore } = await import('@/stores/history')
    const store = useHistoryStore()
    dbServiceMock.getHistoryMessage.mockResolvedValue(null)
    const target = {
      message_id: 'msg_target',
      conversation_id: 'conv_1',
      sender_info: { user_id: 'usr_peer', nickname: '小明' },
      message_type: 'text',
      seq_id: '100',
      content: '原消息',
      status: 1,
      timestamp: new Date().toISOString(),
      is_recalled: false,
      client_message_id: 'target_client',
    }
    messageServiceMock.getMessageContext.mockResolvedValue({
      target_message_id: 'msg_target',
      messages: [target],
      has_more_before: true,
      has_more_after: true,
    })

    const found = await store.jumpToMessage('conv_1', 'msg_target')

    expect(found?.message_id).toBe('msg_target')
    expect(messageServiceMock.getMessageContext).toHaveBeenCalledOnce()
    expect(dbServiceMock.putHistory).toHaveBeenCalledWith([target])
    expect(store.chatMessages.some(message => message.message_id === 'msg_target')).toBe(true)
  })

  it('redacts recalled targets and every direct reply in memory and IndexedDB', async () => {
    const { useHistoryStore } = await import('@/stores/history')
    const store = useHistoryStore()
    store.chatMessages.push(
      {
        message_id: 'msg_target',
        conversation_id: 'conv_1',
        sender_info: { user_id: 'usr_peer', nickname: '小明' },
        message_type: 'text',
        seq_id: '100',
        content: '原消息',
        status: 1,
        timestamp: new Date().toISOString(),
        is_recalled: false,
        client_message_id: 'target_client',
      },
      {
        message_id: 'msg_reply',
        conversation_id: 'conv_1',
        sender_info: { user_id: 'usr_me', nickname: '我' },
        message_type: 'text',
        seq_id: '101',
        content: '回复',
        status: 1,
        timestamp: new Date().toISOString(),
        is_recalled: false,
        client_message_id: 'reply_client',
        reply_to: {
          message_id: 'msg_target',
          seq_id: '100',
          sender_id: 'usr_peer',
          sender_display_name: '小明',
          message_type: 'text',
          preview_text: '不能继续显示',
          state: 'active',
        },
      },
    )

    await store.handleMessageRecalled({
      message_id: 'msg_target',
      conversation_id: 'conv_1',
    })

    expect(store.chatMessages[0]?.is_recalled).toBe(true)
    expect(store.chatMessages[0]?.content).toBe('')
    expect(store.chatMessages[1]?.reply_to).toEqual(expect.objectContaining({
      state: 'recalled',
      preview_text: null,
    }))
    expect(dbServiceMock.markMessageRecalled).toHaveBeenCalledWith(
      'usr_me', 'conv_1', 'msg_target',
    )
  })

  it('keeps the original text of my own recalled text for 重新编辑 and clears the content', async () => {
    const { useHistoryStore } = await import('@/stores/history')
    const store = useHistoryStore()
    const make = (id: string, sender: string, extra: Record<string, unknown> = {}) => ({
      message_id: id,
      conversation_id: 'grp_1',
      sender_info: { user_id: sender },
      message_type: 'text',
      seq_id: '5',
      content: '原文 ' + id,
      at_users: ['usr_x'],
      status: 1,
      timestamp: new Date().toISOString(),
      is_recalled: false,
      client_message_id: id,
      ...extra,
    })
    store.groupMessages.push(make('m_own', 'usr_me') as never, make('m_peer', 'usr_peer') as never,
      make('m_img', 'usr_me', { message_type: 'image', content: '{"url":"x"}' }) as never)

    for (const id of ['m_own', 'm_peer', 'm_img', 'm_own']) {
      await store.handleMessageRecalled({ message_id: id, conversation_id: 'grp_1', recall_user_id: id === 'm_peer' ? 'usr_peer' : 'usr_me' })
    }

    expect(store.recalledOriginal('m_own')).toEqual({ conversationId: 'grp_1', content: '原文 m_own', atUsers: ['usr_x'] })
    expect(store.recalledOriginal('m_peer')).toBeNull()
    expect(store.recalledOriginal('m_img')).toBeNull()
    for (const message of store.groupMessages) {
      expect(message.is_recalled).toBe(true)
      expect(message.content).toBe('')
      expect(message.at_users).toBeNull()
    }
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

  it('stamps optimistic messages with the server time format and sends at_users only for groups', async () => {
    const { useHistoryStore } = await import('@/stores/history')
    const store = useHistoryStore()
    store.sendMessage('grp_1', 'grp_1', 'text', '@bob hi', true, ['usr_bob'])
    const group = store.groupMessages.at(-1)!
    expect(group.timestamp).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
    expect(group.at_users).toEqual(['usr_bob'])
    expect(websocketMock.sendMessage).toHaveBeenLastCalledWith(expect.objectContaining({
      type: 'GROUP_MESSAGE_REQUEST',
      message: expect.objectContaining({ at_users: ['usr_bob'] }),
    }))

    store.sendMessage('conv_1', 'usr_peer', 'text', 'hi', false, ['usr_bob'])
    const priv = store.chatMessages.at(-1)!
    expect(priv.timestamp).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
    expect(priv.at_users).toBeUndefined()
    const lastFrame = websocketMock.sendMessage.mock.calls.at(-1)![0] as { message: Record<string, unknown> }
    expect(lastFrame.message).not.toHaveProperty('at_users')
  })

  it('applies an edit to the message, replies quoting it, the reply target and the cache', async () => {
    const { useHistoryStore } = await import('@/stores/history')
    const store = useHistoryStore()
    dbServiceMock.applyMessageEdit.mockResolvedValue(undefined)
    const base = {
      conversation_id: 'conv_1',
      message_type: 'text',
      status: 1,
      timestamp: new Date().toISOString(),
      is_recalled: false,
    }
    store.chatMessages.push(
      { ...base, message_id: 'msg_target', sender_info: { user_id: 'usr_peer', nickname: '小明' }, seq_id: '100', content: '原文', client_message_id: 't', content_revision: 1 },
      {
        ...base, message_id: 'msg_reply', sender_info: { user_id: 'usr_me', nickname: '我' }, seq_id: '101', content: '回复', client_message_id: 'r',
        reply_to: { message_id: 'msg_target', seq_id: '100', sender_id: 'usr_peer', sender_display_name: '小明', message_type: 'text', preview_text: '原文', state: 'active' },
      },
    )
    store.replyTarget = {
      conversationId: 'conv_1',
      reference: { message_id: 'msg_target', seq_id: '100', sender_id: 'usr_peer', sender_display_name: '小明', message_type: 'text', preview_text: '原文', state: 'active' },
    } as never

    const edited = {
      ...base, message_id: 'msg_target', sender_info: { user_id: 'usr_peer', nickname: '小明' }, seq_id: '100',
      content: '改过了', client_message_id: 't', edited: true, edited_at: '2026-10-22 01:00:00', content_revision: 3,
    }
    await store.handleMessageEdited(edited as never)

    expect(store.chatMessages[0]).toEqual(expect.objectContaining({
      content: '改过了', edited: true, edited_at: '2026-10-22 01:00:00', content_revision: 3,
    }))
    // 引用保留引用时的快照，只标成“原消息已编辑”
    expect(store.chatMessages[1]?.reply_to).toEqual(expect.objectContaining({ state: 'edited', preview_text: '原文' }))
    expect(store.replyTarget?.reference.state).toBe('edited')
    expect(dbServiceMock.applyMessageEdit).toHaveBeenCalledWith('usr_me', edited)

    // 乱序到达的旧版本被忽略
    await store.handleMessageEdited({ ...edited, content: '旧版本', content_revision: 2 } as never)
    expect(store.chatMessages[0]?.content).toBe('改过了')
  })

  it('requests offline sync from local seq, falling back to the list snapshot', async () => {
    const { useHistoryStore } = await import('@/stores/history')
    const { useListStore } = await import('@/stores/list')
    const store = useHistoryStore()
    useListStore().conversations = [
      { conversation_id: 'conv_1', latest_seq: '10', type: 'private', target_info: { id: 'usr_peer', name: '对方' }, last_message: null, unread_count: 0, last_activity_time: '' },
      { conversation_id: 'grp_1', latest_seq: '40', type: 'group', target_info: { id: 'grp_1', name: '群' }, last_message: null, unread_count: 0, last_activity_time: '' },
    ] as never
    dbServiceMock.getLastHistorySeq.mockImplementation(async (_userId: string, conversationId: string) => (
      conversationId === 'conv_1' ? '15' : null
    ))
    store.activeConversationId = 'conv_1'
    store.chatMessages = [{
      message_id: 'msg_a',
      conversation_id: 'conv_1',
      sender_info: { user_id: 'usr_peer' },
      message_type: 'text',
      content: '已在屏幕上',
      seq_id: '12',
      status: 1,
      timestamp: '2026-10-21 22:00:00',
      is_recalled: false,
    }]

    await store.requestOfflineSync()

    expect(websocketMock.sendMessage).toHaveBeenCalledWith({
      type: 'SYNC_REQUEST',
      message: {
        conversations: [
          { conversation_id: 'conv_1', last_seq: '15' },
          { conversation_id: 'grp_1', last_seq: '40' },
        ],
        watch: [{ conversation_id: 'conv_1', message_ids: ['msg_a'] }],
      },
    }, { persist: false })
  })

  it('只把和本地历史连续的补齐消息写进 IndexedDB；截断时一条都不缓存，正在看的会话按本地旧游标走 HTTP 补全', async () => {
    const { useHistoryStore } = await import('@/stores/history')
    const { useListStore } = await import('@/stores/list')
    const { resetConversationLiveState } = await import('@/stores/conversation-live')
    resetConversationLiveState()
    const store = useHistoryStore()
    useListStore().conversations = [] as never
    const msg = (id: string, conversationId: string, seq: string) => ({
      message_id: id,
      conversation_id: conversationId,
      sender_info: { user_id: 'usr_peer', nickname: '对方' },
      message_type: 'text',
      content: id,
      seq_id: seq,
      status: 1,
      timestamp: '2026-10-21 22:05:00',
      is_recalled: false,
    }) as never
    // conv_1 本地有历史（到 seq 10），conv_2 本地从没打开过
    dbServiceMock.getLastHistorySeq.mockImplementation(async (_userId: string, conversationId: string) => (
      conversationId === 'conv_1' ? '10' : null
    ))
    const cached = () => dbServiceMock.putHistory.mock.calls.flatMap(call => call[0].map((item: { message_id: string }) => item.message_id))

    await store.applyOfflineSync({
      conversations: [],
      revisions: [],
      messages: [msg('p11', 'conv_1', '11'), msg('q5', 'conv_2', '5')],
      truncated: false,
    })
    expect(cached()).toEqual(['p11'])

    dbServiceMock.putHistory.mockClear()
    const syncMessages = vi.fn().mockResolvedValue([msg('p12', 'conv_1', '12'), msg('p13', 'conv_1', '13')])
    Object.assign(messageServiceMock, { syncMessages })
    store.activeConversationId = 'conv_1'
    store.chatMessages = [msg('p10', 'conv_1', '10')] as never
    await store.applyOfflineSync({
      conversations: [],
      revisions: [],
      // 截断：只给了最新的 p13，p12 被截掉
      messages: [msg('p13', 'conv_1', '13'), msg('q9', 'conv_2', '9')],
      truncated: true,
    })
    expect(cached()).not.toContain('q9')
    // 截断时不先把 p13 写进本地，HTTP 增量才会从本地的 seq 10 开始，把 p12 补回来
    expect(syncMessages).toHaveBeenCalledWith('conv_1', '10')
    expect(store.chatMessages.map(item => item.message_id)).toEqual(['p10', 'p12', 'p13'])
  })

  it('applies an offline snapshot without counting its messages again', async () => {
    const { useHistoryStore } = await import('@/stores/history')
    const { useListStore } = await import('@/stores/list')
    const { resetConversationLiveState } = await import('@/stores/conversation-live')
    resetConversationLiveState()
    const store = useHistoryStore()
    const list = useListStore()
    list.conversations = [{
      conversation_id: 'conv_1',
      type: 'private',
      target_info: { id: 'usr_peer', name: '对方' },
      last_message: { message_id: 'msg_old', content_preview: '原文', timestamp: '2026-10-21 22:00:00', is_recalled: false },
      unread_count: 0,
      latest_seq: '10',
      last_activity_time: '2026-10-21 22:00:00',
    }] as never
    store.activeConversationId = 'conv_1'
    store.chatMessages = [{
      message_id: 'msg_old',
      conversation_id: 'conv_1',
      sender_info: { user_id: 'usr_peer', nickname: '对方' },
      message_type: 'text',
      content: '原文',
      seq_id: '10',
      status: 1,
      timestamp: '2026-10-21 22:00:00',
      is_recalled: false,
      client_message_id: 'old',
      content_revision: 1,
    }]
    dbServiceMock.putHistory.mockResolvedValue(undefined)
    dbServiceMock.applyMessageEdit.mockResolvedValue(undefined)

    await store.applyOfflineSync({
      conversations: [{
        conversation_id: 'conv_1',
        type: 'private',
        target_info: { id: 'usr_peer', name: '对方' },
        last_message: { message_id: 'msg_new', content_preview: '漏收', timestamp: '2026-10-21 22:05:00', is_recalled: false },
        unread_count: 1,
        latest_seq: '20',
        last_activity_time: '2026-10-21 22:05:00',
      }],
      revisions: [{
        message_id: 'msg_old',
        conversation_id: 'conv_1',
        sender_info: { user_id: 'usr_peer', nickname: '对方' },
        message_type: 'text',
        content: '改过了',
        seq_id: '10',
        status: 1,
        timestamp: '2026-10-21 22:00:00',
        is_recalled: false,
        edited: true,
        edited_at: '2026-10-21 22:04:00',
        content_revision: 2,
      }],
      messages: [{
        message_id: 'msg_new',
        conversation_id: 'conv_1',
        sender_info: { user_id: 'usr_peer', nickname: '对方' },
        message_type: 'text',
        content: '漏收',
        seq_id: '20',
        status: 1,
        timestamp: '2026-10-21 22:05:00',
        is_recalled: false,
      }],
      truncated: false,
    })

    expect(store.chatMessages.map(message => message.message_id)).toEqual(['msg_old', 'msg_new'])
    expect(store.chatMessages[0]?.content).toBe('改过了')
    // 正在看这个会话：快照不把未读标上去，和实时消息口径一致
    expect(list.conversations[0]?.unread_count).toBe(0)
    expect(list.conversations[0]?.last_message.content_preview).toBe('漏收')

    store.activeConversationId = null
    store.handleWebSocketMessage({
      message_id: 'msg_new',
      conversation_id: 'conv_1',
      sender_info: { user_id: 'usr_peer', nickname: '对方' },
      message_type: 'text',
      content: '漏收',
      seq_id: '20',
      status: 1,
      timestamp: '2026-10-21 22:05:00',
      is_recalled: false,
    })
    expect(list.conversations[0]?.unread_count).toBe(0)
  })

  it('shows a message sent from another signed-in device without adding unread', async () => {
    const { useHistoryStore } = await import('@/stores/history')
    const { useListStore } = await import('@/stores/list')
    const store = useHistoryStore()
    const list = useListStore()
    list.conversations = [{
      conversation_id: 'conv_1',
      type: 'private',
      target_info: { id: 'usr_peer', name: '对方' },
      last_message: null,
      unread_count: 0,
      last_activity_time: '2026-10-21 10:00:00',
    }] as never
    store.activeConversationId = 'conv_1'

    store.handleWebSocketMessage({
      message_id: 'msg_phone',
      conversation_id: 'conv_1',
      sender_info: { user_id: 'usr_me', nickname: '我' },
      message_type: 'text',
      content: '手机上发出的',
      seq_id: '11',
      client_message_id: 'c-phone',
      timestamp: '2026-10-21 22:00:00',
      status: 1,
    } as never)

    expect(store.chatMessages.map(message => message.message_id)).toContain('msg_phone')
    expect(list.conversations[0]?.unread_count).toBe(0)
    expect(list.conversations[0]?.last_message?.content_preview).toBe('手机上发出的')
  })

  it('clears unread when another of my devices reads without marking my own messages as read by the peer', async () => {
    const { useHistoryStore } = await import('@/stores/history')
    const { useListStore } = await import('@/stores/list')
    const store = useHistoryStore()
    const list = useListStore()
    list.conversations = [{
      conversation_id: 'conv_1',
      type: 'private',
      target_info: { id: 'usr_peer', name: '对方' },
      last_message: null,
      unread_count: 3,
      read_seq: '1',
      last_activity_time: '2026-10-21 10:00:00',
    }] as never
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

    store.handleReadReceipt({ conversation_id: 'conv_1', reader_id: 'usr_me', read_seq: '12' })

    expect(message.delivery_state).toBe('sent')
    expect(list.conversations[0]?.unread_count).toBe(0)
    expect(list.conversations[0]?.read_seq).toBe('12')
    expect(store.readStates.conv_1?.read_seq).toBe('12')
  })
})
