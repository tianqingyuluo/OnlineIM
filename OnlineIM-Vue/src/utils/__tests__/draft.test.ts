import { describe, expect, it } from 'vitest'
import { draftPreview, draftSignature } from '@/utils/draft'

describe('draft preview', () => {
  it('collapses whitespace and truncates by code point', () => {
    expect(draftPreview('  \n')).toBe('')
    expect(draftPreview('  你好\n\n世界  ')).toBe('你好 世界')
    expect(draftPreview('哈'.repeat(41))).toBe(`${'哈'.repeat(40)}…`)
    expect(draftPreview('🙂'.repeat(45))).toBe(`${'🙂'.repeat(40)}…`)
  })

  it('signature ignores mention order and blank names', () => {
    const left = draftSignature('hi', {
      replyToMessageId: 'msg_1',
      atUsers: [
        { user_id: 'usr_b', name: '小明' },
        { user_id: 'usr_a', name: '  ' },
        { user_id: 'usr_c', name: '小红' },
      ],
    })
    const right = draftSignature('hi', {
      replyToMessageId: 'msg_1',
      atUsers: [
        { user_id: 'usr_c', name: '小红' },
        { user_id: 'usr_b', name: '小明' },
      ],
    })
    expect(left).toBe(right)
    expect(draftSignature('hi ', { replyToMessageId: null, atUsers: [] }))
      .not.toBe(draftSignature('hi', { replyToMessageId: null, atUsers: [] }))
  })
})
