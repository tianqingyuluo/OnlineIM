import { describe, expect, it } from 'vitest'
import { formatLastSeen, formatPresence } from '@/utils/presence'

const now = new Date(2026, 9, 21, 15, 4, 0).getTime()

describe('formatPresence', () => {
  it('shows online, offline and last seen', () => {
    expect(formatPresence(undefined, now)).toBe('离线')
    expect(formatPresence({ user_id: 'usr_a', status: 'online', last_seen_at: now - 1000 }, now)).toBe('在线')
    expect(formatPresence({ user_id: 'usr_a', status: 'offline', visible: true }, now)).toBe('离线')
    expect(formatPresence({ user_id: 'usr_a', status: 'online', visible: false }, now)).toBe('离线')
    expect(formatPresence({
      user_id: 'usr_a',
      status: 'offline',
      visible: true,
      last_seen_at: now - 5 * 60_000,
    }, now)).toBe('离线 · 5 分钟前')
  })

  it('formats last seen across yesterday, this year and previous years', () => {
    expect(formatLastSeen(now - 30_000, now)).toBe('刚刚')
    expect(formatLastSeen(now - 2 * 3_600_000, now)).toBe('2 小时前')
    expect(formatLastSeen(new Date(2026, 9, 20, 9, 5).getTime(), now)).toBe('昨天 09:05')
    expect(formatLastSeen(new Date(2026, 9, 2, 9, 5).getTime(), now)).toBe('10月2日 09:05')
    expect(formatLastSeen(new Date(2024, 9, 2, 9, 5).getTime(), now)).toBe('2024年10月2日 09:05')
  })
})
