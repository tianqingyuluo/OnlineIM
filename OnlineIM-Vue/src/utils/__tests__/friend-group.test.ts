import { describe, expect, it } from 'vitest'
import { friendMoveErrorText } from '@/utils/friend-group'

describe('friendMoveErrorText', () => {
  it('keeps a specific backend message', () => {
    expect(friendMoveErrorText({ message: '好友分组不存在', status: 404 })).toBe('好友分组不存在')
  })

  it('replaces a generic or non-chinese failure with a clear sentence', () => {
    expect(friendMoveErrorText({ message: '啊哦，发生了些小错误' })).toBe('移动好友到分组失败')
    expect(friendMoveErrorText({ message: 'Request failed with status code 404' })).toBe('移动好友到分组失败')
    expect(friendMoveErrorText(undefined)).toBe('移动好友到分组失败')
  })
})
