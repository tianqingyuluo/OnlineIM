import { describe, expect, it } from 'vitest'
import { formatConversationTime, formatUnread, parseTimestamp, toServerTimestamp } from '@/utils/conversation-time'

describe('conversation time', () => {
  const now = new Date(2026, 9, 21, 22, 30, 0).getTime()
  it('parses server and ISO timestamps', () => {
    expect(parseTimestamp('2026-10-21 22:00:00')).toBe(new Date(2026, 9, 21, 22, 0, 0).getTime())
    expect(parseTimestamp(new Date(now).toISOString())).toBe(now)
    expect(parseTimestamp(null)).toBe(0)
    expect(toServerTimestamp(now)).toBe('2026-10-21 22:30:00')
  })
  it('formats relative to now', () => {
    expect(formatConversationTime('2026-10-21 09:05:00', now)).toBe('09:05')
    expect(formatConversationTime('2026-10-20 23:59:00', now)).toBe('昨天')
    expect(formatConversationTime('2026-03-02 10:00:00', now)).toBe('03-02')
    expect(formatConversationTime('2025-12-31 10:00:00', now)).toBe('2025-12-31')
    expect(formatConversationTime('', now)).toBe('')
  })
  it('formats unread badge', () => {
    expect(formatUnread(0)).toBe('')
    expect(formatUnread(5)).toBe('5')
    expect(formatUnread(120)).toBe('99+')
  })
})
