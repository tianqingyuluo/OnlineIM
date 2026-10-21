import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { compareSeqId } from '@/utils/seq-id'

type Row = { message_id: string, conversation_id: string, seq_id: string, [key: string]: unknown }
type Range = { id: number, user_id: string, conversation_id: string, min_seq_id: string, max_seq_id: string }

const local: { history: Row[], ranges: Range[], nextId: number } = { history: [], ranges: [], nextId: 1 }
const server: Row[] = []

const dbServiceMock = {
  getPendingRanges: vi.fn(async (_u: string, c: string) => local.ranges.filter(r => r.conversation_id === c)),
  addPendingRange: vi.fn(async (u: string, c: string, min: string, max: string) => {
    const id = local.nextId++
    local.ranges.push({ id, user_id: u, conversation_id: c, min_seq_id: min, max_seq_id: max })
    return id
  }),
  deletePendingRange: vi.fn(async (id: number) => { local.ranges = local.ranges.filter(r => r.id !== id) }),
  getLastHistorySeq: vi.fn(async (_u: string, c: string) => {
    const rows = local.history.filter(r => r.conversation_id === c).sort((a, b) => compareSeqId(b.seq_id, a.seq_id))
    return rows[0]?.seq_id ?? null
  }),
  putHistory: vi.fn(async (items: Row[]) => {
    for (const item of items) {
      local.history = local.history.filter(r => r.message_id !== item.message_id)
      local.history.push({ ...item })
    }
  }),
  // 与真实实现一致：只按 before 过滤，取最后 50 条
  getHistory: vi.fn(async (_u: string, c: string, _last?: string, before?: string) => local.history
    .filter(r => r.conversation_id === c && (!before || compareSeqId(r.seq_id, before) < 0))
    .sort((a, b) => compareSeqId(a.seq_id, b.seq_id))
    .slice(-50)),
  addImageFromUrl: vi.fn(),
}
const page = (before?: string) => {
  const older = server.filter(r => !before || compareSeqId(r.seq_id, before) < 0)
  const messages = older.slice(-20)
  return { messages: messages.map(m => ({ ...m })), has_more_before: older.length > messages.length }
}
const messageServiceMock = {
  getPrivateHistory: vi.fn(async (_c: string, before?: string) => page(before)),
  getMessageHistory: vi.fn(async (_c: string, before?: string) => page(before)),
  getHistoryByIndexDB: vi.fn(async (c: string, last?: string, before?: string) => {
    const messages = await dbServiceMock.getHistory('usr_me', c, last, before)
    return { messages, has_more_before: messages.length === 50 }
  }),
}

vi.mock('@/services/websocket.service', () => ({ websocketService: { sendMessage: vi.fn(), sendReceipt: vi.fn(), sendReadReceipt: vi.fn() } }))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ loggedInUser: { user_id: 'usr_me' } }) }))
vi.mock('@/services/conversation.service', () => ({
  conversationService: {
    getReadState: vi.fn().mockResolvedValue({ conversation_id: 'conv_1', delivered_seq: '0', read_seq: '0', latest_seq: '0', unread_count: 0 }),
  },
}))
vi.mock('@/services/message.service', () => ({ MessageService: messageServiceMock }))
vi.mock('@/utils/indexedDB', () => ({ dbService: dbServiceMock, STORES: {} }))

const row = (n: number): Row => ({
  message_id: 'msg_' + n,
  conversation_id: 'conv_1',
  seq_id: String(1000 + n),
  sender_info: { user_id: 'usr_peer', nickname: '对方' },
  message_type: 'text',
  content: '第 ' + n + ' 条',
  status: 1,
  timestamp: '2026-10-21 22:00:00',
  is_recalled: false,
})

async function scrollToTop() {
  const { useHistoryStore } = await import('@/stores/history')
  const store = useHistoryStore()
  await store.init()
  await store.loadInitialHistory('usr_me', 'conv_1')
  for (let i = 0; i < 10 && store.hasMore; i++) await store.loadMessages('conv_1')
  return store.chatMessages.map(m => m.message_id)
}

describe('history paging', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    local.history = []
    local.ranges = []
    server.length = 0
    for (let n = 1; n <= 45; n++) server.push(row(n))
    setActivePinia(createPinia())
  })

  it('第一次打开（本地没有缓存）往上翻能翻到最早一条，而不是停在第一页', async () => {
    const ids = await scrollToTop()
    expect(ids).toEqual(server.map(m => m.message_id))
  })

  it('本地只存过一条实时消息时，往上翻仍能翻完全部历史', async () => {
    await dbServiceMock.putHistory([row(45)])
    const ids = await scrollToTop()
    expect(ids).toEqual(server.map(m => m.message_id))
  })

  it('翻完后再次打开：从本地读完后到底，不重复、不缺', async () => {
    await scrollToTop()
    setActivePinia(createPinia())
    const ids = await scrollToTop()
    expect(ids).toEqual(server.map(m => m.message_id))
  })
})
