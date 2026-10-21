import { describe, expect, it } from 'vitest'
import {
  EDIT_WINDOW_MS,
  editedTitle,
  isEditable,
  isNewerRevision,
  isTerminalEditError,
  mentionLabelsForEdit,
} from '@/utils/message-edit'
import { toServerTimestamp } from '@/utils/conversation-time'

const now = new Date(2026, 9, 22, 1, 0, 0).getTime()
const message = (extra: Record<string, unknown> = {}) => ({
  message_id: 'msg_1',
  message_type: 'text',
  is_recalled: false,
  status: 1,
  delivery_state: 'sent' as const,
  timestamp: toServerTimestamp(now - 30_000),
  sender_info: { user_id: 'usr_me' },
  ...extra,
})

describe('message edit utils', () => {
  it('only own, delivered, non-recalled text messages inside the window are editable', () => {
    expect(isEditable(message() as never, 'usr_me', now)).toBe(true)
    expect(isEditable(message() as never, 'usr_other', now)).toBe(false)
    expect(isEditable(message() as never, undefined, now)).toBe(false)
    expect(isEditable(message({ message_type: 'image' }) as never, 'usr_me', now)).toBe(false)
    expect(isEditable(message({ message_type: 'chat_record' }) as never, 'usr_me', now)).toBe(false)
    expect(isEditable(message({ is_recalled: true }) as never, 'usr_me', now)).toBe(false)
    expect(isEditable(message({ status: 3 }) as never, 'usr_me', now)).toBe(false)
    expect(isEditable(message({ delivery_state: 'sending' }) as never, 'usr_me', now)).toBe(false)
    expect(isEditable(message({ delivery_state: 'failed' }) as never, 'usr_me', now)).toBe(false)
    expect(isEditable(message({ message_id: 'local_abc' }) as never, 'usr_me', now)).toBe(false)
    expect(isEditable(message({ timestamp: '' }) as never, 'usr_me', now)).toBe(false)
    expect(isEditable(message({ timestamp: toServerTimestamp(now - EDIT_WINDOW_MS - 1000) }) as never, 'usr_me', now)).toBe(false)
    expect(isEditable(message({ timestamp: toServerTimestamp(now - EDIT_WINDOW_MS + 1000) }) as never, 'usr_me', now)).toBe(true)
  })

  it('compares revisions and classifies terminal errors', () => {
    expect(isNewerRevision(2, 1)).toBe(true)
    expect(isNewerRevision(2, 2)).toBe(true)
    expect(isNewerRevision(1, 2)).toBe(false)
    expect(isNewerRevision(undefined, undefined)).toBe(true)
    expect(isTerminalEditError('EDIT_WINDOW_EXPIRED')).toBe(true)
    expect(isTerminalEditError('MESSAGE_ALREADY_RECALLED')).toBe(true)
    expect(isTerminalEditError('EDIT_CONFLICT')).toBe(false)
    expect(isTerminalEditError('EDIT_CONTENT_INVALID')).toBe(false)
    expect(editedTitle('2026-10-22 01:00:00')).toBe('编辑于 2026-10-22 01:00:00')
    expect(editedTitle(null)).toBe('已编辑')
  })

  it('puts the original mentions back as picked labels using the name present in the text', () => {
    const names = { usr_a: ['阿A', 'alice'], usr_b: ['小B'], usr_c: ['小C'] }
    const picked = mentionLabelsForEdit('@alice 你好 @小B', ['usr_a', 'usr_b', 'usr_c'], names)
    expect([...picked.entries()]).toEqual([['usr_a', 'alice'], ['usr_b', '小B']])
    expect(mentionLabelsForEdit('没有提及', null, names).size).toBe(0)
  })
})
