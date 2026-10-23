import { beforeEach, describe, expect, it } from 'vitest'
import { clearPendingDraft, readPendingDraft, writePendingDraft } from '@/utils/draft-pending'

describe('draft pending local mirror', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('keeps unsynced drafts per user and conversation', () => {
    writePendingDraft('usr_a', 'conv_1', {
      content: '一半',
      replyToMessageId: 'msg_1',
      atUsers: [{ user_id: 'usr_b', name: '小明' }],
      savedAt: 10,
    })
    writePendingDraft('usr_a', 'conv_2', {
      content: '',
      replyToMessageId: null,
      atUsers: [],
      savedAt: 11,
    })
    writePendingDraft('usr_b', 'conv_1', {
      content: '别人的',
      replyToMessageId: null,
      atUsers: [],
      savedAt: 12,
    })

    expect(readPendingDraft('usr_a', 'conv_1')).toEqual({
      content: '一半',
      replyToMessageId: 'msg_1',
      atUsers: [{ user_id: 'usr_b', name: '小明' }],
      savedAt: 10,
    })
    expect(readPendingDraft('usr_a', 'conv_2')?.content).toBe('')
    expect(readPendingDraft('usr_b', 'conv_1')?.content).toBe('别人的')

    clearPendingDraft('usr_a', 'conv_1')
    expect(readPendingDraft('usr_a', 'conv_1')).toBeNull()
    expect(readPendingDraft('usr_a', 'conv_2')?.content).toBe('')
    expect(readPendingDraft('usr_b', 'conv_1')?.content).toBe('别人的')
  })

  it('ignores corrupt storage and blank identities', () => {
    localStorage.setItem('onlineim.draft.pending.usr_a', '{')
    expect(readPendingDraft('usr_a', 'conv_1')).toBeNull()
    writePendingDraft('', 'conv_1', { content: 'x', replyToMessageId: null, atUsers: [], savedAt: 1 })
    writePendingDraft('usr_a', '', { content: 'x', replyToMessageId: null, atUsers: [], savedAt: 1 })
    expect(localStorage.getItem('onlineim.draft.pending.')).toBeNull()
  })
})