import { describe, expect, it } from 'vitest'
import {
  normalizeSearchKeyword,
  searchResultLocation,
  searchSenderName,
  splitSnippet,
  SEARCH_MAX_KEYWORD_LENGTH,
} from '@/utils/message-search'
import type { MessageSearchItem } from '@/type/message'

const item = (extra: Partial<MessageSearchItem> = {}): MessageSearchItem => ({
  message: {
    message_id: 'msg_1',
    conversation_id: 'conv_usr_ausr_b',
    sender_info: { user_id: 'usr_b', nickname: 'Bob', username: 'bob' },
    message_type: 'text',
    seq_id: '1',
    content: '明天去爬山',
    status: 1,
    timestamp: '2026-10-22 01:00:00',
    is_recalled: false,
    client_message_id: 'c1',
  },
  conversation: { conversation_id: 'conv_usr_ausr_b', type: 'private', name: 'Bob' },
  snippet: '明天去爬山',
  highlights: [{ start: 3, length: 2 }],
  ...extra,
})

describe('message search utils', () => {
  it('normalizes keywords: trims and caps by code points', () => {
    expect(normalizeSearchKeyword('  你好 ')).toBe('你好')
    expect(normalizeSearchKeyword('   ')).toBe('')
    const long = '😀'.repeat(SEARCH_MAX_KEYWORD_LENGTH + 5)
    expect(Array.from(normalizeSearchKeyword(long))).toHaveLength(SEARCH_MAX_KEYWORD_LENGTH)
  })

  it('splits snippets into plain and highlighted segments', () => {
    expect(splitSnippet('明天去爬山吗', [{ start: 3, length: 2 }])).toEqual([
      { text: '明天去', hit: false },
      { text: '爬山', hit: true },
      { text: '吗', hit: false },
    ])
    expect(splitSnippet('aXbX', [{ start: 3, length: 1 }, { start: 1, length: 1 }])).toEqual([
      { text: 'a', hit: false }, { text: 'X', hit: true }, { text: 'b', hit: false }, { text: 'X', hit: true },
    ])
  })

  it('ignores out-of-range, empty and overlapping highlights; keeps markup as text', () => {
    expect(splitSnippet('<b>x</b>', [{ start: 3, length: 1 }])).toEqual([
      { text: '<b>', hit: false }, { text: 'x', hit: true }, { text: '</b>', hit: false },
    ])
    expect(splitSnippet('abc', [{ start: 2, length: 5 }, { start: -1, length: 1 }, { start: 0, length: 0 }]))
      .toEqual([{ text: 'abc', hit: false }])
    expect(splitSnippet('abcd', [{ start: 0, length: 3 }, { start: 1, length: 2 }])).toEqual([
      { text: 'abc', hit: true }, { text: 'd', hit: false },
    ])
    expect(splitSnippet('', null)).toEqual([])
  })

  it('builds the chat location with ?msg= for private and group results', () => {
    expect(searchResultLocation(item())).toEqual({ path: '/main/chat/private/conv_usr_ausr_b', query: { msg: 'msg_1' } })
    const group = item({ conversation: { conversation_id: 'grp_1', type: 'group', name: '一群' } })
    group.message = { ...group.message, conversation_id: 'grp_1', message_id: 'msg_2' }
    expect(searchResultLocation(group)).toEqual({ path: '/main/chat/group/grp_1', query: { msg: 'msg_2' } })
  })

  it('shows 我 for own messages and nickname for others', () => {
    expect(searchSenderName(item(), 'usr_a')).toBe('Bob')
    expect(searchSenderName(item(), 'usr_b')).toBe('我')
  })
})
