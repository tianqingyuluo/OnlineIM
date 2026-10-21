import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const getConversationById = vi.fn()
vi.mock('@/services/conversation.service', () => ({
  conversationService: { getConversationById, getReadState: vi.fn().mockResolvedValue({ conversation_id: 'x', delivered_seq: '0', read_seq: '0', latest_seq: '0', unread_count: 0 }), getMessageReaders: vi.fn() },
}))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ loggedInUser: { user_id: 'usr_me', username: 'me' } }) }))
vi.mock('@/utils/indexedDB', () => ({ dbService: { putHistory: vi.fn().mockResolvedValue(undefined), markMessageRecalled: vi.fn().mockResolvedValue(undefined) }, STORES: {} }))
vi.mock('@/services/websocket.service', () => ({ websocketService: { sendMessage: vi.fn(), sendReceipt: vi.fn(), sendReadReceipt: vi.fn() } }))

const conv = (id: string, extra: Record<string, unknown> = {}) => ({
  conversation_id: id,
  type: id.startsWith('grp_') ? 'group' : 'private',
  target_info: { id: 'usr_peer', name: id },
  last_message: null,
  unread_count: 0,
  last_activity_time: '2026-10-21 10:00:00',
  ...extra,
})
const msg = (id: string, conversationId: string, sender: string, extra: Record<string, unknown> = {}) => ({
  message_id: id,
  conversation_id: conversationId,
  sender_info: { user_id: sender, username: sender },
  message_type: 'text',
  content: 'hello ' + id,
  timestamp: '2026-10-21 22:00:00',
  seq_id: '1',
  status: 1,
  ...extra,
})

describe('conversation list live updates', () => {
  beforeEach(async () => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    setActivePinia(createPinia())
    const live = await import('@/stores/conversation-live')
    live.resetConversationLiveState()
  })

  async function setup(conversations: unknown[]) {
    const { useListStore } = await import('@/stores/list')
    const list = useListStore()
    list.conversations = conversations as never
    return { list, live: await import('@/stores/conversation-live') }
  }

  it('updates preview, time and unread for incoming messages of other conversations, once per message', async () => {
    const { list, live } = await setup([conv('conv_a'), conv('conv_b', { last_activity_time: '2026-10-21 12:00:00' })])
    live.onConversationMessage(msg('m1', 'conv_a', 'usr_peer') as never, 'conv_b')
    live.onConversationMessage(msg('m1', 'conv_a', 'usr_peer') as never, 'conv_b')
    live.onConversationMessage(msg('m2', 'conv_a', 'usr_peer', { message_type: 'image', content: '{}' }) as never, null)
    const a = list.conversations.find(c => c.conversation_id === 'conv_a')!
    expect(a.unread_count).toBe(2)
    expect(a.last_message.content_preview).toBe('[图片]')
    expect(a.last_activity_time).toBe('2026-10-21 22:00:00')
    expect(live.sortByActivity(list.conversations)[0].conversation_id).toBe('conv_a')
  })

  it('does not count unread for the conversation being viewed or for own messages', async () => {
    const { list, live } = await setup([conv('conv_a')])
    live.onConversationMessage(msg('m1', 'conv_a', 'usr_peer') as never, 'conv_a')
    live.onConversationMessage(msg('local_c1', 'conv_a', 'usr_me', { content: 'mine', timestamp: '2026-10-21 22:01:00' }) as never, null)
    const a = list.conversations[0]
    expect(a.unread_count).toBe(0)
    expect(a.last_message.content_preview).toBe('mine')
    live.onConversationAck('conv_a', 'c1', 'msg_real')
    expect(a.last_message.message_id).toBe('msg_real')
    live.onConversationRecall('conv_a', 'msg_real')
    expect(a.last_message.content_preview).toBe('[消息已撤回]')
    expect(a.last_message.is_recalled).toBe(true)
  })

  it('ignores out-of-order older messages for the preview but still counts them', async () => {
    const { list, live } = await setup([conv('conv_a')])
    live.onConversationMessage(msg('m2', 'conv_a', 'usr_peer', { timestamp: '2026-10-21 22:05:00', content: 'newer' }) as never, null)
    live.onConversationMessage(msg('m1', 'conv_a', 'usr_peer', { timestamp: '2026-10-21 22:00:00', content: 'older' }) as never, null)
    expect(list.conversations[0].last_message.content_preview).toBe('newer')
    expect(list.conversations[0].unread_count).toBe(2)
  })

  it('fetches a conversation that is not in the list yet', async () => {
    const { list, live } = await setup([conv('conv_a')])
    getConversationById.mockResolvedValue(conv('conv_new', { unread_count: 1, last_message: { message_id: 'm9', content_preview: 'hi', timestamp: '2026-10-21 22:09:00', is_recalled: false } }))
    live.onConversationMessage(msg('m9', 'conv_new', 'usr_peer') as never, null)
    await vi.runAllTimersAsync()
    expect(getConversationById).toHaveBeenCalledWith('conv_new')
    expect(list.conversations[0].conversation_id).toBe('conv_new')
    expect(list.conversations[0].unread_count).toBe(1)
  })

  it('clears unread on read and reconciles with the server, removing conversations that became invisible', async () => {
    const { list, live } = await setup([conv('conv_a', { unread_count: 3 }), conv('grp_gone')])
    getConversationById.mockImplementation(async (id: string) => {
      if (id === 'grp_gone') throw { status: 404 }
      return conv('conv_a', { unread_count: 0 })
    })
    live.onConversationRead('conv_a', 0)
    expect(list.conversations[0].unread_count).toBe(0)
    await vi.runAllTimersAsync()
    expect(getConversationById).toHaveBeenCalledWith('conv_a')
    await live.refreshConversation('grp_gone')
    expect(list.conversations.map(c => c.conversation_id)).toEqual(['conv_a'])
  })

  it('history store keeps messages of other conversations out of the open chat', async () => {
    const { list } = await setup([conv('conv_a'), conv('conv_b')])
    const { useHistoryStore } = await import('@/stores/history')
    const history = useHistoryStore()
    history.activeConversationId = 'conv_b'
    history.handleWebSocketMessage(msg('m1', 'conv_a', 'usr_peer') as never)
    history.handleWebSocketMessage(msg('m2', 'conv_b', 'usr_peer', { seq_id: '2' }) as never)
    expect(history.chatMessages.map(m => m.message_id)).toEqual(['m2'])
    expect(list.conversations.find(c => c.conversation_id === 'conv_a')!.unread_count).toBe(1)
    expect(list.conversations.find(c => c.conversation_id === 'conv_b')!.unread_count).toBe(0)
    history.leaveConversation('conv_b')
    expect(history.activeConversationId).toBeNull()
  })

  it('flags [有人@我] only for mentioned, unseen group messages and clears it on read', async () => {
    const { list, live } = await setup([conv('grp_1'), conv('grp_2')])
    const g1 = () => list.conversations.find(c => c.conversation_id === 'grp_1')!
    live.onConversationMessage(msg('g_plain', 'grp_1', 'usr_peer') as never, null)
    live.onConversationMessage(msg('g_other', 'grp_1', 'usr_peer', { at_users: ['usr_x'] }) as never, null)
    expect(g1().at_me).toBeFalsy()
    live.onConversationMessage(msg('g_own', 'grp_1', 'usr_me', { at_users: ['usr_me'] }) as never, null)
    live.onConversationMessage(msg('g_view', 'grp_1', 'usr_peer', { at_users: ['usr_me'] }) as never, 'grp_1')
    expect(g1().at_me).toBeFalsy()
    live.onConversationMessage(msg('g_at', 'grp_1', 'usr_peer', { at_users: ['usr_x', 'usr_me'] }) as never, null)
    live.onConversationMessage(msg('g_at', 'grp_1', 'usr_peer', { at_users: ['usr_me'] }) as never, null)
    expect(g1().at_me).toBe(true)
    expect(g1().at_me_count).toBe(1)
    expect(g1().at_me_message_id).toBe('g_at')
    // 帧上的 at_me 也能触发
    live.onConversationMessage(msg('g2_at', 'grp_2', 'usr_peer') as never, null, true)
    expect(list.conversations.find(c => c.conversation_id === 'grp_2')!.at_me).toBe(true)

    live.onConversationRead('grp_1', 2)
    expect(g1().at_me).toBe(true)
    live.onConversationRead('grp_1', 0)
    expect(g1().at_me).toBe(false)
    expect(g1().at_me_count).toBe(0)
  })

  it('recall by someone else lowers my unread count once, only when I had not read it', async () => {
    const { list, live } = await setup([
      conv('conv_a', { unread_count: 2, read_seq: '10', last_message: { message_id: 'm2', content_preview: 'hi', timestamp: '2026-10-21 22:00:00', is_recalled: false } }),
      conv('conv_b', { unread_count: 1, read_seq: '50' }),
      conv('conv_c', { unread_count: 3 }),
    ])
    live.onConversationRecall('conv_a', 'm2', { recallUserId: 'usr_peer', seqId: '12', activeConversationId: null })
    live.onConversationRecall('conv_a', 'm2', { recallUserId: 'usr_peer', seqId: '12', activeConversationId: null })
    const a = list.conversations.find(c => c.conversation_id === 'conv_a')!
    expect(a.unread_count).toBe(1)
    expect(a.last_message.is_recalled).toBe(true)
    expect(a.last_message.sender_id).toBe('usr_peer')

    // 已读过的消息被撤回：不扣
    live.onConversationRecall('conv_b', 'm_old', { recallUserId: 'usr_peer', seqId: '40', activeConversationId: null })
    expect(list.conversations.find(c => c.conversation_id === 'conv_b')!.unread_count).toBe(1)
    // 自己撤回、或正在看这个会话：不扣
    live.onConversationRecall('conv_c', 'm_mine', { recallUserId: 'usr_me', seqId: '9', activeConversationId: null })
    live.onConversationRecall('conv_c', 'm_viewing', { recallUserId: 'usr_peer', seqId: '9', activeConversationId: 'conv_c' })
    expect(list.conversations.find(c => c.conversation_id === 'conv_c')!.unread_count).toBe(3)
  })

  it('does not count a group message again when it is already the list preview', async () => {
    const { list, live } = await setup([conv('grp_1', {
      unread_count: 1,
      latest_seq: '9',
      last_message: { message_id: 'g1', content_preview: 'hello', timestamp: '2026-10-21 22:00:00', is_recalled: false },
    })])
    live.onConversationMessage(msg('g1', 'grp_1', 'usr_peer', { seq_id: '9' }) as never, null)
    expect(list.conversations[0].unread_count).toBe(1)
  })

  it('applies an offline snapshot and keeps unread added by a newer live message', async () => {
    const { list, live } = await setup([conv('conv_a', { latest_seq: '10', unread_count: 0 })])
    const preview = { message_id: 'm20', content_preview: '漏了', timestamp: '2026-10-21 22:05:00', is_recalled: false }
    live.applyOfflineConversations([
      conv('conv_a', { unread_count: 3, latest_seq: '20', last_message: preview, last_activity_time: '2026-10-21 22:05:00' }),
    ], null)
    expect(list.conversations[0].unread_count).toBe(3)
    expect(list.conversations[0].last_message.content_preview).toBe('漏了')

    live.onConversationMessage(msg('m21', 'conv_a', 'usr_peer', { seq_id: '21', content: '更新', timestamp: '2026-10-21 22:06:00' }) as never, null)
    expect(list.conversations[0].unread_count).toBe(4)

    live.applyOfflineConversations([
      conv('conv_a', { unread_count: 3, latest_seq: '20', last_message: preview, last_activity_time: '2026-10-21 22:05:00' }),
    ], null)
    expect(list.conversations[0].unread_count).toBe(4)
    expect(list.conversations[0].last_message.content_preview).toBe('更新')
  })

  it('applies a draft preview from the snapshot even when a newer live message is kept', async () => {
    const { list, live } = await setup([conv('conv_a', {
      unread_count: 0,
      latest_seq: '30',
      draft_preview: '旧草稿',
      last_message: { message_id: 'm30', content_preview: '新', timestamp: '2026-10-21 23:00:00', is_recalled: false },
    })])
    live.applyOfflineConversations([
      conv('conv_a', {
        unread_count: 2,
        latest_seq: '20',
        draft_preview: '另一台写的',
        last_message: { message_id: 'm20', content_preview: '旧', timestamp: '2026-10-21 22:00:00', is_recalled: false },
      }),
    ], 'conv_a')
    const a = list.conversations[0]
    expect(a.unread_count).toBe(0)
    expect(a.last_message.content_preview).toBe('新')
    expect(a.draft_preview).toBe('另一台写的')

    live.applyOfflineConversations([
      conv('conv_a', { unread_count: 2, latest_seq: '20', draft_preview: null }),
    ], null)
    expect(a.draft_preview).toBeNull()
    expect(a.last_message.content_preview).toBe('新')
    expect(a.unread_count).toBe(2)
  })

  it('does not raise unread for the conversation currently on screen', async () => {
    const { list, live } = await setup([conv('conv_a', { unread_count: 0, latest_seq: '1' })])
    live.applyOfflineConversations([
      conv('conv_a', { unread_count: 5, latest_seq: '8', last_message: { message_id: 'm8', content_preview: '新', timestamp: '2026-10-21 23:00:00', is_recalled: false }, last_activity_time: '2026-10-21 23:00:00' }),
    ], 'conv_a')
    expect(list.conversations[0].unread_count).toBe(0)
    expect(list.conversations[0].last_message.content_preview).toBe('新')
  })

  it('clears unread when another device of the same account advances the read cursor', async () => {
    const { list, live } = await setup([conv('conv_a', { unread_count: 4, read_seq: '3' })])
    live.onOwnDeviceRead('conv_a', '9')
    expect(list.conversations[0]?.unread_count).toBe(0)
    expect(list.conversations[0]?.read_seq).toBe('9')
  })
})
