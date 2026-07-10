import { describe, expect, it } from 'vitest'
import { compareSeqId, maxSeqId } from '@/utils/seq-id'

describe('seq id comparison', () => {
  it('compares Snowflake values numerically without Number precision loss', () => {
    expect(compareSeqId('9007199254740993', '9007199254740992')).toBe(1)
    expect(compareSeqId('10', '2')).toBe(1)
    expect(maxSeqId('2', '10')).toBe('10')
  })

  it('treats invalid local cache values as zero instead of lexical order', () => {
    expect(compareSeqId('invalid', '0')).toBe(0)
    expect(compareSeqId('invalid', '1')).toBe(-1)
  })
})
